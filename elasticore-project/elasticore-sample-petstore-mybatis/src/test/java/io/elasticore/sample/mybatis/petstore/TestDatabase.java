package io.elasticore.sample.mybatis.petstore;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A database of the CI job's, given by ELASTICORE_IT_{VENDOR}_URL, _USER and
 * _PASSWORD (an administrator), and a place of its own in it for one test
 * class: a schema on PostgreSQL, a database on SQL Server, a user on Oracle -
 * so every run starts from the generated schema on empty tables, whatever
 * ran before it.
 */
final class TestDatabase {

    private TestDatabase() {
    }

    /** The environment variable that names the database: ELASTICORE_IT_ORACLE_URL ... */
    static String urlVariable(String vendor) {
        return "ELASTICORE_IT_" + vendor.toUpperCase(Locale.ROOT) + "_URL";
    }

    /**
     * Makes a new, empty place on the database and answers the Spring
     * properties that point the application at it, with the schema the
     * generator wrote for that database and the catalog.
     */
    static Map<String, String> fresh(String vendor) {
        String v = vendor.toUpperCase(Locale.ROOT);
        String url = System.getenv("ELASTICORE_IT_" + v + "_URL");
        String user = System.getenv("ELASTICORE_IT_" + v + "_USER");
        String password = System.getenv("ELASTICORE_IT_" + v + "_PASSWORD");
        String name = "petstore_" + Long.toString(System.nanoTime(), 36);
        Map<String, String> p = new LinkedHashMap<String, String>();
        try (Connection c = DriverManager.getConnection(url, user, password); Statement s = c.createStatement()) {
            switch (vendor) {
                case "postgresql":
                    s.execute("CREATE SCHEMA " + name);
                    p.put("spring.datasource.url", url + (url.contains("?") ? "&" : "?") + "currentSchema=" + name);
                    p.put("spring.datasource.username", user);
                    p.put("spring.datasource.password", password);
                    break;
                case "sqlserver":
                    s.execute("CREATE DATABASE " + name);
                    p.put("spring.datasource.url", url + ";databaseName=" + name);
                    p.put("spring.datasource.username", user);
                    p.put("spring.datasource.password", password);
                    break;
                case "oracle":
                    String secret = "Pw" + Long.toString(System.nanoTime(), 36);
                    s.execute("CREATE USER " + name + " IDENTIFIED BY \"" + secret + "\" QUOTA UNLIMITED ON USERS");
                    s.execute("GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO " + name);
                    p.put("spring.datasource.url", url);
                    p.put("spring.datasource.username", name);
                    p.put("spring.datasource.password", secret);
                    break;
                default:
                    throw new IllegalArgumentException(vendor);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot make a place for the test on " + vendor + ": " + e.getMessage(), e);
        }
        // Spring runs a schema on an embedded database only, unless told to (application.yml does too).
        p.put("spring.sql.init.mode", "always");
        p.put("spring.sql.init.schema-locations", "classpath:db/petstore-schema-" + vendor + ".sql");
        p.put("spring.sql.init.data-locations", "classpath:data.sql");
        return p;
    }
}
