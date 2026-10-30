package dddmybatis;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The domain model over MyBatis on Oracle, from db/schema-oracle.sql, as a user of its own. */
@Tag("database")
@EnabledIfEnvironmentVariable(named = "ELASTICORE_IT_ORACLE_URL", matches = ".+")
class DomainOnOracleTest extends DomainOnDatabase {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        TestDatabase.fresh("oracle", "dddmb").forEach((k, v) -> r.add(k, () -> v));
    }
}
