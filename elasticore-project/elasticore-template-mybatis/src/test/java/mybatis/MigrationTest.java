package mybatis;

import io.elasticore.base.model.pub.rules.schema.Database;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;

/** A migration the generator writes, run on H2 every build and on each database CI gives (MigrationRun). */
public class MigrationTest {

    @Test
    public void onH2() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:migration" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")) {
            MigrationRun.on(c, Database.H2);
        }
    }

    /** The same on Oracle, PostgreSQL and SQL Server, each in a place of its own (TestDatabase). */
    @Tag("database")
    public static class OnDatabasesTest {

        private static void run(String vendor, Database db) throws Exception {
            Map<String, String> p = TestDatabase.fresh(vendor, "migration");
            try (Connection c = DriverManager.getConnection(p.get("spring.datasource.url"),
                    p.get("spring.datasource.username"), p.get("spring.datasource.password"))) {
                MigrationRun.on(c, db);
            }
        }

        @Test
        @EnabledIfEnvironmentVariable(named = "ELASTICORE_IT_POSTGRESQL_URL", matches = ".+")
        public void onPostgreSql() throws Exception {
            run("postgresql", Database.POSTGRESQL);
        }

        @Test
        @EnabledIfEnvironmentVariable(named = "ELASTICORE_IT_ORACLE_URL", matches = ".+")
        public void onOracle() throws Exception {
            run("oracle", Database.ORACLE);
        }

        @Test
        @EnabledIfEnvironmentVariable(named = "ELASTICORE_IT_SQLSERVER_URL", matches = ".+")
        public void onSqlServer() throws Exception {
            run("sqlserver", Database.SQLSERVER);
        }
    }
}
