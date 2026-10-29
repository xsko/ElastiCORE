package io.elasticore.sample.petstore;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Hibernate validates db/schema-oracle.sql on Oracle (ELASTICORE_IT_ORACLE_URL). */
@EnabledIfEnvironmentVariable(named = "ELASTICORE_IT_ORACLE_URL", matches = ".+")
class SchemaValidateOnOracleTest extends SchemaValidateOnDatabase {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        database(r, "oracle");
    }
}
