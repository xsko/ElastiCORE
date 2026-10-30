package dddmybatis;

import io.elasticore.runtime.port.DbmsService;
import io.elasticore.sample.dddmybatis.shop.application.dto.CustomerRevenueDTO;
import io.elasticore.sample.dddmybatis.shop.application.dto.OrderTotalsDTO;
import io.elasticore.sample.dddmybatis.shop.application.dto.RevenueSearchInput;
import io.elasticore.sample.dddmybatis.shop.application.port.out.ShopReportService;
import io.elasticore.sample.dddmybatis.shop.domain.model.Customer;
import io.elasticore.sample.dddmybatis.shop.domain.model.Order;
import io.elasticore.sample.dddmybatis.shop.domain.model.OrderItem;
import io.elasticore.sample.dddmybatis.shop.domain.repository.CustomerRepositoryPort;
import io.elasticore.sample.dddmybatis.shop.domain.repository.OrderRepositoryPort;
import io.elasticore.springboot3.dbms.DbmsServiceProxyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The outbound side of the hexagon, driven for real.
 *
 * The aggregate answers writes; a view that spans aggregates - revenue by
 * customer is Order data grouped by Customer data - is a read model, and the
 * dbms port is how one is declared. This test is the proof that the declared
 * port is a working program: the writes go in through the domain model, the
 * report comes out through the generated interface, and the two agree.
 *
 * The proxy is built directly from the factory the runtime uses, so what is
 * exercised is the real invocation path - the YAML the @DbmsService
 * annotation names is read from the classpath, the SQL runs on the same H2
 * the aggregates persist to.
 */
@SpringBootTest
@Transactional
class HexagonalPortTest {

    @Autowired
    private CustomerRepositoryPort customers;

    @Autowired
    private OrderRepositoryPort orders;

    @Autowired
    private BeanFactory beanFactory;

    @Autowired
    private Environment environment;

    /**
     * The port, injected.
     *
     * It used to be built here from the factory, which proved the proxy works
     * and not that anything could be given one. A port that no container can
     * hand out is an interface, not a port - and that is exactly what the
     * first attempt at letting a service depend on one ran into: "No
     * qualifying bean of type ShopReportService".
     *
     * Registering it is one @Bean method, in DddSampleApp beside the other
     * runtime pieces. Asking for it here is what proves the registration.
     */
    @Autowired
    private ShopReportService reports;

    @Test
    @DisplayName("what the aggregates wrote, the read model reports")
    void theReadModelSeesWhatTheDomainWrote() {
        OrderTotalsDTO before = reports.orderTotals();
        Customer ada = customer("Ada", "Lovelace", "ada.port@example.com");
        Customer grace = customer("Grace", "Hopper", "grace.port@example.com");

        order(ada, "10.00");
        order(ada, "5.50");
        order(grace, "100.00");

        // A cross-aggregate view, computed where it belongs: in SQL, not by
        // loading two aggregates and walking them.
        // The rows of this test's two customers: other tests in the same
        // context commit customers of their own.
        List<CustomerRevenueDTO> revenue = new java.util.ArrayList<CustomerRevenueDTO>();
        for (CustomerRevenueDTO r : reports.revenueByCustomer(new RevenueSearchInput())) {
            if (r.getCustomerId().equals(ada.getCustomerId()) || r.getCustomerId().equals(grace.getCustomerId())) revenue.add(r);
        }
        assertThat(revenue).hasSize(2);
        assertThat(revenue.get(0).getLastName()).isEqualTo("Hopper");
        assertThat(revenue.get(0).getTotalRevenue()).isEqualByComparingTo("100.00");
        assertThat(revenue.get(1).getOrderCount()).isEqualTo(2L);
        assertThat(revenue.get(1).getTotalRevenue()).isEqualByComparingTo("15.50");

        OrderTotalsDTO totals = reports.orderTotals();
        assertThat(totals.getOrderCount() - before.getOrderCount()).isEqualTo(3L);
        assertThat(totals.getRevenue().subtract(before.getRevenue())).isEqualByComparingTo("115.50");
    }

    @Test
    @DisplayName("the conditional SQL block follows the input")
    void conditionalSqlFollowsTheInput() {
        order(customer("Ada", "Lovelace", "ada.cond@example.com"), "10.00");
        order(customer("Grace", "Hopper", "grace.cond@example.com"), "20.00");

        RevenueSearchInput byName = new RevenueSearchInput();
        byName.setLastName("Hopper");

        List<CustomerRevenueDTO> filtered = reports.revenueByCustomer(byName);
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getFirstName()).isEqualTo("Grace");
    }

    @Test
    @DisplayName("in a MyBatis domain the port is a generated MyBatis adapter, not the runtime's proxy")
    void thePortIsAGeneratedAdapter() {
        assertThat(reports).isInstanceOf(
                io.elasticore.sample.dddmybatis.shop.infrastructure.persistence.ShopReportServiceAdapter.class);
    }

    @Test
    @DisplayName("a page of the read model is a count and a page of rows")
    void aPageOfTheReadModel() {
        String tag = "Pager" + System.nanoTime();
        for (int i = 0; i < 3; i++) customer("P" + i, tag, "pager" + i + System.nanoTime() + "@example.com");

        RevenueSearchInput input = new RevenueSearchInput();
        input.setLastName(tag);
        org.springframework.data.domain.Page<CustomerRevenueDTO> page = reports.customerPage(input);
        assertThat(page.getTotalElements()).isEqualTo(3L);
        assertThat(page.getContent()).hasSize(3).allSatisfy(r -> assertThat(r.getLastName()).isEqualTo(tag));
    }

    @Test
    @DisplayName("a write through the port changes the rows it names and answers how many")
    void aWriteThroughThePort() {
        Customer c = customer("Wren", "Writer", "wren" + System.nanoTime() + "@example.com");
        assertThat(reports.updateCustomerPhone(c.getCustomerId(), "010-1234")).isEqualTo(1);
        assertThat(customers.findById(c.getCustomerId()).orElseThrow().getPhone()).isEqualTo("010-1234");
    }

    // ---- fixtures ---------------------------------------------------------

    private Customer customer(String first, String last, String email) {
        Customer c = new Customer();
        c.creation().setFirstName(first);
        c.creation().setLastName(last);
        c.creation().setEmail(email);
        return customers.save(c);
    }

    private void order(Customer customer, String amount) {
        Order o = new Order();
        o.creation().setOrderDate(LocalDateTime.now());
        o.creation().setTotalAmount(new BigDecimal(amount));
        o.creation().setCustomerId(customer.getCustomerId());
        // The aggregate refuses to be written without a line, so the read
        // model is fed orders that are actually orders.
        OrderItem item = new OrderItem();
        item.setItemSeq(1);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal(amount));
        o.addOrderItem(item);
        orders.save(o);
    }
}
