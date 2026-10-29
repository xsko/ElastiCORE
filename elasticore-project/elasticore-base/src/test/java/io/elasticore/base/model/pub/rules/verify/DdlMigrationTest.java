package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.pub.rules.schema.DdlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The project's DDL across changes of the model: the first run writes the
 * schema and the first migration of each database's history; a change writes
 * what changed - what adds as statements, what can lose data as comments to
 * review - and a run with nothing changed writes nothing. The migrations are
 * run in order on H2, which ends with the tables of the last model.
 */
public class DdlMigrationTest {

    private static final String[] KEYS = {"elasticore.base.path", "elasticore.rules.force", DdlConfig.DATABASES,
            DdlConfig.MIGRATION, "elasticore.ddl.now", "elasticore.architecture", "elasticore.language"};

    private final String[] saved = new String[KEYS.length];
    private final List<File> projects = new ArrayList<File>();

    {
        for (int i = 0; i < KEYS.length; i++) saved[i] = System.getProperty(KEYS[i]);
    }

    @AfterEach
    void clean() throws IOException {
        for (int i = 0; i < KEYS.length; i++) {
            if (saved[i] == null) System.clearProperty(KEYS[i]);
            else System.setProperty(KEYS[i], saved[i]);
        }
        for (File p : projects) delete(p.toPath());
    }

    private static final String V1 = String.join("\n",
            "entity:",
            "  Customer:",
            "    meta: entity @expose(20) @access(*=public)",
            "    fields:",
            "      customerId: long @id @sequence",
            "      name: string(30)!",
            "      nickname: string(20)",
            "      grade: int",
            "");

    private static final String V2 = String.join("\n",
            "entity:",
            "  Customer:",
            "    meta: entity @expose(20) @access(*=public)",
            "    fields:",
            "      customerId: long @id @sequence",
            "      name: string(60)!",             // longer: widened
            "      grade: int",                    // nickname gone: a column to review
            "      email: string(100)",            // new, may be empty
            "      tier: int!",                    // new, required: added empty, closed by review
            "  Visit:",                            // a new table, and its key to Customer
            "    meta: entity @expose(20) @access(*=public)",
            "    fields:",
            "      visitId: long @id @sequence",
            "      customer: Customer @ManyToOne",
            "      memo: string(200)",
            "");

    @Test
    public void eachChangeOfTheModelIsAMigrationAndTheyBuildTheLastSchema() throws Exception {
        File project = project(V1);
        System.setProperty(DdlConfig.DATABASES, "h2,postgresql,oracle,sqlserver");
        System.setProperty(DdlConfig.MIGRATION, "flyway");

        System.setProperty("elasticore.ddl.now", "20260101000000");
        run(project);
        File h2 = new File(project, "src/main/resources/db/migration/h2/V20260101000000__init.sql");
        assertTrue(h2.isFile(), "the first migration of H2's history");
        assertTrue(new File(project, "src/main/resources/db/migration/oracle/V20260101000000__init.sql").isFile());
        assertTrue(new File(project, "src/main/resources/db/migration/schema-state.json").isFile());
        assertTrue(new File(project, "src/main/resources/db/schema-sqlserver.sql").isFile());
        // MyBatis's own H2 file gives way to the project's schema.
        assertNull(find(project, "dgshop-schema.sql"));

        // Nothing changed: nothing written.
        System.setProperty("elasticore.ddl.now", "20260102000000");
        run(project);
        assertFalse(new File(project, "src/main/resources/db/migration/h2").list().length > 1, "a run without a change wrote a migration");

        // The model changes.
        Files.write(new File(project, "src/main/resources/blueprint/dgshop/model/model.yml").toPath(),
                V2.getBytes(StandardCharsets.UTF_8));
        System.setProperty("elasticore.ddl.now", "20260103000000");
        run(project);
        File[] h2s = new File(project, "src/main/resources/db/migration/h2").listFiles();
        assertEquals(2, h2s.length);
        File change = null;
        for (File f : h2s) if (f.getName().startsWith("V20260103000000__")) change = f;
        assertNotNull(change, "the change's migration");
        String sql = new String(Files.readAllBytes(change.toPath()), StandardCharsets.UTF_8);
        assertTrue(sql.contains("CREATE TABLE visit ("), sql);
        assertTrue(sql.contains("ALTER TABLE customer ADD COLUMN email VARCHAR(100);"), sql);
        assertTrue(sql.contains("ALTER TABLE customer ALTER COLUMN name SET DATA TYPE VARCHAR(60);"), sql);
        assertTrue(sql.contains("ALTER TABLE visit ADD CONSTRAINT fk_visit_customer FOREIGN KEY (customer_customer_id)"), sql);
        // What can lose data waits, commented.
        assertTrue(sql.contains("-- REVIEW: 2 changes wait"), sql);
        assertTrue(sql.contains("-- ALTER TABLE customer DROP COLUMN nickname;"), sql);
        assertTrue(sql.contains("-- ALTER TABLE customer ALTER COLUMN tier SET NOT NULL;"), sql);
        assertTrue(sql.contains("ALTER TABLE customer ADD COLUMN tier INTEGER;"), sql);
        // Each database in its words.
        String oracle = read(new File(project, "src/main/resources/db/migration/oracle/" + change.getName()));
        assertTrue(oracle.contains("ALTER TABLE customer ADD (email VARCHAR2(100 CHAR));"), oracle);
        assertTrue(oracle.contains("ALTER TABLE customer MODIFY (name VARCHAR2(60 CHAR));"), oracle);
        String sqlServer = read(new File(project, "src/main/resources/db/migration/sqlserver/" + change.getName()));
        assertTrue(sqlServer.contains("ALTER TABLE customer ALTER COLUMN name NVARCHAR(60) NOT NULL;"), sqlServer);
        String postgres = read(new File(project, "src/main/resources/db/migration/postgresql/" + change.getName()));
        assertTrue(postgres.contains("ALTER TABLE customer ALTER COLUMN name TYPE VARCHAR(60);"), postgres);

        // The migrations in order, on H2, make the tables the last model needs.
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:ddl" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")) {
            List<File> ordered = new ArrayList<File>();
            for (File f : h2s) ordered.add(f);
            ordered.sort((a, b) -> a.getName().compareTo(b.getName()));
            for (File f : ordered) execute(c, read(f));
            DatabaseMetaData md = c.getMetaData();
            assertEquals(60, columnSize(md, "CUSTOMER", "NAME"));
            assertTrue(columnSize(md, "CUSTOMER", "EMAIL") > 0);
            assertTrue(columnSize(md, "VISIT", "MEMO") > 0);
            // The dropped column waits for review: it is still there.
            assertTrue(columnSize(md, "CUSTOMER", "NICKNAME") > 0);
        }
    }

    @Test
    public void aRunThatWouldBreakAHistoryIsRefused() throws Exception {
        File project = project(V1);
        System.setProperty(DdlConfig.MIGRATION, "flyway");
        File migration = new File(project, "src/main/resources/db/migration");
        File state = new File(migration, "schema-state.json");

        System.setProperty(DdlConfig.DATABASES, "h2,postgresql");
        System.setProperty("elasticore.ddl.now", "20260101000000");
        run(project);
        // PostgreSQL leaves the list: its history stops.
        System.setProperty(DdlConfig.DATABASES, "h2");
        System.setProperty("elasticore.ddl.now", "20260102000000");
        assertTrue(run(project, 0).contains("postgresql is no longer among the databases"));
        assertFalse(read(state).contains("postgresql"), read(state));
        // And comes back: a new init over the old history would fail, so nothing is written.
        System.setProperty(DdlConfig.DATABASES, "h2,postgresql");
        System.setProperty("elasticore.ddl.now", "20260103000000");
        String log = run(project, 1);
        assertTrue(log.contains("taken out of the databases and put back"), log);
        assertEquals(1, new File(migration, "postgresql").list().length);
        assertFalse(read(state).contains("postgresql"), "the state did not move");

        // The state lost while migrations remain: every table would be new again.
        System.setProperty(DdlConfig.DATABASES, "h2");
        assertTrue(state.delete());
        log = run(project, 1);
        assertTrue(log.contains("holds migrations but no schema-state.json"), log);
        assertFalse(state.exists());
        assertEquals(1, new File(migration, "h2").list().length);

        // A history of the project's own: -PddlBaseline starts the state from the model, with no init.
        System.setProperty("elasticore.ddl.baseline", "");
        try {
            run(project, 0);
        } finally {
            System.clearProperty("elasticore.ddl.baseline");
        }
        assertTrue(state.isFile());
        assertEquals(1, new File(migration, "h2").list().length);

        // A state a merge left its markers in is refused, not a crash.
        Files.write(state.toPath(), "<<<<<<< HEAD\n{}\n=======\n{}\n>>>>>>> other\n".getBytes(StandardCharsets.UTF_8));
        log = run(project, 1);
        assertTrue(log.contains("cannot be read"), log);

        // A migration of this run's version already there, whatever its name: Flyway takes one per version.
        assertTrue(state.delete());
        System.setProperty("elasticore.ddl.baseline", "");
        try {
            run(project, 0);
        } finally {
            System.clearProperty("elasticore.ddl.baseline");
        }
        Files.write(new File(project, "src/main/resources/blueprint/dgshop/model/model.yml").toPath(),
                V2.getBytes(StandardCharsets.UTF_8));
        System.setProperty("elasticore.ddl.now", "20260109000000");
        Files.write(new File(migration, "h2/V20260109000000__mine.sql").toPath(), "-- mine\n".getBytes(StandardCharsets.UTF_8));
        String before = read(state);
        log = run(project, 1);
        assertTrue(log.contains("has this run's version already"), log);
        assertEquals(before, read(state), "the state did not move");
    }

    @Test
    public void aDomainCanLeaveTheProjectsDdlOrHaveADatabaseOfItsOwn() throws Exception {
        File project = project(V1);
        System.setProperty(DdlConfig.DATABASES, "h2");
        addDomain(project, "legacy", "  ddl: false\n");
        addDomain(project, "billing", "  ddl:\n    separate: true\n    databases: [oracle]\n");
        run(project);
        String common = read(new File(project, "src/main/resources/db/schema-h2.sql"));
        assertTrue(common.contains("CREATE TABLE customer ("), common);
        assertFalse(common.contains("legacy_entry"), common);
        assertFalse(common.contains("billing_entry"), common);
        String billing = read(new File(project, "src/main/resources/db/billing/schema-oracle.sql"));
        assertTrue(billing.contains("CREATE TABLE billing_entry ("), billing);
        assertFalse(new File(project, "src/main/resources/db/billing/schema-h2.sql").exists());
    }

    // ---- helpers -----------------------------------------------------------------

    private void addDomain(File project, String domain, String ddl) throws IOException {
        File blueprint = new File(project, "src/main/resources/blueprint/" + domain);
        assertTrue(new File(blueprint, "model").mkdirs());
        Files.write(new File(blueprint, "env.yml").toPath(), String.join("\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: " + domain,
                "    framework: springboot",
                "    mode: mybatis",
                "    j2ee: jakarta",
                ddl.replaceAll("(?m)^", "  ").replaceAll("\\s+$", ""),
                "  namespace:",
                "    base: io.acme." + domain,
                "").getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), String.join("\n",
                "entity:",
                "  " + Character.toUpperCase(domain.charAt(0)) + domain.substring(1) + "Entry:",
                "    meta: entity @expose(20) @access(*=public)",
                "    fields:",
                "      entryId: long @id @sequence",
                "      note: string(40)",
                "").getBytes(StandardCharsets.UTF_8));
    }

    private static void execute(Connection c, String script) throws Exception {
        try (Statement st = c.createStatement()) {
            StringBuilder sb = new StringBuilder();
            for (String line : script.split("\n")) {
                String t = line.trim();
                if (t.startsWith("--") || t.isEmpty()) continue;
                sb.append(line).append('\n');
                if (t.endsWith(";")) {
                    String s = sb.toString().trim();
                    st.execute(s.substring(0, s.length() - 1));
                    sb.setLength(0);
                }
            }
        }
    }

    private static int columnSize(DatabaseMetaData md, String table, String column) throws Exception {
        try (ResultSet rs = md.getColumns(null, null, table, column)) {
            return rs.next() ? rs.getInt("COLUMN_SIZE") : -1;
        }
    }

    private String run(File project) throws Exception {
        return run(project, 0);
    }

    private String run(File project, int expected) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        PrintStream err = System.err;
        PrintStream both = new PrintStream(bytes, true, "UTF-8");
        System.setOut(both);
        System.setErr(both);
        int code;
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.rules.force", "true");
            code = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        assertEquals(expected, code, log);
        return log;
    }

    private File project(String model) throws IOException {
        File dir = Files.createTempDirectory("elcore-ddl").toFile();
        projects.add(dir);
        File blueprint = new File(dir, "src/main/resources/blueprint/dgshop");
        assertTrue(new File(blueprint, "model").mkdirs());
        Files.write(new File(blueprint, "env.yml").toPath(), String.join("\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: dgshop",
                "    framework: springboot",
                "    mode: mybatis",
                "    j2ee: jakarta",
                "  namespace:",
                "    base: io.acme.dgshop",
                "").getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static Path find(File project, String name) throws IOException {
        File root = new File(project, "src/main/resources");
        if (!root.isDirectory()) return null;
        try (Stream<Path> all = Files.walk(root.toPath())) {
            return all.filter(p -> p.getFileName().toString().equals(name)).findFirst().orElse(null);
        }
    }

    private static void delete(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> all = Files.walk(root)) {
            List<Path> paths = new ArrayList<Path>();
            all.forEach(paths::add);
            for (int i = paths.size() - 1; i >= 0; i--) Files.deleteIfExists(paths.get(i));
        }
    }
}
