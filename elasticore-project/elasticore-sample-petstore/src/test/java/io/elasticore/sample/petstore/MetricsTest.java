package io.elasticore.sample.petstore;

import io.elasticore.sample.petstore.domain.model.Customer;
import io.elasticore.sample.petstore.domain.model.OrderStatus;
import io.elasticore.sample.petstore.domain.model.PurchaseOrder;
import io.elasticore.sample.petstore.domain.model.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.yaml.snakeyaml.Yaml;

import javax.sql.DataSource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The shop's metrics (blueprint/petstore/model/metrics.yml) as the semantic
 * model exports them: each one's SQL, run on the live database, gives what
 * the business means by it - computed here a second way, from the entities.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MetricsTest {

    @Autowired MockMvc mvc;
    @Autowired DataSource dataSource;
    @Autowired EntityManager entityManager;

    private static final String CARD = "\"creditCard\":{\"creditCardNumber\":\"4111111111111111\",\"creditCardType\":\"VISA\",\"creditCardExpDate\":\"12/30\"}";

    @Test
    @SuppressWarnings("unchecked")
    void eachMetricIsWhatTheBusinessMeansByIt() throws Exception {
        // Orders in every state: placed, shipped and cancelled.
        MockHttpSession user = signIn("user", "user");
        MockHttpSession admin = signIn("admin", "admin");
        String shipped = order(user, 1000, 2);
        order(user, 1002, 1);
        String cancelled = order(user, 1004, 3);
        mvc.perform(post("/api/petstore/purchaseOrder/" + shipped + "/ship").session(admin)).andExpect(status().isOk());
        mvc.perform(post("/api/petstore/purchaseOrder/" + cancelled + "/cancel").session(user)).andExpect(status().isOk());

        Map<String, Object> model;
        try (InputStream in = getClass().getResourceAsStream("/blueprint/semantic_model.yml")) {
            model = new Yaml().load(in);
        }
        Map<String, BigDecimal> measured = new HashMap<String, BigDecimal>();
        for (Map<String, Object> ds : (List<Map<String, Object>>) model.get("datasets")) {
            if (!ds.containsKey("metrics")) continue;
            for (Map<String, Object> m : (List<Map<String, Object>>) ds.get("metrics")) {
                Map<String, Object> expression = (Map<String, Object>) m.get("expression");
                // The portable form: a metric is always in the Ossie language.
                String sql = (String) expression.get("sql");
                assertThat(expression).as((String) m.get("name")).containsKey("sql").doesNotContainKey("dialects");
                measured.put((String) m.get("name"), run(sql, (String) ds.get("source")));
            }
        }
        // Each reads one entity, so each is its dataset's: none is left at the model root.
        assertThat(model).doesNotContainKey("metrics");

        // The same, from the entities.
        List<PurchaseOrder> orders = entityManager.createQuery("select o from PurchaseOrder o", PurchaseOrder.class).getResultList();
        BigDecimal revenue = BigDecimal.ZERO;
        long kept = 0, shippedCount = 0, cancelledCount = 0, placedCount = 0, paidFinished = 0;
        java.util.Set<Long> buyers = new java.util.HashSet<Long>();
        java.util.Set<Long> keptBuyers = new java.util.HashSet<Long>();
        for (PurchaseOrder o : orders) {
            buyers.add(o.getCustomerId());
            if (o.getStatus() != OrderStatus.CANCELLED) keptBuyers.add(o.getCustomerId());
            if (o.getStatus() == OrderStatus.PLACED) placedCount++;
            if (o.getStatus() != OrderStatus.PLACED && o.getTotal().toBigDecimal().signum() > 0) paidFinished++;
            if (o.getStatus() != OrderStatus.CANCELLED) {
                revenue = revenue.add(o.getTotal().toBigDecimal());
                kept++;
            }
            if (o.getStatus() == OrderStatus.SHIPPED) shippedCount++;
            if (o.getStatus() == OrderStatus.CANCELLED) cancelledCount++;
        }
        long customers = entityManager.createQuery("select c from Customer c", Customer.class).getResultList()
                .stream().filter(c -> c.getRole() == UserRole.USER).count();

        assertThat(kept).as("there is something to measure").isGreaterThanOrEqualTo(2);
        assertThat(measured.get("revenue")).isEqualByComparingTo(revenue);
        assertThat(measured.get("orderCount")).isEqualByComparingTo(BigDecimal.valueOf(kept));
        assertThat(measured.get("averageOrder").setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(revenue.divide(BigDecimal.valueOf(kept), 2, RoundingMode.HALF_UP));
        assertThat(measured.get("shippedOrders")).isEqualByComparingTo(BigDecimal.valueOf(shippedCount));
        assertThat(measured.get("cancelledOrders")).isEqualByComparingTo(BigDecimal.valueOf(cancelledCount));
        assertThat(measured.get("customerCount")).isEqualByComparingTo(BigDecimal.valueOf(customers));

        // Written with a list, an or, a group, a not: the same numbers a second way.
        assertThat(placedCount).as("every state has a row").isPositive();
        assertThat(shippedCount).isPositive();
        assertThat(cancelledCount).isPositive();
        assertThat(paidFinished).isPositive();
        assertThat(measured.get("liveOrders")).isEqualByComparingTo(BigDecimal.valueOf(placedCount + shippedCount));
        assertThat(measured.get("otherOrders")).isEqualByComparingTo(BigDecimal.valueOf(kept));
        assertThat(measured.get("finishedOrders")).isEqualByComparingTo(BigDecimal.valueOf(shippedCount + cancelledCount));
        assertThat(measured.get("paidFinished")).isEqualByComparingTo(BigDecimal.valueOf(paidFinished));
        assertThat(measured.get("unfinished")).isEqualByComparingTo(BigDecimal.valueOf(placedCount));
        // Three orders by one customer are one buyer.
        assertThat((long) orders.size()).as("a customer with several orders").isGreaterThan(buyers.size());
        assertThat(measured.get("buyers")).isEqualByComparingTo(BigDecimal.valueOf(buyers.size()));
        assertThat(measured.get("keptBuyers")).isEqualByComparingTo(BigDecimal.valueOf(keptBuyers.size()));
    }

    /**
     * A line without in, or, a parenthesis or distinct is written as it was before they could be read,
     * now in its dataset's metrics, on the dataset's fields by their own names.
     */
    @Test
    void theMetricsOfTheOldFormAreWrittenAsTheyWere() throws Exception {
        Map<String, Object> model;
        try (InputStream in = getClass().getResourceAsStream("/blueprint/semantic_model.yml")) {
            model = new Yaml().load(in);
        }
        assertThat(datasetMetrics(model))
                .containsEntry("PurchaseOrder.revenue", "SUM(CASE WHEN status <> 'CAN' THEN total END)")
                .containsEntry("PurchaseOrder.orderCount", "COUNT(CASE WHEN status <> 'CAN' THEN 1 END)")
                .containsEntry(
                        "PurchaseOrder.averageOrder",
                        "(SUM(CASE WHEN status <> 'CAN' THEN total END))"
                                + " / NULLIF((COUNT(CASE WHEN status <> 'CAN' THEN 1 END)), 0)")
                .containsEntry("PurchaseOrder.shippedOrders", "COUNT(CASE WHEN status = 'SHP' THEN 1 END)")
                .containsEntry("PurchaseOrder.cancelledOrders", "COUNT(CASE WHEN status = 'CAN' THEN 1 END)")
                .containsEntry("Customer.customerCount", "COUNT(CASE WHEN role = 'U' THEN 1 END)");
    }

    /** Each dataset's metrics, named as a consumer names them - PurchaseOrder.revenue - and their SQL. */
    @SuppressWarnings("unchecked")
    private static Map<String, String> datasetMetrics(Map<String, Object> model) {
        Map<String, String> written = new HashMap<String, String>();
        for (Map<String, Object> ds : (List<Map<String, Object>>) model.get("datasets")) {
            if (!ds.containsKey("metrics")) continue;
            for (Map<String, Object> m : (List<Map<String, Object>>) ds.get("metrics")) {
                written.put(ds.get("name") + "." + m.get("name"),
                        (String) ((Map<String, Object>) m.get("expression")).get("sql"));
            }
        }
        return written;
    }

    /**
     * As in SQL, a row whose status is null meets neither a condition nor its not: != , not in and
     * not (...) leave it out, so unfinished and finishedOrders do not add up to every order then
     * (the DSL guide says so). The status is taken away in a transaction that is rolled back.
     */
    @Test
    @SuppressWarnings("unchecked")
    void aNullStatusIsInNeitherAConditionNorItsNot() throws Exception {
        MockHttpSession user = signIn("user", "user");
        String unknown = order(user, 1000, 1);
        Map<String, Object> model;
        try (InputStream in = getClass().getResourceAsStream("/blueprint/semantic_model.yml")) {
            model = new Yaml().load(in);
        }
        Map<String, String> sqls = new HashMap<String, String>();
        for (Map.Entry<String, String> m : datasetMetrics(model).entrySet()) {
            if (m.getKey().startsWith("PurchaseOrder.")) sqls.put(m.getKey().substring("PurchaseOrder.".length()), m.getValue());
        }
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (java.sql.PreparedStatement p =
                        c.prepareStatement("UPDATE ps_purchase_order SET status = NULL WHERE order_id = ?")) {
                    p.setString(1, unknown);
                    assertThat(p.executeUpdate()).isEqualTo(1);
                }
                long all = count(c, "SELECT COUNT(*) FROM ps_purchase_order");
                long cancelled = count(c, "SELECT COUNT(*) FROM ps_purchase_order WHERE status = 'CAN'");
                long nulls = count(c, "SELECT COUNT(*) FROM ps_purchase_order WHERE status IS NULL");
                assertThat(nulls).isEqualTo(1);
                long finished = count(c, metric(sqls.get("finishedOrders")));
                long unfinished = count(c, metric(sqls.get("unfinished")));
                assertThat(finished + unfinished).as("the null row is in neither").isEqualTo(all - nulls);
                assertThat(count(c, metric(sqls.get("otherOrders")))).as("not in leaves it out")
                        .isEqualTo(all - cancelled - nulls);
                assertThat(count(c, metric(sqls.get("orderCount")))).as("!= leaves it out")
                        .isEqualTo(all - cancelled - nulls);
            } finally {
                c.rollback();
            }
        }
    }

    /** A PurchaseOrder metric as a query on its table, whose columns its fields are. */
    private static String metric(String sql) {
        return "SELECT " + sql + " FROM ps_purchase_order";
    }

    private static long count(Connection c, String query) throws Exception {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(query)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /**
     * A dataset's metric names its fields by their own names, and each field here is the column of
     * the same name: on the database it is a query on the dataset's table as written.
     */
    private BigDecimal run(String expression, String table) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT " + expression + " FROM " + table)) {
            rs.next();
            BigDecimal value = rs.getBigDecimal(1);
            return value == null ? BigDecimal.ZERO : value;
        }
    }

    private String order(MockHttpSession session, int itemId, int quantity) throws Exception {
        MvcResult r = mvc.perform(post("/api/petstore/purchaseOrder").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"itemId\":" + itemId + ",\"quantity\":" + quantity + "}]," + CARD + "}"))
                .andExpect(status().isOk()).andReturn();
        return com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.orderId");
    }

    private MockHttpSession signIn(String login, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/petstore/customer/signin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }
}
