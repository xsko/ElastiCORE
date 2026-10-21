package ddd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.elasticore.sample.ddd.shop.application.OrderCoreService;
import io.elasticore.sample.ddd.shop.application.OrderService;
import io.elasticore.sample.ddd.shop.application.dto.OrderSrchDTO;
import io.elasticore.sample.ddd.shop.domain.event.OrderPlacedEvent;
import io.elasticore.sample.ddd.shop.domain.model.Order;
import io.elasticore.sample.ddd.shop.domain.model.OrderItem;
import io.elasticore.sample.ddd.shop.domain.repository.OrderRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * What an independent review of the generated hexagon found, held shut.
 *
 * Every case here passed through the generated controller, service and
 * mapper unchallenged before - each one returned 200 or 500 and did the
 * wrong thing quietly: a stale write undid a newer one, a create overwrote a
 * row, a client named its own audit trail, a member added the obvious way
 * vanished on save. The fixes are in the generator; this is what keeps them
 * there.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class ApiContractTest {

    private static final String ORDERS = "/api/v1/shop/order";
    private static final String CUSTOMERS = "/api/v1/shop/customer";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private ApplicationContext context;
    @Autowired private OrderRepositoryPort orders;
    @Autowired private TransactionTemplate tx;
    @Autowired private ApplicationEvents events;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

    private String createOrder(String notes) throws Exception {
        String body = mvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderDate\":\"2026-08-15 10:00:00\",\"notes\":\"" + notes + "\","
                                + "\"items\":[{\"itemSeq\":1,\"quantity\":1,\"unitPrice\":10.00}]}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("orderId").asText();
    }

    private JsonNode read(String path) throws Exception {
        return json.readTree(mvc.perform(get(path)).andReturn().getResponse().getContentAsString());
    }

    private int send(String path, String body) throws Exception {
        return mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("a write carrying a stale version is refused, not applied over the newer one")
    void staleVersionIsAConflict() throws Exception {
        String id = createOrder("v");
        long v0 = read(ORDERS + "/" + id).path("version").asLong();

        assertThat(send(ORDERS, "{\"orderId\":\"" + id + "\",\"notes\":\"first\",\"version\":" + v0 + "}"))
                .isEqualTo(200);
        assertThat(send(ORDERS, "{\"orderId\":\"" + id + "\",\"notes\":\"second\",\"version\":" + v0 + "}"))
                .as("the second writer read the same version; its write would undo the first")
                .isEqualTo(409);
        assertThat(read(ORDERS + "/" + id).path("notes").asText()).isEqualTo("first");

        assertThat(send(ORDERS, "{\"orderId\":\"" + id + "\",\"notes\":\"no version sent\"}"))
                .as("a request that sends no version is not checked")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("a create naming an identifier that is taken is refused, not merged over it")
    void createNeverOverwrites() throws Exception {
        String id = createOrder("original");
        long version = read(ORDERS + "/" + id).path("version").asLong();

        int status = mvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + id + "\",\"version\":" + version
                                + ",\"orderDate\":\"2026-01-01 00:00:00\",\"notes\":\"overwritten\","
                                + "\"items\":[{\"itemSeq\":1,\"quantity\":1,\"unitPrice\":1.00}]}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(409);
        assertThat(read(ORDERS + "/" + id).path("notes").asText()).isEqualTo("original");
    }

    @Test
    @DisplayName("the audit trail and the version are the persistence layer's to write")
    void auditFieldsAreNotClientWritable() throws Exception {
        String id = createOrder("audit");
        long version = read(ORDERS + "/" + id).path("version").asLong();

        send(ORDERS, "{\"orderId\":\"" + id + "\",\"notes\":\"x\","
                + "\"lastModifiedBy\":\"mallory\",\"lastModifiedDate\":\"2000-01-01 00:00:00\","
                + "\"createdBy\":\"mallory\"}");

        JsonNode after = read(ORDERS + "/" + id);
        assertThat(after.path("lastModifiedBy").asText()).isNotEqualTo("mallory");
        assertThat(after.path("createdBy").asText()).isNotEqualTo("mallory");
        assertThat(after.path("lastModifiedDate").asText())
                .as("Spring Data auditing writes the date, not the request")
                .isNotEqualTo("2000-01-01 00:00:00")
                .isNotEmpty();
        assertThat(after.path("version").asLong()).isGreaterThan(version);
    }

    @Test
    @DisplayName("an update is validated for what it sends: 400, not a database error")
    void invalidUpdateIsABadRequest() throws Exception {
        String id = createOrder("valid");
        assertThat(send(ORDERS, "{\"orderId\":\"" + id + "\",\"trackingNumber\":\"" + "x".repeat(60) + "\"}"))
                .isEqualTo(400);
        assertThat(send(ORDERS, "{\"orderId\":\"" + id + "\",\"notes\":\"partial is fine\"}"))
                .as("the required fields a partial update leaves out are not demanded of it")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("a member sent with its root is validated with it: a line of quantity 0 is 400")
    void membersAreValidatedWithTheirRoot() throws Exception {
        int created = mvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderDate\":\"2026-08-15 10:00:00\",\"notes\":\"zero\","
                                + "\"items\":[{\"itemSeq\":1,\"quantity\":0,\"unitPrice\":10.00}]}"))
                .andReturn().getResponse().getStatus();
        assertThat(created).as("quantity is @min(1); the line arrives inside the order").isEqualTo(400);

        String id = createOrder("member edit");
        assertThat(send(ORDERS, "{\"orderId\":\"" + id + "\","
                + "\"items\":[{\"itemSeq\":1,\"quantity\":0,\"unitPrice\":10.00}]}"))
                .isEqualTo(400);
        assertThat(read(ORDERS + "/" + id).path("items").get(0).path("quantity").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("@behavior: the state is regenerated in OrderBase, the entity is the developer's Order")
    void behaviourHasItsOwnClass() throws Exception {
        Class<?> order = io.elasticore.sample.ddd.shop.domain.model.Order.class;
        Class<?> base = order.getSuperclass();
        assertThat(base.getSimpleName()).isEqualTo("OrderBase");
        assertThat(java.lang.reflect.Modifier.isAbstract(base.getModifiers())).isTrue();
        // Asked of JPA rather than of the annotations: the shop maps through
        // META-INF/orm.xml (mapping: xml), and the answer must not depend on where.
        jakarta.persistence.metamodel.Metamodel jpa =
                context.getBean(jakarta.persistence.EntityManagerFactory.class).getMetamodel();
        assertThat(jpa.managedType(base)).isInstanceOf(jakarta.persistence.metamodel.MappedSuperclassType.class);
        assertThat(jpa.entity(order)).isNotNull();
        assertThat(indexesOf(order)).as("the table's index moved with the entity").isEqualTo(1);
        assertThat(base.getSuperclass().getSimpleName()).as("@extend still holds").isEqualTo("AuditEntity");

        java.lang.reflect.Method setStatus = base.getDeclaredMethod("setStatus",
                io.elasticore.sample.ddd.shop.domain.model.OrderStatus.class);
        assertThat(java.lang.reflect.Modifier.isProtected(setStatus.getModifiers()))
                .as("a @behavior field is changed by the entity's behaviour only").isTrue();

        String id = createOrder("guarded");
        send(ORDERS, "{\"orderId\":\"" + id + "\",\"status\":\"CAN\"}");
        assertThat(read(ORDERS + "/" + id).path("status").asText())
                .as("a request cannot send it").isNotEqualTo("CAN");
    }

    /** The indexes a class's table declares: on its @Table, or in its orm.xml entry. */
    private static int indexesOf(Class<?> entity) throws Exception {
        jakarta.persistence.Table table = entity.getAnnotation(jakarta.persistence.Table.class);
        if (table != null) return table.indexes().length;
        java.io.InputStream in = ApiContractTest.class.getResourceAsStream("/META-INF/orm.xml");
        assertThat(in).as("no @Table and no orm.xml for " + entity.getName()).isNotNull();
        String xml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int start = xml.indexOf("<entity class=\"" + entity.getName() + "\"");
        int end = xml.indexOf("</entity>", start);
        assertThat(start).as(entity.getName() + " in orm.xml").isGreaterThanOrEqualTo(0);
        return xml.substring(start, end).split("<index ", -1).length - 1;
    }

    @Test
    @DisplayName("an event is a value: it shows what it says and equals its twin")
    void eventsAreValues() {
        java.time.Instant at = java.time.Instant.parse("2026-09-24T00:00:00Z");
        io.elasticore.sample.ddd.shop.domain.event.OrderPlacedEvent a =
                new io.elasticore.sample.ddd.shop.domain.event.OrderPlacedEvent("o-1", new java.math.BigDecimal("19.99"), at);
        io.elasticore.sample.ddd.shop.domain.event.OrderPlacedEvent b =
                new io.elasticore.sample.ddd.shop.domain.event.OrderPlacedEvent("o-1", new java.math.BigDecimal("19.99"), at);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a.toString()).contains("o-1").contains("OrderPlacedEvent").contains("totalAmount=19.99");
        assertThat(a).isNotEqualTo(new io.elasticore.sample.ddd.shop.domain.event.OrderPlacedEvent("o-2", new java.math.BigDecimal("19.99"), at));
    }

    @Test
    @DisplayName("an absent row is 404 and a broken invariant is 422, each saying which")
    void refusalsCarryTheirOwnStatus() throws Exception {
        assertThat(send(ORDERS, "{\"orderId\":\"no-such-order\",\"notes\":\"x\"}")).isEqualTo(404);

        int status = mvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderDate\":\"2026-08-15 10:00:00\",\"notes\":\"no lines\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(status)
                .as("an order without a line is the domain refusing, not the program failing")
                .isEqualTo(422);
    }

    @Test
    @DisplayName("the members cannot be added past the root")
    void theMemberListIsReadOnly() {
        Order order = new Order();
        OrderItem line = new OrderItem();
        order.addOrderItem(line);

        assertThat(order.getItems()).containsExactly(line);
        assertThat(line.getOrder()).isSameAs(order);
        assertThatThrownBy(() -> order.getItems().add(new OrderItem()))
                .as("a member added to the list directly is never given its owner and is dropped on save")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("deleting by condition removes each aggregate with its members")
    void deleteByConditionIsNotABulkDelete() throws Exception {
        mvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderDate\":\"1999-01-01 10:00:00\",\"notes\":\"old\","
                        + "\"items\":[{\"itemSeq\":1,\"quantity\":1,\"unitPrice\":10.00}]}"));

        OrderSrchDTO old = new OrderSrchDTO();
        old.setOrderDateFrom(LocalDateTime.of(1999, 1, 1, 0, 0));
        old.setOrderDateTo(LocalDateTime.of(1999, 1, 2, 0, 0));

        OrderCoreService service = context.getBean(OrderService.class);
        assertThat(service.delete(old))
                .as("a bulk DELETE left the lines behind and failed on their foreign key")
                .isEqualTo(1);
        assertThat(service.countBySearch(old)).isZero();
    }

    @Test
    @DisplayName("the core service is not a second bean beside the one that extends it")
    void oneServiceBeanPerRoot() {
        assertThat(context.getBeansOfType(OrderCoreService.class)).hasSize(1);
    }

    @Test
    @DisplayName("an event records when it was raised, not when it was published")
    void eventTimeIsRaiseTime() throws Exception {
        Order order = new Order();
        order.creation().setOrderDate(LocalDateTime.now());
        OrderItem line = new OrderItem();
        line.setQuantity(1);
        line.setUnitPrice(BigDecimal.ONE);
        order.addOrderItem(line);

        Instant before = Instant.now();
        order.raisePlaced();
        Instant raised = Instant.now();
        Thread.sleep(50);
        tx.executeWithoutResult(s -> orders.save(order));

        List<OrderPlacedEvent> placed = events.stream(OrderPlacedEvent.class).toList();
        assertThat(placed).hasSize(1);
        assertThat(placed.get(0).getOrderId()).as("built once the row had its identity").isNotNull();
        assertThat(placed.get(0).getOccurredOn()).isBetween(before, raised);
    }

    @Test
    @DisplayName("a one-to-one member travels with its root and keeps its identity")
    void singleMemberIsReachableThroughItsRoot() throws Exception {
        String created = mvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Grace\",\"lastName\":\"Hopper\",\"email\":\"grace.vip@example.com\","
                                + "\"vipProfile\":{\"tier\":\"GOLD\",\"discountRate\":10.0}}"))
                .andReturn().getResponse().getContentAsString();
        long customerId = json.readTree(created).path("customerId").asLong();

        JsonNode profile = read(CUSTOMERS + "/" + customerId).path("vipProfile");
        assertThat(profile.path("tier").asText())
                .as("a member has no endpoint; if its root does not carry it, nothing can")
                .isEqualTo("GOLD");
        long profileId = profile.path("profileId").asLong();

        assertThat(send(CUSTOMERS, "{\"customerId\":" + customerId + ",\"vipProfile\":{\"tier\":\"PLATINUM\"}}"))
                .isEqualTo(200);
        JsonNode after = read(CUSTOMERS + "/" + customerId).path("vipProfile");
        assertThat(after.path("tier").asText()).isEqualTo("PLATINUM");
        assertThat(after.path("profileId").asLong())
                .as("changed where it stands, not replaced by a new row")
                .isEqualTo(profileId);

        Long owner = tx.execute(s -> em.createQuery(
                "select v.customer.customerId from VipProfile v where v.profileId = :id", Long.class)
                .setParameter("id", profileId).getSingleResult());
        assertThat(owner).as("the member says whose it is, not only the root").isEqualTo(customerId);
    }
    @Test
    @DisplayName("a member's identity is its own: an id in the request does not move it")
    void memberIdentityIsNotTheRequests() throws Exception {
        String created = mvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Alan\",\"lastName\":\"Turing\",\"email\":\"alan.vip@example.com\","
                                + "\"vipProfile\":{\"profileId\":424242,\"tier\":\"GOLD\"}}"))
                .andReturn().getResponse().getContentAsString();
        long customerId = json.readTree(created).path("customerId").asLong();
        long profileId = read(CUSTOMERS + "/" + customerId).path("vipProfile").path("profileId").asLong();
        assertThat(profileId).as("an echoed id on a create is not persisted as given").isNotEqualTo(424242L);

        assertThat(send(CUSTOMERS, "{\"customerId\":" + customerId
                + ",\"vipProfile\":{\"profileId\":99,\"tier\":\"SILVER\"}}"))
                .as("a different id would alter the key of a loaded row")
                .isEqualTo(200);
        JsonNode after = read(CUSTOMERS + "/" + customerId).path("vipProfile");
        assertThat(after.path("profileId").asLong()).isEqualTo(profileId);
        assertThat(after.path("tier").asText()).isEqualTo("SILVER");
    }

    @Test
    @DisplayName("members sent with an update are validated too")
    void nestedMembersAreValidated() throws Exception {
        String id = createOrder("nested");
        assertThat(send(ORDERS, "{\"orderId\":\"" + id + "\",\"items\":[{\"itemSeq\":1,\"quantity\":0,\"unitPrice\":1.00}]}"))
                .as("a line with quantity 0 breaks @Min(1); it used to reach the database")
                .isEqualTo(400);
    }

    /** A controller written by hand, outside every generated package (registered below, not scanned). */
    @org.springframework.web.bind.annotation.RestController
    static class OutsideController {
        @org.springframework.web.bind.annotation.PostMapping("/outside")
        String take(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body) {
            return "ok";
        }
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class OutsideTheDomain {
        @org.springframework.context.annotation.Bean
        OutsideController outsideController() {
            return new OutsideController();
        }
    }

    private static final MediaType PROBLEM = MediaType.APPLICATION_PROBLEM_JSON;

    private org.springframework.mock.web.MockHttpServletResponse answer(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.accept(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    @Test
    @DisplayName("what Spring MVC refuses before a controller runs is the API's problem body with a code (Boot 3, Jackson 2)")
    void requestShapeRefusalsAreProblems() throws Exception {
        // A coded enum read by the generated CustomDeserializer: its own refusal, at the field.
        org.springframework.mock.web.MockHttpServletResponse coded = answer(post(ORDERS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderDate\":\"2026-08-15 10:00:00\",\"paymentMethod\":\"XX\","
                        + "\"items\":[{\"itemSeq\":1,\"quantity\":1,\"unitPrice\":10.00}]}"));
        assertThat(coded.getStatus()).isEqualTo(400);
        assertThat(MediaType.parseMediaType(coded.getContentType()).isCompatibleWith(PROBLEM)).isTrue();
        JsonNode body = json.readTree(coded.getContentAsString());
        assertThat(body.path("code").asText()).isEqualTo("invalid");
        assertThat(body.path("fieldErrors").get(0).path("field").asText()).isEqualTo("paymentMethod");
        assertThat(body.path("fieldErrors").get(0).path("code").asText()).isEqualTo("typeMismatch");
        assertThat(body.path("fieldErrors").get(0).path("message").asText()).isEqualTo("Unknown PaymentMethod code: XX");

        JsonNode text = json.readTree(answer(post(ORDERS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderDate\":\"2026-08-15 10:00:00\",\"items\":[{\"itemSeq\":1,\"quantity\":\"abc\",\"unitPrice\":10.00}]}"))
                .getContentAsString());
        assertThat(text.path("fieldErrors").get(0).path("field").asText()).isEqualTo("items[0].quantity");
        assertThat(text.path("fieldErrors").get(0).path("message").asText()).isEqualTo("must be a number");

        assertThat(json.readTree(answer(post(ORDERS).contentType(MediaType.APPLICATION_JSON).content("{\"notes\":"))
                .getContentAsString()).path("code").asText()).isEqualTo("unreadable");
        assertThat(json.readTree(answer(get(CUSTOMERS + "/abc")).getContentAsString())
                .path("fieldErrors").get(0).path("code").asText()).isEqualTo("typeMismatch");

        org.springframework.mock.web.MockHttpServletResponse method = answer(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(ORDERS));
        assertThat(method.getStatus()).isEqualTo(405);
        assertThat(method.getHeader("Allow")).contains("POST");
        assertThat(json.readTree(method.getContentAsString()).path("code").asText()).isEqualTo("method-not-allowed");

        assertThat(json.readTree(answer(post(ORDERS).contentType(MediaType.TEXT_PLAIN).content("x"))
                .getContentAsString()).path("code").asText()).isEqualTo("unsupported-media-type");

        org.springframework.mock.web.MockHttpServletResponse nowhere = answer(get("/api/v1/shop/nothing/here"));
        assertThat(nowhere.getStatus()).isEqualTo(404);
        assertThat(json.readTree(nowhere.getContentAsString()).path("code").asText()).isEqualTo("not-found");
        // A browser keeps Spring Boot's error page.
        org.springframework.mock.web.MockHttpServletResponse page = mvc.perform(get("/api/v1/shop/nothing/here")
                .accept(MediaType.TEXT_HTML)).andReturn().getResponse();
        assertThat(page.getStatus()).isEqualTo(404);
        assertThat(page.getContentAsString()).doesNotContain("\"code\"");

        // A controller outside every generated package gets the same answer.
        assertThat(json.readTree(answer(post("/outside").contentType(MediaType.APPLICATION_JSON).content("{\"a\":"))
                .getContentAsString()).path("code").asText()).isEqualTo("unreadable");
    }

    @Test
    @DisplayName("on a Korean machine a field error and a refused body are said in English unless the request asks for Korean")
    void errorsAreNotSaidInTheMachinesLanguage() throws Exception {
        String id = createOrder("language");
        String tooLong = "{\"orderId\":\"" + id + "\",\"trackingNumber\":\"" + "x".repeat(60) + "\"}";
        java.util.regex.Pattern hangul = java.util.regex.Pattern.compile("[\\uAC00-\\uD7A3]");
        for (String language : new String[]{null, "en", "sv"}) {
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder field = put(ORDERS)
                    .contentType(MediaType.APPLICATION_JSON).content(tooLong);
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder text = post(ORDERS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"orderDate\":\"2026-08-15 10:00:00\",\"items\":[{\"itemSeq\":1,\"quantity\":\"abc\",\"unitPrice\":10.00}]}");
            if (language != null) {
                field.header("Accept-Language", language);
                text.header("Accept-Language", language);
            }
            for (org.springframework.mock.web.MockHttpServletResponse r : new org.springframework.mock.web.MockHttpServletResponse[]{
                    answer(field), answer(text)}) {
                String body = r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                assertThat(r.getStatus()).isEqualTo(400);
                assertThat(hangul.matcher(body).find()).as("Accept-Language " + language + ": " + body).isFalse();
            }
        }
    }

    @Test
    @DisplayName("GET of a row that is not there answers the problem body, not an empty 404")
    void findByIdOfNothingIsAProblem() throws Exception {
        org.springframework.mock.web.MockHttpServletResponse missing = answer(get(ORDERS + "/no-such-order"));
        assertThat(missing.getStatus()).isEqualTo(404);
        JsonNode body = json.readTree(missing.getContentAsString());
        assertThat(body.path("code").asText()).isEqualTo("not-found");
        assertThat(body.path("detail").asText()).isEqualTo("Order not found");
    }

    @Test
    @DisplayName("nothing is written from a member back into its root")
    void noMappingRunsFromMemberToRoot() throws Exception {
        String mapper = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src/main/java/io/elasticore/sample/ddd/shop/application/dto/ShopMapper.java")),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(mapper)
                .as("the member-to-root direction would let a request edit the root through its part")
                .doesNotContain("Customer m = to.getCustomer()")
                .doesNotContain("Customer m = to.getCustomer();");
    }
}
