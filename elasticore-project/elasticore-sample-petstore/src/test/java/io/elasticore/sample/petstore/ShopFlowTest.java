package io.elasticore.sample.petstore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The shop as a browser uses it: browse, sign in, check out, look at the orders. */
@SpringBootTest
@AutoConfigureMockMvc
class ShopFlowTest {

    @Autowired MockMvc mvc;

    private static final String CARD = "\"creditCard\":{\"creditCardNumber\":\"4111111111111111\",\"creditCardType\":\"VISA\",\"creditCardExpDate\":\"12/30\"}";

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpSession signIn(String login, String password) throws Exception {
        MvcResult r = mvc.perform(json(post("/api/petstore/customer/signin"),
                        "{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    @Test
    void aVisitorBrowsesTheCatalog() throws Exception {
        mvc.perform(json(post("/api/petstore/category/search"), "{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)));
        mvc.perform(json(post("/api/petstore/product/search"), "{\"categoryId\":1001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Bulldog"));
        // @sort(name+): a search that names no order is in name order (stored: Angelfish, Tiger Shark, Koi, Goldfish).
        mvc.perform(json(post("/api/petstore/product/search"), "{\"categoryId\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(org.hamcrest.Matchers.contains("Angelfish", "Goldfish", "Koi", "Tiger Shark")));
        // One the request names still wins.
        mvc.perform(json(post("/api/petstore/product/search"), "{\"categoryId\":1000,\"sortCode\":\"name-\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Tiger Shark"));
        mvc.perform(json(post("/api/petstore/item/search"), "{\"name\":\"%Puppy%\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productName").exists());
        // @search(like) is part of the name, whatever its case: "gold" finds Golden Retriever and Goldfish
        // (name order), "  BULL " the Bulldog.
        mvc.perform(json(post("/api/petstore/product/search"), "{\"name\":\"gold\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(org.hamcrest.Matchers.contains("Golden Retriever", "Goldfish")));
        mvc.perform(json(post("/api/petstore/product/search"), "{\"name\":\"  BULL \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(org.hamcrest.Matchers.contains("Bulldog")));
        mvc.perform(get("/api/petstore/item/1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitCost").value(10.00));
    }

    @Test
    void aVisitorCannotChangeTheCatalogNorOrder() throws Exception {
        mvc.perform(json(post("/api/petstore/category"), "{\"name\":\"Fake\",\"description\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        // A well-formed order: the request is checked first (400), then who asks (401).
        mvc.perform(json(post("/api/petstore/purchaseOrder"), "{\"lines\":[{\"itemId\":1000,\"quantity\":1}]," + CARD + "}"))
                .andExpect(status().isUnauthorized());
        MockHttpSession user = signIn("user", "user");
        mvc.perform(json(post("/api/petstore/category"), "{\"name\":\"Fake\",\"description\":\"x\"}").session(user))
                .andExpect(status().isForbidden());
        // Customers are read by their owner: a user's search finds themselves alone.
        mvc.perform(json(post("/api/petstore/customer/search"), "{}").session(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].login").value("user"));
        mvc.perform(delete("/api/petstore/item/1000").session(user))
                .andExpect(status().isForbidden());
    }

    @Test
    void aWrongPasswordIsRefused() throws Exception {
        mvc.perform(json(post("/api/petstore/customer/signin"), "{\"login\":\"user\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Wrong login or password"));
    }

    /** The generated sign-in (@signin): who am I, sign out, and a visitor. */
    @Test
    void signingOutEndsTheSession() throws Exception {
        MockHttpSession user = signIn("user", "user");
        mvc.perform(get("/api/petstore/customer/me").session(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("user"));
        mvc.perform(post("/api/petstore/customer/signout").session(user))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/petstore/customer/me").session(user))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/petstore/customer/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aCustomerChecksOutAndTheShopPricesTheCart() throws Exception {
        MockHttpSession user = signIn("user", "user");
        // The client claims a price of 0.01; the shop charges the catalog's.
        String cart = "{\"lines\":[{\"itemId\":1000,\"quantity\":2,\"unitCost\":0.01},{\"itemId\":1001,\"quantity\":1}]," + CARD + "}";
        mvc.perform(json(post("/api/petstore/purchaseOrder"), cart).session(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLC"))
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.totalWithoutVat").value(30.00))   // 2 x 10.00 + 10.00
                .andExpect(jsonPath("$.vat").value(1.65))                // 5.5 %
                .andExpect(jsonPath("$.discount").value(0.71))           // 2.25 % of 31.65
                .andExpect(jsonPath("$.total").value(30.94))
                .andExpect(jsonPath("$.deliveryAddress.street1").value("The User Boulevard"))
                .andExpect(jsonPath("$.creditCard.creditCardType").value("VISA"))
                .andExpect(content().string(not(containsString("4111111111111111"))));

        // The same search an administrator runs; a customer's is narrowed to their own orders.
        mvc.perform(json(post("/api/petstore/purchaseOrder/search"), "{}").session(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lines[0].itemName").exists())
                .andExpect(jsonPath("$.content[*].customerId", everyItem(is(2))));
        // Asking for someone else's is asking for rows that are not yours: none.
        mvc.perform(json(post("/api/petstore/purchaseOrder/search"), "{\"customerId\":1}").session(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void anEmptyCartOrAMissingCardIsABadRequest() throws Exception {
        MockHttpSession user = signIn("user", "user");
        mvc.perform(json(post("/api/petstore/purchaseOrder"), "{\"lines\":[]," + CARD + "}").session(user))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/petstore/purchaseOrder"),
                        "{\"lines\":[{\"itemId\":1000,\"quantity\":1}],\"creditCard\":{\"creditCardType\":\"VISA\",\"creditCardExpDate\":\"12/30\"}}").session(user))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/petstore/purchaseOrder"),
                        "{\"lines\":[{\"itemId\":1000,\"quantity\":0}]," + CARD + "}").session(user))
                .andExpect(status().isBadRequest());
        // No lines at all is the same empty cart: 400, not the entity's 422.
        mvc.perform(json(post("/api/petstore/purchaseOrder"), "{" + CARD + "}").session(user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines"));
        // An item that does not exist: 404, said by a code a program can branch on.
        mvc.perform(json(post("/api/petstore/purchaseOrder"),
                        "{\"lines\":[{\"itemId\":99999,\"quantity\":1}]," + CARD + "}").session(user))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("reference.OrderLine.item"))
                // The id as written: a number passed to a message is not grouped (99,999).
                .andExpect(jsonPath("$.detail").value("Item 99999 does not exist"));
    }

    @Test
    void anOrderIsCancelledOnceAndShippedByTheShopOnly() throws Exception {
        MockHttpSession user = signIn("user", "user");
        MvcResult placed = mvc.perform(json(post("/api/petstore/purchaseOrder"),
                        "{\"lines\":[{\"itemId\":1002,\"quantity\":1}]," + CARD + "}").session(user))
                .andExpect(status().isOk()).andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(placed.getResponse().getContentAsString(), "$.orderId");

        mvc.perform(post("/api/petstore/purchaseOrder/" + id + "/ship").session(user))
                .andExpect(status().isForbidden());

        MockHttpSession admin = signIn("admin", "admin");
        mvc.perform(post("/api/petstore/purchaseOrder/" + id + "/ship").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHP"));

        // Shipped: too late to cancel - 409, the order's state conflicts - said by a code a program can branch on.
        mvc.perform(post("/api/petstore/purchaseOrder/" + id + "/cancel").session(user))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("state.PurchaseOrder.status.cancel"))
                .andExpect(jsonPath("$.detail").value("PurchaseOrder cannot cancel when status is SHIPPED; it must be [PLACED]"));

        // And in the caller's language, from i18n/petstore-messages_ko.properties.
        mvc.perform(post("/api/petstore/purchaseOrder/" + id + "/cancel").session(user).header("Accept-Language", "ko"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("지금 상태에서는 할 수 없는 요청"))
                .andExpect(jsonPath("$.detail").value("주문 상태가 SHIPPED이면 취소할 수 없습니다 (가능한 상태: [PLACED])"));
    }

    /** states: delete - an order is deleted only once cancelled; a shipped one stays (409). */
    @Test
    void onlyACancelledOrderIsDeleted() throws Exception {
        MockHttpSession user = signIn("user", "user");
        MockHttpSession admin = signIn("admin", "admin");
        String shipped = placeOrder(user, 1004);
        mvc.perform(post("/api/petstore/purchaseOrder/" + shipped + "/ship").session(admin)).andExpect(status().isOk());
        mvc.perform(delete("/api/petstore/purchaseOrder/" + shipped).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("state.PurchaseOrder.status.delete"))
                .andExpect(jsonPath("$.detail").value("PurchaseOrder cannot be deleted when status is SHIPPED; it must be [CANCELLED]"));
        mvc.perform(get("/api/petstore/purchaseOrder/" + shipped).session(admin)).andExpect(status().isOk());

        String cancelled = placeOrder(user, 1005);
        mvc.perform(post("/api/petstore/purchaseOrder/" + cancelled + "/cancel").session(user)).andExpect(status().isOk());
        mvc.perform(delete("/api/petstore/purchaseOrder/" + cancelled).session(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/petstore/purchaseOrder/" + cancelled).session(admin)).andExpect(status().isNotFound());
    }

    private String placeOrder(MockHttpSession user, int itemId) throws Exception {
        MvcResult placed = mvc.perform(json(post("/api/petstore/purchaseOrder"),
                        "{\"lines\":[{\"itemId\":" + itemId + ",\"quantity\":1}]," + CARD + "}").session(user))
                .andExpect(status().isOk()).andReturn();
        return com.jayway.jsonpath.JsonPath.read(placed.getResponse().getContentAsString(), "$.orderId");
    }

    /** A language nobody translated is answered in the default wording, not the server machine's. */
    @Test
    void anErrorIsSaidInTheRequestsLanguage() throws Exception {
        MockHttpSession user = signIn("user", "user");
        mvc.perform(post("/api/petstore/purchaseOrder/no-such-order/cancel").session(user).header("Accept-Language", "ko"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not-found"))
                .andExpect(jsonPath("$.title").value("찾을 수 없음"));
        mvc.perform(post("/api/petstore/purchaseOrder/no-such-order/cancel").session(user).header("Accept-Language", "fr"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not found"));
        mvc.perform(json(post("/api/petstore/purchaseOrder"), "{\"lines\":[]," + CARD + "}").session(user)
                        .header("Accept-Language", "ko"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("Size"))
                // A field's message by Spring's codes (Size.lines) in the same bundle.
                .andExpect(jsonPath("$.fieldErrors[0].message").value("장바구니가 비어 있습니다"));
    }

    @Test
    void someoneElsesOrderIsNotThere() throws Exception {
        MockHttpSession user = signIn("user", "user");
        MvcResult placed = mvc.perform(json(post("/api/petstore/purchaseOrder"),
                        "{\"lines\":[{\"itemId\":1003,\"quantity\":1}]," + CARD + "}").session(user))
                .andExpect(status().isOk()).andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(placed.getResponse().getContentAsString(), "$.orderId");

        MockHttpSession other = signUp("vader");
        mvc.perform(get("/api/petstore/purchaseOrder/" + id).session(other)).andExpect(status().isNotFound());
        mvc.perform(post("/api/petstore/purchaseOrder/" + id + "/cancel").session(other)).andExpect(status().isNotFound());
    }

    @Test
    void aNewCustomerSignsUpAsAUserAndThePasswordIsNeverShown() throws Exception {
        MockHttpSession session = signUp("luke");
        mvc.perform(get("/api/petstore/customer/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("luke"))
                .andExpect(jsonPath("$.role").value("U"))
                .andExpect(jsonPath("$.password").doesNotExist());
        signIn("luke", "force-1234");

        Long id = ((Number) com.jayway.jsonpath.JsonPath.read(mvc.perform(get("/api/petstore/customer/me").session(session))
                .andReturn().getResponse().getContentAsString(), "$.customerId")).longValue();
        mvc.perform(json(put("/api/petstore/customer"), "{\"customerId\":" + id + ",\"telephone\":\"+82 10 1234 5678\",\"role\":\"A\"}").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telephone").value("+82 10 1234 5678"))
                .andExpect(jsonPath("$.role").value("U"));
    }

    @Test
    void aSignUpWithAnInvalidAddressIsABadRequest() throws Exception {
        mvc.perform(json(post("/api/petstore/account/signup"), "{\"login\":\"leia\",\"password\":\"p\",\"firstName\":\"Leia\","
                        + "\"lastName\":\"Organa\",\"homeAddress\":{\"city\":\"Alderaan\",\"zipcode\":\"1\",\"countryCode\":\"US\"}}"))
                .andExpect(status().isBadRequest());
    }

    /** A missing required value is the request's fault - 400 - not a conflict with stored data. */
    @Test
    void aSignUpWithoutAnAddressOrAnOrderWithoutACardIsABadRequest() throws Exception {
        mvc.perform(json(post("/api/petstore/account/signup"),
                        "{\"login\":\"leia\",\"password\":\"p-1234\",\"firstName\":\"Leia\",\"lastName\":\"Organa\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("homeAddress")))
                .andExpect(jsonPath("$.code").value("invalid"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("homeAddress"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("NotNull"));
        MockHttpSession user = signIn("user", "user");
        mvc.perform(json(post("/api/petstore/purchaseOrder"), "{\"lines\":[{\"itemId\":1000,\"quantity\":1}]}").session(user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("creditCard")));
    }

    /** The same login twice is a conflict with a stored row, and says so. */
    @Test
    void aTakenLoginIsAConflict() throws Exception {
        signUp("obiwan");
        mvc.perform(json(post("/api/petstore/account/signup"), "{\"login\":\"obiwan\",\"password\":\"force-1234\","
                        + "\"firstName\":\"F\",\"lastName\":\"L\",\"homeAddress\":{\"street1\":\"1 Main St\",\"city\":\"Mos Eisley\","
                        + "\"zipcode\":\"12345\",\"countryCode\":\"US\"}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A unique value is already taken."));
    }

    @Test
    void theCustomerSearchHasNoPasswordCondition() throws Exception {
        MockHttpSession admin = signIn("admin", "admin");
        // An unknown property is ignored: "%" on the password matches nothing - every customer comes back.
        mvc.perform(json(post("/api/petstore/customer/search"), "{\"password\":\"zzz%\"}").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].login").exists());
    }

    /** A search the caller got wrong is the caller's 400, not the server's 500. */
    @Test
    void aBadSearchIsABadRequest() throws Exception {
        mvc.perform(json(post("/api/petstore/item/search"), "{\"sortColumn\":\"noSuchColumn\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/petstore/item/search"), "{\"pageNumber\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/petstore/item/search"), "{\"pageSize\":0}"))
                .andExpect(status().isBadRequest());
    }

    private MockHttpSession signUp(String login) throws Exception {
        MvcResult r = mvc.perform(json(post("/api/petstore/account/signup"), "{\"login\":\"" + login + "\",\"password\":\"force-1234\","
                        + "\"firstName\":\"F\",\"lastName\":\"L\",\"role\":\"A\",\"homeAddress\":{\"street1\":\"1 Main St\",\"city\":\"Mos Eisley\","
                        + "\"zipcode\":\"12345\",\"countryCode\":\"US\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("U"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }
}
