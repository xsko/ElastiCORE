package io.elasticore.sample.mybatis.petstore;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The shop on SQL Server, in a database of its own (ELASTICORE_IT_SQLSERVER_URL). */
@EnabledIfEnvironmentVariable(named = "ELASTICORE_IT_SQLSERVER_URL", matches = ".+")
class ShopFlowOnSqlServerTest extends ShopFlowOnDatabase {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        set(r, TestDatabase.fresh("sqlserver"));
    }

    @Override
    String databaseId() {
        return "sqlserver";
    }
}
