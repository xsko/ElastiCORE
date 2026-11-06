package ddd

import io.elasticore.blueprint.domain.shop.dto.AgentDTO
import io.elasticore.blueprint.domain.shop.dto.TicketDTO
import io.elasticore.blueprint.domain.shop.dto.TicketSrchDTO
import io.elasticore.blueprint.domain.shop.entity.Product
import io.elasticore.blueprint.domain.shop.entity.Ticket
import io.elasticore.blueprint.domain.shop.entity.TicketEntry
import io.elasticore.blueprint.domain.shop.enums.BillingStatus
import io.elasticore.blueprint.domain.shop.enums.TicketStatus
import io.elasticore.blueprint.domain.shop.service.AgentService
import io.elasticore.blueprint.domain.shop.service.TicketService
import io.elasticore.springboot3.domain.Quantity
import io.elasticore.springboot3.security.CurrentUser
import io.elasticore.springboot3.security.ForbiddenException
import io.elasticore.springboot3.security.NotSignedInException
import io.elasticore.springboot3.security.SessionCurrentUser
import io.elasticore.springboot3.validation.InvariantViolationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.math.BigDecimal

/**
 * The domain-rule DSL, generated in Kotlin: states, @snapshot, @compute,
 * @access, @owner, @login and @password (shop/model/ticket.yml).
 */
@SpringBootTest(classes = [KotlinShopApp::class])
class DomainRulesDslTest {

    @Autowired lateinit var agents: AgentService
    @Autowired lateinit var tickets: TicketService

    @AfterEach
    fun leaveTheRequest() = RequestContextHolder.resetRequestAttributes()

    /** A request from this agent - or from a visitor, for null. */
    private fun asAgent(id: Long?) {
        val request = MockHttpServletRequest()
        if (id != null) SessionCurrentUser.signIn(request, id, "USER")
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
    }

    /** Made by the application itself, as at start-up. */
    private fun agent(login: String): AgentDTO = CurrentUser.callAsSystem {
        agents.save(AgentDTO().apply { this.login = login; secret = "pw-$login"; grade = "GOLD" })!!
    }

    @Test
    fun aTicketMovesThroughItsLifecycleAndNoFurther() {
        val ticket = Ticket()
        assertThat(ticket.status).isEqualTo(TicketStatus.OPEN)
        assertThat(ticket.canClose()).isTrue()
        ticket.close()
        assertThat(ticket.status).isEqualTo(TicketStatus.CLOSED)
        assertThat(ticket.canClose()).isFalse()
        val refused = assertThrows<InvariantViolationException> { ticket.close() }
        assertThat(refused.message).contains("cannot close when status is CLOSED")
    }

    @Test
    fun aCommandOfTwoLifecyclesMovesBothOrNeither() {
        val ticket = Ticket()
        assertThat(ticket.canCancel()).isTrue()
        ticket.cancel()
        assertThat(ticket.status).isEqualTo(TicketStatus.CLOSED)
        assertThat(ticket.billing).isEqualTo(BillingStatus.VOID)

        // Billed already: the status could close, but must not.
        val billed = Ticket()
        billed.bill()
        assertThat(billed.canCancel()).isFalse()
        val refused = assertThrows<InvariantViolationException> { billed.cancel() }
        assertThat(refused.message).contains("cannot cancel when billing is BILLED")
        assertThat(billed.status).isEqualTo(TicketStatus.OPEN)
    }

    @Test
    fun eitherCommandThatClosesTheTicketRecordsWhen() {
        val closed = Ticket()
        assertThat(closed.closedAt).isNull()
        closed.close()
        assertThat(closed.closedAt).isNotNull()

        // cancel moves two lifecycles; it stamps the one that enters CLOSED.
        val cancelled = Ticket()
        cancelled.cancel()
        assertThat(cancelled.closedAt).isNotNull()

        // Refused: nothing moves, nothing is stamped.
        val billed = Ticket()
        billed.bill()
        assertThrows<InvariantViolationException> { billed.cancel() }
        assertThat(billed.closedAt).isNull()
    }

    @Test
    fun theStampIsStoredAndNoRequestWritesIt() {
        val lee = agent("choi").agentId!!
        asAgent(lee)
        val made = tickets.save(TicketDTO().apply { subject = "stamp"; closedAt = java.time.Instant.EPOCH })!!
        assertThat(made.closedAt).isNull()

        val before = java.time.Instant.now().minusSeconds(2)
        tickets.close(made.ticketId!!)
        val read = tickets.findById(made.ticketId!!).get()
        assertThat(read.status).isEqualTo(TicketStatus.CLOSED)
        assertThat(read.closedAt).isAfter(before)
    }

    @Autowired lateinit var jdbc: org.springframework.jdbc.core.JdbcTemplate
    @Autowired lateinit var tx: org.springframework.transaction.support.TransactionTemplate
    @Autowired lateinit var em: jakarta.persistence.EntityManager

    /** An administrator's request. */
    private fun asAdmin() {
        val request = MockHttpServletRequest()
        SessionCurrentUser.signIn(request, 0L, "ADMIN")
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
    }

    private fun ticketWithAnEntry(subject: String): String {
        val owner = agent("sd-" + System.nanoTime()).agentId!!
        asAgent(owner)
        // An entry with no product: this application keeps no product table.
        return tickets.save(TicketDTO().apply {
            this.subject = subject
            entries = listOf(io.elasticore.blueprint.domain.shop.dto.TicketEntryDTO().apply { hours = Quantity.of(2) })
        })!!.ticketId!!
    }

    private fun count(sql: String, id: String): Long = jdbc.queryForObject(sql, Long::class.java, id)!!

    @Test
    fun aDeleteKeepsTheTicketAndItsEntriesAndTheLifecycleStillGuardsIt() {
        val id = ticketWithAnEntry("soft")
        asAdmin()
        // Open: the lifecycle refuses the delete, as it did before (states: delete), and nothing is kept as deleted.
        // Spring hands the refusal on wrapped, as it did before the delete was kept (states: delete).
        val refused = assertThrows<RuntimeException> { tickets.deleteById(id) }
        assertThat(generateSequence<Throwable>(refused) { it.cause }.any { it is InvariantViolationException }).isTrue()
        assertThat(count("SELECT COUNT(*) FROM shop_ticket WHERE ticket_id = ? AND deleted_at IS NULL", id)).isEqualTo(1L)

        tickets.close(id)
        assertThat(tickets.deleteById(id)).isTrue()
        assertThat(count("SELECT COUNT(*) FROM shop_ticket WHERE ticket_id = ? AND deleted_at IS NOT NULL", id)).isEqualTo(1L)
        // Its members are kept with it, as @deleted rows of their own.
        assertThat(count("SELECT COUNT(*) FROM shop_ticket_entry WHERE ticket_ticket_id = ? AND deleted_at IS NOT NULL", id))
            .isEqualTo(1L)
        assertThat(tickets.findById(id)).isEmpty
    }

    @Test
    fun anEntryTakenOutIsKeptAndDoesNotComeBack() {
        val id = ticketWithAnEntry("orphan")
        val read = tickets.findById(id).get()
        assertThat(read.entries).hasSize(1)
        // Taken out of its aggregate: orphan removal is the delete, and the delete keeps the row.
        tx.executeWithoutResult {
            val ticket = em.find(Ticket::class.java, id)
            ticket.removeTicketEntry(ticket.entries!!.first())
        }
        assertThat(count("SELECT COUNT(*) FROM shop_ticket_entry WHERE ticket_ticket_id = ? AND deleted_at IS NOT NULL", id))
            .isEqualTo(1L)
        assertThat(tickets.findById(id).get().entries ?: emptyList()).isEmpty()
    }

    @Test
    fun aTicketIsDeletedOnlyWhenEveryLifecycleSaysSo() {
        val open = Ticket()
        assertThat(open.canDelete()).isFalse()
        val refused = assertThrows<InvariantViolationException> { open.checkDeletable() }
        assertThat(refused.code).isEqualTo("state.Ticket.status.delete")
        assertThat(refused.message).isEqualTo("Ticket cannot be deleted when status is OPEN; it must be [CLOSED]")

        val cancelled = Ticket()
        cancelled.cancel()
        assertThat(cancelled.canDelete()).isTrue()
        cancelled.checkDeletable()

        // Closed but billed: the second lifecycle refuses.
        val billed = Ticket()
        billed.bill()
        billed.close()
        assertThat(billed.canDelete()).isFalse()
        assertThat(assertThrows<InvariantViolationException> { billed.checkDeletable() }.code)
            .isEqualTo("state.Ticket.billing.delete")
    }

    @Test
    fun anEntryTakesThePriceOnceAndTheTicketAddsItUp() {
        val product = Product().apply { price = BigDecimal("12.50") }
        val entry = TicketEntry()
        entry.creation().productId = "p-1"
        entry.creation().hours = Quantity.of(2)
        val ticket = Ticket()
        ticket.addTicketEntry(entry)

        ticket.derive { _, _ -> product }
        assertThat(entry.rate).isEqualByComparingTo("12.50")
        assertThat(entry.amount).isEqualByComparingTo("25.00")
        assertThat(ticket.total).isEqualByComparingTo("25.00")

        // Taken once: a later price leaves the entry as it was.
        product.price = BigDecimal("99")
        ticket.derive { _, _ -> product }
        assertThat(entry.rate).isEqualByComparingTo("12.50")

        assertThrows<InvariantViolationException> { TicketEntry().apply { creation().productId = "gone" }.derive { _, _ -> null } }
    }

    @Test
    fun aPasswordIsKeptAsAHashAndSignsIn() {
        val saved = agent("kim")
        assertThat(CurrentUser.callAsSystem { agents.findById(saved.agentId!!).get() }.secret).isNull()   // never read back
        assertThat(agents.authenticate("kim", "pw-kim")).isPresent
        assertThat(agents.authenticate("kim", "wrong")).isEmpty
        assertThat(agents.authenticate("nobody", "pw-kim")).isEmpty
        assertThat(saved.grade).isEqualTo("GOLD")

        // A visitor signing up cannot give themselves a grade.
        asAgent(null)
        assertThat(agents.save(AgentDTO().apply { login = "visitor"; secret = "x"; grade = "GOLD" })!!.grade).isNull()
    }

    @Test
    fun theOwnerSeesTheirOwnAndOnlyTheAdministratorDoesMore() {
        val lee = agent("lee").agentId!!
        val park = agent("park").agentId!!

        asAgent(null)
        assertThrows<NotSignedInException> { tickets.save(TicketDTO().apply { subject = "visitor" }) }

        asAgent(lee)
        // Created for whoever is signed in, whatever the request says.
        val mine = tickets.save(TicketDTO().apply { subject = "printer"; agentId = park })!!
        assertThat(mine.agentId).isEqualTo(lee)
        assertThat(tickets.findBySearch(TicketSrchDTO()).content).allMatch { it.agentId == lee }
        // A visitor-level field the administrator alone writes.
        assertThat(agents.update(AgentDTO().apply { agentId = lee; grade = "PLATINUM" })!!.grade).isEqualTo("GOLD")

        asAgent(park)
        assertThat(tickets.findById(mine.ticketId!!)).isEmpty
        assertThrows<jakarta.persistence.EntityNotFoundException> { tickets.close(mine.ticketId!!) }
        assertThrows<ForbiddenException> { tickets.findAll() }

        asAgent(lee)
        assertThat(tickets.close(mine.ticketId!!)!!.status).isEqualTo(TicketStatus.CLOSED)
    }
}
