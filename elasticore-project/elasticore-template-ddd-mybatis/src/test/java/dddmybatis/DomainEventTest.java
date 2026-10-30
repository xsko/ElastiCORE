package dddmybatis;

import io.elasticore.sample.dddmybatis.shop.domain.event.OrderCancelledEvent;
import io.elasticore.sample.dddmybatis.shop.domain.event.OrderPlacedEvent;
import io.elasticore.sample.dddmybatis.shop.domain.model.Customer;
import io.elasticore.sample.dddmybatis.shop.domain.model.Order;
import io.elasticore.sample.dddmybatis.shop.domain.model.OrderItem;
import io.elasticore.sample.dddmybatis.shop.domain.repository.CustomerRepositoryPort;
import io.elasticore.sample.dddmybatis.shop.domain.repository.OrderRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the aggregate said, heard by something outside it.
 *
 * The rest of the DDD rules cut the write path between aggregates; an event
 * is what carries the fact across afterwards, so this is the half of the
 * boundary that has to be checked by publishing one rather than by reading
 * the source. Three things can only be established here: that the event
 * reaches a listener at all, that it reaches one only when the row was
 * actually written, and that it is not delivered twice.
 */
@SpringBootTest
@Transactional
@RecordApplicationEvents
class DomainEventTest {

    @Autowired
    private OrderRepositoryPort orders;

    @Autowired
    private CustomerRepositoryPort customers;

    @Autowired
    private ApplicationEvents events;

    @Test
    @DisplayName("an event the root raised is published when the root is saved")
    void raisedEventIsPublishedOnSave() {
        Order order = newOrder();
        order.raisePlaced();

        orders.save(order);

        assertThat(events.stream(OrderPlacedEvent.class))
                .as("the aggregate said it happened; saving it is what published that")
                .hasSize(1)
                .allSatisfy(e -> assertThat(e.getOrderId()).isEqualTo(order.getOrderId()));
        assertThat(events.stream(OrderPlacedEvent.class).findFirst().orElseThrow().getOccurredOn())
                .as("an event says when it happened")
                .isNotNull();
    }

    @Test
    @DisplayName("an aggregate saved without raising anything publishes nothing")
    void nothingRaisedIsNothingPublished() {
        orders.save(newOrder());

        assertThat(events.stream(OrderPlacedEvent.class))
                .as("a save is not an event; the domain decides what one is")
                .isEmpty();
    }

    @Test
    @DisplayName("an event is published once, however often the root is saved after")
    void publishedEventIsNotPublishedAgain() {
        Order order = newOrder();
        order.raiseCancelled();

        orders.save(order);
        order.setNotes("changed after the event was published");
        orders.save(order);

        assertThat(events.stream(OrderCancelledEvent.class))
                .as("the root cleared what had been published; the second save had nothing to say")
                .hasSize(1);
    }

    private Order newOrder() {
        Customer customer = new Customer();
        customer.creation().setFirstName("Dana");
        customer.creation().setLastName("Reed");
        customer.creation().setEmail(String.format("dana%d@example.com", System.nanoTime()));
        customers.save(customer);

        Order order = new Order();
        order.creation().setOrderDate(LocalDateTime.now());
        order.creation().setTotalAmount(new BigDecimal("19.99"));
        order.creation().setCustomerId(customer.getCustomerId());
        // An order with no line is not a smaller order; the aggregate says so
        // itself now, and refuses to be written without one.
        order.addOrderItem(newItem());
        return order;
    }

    private OrderItem newItem() {
        OrderItem item = new OrderItem();
        item.setItemSeq(1);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("19.99"));
        return item;
    }
}
