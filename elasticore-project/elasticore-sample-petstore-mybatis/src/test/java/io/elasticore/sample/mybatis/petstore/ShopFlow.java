package io.elasticore.sample.mybatis.petstore;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The shop as a browser uses it - browse, sign up and in, check out, look at
 * the orders - over the generated MyBatis mappers. Run on H2 (ShopFlowTest)
 * and on Oracle, PostgreSQL and SQL Server (ShopFlowOnDatabasesTest), each on
 * the schema the generator wrote for it and the same catalog: the identity
 * columns number it 1, 2, 3 ... so Fish is category 1, Dogs 2, item 1 is the
 * large Angelfish at 10.00 and item 2 the toothless one at 10.00.
 */
abstract class ShopFlow {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired StatementCounter statements;

    static final String CARD = "\"creditCard\":{\"creditCardNumber\":\"4111111111111111\",\"creditCardType\":\"VISA\",\"creditCardExpDate\":\"12/30\"}";

    /** Counts the statements MyBatis prepares, so a test can say how many a request took. */
    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    static class StatementCounter implements Interceptor {
        final List<String> sql = new CopyOnWriteArrayList<String>();

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            sql.add(((StatementHandler) invocation.getTarget()).getBoundSql().getSql());
            return invocation.proceed();
        }
    }

    @TestConfiguration
    static class Counting {
        @Bean
        StatementCounter statementCounter() {
            return new StatementCounter();
        }
    }

    static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    MockHttpSession signIn(String login, String password) throws Exception {
        MvcResult r = mvc.perform(json(post("/api/petstore/customer/signin"),
                        "{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    MockHttpSession signUp(String login) throws Exception {
        mvc.perform(json(post("/api/petstore/customer"), "{\"login\":\"" + login + "\",\"password\":\"force-1234\","
                        + "\"firstName\":\"F\",\"lastName\":\"L\",\"role\":\"A\",\"homeAddress\":{\"street1\":\"1 Main St\","
                        + "\"city\":\"Mos Eisley\",\"zipcode\":\"12345\",\"countryCode\":\"US\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("U"))
                .andExpect(jsonPath("$.password").doesNotExist());
        return signIn(login, "force-1234");
    }

    String placeOrder(MockHttpSession who, String lines) throws Exception {
        MvcResult placed = mvc.perform(json(post("/api/petstore/purchaseOrder"), "{\"lines\":" + lines + "," + CARD + "}")
                        .session(who))
                .andExpect(status().isOk()).andReturn();
        return com.jayway.jsonpath.JsonPath.read(placed.getResponse().getContentAsString(), "$.orderId");
    }

    long linesOf(String orderId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ps_order_line WHERE order_order_id = ?", Long.class, orderId);
    }

    @Test
    void aVisitorBrowsesTheCatalog() throws Exception {
        mvc.perform(json(post("/api/petstore/category/search"), "{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)));
        // @sort(name+): a search that names no order is in name order (stored: Angelfish, Tiger Shark, Koi, Goldfish).
        mvc.perform(json(post("/api/petstore/product/search"), "{\"categoryCategoryId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(contains("Angelfish", "Goldfish", "Koi", "Tiger Shark")));
        mvc.perform(json(post("/api/petstore/product/search"), "{\"categoryCategoryId\":1,\"sortCode\":\"name-\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Tiger Shark"));
        // The joined product's name, read with the item in the same statement.
        mvc.perform(json(post("/api/petstore/item/search"), "{\"name\":\"Puppy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productName").exists());
        // Money searched by a range: the items from 100.00 to 120.00.
        mvc.perform(json(post("/api/petstore/item/search"), "{\"unitCostFrom\":100,\"unitCostTo\":120,\"pageSize\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(8)))
                .andExpect(jsonPath("$.content[*].unitCost", everyItem(org.hamcrest.Matchers.anyOf(is(100.0), is(120.0)))));
        mvc.perform(get("/api/petstore/item/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitCost").value(10.00));
    }

    @Test
    void aVisitorCannotChangeTheCatalogNorOrder() throws Exception {
        mvc.perform(json(post("/api/petstore/category"), "{\"name\":\"Fake\",\"description\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/petstore/purchaseOrder"), "{\"lines\":[{\"itemId\":1,\"quantity\":1}]," + CARD + "}"))
                .andExpect(status().isUnauthorized());
        MockHttpSession user = signIn("user", "user");
        mvc.perform(json(post("/api/petstore/category"), "{\"name\":\"Fake\",\"description\":\"x\"}").session(user))
                .andExpect(status().isForbidden());
        // Customers are read by their owner: a user's search finds themselves alone.
        mvc.perform(json(post("/api/petstore/customer/search"), "{}").session(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].login").value("user"))
                .andExpect(jsonPath("$.content[0].homeAddress.street1").value("The User Boulevard"));
        mvc.perform(delete("/api/petstore/item/1").session(user))
                .andExpect(status().isForbidden());
    }

    @Test
    void aWrongPasswordIsRefusedAndSigningOutEndsTheSession() throws Exception {
        mvc.perform(json(post("/api/petstore/customer/signin"), "{\"login\":\"user\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized());
        MockHttpSession user = signIn("user", "user");
        mvc.perform(get("/api/petstore/customer/me").session(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("user"));
        mvc.perform(post("/api/petstore/customer/signout").session(user))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/petstore/customer/me").session(user))
                .andExpect(status().isUnauthorized());
    }

    /** An order and its lines are written together, priced by the shop, and read back together. */
    @Test
    void aCustomerChecksOutAndTheShopPricesTheCart() throws Exception {
        MockHttpSession user = signIn("user", "user");
        // The client claims a price of 0.01; the shop charges the catalog's.
        String cart = "{\"lines\":[{\"itemId\":1,\"quantity\":2,\"unitCost\":0.01},{\"itemId\":2,\"quantity\":1}]," + CARD + "}";
        MvcResult placed = mvc.perform(json(post("/api/petstore/purchaseOrder"), cart).session(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLC"))
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.lines[*].itemName", everyItem(not(org.hamcrest.Matchers.emptyOrNullString()))))
                .andExpect(jsonPath("$.totalWithoutVat").value(30.00))   // 2 x 10.00 + 10.00
                .andExpect(jsonPath("$.vat").value(1.65))                // 5.5 %
                .andExpect(jsonPath("$.discount").value(0.71))           // 2.25 % of 31.65
                .andExpect(jsonPath("$.total").value(30.94))
                .andExpect(jsonPath("$.deliveryAddress.street1").value("The User Boulevard"))
                .andExpect(jsonPath("$.creditCard.creditCardType").value("VISA"))
                .andExpect(jsonPath("$.customerId").value(2))
                .andExpect(content().string(not(containsString("4111111111111111"))))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(placed.getResponse().getContentAsString(), "$.orderId");
        assertThat(linesOf(id)).isEqualTo(2);
        // The columns hold what the JPA code would: the amount, the count, the card type's code.
        assertThat(jdbc.queryForObject("SELECT quantity FROM ps_order_line WHERE order_order_id = ? AND item_item_id = 1",
                Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT credit_card_type FROM ps_purchase_order WHERE order_id = ?",
                String.class, id)).isEqualTo("VISA");
        assertThat(jdbc.queryForObject("SELECT total FROM ps_purchase_order WHERE order_id = ?",
                java.math.BigDecimal.class, id)).isEqualByComparingTo("30.94");

        mvc.perform(get("/api/petstore/purchaseOrder/" + id).session(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.lines[*].quantity").value(org.hamcrest.Matchers.containsInAnyOrder(2, 1)))
                .andExpect(jsonPath("$.lines[*].subTotal").value(org.hamcrest.Matchers.containsInAnyOrder(20.0, 10.0)))
                .andExpect(jsonPath("$.deliveryAddress.city").value("San Francisco"));

        // A customer's search is narrowed to their own orders; asking for another's finds none.
        mvc.perform(json(post("/api/petstore/purchaseOrder/search"), "{}").session(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lines[0].itemName").exists())
                .andExpect(jsonPath("$.content[*].customerId", everyItem(is(2))));
        mvc.perform(json(post("/api/petstore/purchaseOrder/search"), "{\"customerId\":1}").session(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    /** A page of orders with their lines is three statements, however many orders: no N+1. */
    @Test
    void aPageOfOrdersAndTheirLinesIsThreeStatements() throws Exception {
        MockHttpSession buyer = signUp("pager");
        for (int i = 0; i < 4; i++) placeOrder(buyer, "[{\"itemId\":3,\"quantity\":1},{\"itemId\":4,\"quantity\":" + (i + 1) + "}]");
        statements.sql.clear();
        mvc.perform(json(post("/api/petstore/purchaseOrder/search"), "{\"pageSize\":3}").session(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].lines", everyItem(hasSize(2))));
        assertThat(statements.sql).hasSize(3);
        assertThat(statements.sql.get(2)).contains("ps_order_line").contains(" IN ");
        // The second page reads the rest.
        mvc.perform(json(post("/api/petstore/purchaseOrder/search"), "{\"pageSize\":3,\"pageNumber\":1}").session(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
        // Sorted by a column of the embedded delivery address and by Money, through the sort's column list.
        mvc.perform(json(post("/api/petstore/purchaseOrder/search"), "{\"sortCode\":\"deliveryAddress.city+,total-\"}").session(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(4)));
        mvc.perform(json(post("/api/petstore/purchaseOrder/search"), "{\"totalFrom\":40}").session(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].total", everyItem(org.hamcrest.Matchers.greaterThanOrEqualTo(40.0))));
    }

    @Test
    void anEmptyCartAMissingCardOrAnUnknownItemIsRefused() throws Exception {
        MockHttpSession user = signIn("user", "user");
        mvc.perform(json(post("/api/petstore/purchaseOrder"), "{\"lines\":[]," + CARD + "}").session(user))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/petstore/purchaseOrder"), "{\"lines\":[{\"itemId\":1,\"quantity\":1}]}").session(user))
                .andExpect(status().isBadRequest());
        // A line's own @min(1), checked with the order it arrives in.
        mvc.perform(json(post("/api/petstore/purchaseOrder"), "{\"lines\":[{\"itemId\":1,\"quantity\":0}]," + CARD + "}").session(user))
                .andExpect(status().isBadRequest());
        // The card's required number, checked inside the embedded value.
        mvc.perform(json(post("/api/petstore/purchaseOrder"), "{\"lines\":[{\"itemId\":1,\"quantity\":1}],"
                        + "\"creditCard\":{\"creditCardType\":\"VISA\",\"creditCardExpDate\":\"12/30\"}}").session(user))
                .andExpect(status().isBadRequest());
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM ps_purchase_order", Long.class);
        mvc.perform(json(post("/api/petstore/purchaseOrder"), "{\"lines\":[{\"itemId\":99999,\"quantity\":1}]," + CARD + "}").session(user))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("reference.OrderLine.item"))
                .andExpect(jsonPath("$.detail").value("Item 99999 does not exist"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ps_purchase_order", Long.class)).isEqualTo(before);
    }

    @Test
    void someoneElsesOrderIsNotThere() throws Exception {
        MockHttpSession user = signIn("user", "user");
        String id = placeOrder(user, "[{\"itemId\":5,\"quantity\":1}]");
        MockHttpSession other = signUp("vader");
        mvc.perform(get("/api/petstore/purchaseOrder/" + id).session(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/petstore/purchaseOrder/" + id).session(user)).andExpect(status().isOk());
    }

    /** Deleting an order deletes its lines first; a customer with orders is still referenced (409). */
    @Test
    void anOrderIsDeletedWithItsLines() throws Exception {
        MockHttpSession buyer = signUp("deleter");
        String id = placeOrder(buyer, "[{\"itemId\":6,\"quantity\":1},{\"itemId\":7,\"quantity\":2}]");
        assertThat(linesOf(id)).isEqualTo(2);
        Long customerId = jdbc.queryForObject("SELECT customer_id FROM ps_customer WHERE login = 'deleter'", Long.class);

        MockHttpSession admin = signIn("admin", "admin");
        mvc.perform(delete("/api/petstore/customer/" + customerId).session(admin))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/petstore/purchaseOrder/" + id).session(admin))
                .andExpect(status().isOk());
        assertThat(linesOf(id)).isZero();
        mvc.perform(get("/api/petstore/purchaseOrder/" + id).session(admin)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/petstore/customer/" + customerId).session(admin))
                .andExpect(status().isOk());
    }

    @Test
    void aNewCustomerSignsUpAsAUserAndThePasswordIsNeverShown() throws Exception {
        MockHttpSession session = signUp("luke");
        mvc.perform(get("/api/petstore/customer/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("luke"))
                .andExpect(jsonPath("$.role").value("U"))
                .andExpect(jsonPath("$.homeAddress.city").value("Mos Eisley"))
                .andExpect(jsonPath("$.password").doesNotExist());
        String stored = jdbc.queryForObject("SELECT password FROM ps_customer WHERE login = 'luke'", String.class);
        assertThat(stored).isNotEqualTo("force-1234");

        Long id = ((Number) com.jayway.jsonpath.JsonPath.read(mvc.perform(get("/api/petstore/customer/me").session(session))
                .andReturn().getResponse().getContentAsString(), "$.customerId")).longValue();
        // An update keeps what it does not name - the embedded address too - and the role only an administrator sets.
        mvc.perform(json(put("/api/petstore/customer"), "{\"customerId\":" + id + ",\"telephone\":\"+82 10 1234 5678\",\"role\":\"A\"}").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telephone").value("+82 10 1234 5678"))
                .andExpect(jsonPath("$.homeAddress.street1").value("1 Main St"))
                .andExpect(jsonPath("$.role").value("U"));
        // A new address replaces the old one whole.
        mvc.perform(json(put("/api/petstore/customer"), "{\"customerId\":" + id + ",\"homeAddress\":{\"street1\":\"2 Dune Sea\","
                        + "\"city\":\"Anchorhead\",\"zipcode\":\"54321\",\"countryCode\":\"US\"}}").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeAddress.city").value("Anchorhead"));
        assertThat(jdbc.queryForObject("SELECT city FROM ps_customer WHERE customer_id = ?", String.class, id))
                .isEqualTo("Anchorhead");
    }

    @Test
    void aSignUpWithoutOrWithAnInvalidAddressIsABadRequest() throws Exception {
        mvc.perform(json(post("/api/petstore/customer"),
                        "{\"login\":\"leia\",\"password\":\"p-1234\",\"firstName\":\"Leia\",\"lastName\":\"Organa\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/petstore/customer"), "{\"login\":\"leia\",\"password\":\"p\",\"firstName\":\"Leia\","
                        + "\"lastName\":\"Organa\",\"homeAddress\":{\"city\":\"Alderaan\",\"zipcode\":\"1\",\"countryCode\":\"US\"}}"))
                .andExpect(status().isBadRequest());
    }

    /** The same login twice is a conflict with a stored row: the unique index the schema declares. */
    @Test
    void aTakenLoginIsAConflict() throws Exception {
        signUp("obiwan");
        mvc.perform(json(post("/api/petstore/customer"), "{\"login\":\"obiwan\",\"password\":\"force-1234\","
                        + "\"firstName\":\"F\",\"lastName\":\"L\",\"homeAddress\":{\"street1\":\"1 Main St\",\"city\":\"Mos Eisley\","
                        + "\"zipcode\":\"12345\",\"countryCode\":\"US\"}}"))
                .andExpect(status().isConflict());
    }

    /** A sort the API does not know is the caller's 400, and none of its text reaches the SQL. */
    @Test
    void aBadSearchIsABadRequest() throws Exception {
        mvc.perform(json(post("/api/petstore/item/search"), "{\"sortCode\":\"name; DROP TABLE ps_item+\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/petstore/item/search"), "{\"pageNumber\":-1}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ps_item", Long.class)).isEqualTo(32);
    }
}
