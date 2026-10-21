package ddd

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.elasticore.springboot3.bean.ApplicationContextProvider
import io.elasticore.springboot3.bean.DbTransactionGateway
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

/**
 * The Kotlin shop domain, generated through the hexagonal Kotlin bundle and
 * booted: no plugin opens the generated classes here, so this is also the
 * proof that the generated services can be proxied as they are written.
 */
@SpringBootApplication(proxyBeanMethods = false)
@Import(DbTransactionGateway::class, ApplicationContextProvider::class)
@EnableJpaAuditing
@ComponentScan("io.elasticore.blueprint.domain.shop")
@EntityScan("io.elasticore.blueprint.domain.shop.entity", "io.elasticore.blueprint.domain.shop.infrastructure")
@EnableJpaRepositories("io.elasticore.blueprint.domain.shop.infrastructure")
open class KotlinShopApp

@SpringBootTest(classes = [KotlinShopApp::class])
@AutoConfigureMockMvc
class ShopApiContractTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var json: ObjectMapper

    private val orders = "/api/v1/shop/order"

    private fun createOrder(notes: String): String {
        val body = mvc.perform(post(orders).contentType(MediaType.APPLICATION_JSON)
            .content("""{"orderDate":"2026-08-15 10:00:00","notes":"$notes","items":[{"itemSeq":1,"quantity":1,"unitPrice":10.00}]}"""))
            .andReturn().response.contentAsString
        return json.readTree(body).path("orderId").asText()
    }

    private fun read(id: String): JsonNode =
        json.readTree(mvc.perform(get("$orders/$id")).andReturn().response.contentAsString)

    private fun send(body: String): Int =
        mvc.perform(put(orders).contentType(MediaType.APPLICATION_JSON).content(body))
            .andReturn().response.status

    @Test
    @DisplayName("an order is created with its lines and read back")
    fun createAndRead() {
        val id = createOrder("kotlin")
        assertThat(id).isNotEmpty()
        assertThat(read(id).path("items").size()).isEqualTo(1)
    }

    @Test
    @DisplayName("a stale version is a conflict, not a lost update")
    fun staleVersionIsAConflict() {
        val id = createOrder("v")
        val v0 = read(id).path("version").asLong()
        assertThat(send("""{"orderId":"$id","notes":"first","version":$v0}""")).isEqualTo(200)
        assertThat(send("""{"orderId":"$id","notes":"second","version":$v0}""")).isEqualTo(409)
        assertThat(read(id).path("notes").asText()).isEqualTo("first")
    }

    @Test
    @DisplayName("a create naming a taken identifier is refused")
    fun createNeverOverwrites() {
        val id = createOrder("original")
        val status = mvc.perform(post(orders).contentType(MediaType.APPLICATION_JSON)
            .content("""{"orderId":"$id","orderDate":"2026-01-01 00:00:00","notes":"overwritten","items":[{"itemSeq":1,"quantity":1,"unitPrice":1.00}]}"""))
            .andReturn().response.status
        assertThat(status).isEqualTo(409)
        assertThat(read(id).path("notes").asText()).isEqualTo("original")
    }

    @Test
    @DisplayName("an update is validated for what it sends, and a missing row is 404")
    fun validationAndNotFound() {
        val id = createOrder("valid")
        assertThat(send("""{"orderId":"$id","trackingNumber":"${"x".repeat(60)}"}""")).isEqualTo(400)
        assertThat(send("""{"orderId":"$id","notes":"partial"}""")).isEqualTo(200)
        assertThat(send("""{"orderId":"no-such-order","notes":"x"}""")).isEqualTo(404)
    }

    @Test
    @DisplayName("an order without a line is refused by the domain: 422")
    fun brokenInvariantIsUnprocessable() {
        val response = mvc.perform(post(orders).contentType(MediaType.APPLICATION_JSON)
            .content("""{"orderDate":"2026-08-15 10:00:00","notes":"no lines"}"""))
            .andReturn().response
        assertThat(response.status).isEqualTo(422)
        // The rule that refused, by its code, and its default wording from i18n/shop-messages.properties.
        val body = response.contentAsString
        assertThat(body).contains("\"code\":\"invariant.Order.items-size-gt-0\"")
        assertThat(body).contains("an order has at least one line")
    }

    @Test
    @DisplayName("on a Korean machine a field error is said in English unless the request asks for Korean")
    fun aFieldErrorIsNotSaidInTheMachinesLanguage() {
        val id = createOrder("language")
        val tooLong = """{"orderId":"$id","trackingNumber":"${"x".repeat(60)}"}"""
        val hangul = Regex("[\\uAC00-\\uD7A3]")
        for (language in listOf(null, "en", "sv")) {
            var request = put(orders).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(tooLong)
            if (language != null) request = request.header("Accept-Language", language)
            val response = mvc.perform(request).andReturn().response
            val body = response.getContentAsString(Charsets.UTF_8)
            assertThat(response.status).isEqualTo(400)
            assertThat(body).contains("trackingNumber")
            assertThat(hangul.containsMatchIn(body)).describedAs("Accept-Language $language: $body").isFalse()
        }
    }

    private fun answer(request: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder) =
        mvc.perform(request.accept(MediaType.APPLICATION_JSON)).andReturn().response

    @Test
    @DisplayName("what Spring MVC refuses before a controller runs is the API's problem body with a code (Kotlin, Jackson 2)")
    fun requestShapeRefusalsAreProblems() {
        // A coded enum read by the generated CustomDeserializer: its own refusal, at the field.
        val coded = answer(post(orders).contentType(MediaType.APPLICATION_JSON)
            .content("""{"orderDate":"2026-08-15 10:00:00","paymentMethod":"XX","items":[{"itemSeq":1,"quantity":1,"unitPrice":10.00}]}"""))
        assertThat(coded.status).isEqualTo(400)
        assertThat(MediaType.parseMediaType(coded.contentType).isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue()
        val body = json.readTree(coded.contentAsString)
        assertThat(body.path("code").asText()).isEqualTo("invalid")
        assertThat(body.path("fieldErrors").get(0).path("field").asText()).isEqualTo("paymentMethod")
        assertThat(body.path("fieldErrors").get(0).path("code").asText()).isEqualTo("typeMismatch")
        assertThat(body.path("fieldErrors").get(0).path("message").asText()).isEqualTo("Unknown PaymentMethod code: XX")

        assertThat(json.readTree(answer(post(orders).contentType(MediaType.APPLICATION_JSON).content("""{"notes":"""))
            .contentAsString).path("code").asText()).isEqualTo("unreadable")

        val nowhere = answer(get("/api/v1/shop/nothing/here"))
        assertThat(nowhere.status).isEqualTo(404)
        assertThat(json.readTree(nowhere.contentAsString).path("code").asText()).isEqualTo("not-found")
    }

    @Test
    @DisplayName("GET of a row that is not there answers the problem body, not an empty 404")
    fun findByIdOfNothingIsAProblem() {
        val missing = answer(get("$orders/no-such-order"))
        assertThat(missing.status).isEqualTo(404)
        val body = json.readTree(missing.contentAsString)
        assertThat(body.path("code").asText()).isEqualTo("not-found")
        assertThat(body.path("detail").asText()).isEqualTo("Order not found")
    }

    @Test
    @DisplayName("the login an agent signs in with is unique: a second agent with it is refused (409), the first still signs in")
    fun aTakenLoginIsAConflict() {
        val agents = "/api/v1/shop/agent"
        fun create(login: String, secret: String): Int = mvc.perform(post(agents).contentType(MediaType.APPLICATION_JSON)
            .content("""{"login":"$login","secret":"$secret"}""")).andReturn().response.status
        assertThat(create("taken", "pw-first")).isEqualTo(200)
        assertThat(create("taken", "pw-second")).isEqualTo(409)
        val signIn = mvc.perform(post("$agents/signin").contentType(MediaType.APPLICATION_JSON)
            .content("""{"login":"taken","secret":"pw-first"}""")).andReturn().response
        assertThat(signIn.status).isEqualTo(200)

        // Renaming one's own login to a taken one is refused the same way.
        assertThat(create("other", "pw-other")).isEqualTo(200)
        val other = mvc.perform(post("$agents/signin").contentType(MediaType.APPLICATION_JSON)
            .content("""{"login":"other","secret":"pw-other"}""")).andReturn()
        val otherId = json.readTree(other.response.contentAsString).path("agentId").asLong()
        val session = other.request.session as org.springframework.mock.web.MockHttpSession
        val rename = mvc.perform(put(agents).session(session).contentType(MediaType.APPLICATION_JSON)
            .content("""{"agentId":$otherId,"login":"taken"}""")).andReturn().response.status
        assertThat(rename).isEqualTo(409)
    }
}
