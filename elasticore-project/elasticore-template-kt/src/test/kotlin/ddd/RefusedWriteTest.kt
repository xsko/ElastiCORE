package ddd

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.transaction.annotation.Transactional

/**
 * Issue 344, in Kotlin: a refused update leaves no trace in what its transaction reads. The request is
 * mapped onto the managed entity, then refused by the check the write makes after the mapping - a parent
 * that names no row (404 reference.Category.parentCategory). A read of the row later in the same
 * transaction answers the stored name, and so does the table.
 */
@SpringBootTest(classes = [KotlinShopApp::class])
@AutoConfigureMockMvc
@Transactional
class RefusedWriteTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var json: ObjectMapper

    @PersistenceContext
    private lateinit var em: EntityManager

    private val categories = "/api/v1/shop/category"

    @Test
    @DisplayName("a refused update is not what the same transaction reads next")
    fun aRefusedUpdateIsNotReadBack() {
        val created = mvc.perform(post(categories).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"original"}""")).andReturn().response
        assertThat(created.status).`as`(created.contentAsString).isEqualTo(200)
        val id = json.readTree(created.contentAsString).path("catId").asLong()

        val refused = mvc.perform(put(categories).contentType(MediaType.APPLICATION_JSON)
            .content("""{"catId":$id,"name":"REFUSED CHANGE","parentCategoryCatId":987654}""")).andReturn().response
        assertThat(refused.status).`as`(refused.contentAsString).isEqualTo(404)

        val read = mvc.perform(get("$categories/$id")).andReturn().response
        assertThat(json.readTree(read.contentAsString).path("name").asText())
            .`as`("the refused change is not what the transaction reads")
            .isEqualTo("original")
        assertThat(em.createQuery("select c.name from Category c where c.catId = :id", String::class.java)
            .setParameter("id", id).singleResult).isEqualTo("original")
    }
}
