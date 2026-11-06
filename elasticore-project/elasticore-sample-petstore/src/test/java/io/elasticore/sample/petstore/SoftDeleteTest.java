package io.elasticore.sample.petstore;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Product's discontinuedAt is @deleted: a product taken off the catalog is
 * kept - the items and orders that name it still do - and the catalog no
 * longer shows it. Hibernate 7, annotations on the entity.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SoftDeleteTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpSession admin() throws Exception {
        MvcResult r = mvc.perform(json(post("/api/petstore/customer/signin"), "{\"login\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    private long create(MockHttpSession admin, String path, String body, String key) throws Exception {
        String reply = mvc.perform(json(post(path), body).session(admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(reply, "$." + key)).longValue();
    }

    @Test
    void aProductAnItemNamesIsTakenOffTheCatalogAndKept() throws Exception {
        MockHttpSession admin = admin();
        long product = create(admin, "/api/petstore/product",
                "{\"name\":\"Glass Catfish\",\"description\":\"see-through\",\"categoryId\":1000}", "productId");
        long item = create(admin, "/api/petstore/item",
                "{\"name\":\"Small\",\"description\":\"one fish\",\"unitCost\":5.00,\"productId\":" + product + "}", "itemId");

        // Deleted outright, the item's foreign key would refuse it; kept, it goes.
        mvc.perform(delete("/api/petstore/product/" + product).session(admin)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ps_product WHERE product_id = ? AND discontinued_at IS NOT NULL",
                Long.class, product)).isEqualTo(1L);
        mvc.perform(get("/api/petstore/product/" + product)).andExpect(status().isNotFound());
        mvc.perform(json(post("/api/petstore/product/search"), "{\"name\":\"Glass Catfish\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
        // What names it still does.
        mvc.perform(get("/api/petstore/item/" + item)).andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value((int) product));
    }

    @Test
    void aRequestCannotTakeAProductOff() throws Exception {
        MockHttpSession admin = admin();
        long product = create(admin, "/api/petstore/product",
                "{\"name\":\"Ghost Shrimp\",\"description\":\"small\",\"categoryId\":1000,"
                        + "\"discontinuedAt\":\"2000-01-01T00:00:00Z\"}", "productId");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ps_product WHERE product_id = ? AND discontinued_at IS NULL",
                Long.class, product)).isEqualTo(1L);
        mvc.perform(get("/api/petstore/product/" + product)).andExpect(status().isOk());
    }
}
