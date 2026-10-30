package dddmybatis;

import io.elasticore.sample.dddmybatis.shop.application.LoyaltyProcessManager;
import io.elasticore.sample.dddmybatis.shop.infrastructure.persistence.ShopOutboxRelay;
import io.elasticore.sample.dddmybatis.shop.application.dto.CustomerOrderCountDTO;
import io.elasticore.sample.dddmybatis.shop.application.port.out.OrderQueryPort;
import io.elasticore.sample.dddmybatis.shop.domain.event.OrderPlacedEvent;
import io.elasticore.sample.dddmybatis.shop.domain.model.Customer;
import io.elasticore.sample.dddmybatis.shop.domain.model.Order;
import io.elasticore.sample.dddmybatis.shop.domain.model.OrderItem;
import io.elasticore.sample.dddmybatis.shop.domain.repository.CustomerRepositoryPort;
import io.elasticore.sample.dddmybatis.shop.domain.repository.OrderRepositoryPort;
import io.elasticore.springboot3.outbox.OutboxEnvelope;
import io.elasticore.springboot3.outbox.OutboxPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What an event carries, how it leaves (outbox), what reacts to it (process
 * manager), and the read side (a projection typed as its DTO) - generated
 * from the shop DSL and run.
 */
@SpringBootTest
@Import(DddExtrasTest.Broker.class)
class DddExtrasTest {

    @TestConfiguration
    static class Broker {
        static final List<OutboxEnvelope> sent = new CopyOnWriteArrayList<>();

        @Bean
        OutboxPublisher publisher() {
            return sent::add;
        }
    }

    @Autowired private TransactionTemplate tx;
    @Autowired private OrderRepositoryPort orders;
    @Autowired private CustomerRepositoryPort customers;
    @Autowired private ShopOutboxRelay relay;
    @Autowired private OrderQueryPort orderQuery;
    @Autowired private LoyaltyProcessManager loyalty;

    private String placeOrder() {
        return tx.execute(status -> {
            Customer customer = new Customer();
            customer.creation().setFirstName("Dana");
            customer.creation().setLastName("Reed");
            customer.creation().setEmail(String.format("dana%d@example.com", System.nanoTime()));
            customers.save(customer);

            Order order = new Order();
            order.creation().setOrderDate(LocalDateTime.now());
            order.creation().setTotalAmount(new BigDecimal("19.99"));
            order.creation().setCustomerId(customer.getCustomerId());
            OrderItem item = new OrderItem();
            item.setItemSeq(1);
            item.setQuantity(1);
            item.setUnitPrice(new BigDecimal("19.99"));
            order.addOrderItem(item);
            order.raisePlaced();
            return orders.save(order).getOrderId();
        });
    }

    @Test
    @DisplayName("the event is recorded with the order and relayed, carrying what @event named")
    void outboxAndPayload() {
        String id = placeOrder();
        assertThat(relay.pending()).isPositive();
        assertThat(relay.relay()).isPositive();

        OutboxEnvelope placed = Broker.sent.stream()
                .filter(m -> id.equals(m.getAggregateId())).findFirst().orElseThrow();
        assertThat(placed.getEventType()).isEqualTo(OrderPlacedEvent.class.getName());
        assertThat(placed.getAggregateType()).isEqualTo("Order");
        assertThat(placed.getPayload()).contains("\"totalAmount\":19.99");
        assertThat(relay.pending()).isZero();
    }

    @Test
    @DisplayName("the process manager is a bean that reacts to the event after commit")
    void processManagerIsWired() throws Exception {
        assertThat(loyalty).isNotNull();
        assertThat(LoyaltyProcessManager.class.getMethod("on", OrderPlacedEvent.class)
                .isAnnotationPresent(TransactionalEventListener.class)).isTrue();
        placeOrder();
    }

    @Test
    @DisplayName("a projection query answers with its DTO through the query port")
    void projection() {
        placeOrder();
        List<CustomerOrderCountDTO> rows = orderQuery.countByCustomer();
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getCustomerId()).isNotNull();
            assertThat(r.getOrderCount()).isPositive();
        });
    }
}
