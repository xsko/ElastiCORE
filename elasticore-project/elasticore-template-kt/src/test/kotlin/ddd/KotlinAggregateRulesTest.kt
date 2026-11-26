package ddd

import io.elasticore.blueprint.domain.shop.entity.Order
import io.elasticore.blueprint.domain.shop.entity.OrderInvariants
import io.elasticore.blueprint.domain.shop.entity.OrderItem
import io.elasticore.blueprint.domain.shop.entity.OrderFactory
import io.elasticore.blueprint.domain.shop.entity.OrderRules
import io.elasticore.blueprint.domain.shop.event.OrderCancelledEvent
import io.elasticore.blueprint.domain.shop.event.OrderPlacedEvent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * The DDD rules, in the other language, actually running.
 *
 * The Java sample proves these patterns through a persistence context, which
 * this module has no harness for. What is language-specific is not the JPA
 * behaviour but the source that was generated - a nullable property read with
 * the wrong assertion, or a comparison written the way Java writes it, is a
 * compile error at best and a wrong answer at worst. So the generated Kotlin
 * is called directly.
 *
 * Outside src/test/kotlin/io on purpose: the generation task empties that
 * tree on every run, and a hand-written test living in it would vanish.
 */
class KotlinAggregateRulesTest {

    @Test
    @DisplayName("the invariant listener refuses an order with no line")
    fun invariantRefusesAnEmptyOrder() {
        assertThatThrownBy { OrderInvariants().check(order("7.50")) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("an order has at least one line")
    }

    @Test
    @DisplayName("the same order passes once it has one")
    fun invariantAcceptsAnOrderWithALine() {
        val order = order("7.50")
        order.addOrderItem(item())

        OrderInvariants().check(order)
    }

    @Test
    @DisplayName("a null aggregate is not a violation - there is nothing to promise about")
    fun invariantIgnoresNull() {
        OrderInvariants().check(null)
    }

    @Test
    @DisplayName("the hand-written numeric rule (OrderRules) compares the way BigDecimal has to be compared")
    fun numericSpecification() {
        assertThat(OrderRules.highValue().test(order("150.00"))).isTrue()
        assertThat(OrderRules.highValue().test(order("100.00")))
            .`as`("the bound is exclusive, as the condition wrote it")
            .isFalse()
        assertThat(OrderRules.highValue().test(Order()))
            .`as`("an unset property is not a match, and is not an exception either")
            .isFalse()
    }

    @Test
    @DisplayName("the hand-written collection rule reads the members through the property")
    fun collectionSpecification() {
        val empty = order("10.00")
        assertThat(OrderRules.hasLines().test(empty)).isFalse()

        val filled = order("10.00")
        filled.addOrderItem(item())
        assertThat(OrderRules.hasLines().test(filled)).isTrue()
    }

    @Test
    @DisplayName("two rules combine, which is why they are predicates")
    fun specificationsCombine() {
        val worthShipping =
            OrderRules.highValue().and(OrderRules.hasLines())

        val valuableWithLines = order("150.00")
        valuableWithLines.addOrderItem(item())

        assertThat(worthShipping.test(order("150.00"))).isFalse()
        assertThat(worthShipping.test(valuableWithLines)).isTrue()
    }

    @Test
    @DisplayName("the aggregate's members are changed one at a time, through the root")
    fun memberOperations() {
        val order = order("10.00")
        val line = item()

        order.addOrderItem(line)
        assertThat(order.items)
            .`as`("the property is mutable so that the root can change it")
            .containsExactly(line)
        assertThat(line.order)
            .`as`("adding through the root is what makes the member say whose it is")
            .isSameAs(order)

        order.removeOrderItem(line)
        assertThat(order.items).isEmpty()
        // Kept on purpose: the member is deleted at the next flush, and the
        // root's promises are checked then, through this reference.
        assertThat(line.order).isSameAs(order)

        // What the property hands out is a view: a member put in past the
        // root would never be given its owner and would vanish on save.
        org.assertj.core.api.Assertions.assertThatThrownBy { order.items!!.add(OrderItem()) }
            .isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test
    @DisplayName("replacing swaps every member at once, which the boundary makes safe")
    fun replaceMembers() {
        val order = order("10.00")
        order.addOrderItem(item())

        val replacement = item()
        order.replaceItems(listOf(replacement))

        assertThat(order.items).containsExactly(replacement)
    }

    @Test
    @DisplayName("a raised event is held until the aggregate is saved, and carries its identity")
    fun eventsAreHeldOnTheRoot() {
        val order = order("10.00")
        order.orderId = "ord-1"

        assertThat(order.domainEvents())
            .`as`("nothing has happened yet")
            .isEmpty()

        order.raisePlaced()
        order.raiseCancelled()

        val raised = order.domainEvents()
        assertThat(raised).hasSize(2)
        assertThat(raised.map { it::class.java })
            .containsExactly(OrderPlacedEvent::class.java, OrderCancelledEvent::class.java)
        assertThat((raised.first() as OrderPlacedEvent).orderId)
            .`as`("built at publication, so it carries the identity the row now has")
            .isEqualTo("ord-1")
        assertThat((raised.first() as OrderPlacedEvent).occurredOn).isNotNull()

        order.clearDomainEvents()
        assertThat(order.domainEvents())
            .`as`("published once; the next save has nothing to say")
            .isEmpty()
    }

    @Test
    @DisplayName("the factory is a place, and refuses to pretend it is more")
    fun theFactoryIsAPlace() {
        assertThatThrownBy { OrderFactory.place(java.time.LocalDateTime.now()) }
            .isInstanceOf(UnsupportedOperationException::class.java)
            .hasMessageContaining("write it")
    }

    /**
     * The list is the root's, and the root is the only thing that can swap it.
     *
     * This was the last gap between the two languages, and it looked like a
     * language constraint: "private set" is refused on an open property, and
     * a JPA entity's properties are open because the all-open plugin opens
     * them. The two turned out not to be exclusive - the plugin leaves an
     * explicit "final" alone - so the property is sealed and the class stays
     * open, which is what each side actually needed.
     *
     * Asserted through reflection because the compiler is the thing being
     * checked: if the setter comes back it becomes public and this fails,
     * which is the only way a generated modifier can be held down.
     */
    @Test
    @DisplayName("the members list cannot be replaced from outside the root")
    fun theCollectionSetterIsSealed() {
        val setter = Order::class.java.methods.firstOrNull { it.name == "setItems" }

        assertThat(setter)
            .`as`("handing out a setter for the members hands out the aggregate")
            .isNull()

        assertThat(Order::class.java.methods.map { it.name })
            .`as`("what is offered instead: one member in, one out, or all of them at once")
            .contains("addOrderItem", "removeOrderItem", "replaceItems")
    }

    private fun order(amount: String): Order {
        val order = Order()
        order.creation().totalAmount = BigDecimal(amount)
        return order
    }

    private fun item(): OrderItem {
        val item = OrderItem()
        item.itemSeq = 1
        item.quantity = 1
        item.unitPrice = BigDecimal("10.00")
        return item
    }
}
