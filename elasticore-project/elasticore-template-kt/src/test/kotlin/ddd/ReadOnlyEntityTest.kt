package ddd

import io.elasticore.blueprint.domain.refdata.service.CountryService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

/**
 * @readonly in Kotlin, as in Java: the entity is read and searched over HTTP, and written by
 * the application's own code only - its controller has no write endpoint, and its service no
 * write method that code could reach it through.
 */
@SpringBootTest(
    classes = [KotlinRefdataApp::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:kt-readonly;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/schema-h2.sql",
    ],
)
@AutoConfigureMockMvc
class ReadOnlyEntityTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var jdbc: JdbcTemplate

    private val countries = "/api/v1/refdata/country"

    @Test
    fun aReadonlyEntityIsReadOverHttpAndNeverWritten() {
        jdbc.update("INSERT INTO ref_country (iso_code, name) VALUES (?, ?)", "KR", "Korea")

        val read = mvc.perform(get("$countries/KR")).andReturn().response
        assertThat(read.status).isEqualTo(200)
        assertThat(read.contentAsString).contains("Korea")
        val found = mvc.perform(post("$countries/search").contentType(MediaType.APPLICATION_JSON).content("""{"name":"Kor"}"""))
            .andReturn().response
        assertThat(found.status).isEqualTo(200)
        assertThat(found.contentAsString).contains("\"isoCode\":\"KR\"")

        // No create, update or delete endpoint: nothing at the path, or not that method there.
        val writes = listOf(
            post(countries).contentType(MediaType.APPLICATION_JSON).content("""{"isoCode":"JP","name":"Japan"}"""),
            put(countries).contentType(MediaType.APPLICATION_JSON).content("""{"isoCode":"KR","name":"Changed"}"""),
            put("$countries/batch").contentType(MediaType.APPLICATION_JSON).content("""[{"isoCode":"KR","name":"Changed"}]"""),
            post("$countries/delete").contentType(MediaType.APPLICATION_JSON).content("""[{"isoCode":"KR"}]"""),
            delete("$countries/KR"),
        )
        for (write in writes) {
            assertThat(mvc.perform(write).andReturn().response.status).isIn(404, 405)
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ref_country", Long::class.java)).isEqualTo(1L)
        assertThat(jdbc.queryForObject("SELECT name FROM ref_country WHERE iso_code = 'KR'", String::class.java))
            .isEqualTo("Korea")
    }

    @Test
    fun theServiceHasNoWriteMethod() {
        val names = CountryService::class.java.methods.map { it.name }.toSet()
        assertThat(names).contains("findById", "findBySearch")
        assertThat(names).doesNotContain("save", "update", "batchUpdate", "delete", "deleteById", "updateBySpecification")
    }
}
