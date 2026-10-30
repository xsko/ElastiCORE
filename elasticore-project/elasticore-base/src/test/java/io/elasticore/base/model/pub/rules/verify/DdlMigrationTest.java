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

    /** What the last {@link #change} printed. */
    private String lastLog = "";

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
    public void aKeyNamedByAHistoryTakenOverIsNotDroppedAsWritten() throws Exception {
        // The project's own history built the database - Hibernate names a key by a hash - and the state
        // starts from the model (-PddlBaseline), naming its keys as the model would. A statement by such a
        // name waits for review; a key the migrations made later is theirs, and goes as written.
        File project = project(V2);
        System.setProperty(DdlConfig.MIGRATION, "flyway");
        System.setProperty(DdlConfig.DATABASES, "h2");
        File migration = new File(project, "src/main/resources/db/migration");
        File state = new File(migration, "schema-state.json");
        File h2 = new File(migration, "h2");
        assertTrue(h2.mkdirs());
        Files.write(new File(h2, "V1__mine.sql").toPath(), "-- the project's own\n".getBytes(StandardCharsets.UTF_8));
        System.setProperty("elasticore.ddl.baseline", "");
        try {
            run(project, 0);
        } finally {
            System.clearProperty("elasticore.ddl.baseline");
        }
        assertTrue(read(state).contains("\"takenOver\""), read(state));
        assertTrue(read(state).contains("\"fk_visit_customer\""), read(state));

        // A change elsewhere keeps the name taken over while the model still has the key.
        String sql = change(project, V2 + "      note: string(40)\n", "20260101120000");
        assertTrue(sql.contains("note"), sql);
        assertTrue(read(state).contains("\"fk_visit_customer\""), "the name taken over was forgotten: " + read(state));

        String withoutCustomer = V2.replace("      customer: Customer @ManyToOne\n", "");
        sql = change(project, withoutCustomer, "20260102000000");
        assertTrue(sql.contains("-- ALTER TABLE visit DROP CONSTRAINT fk_visit_customer;"), sql);
        assertFalse(sql.contains("\nALTER TABLE visit DROP CONSTRAINT fk_visit_customer;"), sql);
        assertTrue(sql.contains("taken over with -PddlBaseline"), sql);
        // The build says so, for every database's file.
        assertTrue(
                lastLog.contains("[elcore3] ddl: REVIEW ")
                        && lastLog.contains("/*/V20260102000000: drop foreign key fk_visit_customer"),
                lastLog);
        assertFalse(read(state).contains("\"takenOver\""), "nothing by a taken-over name is left: " + read(state));

        // A key the migrations make is named by them: dropped later, as written.
        sql = change(project, withoutCustomer + "      owner: Customer @ManyToOne\n", "20260103000000");
        assertTrue(sql.contains("\nALTER TABLE visit ADD CONSTRAINT fk_visit_owner FOREIGN KEY"), sql);
        assertFalse(read(state).contains("\"takenOver\""), read(state));
        sql = change(project, withoutCustomer, "20260104000000");
        assertTrue(sql.contains("\nALTER TABLE visit DROP CONSTRAINT fk_visit_owner;"), sql);
        assertFalse(sql.contains("-- ALTER TABLE visit DROP CONSTRAINT fk_visit_owner"), sql);
        assertFalse(lastLog.contains("fk_visit_owner"), lastLog);
    }

    @Test
    public void whatOnlySqlServerDropsAndMakesAgainWaitsThereAlone() throws Exception {
        // A history of the project's own for H2 and SQL Server, taken over with its index. Widening the
        // column the index is on: SQL Server drops the index and makes it again by the name taken over -
        // that waits there; H2 widens the column as written.
        String model = V1.replace(
                "@access(*=public)", "@access(*=public) @Index(name=\"ix_customer_name\", columnList=\"name\")");
        File project = project(model);
        System.setProperty(DdlConfig.MIGRATION, "flyway");
        System.setProperty(DdlConfig.DATABASES, "h2,sqlserver");
        File migration = new File(project, "src/main/resources/db/migration");
        for (String db : new String[] {"h2", "sqlserver"}) {
            File dir = new File(migration, db);
            assertTrue(dir.mkdirs());
            Files.write(
                    new File(dir, "V1__mine.sql").toPath(), "-- the project's own\n".getBytes(StandardCharsets.UTF_8));
        }
        System.setProperty("elasticore.ddl.baseline", "");
        try {
            run(project, 0);
        } finally {
            System.clearProperty("elasticore.ddl.baseline");
        }
        assertTrue(read(new File(migration, "schema-state.json")).contains("\"ix_customer_name\""));

        String h2 = change(project, model.replace("name: string(30)!", "name: string(60)!"), "20260105000000");
        assertTrue(h2.contains("\nALTER TABLE customer ALTER COLUMN name"), h2);
        File sqlserver = null;
        for (File each : new File(migration, "sqlserver").listFiles()) {
            if (each.getName().startsWith("V20260105000000__")) sqlserver = each;
        }
        assertNotNull(sqlserver);
        String ms = read(sqlserver);
        assertTrue(ms.contains("-- ALTER TABLE customer ALTER COLUMN name") && ms.contains("ix_customer_name"), ms);
        assertTrue(lastLog.contains("/sqlserver/V20260105000000: customer.name"), lastLog);
        assertFalse(
                lastLog.contains("REVIEW src/main/resources/db/migration/h2/")
                        || lastLog.contains("/*/V20260105000000"),
                lastLog);
    }

    /** The model changes; the migration of that change, for H2. */
    private String change(File project, String model, String version) throws Exception {
        Files.write(
                new File(project, "src/main/resources/blueprint/dgshop/model/model.yml").toPath(),
                model.getBytes(StandardCharsets.UTF_8));
        System.setProperty("elasticore.ddl.now", version);
        lastLog = run(project, 0);
        File f = null;
        File[] all = new File(project, "src/main/resources/db/migration/h2").listFiles();
        assertNotNull(all);
        for (File each : all) if (each.getName().startsWith("V" + version + "__")) f = each;
        assertNotNull(f, "a migration of " + version);
        return read(f);
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

    @Test
    public void aHistoryOfTheProjectsOwnInTheDefaultLocationIsNotWrittenOver() throws Exception {
        // Spring Boot's default, classpath:db/migration, holds the project's own history - and Flyway
        // reads the folders under it, so a first migration in migration/h2/ would create every table again.
        File project = project(V1);
        System.setProperty(DdlConfig.MIGRATION, "flyway");
        System.setProperty(DdlConfig.DATABASES, "h2");
        File migration = new File(project, "src/main/resources/db/migration");
        File state = new File(migration, "schema-state.json");
        assertTrue(migration.mkdirs());
        Files.write(
                new File(migration, "V1__baseline.sql").toPath(),
                "-- the project's own\n".getBytes(StandardCharsets.UTF_8));

        System.setProperty("elasticore.ddl.now", "20260101000000");
        String log = run(project, 1);
        assertTrue(log.contains("holds a Flyway history of the project's own"), log);
        assertFalse(state.exists(), "no state");
        assertFalse(new File(migration, "h2").exists(), "no first migration beside the history");

        // -PddlBaseline: the state starts from the model, and the next change goes into migration/h2/.
        System.setProperty("elasticore.ddl.baseline", "");
        try {
            run(project, 0);
        } finally {
            System.clearProperty("elasticore.ddl.baseline");
        }
        assertTrue(state.isFile());
        assertFalse(new File(migration, "h2").exists(), "a baseline writes no init");
        Files.write(
                new File(project, "src/main/resources/blueprint/dgshop/model/model.yml").toPath(),
                V2.getBytes(StandardCharsets.UTF_8));
        System.setProperty("elasticore.ddl.now", "20260102000000");
        run(project, 0);
        String[] h2 = new File(migration, "h2").list();
        assertNotNull(h2);
        assertEquals(1, h2.length);
        assertTrue(h2[0].startsWith("V20260102000000__") && !h2[0].contains("init"), h2[0]);
        assertTrue(read(new File(migration, "h2/" + h2[0])).contains("CREATE TABLE visit ("));
    }

    @Test
    public void aHistoryInTheDefaultLocationIsNotForSeveralDatabases() throws Exception {
        // Flyway reading migration/ and its folders would run each database's migrations on the other.
        File project = project(V1);
        System.setProperty(DdlConfig.MIGRATION, "flyway");
        System.setProperty(DdlConfig.DATABASES, "h2,postgresql");
        File migration = new File(project, "src/main/resources/db/migration");
        assertTrue(migration.mkdirs());
        Files.write(
                new File(migration, "V1__baseline.sql").toPath(),
                "-- the project's own\n".getBytes(StandardCharsets.UTF_8));
        System.setProperty("elasticore.ddl.baseline", "");
        String log;
        try {
            log = run(project, 1);
        } finally {
            System.clearProperty("elasticore.ddl.baseline");
        }
        assertTrue(log.contains("Move the history into"), log);
        assertFalse(new File(migration, "schema-state.json").exists());
        assertFalse(new File(migration, "h2").exists());
        assertFalse(new File(migration, "postgresql").exists());
    }

    @Test
    public void aBaselineOverTheDefaultLocationTakesTheNamesOverAndASecondDatabaseIsRefusedLater() throws Exception {
        // The history in migration/ itself, for one database, built with a key the model names fk_visit_customer.
        File project = project(V2);
        System.setProperty(DdlConfig.MIGRATION, "flyway");
        System.setProperty(DdlConfig.DATABASES, "h2");
        File migration = new File(project, "src/main/resources/db/migration");
        File state = new File(migration, "schema-state.json");
        assertTrue(migration.mkdirs());
        Files.write(
                new File(migration, "V1__baseline.sql").toPath(),
                "-- the project's own\n".getBytes(StandardCharsets.UTF_8));
        System.setProperty("elasticore.ddl.baseline", "");
        try {
            run(project, 0);
        } finally {
            System.clearProperty("elasticore.ddl.baseline");
        }
        // As for a history in migration/{db}/: the names are taken over, so dropping the key waits for review.
        assertTrue(read(state).contains("\"fk_visit_customer\""), read(state));
        String sql = change(project, V2.replace("      customer: Customer @ManyToOne\n", ""), "20260102000000");
        assertTrue(sql.contains("-- ALTER TABLE visit DROP CONSTRAINT fk_visit_customer;"), sql);

        // A second database after the baseline: its first migration would sit where Flyway reads the other's.
        System.setProperty(DdlConfig.DATABASES, "h2,postgresql");
        System.setProperty("elasticore.ddl.now", "20260103000000");
        String log = run(project, 1);
        assertTrue(log.contains("Move the history into"), log);
        assertFalse(new File(migration, "postgresql").exists(), "no init beside the history");
    }

    @Test
    public void aHistoryOfTheProjectsOwnIsAnyMigrationFlywayReadsOutsideTheDatabasesFolders() throws Exception {
        File migration = Files.createTempDirectory("elcore-history").toFile();
        projects.add(migration);
        assertFalse(DdlRun.hasOwnHistory(migration));
        // The databases' own folders are ElastiCORE's.
        File h2 = new File(migration, "h2");
        assertTrue(h2.mkdirs());
        Files.write(new File(h2, "V1__init.sql").toPath(), new byte[0]);
        Files.write(new File(migration, "schema-state.json").toPath(), new byte[0]);
        Files.write(new File(migration, "README.sql").toPath(), new byte[0]);
        assertFalse(DdlRun.hasOwnHistory(migration));
        // A repeatable migration in migration/ itself.
        File repeatable = new File(migration, "R__views.sql");
        Files.write(repeatable.toPath(), new byte[0]);
        assertTrue(DdlRun.hasOwnHistory(migration));
        assertTrue(repeatable.delete());
        // Flyway's "common" beside "{vendor}": read only where the project's locations name it - not
        // a sign of the default location, so not counted (a multi-database build with it stays fine).
        File common = new File(migration, "common");
        assertTrue(common.mkdirs());
        Files.write(new File(common, "V2__seed.sql").toPath(), new byte[0]);
        Files.write(new File(common, "R__reference.sql").toPath(), new byte[0]);
        assertFalse(DdlRun.hasOwnHistory(migration));
        // A versioned one straight in migration/: the default location.
        Files.write(new File(migration, "V1__mine.sql").toPath(), new byte[0]);
        assertTrue(DdlRun.hasOwnHistory(migration));
    }
}
