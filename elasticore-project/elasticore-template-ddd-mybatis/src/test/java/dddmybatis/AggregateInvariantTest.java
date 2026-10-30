package dddmybatis;

import io.elasticore.sample.dddmybatis.shop.domain.model.Customer;
import io.elasticore.sample.dddmybatis.shop.domain.model.Order;
import io.elasticore.sample.dddmybatis.shop.domain.model.OrderItem;
import io.elasticore.sample.dddmybatis.shop.domain.model.Product;
import io.elasticore.sample.dddmybatis.shop.domain.repository.CustomerRepositoryPort;
import io.elasticore.sample.dddmybatis.shop.domain.repository.OrderRepositoryPort;
import io.elasticore.sample.dddmybatis.shop.domain.repository.ProductRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the aggregate promised, refused when it is not true.
 *
 * The other tests keep their orders valid, which shows the promise can be
 * kept; it does not show it is enforced, and a check that never fails is
 * indistinguishable from one that is not wired up. So this one breaks it on
 * purpose, on both forms the DSL can state: a collection that must have
 * members, and a number that must be above a bound.
 *
 * Saved rather than merely constructed. The check runs when the row is
 * written, so an invalid aggregate is a legal object right up to the moment
 * something tries to make it a fact - which is the point, since that is the
 * only moment every path to the database has in common.
 */
@SpringBootTest
@Transactional
class AggregateInvariantTest {

    @Autowired
    private OrderRepositoryPort orders;

    @Autowired
    private CustomerRepositoryPort customers;

    @Autowired
    private ProductRepositoryPort products;

    @Test
    @DisplayName("an order with no line cannot be written")
    void anOrderWithoutLinesIsRefused() {
        Order order = newOrder();

        assertThatThrownBy(() -> orders.save(order))
                .as("the aggregate said an order has at least one line")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("an order has at least one line");
    }

    @Test
    @DisplayName("the same order is written once it has one")
    void anOrderWithALineIsAccepted() {
        Order order = newOrder();
        order.addOrderItem(newItem());

        orders.save(order);

        assertThat(order.getOrderId())
                .as("nothing else about the order changed; only the promise it now keeps")
                .isNotEmpty();
    }

    /**
     * The edge that used to escape, now closed.
     *
     * Removing the last member deletes the member's row and leaves the
     * root's untouched, so the root's own listener never ran and the order
     * was left with no lines. This test stated that as a limitation and said
     * it would fail when there was a way to make the root's write follow its
     * members'. There is: the member carries a listener that checks the
     * root's promises when its row is deleted, at flush - after a replace
     * has refilled the collection, which is why swapping lines still works
     * (replacingEveryLineIsStillAllowed).
     */
    @Test
    @DisplayName("removing the last line is caught when the line's row is deleted")
    void emptyingAnOrderIsCaught() {
        Order order = newOrder();
        OrderItem item = newItem();
        order.addOrderItem(item);
        orders.save(order);

        order.removeOrderItem(item);

        assertThatThrownBy(() -> orders.save(order))
                .as("the order may not be left without the line it promised to have")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("an order has at least one line");
    }

    @Test
    @DisplayName("a root being deleted may lose its last line on the way out")
    void deletingTheRootIsNotRefused() {
        Order order = newOrder();
        OrderItem item = newItem();
        order.addOrderItem(item);
        orders.save(order);

        // The last line removed, then the order itself: the lines leave with
        // their root, and the root's promise no longer has anything to hold.
        order.removeOrderItem(item);
        orders.delete(order);

        assertThat(orders.findById(order.getOrderId())).isEmpty();
    }

    @Test
    @DisplayName("replacing every line at once is still allowed")
    void replacingEveryLineIsStillAllowed() {
        Order order = newOrder();
        order.addOrderItem(newItem());
        orders.save(order);

        order.replaceItems(java.util.List.of(newItem(), newItem()));
        orders.save(order);

        assertThat(orders.findById(order.getOrderId()).orElseThrow().getItems()).hasSize(2);
    }

    @Test
    @DisplayName("changing the root as well brings the check back")
    void emptyingAnOrderIsCaughtWhenTheRootChangesToo() {
        Order order = newOrder();
        OrderItem item = newItem();
        order.addOrderItem(item);
        orders.save(order);

        order.removeOrderItem(item);
        order.setNotes("and something about the order itself");

        assertThatThrownBy(() -> orders.save(order))
                .as("now there is an update on the root, and the promise is checked on it")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("an order has at least one line");
    }

    @Test
    @DisplayName("a number below its bound is refused")
    void aProductWithoutAPriceIsRefused() {
        Product product = newProduct(BigDecimal.ZERO);

        assertThatThrownBy(() -> products.save(product))
                .as("the numeric form of the same statement, compared the way BigDecimal has to be")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("a product has a price");
    }

    @Test
    @DisplayName("the same product is written once the number is above it")
    void aPricedProductIsAccepted() {
        products.save(newProduct(new BigDecimal("0.01")));
    }

    private Order newOrder() {
        Customer customer = new Customer();
        customer.creation().setFirstName("Edsger");
        customer.creation().setLastName("Dijkstra");
        customer.creation().setEmail(String.format("edsger%d@example.com", System.nanoTime()));
        customers.save(customer);

        Order order = new Order();
        order.creation().setOrderDate(LocalDateTime.now());
        order.creation().setTotalAmount(new BigDecimal("7.50"));
        order.creation().setCustomerId(customer.getCustomerId());
        return order;
    }

    private OrderItem newItem() {
        OrderItem item = new OrderItem();
        item.setItemSeq(1);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("7.50"));
        return item;
    }

    private Product newProduct(BigDecimal price) {
        Product product = new Product();
        product.setName("Analytical Engine");
        product.setSku(String.format("SKU%d", System.nanoTime()));
        product.setPrice(price);
        return product;
    }
}
