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
    @Autowired io.elasticore.sample.petstore.infrastructure.persistence.CategoryRepository categories;

    /** What the export writes in the portable language runs here: each metric of a dataset, over its table. */
    @Test
    @SuppressWarnings("unchecked")
    void everyMetricRunsOnTheDatabase() throws Exception {
        Map<String, Object> model;
        try (InputStream in = getClass().getResourceAsStream("/blueprint/semantic_model.yml")) {
            model = new Yaml().load(in);
        }
        int run = 0;
        for (Map<String, Object> ds : (List<Map<String, Object>>) model.get("datasets")) {
            if (!ds.containsKey("metrics")) continue;
            for (Map<String, Object> m : (List<Map<String, Object>>) ds.get("metrics")) {
                String sql = (String) ((Map<String, Object>) m.get("expression")).get("sql");
                assertThat(jdbc.queryForList("SELECT " + sql + " FROM " + ds.get("source")))
                        .as(ds.get("name") + "." + m.get("name")).hasSize(1);
                run++;
            }
        }
        assertThat(run).as("the metrics of the datasets").isPositive();
    }

    @Test
    void hibernateValidatesTheSchemaAndTheShopRunsOnIt() throws Exception {
        mvc.perform(post("/api/petstore/category/search").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/petstore/customer/signin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"user\",\"password\":\"user\"}"))
                .andExpect(status().isOk());
    }

    /**
     * clob and blob keep the value in the row: what Hibernate writes, SQL reads
     * back as it is - not a large object's number (PostgreSQL's oid), as a
     * @Lob String was.
     */
    @Test
    void aLargeValueIsKeptInTheRow() {
        StringBuilder text = new StringBuilder();
        while (text.length() < 6000) text.append("A long story of the fish, 물고기 이야기. ");
        String story = text.toString();
        byte[] icon = new byte[5000];
        for (int i = 0; i < icon.length; i++) icon[i] = (byte) (i % 251);

        io.elasticore.sample.petstore.domain.model.Category c = new io.elasticore.sample.petstore.domain.model.Category();
        c.setName("Large values");
        c.setDescription("clob and blob in the row");
        c.setStory(story);
        c.setIcon(icon);
        Long id = categories.saveAndFlush(c).getCategoryId();

        assertThat(jdbc.queryForObject("SELECT story FROM ps_category WHERE category_id = ?", String.class, id))
                .isEqualTo(story);
        assertThat(jdbc.queryForObject("SELECT icon FROM ps_category WHERE category_id = ?", byte[].class, id))
                .isEqualTo(icon);
        assertThat(categories.findById(id).orElseThrow().getStory()).isEqualTo(story);
        categories.deleteById(id);
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
