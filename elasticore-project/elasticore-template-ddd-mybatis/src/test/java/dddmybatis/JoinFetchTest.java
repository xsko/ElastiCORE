package dddmybatis;

import io.elasticore.sample.dddmybatis.shop.application.OrderService;
import io.elasticore.sample.dddmybatis.shop.application.dto.OrderDTO;
import io.elasticore.sample.dddmybatis.shop.application.dto.OrderSrchDTO;
import io.elasticore.sample.dddmybatis.shop.domain.model.Customer;
import io.elasticore.sample.dddmybatis.shop.domain.model.Order;
import io.elasticore.sample.dddmybatis.shop.domain.model.OrderItem;
import io.elasticore.sample.dddmybatis.shop.domain.repository.CustomerRepositoryPort;
import io.elasticore.sample.dddmybatis.shop.domain.repository.OrderRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A page of aggregates is a fixed number of statements, whatever its size:
 * the count, the rows with the rows they point at (one join, so each order's
 * customer comes with it), and one statement per collection for all the rows
 * at once - not a statement per order.
 */
@SpringBootTest
@Transactional
class JoinFetchTest {

    private static final int ORDERS = 5;

    @Autowired private OrderService service;
    @Autowired private OrderRepositoryPort orders;
    @Autowired private CustomerRepositoryPort customers;
    @Autowired private DddMyBatisSampleApp.StatementCounter statements;

    @Test
    @DisplayName("the paged search reads each order's customer in the same statement")
    void theCustomerIsReadWithTheOrder() {
        String tag = "join-" + System.nanoTime();
        for (int i = 0; i < ORDERS; i++) {
            Customer c = new Customer();
            c.creation().setFirstName("Customer" + i);
            c.creation().setLastName("Fetch");
            c.creation().setEmail(String.format("fetch%d-%d@example.com", i, System.nanoTime()));
            customers.save(c);

            Order order = new Order();
            order.creation().setOrderDate(LocalDateTime.now());
            order.creation().setTotalAmount(new BigDecimal("14.49"));
            order.creation().setCustomerId(c.getCustomerId());
            order.setNotes(tag);
            OrderItem item = new OrderItem();
            item.setItemSeq(1);
            item.setQuantity(1);
            item.setUnitPrice(new BigDecimal("14.49"));
            order.addOrderItem(item);
            orders.save(order);
        }

        OrderSrchDTO search = new OrderSrchDTO();
        search.setPageSize(50);
        statements.count.set(0);
        Page<OrderDTO> page = service.findBySearch(search);
        int used = statements.count.get();

        List<OrderDTO> mine = new ArrayList<OrderDTO>();
        for (OrderDTO dto : page.getContent()) {
            if (tag.equals(dto.getNotes())) mine.add(dto);
        }
        assertThat(mine).hasSize(ORDERS);
        assertThat(mine).allSatisfy(dto -> assertThat(dto.getCustomerFirstName()).startsWith("Customer"));
        assertThat(mine).allSatisfy(dto -> assertThat(dto.getItems()).hasSize(1));
        // The count, the page, and the order's one collection (its lines).
        assertThat(used).as("statements for one page of %d orders: %s", ORDERS, statements.sql).isEqualTo(3);
    }
}
