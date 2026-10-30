package dddmybatis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.elasticore.sample.dddmybatis.shop.application.dto.CustomerOrderCountDTO;
import io.elasticore.sample.dddmybatis.shop.application.dto.CustomerRevenueDTO;
import io.elasticore.sample.dddmybatis.shop.application.dto.RevenueSearchInput;
import io.elasticore.sample.dddmybatis.shop.application.port.out.OrderQueryPort;
import io.elasticore.sample.dddmybatis.shop.application.port.out.ProductQueryPort;
import io.elasticore.sample.dddmybatis.shop.application.port.out.ShopReportService;
import io.elasticore.sample.dddmybatis.shop.domain.model.Customer;
import io.elasticore.sample.dddmybatis.shop.domain.model.Order;
import io.elasticore.sample.dddmybatis.shop.domain.model.OrderItem;
import io.elasticore.sample.dddmybatis.shop.domain.model.Product;
import io.elasticore.sample.dddmybatis.shop.domain.model.VipProfile;
import io.elasticore.sample.dddmybatis.shop.domain.repository.CustomerRepositoryPort;
import io.elasticore.sample.dddmybatis.shop.domain.repository.OrderRepositoryPort;
import io.elasticore.sample.dddmybatis.shop.domain.repository.ProductRepositoryPort;
import io.elasticore.sample.dddmybatis.shop.infrastructure.persistence.ShopOutboxRelay;
import io.elasticore.springboot3.outbox.OutboxEnvelope;
import io.elasticore.springboot3.outbox.OutboxPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The domain model over MyBatis on a real database - the SQL each database
 * reads its own way: the aggregate written with its members and compared
 * with the rows stored, the version, the one-to-one member, the outbox's
 * row locks, the declared queries, the dbms port, a page, every column type.
 * Each subclass is one database, in a schema made for the run from the DDL
 * the generator wrote for it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DomainOnDatabase.Broker.class)
abstract class DomainOnDatabase {

    @TestConfiguration
    static class Broker {
        static final List<OutboxEnvelope> sent = new CopyOnWriteArrayList<>();

        @Bean
        OutboxPublisher publisher() {
            return sent::add;
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderRepositoryPort orders;
    @Autowired CustomerRepositoryPort customers;
    @Autowired ProductRepositoryPort products;
    @Autowired ProductQueryPort productQuery;
    @Autowired OrderQueryPort orderQuery;
    @Autowired ShopReportService reports;
    @Autowired ShopOutboxRelay relay;

    @Test
    @DisplayName("an aggregate is written with its members, compared with the rows stored, and deleted with them")
    void anAggregateAndItsMembers() {
        Order order = newOrder(customer("Ada", "Lovelace"));
        order.addOrderItem(item(1, "9.99"));
        order.addOrderItem(item(2, "4.50"));
        orders.save(order);

        Order loaded = orders.findById(order.getOrderId()).orElseThrow();
        assertThat(loaded.getItems()).hasSize(2);
        String kept = loaded.getItems().get(1).getOrderItemId();
        loaded.removeOrderItem(loaded.getItems().get(0));
        loaded.addOrderItem(item(3, "1.00"));
        orders.save(loaded);

        Order reloaded = orders.findById(order.getOrderId()).orElseThrow();
        assertThat(reloaded.getItems()).hasSize(2);
        assertThat(reloaded.getItems().stream().map(OrderItem::getOrderItemId)).contains(kept);
        assertThat(jdbc.queryForObject("select count(*) from shop_order_item where order_order_id = ?", Long.class,
                order.getOrderId())).isEqualTo(2L);

        orders.delete(reloaded);
        assertThat(orders.findById(order.getOrderId())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from shop_order_item where order_order_id = ?", Long.class,
                order.getOrderId())).isZero();
    }

    @Test
    @DisplayName("a stale copy is not deleted, and the version counts up with each save")
    void aStaleDeleteIsRefused() {
        Order order = newOrder(customer("Edsger", "Dijkstra"));
        order.addOrderItem(item(1, "5.00"));
        orders.save(order);
        Order stale = orders.findById(order.getOrderId()).orElseThrow();
        Order fresh = orders.findById(order.getOrderId()).orElseThrow();
        fresh.setNotes("moved on");
        orders.save(fresh);
        assertThatThrownBy(() -> orders.delete(stale))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        assertThat(orders.existsById(order.getOrderId())).isTrue();

        // Saved twice in one transaction: the second compares the version the first left.
        tx.executeWithoutResult(s -> {
            Order twice = orders.findById(order.getOrderId()).orElseThrow();
            twice.setNotes("once");
            orders.save(twice);
            twice.setNotes("twice");
            orders.save(twice);
        });
        assertThat(orders.findById(order.getOrderId()).orElseThrow().getNotes()).isEqualTo("twice");
    }

    @Test
    @DisplayName("derived queries: a value's own % as written, an empty list, an order by name, a delete row by row")
    void derivedQueries() {
        String tag = Long.toString(System.nanoTime(), 36);
        Product percent = new Product();
        percent.setName("50% off " + tag);
        percent.setSku("A" + tag);
        percent.setPrice(new BigDecimal("1.00"));
        percent.setActive(true);
        products.save(percent);
        Product plain = new Product();
        plain.setName("500 off " + tag);
        plain.setSku("B" + tag);
        plain.setPrice(new BigDecimal("1.00"));
        plain.setActive(true);
        products.save(plain);

        assertThat(products.findByNameContaining("0% off " + tag)).extracting(Product::getSku).containsExactly("A" + tag);
        assertThat(products.findBySkuStartingWith("A" + tag)).extracting(Product::getSku).containsExactly("A" + tag);
        assertThat(products.findBySkuStartingWith("_" + tag)).as("_ is no wildcard").isEmpty();
        assertThat(products.findBySkuIn(java.util.Collections.emptyList())).isEmpty();
        assertThat(products.findBySkuIn(java.util.Arrays.asList("A" + tag, "B" + tag))).hasSize(2);
        List<String> skus = products.findAllByOrderBySkuDesc().stream().map(Product::getSku).collect(Collectors.toList());
        assertThat(skus.indexOf("B" + tag)).isLessThan(skus.indexOf("A" + tag));

        Order order = newOrder(customer("Barbara", "Liskov"));
        order.addOrderItem(item(1, "3.00"));
        io.elasticore.springboot3.mybatis.Rows.set(order, "trackingNumber", "T" + tag);
        orders.save(order);
        Long deleted = tx.execute(s -> orderQuery.deleteByTrackingNumber("T" + tag));
        assertThat(deleted).isEqualTo(1L);
        assertThat(orders.existsById(order.getOrderId())).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from shop_order_item where order_order_id = ?", Long.class,
                order.getOrderId())).as("its lines with it").isZero();
    }

    @Test
    @DisplayName("a stale aggregate is refused")
    void aStaleAggregateIsRefused() {
        Order order = newOrder(customer("Grace", "Hopper"));
        order.addOrderItem(item(1, "5.00"));
        orders.save(order);
        Order first = orders.findById(order.getOrderId()).orElseThrow();
        Order second = orders.findById(order.getOrderId()).orElseThrow();
        first.setNotes("first");
        orders.save(first);
        second.setNotes("second");
        assertThatThrownBy(() -> orders.save(second))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("a one-to-one member travels with its root and keeps its identity")
    void aOneToOneMember() throws Exception {
        String created = mvc.perform(post("/api/v1/shop/customer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Alan\",\"lastName\":\"Turing\",\"email\":\"alan" + System.nanoTime()
                                + "@example.com\",\"vipProfile\":{\"tier\":\"GOLD\",\"discountRate\":10.0}}"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).path("customerId").asLong();
        JsonNode profile = read("/api/v1/shop/customer/" + id).path("vipProfile");
        assertThat(profile.path("tier").asText()).as(created).isEqualTo("GOLD");
        long profileId = profile.path("profileId").asLong();

        mvc.perform(put("/api/v1/shop/customer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":" + id + ",\"vipProfile\":{\"tier\":\"PLATINUM\"}}"));
        JsonNode after = read("/api/v1/shop/customer/" + id).path("vipProfile");
        assertThat(after.path("tier").asText()).isEqualTo("PLATINUM");
        assertThat(after.path("profileId").asLong()).isEqualTo(profileId);
    }

    /**
     * Read and saved in one transaction, where MyBatis answers a second read
     * of the row from its session: the member the root held before must still
     * be the stored one, or the one replaced is never deleted.
     */
    @Test
    @DisplayName("a one-to-one member the root replaces or lets go is deleted")
    void aReplacedOneToOneMemberIsDeleted() {
        Customer c = new Customer();
        c.creation().setFirstName("Grace");
        c.creation().setLastName("Hopper");
        c.creation().setEmail("grace" + System.nanoTime() + "@example.com");
        VipProfile gold = new VipProfile();
        gold.setTier("GOLD");
        c.creation().setVipProfile(gold);
        Long id = customers.save(c).getCustomerId();
        Long first = gold.getProfileId();
        assertThat(profiles(first)).isEqualTo(1);

        Long second = tx.execute(s -> {
            Customer loaded = customers.findById(id).orElseThrow();
            VipProfile platinum = new VipProfile();
            platinum.setTier("PLATINUM");
            io.elasticore.springboot3.mybatis.Rows.set(loaded, "vipProfile", platinum);
            customers.save(loaded);
            return platinum.getProfileId();
        });
        assertThat(profiles(first)).as("the profile replaced").isZero();
        assertThat(profiles(second)).isEqualTo(1);

        tx.executeWithoutResult(s -> {
            Customer loaded = customers.findById(id).orElseThrow();
            io.elasticore.springboot3.mybatis.Rows.set(loaded, "vipProfile", null);
            customers.save(loaded);
        });
        assertThat(profiles(second)).as("the profile let go").isZero();

        // Replaced in memory and deleted without a save: the stored one goes too.
        VipProfile third = new VipProfile();
        third.setTier("SILVER");
        Customer again = customers.findById(id).orElseThrow();
        io.elasticore.springboot3.mybatis.Rows.set(again, "vipProfile", third);
        customers.save(again);
        Long stored = third.getProfileId();
        Customer replaced = customers.findById(id).orElseThrow();
        io.elasticore.springboot3.mybatis.Rows.set(replaced, "vipProfile", new VipProfile());
        customers.delete(replaced);
        assertThat(profiles(stored)).as("the stored profile of a deleted customer").isZero();
    }

    /** Read again in one transaction, as JPA's persistence context answers: what changed in memory stays. */
    @Test
    @DisplayName("an aggregate read again in its transaction keeps what changed in memory")
    void readAgainInItsTransaction() {
        Order order = newOrder(customer("Niklaus", "Wirth"));
        order.addOrderItem(item(1, "2.00"));
        order.addOrderItem(item(2, "3.00"));
        orders.save(order);
        tx.executeWithoutResult(s -> {
            Order first = orders.findById(order.getOrderId()).orElseThrow();
            first.removeOrderItem(first.getItems().get(0));
            Order second = orders.findById(order.getOrderId()).orElseThrow();
            assertThat(second.getItems()).hasSize(1);
        });
    }

    private long profiles(Long profileId) {
        return jdbc.queryForObject("select count(*) from shop_vip_profile where profile_id = ?", Long.class, profileId);
    }

    @Test
    @DisplayName("an event is recorded with the order and relayed through the outbox's row lock")
    void theOutbox() {
        String id = tx.execute(s -> {
            Order order = newOrder(customer("Dana", "Reed"));
            order.addOrderItem(item(1, "19.99"));
            order.raisePlaced();
            return orders.save(order).getOrderId();
        });
        assertThat(relay.pending()).isPositive();
        assertThat(relay.relay()).isPositive();
        assertThat(Broker.sent.stream().map(OutboxEnvelope::getAggregateId)).contains(id);
        assertThat(relay.pending()).isZero();
    }

    @Test
    @DisplayName("the declared queries run: derived, native, a page, a projection")
    void declaredQueries() {
        String run = Long.toString(System.nanoTime(), 36);
        product("DB1-" + run, "10.00", true);
        product("DB2-" + run, "25.00", true);
        product("DB3-" + run, "40.00", false);
        assertThat(products.findBySku("DB2-" + run).map(Product::getPrice).orElse(null)).isEqualByComparingTo("25.00");
        assertThat(products.findByPriceRange(new BigDecimal("20"), new BigDecimal("45")).stream()
                .map(Product::getSku).collect(Collectors.toList()))
                .contains("DB2-" + run, "DB3-" + run).doesNotContain("DB1-" + run);
        Page<Product> page = productQuery.findByActive(false, PageRequest.of(0, 50));
        assertThat(page.getContent().stream().map(Product::getSku).collect(Collectors.toList())).contains("DB3-" + run);

        Order order = newOrder(customer("Eve", "Count"));
        order.addOrderItem(item(1, "1.00"));
        orders.save(order);
        List<CustomerOrderCountDTO> counts = orderQuery.countByCustomer();
        assertThat(counts).isNotEmpty().allSatisfy(c -> assertThat(c.getOrderCount()).isPositive());
    }

    @Test
    @DisplayName("the dbms port runs on MyBatis: a filtered read model, a page, a write")
    void theDbmsPort() {
        String tag = "Port" + Long.toString(System.nanoTime(), 36);
        Customer c = customer("Paul", tag);
        Order order = newOrder(c);
        order.addOrderItem(item(1, "12.00"));
        orders.save(order);

        RevenueSearchInput byName = new RevenueSearchInput();
        byName.setLastName(tag);
        List<CustomerRevenueDTO> revenue = reports.revenueByCustomer(byName);
        assertThat(revenue).hasSize(1);
        assertThat(revenue.get(0).getTotalRevenue()).isEqualByComparingTo(order.getTotalAmount());

        assertThat(reports.customerPage(byName).getTotalElements()).isEqualTo(1L);

        // A page in no order of its own (SQL Server pages only ordered rows), one value per row, a NULL among them.
        customer("Mary", tag);
        Page<String> phones = reports.customerPhones(tag);
        assertThat(phones.getTotalElements()).isEqualTo(2L);
        assertThat(phones.getContent()).hasSize(2).containsOnlyNulls();

        assertThat(reports.updateCustomerPhone(c.getCustomerId(), "010-0000")).isEqualTo(1);
        assertThat(reports.customerPhones(tag).getContent()).containsExactlyInAnyOrder("010-0000", null);
        assertThat(reports.countOrdersByStatus("none")).isZero();
    }

    @Test
    @DisplayName("a search pages and sorts on this database")
    void aSearchPage() throws Exception {
        String run = Long.toString(System.nanoTime(), 36);
        for (int i = 0; i < 3; i++) product("PG" + i + "-" + run, (10 + i) + ".00", true);
        String body = mvc.perform(post("/api/v1/shop/product/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + run + "\",\"pageNumber\":0,\"pageSize\":2,\"sortCode\":\"price-\"}"))
                .andReturn().getResponse().getContentAsString();
        JsonNode page = json.readTree(body);
        assertThat(page.path("totalElements").asLong()).as(body).isEqualTo(3L);
        assertThat(page.path("content")).hasSize(2);
        assertThat(page.path("content").get(0).path("sku").asText()).as("highest price first").startsWith("PG2-");
    }

    @Test
    @DisplayName("every value type survives the request, the column and the reply")
    void everyType() throws Exception {
        byte[] image = {1, 2, 3, (byte) 0xFF};
        String body = "{\"label\":\"probe\",\"note\":\"a longer note\",\"rawText\":\"large text\","
                + "\"payload\":\"{\\\"k\\\":1}\",\"readCount\":42,\"totalCount\":9000000000,"
                + "\"bigCount\":123456789012345678901,\"units\":5,\"ratio\":12.34,\"exactValue\":1.5,\"amount\":99.95,"
                + "\"score\":0.25,\"weight\":1.5,\"active\":true,\"readDate\":\"2026-10-03\",\"readTime\":\"09:30:00\","
                + "\"localAt\":\"2026-10-03 09:30:00\",\"measuredAt\":\"2026-10-03T09:30:00+09:00\","
                + "\"image\":\"" + Base64.getEncoder().encodeToString(image) + "\"}";
        String created = mvc.perform(post("/api/v1/catalog/reading").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).path("readingId").asLong();
        assertThat(id).as(created).isPositive();
        JsonNode r = read("/api/v1/catalog/reading/" + id);
        assertThat(r.path("bigCount").bigIntegerValue().toString()).isEqualTo("123456789012345678901");
        assertThat(r.path("rawText").asText()).isEqualTo("large text");
        assertThat(new BigDecimal(r.path("amount").asText())).isEqualByComparingTo("99.95");
        assertThat(r.path("active").asBoolean()).isTrue();
        assertThat(r.path("localAt").asText()).isEqualTo("2026-10-03 09:30:00");
        assertThat(r.path("measuredAt").asText()).isEqualTo("2026-10-03T00:30:00Z");
        assertThat(Base64.getDecoder().decode(r.path("image").asText())).containsExactly(image);
    }

    // ---- fixtures ---------------------------------------------------------

    private JsonNode read(String path) throws Exception {
        return json.readTree(mvc.perform(get(path)).andReturn().getResponse().getContentAsString());
    }

    private Customer customer(String first, String last) {
        Customer c = new Customer();
        c.creation().setFirstName(first);
        c.creation().setLastName(last);
        c.creation().setEmail(first.toLowerCase() + System.nanoTime() + "@example.com");
        return customers.save(c);
    }

    private Order newOrder(Customer customer) {
        Order order = new Order();
        order.creation().setOrderDate(LocalDateTime.now().withNano(0));
        order.creation().setTotalAmount(new BigDecimal("12.00"));
        order.creation().setCustomerId(customer.getCustomerId());
        return order;
    }

    private OrderItem item(int seq, String unitPrice) {
        OrderItem item = new OrderItem();
        item.setItemSeq(seq);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal(unitPrice));
        return item;
    }

    private Product product(String sku, String price, boolean active) {
        Product p = new Product();
        p.setName("P " + sku);
        p.setSku(sku);
        p.setPrice(new BigDecimal(price));
        p.setActive(active);
        return products.save(p);
    }
}
