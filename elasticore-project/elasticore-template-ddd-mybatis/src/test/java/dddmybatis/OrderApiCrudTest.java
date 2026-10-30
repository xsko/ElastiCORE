package dddmybatis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The generated API, driven over HTTP.
 *
 * The boundary test proves the model persists; this proves there is a running
 * program around it. Every request goes through the generated controller, the
 * generated service, the generated mapper and the generated repository, so a
 * missing bean, an unmapped field or a route that was never registered fails
 * here and nowhere earlier.
 *
 * It also states the boundary as a fact about the API rather than about a
 * class: a member of an aggregate has no endpoint of its own, and asking for
 * one gets nothing back.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderApiCrudTest {

    private static final String ORDERS = "/api/v1/shop/order";
    private static final String CUSTOMERS = "/api/v1/shop/customer";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    @DisplayName("create, read, update, search and delete an order over HTTP")
    void crudOverHttp() throws Exception {
        // CREATE
        String created = mvc.perform(post(ORDERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        // The DSL decides both shapes: the date pattern the
                        // generated @JsonFormat states, and the enum's code
                        // rather than its name.
                        // The line is not decoration: the aggregate promises
                        // it has one, and refuses to be written otherwise.
                        .content("{\"orderDate\":\"2026-08-15 10:00:00\","
                                + "\"totalAmount\":14.49,"
                                + "\"status\":\"PEN\","
                                + "\"notes\":\"first\","
                                + "\"items\":[{\"itemSeq\":1,\"quantity\":1,\"unitPrice\":14.49}]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String orderId = json.readTree(created).path("orderId").asText();
        assertThat(orderId).as("the id the generator assigns on persist").isNotEmpty();

        // READ
        JsonNode read = json.readTree(mvc.perform(get(ORDERS + "/" + orderId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(read.path("notes").asText()).isEqualTo("first");

        // UPDATE
        mvc.perform(put(ORDERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderId\":\"%s\",\"notes\":\"second\"}", orderId)))
                .andExpect(status().isOk());

        JsonNode afterUpdate = json.readTree(mvc.perform(get(ORDERS + "/" + orderId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(afterUpdate.path("notes").asText()).isEqualTo("second");

        // SEARCH
        JsonNode page = json.readTree(mvc.perform(post(ORDERS + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":0,\"pageSize\":10}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(page.path("content").isArray()).isTrue();
        assertThat(page.path("content").size()).isGreaterThan(0);

        // DELETE
        mvc.perform(delete(ORDERS + "/" + orderId)).andExpect(status().isOk());
        mvc.perform(get(ORDERS + "/" + orderId)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a root that is its own aggregate has the same API")
    void customerCrudOverHttp() throws Exception {
        String created = mvc.perform(post(CUSTOMERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\","
                                + "\"email\":\"ada.api@example.com\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long customerId = json.readTree(created).path("customerId").asLong();
        assertThat(customerId).isPositive();

        mvc.perform(get(CUSTOMERS + "/" + customerId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an aggregate is created with its members in one request")
    void membersArriveWithTheirRoot() throws Exception {
        // The one way in. A member has no endpoint of its own, so if the root
        // does not accept them there is no request that can create them - and
        // for a while there was not: the order was created and its lines were
        // dropped without a word.
        String created = mvc.perform(post(ORDERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderDate\":\"2026-08-17 10:00:00\","
                                + "\"notes\":\"with items\","
                                + "\"items\":[{\"itemSeq\":1,\"quantity\":2,\"unitPrice\":9.99},"
                                + "{\"itemSeq\":2,\"quantity\":1,\"unitPrice\":4.50}]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String orderId = json.readTree(created).path("orderId").asText();
        JsonNode read = json.readTree(mvc.perform(get(ORDERS + "/" + orderId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(read.path("items").size())
                .as("the members were created with the root")
                .isEqualTo(2);

        // And replaced wholesale on update, which the boundary makes safe:
        // nothing outside the aggregate holds one.
        mvc.perform(put(ORDERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderId\":\"%s\","
                                + "\"items\":[{\"itemSeq\":9,\"quantity\":5,\"unitPrice\":1.00}]}", orderId)))
                .andExpect(status().isOk());

        JsonNode afterUpdate = json.readTree(mvc.perform(get(ORDERS + "/" + orderId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(afterUpdate.path("items").size()).isEqualTo(1);
        assertThat(afterUpdate.path("items").get(0).path("itemSeq").asInt()).isEqualTo(9);
    }

    @Test
    @DisplayName("an order is given its customer by identity, and the customer is untouched")
    void customerIsSetByIdentity() throws Exception {
        String customer = mvc.perform(post(CUSTOMERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Grace\",\"lastName\":\"Hopper\","
                                + "\"email\":\"grace.byid@example.com\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long customerId = json.readTree(customer).path("customerId").asLong();

        // The identity is all that is sent. Nothing about the customer travels
        // with the order, which is the whole of what the boundary asks for.
        String created = mvc.perform(post(ORDERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderDate\":\"2026-08-17 11:00:00\","
                                + "\"customerId\":%d,"
                                + "\"items\":[{\"itemSeq\":1,\"quantity\":1,\"unitPrice\":5.00}],"
                                + "\"customerFirstName\":\"IGNORED\"}", customerId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String orderId = json.readTree(created).path("orderId").asText();
        JsonNode order = json.readTree(mvc.perform(get(ORDERS + "/" + orderId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(order.path("customerId").asLong())
                .as("the reference was attached by identity")
                .isEqualTo(customerId);
        assertThat(order.path("customerFirstName").asText())
                .as("and reading across the boundary still works")
                .isEqualTo("Grace");

        JsonNode reloaded = json.readTree(mvc.perform(get(CUSTOMERS + "/" + customerId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(reloaded.path("firstName").asText())
                .as("the order carried a name for the customer and did not apply it")
                .isEqualTo("Grace");
    }

    @Test
    @DisplayName("a member of an aggregate has no API of its own")
    void memberHasNoEndpoint() throws Exception {
        // OrderItem and VipProfile are inside other aggregates. The rules
        // declined their controllers, so nothing is mapped at these paths -
        // which is the boundary stated where a caller can see it.
        mvc.perform(get("/api/v1/shop/orderItem/1")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/shop/vipProfile/1")).andExpect(status().isNotFound());
    }
}
