package dddmybatis;

import io.elasticore.sample.dddmybatis.shop.application.dto.OrderDTO;
import io.elasticore.sample.dddmybatis.shop.application.dto.ShopMapper;
import io.elasticore.sample.dddmybatis.shop.domain.model.Customer;
import io.elasticore.sample.dddmybatis.shop.domain.model.Order;
import io.elasticore.sample.dddmybatis.shop.domain.model.OrderItem;
import io.elasticore.sample.dddmybatis.shop.domain.repository.CustomerRepositoryPort;
import io.elasticore.sample.dddmybatis.shop.domain.repository.OrderRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The aggregate boundary, checked against a database rather than a compiler,
 * through the domain's ports - over MyBatis, the generated adapter does what
 * JPA's cascade and orphan removal did: a member added through the root is
 * inserted with it, one removed is deleted, another aggregate is never
 * written through.
 */
@SpringBootTest
@Transactional
class AggregateBoundaryTest {

    @Autowired
    private OrderRepositoryPort orders;

    @Autowired
    private CustomerRepositoryPort customers;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("a member added through the root is persisted with it")
    void memberIsPersistedThroughItsRoot() {
        Order order = newOrder(persistedCustomer());
        order.addOrderItem(newItem(1, "9.99"));
        order.addOrderItem(newItem(2, "4.50"));

        orders.save(order);

        Order reloaded = orders.findById(order.getOrderId()).orElseThrow();
        assertThat(reloaded.getItems())
                .as("the root was saved; its members had to be saved with it")
                .hasSize(2);
        assertThat(reloaded.getItems())
                .allSatisfy(item -> assertThat(item.getOrder().getOrderId())
                        .as("a member knows the root it belongs to")
                        .isEqualTo(order.getOrderId()));
    }

    @Test
    @DisplayName("a member removed through the root is deleted, not orphaned")
    void memberIsRemovedThroughItsRoot() {
        Order order = newOrder(persistedCustomer());
        order.addOrderItem(newItem(1, "9.99"));
        order.addOrderItem(newItem(2, "4.50"));
        orders.save(order);

        Order loaded = orders.findById(order.getOrderId()).orElseThrow();
        String kept = loaded.getItems().get(1).getOrderItemId();
        loaded.removeOrderItem(loaded.getItems().get(0));
        orders.save(loaded);

        Order reloaded = orders.findById(order.getOrderId()).orElseThrow();
        assertThat(reloaded.getItems())
                .as("the root no longer holds it")
                .hasSize(1);
        assertThat(reloaded.getItems().get(0).getOrderItemId())
                .as("the member it kept is the same row, updated in place")
                .isEqualTo(kept);
        // The collection cannot say whether the row was deleted or orphaned. The table can.
        assertThat(jdbc.queryForObject("select count(*) from shop_order_item where order_order_id = ?", Long.class,
                order.getOrderId()))
                .as("removed through the root means gone, not orphaned")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("saving an order does not modify the customer it references")
    void savingAnOrderDoesNotWriteThroughToAnotherAggregate() {
        Customer customer = persistedCustomer();
        Order order = newValidOrder(customer);
        orders.save(order);

        // A DTO carrying the other aggregate's field, changed.
        OrderDTO dto = new OrderDTO();
        dto.setOrderId(order.getOrderId());
        dto.setNotes("shipped");
        dto.setCustomerFirstName("OVERWRITTEN");

        Order managed = orders.findById(order.getOrderId()).orElseThrow();
        ShopMapper.mapping(dto, managed, true);
        orders.save(managed);

        assertThat(orders.findById(order.getOrderId()).orElseThrow().getNotes())
                .as("the order itself is still written")
                .isEqualTo("shipped");
        assertThat(customers.findById(customer.getCustomerId()).orElseThrow().getFirstName())
                .as("one transaction, one aggregate")
                .isEqualTo("Ada");
    }

    @Test
    @DisplayName("another aggregate is referenced by its identity and read through a read-only join")
    void anotherAggregateIsReferencedById() throws Exception {
        assertThat(io.elasticore.sample.dddmybatis.shop.domain.model.OrderBase.class.getDeclaredField("customerId").getType())
                .isEqualTo(Long.class);
        assertThat(java.util.Arrays.stream(Order.class.getMethods()).map(java.lang.reflect.Method::getName))
                .contains("getCustomer", "getCustomerId")
                .doesNotContain("setCustomer");

        Customer customer = persistedCustomer();
        Order order = newValidOrder(customer);
        orders.save(order);

        Order reloaded = orders.findById(order.getOrderId()).orElseThrow();
        assertThat(reloaded.getCustomerId()).isEqualTo(customer.getCustomerId());
        assertThat(reloaded.getCustomer().getFirstName())
                .as("the same column, joined read-only: reads still see the customer")
                .isEqualTo("Ada");
    }

    @Test
    @DisplayName("a stale aggregate is refused, not written over")
    // Two requests, not one transaction: inside one, MyBatis hands the same row back.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void aStaleAggregateIsRefused() {
        Order order = newValidOrder(persistedCustomer());
        orders.save(order);
        Order first = orders.findById(order.getOrderId()).orElseThrow();
        Order second = orders.findById(order.getOrderId()).orElseThrow();
        first.setNotes("first");
        orders.save(first);
        assertThat(first.getVersion()).as("the version counted up in memory too").isEqualTo(second.getVersion() + 1);

        second.setNotes("second");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> orders.save(second))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    }

    // ---- fixtures ---------------------------------------------------------

    private Customer persistedCustomer() {
        Customer c = new Customer();
        c.creation().setFirstName("Ada");
        c.creation().setLastName("Lovelace");
        c.creation().setEmail(String.format("ada%d@example.com", System.nanoTime()));
        return customers.save(c);
    }

    private Order newOrder(Customer customer) {
        Order order = new Order();
        order.creation().setOrderDate(LocalDateTime.now());
        order.creation().setTotalAmount(new BigDecimal("14.49"));
        order.creation().setCustomerId(customer.getCustomerId());
        return order;
    }

    /** The same order, already valid: the aggregate promises it has at least one line. */
    private Order newValidOrder(Customer customer) {
        Order order = newOrder(customer);
        order.addOrderItem(newItem(1, "14.49"));
        return order;
    }

    private OrderItem newItem(int seq, String unitPrice) {
        OrderItem item = new OrderItem();
        item.setItemSeq(seq);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal(unitPrice));
        return item;
    }
}
