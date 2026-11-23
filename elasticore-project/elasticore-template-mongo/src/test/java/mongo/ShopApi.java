package mongo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.elasticore.sample.mongo.shop.application.PurchaseOrderService;
import io.elasticore.sample.mongo.shop.application.dto.OrderLineDTO;
import io.elasticore.sample.mongo.shop.application.dto.PurchaseOrderDTO;
import io.elasticore.sample.mongo.shop.application.dto.PurchaseOrderSrchDTO;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated MongoDB shop, over HTTP and a real MongoDB: what a JPA domain
 * answers, a MongoDB one answers the same way - and what it keeps in the
 * documents is read back with MongoTemplate, to show how it is stored.
 * MongoApiTest runs it on a MongoDB Testcontainers starts;
 * MongoApiOnServerTest on the server ELASTICORE_IT_MONGODB_URI names.
 */
@SpringBootTest(classes = MongoSampleApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.data.mongodb.auto-index-creation=true")
public abstract class ShopApi {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper json;

    @Autowired
    MongoTemplate mongo;

    @Autowired
    PurchaseOrderService orders;

    /** A database of its own for each run, so that runs never see each other's documents. */
    static String database() {
        return "shop_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    // ---- one document, its reference and its members ---------------------------------

    @Test
    public void aDocumentKeepsItsMembersAndPointsAtAnother() throws Exception {
        JsonNode customer = create("customer", "{\"name\":\"Kim\",\"email\":\"" + unique() + "@a.com\","
                + "\"address\":{\"city\":\"Seoul\",\"zip\":\"04524\"}}");
        long customerId = id(customer, "customerId");
        // The key from the sequence, the model's default, and the creation time stamped.
        assertTrue(customerId >= 1, customer.toString());
        assertEquals(1, customer.get("grade").asInt(), customer.toString());
        assertFalse(customer.get("joinedAt").isNull(), customer.toString());
        assertEquals("Seoul", customer.get("address").get("city").asText(), customer.toString());

        JsonNode product = create("product", "{\"name\":\"Apple\",\"price\":1200,\"stock\":3}");
        // @genid: the service makes the key.
        String code = product.get("productCode").asText();
        assertEquals(12, code.length(), product.toString());

        JsonNode order = create("purchaseOrder", "{\"orderNo\":\"A-1\",\"status\":\"N\",\"amount\":5000,"
                + "\"total\":12.50,\"customerCustomerId\":" + customerId + ",\"productProductCode\":\"" + code + "\","
                + "\"lines\":[{\"sku\":\"SKU-1\",\"qty\":2},{\"sku\":\"SKU-2\",\"qty\":1}]}");
        long orderId = id(order, "orderId");
        // A second document of the collection takes the next number.
        long next = id(create("purchaseOrder", "{\"orderNo\":\"A-2\",\"status\":\"N\",\"amount\":10,"
                + "\"customerCustomerId\":" + customerId + "}"), "orderId");
        assertEquals(orderId + 1, next);

        JsonNode read = body(send("GET", "/purchaseOrder/" + orderId, null), 200);
        assertEquals("Kim", read.get("customer").get("name").asText(), read.toString());
        assertEquals("Apple", read.get("product").get("name").asText(), read.toString());
        assertEquals("N", read.get("status").asText(), read.toString());
        assertEquals(0, read.get("version").asLong(), read.toString());
        JsonNode lines = read.get("lines");
        assertEquals(2, lines.size(), read.toString());
        assertEquals("SKU-1", lines.get(0).get("sku").asText(), read.toString());
        // The members' keys come from their own sequence.
        assertTrue(lines.get(0).get("lineId").asLong() >= 1, read.toString());
        assertTrue(lines.get(1).get("lineId").asLong() > lines.get(0).get("lineId").asLong(), read.toString());

        // How it is stored: the order's own collection, the customer by its id,
        // the enum by its code, the lines inside the order, the decimal as Decimal128.
        Document stored = mongo.getCollection("purchase_order").find(new Document("_id", orderId)).first();
        assertNotNull(stored);
        assertEquals(customerId, ((Number) stored.get("customer")).longValue(), stored.toJson());
        assertEquals("N", stored.get("status"), stored.toJson());
        assertEquals(new Decimal128(new java.math.BigDecimal("12.50")), stored.get("total"), stored.toJson());
        List<?> embedded = (List<?>) stored.get("lines");
        assertEquals(2, embedded.size(), stored.toJson());
        assertFalse(((Document) embedded.get(0)).containsKey("order"), stored.toJson());
        Document storedCustomer = mongo.getCollection("customer").find(new Document("_id", customerId)).first();
        assertEquals("Seoul", ((Document) storedCustomer.get("address")).get("city"), storedCustomer.toJson());

        // A missing one is a 404.
        send("GET", "/purchaseOrder/" + (next + 1000), null, 404);
    }

    @Test
    public void aTakenUniqueValueIsA409() throws Exception {
        String email = unique() + "@a.com";
        create("customer", "{\"name\":\"Lee\",\"email\":\"" + email + "\"}");
        HttpResponse<String> taken = send("POST", "/customer", "{\"name\":\"Lee 2\",\"email\":\"" + email + "\"}", 409);
        // The kind of refusal, never the driver's text (E11000, the index and the value).
        assertEquals("unique", json.readTree(taken.body()).get("code").asText(), taken.body());
        assertFalse(taken.body().contains(email), taken.body());
        assertFalse(taken.body().contains("E11000"), taken.body());
        // Without an email, as many as you like: the index leaves them out.
        create("customer", "{\"name\":\"No mail 1\"}");
        create("customer", "{\"name\":\"No mail 2\"}");
    }

    // ---- search -----------------------------------------------------------------------

    @Test
    public void aSearchIsPagedSortedAndNarrowedByEachCondition() throws Exception {
        long customer = id(create("customer", "{\"name\":\"Park Search\",\"email\":\"" + unique() + "@a.com\","
                + "\"grade\":4}"), "customerId");
        long other = id(create("customer", "{\"name\":\"Other\",\"email\":\"" + unique() + "@a.com\"}"), "customerId");
        for (int i = 1; i <= 5; i++) {
            create("purchaseOrder", "{\"orderNo\":\"S-" + i + "\",\"status\":\"" + (i % 2 == 0 ? "P" : "N")
                    + "\",\"amount\":" + (i * 100) + ",\"memo\":\"Gift (" + i + ") wrap\",\"customerCustomerId\":"
                    + customer + (i == 5 ? ",\"deliveredAt\":\"2026-11-01 10:00:00\"" : "") + "}");
        }
        create("purchaseOrder", "{\"orderNo\":\"S-9\",\"status\":\"N\",\"amount\":900,\"customerCustomerId\":" + other
                + "}");

        // The second page of two, the largest amount first.
        JsonNode page = body(send("POST", "/purchaseOrder/search", "{\"customerCustomerId\":" + customer
                + ",\"pageNumber\":1,\"pageSize\":2,\"sortCode\":\"amount-\"}"), 200);
        assertEquals(5, page.get("totalElements").asInt(), page.toString());
        List<Integer> amounts = new ArrayList<Integer>();
        for (JsonNode row : page.get("content")) amounts.add(row.get("amount").asInt());
        assertEquals(List.of(300, 200), amounts, page.toString());

        String mine = "\"customerCustomerId\":" + customer;
        assertEquals(3, total("purchaseOrder", "{" + mine + ",\"status\":\"N\"}"));
        assertEquals(3, total("purchaseOrder", "{" + mine + ",\"amountFrom\":200,\"amountTo\":400}"));
        // like: contains, ignoring case, the value's own regex characters taken as written.
        assertEquals(1, total("purchaseOrder", "{" + mine + ",\"memo\":\"GIFT (3)\"}"));
        assertEquals(4, total("purchaseOrder", "{" + mine + ",\"deliveredAtEmpty\":true}"));
        assertEquals(1, total("purchaseOrder", "{" + mine + ",\"deliveredAtEmpty\":false}"));
        assertEquals(1, total("purchaseOrder", "{\"orderNo\":\"S-9\"}"));
        assertEquals(1, total("customer", "{\"name\":\"park sea\",\"grade\":4}"));
        assertEquals(0, total("customer", "{\"name\":\"park sea\",\"grade\":5}"));

        // A sort by a reference's id is by what the document holds; anything else is refused.
        send("POST", "/purchaseOrder/search", "{\"sortCode\":\"customer.customerId+,orderId-\"}", 200);
        String refused = send("POST", "/purchaseOrder/search", "{\"sortCode\":\"customer.name+\"}", 400).body();
        assertTrue(refused.contains("cannot be sorted by"), refused);
        // A customer's e-mail is shown masked (@Mask): its order would give the e-mail away, so it is not sorted by.
        String masked = send("POST", "/customer/search", "{\"sortCode\":\"email+\"}", 400).body();
        assertTrue(masked.contains("\"no-such-property\"") && masked.contains("cannot be sorted by 'email'"), masked);
        send("POST", "/customer/search", "{\"sortCode\":\"name+,address.city-\"}", 200);
    }

    // ---- update and delete --------------------------------------------------------------

    @Test
    public void anUpdateCountsTheVersionUpAndAStaleOneIsA409() throws Exception {
        long customer = id(create("customer", "{\"name\":\"Choi\"}"), "customerId");
        long order = id(create("purchaseOrder", "{\"orderNo\":\"U-1\",\"status\":\"N\",\"amount\":10,"
                + "\"customerCustomerId\":" + customer + ",\"lines\":[{\"sku\":\"X\",\"qty\":1}]}"), "orderId");
        JsonNode updated = body(send("PUT", "/purchaseOrder", "{\"orderId\":" + order + ",\"status\":\"S\","
                + "\"version\":0,\"lines\":[{\"sku\":\"Y\",\"qty\":3},{\"sku\":\"Z\",\"qty\":4}]}"), 200);
        assertEquals("S", updated.get("status").asText(), updated.toString());
        assertEquals(1, updated.get("version").asLong(), updated.toString());
        // The lines the request sent replace the ones the order had.
        assertEquals(2, updated.get("lines").size(), updated.toString());
        assertEquals("Y", updated.get("lines").get(0).get("sku").asText(), updated.toString());
        assertEquals(10, updated.get("amount").asLong(), "what the request left out is kept: " + updated);
        send("PUT", "/purchaseOrder", "{\"orderId\":" + order + ",\"memo\":\"late\",\"version\":0}", 409);
    }

    @Test
    public void aDeleteRemovesTheDocumentOrKeepsItWhenDeleted() throws Exception {
        long customer = id(create("customer", "{\"name\":\"Jung\",\"email\":\"" + unique() + "@a.com\"}"),
                "customerId");
        long order = id(create("purchaseOrder", "{\"orderNo\":\"D-1\",\"status\":\"N\",\"amount\":10,"
                + "\"customerCustomerId\":" + customer + "}"), "orderId");
        assertEquals("true", send("DELETE", "/purchaseOrder/" + order, null, 200).body());
        send("GET", "/purchaseOrder/" + order, null, 404);
        assertEquals(0, mongo.count(Query.query(Criteria.where("_id").is(order)), "purchase_order"));

        // @deleted: the customer's document is kept with the time, and no read finds it.
        assertEquals("true", send("DELETE", "/customer/" + customer, null, 200).body());
        send("GET", "/customer/" + customer, null, 404);
        Document kept = mongo.getCollection("customer").find(new Document("_id", customer)).first();
        assertNotNull(kept);
        assertNotNull(kept.get("retiredAt"), kept.toJson());
        assertEquals(0, total("customer", "{\"customerId\":" + customer + "}"));
    }

    /**
     * An html field over MongoDB (#208, #209): kept to the basic safelist on a create and on an update, and its text
     * without tags written beside it, whatever the request sent - in the stored document too.
     */
    @Test
    public void anHtmlFieldIsCleanedAndItsTextKept() throws Exception {
        JsonNode created = create("product", "{\"name\":\"Lamp\",\"price\":30,\"descriptionText\":\"sent\","
                + "\"description\":\"<p onclick=\\\"steal()\\\">A <b>warm</b> lamp</p><script>steal()</script>\"}");
        String code = created.get("productCode").asText();
        assertEquals("<p>A <b>warm</b> lamp</p>", created.get("description").asText(), created.toString());
        assertEquals("A warm lamp", created.get("descriptionText").asText(), created.toString());
        Document stored = mongo.getCollection("product").find(new Document("_id", code)).first();
        assertNotNull(stored);
        assertEquals("<p>A <b>warm</b> lamp</p>", stored.getString("description"), stored.toJson());

        JsonNode changed = body(send("PUT", "/product", "{\"productCode\":\"" + code + "\",\"name\":\"Lamp\",\"price\":30,"
                + "\"description\":\"<a href=\\\"https://example.com/l\\\">spec</a><img src=\\\"x\\\" onerror=\\\"steal()\\\">\"}"),
                200);
        assertEquals("<a href=\"https://example.com/l\" rel=\"nofollow\">spec</a>", changed.get("description").asText(),
                changed.toString());
        assertEquals("spec", changed.get("descriptionText").asText(), changed.toString());
    }

    /**
     * An html field on a member the document embeds (OrderLine.note), which has no service of its own: the order's
     * service cleans it with the order on a create, an update, a batch update and an update by condition - in the
     * stored document too.
     */
    @Test
    public void anEmbeddedMembersHtmlFieldIsCleanedOnEveryWrite() throws Exception {
        String raw = "<b onclick=\"steal()\">hi</b><script>steal()</script>";
        String sent = json.writeValueAsString(raw);
        String clean = "<b>hi</b>";
        String orderNo = "H-" + unique();
        long customer = id(create("customer", "{\"name\":\"Han\"}"), "customerId");
        JsonNode created = create("purchaseOrder", "{\"orderNo\":\"" + orderNo + "\",\"status\":\"N\",\"amount\":10,"
                + "\"customerCustomerId\":" + customer + ",\"lines\":[{\"sku\":\"H\",\"qty\":1,\"note\":" + sent + "}]}");
        long order = id(created, "orderId");
        assertEquals(clean, created.get("lines").get(0).get("note").asText(), created.toString());
        assertEquals(clean, storedLine(order).getString("note"));

        JsonNode updated = body(send("PUT", "/purchaseOrder", "{\"orderId\":" + order + ",\"version\":0,"
                + "\"lines\":[{\"sku\":\"U\",\"qty\":1,\"note\":" + sent + "}]}"), 200);
        assertEquals(clean, updated.get("lines").get(0).get("note").asText(), updated.toString());
        assertEquals(clean, storedLine(order).getString("note"));

        send("PUT", "/purchaseOrder/batch", "[{\"orderId\":" + order + ",\"version\":1,"
                + "\"lines\":[{\"sku\":\"B\",\"qty\":1,\"note\":" + sent + "}]}]", 200);
        assertEquals("B", storedLine(order).getString("sku"));
        assertEquals(clean, storedLine(order).getString("note"));

        OrderLineDTO line = new OrderLineDTO();
        line.setSku("C");
        line.setQty(1);
        line.setNote(raw);
        PurchaseOrderDTO change = new PurchaseOrderDTO();
        change.setLines(List.of(line));
        PurchaseOrderSrchDTO where = new PurchaseOrderSrchDTO();
        where.setOrderNo(orderNo);
        assertEquals(1, orders.updateBySpecification(change, where));
        assertEquals("C", storedLine(order).getString("sku"));
        assertEquals(clean, storedLine(order).getString("note"));
    }

    /** The order's one line, as the document holds it. */
    private Document storedLine(long orderId) {
        Document stored = mongo.getCollection("purchase_order").find(new Document("_id", orderId)).first();
        assertNotNull(stored);
        List<?> lines = (List<?>) stored.get("lines");
        assertEquals(1, lines.size(), stored.toJson());
        return (Document) lines.get(0);
    }

    /**
     * Issue 270: Customer.email is @Mask(EMAIL). The read DTO sent back as it is carries the masked text, which is the
     * stored value masked: PUT and PUT /batch keep the stored address. A new one is written, and so is masked text
     * that is not the stored value masked.
     */
    @Test
    public void aMaskedFieldSentBackAsShownKeepsTheStoredValue() throws Exception {
        String local = "keeper" + unique();
        String email = local + "@a.com";
        String masked = local.substring(0, 2) + "*".repeat(local.length() - 2) + "@a.com";
        long id = id(create("customer", "{\"name\":\"Masked\",\"email\":\"" + email + "\"}"), "customerId");
        com.fasterxml.jackson.databind.node.ObjectNode read =
                (com.fasterxml.jackson.databind.node.ObjectNode) body(send("GET", "/customer/" + id, null), 200);
        assertEquals(masked, read.get("email").asText(), read.toString());

        read.put("name", "Masked renamed");
        JsonNode updated = body(send("PUT", "/customer", json.writeValueAsString(read)), 200);
        assertEquals("Masked renamed", updated.get("name").asText(), updated.toString());
        assertEquals(masked, updated.get("email").asText(), updated.toString());
        assertEquals(email, storedEmail(id));

        read.put("name", "Masked batched");
        send("PUT", "/customer/batch", "[" + json.writeValueAsString(read) + "]", 200);
        assertEquals(email, storedEmail(id));

        String moved = "moved" + unique() + "@a.com";
        read.put("email", moved);
        send("PUT", "/customer", json.writeValueAsString(read), 200);
        assertEquals(moved, storedEmail(id));

        // The old masked text is not the new address masked: written as sent, as any other text.
        read.put("email", masked);
        send("PUT", "/customer", json.writeValueAsString(read), 200);
        assertEquals(masked, storedEmail(id));
    }

    /**
     * POST /customer/export (issue 271) over MongoDB: the documents the search finds, every page of them, in the
     * columns asked for - the email masked as the response masks it (@Mask(EMAIL)), a text a spreadsheet would run
     * written as text.
     */
    @Test
    public void anExportWritesWhatTheSearchFindsMasked() throws Exception {
        String tag = unique();
        String local = "exp" + unique();
        long first = id(create("customer", "{\"name\":\"=" + tag + " One\",\"email\":\"" + local + "@a.com\"}"),
                "customerId");
        long second = id(create("customer", "{\"name\":\"" + tag + " Two\"}"), "customerId");

        HttpResponse<String> response = send("POST", "/customer/export",
                "{\"search\":{\"name\":\"" + tag + "\"},\"columns\":[\"customerId\",\"name\",\"email\"]}", 200);
        assertEquals("text/csv;charset=UTF-8", response.headers().firstValue("Content-Type").orElse(""));
        String[] lines = response.body().split("\r\n", -1);
        assertEquals("\uFEFFcustomerId,name,email", lines[0]);
        String masked = local.substring(0, 2) + "*".repeat(local.length() - 2) + "@a.com";
        assertEquals(first + ",'=" + tag + " One," + masked, lines[1], response.body());
        assertEquals(second + "," + tag + " Two,", lines[2], response.body());
        assertEquals(4, lines.length, response.body());
        assertFalse(response.body().contains(local + "@"), response.body());
    }

    private String storedEmail(long customerId) {
        Document stored = mongo.getCollection("customer").find(new Document("_id", customerId)).first();
        assertNotNull(stored);
        return stored.getString("email");
    }

    // ---- helpers ----------------------------------------------------------------------

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private JsonNode create(String entity, String body) throws Exception {
        return body(send("POST", "/" + entity, body), 200);
    }

    private long total(String entity, String search) throws Exception {
        return body(send("POST", "/" + entity + "/search", search), 200).get("totalElements").asLong();
    }

    private static long id(JsonNode node, String field) {
        assertNotNull(node.get(field), node.toString());
        return node.get(field).asLong();
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/shop" + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path, String body, int status) throws Exception {
        HttpResponse<String> response = send(method, path, body);
        assertEquals(status, response.statusCode(), method + " " + path + ": " + response.body());
        return response;
    }

    private JsonNode body(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        return json.readTree(response.body());
    }
}
