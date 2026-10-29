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
 *     }
 * }
 * </pre>
 * and a domain changes what it needs in its env.yml - {@code ddl: false} to
 * leave its tables out (a domain that reads tables another system owns), or
 * any of {@code ddl.databases}, {@code ddl.migration}, and {@code ddl.separate:
 * true} for a domain whose tables are in a database of their own (its files
 * under db/{domain}/).
 */
public final class DdlConfig {

    public static final String DATABASES = "elasticore.ddl.databases";
    public static final String MIGRATION = "elasticore.ddl.migration";

    /** Off: the DDL is not written for these tables. */
    public static final DdlConfig OFF = new DdlConfig(Collections.<Database>emptyList(), false, false);

    public final List<Database> databases;
    /** Whether the changes are written as Flyway migrations, with the state they build. */
    public final boolean migration;
    /** Whether the domain's tables are in a database of their own. */
    public final boolean separate;

    private DdlConfig(List<Database> databases, boolean migration, boolean separate) {
        this.databases = Collections.unmodifiableList(databases);
        this.migration = migration;
        this.separate = separate;
    }

    public boolean enabled() {
        return !databases.isEmpty();
    }

    /** The project's DDL, from the build; OFF when it says none. */
    public static DdlConfig global() {
        List<Database> dbs = databases(System.getProperty(DATABASES));
        if (dbs.isEmpty()) return OFF;
        return new DdlConfig(dbs, flyway(System.getProperty(MIGRATION)), false);
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
        return new DdlConfig(new ArrayList<Database>(dbs), migration, separate);
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

    static boolean flyway(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty() || t.equals("none") || t.equals("false")) return false;
        if (t.equals("flyway")) return true;
        throw new IllegalArgumentException("ddl: migration = '" + text + "' - flyway or none");
    }
}
