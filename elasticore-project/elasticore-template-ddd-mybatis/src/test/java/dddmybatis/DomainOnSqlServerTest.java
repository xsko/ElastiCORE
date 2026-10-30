package dddmybatis;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The domain model over MyBatis on SQL Server, from db/schema-sqlserver.sql, in a database of its own. */
@Tag("database")
@EnabledIfEnvironmentVariable(named = "ELASTICORE_IT_SQLSERVER_URL", matches = ".+")
class DomainOnSqlServerTest extends DomainOnDatabase {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        TestDatabase.fresh("sqlserver", "dddmb").forEach((k, v) -> r.add(k, () -> v));
    }
}
