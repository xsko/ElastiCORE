package io.elasticore.base.model.pub.rules.schema;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ddl { oracleVersion }: a boolean is a BOOLEAN column on Oracle from 23, as Hibernate's
 * OracleDialect maps it from that version, and a NUMBER(1) below it and when unset. Nothing
 * else changes, and the state says which the tables were built for, so turning it on is a
 * migration of Oracle's boolean columns alone.
 */
public class OracleVersionTest {

    private static final Column TERMINAL = Column.of("terminal", ColumnType.BOOLEAN);

    @Test
    public void aBooleanIsBooleanOnOracleFrom23AndNumber1BelowAndWhenUnset() {
        assertEquals("NUMBER(1)", Database.ORACLE.type(TERMINAL));
        assertEquals("NUMBER(1)", Database.ORACLE.type(TERMINAL, 0));
        assertEquals("NUMBER(1)", Database.ORACLE.type(TERMINAL, 19));
        assertEquals("NUMBER(1)", Database.ORACLE.type(TERMINAL, 22));
        assertEquals("BOOLEAN", Database.ORACLE.type(TERMINAL, 23));
        assertEquals("BOOLEAN", Database.ORACLE.type(TERMINAL, 26));
        // The other databases hold a boolean as they did.
        assertEquals("BOOLEAN", Database.H2.type(TERMINAL, 23));
        assertEquals("BOOLEAN", Database.POSTGRESQL.type(TERMINAL, 23));
        assertEquals("BIT", Database.SQLSERVER.type(TERMINAL, 23));
        // Only the boolean: every other type is the same for 23 as for an Oracle unsaid.
        for (ColumnType type : ColumnType.values()) {
            if (type == ColumnType.BOOLEAN) continue;
            Column c = Column.of("c", type);
            for (Database db : Database.values()) assertEquals(db.type(c, 0), db.type(c, 23), db + " " + type);
        }
    }

    @Test
    public void theDdlSaysWhichOracleItIsFor() {
        assertEquals("Oracle 23 or later", Database.ORACLE.title(23));
        assertEquals("Oracle 23 or later", Database.ORACLE.title(26));
        assertEquals(Database.ORACLE.title, Database.ORACLE.title(22));
        assertEquals(Database.ORACLE.title, Database.ORACLE.title(0));
        assertEquals(Database.H2.title, Database.H2.title(23));

        Schema s = new Schema();
        Table t = new Table("status_code", "StatusCode");
        t.column(Column.of("code", ColumnType.TEXT).length(10).nullable(false));
        t.column(TERMINAL);
        t.primaryKey("code");
        s.table(t);
        assertTrue(SchemaWriter.body(s, Database.ORACLE).contains("    terminal NUMBER(1),\n"));
        s.oracleVersion(23);
        assertTrue(SchemaWriter.body(s, Database.ORACLE).contains("    terminal BOOLEAN,\n"));
        assertTrue(SchemaWriter.body(s, Database.SQLSERVER).contains("    terminal BIT,\n"));
    }

    @Test
    public void theSettingIsAMajorVersionFrom12() {
        assertEquals(0, DdlConfig.oracleVersion(null));
        assertEquals(0, DdlConfig.oracleVersion(" "));
        assertEquals(23, DdlConfig.oracleVersion("23"));
        assertEquals(19, DdlConfig.oracleVersion(" 19 "));
        assertEquals(12, DdlConfig.oracleVersion("12"));
        for (String wrong : new String[] {"11", "0", "-1", "23ai", "23.4", "19c", "12345"}) {
            IllegalArgumentException e =
                    assertThrows(IllegalArgumentException.class, () -> DdlConfig.oracleVersion(wrong), wrong);
            assertTrue(e.getMessage().contains("oracleVersion = '" + wrong + "'"), e.getMessage());
        }
    }

    @Test
    public void theVersionIsTheProjectsForEveryDomain() {
        String[] keys = {DdlConfig.DATABASES, DdlConfig.ORACLE_VERSION};
        String[] saved = {System.getProperty(keys[0]), System.getProperty(keys[1])};
        try {
            System.clearProperty(DdlConfig.DATABASES);
            System.clearProperty(DdlConfig.ORACLE_VERSION);
            assertSame(DdlConfig.OFF, DdlConfig.global());

            System.setProperty(DdlConfig.DATABASES, "h2,oracle");
            assertEquals(0, DdlConfig.global().oracleVersion, "unset: below 23");
            System.setProperty(DdlConfig.ORACLE_VERSION, "23");
            DdlConfig project = DdlConfig.global();
            assertEquals(23, project.oracleVersion);
            Map<String, String> env = new HashMap<String, String>();
            env.put("ddl.databases", "oracle");
            env.put("ddl.separate", "true");
            assertEquals(23, DdlConfig.forDomain(project, env::get).oracleVersion, "a domain of its own too");
            env.put("ddl", "false");
            assertSame(DdlConfig.OFF, DdlConfig.forDomain(project, env::get));

            // No databases of the project's: off, still with the Oracle a domain that switches its DDL on is for.
            System.clearProperty(DdlConfig.DATABASES);
            DdlConfig off = DdlConfig.global();
            assertFalse(off.enabled());
            env.clear();
            env.put("ddl.databases", "oracle");
            assertEquals(23, DdlConfig.forDomain(off, env::get).oracleVersion);
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
    }

    @Test
    public void theStateSaysTheVersionOnlyWhenSaid() {
        Schema s = new Schema();
        assertFalse(s.toJson().contains("oracleVersion"), "a state of a history without it reads as it did");
        assertEquals(0, Schema.fromJson(s.toJson()).oracleVersion());
        s.oracleVersion(23);
        assertTrue(s.toJson().contains("\"oracleVersion\" : 23"), s.toJson());
        assertEquals(23, Schema.fromJson(s.toJson()).oracleVersion());
    }

    /** A table with a boolean column, indexed, with a comment, as the state or the model has it. */
    private static Schema items(int oracleVersion, Column terminal) {
        Table t = new Table("item", "Item").comments(true);
        t.column(Column.of("item_id", ColumnType.BIGINT).nullable(false));
        t.column(terminal);
        t.column(Column.of("code", ColumnType.TEXT).length(10));
        t.primaryKey("item_id");
        t.index(new Table.Index("ix_item_terminal", Collections.singletonList("terminal"), false));
        t.index(new Table.Index("ix_item_code_terminal", Arrays.asList("code", "terminal"), true));
        t.index(new Table.Index("ix_item_code", Collections.singletonList("code"), false));
        Schema s = new Schema().oracleVersion(oracleVersion);
        s.table(t);
        return s;
    }

    @Test
    public void turningItOnRetypesOraclesBooleanColumnsAloneByACopy() {
        Column terminal = TERMINAL.comment("Ended");
        List<SchemaDiff.Change> changes = SchemaDiff.between(items(0, terminal), items(23, terminal));
        assertEquals(1, changes.size(), changes.toString());
        SchemaDiff.Change c = changes.get(0);
        assertEquals(
                "item.terminal: NUMBER(1) -> BOOLEAN, the DDL being for Oracle 23 or later now - on Oracle only; its values"
                        + " are copied into a new column, which takes the old one's place",
                c.description);
        // Oracle changes the type of an empty column only (ORA-01439): on rows, a copy - for review.
        assertTrue(c.on(Database.ORACLE));
        assertFalse(c.safeOn(Database.ORACLE));
        assertEquals(
                Arrays.asList(
                        "ALTER TABLE item ADD (terminal_new BOOLEAN)",
                        "UPDATE item SET terminal_new = terminal",
                        "ALTER TABLE item DROP COLUMN terminal",
                        "ALTER TABLE item RENAME COLUMN terminal_new TO terminal",
                        "CREATE INDEX ix_item_terminal ON item (terminal)",
                        "CREATE UNIQUE INDEX ix_item_code_terminal ON item (code, terminal)",
                        "COMMENT ON COLUMN item.terminal IS 'Ended'"),
                c.sql(Database.ORACLE));
        // Not the other databases': they hold a boolean as they did, and their migrations do not have it.
        for (Database db : new Database[] {Database.H2, Database.POSTGRESQL, Database.SQLSERVER}) {
            assertFalse(c.on(db), db.id);
            assertTrue(c.sql(db).isEmpty(), db.id);
            assertTrue(SchemaDiff.on(changes, db).isEmpty(), db.id);
        }
        assertEquals(changes, SchemaDiff.on(changes, Database.ORACLE));
        String oracle = SchemaDiff.render(changes, Database.ORACLE, 23, "The change");
        assertTrue(oracle.startsWith("-- The change, for Oracle 23 or later.\n"), oracle);
        assertTrue(oracle.contains("-- ALTER TABLE item ADD (terminal_new BOOLEAN);"), oracle);

        // Nothing when the version stays, or changes without changing the column.
        assertTrue(SchemaDiff.between(items(23, terminal), items(23, terminal)).isEmpty());
        assertTrue(SchemaDiff.between(items(19, terminal), items(22, terminal)).isEmpty());
        assertTrue(SchemaDiff.between(items(23, terminal), items(26, terminal)).isEmpty());
    }

    @Test
    public void turningItOffRetypesThemBackAndKeepsWhatTheColumnWas() {
        // Required, without a comment, beside a column already called terminal_new.
        Column required = TERMINAL.nullable(false);
        Schema before = items(23, required);
        before.table("item").column(Column.of("terminal_new", ColumnType.INTEGER));
        Schema after = items(0, required);
        after.table("item").column(Column.of("terminal_new", ColumnType.INTEGER));
        after.table("item").comments(false);
        List<SchemaDiff.Change> changes = SchemaDiff.between(before, after);
        assertEquals(1, changes.size(), changes.toString());
        assertTrue(
                changes.get(0)
                        .description
                        .startsWith("item.terminal: BOOLEAN -> NUMBER(1), the DDL being for " + Database.ORACLE.title
                                + " now"),
                changes.get(0).description);
        assertEquals(
                Arrays.asList(
                        "ALTER TABLE item ADD (terminal_new_ NUMBER(1))",
                        "UPDATE item SET terminal_new_ = terminal",
                        "ALTER TABLE item DROP COLUMN terminal",
                        "ALTER TABLE item RENAME COLUMN terminal_new_ TO terminal",
                        "ALTER TABLE item MODIFY (terminal NOT NULL)",
                        "CREATE INDEX ix_item_terminal ON item (terminal)",
                        "CREATE UNIQUE INDEX ix_item_code_terminal ON item (code, terminal)"),
                changes.get(0).sql(Database.ORACLE));
    }

    @Test
    public void anIndexTheModelDropsIsNotMadeAgainAndACommentOnlyWithCommentsOn() {
        Schema before = items(0, TERMINAL.comment("Ended"));
        before.table("item").comments(false);
        Schema after = items(23, TERMINAL.comment("Ended"));
        after.table("item").comments(false);
        Table without = new Table("item", "Item");
        for (Column c : after.table("item").columns()) without.column(c);
        without.primaryKey("item_id");
        without.index(new Table.Index("ix_item_code", Collections.singletonList("code"), false));
        after.replace(without);
        List<SchemaDiff.Change> changes = SchemaDiff.between(before, after);
        // The two indexes the model no longer has are dropped first, as for any database.
        assertEquals(3, changes.size(), changes.toString());
        assertTrue(changes.get(0).description.startsWith("drop index ix_item_terminal"));
        assertTrue(changes.get(1).description.startsWith("drop index ix_item_code_terminal"));
        assertEquals(
                Arrays.asList(
                        "ALTER TABLE item ADD (terminal_new BOOLEAN)",
                        "UPDATE item SET terminal_new = terminal",
                        "ALTER TABLE item DROP COLUMN terminal",
                        "ALTER TABLE item RENAME COLUMN terminal_new TO terminal"),
                changes.get(2).sql(Database.ORACLE));
    }

    @Test
    public void whatTheModelAddsOrRetypesIsForTheVersionItIsNowFor() {
        Schema before = new Schema();
        Table t = new Table("item", "Item");
        t.column(Column.of("item_id", ColumnType.BIGINT).nullable(false));
        t.column(Column.of("grade", ColumnType.INTEGER));
        t.primaryKey("item_id");
        before.table(t);

        Schema after = new Schema().oracleVersion(23);
        Table now = new Table("item", "Item");
        now.column(Column.of("item_id", ColumnType.BIGINT).nullable(false));
        now.column(Column.of("grade", ColumnType.BOOLEAN));
        now.column(Column.of("active", ColumnType.BOOLEAN));
        now.column(Column.of("ended", ColumnType.BOOLEAN).nullable(false));
        now.primaryKey("item_id");
        after.table(now);
        Table added = new Table("tag", "Tag");
        added.column(Column.of("tag_id", ColumnType.BIGINT).nullable(false));
        added.column(Column.of("shown", ColumnType.BOOLEAN));
        added.primaryKey("tag_id");
        after.table(added);

        List<String> oracle = new java.util.ArrayList<String>();
        for (SchemaDiff.Change c : SchemaDiff.between(before, after)) oracle.addAll(c.sql(Database.ORACLE));
        assertTrue(
                oracle.contains("CREATE TABLE tag (\n    tag_id NUMBER(19) NOT NULL,\n    shown BOOLEAN,\n"
                        + "    PRIMARY KEY (tag_id)\n)"),
                oracle.toString());
        assertTrue(oracle.contains("ALTER TABLE item MODIFY (grade BOOLEAN)"), oracle.toString());
        assertTrue(oracle.contains("ALTER TABLE item ADD (active BOOLEAN)"), oracle.toString());
        assertTrue(oracle.contains("ALTER TABLE item ADD (ended BOOLEAN)"), oracle.toString());
        assertFalse(oracle.toString().contains("NUMBER(1)"), oracle.toString());
    }
}
