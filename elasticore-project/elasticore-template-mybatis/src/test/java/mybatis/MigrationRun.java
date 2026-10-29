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
        execute(c, SchemaDiff.render(changes, db, "test"));

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
