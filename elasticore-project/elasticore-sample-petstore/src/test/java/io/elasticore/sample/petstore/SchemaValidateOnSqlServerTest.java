package io.elasticore.sample.petstore;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Hibernate validates db/schema-sqlserver.sql on SQL Server (ELASTICORE_IT_SQLSERVER_URL). */
@EnabledIfEnvironmentVariable(named = "ELASTICORE_IT_SQLSERVER_URL", matches = ".+")
class SchemaValidateOnSqlServerTest extends SchemaValidateOnDatabase {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        database(r, "sqlserver");
    }
}
