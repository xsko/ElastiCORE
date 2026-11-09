package io.elasticore.base.model.pub.rules.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What DDL a domain's tables are written in, and where.
 *
 * The project says it once, in the build (the Gradle plugin passes it as
 * system properties):
 * <pre>
 * elasticore {
 *     ddl {
 *         databases = ['postgresql', 'oracle']   // h2, postgresql, oracle, sqlserver
 *         migration = 'flyway'                   // or none: the schema only
 *         comments = true                        // each column's label as its comment
 *         oracleVersion = 23                     // the Oracle the tables are for: a boolean is BOOLEAN from 23
 *     }
 * }
 * </pre>
 * and a domain changes what it needs in its env.yml - {@code ddl: false} to
 * leave its tables out (a domain that reads tables another system owns), or
 * any of {@code ddl.databases}, {@code ddl.migration}, {@code ddl.comments},
 * and {@code ddl.separate: true} for a domain whose tables are in a database
 * of their own (its files under db/{domain}/). The Oracle version is the
 * project's alone.
 */
public final class DdlConfig {

    public static final String DATABASES = "elasticore.ddl.databases";
    public static final String MIGRATION = "elasticore.ddl.migration";
    public static final String COMMENTS = "elasticore.ddl.comments";
    public static final String ORACLE_VERSION = "elasticore.ddl.oracleVersion";

    /** Off: the DDL is not written for these tables. */
    public static final DdlConfig OFF = new DdlConfig(Collections.<Database>emptyList(), false, false, false, 0);

    public final List<Database> databases;
    /** Whether the changes are written as Flyway migrations, with the state they build. */
    public final boolean migration;
    /** Whether the domain's tables are in a database of their own. */
    public final boolean separate;
    /** Whether the DDL says what each column holds - its label - as a comment (ddl { comments = true }). */
    public final boolean comments;
    /** The Oracle version the tables are for (ddl { oracleVersion = 23 }); 0 when unsaid, as below 23. */
    public final int oracleVersion;

    private DdlConfig(
            List<Database> databases, boolean migration, boolean separate, boolean comments, int oracleVersion) {
        this.databases = Collections.unmodifiableList(databases);
        this.migration = migration;
        this.separate = separate;
        this.comments = comments;
        this.oracleVersion = oracleVersion;
    }

    public boolean enabled() {
        return !databases.isEmpty();
    }

    /** The project's DDL, from the build; OFF when it says none. */
    public static DdlConfig global() {
        List<Database> dbs = databases(System.getProperty(DATABASES));
        int oracleVersion = oracleVersion(System.getProperty(ORACLE_VERSION));
        // Off, but still the Oracle a domain that switches its DDL on writes for.
        if (dbs.isEmpty()) return oracleVersion == 0 ? OFF : new DdlConfig(dbs, false, false, false, oracleVersion);
        return new DdlConfig(
                dbs,
                flyway(System.getProperty(MIGRATION)),
                false,
                Boolean.parseBoolean(
                        String.valueOf(System.getProperty(COMMENTS)).trim()),
                oracleVersion);
    }

    /** A source of a domain's env.yml settings by dotted key. */
    public interface Settings {
        String get(String key);
    }

    /**
     * A domain's DDL: the project's, with what the domain's env.yml says
     * instead. A domain can switch it on where the project has none.
     */
    public static DdlConfig forDomain(DdlConfig project, Settings env) {
        String whole = env.get("ddl");
        if (whole != null && ("false".equalsIgnoreCase(whole.trim()) || "none".equalsIgnoreCase(whole.trim()))) return OFF;
        List<Database> dbs = databases(env.get("ddl.databases"));
        if (dbs.isEmpty()) dbs = project.databases;
        if (dbs.isEmpty()) return OFF;
        String m = env.get("ddl.migration");
        boolean migration = m != null ? flyway(m) : project.migration;
        boolean separate = "true".equalsIgnoreCase(String.valueOf(env.get("ddl.separate")).trim());
        String c = env.get("ddl.comments");
        boolean comments = c != null ? "true".equalsIgnoreCase(c.trim()) : project.comments;
        return new DdlConfig(new ArrayList<Database>(dbs), migration, separate, comments, project.oracleVersion);
    }

    /** "h2, oracle", "[postgresql, sqlserver]" - an unknown name stops the run. */
    static List<Database> databases(String text) {
        List<Database> out = new ArrayList<Database>();
        if (text == null) return out;
        String t = text.trim();
        if (t.startsWith("[") && t.endsWith("]")) t = t.substring(1, t.length() - 1);
        for (String part : t.split(",")) {
            String id = part.trim();
            if (id.isEmpty()) continue;
            Database db = Database.of(id);
            if (db == null) {
                throw new IllegalArgumentException("ddl: '" + id + "' is no database the DDL is written for - h2, postgresql,"
                        + " oracle or sqlserver");
            }
            if (!out.contains(db)) out.add(db);
        }
        return out;
    }

    /** "23" - a major version, from 12 on; none said is 0. Anything else stops the run. */
    static int oracleVersion(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        String t = text.trim();
        int version = t.matches("\\d{1,4}") ? Integer.parseInt(t) : 0;
        if (version < 12) {
            throw new IllegalArgumentException(
                    "ddl: oracleVersion = '" + text + "' - the major version of the Oracle the"
                            + " tables are for, 12 or later (23 for BOOLEAN columns)");
        }
        return version;
    }

    static boolean flyway(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty() || t.equals("none") || t.equals("false")) return false;
        if (t.equals("flyway")) return true;
        throw new IllegalArgumentException("ddl: migration = '" + text + "' - flyway or none");
    }
}
