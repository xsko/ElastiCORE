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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        Map<String, String> tables = new HashMap<String, String>();
        for (Map<String, Object> ds : (List<Map<String, Object>>) model.get("datasets")) {
            tables.put((String) ds.get("name"), (String) ds.get("source"));
        }
        Map<String, BigDecimal> measured = new HashMap<String, BigDecimal>();
        for (Map<String, Object> m : (List<Map<String, Object>>) model.get("metrics")) {
            Map<String, Object> expression = (Map<String, Object>) m.get("expression");
            String sql = (String) ((List<Map<String, Object>>) expression.get("dialects")).get(0).get("expression");
            measured.put((String) m.get("name"), run(sql, tables));
        }

        // The same, from the entities.
        List<PurchaseOrder> orders = entityManager.createQuery("select o from PurchaseOrder o", PurchaseOrder.class).getResultList();
        BigDecimal revenue = BigDecimal.ZERO;
        long kept = 0, shippedCount = 0, cancelledCount = 0;
        for (PurchaseOrder o : orders) {
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
    }

    /** A metric's SQL names datasets; on the database it reads their tables. */
    private BigDecimal run(String expression, Map<String, String> tables) throws Exception {
        String sql = expression;
        String from = null;
        for (Map.Entry<String, String> t : tables.entrySet()) {
            Matcher m = Pattern.compile("\\b" + t.getKey() + "\\.").matcher(sql);
            if (!m.find()) continue;
            assertThat(from).as("one dataset per metric here: " + expression).isNull();
            from = t.getValue();
            sql = sql.replaceAll("\\b" + t.getKey() + "\\.", t.getValue() + ".");
        }
        assertThat(from).as("a dataset in " + expression).isNotNull();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT " + sql + " FROM " + from)) {
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
