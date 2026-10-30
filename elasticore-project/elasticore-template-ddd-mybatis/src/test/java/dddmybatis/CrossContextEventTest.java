package dddmybatis;

import io.elasticore.sample.dddmybatis.billing.application.ReceiptingProcessManager;
import io.elasticore.sample.dddmybatis.shop.domain.event.OrderPlacedEvent;
import io.elasticore.sample.dddmybatis.shop.domain.model.Customer;
import io.elasticore.sample.dddmybatis.shop.domain.model.Order;
import io.elasticore.sample.dddmybatis.shop.domain.model.OrderItem;
import io.elasticore.sample.dddmybatis.shop.domain.repository.CustomerRepositoryPort;
import io.elasticore.sample.dddmybatis.shop.domain.repository.OrderRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * One bounded context reacting to another's event.
 *
 * billing/model/domainservice.yml declares {@code on: [shop:OrderPlaced]};
 * billing's env.yml says it uses shop, and shop's says it publishes the
 * event. The generated ReceiptingProcessManager is billing's code, and it
 * hears shop's order being placed - after shop's transaction committed, and
 * only then.
 */
@SpringBootTest
class CrossContextEventTest {

    @Autowired private TransactionTemplate tx;
    @Autowired private OrderRepositoryPort orders;
    @Autowired private CustomerRepositoryPort customers;
    @SpyBean private ReceiptingProcessManager receipting;

    private String placeOrder(boolean rollback) {
        return tx.execute(status -> {
            Customer customer = new Customer();
            customer.creation().setFirstName("Kim");
            customer.creation().setLastName("Lee");
            customer.creation().setEmail(String.format("kim%d@example.com", System.nanoTime()));
            customers.save(customer);

            Order order = new Order();
            order.creation().setOrderDate(LocalDateTime.now());
            order.creation().setTotalAmount(new BigDecimal("42.50"));
            order.creation().setCustomerId(customer.getCustomerId());
            OrderItem item = new OrderItem();
            item.setItemSeq(1);
            item.setQuantity(1);
            item.setUnitPrice(new BigDecimal("42.50"));
            order.addOrderItem(item);
            order.raisePlaced();
            String id = orders.save(order).getOrderId();
            if (rollback) status.setRollbackOnly();
            return id;
        });
    }

    @Test
    @DisplayName("billing hears shop's OrderPlaced once shop has committed, with what the event carries")
    void billingHearsShop() {
        clearInvocations(receipting);
        String orderId = placeOrder(false);

        ArgumentCaptor<OrderPlacedEvent> heard = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(receipting, times(1)).on(heard.capture());
        assertThat(heard.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(heard.getValue().getTotalAmount()).isEqualByComparingTo("42.50");
    }

    @Test
    @DisplayName("an order shop rolled back is never heard by billing")
    void rolledBackIsNotHeard() {
        clearInvocations(receipting);
        placeOrder(true);
        verify(receipting, never()).on(any(OrderPlacedEvent.class));
    }

    @Test
    @DisplayName("the context map draws the event crossing from shop to billing")
    void contextMapDrawsIt() throws Exception {
        String map = new String(Files.readAllBytes(Paths.get("src/main/resources/blueprint/context-map.md")),
                StandardCharsets.UTF_8);
        assertThat(map).contains("ctx_shop -->|OrderPlaced| ctx_billing");
        assertThat(map).contains("| shop |  | OrderPlaced |");
    }
}
