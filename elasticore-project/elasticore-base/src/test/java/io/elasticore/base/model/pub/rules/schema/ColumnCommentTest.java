package io.elasticore.base.model.pub.rules.schema;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With ddl comments on, a column's label is said to the database as its
 * comment: COMMENT ON COLUMN, or SQL Server's MS_Description property. A
 * comment is not how the column is stored - adding, changing or removing one
 * runs as written - and it is compared, written and kept in the state only
 * for a table whose domain has them on, so a state written without them
 * reads as it did and a schema without them is the bytes it was.
 */
public class ColumnCommentTest {

    private static Schema of(boolean comments, Column... columns) {
        Table t = new Table("item", "Item").comments(comments);
        t.column(Column.of("item_id", ColumnType.BIGINT).nullable(false));
        for (Column c : columns) t.column(c);
        t.primaryKey("item_id");
        Schema s = new Schema();
        s.table(t);
        return s;
    }

    private static Column name(String comment) {
        return Column.of("name", ColumnType.TEXT).length(40).comment(comment);
    }

    @Test
    public void eachDatabaseSaysItInItsOwnWordsWithTheQuoteDoubled() {
        assertEquals(
                "COMMENT ON COLUMN item.name IS 'the item''s name'",
                Database.H2.comment("item", "name", "the item's name", false));
        assertEquals(
                "COMMENT ON COLUMN item.name IS 'the item''s name'",
                Database.POSTGRESQL.comment("item", "name", "the item's name", true));
        assertEquals(
                "COMMENT ON COLUMN item.name IS 'the item''s name'",
                Database.ORACLE.comment("item", "name", "the item's name", false));
        assertEquals(
                "EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'the item''s name',"
                        + " @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'item',"
                        + " @level2type = N'COLUMN', @level2name = N'name'",
                Database.SQLSERVER.comment("item", "name", "the item's name", false));
        // A table in a schema of its own is in that schema on SQL Server, and one that had a comment is updated.
        assertEquals(
                "EXEC sp_updateextendedproperty @name = N'MS_Description', @value = N'x', @level0type = N'SCHEMA',"
                        + " @level0name = N'acct', @level1type = N'TABLE', @level1name = N'ledger', @level2type = N'COLUMN',"
                        + " @level2name = N'memo'",
                Database.SQLSERVER.comment("acct.ledger", "memo", "x", true));
    }

    @Test
    public void aCommentRemovedIsNoneInEachDatabasesWords() {
        assertEquals("COMMENT ON COLUMN item.name IS NULL", Database.H2.dropComment("item", "name"));
        assertEquals("COMMENT ON COLUMN item.name IS NULL", Database.POSTGRESQL.dropComment("item", "name"));
        // Oracle has no NULL there; its empty comment is none.
        assertEquals("COMMENT ON COLUMN item.name IS ''", Database.ORACLE.dropComment("item", "name"));
        assertEquals(
                "EXEC sp_dropextendedproperty @name = N'MS_Description', @level0type = N'SCHEMA',"
                        + " @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'item', @level2type = N'COLUMN',"
                        + " @level2name = N'name'",
                Database.SQLSERVER.dropComment("item", "name"));
    }

    @Test
    public void aNewTableSaysWhatItsColumnsHoldAfterItIsMade() {
        String body = SchemaWriter.body(of(true, name("the item's name")), Database.POSTGRESQL);
        assertTrue(body.contains(");\nCOMMENT ON COLUMN item.name IS 'the item''s name';"), body);
        assertEquals(
                1,
                SchemaWriter.comments(of(true, name("x")).table("item"), Database.H2)
                        .size());

        List<SchemaDiff.Change> changes = SchemaDiff.between(new Schema(), of(true, name("x")));
        assertEquals(1, changes.size(), changes.toString());
        assertEquals(
                "COMMENT ON COLUMN item.name IS 'x'",
                changes.get(0).sql(Database.ORACLE).get(1));
    }

    @Test
    public void withCommentsOffTheSchemaIsTheBytesItWas() {
        Schema plain = of(false, Column.of("name", ColumnType.TEXT).length(40));
        Schema said = of(false, name("the item's name"));
        for (Database db : Database.values()) {
            assertEquals(SchemaWriter.body(plain, db), SchemaWriter.body(said, db), db.id);
        }
        // Nor is a comment the state holds compared: off, it is not removed, only no longer kept.
        assertTrue(
                SchemaDiff.between(of(true, name("x")), of(false, name(null))).isEmpty());
    }

    @Test
    public void aCommentAddedChangedOrRemovedRunsAsWritten() {
        // A state written before comments were on has none: each column with a label gets one, added.
        SchemaDiff.Change added = only(of(false, name(null)), of(true, name("name")));
        assertTrue(added.safe);
        assertEquals("comment of item.name", added.description);
        assertTrue(added.sql(Database.SQLSERVER).get(0).startsWith("EXEC sp_addextendedproperty"));

        SchemaDiff.Change changed = only(of(true, name("name")), of(true, name("display name")));
        assertTrue(changed.safe);
        assertEquals(
                "COMMENT ON COLUMN item.name IS 'display name'",
                changed.sql(Database.H2).get(0));
        assertTrue(changed.sql(Database.SQLSERVER).get(0).startsWith("EXEC sp_updateextendedproperty"));

        SchemaDiff.Change removed = only(of(true, name("name")), of(true, name(null)));
        assertTrue(removed.safe);
        assertEquals(
                "COMMENT ON COLUMN item.name IS ''",
                removed.sql(Database.ORACLE).get(0));
        assertTrue(removed.sql(Database.SQLSERVER).get(0).startsWith("EXEC sp_dropextendedproperty"));

        // Not a storage change: the same comment, and a type change beside it, are their own.
        assertTrue(SchemaDiff.between(of(true, name("name")), of(true, name("name")))
                .isEmpty());
        List<SchemaDiff.Change> both = SchemaDiff.between(
                of(true, name("name")),
                of(true, Column.of("name", ColumnType.TEXT).length(80).comment("display name")));
        assertEquals(2, both.size(), both.toString());
        assertFalse(both.get(0).sql(Database.H2).get(0).startsWith("COMMENT"), both.get(0).description);
    }

    @Test
    public void aNewColumnSaysWhatItHoldsInTheSameChange() {
        SchemaDiff.Change added = only(of(true), of(true, name("name")));
        assertEquals("column item.name", added.description);
        assertEquals(2, added.sql(Database.POSTGRESQL).size());
        assertEquals(
                "COMMENT ON COLUMN item.name IS 'name'",
                added.sql(Database.POSTGRESQL).get(1));
        // Required: added empty, closed by hand - the comment comes with the first step.
        List<SchemaDiff.Change> required =
                SchemaDiff.between(of(true), of(true, name("name").nullable(false)));
        assertEquals(
                "COMMENT ON COLUMN item.name IS 'name'",
                required.get(0).sql(Database.H2).get(1));
        // No label: nothing to say.
        assertEquals(1, only(of(true), of(true, name(null))).sql(Database.H2).size());
    }

    @Test
    public void theStateKeepsACommentAndAStateWithoutOneReadsAsItDid() {
        Schema said = of(true, name("the item's \"name\""));
        String json = said.toJson();
        assertTrue(json.contains("\"comment\" : \"the item's \\\"name\\\"\""), json);
        assertEquals("the item's \"name\"", Schema.fromJson(json).table("item").column("name").comment);

        String old = of(false, Column.of("name", ColumnType.TEXT).length(40)).toJson();
        // The one "comment" is the file's own, at its root.
        assertEquals(1, old.split("\"comment\" :", -1).length - 1, old);
        assertNull(Schema.fromJson(old).table("item").column("name").comment);
    }

    @Test
    public void aTableTwoPartsMakeSaysWhatItHoldsWhenEitherDoes() {
        Schema s = new Schema();
        s.table(new Table("item", "Item").column(Column.of("item_id", ColumnType.BIGINT)));
        s.table(new Table("item", "Other").comments(true).column(name("name")));
        assertTrue(s.table("item").comments());
        assertEquals(1, SchemaWriter.comments(s.table("item"), Database.H2).size());
    }

    @Test
    public void theLimitIsCountedInTheBytesEachDatabaseKeeps() {
        String korean = "이름"; // two characters
        assertEquals(6, Database.ORACLE.commentBytes(korean));
        assertEquals(4, Database.SQLSERVER.commentBytes(korean));
        assertEquals(4000, Database.ORACLE.commentLimit());
        assertEquals(7500, Database.SQLSERVER.commentLimit());
        assertEquals(0, Database.POSTGRESQL.commentLimit());
        assertEquals(0, Database.H2.commentLimit());
    }

    @Test
    public void theProjectTurnsThemOnAndADomainItsOwnWay() {
        String saved = System.getProperty(DdlConfig.COMMENTS);
        String dbs = System.getProperty(DdlConfig.DATABASES);
        try {
            System.setProperty(DdlConfig.DATABASES, "h2");
            System.clearProperty(DdlConfig.COMMENTS);
            assertFalse(DdlConfig.global().comments, "off unless asked");
            System.setProperty(DdlConfig.COMMENTS, "true");
            DdlConfig project = DdlConfig.global();
            assertTrue(project.comments);

            Map<String, String> env = new HashMap<String, String>();
            assertTrue(DdlConfig.forDomain(project, env::get).comments, "the project's");
            env.put("ddl.comments", "false");
            assertFalse(DdlConfig.forDomain(project, env::get).comments);
            System.clearProperty(DdlConfig.COMMENTS);
            env.put("ddl.comments", "true");
            assertTrue(DdlConfig.forDomain(DdlConfig.global(), env::get).comments, "a domain asks for its own");
        } finally {
            restore(DdlConfig.COMMENTS, saved);
            restore(DdlConfig.DATABASES, dbs);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }

    private static SchemaDiff.Change only(Schema before, Schema after) {
        List<SchemaDiff.Change> changes = SchemaDiff.between(before, after);
        assertEquals(1, changes.size(), changes.toString());
        return changes.get(0);
    }
}
