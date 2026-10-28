package mybatis;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The shop on Oracle, from db/shop-schema-oracle.sql, as a user of its own. */
@Tag("database")
@EnabledIfEnvironmentVariable(named = "ELASTICORE_IT_ORACLE_URL", matches = ".+")
public class ShopApiOnOracleTest extends ShopApi {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        TestDatabase.fresh("oracle", "shop").forEach((k, v) -> r.add(k, () -> v));
    }
}
