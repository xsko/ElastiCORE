package io.elasticore.sample.petstore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The schema the generator wrote (db/schema-h2.sql, elasticore { ddl }) is
 * the one Hibernate maps: the tables are made from it rather than by
 * ddl-auto, and Hibernate starts with ddl-auto=validate - which fails on a
 * missing table, column or sequence, or a column of another type. Then the
 * shop works on it: the catalog is read and the demo accounts were written.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:petstore-validate;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/schema-h2.sql",
        "spring.sql.init.data-locations=classpath:data.sql"})
@AutoConfigureMockMvc
class SchemaValidateTest {

    @Autowired MockMvc mvc;

    @Test
    void hibernateValidatesTheGeneratedSchemaAndTheShopRunsOnIt() throws Exception {
        mvc.perform(post("/api/petstore/category/search").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)));
        mvc.perform(post("/api/petstore/customer/signin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"user\",\"password\":\"user\"}"))
                .andExpect(status().isOk());
    }
}
