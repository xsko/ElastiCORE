package ddd;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The schema the generator wrote (db/schema-h2.sql, elasticore { ddl }) is
 * the one Hibernate maps - the domain's tables and Envers' audit tables, its
 * revision table and the sequence that numbers it: the tables are made from
 * it, and Hibernate starts with ddl-auto=validate, which fails on a missing
 * table, column or sequence, or a column of another type.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ddd-validate;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/schema-h2.sql"})
class SchemaValidateTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void hibernateValidatesTheGeneratedSchema() {
        // Started: validation passed. The audit tables are there for Envers to write to.
        Integer audits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) LIKE '%\\_aud' ESCAPE '\\'", Integer.class);
        assertTrue(audits != null && audits > 0, "no audit table in the schema");
    }
}
