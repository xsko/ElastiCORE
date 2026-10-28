package io.elasticore.sample.mybatis.petstore;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The shop on Oracle, as a user of its own (ELASTICORE_IT_ORACLE_URL). */
@EnabledIfEnvironmentVariable(named = "ELASTICORE_IT_ORACLE_URL", matches = ".+")
class ShopFlowOnOracleTest extends ShopFlowOnDatabase {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        set(r, TestDatabase.fresh("oracle"));
    }

    @Override
    String databaseId() {
        return "oracle";
    }
}
