package dddmybatis;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The domain model over MyBatis on PostgreSQL, from db/schema-postgresql.sql, in a schema of its own. */
@Tag("database")
@EnabledIfEnvironmentVariable(named = "ELASTICORE_IT_POSTGRESQL_URL", matches = ".+")
class DomainOnPostgreSqlTest extends DomainOnDatabase {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        TestDatabase.fresh("postgresql", "dddmb").forEach((k, v) -> r.add(k, () -> v));
    }
}
