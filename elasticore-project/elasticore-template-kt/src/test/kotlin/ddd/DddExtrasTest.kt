package ddd

import io.elasticore.blueprint.domain.shop.dto.CustomerOrderCountDTO
import io.elasticore.blueprint.domain.shop.entity.Customer
import io.elasticore.blueprint.domain.shop.entity.Order
import io.elasticore.blueprint.domain.shop.entity.OrderItem
import io.elasticore.blueprint.domain.shop.event.OrderPlacedEvent
import io.elasticore.blueprint.domain.shop.repository.CustomerRepositoryPort
import io.elasticore.blueprint.domain.shop.repository.OrderRepositoryPort
import io.elasticore.blueprint.domain.shop.service.LoyaltyListener
import io.elasticore.blueprint.domain.shop.service.OrderQueryPort
import io.elasticore.blueprint.domain.shop.infrastructure.ShopOutboxRelay
import io.elasticore.springboot3.outbox.OutboxEnvelope
import io.elasticore.springboot3.outbox.OutboxPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList

/** The same checks as the Java sample, on what the Kotlin rules generate. */
@SpringBootTest(classes = [KotlinShopApp::class])
@Import(DddExtrasTest.Broker::class)
class DddExtrasTest {

    @TestConfiguration
    open class Broker {
        companion object {
            val sent: MutableList<OutboxEnvelope> = CopyOnWriteArrayList()
        }

        @Bean
        open fun publisher(): OutboxPublisher = OutboxPublisher { sent.add(it) }
    }

    @Autowired lateinit var tx: TransactionTemplate
    @Autowired lateinit var orders: OrderRepositoryPort
    @Autowired lateinit var customers: CustomerRepositoryPort
    @Autowired lateinit var relay: ShopOutboxRelay
    @Autowired lateinit var orderQuery: OrderQueryPort
    @Autowired lateinit var loyalty: LoyaltyListener

    private fun placeOrder(): String = tx.execute {
        val customer = Customer()
        customer.creation().firstName = "Dana"
        customer.creation().lastName = "Reed"
        customer.creation().email = "dana${System.nanoTime()}@example.com"
        customers.save(customer)

        val order = Order()
        order.creation().orderDate = LocalDateTime.now()
        order.creation().totalAmount = BigDecimal("19.99")
        order.creation().customerId = customer.customerId
        val item = OrderItem()
        item.itemSeq = 1
        item.quantity = 1
        item.unitPrice = BigDecimal("19.99")
        order.addOrderItem(item)
        order.raisePlaced()
        orders.save(order).orderId!!
    }!!

    @Test
    @DisplayName("the event is recorded with the order and relayed, carrying what @event named")
    fun outboxAndPayload() {
        val id = placeOrder()
        assertThat(relay.pending()).isPositive()
        assertThat(relay.relay()).isPositive()
        val placed = Broker.sent.first { it.aggregateId == id }
        assertThat(placed.eventType).isEqualTo(OrderPlacedEvent::class.java.name)
        assertThat(placed.payload).contains("\"totalAmount\":19.99")
        assertThat(relay.pending()).isZero()
    }

    @Test
    @DisplayName("the hand-written listener is a bean that reacts to the event after commit")
    fun listenerIsWired() {
        assertThat(loyalty).isNotNull
        assertThat(LoyaltyListener::class.java.getMethod("on", OrderPlacedEvent::class.java)
            .isAnnotationPresent(TransactionalEventListener::class.java)).isTrue()
        placeOrder()
    }

    @Test
    @DisplayName("a projection query answers with its DTO through the query port")
    fun projection() {
        placeOrder()
        val rows: List<CustomerOrderCountDTO> = orderQuery.countByCustomer()
        assertThat(rows).isNotEmpty
        assertThat(rows).allSatisfy { assertThat(it.orderCount).isPositive() }
    }
}
