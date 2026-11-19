package ktmongo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * The generated MongoDB shop in Kotlin, over HTTP and a real MongoDB: the
 * requests and answers of elasticore-template-mongo's Java shop, word for word
 * - what the Java code answers, the Kotlin code answers the same way, and
 * keeps the same documents, read back with MongoTemplate.
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
        send("POST", "/customer", "{\"name\":\"Lee 2\",\"email\":\"" + email + "\"}", 409);
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
