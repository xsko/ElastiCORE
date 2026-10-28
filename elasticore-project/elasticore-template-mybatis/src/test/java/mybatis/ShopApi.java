package mybatis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The generated MyBatis shop, over HTTP and a real database: what a JPA domain
 * answers, a MyBatis one answers the same way. Run on H2 (MyBatisApiTest) and
 * on Oracle, PostgreSQL and SQL Server (ShopApiOn*Test), each on the schema
 * the generator wrote for it.
 */
@SpringBootTest(classes = MyBatisSampleApp.class)
@AutoConfigureMockMvc
public abstract class ShopApi {

    private static final String API = "/api/shop";

    @Autowired
    MockMvc mvc;

    @Autowired
    org.apache.ibatis.session.SqlSessionFactory sessions;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MyBatisSampleApp.StatementCounter statements;

    // ---- one row and the rows it points at -----------------------------------

    @Test
    public void aRowIsReadWithTheRowsItPointsAt() throws Exception {
        long food = id(create("category", "{\"name\":\"Food\"}"), "categoryId");
        long fruit = id(create("category", "{\"name\":\"Fruit\",\"parentCategoryId\":" + food + "}"), "categoryId");
        JsonNode product = create("product", "{\"name\":\"Apple\",\"price\":1200,\"categoryCategoryId\":" + fruit + "}");
        // @genid: the service makes the key, as JPA's @PrePersist did.
        String code = product.get("productCode").asText();
        assertEquals(12, code.length(), product.toString());
        assertEquals(fruit, product.get("categoryCategoryId").asLong(), product.toString());

        JsonNode customer = create("customer", "{\"name\":\"Kim\",\"email\":\"" + unique() + "@a.com\"}");
        // The default of the model, and the creation time JPA's auditing set.
        assertEquals(1, customer.get("grade").asInt(), customer.toString());
        assertFalse(customer.get("joinedAt").isNull(), customer.toString());

        JsonNode order = create("purchaseOrder", order(customer.get("customerId").asLong(), code, "N", 5000, "first"));
        JsonNode read = body(perform(get(API + "/purchaseOrder/" + order.get("orderId").asLong()), 200));
        assertEquals("Kim", read.get("customer").get("name").asText(), read.toString());
        assertEquals("Apple", read.get("product").get("name").asText(), read.toString());
        // A relation of the joined row comes with its key.
        assertEquals(fruit, read.get("product").get("categoryCategoryId").asLong(), read.toString());
        assertEquals("N", read.get("status").asText(), read.toString());
        // The enum is stored by its code, as the JPA converter stores it.
        assertEquals("N", jdbc.queryForObject("SELECT status FROM purchase_order WHERE order_id = ?",
                String.class, order.get("orderId").asLong()));
        assertEquals(0L, read.get("version").asLong(), read.toString());

        // A tree: a category read with its parent.
        JsonNode child = body(perform(get(API + "/category/" + fruit), 200));
        assertEquals("Food", child.get("parent").get("name").asText(), child.toString());
    }

    // ---- search -----------------------------------------------------------------

    @Test
    public void everySearchConditionOfTheModelNarrowsTheRows() throws Exception {
        long customer = id(create("customer", "{\"name\":\"Park Search\",\"email\":\"" + unique() + "@a.com\",\"grade\":4}"), "customerId");
        String tag = unique();
        long first = id(create("purchaseOrder", order(customer, null, "N", 1000, tag + " Gift Box")), "orderId");
        id(create("purchaseOrder", order(customer, null, "P", 2000, tag + " plain")), "orderId");
        id(create("purchaseOrder", order(customer, null, "P", 3000, tag + " GIFT card")), "orderId");
        jdbc.update("UPDATE purchase_order SET delivered_at = CURRENT_TIMESTAMP WHERE order_id = ?", first);

        assertEquals(3, total("purchaseOrder", "{\"customerCustomerId\":" + customer + "}"));
        assertEquals(2, total("purchaseOrder", "{\"customerCustomerId\":" + customer + ",\"status\":\"P\"}"));
        assertEquals(2, total("purchaseOrder", "{\"customerCustomerId\":" + customer + ",\"amountFrom\":1500,\"amountTo\":3000}"));
        // like on a string: any part, ignoring case.
        assertEquals(2, total("purchaseOrder", "{\"customerCustomerId\":" + customer + ",\"memo\":\"gift\"}"));
        // isnull: a yes or no on whether the column is empty.
        assertEquals(1, total("purchaseOrder", "{\"customerCustomerId\":" + customer + ",\"deliveredAtEmpty\":false}"));
        assertEquals(2, total("purchaseOrder", "{\"customerCustomerId\":" + customer + ",\"deliveredAtEmpty\":true}"));
        // An empty text searches by nothing, as the JPA search does.
        assertEquals(3, total("purchaseOrder", "{\"customerCustomerId\":" + customer + ",\"memo\":\"  \"}"));
        // >= on a number.
        assertTrue(total("customer", "{\"grade\":4,\"name\":\"park search\"}") >= 1);
        assertEquals(0, total("customer", "{\"grade\":5,\"name\":\"park search\"}"));
        // in on a single value: an equality, as JPA's in() over one value is.
        String orderNo = jdbc.queryForObject("SELECT order_no FROM purchase_order WHERE order_id = ?", String.class, first);
        assertEquals(1, total("purchaseOrder", "{\"orderNo\":\"" + orderNo + "\"}"));

        // _% matches the start; < compares.
        String slug = unique().substring(0, 8);
        create("category", "{\"name\":\"Slugged\",\"slug\":\"" + slug + "-fruit\"}");
        assertEquals(1, total("category", "{\"slug\":\"" + slug + "\"}"));
        assertEquals(0, total("category", "{\"slug\":\"fruit\"}"));
        String name = "Stocked " + unique();
        create("product", "{\"name\":\"" + name + "\",\"price\":10,\"stock\":3}");
        assertEquals(1, total("product", "{\"name\":\"" + name + "\",\"stock\":4}"));
        assertEquals(0, total("product", "{\"name\":\"" + name + "\",\"stock\":3}"));

        // @search with no condition on text: equal, or like when the value carries a %.
        String label = unique().substring(0, 10);
        create("product", "{\"name\":\"Labelled\",\"price\":10,\"label\":\"" + label + "\"}");
        assertEquals(1, total("product", "{\"label\":\"" + label + "\"}"));
        assertEquals(0, total("product", "{\"label\":\"" + label.substring(0, 5) + "\"}"));
        assertEquals(1, total("product", "{\"label\":\"" + label.substring(0, 5) + "%\"}"));
    }

    @Test
    public void aColumnNotUpdatableKeepsItsValueAndAFormulaReadsNull() throws Exception {
        JsonNode product = create("product", "{\"name\":\"Pear\",\"price\":900,\"origin\":\"Naju\"}");
        String code = product.get("productCode").asText();
        // @formula is JPA's SELECT expression; MyBatis reads the table's columns.
        assertTrue(product.get("rating") == null || product.get("rating").isNull(), product.toString());
        JsonNode updated = body(perform(put(API + "/product").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productCode\":\"" + code + "\",\"price\":950,\"origin\":\"Elsewhere\"}"), 200));
        assertEquals(950, updated.get("price").asInt(), updated.toString());
        assertEquals("Naju", jdbc.queryForObject("SELECT origin FROM product WHERE product_code = ?", String.class, code));
    }

    // ---- page and sort ------------------------------------------------------------

    @Test
    public void aPageIsTwoStatementsSortedAsAskedAndARefusedSortIsA400() throws Exception {
        long customer = id(create("customer", "{\"name\":\"Lee Page\",\"email\":\"" + unique() + "@a.com\"}"), "customerId");
        for (int i = 1; i <= 5; i++) create("purchaseOrder", order(customer, null, "N", i * 100, "page " + i));

        statements.count.set(0);
        JsonNode page = body(perform(post(API + "/purchaseOrder/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerCustomerId\":" + customer + ",\"pageNumber\":1,\"pageSize\":2,\"sortCode\":\"amount-\"}"), 200));
        // The count and the page, each row with its customer: no statement per row.
        assertEquals(2, statements.count.get());
        String last = statements.sql.get(statements.sql.size() - 1).replaceAll("\\s+", " ");
        // The page in the database's own syntax, chosen by the database id the runtime library set.
        String id = sessions.getConfiguration().getDatabaseId();
        boolean fetch = "oracle".equals(id) || "sqlserver".equals(id);
        assertTrue(last.contains(fetch ? "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY" : "LIMIT ? OFFSET ?"), id + ": " + last);
        assertEquals(5, page.get("totalElements").asInt(), page.toString());
        List<Integer> amounts = new ArrayList<Integer>();
        for (JsonNode row : page.get("content")) amounts.add(row.get("amount").asInt());
        assertEquals(java.util.Arrays.asList(300, 200), amounts);

        // Sorting by a column of a joined row.
        perform(post(API + "/purchaseOrder/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sortCode\":\"customer.name+,orderId-\"}"), 200);

        // Nothing a request names reaches ORDER BY unless it is a property.
        MvcResult refused = perform(post(API + "/purchaseOrder/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sortCode\":\"amount;drop table purchase_order+\"}"), 400);
        assertTrue(refused.getResponse().getContentAsString().contains("cannot be sorted by"));
    }

    // ---- writes -----------------------------------------------------------------

    @Test
    public void anUpdateCountsTheVersionUpAndAStaleOneIsA409() throws Exception {
        long customer = id(create("customer", "{\"name\":\"Choi\",\"email\":\"" + unique() + "@a.com\"}"), "customerId");
        long order = id(create("purchaseOrder", order(customer, null, "N", 700, "to ship")), "orderId");

        JsonNode updated = body(perform(put(API + "/purchaseOrder").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + order + ",\"status\":\"S\",\"version\":0}"), 200));
        assertEquals("S", updated.get("status").asText(), updated.toString());
        assertEquals(1L, updated.get("version").asLong(), updated.toString());
        // What the update did not send is kept.
        assertEquals(700, updated.get("amount").asInt(), updated.toString());
        assertEquals("Choi", updated.get("customer").get("name").asText(), updated.toString());

        perform(put(API + "/purchaseOrder").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + order + ",\"memo\":\"late\",\"version\":0}"), 409);
        perform(put(API + "/purchaseOrder").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":999999,\"memo\":\"x\"}"), 404);
    }

    @Test
    public void aModificationTimeIsStampedOnEveryWrite() throws Exception {
        JsonNode created = create("customer", "{\"name\":\"Jung\",\"email\":\"" + unique() + "@a.com\"}");
        long id = created.get("customerId").asLong();
        String joined = created.get("joinedAt").asText();
        jdbc.update("UPDATE customer SET changed_at = ? WHERE customer_id = ?",
                java.sql.Timestamp.valueOf("2000-01-01 00:00:00"), id);
        JsonNode updated = body(perform(put(API + "/customer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":" + id + ",\"grade\":2}"), 200));
        assertEquals(joined, updated.get("joinedAt").asText(), updated.toString());
        assertNotEquals("2000-01-01 00:00:00", updated.get("changedAt").asText(), updated.toString());
    }

    @Test
    public void aRowStillReferencedIsNotDeleted() throws Exception {
        long customer = id(create("customer", "{\"name\":\"Han\",\"email\":\"" + unique() + "@a.com\"}"), "customerId");
        long order = id(create("purchaseOrder", order(customer, null, "N", 100, "keep")), "orderId");

        perform(delete(API + "/customer/" + customer), 409);
        assertEquals("true", perform(delete(API + "/purchaseOrder/" + order), 200).getResponse().getContentAsString());
        perform(get(API + "/purchaseOrder/" + order), 404);
        assertEquals("true", perform(delete(API + "/customer/" + customer), 200).getResponse().getContentAsString());
    }

    @Test
    public void aTakenUniqueValueIsA409() throws Exception {
        String email = unique() + "@a.com";
        create("customer", "{\"name\":\"One\",\"email\":\"" + email + "\"}");
        perform(post(API + "/customer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Two\",\"email\":\"" + email + "\"}"), 409);
    }

    // ---- every column type -----------------------------------------------------------

    /** Each type is written and read back as it was sent - and so is each one left empty. */
    @Test
    public void everyColumnTypeIsKeptAsSent() throws Exception {
        String text = "줄 하나\n" + "x".repeat(5000);        // longer than any VARCHAR2 / NVARCHAR(n) holds inline
        String sent = "{\"name\":\"Every type\",\"active\":true,\"weight\":1234.5678,\"ratio\":0.25,"
                + "\"amount\":12345.678,\"price\":99.95,\"count\":7,\"notes\":\"" + text.replace("\n", "\\n") + "\","
                + "\"body\":\"본문 - body\",\"image\":\"AQID/w==\",\"madeOn\":\"2026-10-29\","
                + "\"madeAt\":\"2026-10-29 09:30:15\",\"opensAt\":\"09:30:00\",\"stampedAt\":\"2026-10-29T00:30:00Z\"}";
        long id = id(create("gadget", sent), "gadgetId");
        JsonNode read = body(perform(get(API + "/gadget/" + id), 200));
        assertTrue(read.get("active").asBoolean(), read.toString());
        assertEquals(1234.5678, read.get("weight").asDouble(), 0.0, read.toString());
        assertEquals(0.25, read.get("ratio").asDouble(), 0.0, read.toString());
        assertEquals(0, new java.math.BigDecimal("12345.678").compareTo(read.get("amount").decimalValue()), read.toString());
        assertEquals(0, new java.math.BigDecimal("99.95").compareTo(read.get("price").decimalValue()), read.toString());
        assertEquals(7, read.get("count").asInt(), read.toString());
        assertEquals(text, read.get("notes").asText());
        assertEquals("본문 - body", read.get("body").asText(), read.toString());
        assertEquals("AQID/w==", read.get("image").asText(), read.toString());
        assertEquals("2026-10-29", read.get("madeOn").asText(), read.toString());
        assertEquals("2026-10-29 09:30:15", read.get("madeAt").asText(), read.toString());
        assertTrue(read.get("opensAt").asText().startsWith("09:30"), read.toString());
        assertEquals(java.time.Instant.parse("2026-10-29T00:30:00Z"),
                java.time.Instant.parse(read.get("stampedAt").asText()), read.toString());

        // Searched by a boolean, a decimal range and a time range.
        assertEquals(1, total("gadget", "{\"name\":\"Every type\",\"active\":true,\"amountFrom\":12345,\"amountTo\":12346,"
                + "\"madeAtFrom\":\"2026-10-29 00:00:00\",\"madeAtTo\":\"2026-10-29 23:59:59\"}"));
        assertEquals(0, total("gadget", "{\"name\":\"Every type\",\"active\":false}"));

        // Every column left empty: written as NULL of its type, read back as null.
        long empty = id(create("gadget", "{\"name\":\"Nothing\"}"), "gadgetId");
        JsonNode none = body(perform(get(API + "/gadget/" + empty), 200));
        for (String field : new String[]{"active", "weight", "ratio", "amount", "price", "count", "notes", "body",
                "image", "madeOn", "madeAt", "opensAt", "stampedAt"}) {
            assertTrue(none.get(field) == null || none.get(field).isNull(), field + ": " + none);
        }
        // An update naming one field writes every column back as it was read.
        JsonNode renamed = body(perform(put(API + "/gadget").contentType(MediaType.APPLICATION_JSON)
                .content("{\"gadgetId\":" + id + ",\"name\":\"Renamed\"}"), 200));
        assertEquals("Renamed", renamed.get("name").asText(), renamed.toString());
        assertEquals(text, renamed.get("notes").asText());
        assertEquals("AQID/w==", renamed.get("image").asText(), renamed.toString());
        assertEquals(java.time.Instant.parse("2026-10-29T00:30:00Z"),
                java.time.Instant.parse(renamed.get("stampedAt").asText()), renamed.toString());
    }

    // ---- helpers ------------------------------------------------------------------

    private static String order(long customer, String product, String status, int amount, String memo) {
        return "{\"orderNo\":\"" + unique().substring(0, 12) + "\",\"status\":\"" + status + "\",\"amount\":" + amount
                + ",\"orderedAt\":\"2026-10-01 10:00:00\",\"customerCustomerId\":" + customer
                + (product == null ? "" : ",\"productProductCode\":\"" + product + "\"")
                + ",\"memo\":\"" + memo + "\"}";
    }

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private JsonNode create(String entity, String body) throws Exception {
        return body(perform(post(API + "/" + entity).contentType(MediaType.APPLICATION_JSON).content(body), 200));
    }

    private long total(String entity, String search) throws Exception {
        return body(perform(post(API + "/" + entity + "/search").contentType(MediaType.APPLICATION_JSON)
                .content(search), 200)).get("totalElements").asLong();
    }

    private static long id(JsonNode node, String field) {
        assertNotNull(node.get(field), node.toString());
        return node.get(field).asLong();
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult r = mvc.perform(request).andReturn();
        assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return r;
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }
}
