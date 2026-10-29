package ddd

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

/**
 * The schema the generator wrote, read off the Kotlin entities
 * (db/schema-h2.sql, elasticore { ddl }), is the one Hibernate maps: the
 * tables are made from it, and Hibernate starts with ddl-auto=validate,
 * which fails on a missing table, column or sequence, or a column of
 * another type.
 */
@SpringBootTest(
    classes = [KotlinShopApp::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:kt-validate;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/schema-h2.sql",
    ],
)
class SchemaValidateTest {

    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun hibernateValidatesTheGeneratedSchema() {
        val tables = jdbc.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'", Int::class.java)
        assertTrue(tables != null && tables > 10, "the schema made $tables tables")
    }
}
