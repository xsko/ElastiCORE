package io.elasticore.blueprint;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The schema the generator wrote for the 2.x code (db/schema-h2.sql,
 * elasticore { ddl }) is the one Hibernate maps: the tables are made from it,
 * and Hibernate starts with ddl-auto=validate, which fails on a missing
 * table, column or sequence, or a column of another type.
 */
@DataJpaTest(properties = {
        // application.yaml asks Hibernate to update the schema; here it only checks it.
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.hbm2ddl.auto=validate",
        "spring.jpa.generate-ddl=false",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/schema-h2.sql"})
class SchemaValidateTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void hibernateValidatesTheGeneratedSchema() {
        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'", Integer.class);
        assertTrue(tables != null && tables > 10, "the schema made " + tables + " tables");
    }
}
