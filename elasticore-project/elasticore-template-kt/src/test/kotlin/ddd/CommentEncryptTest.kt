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
        assertThat(created.path("authorPhone").asText()).isEqualTo("010-4321-8765")

        val column = stored(id, "author_phone")!!
        assertThat(column).startsWith("v1:").doesNotContain("4321")
        assertThat(FieldCrypto.current().decrypt(column)).isEqualTo("010-4321-8765")
        assertThat(stored(id, "author_phone_bidx")).isEqualTo(Bidx.of("01043218765"))

        val read = json.readTree(mvc.perform(get("$comments/$id")).andReturn().response.contentAsString)
        assertThat(read.path("authorPhone").asText()).isEqualTo("010-4321-8765")
        assertThat(read.has("authorPhoneBidx")).`as`(read.toString()).isFalse()

        assertThat(found("01043218765")).containsExactly(id.toString())
        assertThat(found("010-4321-8765")).containsExactly(id.toString())

        // An update writes the new value's index: the old phone no longer finds it.
        send(put(comments), """{"commentId":$id,"content":"<p>hi</p>","authorName":"kim","authorPhone":"010-4321-0000"}""")
        assertThat(found("010-4321-8765")).isEmpty()
        assertThat(found("01043210000")).containsExactly(id.toString())
        assertThat(stored(id, "author_phone")).isNotEqualTo(column)
    }
}
