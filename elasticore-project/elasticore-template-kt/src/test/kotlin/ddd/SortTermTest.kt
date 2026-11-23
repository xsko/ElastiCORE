package ddd

import io.elasticore.blueprint.domain.shop.ShopContractSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

/**
 * A search sorts only by what its response shows (Kotlin, JPA, hexagonal). The order of a value no
 * response shows - an agent's password hash, through the ticket's agent or on the agent itself - would
 * tell its order to anyone who may search: 400 no-such-property, before the database is asked.
 */
class SortTermTest : ShopContractSupport() {

    private val TICKET = "/api/v1/shop/ticket"
    private val AGENT = "/api/v1/shop/agent"

    private fun refused(result: MvcResult, term: String) {
        assertThat(result.response.status).describedAs(answer(result)).isEqualTo(400)
        assertThat(code(result)).isEqualTo("no-such-property")
        assertThat(value(result, "detail").toString()).contains("'$term'")
    }

    private fun exporting(body: Any?) =
        MockMvcRequestBuilders.post("$TICKET/export").contentType(MediaType.APPLICATION_JSON).content(json(body))

    @Test
    @DisplayName("an agent's ticket search refuses a sort on the agent's password, by sortCode, sortColumn and the export")
    fun aSortOnAJoinedPasswordIsRefused() {
        val agent = signIn("USER")
        for (subject in listOf("b-ticket", "a-ticket", "c-ticket")) {
            assertThat(ok(sentBy(agent, creating(TICKET, obj("subject", subject))))).isTrue()
        }

        refused(sentBy(agent, searching(TICKET, obj("sortCode", "agent.secret+"))), "agent.secret")
        refused(sentBy(agent, searching(TICKET, obj("sortCode", "subject+,agent.secret-"))), "agent.secret")
        refused(sentBy(agent, searching(TICKET, obj("sortColumn", "agent.secret", "sortAscending", true))), "agent.secret")
        refused(sentBy(agent, searching(TICKET, obj("sortCode", "agent.login+"))), "agent.login")
        refused(sentBy(agent, exporting(obj("sortCode", "agent.secret+"))), "agent.secret")
        refused(sentBy(agent, exporting(obj("search", obj("sortCode", "agent.secret+")))), "agent.secret")

        // What the response shows still sorts: its own values and the agent's key.
        val sorted = sentBy(agent, searching(TICKET, obj("sortCode", "subject-")))
        assertThat(ok(sorted)).describedAs(answer(sorted)).isTrue()
        assertThat(body(sorted).indexOf("c-ticket")).isLessThan(body(sorted).indexOf("a-ticket"))
        assertThat(ok(sentBy(agent, searching(TICKET, obj("sortCode", "agent.agentId+,ticketId-"))))).isTrue()
        assertThat(ok(sentBy(agent, searching(TICKET, obj("sortColumn", "subject", "sortAscending", false))))).isTrue()
        assertThat(ok(sentBy(agent, exporting(obj("sortCode", "subject+"))))).isTrue()
    }

    @Test
    @DisplayName("the administrator's agent search refuses a sort on the password itself")
    fun aSortOnTheOwnPasswordIsRefused() {
        refused(perform(searching(AGENT, obj("sortCode", "secret+"))), "secret")
        assertThat(ok(perform(searching(AGENT, obj("sortCode", "login+"))))).isTrue()
    }
}
