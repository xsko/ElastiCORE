package io.elasticore.base.model.pub.rules.schema;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a change of one column becomes: run at once when it only widens, waiting for review when it can lose data. */
public class SchemaDiffTest {

    private static Schema of(Column... columns) {
        Table t = new Table("item", "Item");
        t.column(Column.of("item_id", ColumnType.BIGINT).nullable(false));
        for (Column c : columns) t.column(c);
        t.primaryKey("item_id");
        Schema s = new Schema();
        s.table(t);
        return s;
    }

    private static SchemaDiff.Change only(Schema before, Schema after) {
        List<SchemaDiff.Change> changes = SchemaDiff.between(before, after);
        assertEquals(1, changes.size(), changes.toString());
        return changes.get(0);
    }

    @Test
    public void whatANameTakenOverChangesIsSaidAndWaitsWhereItMatters() {
        // An index and a key the state took over; the column they are on changes.
        Schema before = of(Column.of("code", ColumnType.TEXT).length(10), Column.of("owner_id", ColumnType.BIGINT));
        before.table("item").index(new Table.Index("ix_item_code", Collections.singletonList("code"), false));
        before.table("item")
                .foreignKey(new Table.ForeignKey(
                        "fk_item_owner",
                        Collections.singletonList("owner_id"),
                        "owner",
                        Collections.singletonList("owner_id")));
        before.takeOverKeysAndIndexes();
        before.databases().add(Database.SQLSERVER.id);

        // Dropped by a name taken over: waits everywhere, and says why.
        Schema without = of(Column.of("code", ColumnType.TEXT).length(10), Column.of("owner_id", ColumnType.BIGINT));
        for (SchemaDiff.Change c : SchemaDiff.between(before, without)) {
            assertFalse(c.safe, c.description);
            assertTrue(c.description.contains("-PddlBaseline"), c.description);
        }
        // The same drops of names not taken over: as written, and nothing said of a baseline.
        Schema plain = of(Column.of("code", ColumnType.TEXT).length(10), Column.of("owner_id", ColumnType.BIGINT));
        plain.table("item").index(new Table.Index("ix_item_code", Collections.singletonList("code"), false));
        plain.table("item")
                .foreignKey(new Table.ForeignKey(
                        "fk_item_owner",
                        Collections.singletonList("owner_id"),
                        "owner",
                        Collections.singletonList("owner_id")));
        for (SchemaDiff.Change c : SchemaDiff.between(plain, without)) {
            assertTrue(c.safe, c.description);
            assertFalse(c.description.contains("-PddlBaseline"), c.description);
        }
        // Made again by such a name, with other columns: the drop and the making both wait, and say so.
        Schema moved = of(Column.of("code", ColumnType.TEXT).length(10), Column.of("owner_id", ColumnType.BIGINT));
        moved.table("item").index(new Table.Index("ix_item_code", java.util.Arrays.asList("code", "owner_id"), false));
        moved.table("item")
                .foreignKey(new Table.ForeignKey(
                        "fk_item_owner",
                        java.util.Arrays.asList("owner_id", "code"),
                        "owner",
                        java.util.Arrays.asList("owner_id", "code")));
        List<SchemaDiff.Change> again = SchemaDiff.between(before, moved);
        assertEquals(4, again.size(), again.toString());
        for (SchemaDiff.Change c : again) {
            assertFalse(c.safe, c.description);
            assertTrue(c.description.contains("-PddlBaseline"), c.description);
        }
        for (SchemaDiff.Change c : SchemaDiff.between(plain, moved)) {
            assertTrue(c.safe, c.description);
            assertFalse(c.description.contains("-PddlBaseline"), c.description);
        }

        // The key's column may now be empty... it was already; made required, around a key taken over:
        // a review everywhere (rows may be empty), named; may be empty again: SQL Server alone waits.
        Schema required = of(
                Column.of("code", ColumnType.TEXT).length(10),
                Column.of("owner_id", ColumnType.BIGINT).nullable(false));
        required.table("item").index(new Table.Index("ix_item_code", Collections.singletonList("code"), false));
        required.table("item")
                .foreignKey(new Table.ForeignKey(
                        "fk_item_owner",
                        Collections.singletonList("owner_id"),
                        "owner",
                        Collections.singletonList("owner_id")));
        SchemaDiff.Change closing = only(before, required);
        assertFalse(closing.safe, closing.description);
        assertTrue(
                closing.description.contains("is now required") && closing.description.contains("fk_item_owner"),
                closing.description);
        required.takeOverKeysAndIndexes();
        required.databases().add(Database.SQLSERVER.id);
        Schema opened = of(Column.of("code", ColumnType.TEXT).length(10), Column.of("owner_id", ColumnType.BIGINT));
        opened.table("item").index(new Table.Index("ix_item_code", Collections.singletonList("code"), false));
        opened.table("item")
                .foreignKey(new Table.ForeignKey(
                        "fk_item_owner",
                        Collections.singletonList("owner_id"),
                        "owner",
                        Collections.singletonList("owner_id")));
        SchemaDiff.Change opening = only(required, opened);
        assertTrue(
                opening.description.contains("may now be empty") && opening.description.contains("fk_item_owner"),
                opening.description);
        assertTrue(opening.safeOn(Database.POSTGRESQL), opening.description);
        assertFalse(opening.safeOn(Database.SQLSERVER), opening.description);
        // With no name taken over, SQL Server's history runs it too.
        SchemaDiff.Change free = only(plain, required);
        assertFalse(free.description.contains("-PddlBaseline"), free.description);
        Schema plainRequired = of(
                Column.of("code", ColumnType.TEXT).length(10),
                Column.of("owner_id", ColumnType.BIGINT).nullable(false));
        plainRequired.databases().add(Database.SQLSERVER.id);
        Schema plainOpened =
                of(Column.of("code", ColumnType.TEXT).length(10), Column.of("owner_id", ColumnType.BIGINT));
        assertTrue(only(plainRequired, plainOpened).safeOn(Database.SQLSERVER));
    }

    @Test
    public void aNameTakenOverWaitsOnlyOnTheDatabasesItWasTakenOverFrom() {
        // Taken over from H2's history; PostgreSQL and SQL Server came after, by the model's names.
        Schema before = of(Column.of("code", ColumnType.TEXT).length(10));
        before.table("item").index(new Table.Index("ix_item_code", Collections.singletonList("code"), false));
        before.takeOverKeysAndIndexes(Collections.singletonList(Database.H2.id));
        before.databases().add(Database.H2.id);
        before.databases().add(Database.POSTGRESQL.id);
        before.databases().add(Database.SQLSERVER.id);
        Schema read = Schema.fromJson(before.toJson());
        assertEquals(before.takenOverOn(), read.takenOverOn());

        // The index made again with another column: dropped and made by the name - waits on H2 alone.
        Schema moved = of(Column.of("code", ColumnType.TEXT).length(10));
        moved.table("item").index(new Table.Index("ix_item_code", java.util.Arrays.asList("code", "item_id"), false));
        List<SchemaDiff.Change> changes = SchemaDiff.between(read, moved);
        assertEquals(2, changes.size(), changes.toString());
        for (SchemaDiff.Change c : changes) {
            // Said in every database's file: where it holds.
            assertTrue(c.description.contains(" - on h2, its name was taken over with -PddlBaseline"), c.description);
            assertFalse(c.safeOn(Database.H2), c.description);
            assertTrue(c.safeOn(Database.POSTGRESQL) && c.safeOn(Database.SQLSERVER), c.description);
        }
        // Widening the column: SQL Server's drop-and-make-again is by its own name there - nothing waits.
        Schema wider = of(Column.of("code", ColumnType.TEXT).length(20));
        wider.table("item").index(new Table.Index("ix_item_code", Collections.singletonList("code"), false));
        SchemaDiff.Change widened = only(read, wider);
        assertTrue(widened.safeOn(Database.SQLSERVER), widened.description);
        assertFalse(widened.description.contains("-PddlBaseline"), widened.description);

        // A name kept by the next state stays taken over from H2 alone.
        Schema next = of(Column.of("code", ColumnType.TEXT).length(10));
        next.table("item").index(new Table.Index("ix_item_code", Collections.singletonList("code"), false));
        next.keepTakenOver(read);
        assertEquals(Collections.singleton(Database.H2.id), next.takenOverOn());
        Schema gone = of(Column.of("code", ColumnType.TEXT).length(10));
        gone.keepTakenOver(read);
        assertTrue(gone.takenOverOn().isEmpty() && !gone.toJson().contains("takenOver"), gone.toJson());
    }

    @Test
    public void aColumnChangeSaysWhatItIs() {
        SchemaDiff.Change narrows = only(
                of(Column.of("name", ColumnType.TEXT).length(40)),
                of(Column.of("name", ColumnType.TEXT).length(20)));
        assertFalse(narrows.safe);
        assertTrue(narrows.description.contains("narrows or changes the type"), narrows.description);
        SchemaDiff.Change widens = only(
                of(Column.of("name", ColumnType.TEXT).length(20)),
                of(Column.of("name", ColumnType.TEXT).length(40)));
        assertTrue(widens.safe);
        assertFalse(widens.description.contains(" - "), widens.description);
        // A column of the key: by hand, whether it widens or not.
        Table wider = new Table("item", "Item");
        wider.column(Column.of("item_id", ColumnType.DECIMAL).decimal(20, 0).nullable(false));
        wider.primaryKey("item_id");
        Schema keyAfter = new Schema();
        keyAfter.table(wider);
        SchemaDiff.Change key = only(of(), keyAfter);
        assertFalse(key.safe);
        assertTrue(key.description.contains("a column of the key"), key.description);
    }

    @Test
    public void aLongerTextOrMoreDigitsRunAtOnce() {
        assertTrue(only(of(Column.of("name", ColumnType.TEXT).length(40)),
                of(Column.of("name", ColumnType.TEXT).length(80))).safe);
        assertTrue(only(of(Column.of("price", ColumnType.DECIMAL).decimal(10, 2)),
                of(Column.of("price", ColumnType.DECIMAL).decimal(14, 2))).safe);
    }

    @Test
    public void aDecimalThatSaidNoneNarrowsWhenItSaysLess() {
        // No precision is DECIMAL(38,2): a declared (12,2) is narrower.
        SchemaDiff.Change c = only(of(Column.of("price", ColumnType.DECIMAL)),
                of(Column.of("price", ColumnType.DECIMAL).decimal(12, 2)));
        assertFalse(c.safe, c.description);
        // And fewer digits after the point, with as many in all, is narrower too.
        assertFalse(only(of(Column.of("rate", ColumnType.DECIMAL).decimal(12, 4)),
                of(Column.of("rate", ColumnType.DECIMAL).decimal(12, 2))).safe);
    }

    @Test
    public void aTextGrowingPastOraclesInlineLimitIsATypeChange() {
        // VARCHAR2(4000 CHAR) -> CLOB: no ALTER makes one of the other.
        SchemaDiff.Change c = only(of(Column.of("memo", ColumnType.TEXT).length(2000)),
                of(Column.of("memo", ColumnType.TEXT).length(8000)));
        assertFalse(c.safe, c.description);
    }

    @Test
    public void sqlServerKeepsTheColumnsNullabilityAndItsIndexes() {
        Schema before = of(Column.of("code", ColumnType.TEXT).length(10).nullable(false));
        before.table("item").index(new Table.Index("uk_item_code", Collections.singletonList("code"), true));
        Schema after = of(Column.of("code", ColumnType.TEXT).length(20).nullable(false));
        after.table("item").index(new Table.Index("uk_item_code", Collections.singletonList("code"), true));
        SchemaDiff.Change c = only(before, after);
        assertTrue(c.safe, c.description);
        List<String> sql = c.sql(Database.SQLSERVER);
        assertEquals("DROP INDEX uk_item_code ON item", sql.get(0));
        assertEquals("ALTER TABLE item ALTER COLUMN code NVARCHAR(20) NOT NULL", sql.get(1));
        assertTrue(sql.get(2).startsWith("CREATE UNIQUE INDEX uk_item_code ON item (code)"), sql.get(2));
        // A column that becomes required: its type change keeps it nullable; the NOT NULL waits for review.
        List<SchemaDiff.Change> both = SchemaDiff.between(of(Column.of("note", ColumnType.TEXT).length(10)),
                of(Column.of("note", ColumnType.TEXT).length(20).nullable(false)));
        assertEquals(2, both.size(), both.toString());
        assertEquals("ALTER TABLE item ALTER COLUMN note NVARCHAR(20) NULL", both.get(0).sql(Database.SQLSERVER).get(0));
        assertTrue(both.get(0).safe);
        assertFalse(both.get(1).safe);
    }

    @Test
    public void anIndexByANameTakenOverIsNotDroppedAndMadeAgainAsWritten() {
        // A history of the project's own made the index, and the state took its name over (-PddlBaseline):
        // the database may hold it by another name, or as a constraint.
        Schema before = of(Column.of("code", ColumnType.TEXT).length(10).nullable(false));
        before.table("item").index(new Table.Index("uk_item_code", Collections.singletonList("code"), true));
        before.takeOverKeysAndIndexes();
        Schema after = of(Column.of("code", ColumnType.TEXT).length(20).nullable(false));
        after.table("item").index(new Table.Index("uk_item_code", Collections.singletonList("code"), true));

        // Widening the column runs at once where no history is for SQL Server - no index is touched there.
        assertTrue(only(before, after).safe, only(before, after).description);
        // On SQL Server the widening drops the index and makes it again: it waits there, and only there -
        // PostgreSQL in the same history widens the column at once.
        before.databases().add(Database.POSTGRESQL.id);
        before.databases().add(Database.SQLSERVER.id);
        SchemaDiff.Change around = only(before, after);
        assertFalse(around.safeOn(Database.SQLSERVER));
        assertTrue(around.safeOn(Database.POSTGRESQL));
        assertTrue(
                around.description.contains("uk_item_code") && around.description.contains("-PddlBaseline"),
                around.description);
        List<SchemaDiff.Change> one = Collections.singletonList(around);
        assertTrue(
                SchemaDiff.render(one, Database.SQLSERVER, 0, "t").contains("-- ALTER TABLE"),
                SchemaDiff.render(one, Database.SQLSERVER, 0, "t"));
        assertTrue(
                SchemaDiff.render(one, Database.POSTGRESQL, 0, "t").contains("\nALTER TABLE"),
                SchemaDiff.render(one, Database.POSTGRESQL, 0, "t"));

        // The index itself changes: dropped and made again by that name, both wait.
        Schema moved = of(Column.of("code", ColumnType.TEXT).length(10).nullable(false));
        moved.table("item").index(new Table.Index("uk_item_code", java.util.Arrays.asList("code", "item_id"), true));
        List<SchemaDiff.Change> changes = SchemaDiff.between(before, moved);
        assertEquals(2, changes.size(), changes.toString());
        for (SchemaDiff.Change c : changes) assertFalse(c.safe, c.description);
        // Without a name taken over, the same change runs as written.
        Schema plain = of(Column.of("code", ColumnType.TEXT).length(10).nullable(false));
        plain.table("item").index(new Table.Index("uk_item_code", Collections.singletonList("code"), true));
        for (SchemaDiff.Change c : SchemaDiff.between(plain, moved)) assertTrue(c.safe, c.description);
    }

    private static Schema coverage(String table) {
        return coverage(table, true);
    }

    private static Schema coverage(String table, boolean indexed) {
        Table t = new Table(table, "Coverage");
        t.column(Column.of("coverage_id", ColumnType.BIGINT).nullable(false));
        t.column(Column.of("coverage_code", ColumnType.TEXT).length(30).nullable(false));
        t.primaryKey("coverage_id");
        if (indexed)
            t.index(new Table.Index("uk_coverage_product_code", Collections.singletonList("coverage_code"), true));
        Schema s = new Schema();
        s.table(t);
        return s;
    }

    /**
     * Issue 329: the table renamed (a table.prefix added), the old one kept for its rows. The new table's index takes
     * the old one's name, which PostgreSQL, Oracle and H2 hold once in a schema: the old index goes first, and the
     * old table still waits for review.
     */
    @Test
    public void anIndexNameAnotherTableTakesIsDroppedFirst() {
        Schema before = coverage("coverage");
        Schema after = coverage("jp_coverage");
        List<SchemaDiff.Change> changes = SchemaDiff.between(before, after);
        String pg = SchemaDiff.render(changes, Database.POSTGRESQL, 19, "rename");
        int drop = pg.indexOf("DROP INDEX uk_coverage_product_code");
        int create = pg.indexOf("CREATE UNIQUE INDEX uk_coverage_product_code ON jp_coverage");
        assertTrue(drop >= 0 && create > drop, pg);
        // Run as written: it drops an index, not rows - the old table's drop still waits.
        assertTrue(pg.indexOf("-- DROP TABLE coverage") > create, pg);
        int drops = 0;
        for (SchemaDiff.Change c : changes) {
            if (!c.description.startsWith("drop index")) continue;
            drops++;
            assertTrue(c.safe, c.description);
            assertEquals("drop index uk_coverage_product_code of coverage - jp_coverage takes its name", c.description);
        }
        assertEquals(1, drops, changes.toString());
        assertTrue(SchemaDiff.render(changes, Database.SQLSERVER, 19, "rename")
                .contains("DROP INDEX uk_coverage_product_code ON coverage"));
    }

    /** An index moved from one kept table to another: dropped once, before it is made on the other. */
    @Test
    public void anIndexMovedBetweenTablesIsDroppedOnce() {
        Schema before = coverage("coverage");
        Table other = new Table("coverage_line", "CoverageLine");
        other.column(Column.of("line_id", ColumnType.BIGINT).nullable(false));
        other.column(Column.of("coverage_code", ColumnType.TEXT).length(30).nullable(false));
        other.primaryKey("line_id");
        before.table(other);
        Schema after = coverage("coverage", false);
        Table moved = new Table("coverage_line", "CoverageLine");
        moved.column(Column.of("line_id", ColumnType.BIGINT).nullable(false));
        moved.column(Column.of("coverage_code", ColumnType.TEXT).length(30).nullable(false));
        moved.primaryKey("line_id");
        moved.index(new Table.Index("UK_COVERAGE_PRODUCT_CODE", Collections.singletonList("coverage_code"), true));
        after.table(moved);
        String pg = SchemaDiff.render(SchemaDiff.between(before, after), Database.POSTGRESQL, 19, "move");
        int drop = pg.indexOf("DROP INDEX uk_coverage_product_code");
        assertTrue(drop >= 0 && pg.indexOf("CREATE UNIQUE INDEX UK_COVERAGE_PRODUCT_CODE ON coverage_line") > drop, pg);
        assertEquals(drop, pg.lastIndexOf("DROP INDEX"), pg);
    }

    /** A name the old table keeps in the model is not taken: nothing is dropped. */
    @Test
    public void aNameStillHeldIsNotDropped() {
        Schema before = coverage("coverage");
        Schema after = coverage("coverage");
        Table copy = new Table("jp_coverage", "Coverage");
        copy.column(Column.of("coverage_id", ColumnType.BIGINT).nullable(false));
        copy.primaryKey("coverage_id");
        copy.index(new Table.Index("uk_coverage_product_code", Collections.singletonList("coverage_id"), true));
        after.table(copy);
        for (SchemaDiff.Change c : SchemaDiff.between(before, after)) {
            assertFalse(c.description.startsWith("drop index"), c.description);
        }
    }
}
