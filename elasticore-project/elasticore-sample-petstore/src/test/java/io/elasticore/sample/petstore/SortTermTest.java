package io.elasticore.sample.petstore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A search sorts only by what its response shows. A MANAGER searches every customer's orders, and an order's
 * response shows neither the customer's e-mail nor the card number (@writeonly) nor - anywhere - the password
 * hash: sorted by, their order would be told to the manager, and with orders of their own to sort among, the
 * values themselves. Such a sort is refused (400 no-such-property) by sortCode, by sortColumn and by the export.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SortTermTest {

    @Autowired MockMvc mvc;

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpSession signIn(String login, String password) throws Exception {
        MvcResult r = mvc.perform(json(post("/api/petstore/customer/signin"),
                        "{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    /** A customer who signed up, with an e-mail, and ordered one item paid with the card given. */
    private MockHttpSession customerWithAnOrder(String login, String email, String card, int itemId) throws Exception {
        mvc.perform(json(post("/api/petstore/account/signup"), "{\"login\":\"" + login + "\",\"password\":\"pw-" + login + "-1\","
                        + "\"firstName\":\"F\",\"lastName\":\"L\",\"email\":\"" + email + "\",\"homeAddress\":{\"street1\":\"1 Main St\","
                        + "\"city\":\"Mos Eisley\",\"zipcode\":\"12345\",\"countryCode\":\"US\"}}"))
                .andExpect(status().isCreated());
        MockHttpSession session = signIn(login, "pw-" + login + "-1");
        mvc.perform(json(post("/api/petstore/purchaseOrder"), "{\"lines\":[{\"itemId\":" + itemId + ",\"quantity\":1}],"
                        + "\"creditCard\":{\"creditCardNumber\":\"" + card + "\",\"creditCardType\":\"VISA\",\"creditCardExpDate\":\"12/30\"}}")
                        .session(session))
                .andExpect(status().isOk());
        return session;
    }

    private ResultActions search(String path, String body, MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder request = json(post(path), body);
        return mvc.perform(session == null ? request : request.session(session));
    }

    private void refused(ResultActions answer, String property) throws Exception {
        String detail = com.jayway.jsonpath.JsonPath.read(answer.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("no-such-property"))
                .andExpect(jsonPath("$.detail").value(containsString("cannot be sorted by '" + property + "'")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8), "$.detail");
        // The properties a sort may name are said; a hidden one is not among them.
        String listed = detail.substring(detail.indexOf("sort by one of"));
        org.hamcrest.MatcherAssert.assertThat(listed, not(containsString("password")));
        org.hamcrest.MatcherAssert.assertThat(listed, not(containsString("creditCardNumber")));
        org.hamcrest.MatcherAssert.assertThat(listed, not(containsString("customer.email")));
    }

    @Test
    void aManagerCannotSortOrdersByWhatTheirResponseDoesNotShow() throws Exception {
        MockHttpSession manager = customerWithAnOrder("lando", "zed@x.org", "5500000000000004", 1006);
        customerWithAnOrder("rey", "amy@x.org", "4111111111111111", 1007);
        long managerId = ((Number) com.jayway.jsonpath.JsonPath.read(mvc.perform(get("/api/petstore/customer/me").session(manager))
                .andReturn().getResponse().getContentAsString(), "$.customerId")).longValue();
        mvc.perform(json(put("/api/petstore/customer"), "{\"customerId\":" + managerId + ",\"role\":\"M\"}")
                        .session(signIn("admin", "admin")))
                .andExpect(status().isOk());

        String orders = "/api/petstore/purchaseOrder/search";
        refused(search(orders, "{\"sortCode\":\"customer.password+\"}", manager), "customer.password");
        refused(search(orders, "{\"sortCode\":\"customer.email-\"}", manager), "customer.email");
        refused(search(orders, "{\"sortCode\":\"creditCard.creditCardNumber+\"}", manager), "creditCard.creditCardNumber");
        refused(search(orders, "{\"sortCode\":\"orderDate-,creditCard.creditCardNumber+\"}", manager), "creditCard.creditCardNumber");
        refused(search(orders, "{\"sortColumn\":\"customer.email\",\"sortAscending\":true}", manager), "customer.email");
        refused(search(orders, "{\"sortColumn\":\"creditCard.creditCardNumber\"}", manager), "creditCard.creditCardNumber");
        // The export reads the same search: refused before a byte of the file is written.
        refused(search("/api/petstore/purchaseOrder/export", "{\"sortCode\":\"customer.email+\"}", manager), "customer.email");
        refused(search("/api/petstore/purchaseOrder/export", "{\"search\":{\"sortCode\":\"creditCard.creditCardNumber-\"}}", manager),
                "creditCard.creditCardNumber");

        // What the response shows still sorts: the order's own values, an embedded part, the card's type, the customer's key.
        for (String sort : new String[] {"orderDate-", "total+", "deliveryAddress.city+,orderId-", "creditCard.creditCardType+", "customerId+"}) {
            search(orders, "{\"sortCode\":\"" + sort + "\"}", manager)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThan(1)));
        }
        search(orders, "{\"sortColumn\":\"total\",\"sortAscending\":false}", manager).andExpect(status().isOk());
        search("/api/petstore/purchaseOrder/export", "{\"sortCode\":\"total-\"}", manager).andExpect(status().isOk());
    }

    @Test
    void aVisitorSortsTheCatalogByWhatItShowsOnly() throws Exception {
        // An item's response shows its product's name (productName @ref(product.name)), not the product's description.
        search("/api/petstore/item/search", "{\"name\":\"Puppy\",\"sortCode\":\"product.name+,itemId-\"}", null)
                .andExpect(status().isOk());
        refused(search("/api/petstore/item/search", "{\"sortCode\":\"product.description+\"}", null), "product.description");
        // A product's response carries its category whole (CategoryDTO): its name sorts.
        // (Other tests add products to the catalog: the order is checked, not the names.)
        java.util.List<String> names = com.jayway.jsonpath.JsonPath.read(
                search("/api/petstore/product/search", "{\"categoryId\":1000,\"sortCode\":\"category.name+,name-\"}", null)
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                "$.content[*].name");
        java.util.List<String> descending = new java.util.ArrayList<String>(names);
        descending.sort(java.util.Comparator.reverseOrder());
        org.hamcrest.MatcherAssert.assertThat(names, org.hamcrest.Matchers.hasItems("Tiger Shark", "Koi", "Goldfish", "Angelfish"));
        org.hamcrest.MatcherAssert.assertThat(names, org.hamcrest.Matchers.equalTo(descending));
        // A customer's own search: the password is never shown, so never sorted by - not even by its owner.
        refused(search("/api/petstore/customer/search", "{\"sortCode\":\"password+\"}", signIn("user", "user")), "password");
        // A property the entity does not have is refused alike.
        refused(search("/api/petstore/item/search", "{\"sortColumn\":\"noSuchColumn\"}", null), "noSuchColumn");
    }
}
