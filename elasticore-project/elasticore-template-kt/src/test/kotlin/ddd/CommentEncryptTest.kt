package ddd

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.elasticore.springboot3.crypto.Bidx
import io.elasticore.springboot3.crypto.FieldCrypto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

/**
 * An encrypted field in Kotlin, over HTTP and on the generated schema: Comment.authorPhone is @encrypt @s(eq) - the
 * column holds v1:... ciphertext, the API the value - and a search by the phone, with or without its dashes, finds
 * the comment through its blind index (author_phone_bidx), after an update too. Keys: TestCryptoKeys.
 */
@SpringBootTest(
    classes = [KotlinContentApp::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:kt-encrypt;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/schema-h2.sql",
    ],
)
@AutoConfigureMockMvc
class CommentEncryptTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var jdbc: JdbcTemplate

    private val comments = "/api/v1/content/comment"

    private fun send(request: MockHttpServletRequestBuilder, body: String): JsonNode {
        val res = mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().response
        assertThat(res.status).`as`(res.contentAsString).isEqualTo(200)
        return json.readTree(res.contentAsString)
    }

    private fun stored(id: Long, column: String): String? =
        jdbc.queryForObject("SELECT $column FROM cms_comment WHERE comment_id = ?", String::class.java, id)

    private fun found(phone: String): List<String> =
        send(post("$comments/search"), """{"authorPhone":"$phone"}""").path("content").findValuesAsText("commentId")

    @Test
    fun theColumnHoldsCiphertextAndTheSearchFindsItByTheBlindIndex() {
        val created = send(post(comments), """{"content":"<p>hi</p>","authorName":"kim","authorPhone":"010-4321-8765"}""")
        val id = created.path("commentId").asLong()
        // Also @Mask(PHONE): the response shows it masked.
        assertThat(created.path("authorPhone").asText()).isEqualTo("010-****-8765")

        val column = stored(id, "author_phone")!!
        assertThat(column).startsWith("v1:").doesNotContain("4321")
        assertThat(FieldCrypto.current().decrypt(column)).isEqualTo("010-4321-8765")
        assertThat(stored(id, "author_phone_bidx")).isEqualTo(Bidx.ofDigits("01043218765"))

        val read = json.readTree(mvc.perform(get("$comments/$id")).andReturn().response.contentAsString)
        assertThat(read.path("authorPhone").asText()).isEqualTo("010-****-8765")
        assertThat(read.has("authorPhoneBidx")).`as`(read.toString()).isFalse()

        assertThat(found("01043218765")).containsExactly(id.toString())
        assertThat(found("010-4321-8765")).containsExactly(id.toString())

        // An update writes the new value's index: the old phone no longer finds it.
        send(put(comments), """{"commentId":$id,"content":"<p>hi</p>","authorName":"kim","authorPhone":"010-4321-0000"}""")
        assertThat(found("010-4321-8765")).isEmpty()
        assertThat(found("01043210000")).containsExactly(id.toString())
        assertThat(stored(id, "author_phone")).isNotEqualTo(column)
    }

    @Autowired lateinit var service: io.elasticore.blueprint.domain.content.service.CommentService

    private fun create(name: String, phone: String): Long =
        send(post(comments), """{"content":"<p>x</p>","authorName":"$name","authorPhone":"$phone"}""").path("commentId").asLong()

    /** A PUT without the phone keeps it (skip-null): the column and the index are left as they were. */
    @Test
    fun aPartialUpdateKeepsThePhoneAndItsIndex() {
        val id = create("partial-${System.nanoTime()}", "010-7000-0001")
        val column = stored(id, "author_phone")
        send(put(comments), """{"commentId":$id,"content":"<p>x</p>","authorName":"renamed"}""")
        assertThat(stored(id, "author_phone")).isEqualTo(column)
        assertThat(stored(id, "author_phone_bidx")).isEqualTo(Bidx.ofDigits("01070000001"))
        assertThat(found("01070000001")).containsExactly(id.toString())
    }

    /**
     * Issue 270: authorPhone is @encrypt and @Mask(PHONE). The read DTO sent back as it is carries the masked text,
     * which is the stored value - decrypted - masked: PUT and PUT /batch keep the stored phone and its index. A new
     * phone is written, and so is masked text that is not the stored value masked.
     */
    @Test
    fun aMaskedPhoneSentBackAsShownKeepsTheStoredOne() {
        val id = create("masked-${System.nanoTime()}", "010-7300-0001")
        val column = stored(id, "author_phone")
        val read = json.readTree(mvc.perform(get("$comments/$id")).andReturn().response.contentAsString)
                as com.fasterxml.jackson.databind.node.ObjectNode
        assertThat(read.path("authorPhone").asText()).isEqualTo("010-****-0001")

        read.put("authorName", "renamed")
        val updated = send(put(comments), json.writeValueAsString(read))
        assertThat(updated.path("authorName").asText()).isEqualTo("renamed")
        assertThat(updated.path("authorPhone").asText()).isEqualTo("010-****-0001")
        assertThat(stored(id, "author_phone")).isEqualTo(column)
        assertThat(found("01073000001")).containsExactly(id.toString())

        read.put("authorName", "batched")
        mvc.perform(put("$comments/batch").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(json.createArrayNode().add(read))))
            .andReturn().response.let { assertThat(it.status).`as`(it.contentAsString).isEqualTo(200) }
        assertThat(stored(id, "author_phone")).isEqualTo(column)
        assertThat(FieldCrypto.current().decrypt(column)).isEqualTo("010-7300-0001")

        read.put("authorPhone", "010-7300-0002")
        assertThat(send(put(comments), json.writeValueAsString(read)).path("authorPhone").asText()).isEqualTo("010-****-0002")
        assertThat(FieldCrypto.current().decrypt(stored(id, "author_phone"))).isEqualTo("010-7300-0002")
        assertThat(found("01073000002")).containsExactly(id.toString())

        // The old masked text is not the new phone masked: written as sent, as any other text.
        read.put("authorPhone", "010-****-0001")
        send(put(comments), json.writeValueAsString(read))
        assertThat(FieldCrypto.current().decrypt(stored(id, "author_phone"))).isEqualTo("010-****-0001")
    }

    /** PUT /batch and an update by condition write each row through the same moment: the index follows. */
    @Test
    fun aBatchAndAnUpdateByConditionReIndex() {
        val a = create("batch-${System.nanoTime()}", "010-7100-0001")
        val b = create("batch-${System.nanoTime()}", "010-7100-0002")
        mvc.perform(put("$comments/batch").contentType(MediaType.APPLICATION_JSON).content(
            """[{"commentId":$a,"content":"<p>x</p>","authorName":"b","authorPhone":"010-7100-1001"},{"commentId":$b,"content":"<p>x</p>","authorName":"b","authorPhone":"010-7100-1002"}]"""))
            .andReturn().response.let { assertThat(it.status).`as`(it.contentAsString).isEqualTo(200) }
        assertThat(found("01071001001")).containsExactly(a.toString())
        assertThat(found("01071001002")).containsExactly(b.toString())
        assertThat(found("01071000001")).isEmpty()

        val name = "cond-${System.nanoTime()}"
        val c = create(name, "010-7200-0001")
        val changed = service.updateBySpecification(
            io.elasticore.blueprint.domain.content.dto.CommentDTO().apply { authorPhone = "010-7200-9999" },
            io.elasticore.blueprint.domain.content.dto.CommentSrchDTO().apply { authorName = name })
        assertThat(changed).isEqualTo(1)
        assertThat(found("01072009999")).containsExactly(c.toString())
        assertThat(found("01072000001")).isEmpty()
        assertThat(FieldCrypto.current().decrypt(stored(c, "author_phone"))).isEqualTo("010-7200-9999")
    }
}
