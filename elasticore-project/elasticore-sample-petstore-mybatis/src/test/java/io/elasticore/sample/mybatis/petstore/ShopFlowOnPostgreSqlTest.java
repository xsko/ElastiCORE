package io.elasticore.sample.mybatis.petstore;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The shop on PostgreSQL, in a schema of its own (ELASTICORE_IT_POSTGRESQL_URL). */
@EnabledIfEnvironmentVariable(named = "ELASTICORE_IT_POSTGRESQL_URL", matches = ".+")
class ShopFlowOnPostgreSqlTest extends ShopFlowOnDatabase {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        set(r, TestDatabase.fresh("postgresql"));
    }

    @Override
    String databaseId() {
        return "postgresql";
    }
}
