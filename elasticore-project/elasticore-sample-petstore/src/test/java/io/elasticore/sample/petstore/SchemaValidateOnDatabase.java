package io.elasticore.sample.petstore;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
@org.springframework.context.annotation.Import({AbstractEntityCheck.class, SoftDeleteHierarchyCheck.class})
abstract class SchemaValidateOnDatabase {

    @Autowired MockMvc mvc;
    @Autowired AbstractEntityCheck codes;
    @Autowired SoftDeleteHierarchyCheck kept;
    @Autowired JdbcTemplate jdbc;

    /** What the export writes in the portable language runs here: each metric, over its dataset's table. */
    @Test
    @SuppressWarnings("unchecked")
    void everyMetricRunsOnTheDatabase() throws Exception {
        Map<String, Object> model;
        try (InputStream in = getClass().getResourceAsStream("/blueprint/semantic_model.yml")) {
            model = new Yaml().load(in);
        }
        Map<String, String> tables = new HashMap<String, String>();
        for (Map<String, Object> ds : (List<Map<String, Object>>) model.get("datasets")) {
            tables.put((String) ds.get("name"), (String) ds.get("source"));
        }
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) model.get("metrics");
        assertThat(metrics).isNotEmpty();
        for (Map<String, Object> m : metrics) {
            String sql = (String) ((Map<String, Object>) m.get("expression")).get("sql");
            String from = null;
            for (Map.Entry<String, String> t : tables.entrySet()) {
                String named = sql.replaceAll("\\b" + t.getKey() + "\\.", t.getValue() + ".");
                if (named.equals(sql)) continue;
                sql = named;
                from = t.getValue();
            }
            assertThat(from).as("a dataset in " + m.get("name")).isNotNull();
            assertThat(jdbc.queryForList("SELECT " + sql + " FROM " + from)).as((String) m.get("name")).hasSize(1);
        }
    }

    @Test
    void hibernateValidatesTheSchemaAndTheShopRunsOnIt() throws Exception {
        mvc.perform(post("/api/petstore/category/search").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/petstore/customer/signin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"user\",\"password\":\"user\"}"))
                .andExpect(status().isOk());
    }

    /** An abstract entity's rows are in its subclasses' tables, and one repository reads them together. */
    @Test
    void anAbstractEntityReadsItsSubclassesTogether() throws Exception {
        codes.readsEveryTable();
    }

    /** @deleted in a JOINED and a TABLE_PER_CLASS hierarchy: the row is kept with the time, and not read. */
    @Test
    void aHierarchysDeleteKeepsTheRow() throws Exception {
        kept.aDeletedPersonIsKeptAndNotFound(mvc);
        kept.aDeletedTagIsKeptAndNotFound(mvc);
    }

    static void database(DynamicPropertyRegistry r, String vendor) {
        TestDatabase.fresh(vendor, "pet").forEach((k, v) -> r.add(k, () -> v));
    }
}
