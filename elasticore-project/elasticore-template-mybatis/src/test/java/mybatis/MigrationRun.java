package mybatis;

import io.elasticore.base.model.pub.rules.schema.Column;
import io.elasticore.base.model.pub.rules.schema.ColumnType;
import io.elasticore.base.model.pub.rules.schema.Database;
import io.elasticore.base.model.pub.rules.schema.Schema;
import io.elasticore.base.model.pub.rules.schema.SchemaDiff;
import io.elasticore.base.model.pub.rules.schema.SchemaWriter;
import io.elasticore.base.model.pub.rules.schema.Table;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A migration the generator writes, run on a database: the first schema,
 * then a change - a text and a decimal widened (one under a unique index, which
 * SQL Server cannot alter in place), a column that may now be empty, a new
 * column with a unique index of its own, a new table with a foreign key and an
 * index, an index dropped - as {@link SchemaDiff} says it in that database's
 * words. The tables end as the second schema says.
 */
final class MigrationRun {

    private MigrationRun() {
    }

    static Schema first() {
        Schema s = new Schema();
        Table c = new Table("mig_customer", "test");
        c.column(Column.of("customer_id", ColumnType.BIGINT).nullable(false).identity(true));
        c.column(Column.of("name", ColumnType.TEXT).length(30).nullable(false));
        c.column(Column.of("grade", ColumnType.INTEGER).nullable(false));
        c.column(Column.of("amount", ColumnType.DECIMAL).decimal(10, 2));
        c.column(Column.of("active", ColumnType.BOOLEAN));
        c.column(Column.of("note", ColumnType.LONG_TEXT));
        c.column(Column.of("seen_at", ColumnType.TIMESTAMP));
        c.primaryKey("customer_id");
        c.index(new Table.Index("uk_mig_customer_name", Collections.singletonList("name"), true));
        c.index(new Table.Index("ix_mig_customer_grade", Collections.singletonList("grade"), false));
        s.table(c);
        return s;
    }

    static Schema second() {
        Schema s = new Schema();
        Table c = new Table("mig_customer", "test");
        c.column(Column.of("customer_id", ColumnType.BIGINT).nullable(false).identity(true));
        c.column(Column.of("name", ColumnType.TEXT).length(60).nullable(false));
        c.column(Column.of("grade", ColumnType.INTEGER));
        c.column(Column.of("amount", ColumnType.DECIMAL).decimal(12, 2));
        c.column(Column.of("active", ColumnType.BOOLEAN));
        c.column(Column.of("note", ColumnType.LONG_TEXT));
        c.column(Column.of("seen_at", ColumnType.TIMESTAMP));
        c.column(Column.of("email", ColumnType.TEXT).length(100));
        c.primaryKey("customer_id");
        c.index(new Table.Index("uk_mig_customer_name", Collections.singletonList("name"), true));
        c.index(new Table.Index("uk_mig_customer_email", Collections.singletonList("email"), true));
        s.table(c);
        Table v = new Table("mig_visit", "test");
        v.column(Column.of("visit_id", ColumnType.BIGINT).nullable(false).identity(true));
        v.column(Column.of("customer_customer_id", ColumnType.BIGINT));
        v.column(Column.of("memo", ColumnType.TEXT).length(200));
        v.primaryKey("visit_id");
        v.foreignKey(new Table.ForeignKey("fk_mig_visit_customer", Collections.singletonList("customer_customer_id"),
                "mig_customer", Collections.singletonList("customer_id")));
        v.index(new Table.Index("ix_mig_visit_memo", Collections.singletonList("memo"), false));
        s.table(v);
        return s;
    }

    /** Runs the first schema and the change on the connection, and checks the tables. */
    static void on(Connection c, Database db) throws Exception {
        execute(c, SchemaWriter.body(first(), db));
        List<SchemaDiff.Change> changes = SchemaDiff.between(first(), second());
        for (SchemaDiff.Change ch : changes) assertTrue(ch.safe, ch.description + " should run as written");
        execute(c, SchemaDiff.render(changes, db, 0, "test"));

        DatabaseMetaData md = c.getMetaData();
        assertEquals(60, size(md, db, "mig_customer", "name"));
        assertEquals(12, size(md, db, "mig_customer", "amount"));
        assertTrue(size(md, db, "mig_customer", "email") >= 100);
        assertTrue(size(md, db, "mig_visit", "memo") >= 200);

        // The rows the change allows: grade empty, two customers without an email (a filtered
        // unique index on SQL Server), a visit pointing at one; a taken name is still refused.
        try (Statement st = c.createStatement()) {
            st.execute("INSERT INTO mig_customer (name, amount) VALUES ('" + "x".repeat(50) + "', 1234567890.12)");
            st.execute("INSERT INTO mig_customer (name) VALUES ('second')");
        }
        long first;
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT MIN(customer_id) FROM mig_customer")) {
            rs.next();
            first = rs.getLong(1);
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO mig_visit (customer_customer_id, memo) VALUES (?, 'hello')")) {
            ps.setLong(1, first);
            ps.executeUpdate();
        }
        boolean refused = false;
        try (Statement st = c.createStatement()) {
            st.execute("INSERT INTO mig_customer (name) VALUES ('second')");
        } catch (java.sql.SQLException e) {
            refused = true;
        }
        assertTrue(refused, "the unique index on name was made again after the alter");
    }

    /** A table whose columns say what they hold (ddl comments on): one with a quote, one without a comment. */
    static Schema commented(String title, String memo, String body, boolean extra) {
        Table n = new Table("mig_note", "test").comments(true);
        n.column(Column.of("note_id", ColumnType.BIGINT).nullable(false).identity(true));
        n.column(Column.of("title", ColumnType.TEXT).length(100).comment(title));
        n.column(Column.of("memo", ColumnType.TEXT).length(200).comment(memo));
        n.column(Column.of("body", ColumnType.TEXT).length(200).comment(body));
        if (extra) n.column(Column.of("extra", ColumnType.TEXT).length(50).comment("one more"));
        n.primaryKey("note_id");
        Schema s = new Schema();
        s.table(n);
        return s;
    }

    /**
     * The comments of a new table, then a change of them - one changed, one removed, one added
     * where there was none, and a new column's - as the database's own catalog reads them.
     */
    static void comments(Connection c, Database db) throws Exception {
        Schema first = commented("the note's title", "a memo", null, false);
        execute(c, SchemaWriter.body(first, db));
        assertEquals("the note's title", comment(c, db, "mig_note", "title"));
        assertEquals("a memo", comment(c, db, "mig_note", "memo"));
        assertEquals(null, comment(c, db, "mig_note", "body"));

        Schema second = commented("the title", null, "the body", true);
        List<SchemaDiff.Change> changes = SchemaDiff.between(first, second);
        for (SchemaDiff.Change ch : changes) assertTrue(ch.safe, ch.description + " should run as written");
        execute(c, SchemaDiff.render(changes, db, 0, "test"));
        assertEquals("the title", comment(c, db, "mig_note", "title"));
        assertEquals(null, comment(c, db, "mig_note", "memo"));
        assertEquals("the body", comment(c, db, "mig_note", "body"));
        assertEquals("one more", comment(c, db, "mig_note", "extra"));

        // Comments off, a change, and on again: the state holds none, the database still does. Each is
        // said as new, and runs whatever the database holds.
        Schema forgotten = commented(null, null, null, true);
        execute(c, SchemaDiff.render(SchemaDiff.between(forgotten, commented("again", null, "the body", true)), db, 0, "on"));
        assertEquals("again", comment(c, db, "mig_note", "title"));
        assertEquals("the body", comment(c, db, "mig_note", "body"));
        // A state that says memo has a comment the database does not: removing it runs all the same.
        Schema stale = commented("again", "a memo", "the body", true);
        execute(c, SchemaDiff.render(SchemaDiff.between(stale, commented("again", null, "the body", true)), db, 0, "drop"));
        assertEquals(null, comment(c, db, "mig_note", "memo"));
    }

    /** Two boolean columns, one required, one indexed, for an Oracle below 23 (0) or from it. */
    static Schema flags(int oracleVersion) {
        Table t = new Table("mig_flag", "test");
        t.column(Column.of("flag_id", ColumnType.BIGINT).nullable(false));
        t.column(Column.of("active", ColumnType.BOOLEAN));
        t.column(Column.of("shown", ColumnType.BOOLEAN).nullable(false));
        t.primaryKey("flag_id");
        t.index(new Table.Index("ix_mig_flag_active", Collections.singletonList("active"), false));
        Schema s = new Schema().oracleVersion(oracleVersion);
        s.table(t);
        return s;
    }

    /**
     * ddl { oracleVersion = 23 } turned on over Oracle tables made below it, with rows: the change
     * is Oracle's alone and waits for review; run as written - uncommented - it makes BOOLEAN columns
     * of the NUMBER(1) ones, which Oracle alters only when empty, and keeps their values, their
     * nullability and their index.
     */
    static void oracleBooleans(Connection c) throws Exception {
        execute(c, SchemaWriter.body(flags(0), Database.ORACLE));
        try (Statement st = c.createStatement()) {
            st.execute("INSERT INTO mig_flag (flag_id, active, shown) VALUES (1, 1, 1)");
            st.execute("INSERT INTO mig_flag (flag_id, active, shown) VALUES (2, 0, 0)");
            st.execute("INSERT INTO mig_flag (flag_id, active, shown) VALUES (3, NULL, 1)");
        }
        List<SchemaDiff.Change> changes = SchemaDiff.between(flags(0), flags(23));
        assertEquals(2, changes.size(), changes.toString());
        for (SchemaDiff.Change ch : changes) {
            assertFalse(ch.safeOn(Database.ORACLE), ch.description + " should wait for review");
            assertTrue(SchemaDiff.on(changes, Database.H2).isEmpty(), ch.description + " is for Oracle only");
            try (Statement st = c.createStatement()) {
                for (String s : ch.sql(Database.ORACLE)) st.execute(s);
            }
        }
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT column_name, data_type, nullable FROM user_tab_columns"
                     + " WHERE table_name = 'MIG_FLAG' AND column_name IN ('ACTIVE', 'SHOWN') ORDER BY column_name")) {
            assertTrue(rs.next());
            assertEquals("ACTIVE BOOLEAN Y", rs.getString(1) + " " + rs.getString(2) + " " + rs.getString(3));
            assertTrue(rs.next());
            assertEquals("SHOWN BOOLEAN N", rs.getString(1) + " " + rs.getString(2) + " " + rs.getString(3));
        }
        StringBuilder rows = new StringBuilder();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT flag_id, active, shown FROM mig_flag ORDER BY flag_id")) {
            while (rs.next()) {
                long id = rs.getLong(1);
                boolean active = rs.getBoolean(2);
                String said = rs.wasNull() ? "null" : String.valueOf(active);
                rows.append(id).append(' ').append(said).append(' ').append(rs.getBoolean(3)).append("; ");
            }
        }
        assertEquals("1 true true; 2 false false; 3 null true; ", rows.toString());
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM user_ind_columns"
                     + " WHERE index_name = 'IX_MIG_FLAG_ACTIVE' AND column_name = 'ACTIVE'")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "the index on active was made again");
        }
    }

    /** A column's comment as the database keeps it, or null for none (Oracle keeps '' as none). */
    private static String comment(Connection c, Database db, String table, String column) throws Exception {
        String sql;
        switch (db) {
            case POSTGRESQL:
                sql = "SELECT d.description FROM pg_description d JOIN pg_class t ON t.oid = d.objoid"
                        + " JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.objsubid"
                        + " WHERE t.relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = current_schema())"
                        + " AND t.relname = ? AND a.attname = ?";
                break;
            case ORACLE:
                sql = "SELECT comments FROM user_col_comments WHERE table_name = UPPER(?) AND column_name = UPPER(?)";
                break;
            case SQLSERVER:
                sql = "SELECT CAST(p.value AS NVARCHAR(4000)) FROM sys.extended_properties p"
                        + " JOIN sys.columns col ON col.object_id = p.major_id AND col.column_id = p.minor_id"
                        + " WHERE p.name = 'MS_Description' AND OBJECT_NAME(p.major_id) = ? AND col.name = ?";
                break;
            default:
                sql = "SELECT remarks FROM information_schema.columns WHERE table_name = UPPER(?) AND column_name = UPPER(?)";
        }
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                String text = rs.next() ? rs.getString(1) : null;
                return text == null || text.isEmpty() ? null : text;
            }
        }
    }

    private static int size(DatabaseMetaData md, Database db, String table, String column) throws Exception {
        for (String t : Arrays.asList(table, table.toUpperCase(Locale.ROOT))) {
            for (String col : Arrays.asList(column, column.toUpperCase(Locale.ROOT))) {
                try (ResultSet rs = md.getColumns(null, null, t, col)) {
                    if (rs.next()) return rs.getInt("COLUMN_SIZE");
                }
            }
        }
        throw new AssertionError(db + ": no column " + table + "." + column);
    }

    /** The script's statements, each ending in a semicolon; comment lines are not run. */
    static void execute(Connection c, String script) throws Exception {
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
}
