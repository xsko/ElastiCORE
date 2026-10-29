package io.elasticore.sample.petstore;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Hibernate validates db/schema-postgresql.sql on PostgreSQL (ELASTICORE_IT_POSTGRESQL_URL). */
@EnabledIfEnvironmentVariable(named = "ELASTICORE_IT_POSTGRESQL_URL", matches = ".+")
class SchemaValidateOnPostgreSqlTest extends SchemaValidateOnDatabase {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        database(r, "postgresql");
    }
}
