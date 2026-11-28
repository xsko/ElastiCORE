package io.elasticore.sample.petstore;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET .../schema/info answered from the model (env.yml config: schemaInfo: model, issue 373), over HTTP on the pet
 * store - JPA, the hexagon, Spring Boot 4 and Jackson 3: the entity, and each field with the keys the answer always
 * had (DTOUtils.getMetaInfo) and what the model says of it, its labels in the request's language.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SchemaInfoTest {

    @Autowired MockMvc mvc;

    private String info(String entity, String language) throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/petstore/" + entity + "/schema/info")
                        .header("Accept-Language", language))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        // In the caller's language: no shared cache keeps it for another.
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private");
        response.setCharacterEncoding("UTF-8");
        return response.getContentAsString();
    }

    @Test
    void theEntityAndALabelInTheRequestsLanguage() throws Exception {
        String en = info("item", "en");
        assertThat((Integer) JsonPath.read(en, "$.metaVersion")).isEqualTo(1);
        assertThat((String) JsonPath.read(en, "$.language")).isEqualTo("en");
        assertThat((String) JsonPath.read(en, "$.entity.name")).isEqualTo("Item");
        assertThat((String) JsonPath.read(en, "$.entity.domain")).isEqualTo("petstore");
        assertThat((String) JsonPath.read(en, "$.entity.label")).isEqualTo("Item for sale");
        assertThat((String) JsonPath.read(en, "$.entity.labels.ja")).isEqualTo("販売単位");
        assertThat((String) JsonPath.read(en, "$.entity.path")).isEqualTo("/api/petstore/item");
        assertThat((String) JsonPath.read(en, "$.entity.key")).isEqualTo("itemId");
        assertThat((String) JsonPath.read(en, "$.entity.dto")).isEqualTo("ItemDTO");
        assertThat((String) JsonPath.read(en, "$.entity.searchDto")).isEqualTo("ItemSrchDTO");

        // unitCost: @label(en=Unit cost, ja=単価) -- 단가 - said in the request's language, every language in labels.
        assertThat((String) JsonPath.read(en, "$.fields.unitCost.label")).isEqualTo("Unit cost");
        assertThat((String) JsonPath.read(info("item", "ja"), "$.fields.unitCost.label")).isEqualTo("単価");
        String ko = info("item", "ko");
        assertThat((String) JsonPath.read(ko, "$.language")).isEqualTo("ko");
        assertThat((String) JsonPath.read(ko, "$.fields.unitCost.label")).isEqualTo("단가");
        assertThat((String) JsonPath.read(ko, "$.entity.label")).isEqualTo("판매 단위 (Large, Male Puppy ...)");
        Map<String, String> labels = JsonPath.read(ko, "$.fields.unitCost.labels");
        assertThat(labels).containsEntry("en", "Unit cost").containsEntry("ja", "単価").hasSize(2);
    }

    @Test
    void eachFieldKeepsWhatItAlwaysSaidAndGainsWhatTheModelSays() throws Exception {
        String json = info("item", "en");
        // name: string(30)! - today's keys, then the model's.
        assertThat((String) JsonPath.read(json, "$.fields.name.type")).isEqualTo("String");
        assertThat((String) JsonPath.read(json, "$.fields.name.description")).isEqualTo("name");
        assertThat((Integer) JsonPath.read(json, "$.fields.name.maxSize")).isEqualTo(30);
        assertThat((String) JsonPath.read(json, "$.fields.name.kind")).isEqualTo("String");
        assertThat((Boolean) JsonPath.read(json, "$.fields.name.required")).isTrue();
        assertThat((Integer) JsonPath.read(json, "$.fields.name.maxLength")).isEqualTo(30);
        assertThat((Boolean) JsonPath.read(json, "$.fields.name.readOnly")).isFalse();
        assertThat((Boolean) JsonPath.read(json, "$.fields.imagePath.required")).isFalse();
        assertThat((Integer) JsonPath.read(json, "$.fields.imagePath.maxLength")).isEqualTo(100);
        List<Object> minLength = JsonPath.read(json, "$.fields[?(@.minLength)]");
        assertThat(minLength).isEmpty();

        // unitCost: money(10.2)! @min(0.01, 'price must be positive').
        assertThat((String) JsonPath.read(json, "$.fields.unitCost.type")).isEqualTo("Money");
        assertThat((String) JsonPath.read(json, "$.fields.unitCost.description")).isEqualTo("단가");
        assertThat((String) JsonPath.read(json, "$.fields.unitCost.kind")).isEqualTo("Money");
        assertThat(new BigDecimal(String.valueOf((Object) JsonPath.read(json, "$.fields.unitCost.min"))))
                .isEqualByComparingTo("0.01");

        // productName @ref(product.name): shown, never written.
        assertThat((Boolean) JsonPath.read(json, "$.fields.productName.readOnly")).isTrue();
        assertThat((Boolean) JsonPath.read(json, "$.fields.productName.required")).isFalse();
    }

    @Test
    void aReferenceSaysWhatItPointsAtAndWhatShowsIt() throws Exception {
        String json = info("item", "en");
        // productId: the product's key (product: Product @ManyToOne, by id in the hexagon).
        assertThat((String) JsonPath.read(json, "$.fields.productId.type")).isEqualTo("Long");
        assertThat((String) JsonPath.read(json, "$.fields.productId.kind")).isEqualTo("Ref");
        assertThat((String) JsonPath.read(json, "$.fields.productId.ref.entity")).isEqualTo("Product");
        assertThat((String) JsonPath.read(json, "$.fields.productId.ref.domain")).isEqualTo("petstore");
        assertThat((String) JsonPath.read(json, "$.fields.productId.ref.key")).isEqualTo("productId");
        assertThat((String) JsonPath.read(json, "$.fields.productId.ref.search"))
                .isEqualTo("/api/petstore/product/search");
        // ItemDTO: productName @ref(product.name) beside it.
        assertThat((String) JsonPath.read(json, "$.fields.productId.ref.display.field")).isEqualTo("productName");
        assertThat((String) JsonPath.read(json, "$.fields.productId.ref.display.of")).isEqualTo("name");
        // A reference with no such field beside it has no display.
        String order = info("purchaseOrder", "en");
        Map<String, Object> customer = JsonPath.read(order, "$.fields.customerId.ref");
        assertThat(customer).containsEntry("entity", "Customer").containsEntry("key", "customerId").doesNotContainKey("display");
        // An embedded value is carried inline: no reference.
        assertThat((String) JsonPath.read(order, "$.fields.deliveryAddress.kind")).isEqualTo("Object");
    }

    /** POST .../search with a body, its rows' values at a path. */
    private List<String> search(String entity, String body, String path) throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/petstore/" + entity + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        response.setCharacterEncoding("UTF-8");
        return JsonPath.read(response.getContentAsString(), path);
    }

    @Test
    void theSearchSaysWhatItTakesAndAPropertyItNamesFilters() throws Exception {
        String json = info("item", "en");
        // @expose(pageSize=50): every field of Item, as the search DTO names it; name: string(30)! @search(like).
        assertThat((Integer) JsonPath.read(json, "$.search.pageSize")).isEqualTo(50);
        List<String> sortable = JsonPath.read(json, "$.search.sortable");
        assertThat(sortable).contains("itemId", "name", "unitCost", "product.name");
        Map<String, Object> fields = JsonPath.read(json, "$.search.fields");
        assertThat(fields).containsOnlyKeys("itemId", "name", "description", "imagePath", "unitCost", "productId");
        assertThat((String) JsonPath.read(json, "$.search.fields.name.field")).isEqualTo("name");
        // The 3.0 like on text: contains, ignoring case.
        assertThat((String) JsonPath.read(json, "$.search.fields.name.op")).isEqualTo("like");
        assertThat((String) JsonPath.read(json, "$.search.fields.description.op")).isEqualTo("auto");
        assertThat((String) JsonPath.read(json, "$.search.fields.itemId.op")).isEqualTo("eq");

        // The property the answer names, sent as it says: only the rows whose name contains it, in any case.
        List<String> all = search("item", "{\"pageSize\":500}", "$.content[*].name");
        List<String> puppies = search("item", "{\"pageSize\":500,\"name\":\"PUPPY\"}", "$.content[*].name");
        assertThat(puppies).isNotEmpty().allMatch(n -> n.toLowerCase(java.util.Locale.ROOT).contains("puppy"));
        assertThat(puppies.size()).isLessThan(all.size());
        // Sorted by a property the answer lists.
        List<String> sorted = search("item", "{\"pageSize\":500,\"name\":\"puppy\",\"sortCode\":\"name+\"}",
                "$.content[*].name");
        assertThat(sorted).isSortedAccordingTo(String::compareTo).hasSameSizeAs(puppies);
    }

    @Test
    void wordsLookInEachOfTheirFields() throws Exception {
        // ProductSrchDTO: keyword: string(50) @search(words:name, description, category.name).
        String json = info("product", "en");
        assertThat((String) JsonPath.read(json, "$.search.fields.keyword.op")).isEqualTo("words");
        List<String> in = JsonPath.read(json, "$.search.fields.keyword.field");
        assertThat(in).containsExactly("name", "description", "category.name");
        // Every word, each in any of them: "fish" and "Japan" are both only in Koi's description.
        List<String> found =
                search("product", "{\"pageSize\":500,\"keyword\":\"fish japan\"}", "$.content[*].name");
        assertThat(found).containsExactly("Koi");
    }

    // ---- actions: what this caller may do, decided by the service's own check --------------------------------

    private static final String CARD =
            "\"creditCard\":{\"creditCardNumber\":\"4111111111111111\",\"creditCardType\":\"VISA\",\"creditCardExpDate\":\"12/30\"}";

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, MockHttpSession session) {
        return session == null ? request : request.session(session);
    }

    /** The actions of an entity's GET .../schema/info, for the caller of this session (null: a visitor). */
    private Map<String, Object> actions(String entity, MockHttpSession session) throws Exception {
        MockHttpServletResponse response = mvc.perform(as(get("/api/petstore/" + entity + "/schema/info"), session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        // The answer is this caller's: no shared cache may keep it for another.
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private");
        response.setCharacterEncoding("UTF-8");
        return JsonPath.read(response.getContentAsString(), "$.actions");
    }

    private MockHttpSession signIn(String login, String password) throws Exception {
        MvcResult r = mvc.perform(json(post("/api/petstore/customer/signin"),
                        "{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    /** POST /customer - sign-up, create=public - answered with the new customer's key. */
    private long signUp(String login, MockHttpSession session) throws Exception {
        String body = "{\"login\":\"" + login + "\",\"password\":\"force-1234\",\"firstName\":\"F\",\"lastName\":\"L\","
                + "\"homeAddress\":{\"street1\":\"1 Main St\",\"city\":\"Mos Eisley\",\"zipcode\":\"12345\","
                + "\"countryCode\":\"US\"}}";
        String created = mvc.perform(as(json(post("/api/petstore/customer"), body), session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(created, "$.customerId")).longValue();
    }

    private String placeOrder(MockHttpSession session, int itemId) throws Exception {
        String placed = mvc.perform(as(
                                json(post("/api/petstore/purchaseOrder"),
                                        "{\"lines\":[{\"itemId\":" + itemId + ",\"quantity\":1}]," + CARD + "}"),
                                session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(placed, "$.orderId");
    }

    private static Map<String, Object> expected(Object... keysAndValues) {
        Map<String, Object> out = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i < keysAndValues.length; i += 2) out.put((String) keysAndValues[i], keysAndValues[i + 1]);
        return out;
    }

    /**
     * PurchaseOrder: {@code @owner(customerId) @access(read=owner|MANAGER, create=user, cancel=owner,
     * delete=MANAGER&owner)}, its lifecycle moving ship and cancel and allowing delete when CANCELLED; ship and
     * update are not declared, so they are the administrator's - and no update is served (nothing @editable).
     * Customer: {@code @owner(customerId) @access(read=owner, create=public, update=owner)}, delete the
     * administrator's. Each caller is told something else, and the service agrees with each answer.
     */
    @Test
    void eachCallerIsToldWhatTheServiceLetsThemDo() throws Exception {
        // A visitor: no order at all; a customer may be created (sign-up) and nothing else.
        assertThat(actions("purchaseOrder", null))
                .isEqualTo(expected(
                        "read", false, "create", false, "delete", false, "ship", false, "cancel", false));
        assertThat(actions("customer", null))
                .isEqualTo(expected("read", false, "create", true, "update", false, "delete", false));
        // false: placing an order asks a visitor to sign in (401); true: signing up works.
        mvc.perform(json(post("/api/petstore/purchaseOrder"), "{\"lines\":[{\"itemId\":1001,\"quantity\":1}]," + CARD + "}"))
                .andExpect(status().isUnauthorized());
        String stamp = String.valueOf(System.nanoTime() % 100000000L);
        long managerId = signUp("m" + stamp, null);

        // A plain customer (USER): their own orders, and their own record.
        MockHttpSession user = signIn("user", "user");
        assertThat(actions("purchaseOrder", user))
                .isEqualTo(expected(
                        "read", List.of("owner"),
                        "create", true,
                        "delete", false,
                        "ship", false,
                        "cancel", List.of("owner", "state")));
        assertThat(actions("customer", user))
                .isEqualTo(expected(
                        "read", List.of("owner"), "create", true, "update", List.of("owner"), "delete", false));
        // true: an order is placed; false: shipping it is the shop's (403).
        String usersOrder = placeOrder(user, 1001);
        mvc.perform(post("/api/petstore/purchaseOrder/" + usersOrder + "/ship").session(user))
                .andExpect(status().isForbidden());
        // ["owner", "state"]: their own order, while it is PLACED - then no more (409).
        mvc.perform(post("/api/petstore/purchaseOrder/" + usersOrder + "/cancel").session(user))
                .andExpect(status().isOk());
        mvc.perform(post("/api/petstore/purchaseOrder/" + usersOrder + "/cancel").session(user))
                .andExpect(status().isConflict());

        // The administrator: every operation; the lifecycle still decides the moves and the delete.
        MockHttpSession admin = signIn("admin", "admin");
        assertThat(actions("purchaseOrder", admin))
                .isEqualTo(expected(
                        "read", true,
                        "create", true,
                        "delete", List.of("state"),
                        "ship", List.of("state"),
                        "cancel", List.of("state")));
        assertThat(actions("customer", admin))
                .isEqualTo(expected("read", true, "create", true, "update", true, "delete", true));
        // No false for the administrator here: they pass every level but none, which no entity of the pet store says.
        // true: every customer is searched; ["state"]: a cancelled order is not shipped (409), a placed one is.
        mvc.perform(json(post("/api/petstore/customer/search"), "{}").session(admin)).andExpect(status().isOk());
        mvc.perform(post("/api/petstore/purchaseOrder/" + usersOrder + "/ship").session(admin))
                .andExpect(status().isConflict());
        String placed = placeOrder(user, 1002);
        mvc.perform(post("/api/petstore/purchaseOrder/" + placed + "/ship").session(admin)).andExpect(status().isOk());

        // A MANAGER: every order is read; deleting is theirs on their own cancelled ones.
        mvc.perform(json(put("/api/petstore/customer"), "{\"customerId\":" + managerId + ",\"role\":\"M\"}")
                        .session(admin))
                .andExpect(status().isOk());
        MockHttpSession manager = signIn("m" + stamp, "force-1234");
        assertThat(actions("purchaseOrder", manager))
                .isEqualTo(expected(
                        "read", true,
                        "create", true,
                        "delete", List.of("owner", "state"),
                        "ship", false,
                        "cancel", List.of("owner", "state")));
        // true: the user's order is read, though it is not theirs; false: shipping it is the shop's (403).
        mvc.perform(get("/api/petstore/purchaseOrder/" + usersOrder).session(manager)).andExpect(status().isOk());
        mvc.perform(post("/api/petstore/purchaseOrder/" + usersOrder + "/ship").session(manager))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEnumerationsValuesInTheRequestsLanguage() throws Exception {
        String en = info("purchaseOrder", "en");
        // Today's keys: the type is "enumeration", the constants as the class has them.
        assertThat((String) JsonPath.read(en, "$.fields.status.type")).isEqualTo("enumeration");
        assertThat((String) JsonPath.read(en, "$.fields.status.enums[0].enum")).isEqualTo("PLACED");
        assertThat((String) JsonPath.read(en, "$.fields.status.enums[0].value")).isEqualTo("PLC");
        // The model's: the lifecycle (states:) writes status - read-only; @label(en=Order status) -- 주문 상태.
        assertThat((String) JsonPath.read(en, "$.fields.status.kind")).isEqualTo("Enum");
        assertThat((Boolean) JsonPath.read(en, "$.fields.status.readOnly")).isTrue();
        assertThat((String) JsonPath.read(en, "$.fields.status.label")).isEqualTo("Order status");
        assertThat((String) JsonPath.read(en, "$.fields.status.options[0].value")).isEqualTo("PLC");
        assertThat((String) JsonPath.read(en, "$.fields.status.options[0].name")).isEqualTo("PLACED");
        assertThat((String) JsonPath.read(en, "$.fields.status.options[0].label")).isEqualTo("Placed");
        assertThat((String) JsonPath.read(en, "$.fields.status.options[0].labels.ko")).isEqualTo("주문됨");
        assertThat(JsonPath.<Map<String, Object>>read(en, "$.fields.status")).doesNotContainKey("enum");

        String ko = info("purchaseOrder", "ko");
        assertThat((String) JsonPath.read(ko, "$.fields.status.label")).isEqualTo("주문 상태");
        assertThat((String) JsonPath.read(ko, "$.fields.status.options[1].label")).isEqualTo("배송됨");
    }
}
