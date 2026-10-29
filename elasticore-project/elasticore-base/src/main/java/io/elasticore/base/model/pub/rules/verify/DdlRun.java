package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.schema.Database;
import io.elasticore.base.model.pub.rules.schema.DdlConfig;
import io.elasticore.base.model.pub.rules.schema.Schema;
import io.elasticore.base.model.pub.rules.schema.SchemaDiff;
import io.elasticore.base.model.pub.rules.schema.SchemaWriter;
import io.elasticore.base.model.pub.rules.schema.Table;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The project's DDL, once every domain is generated: each domain's tables -
 * a MyBatis domain's from its model, a JPA domain's from its entity sources -
 * written per database, and, with migrations on, the change since the last
 * run as a Flyway migration per database.
 *
 * <pre>
 * src/main/resources/db/schema-{db}.sql                       every table, regenerated
 * src/main/resources/db/migration/{db}/V{time}__{what}.sql   written once, then the developer's
 * src/main/resources/db/migration/schema-state.json          the schema the migrations build
 * </pre>
 * A domain with {@code ddl.separate: true} has the same under db/{domain}/.
 * The full schema is a generated file like any other; a migration is written
 * once and never again - Flyway checks what it ran - and the state file says
 * what the migrations have built, so the next change of the model is a
 * migration from it. Commit both.
 */
final class DdlRun {

    /** One domain's part: its name, its settings, and where its tables come from. */
    static final class Part {
        final String domain;
        final DdlConfig config;
        final String root;
        /** A MyBatis domain's entities; null for a JPA one, whose tables are read off its entity package. */
        final List<EntitySpec> mybatis;
        final String entityPackage;

        Part(String domain, DdlConfig config, String root, List<EntitySpec> mybatis, String entityPackage) {
            this.domain = domain;
            this.config = config;
            this.root = root;
            this.mybatis = mybatis;
            this.entityPackage = entityPackage;
        }
    }

    /** What writes a generated (stamped, regenerable) file under a root. */
    interface Writer {
        void write(String root, String relativeDir, String fileName, String extension, String content);
    }

    private final List<Part> parts;
    private final String projectRoot;
    private final List<String> sourceRoots;
    private final Writer writer;
    private final List<String> messages = new ArrayList<String>();

    DdlRun(List<Part> parts, String projectRoot, List<String> sourceRoots, Writer writer) {
        this.parts = parts;
        this.projectRoot = projectRoot;
        this.sourceRoots = sourceRoots;
        this.writer = writer;
    }

    List<String> messages() {
        return messages;
    }

    /** A group of domains in one database: the project's, or one separate domain's. */
    private static final class Group {
        final String name;
        final String root;
        final String dir;
        final Set<Database> databases = new LinkedHashSet<Database>();
        boolean migration;
        final List<Part> parts = new ArrayList<Part>();

        Group(String name, String root, String dir) {
            this.name = name;
            this.root = root;
            this.dir = dir;
        }
    }

    void run(DdlConfig project) throws IOException {
        Map<String, Group> groups = new LinkedHashMap<String, Group>();
        for (Part p : parts) {
            if (!p.config.enabled()) continue;
            Group g;
            if (p.config.separate) {
                // A domain of several models: one history for all of them.
                g = groups.get("separate:" + p.domain);
                if (g == null) groups.put("separate:" + p.domain, g = new Group(p.domain, p.root, "db/" + p.domain));
                g.databases.addAll(p.config.databases);
                g.migration |= p.config.migration;
            } else {
                g = groups.get("");
                if (g == null) {
                    groups.put("", g = new Group("the project", projectRoot, "db"));
                    if (project.enabled()) {
                        g.databases.addAll(project.databases);
                        g.migration = project.migration;
                    }
                }
                if (!project.enabled()) {
                    g.databases.addAll(p.config.databases);
                    g.migration |= p.config.migration;
                } else {
                    if (!new LinkedHashSet<Database>(p.config.databases).equals(g.databases)) {
                        messages.add(String.format("[elcore3] ddl: domain %s names other databases than the project's, in the"
                                + " project's schema - its tables are written for %s (ddl.separate: true for a database of its own)",
                                p.domain, ids(g.databases)));
                    }
                    if (p.config.migration != g.migration) {
                        messages.add(String.format("[elcore3] ddl: domain %s's ddl.migration is not the project's, whose history"
                                + " it is in - the project's (%s) holds; ddl.separate: true for a history of its own",
                                p.domain, g.migration ? "flyway" : "none"));
                    }
                }
            }
            g.parts.add(p);
        }
        if (groups.isEmpty()) return;

        JpaSchemaReader reader = null;
        boolean anyJpa = false;
        for (Part p : parts) anyJpa |= p.config.enabled() && p.mybatis == null;
        if (anyJpa) {
            List<File> resources = new ArrayList<File>();
            for (String d : new LinkedHashSet<String>(sourceRoots)) resources.add(new File(d, "src/main/resources"));
            reader = new JpaSchemaReader().readSettings(resources).read(roots());
            // A domain mapped in XML (mapping: xml) says it in orm.xml, which Hibernate reads too.
            for (String d : new LinkedHashSet<String>(sourceRoots)) {
                reader.readOrmXml(new File(d, "src/main/resources/META-INF/orm.xml"));
            }
        }
        // Each JPA domain's entities are the ones in its entity package; an
        // entity in none - the outbox, a class written by hand - is mapped by
        // Hibernate all the same, and goes with the project's tables.
        // Each entity belongs to the domain whose entity package holds it most closely - a domain the DDL
        // leaves out (ddl: false) keeps its entities out with it.
        Set<String> assigned = new java.util.HashSet<String>();
        Map<Part, List<String>> entitiesOf = new LinkedHashMap<Part, List<String>>();
        for (Part p : parts) entitiesOf.put(p, new ArrayList<String>());
        if (reader != null) {
            for (String fqcn : reader.entityClasses()) {
                String pkg = fqcn.substring(0, fqcn.lastIndexOf('.'));
                Part owner = null;
                for (Part p : parts) {
                    if (p.entityPackage == null || p.entityPackage.isEmpty()) continue;
                    if (!pkg.equals(p.entityPackage) && !pkg.startsWith(p.entityPackage + ".")) continue;
                    if (owner == null || p.entityPackage.length() > owner.entityPackage.length()) owner = p;
                }
                if (owner == null) continue;
                assigned.add(fqcn);
                if (owner.mybatis == null) entitiesOf.get(owner).add(fqcn);
            }
        }
        for (Map.Entry<String, Group> en : groups.entrySet()) {
            Group g = en.getValue();
            Schema schema = new Schema();
            for (Part p : g.parts) {
                Schema part = p.mybatis != null
                        ? io.elasticore.base.model.pub.rules.mybatis.MyBatisTables.of(p.mybatis)
                        : reader.schema(entitiesOf.get(p));
                for (Schema.Sequence s : part.sequences()) schema.sequence(s);
                for (Table t : part.tables()) schema.table(t);
            }
            if (en.getKey().isEmpty() && reader != null) {
                List<String> rest = new ArrayList<String>();
                for (String fqcn : reader.entityClasses()) if (!assigned.contains(fqcn)) rest.add(fqcn);
                if (!rest.isEmpty()) {
                    Schema part = reader.schema(rest);
                    for (Schema.Sequence s : part.sequences()) schema.sequence(s);
                    for (Table t : part.tables()) schema.table(t);
                }
            }
            dropForeignKeysLeaving(schema, g);
            if (schema.isEmpty()) continue;
            write(g, schema);
        }
        if (reader != null) {
            for (String w : reader.warnings()) messages.add("[elcore3] ddl: " + w);
        }
    }

    private List<File> roots() {
        Set<String> dirs = new LinkedHashSet<String>(sourceRoots);
        List<File> out = new ArrayList<File>();
        for (String d : dirs) {
            out.add(new File(d, "src/main/java"));
            out.add(new File(d, "src/main/kotlin"));
        }
        return out;
    }

    /** A foreign key to a table of another database cannot be: it is left out, and said. */
    private void dropForeignKeysLeaving(Schema schema, Group g) {
        for (Table t : schema.tables()) {
            List<Table.ForeignKey> keep = new ArrayList<Table.ForeignKey>();
            boolean dropped = false;
            for (Table.ForeignKey fk : t.foreignKeys()) {
                if (schema.table(fk.table) != null) keep.add(fk);
                else {
                    dropped = true;
                    messages.add(String.format("[elcore3] ddl: %s.%s points at %s, which is not in %s's database - no foreign key",
                            t.name, String.join(",", fk.columns), fk.table, g.name));
                }
            }
            if (dropped) {
                Table copy = new Table(t.name, t.source);
                for (io.elasticore.base.model.pub.rules.schema.Column c : t.columns()) copy.column(c);
                copy.primaryKey(t.primaryKey());
                for (Table.ForeignKey fk : keep) copy.foreignKey(fk);
                for (Table.Index i : t.indexes()) copy.index(i);
                schema.replace(copy);
            }
        }
    }

    private void write(Group g, Schema schema) throws IOException {
        for (Database db : g.databases) {
            String body = "-- The tables of " + g.name + ", for " + db.title + ".\n"
                    + "-- Generated by ElastiCORE from the model, in the column types that database's Hibernate dialect would\n"
                    + "-- choose. Run it with spring.sql.init.schema-locations on an empty database"
                    + (g.migration ? "; the migrations in migration/ build the same, step by step." : ".") + "\n\n"
                    + SchemaWriter.body(schema, db) + "\n";
            writer.write(g.root, g.dir, "schema-" + db.id, "sql", body);
        }
        if (!g.migration) return;

        File dir = new File(g.root, "src/main/resources/" + g.dir + "/migration");
        File stateFile = new File(dir, "schema-state.json");
        Schema state = new Schema();
        if (stateFile.isFile()) {
            try {
                state = Schema.fromJson(new String(Files.readAllBytes(stateFile.toPath()), StandardCharsets.UTF_8));
            } catch (RuntimeException e) {
                // A merge that left its markers in, most likely: two branches each wrote a migration.
                problems.add(String.format("[elcore3] ddl: %s cannot be read (%s) - after a merge, take the state of the"
                        + " branch whose migrations are newest and build again; no migration was written",
                        relative(stateFile), e.getMessage()));
                return;
            }
        }
        boolean baseline = System.getProperty(BASELINE) != null && !"false".equals(System.getProperty(BASELINE));
        if (!stateFile.isFile()) {
            for (Database db : Database.values()) {
                if (!hasMigrations(new File(dir, db.id))) continue;
                if (baseline) {
                    // The project's own history built the tables: the state starts from the model, with no init.
                    writeState(stateFile, schema, g);
                    messages.add(String.format("[elcore3] ddl: %s starts from the model as it is - check that the database"
                            + " matches it; the next change is a migration", relative(stateFile)));
                    return;
                }
                // The state lost while migrations remain: every table would be "new" again, and Flyway refuse it.
                problems.add(String.format("[elcore3] ddl: %s holds migrations but no schema-state.json - restore it from"
                        + " version control, or, for a Flyway history of the project's own that the database is already"
                        + " up to the model with, build once with -PddlBaseline; no migration was written", relative(dir)));
                return;
            }
        }
        for (String had : state.databases()) {
            Database d = Database.of(had);
            if (d == null || !g.databases.contains(d)) {
                messages.add(String.format("[elcore3] ddl: %s is no longer among the databases - its migrations stop here",
                        had));
            }
        }

        String version = version();
        List<SchemaDiff.Change> changes = SchemaDiff.between(state, schema);
        Map<File, String> planned = new LinkedHashMap<File, String>();
        for (Database db : g.databases) {
            File vendorDir = new File(dir, db.id);
            if (!state.databases().contains(db.id)) {
                if (hasMigrations(vendorDir)) {
                    // Its history stopped when it left the list; starting it again over the old would fail.
                    problems.add(String.format("[elcore3] ddl: %s already holds migrations, but the state does not follow"
                            + " %s - it was taken out of the databases and put back. Bring its history up to the model by"
                            + " hand, then add \"%s\" to databases in schema-state.json", relative(vendorDir), db.id, db.id));
                    return;
                }
                // A database the history has not started for starts from what the model is now.
                planned.put(new File(vendorDir, "V" + version + "__init.sql"),
                        "-- The tables of " + g.name + ", for " + db.title + ": the first migration of its history.\n"
                                + "-- Written by ElastiCORE; yours from now on - it is never written again.\n\n"
                                + SchemaWriter.body(schema, db) + "\n");
            } else if (!changes.isEmpty()) {
                planned.put(new File(vendorDir, "V" + version + "__" + SchemaDiff.summary(changes) + ".sql"),
                        SchemaDiff.render(changes, db, "The change of " + g.name + "'s model since the last migration"));
            }
        }
        if (planned.isEmpty() && state.databases().equals(idSet(g.databases))) return;
        // Flyway takes one migration of a version: a file of this second already there, whatever it says, is refused.
        for (File f : planned.keySet()) {
            String[] names = f.getParentFile().list();
            if (names == null) continue;
            for (String n : names) {
                if (n.startsWith("V" + version + "__")) {
                    problems.add(String.format("[elcore3] ddl: %s/%s has this run's version already - no migration was"
                            + " written; build again", relative(f.getParentFile()), n));
                    return;
                }
            }
        }
        // Every migration, then the state: a run that stops between leaves no migration the state does not follow.
        List<File> written = new ArrayList<File>();
        try {
            for (Map.Entry<File, String> en : planned.entrySet()) {
                Files.createDirectories(en.getKey().getParentFile().toPath());
                Files.write(en.getKey().toPath(), en.getValue().getBytes(StandardCharsets.UTF_8));
                written.add(en.getKey());
                System.out.println("    migration    " + relative(en.getKey()) + "  WROTE");
            }
            writeState(stateFile, schema, g);
        } catch (IOException e) {
            for (File f : written) Files.deleteIfExists(f.toPath());
            throw e;
        }
        for (SchemaDiff.Change c : changes) {
            if (!c.safe && !planned.isEmpty()) {
                messages.add(String.format("[elcore3] ddl: REVIEW %s/*/V%s: %s", relative(dir), version, c.description));
            }
        }
    }

    /** -PddlBaseline: the state starts from the model over a Flyway history the project wrote itself. */
    static final String BASELINE = "elasticore.ddl.baseline";

    /** The state, whole or not at all: written beside, then moved over the old one. */
    private void writeState(File stateFile, Schema schema, Group g) throws IOException {
        schema.databases().clear();
        for (Database db : g.databases) schema.databases().add(db.id);
        Files.createDirectories(stateFile.getParentFile().toPath());
        File tmp = new File(stateFile.getParentFile(), stateFile.getName() + ".tmp");
        Files.write(tmp.toPath(), schema.toJson().getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp.toPath(), stateFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), stateFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("    migration    " + relative(stateFile) + "  WROTE");
    }

    /** Refusals: a history this run would break. The runner fails the model check with them. */
    private final List<String> problems = new ArrayList<String>();

    List<String> problems() {
        return problems;
    }

    private static boolean hasMigrations(File vendorDir) {
        String[] names = vendorDir.list();
        if (names == null) return false;
        for (String n : names) if (n.startsWith("V") && n.endsWith(".sql")) return true;
        return false;
    }

    private static Set<String> idSet(Set<Database> dbs) {
        Set<String> out = new LinkedHashSet<String>();
        for (Database d : dbs) out.add(d.id);
        return out;
    }

    private String relative(File f) {
        String p = f.getAbsolutePath().replace('\\', '/');
        String r = new File(projectRoot).getAbsolutePath().replace('\\', '/');
        return p.startsWith(r) ? p.substring(r.length() + 1) : p;
    }

    /** The migration's version: the time it was written (elasticore.ddl.now, for a test, says it). */
    private static String version() {
        String fixed = System.getProperty("elasticore.ddl.now");
        if (fixed != null && !fixed.trim().isEmpty()) return fixed.trim();
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private static String ids(Set<Database> dbs) {
        List<String> out = new ArrayList<String>();
        for (Database d : dbs) out.add(d.id);
        return String.join(", ", out);
    }
}
