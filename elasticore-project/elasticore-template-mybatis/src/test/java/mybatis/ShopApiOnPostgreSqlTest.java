package mybatis;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The shop on PostgreSQL, from db/shop-schema-postgresql.sql, in a schema of its own. */
@Tag("database")
@EnabledIfEnvironmentVariable(named = "ELASTICORE_IT_POSTGRESQL_URL", matches = ".+")
public class ShopApiOnPostgreSqlTest extends ShopApi {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        TestDatabase.fresh("postgresql", "shop").forEach((k, v) -> r.add(k, () -> v));
    }
}
