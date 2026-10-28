package mybatis;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The shop on SQL Server, from db/shop-schema-sqlserver.sql, in a database of its own. */
@Tag("database")
@EnabledIfEnvironmentVariable(named = "ELASTICORE_IT_SQLSERVER_URL", matches = ".+")
public class ShopApiOnSqlServerTest extends ShopApi {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        TestDatabase.fresh("sqlserver", "shop").forEach((k, v) -> r.add(k, () -> v));
    }
}
