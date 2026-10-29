package io.elasticore.sample.petstore;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The schema the generator wrote for a database the CI job gives
 * (db/schema-{vendor}.sql) is the one Hibernate maps there: made from it,
 * Hibernate starts with ddl-auto=validate - a column of another type on that
 * database fails it - and the shop writes and reads on it (the demo accounts
 * are created at start-up, and sign in).
 */
@Tag("database")
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        // The catalog is data.sql with its keys, which SQL Server takes into an identity column only when told; the accounts suffice.
        "spring.sql.init.data-locations=optional:classpath:none.sql",
        "spring.sql.init.continue-on-error=false"})
@AutoConfigureMockMvc
abstract class SchemaValidateOnDatabase {

    @Autowired MockMvc mvc;

    @Test
    void hibernateValidatesTheSchemaAndTheShopRunsOnIt() throws Exception {
        mvc.perform(post("/api/petstore/category/search").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/petstore/customer/signin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"user\",\"password\":\"user\"}"))
                .andExpect(status().isOk());
    }

    static void database(DynamicPropertyRegistry r, String vendor) {
        TestDatabase.fresh(vendor, "pet").forEach((k, v) -> r.add(k, () -> v));
    }
}
