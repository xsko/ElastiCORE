package io.elasticore.base.model.pub.rules.mybatis;

import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.schema.Column;
import io.elasticore.base.model.pub.rules.schema.ColumnType;
import io.elasticore.base.model.pub.rules.schema.Schema;
import io.elasticore.base.model.pub.rules.schema.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The tables a MyBatis domain's mappers read and write, as the shared schema
 * model: the columns MyBatisSql names, the keys, the foreign keys of the
 * relations whose targets are among the tables given, and the indexes the
 * entities declare (@Index, a field's @unique).
 */
public final class MyBatisTables {

    private MyBatisTables() {
    }

    /** An entity with a table of its own: keyed, not embedded, not abstract, nothing refused. */
    public static boolean tableOf(EntitySpec e) {
        return e.pkField() != null && !e.hasMeta("embeddable") && !e.hasMeta("abstract")
                && MyBatisSql.unsupported(e).isEmpty();
    }

    /** The tables of the entities given; a relation to an entity not among them gets its column but no foreign key. */
    public static Schema of(List<EntitySpec> entities) {
        Schema s = new Schema();
        Set<String> names = new HashSet<String>();
        for (EntitySpec e : entities) if (tableOf(e)) names.add(e.getName());
        for (EntitySpec e : entities) if (tableOf(e)) s.table(table(e, names));
        return s;
    }

    static Table table(EntitySpec e, Set<String> tables) {
        String name = MyBatisSql.table(e);
        Table t = new Table(name, e.domainClassName() + "." + e.getName());
        FieldSpec pk = e.pkField();
        for (FieldSpec f : MyBatisSql.fields(e)) {
            if (MyBatisSql.isValue(e, f)) {
                Column c = column(e, f, MyBatisSql.column(f));
                if (f == pk) c = c.nullable(false).identity(MyBatisSql.generatedKey(pk));
                else if (f.required()) c = c.nullable(false);
                t.column(c);
            } else if (MyBatisSql.isEmbedded(e, f)) {
                // A part is required only where the value is: an optional
                // address leaves every one of its columns empty.
                for (MyBatisSql.Part p : MyBatisSql.parts(e, f)) {
                    t.column(column(e, p.field, p.column).nullable(!(f.required() && p.field.required())));
                }
            } else if (MyBatisSql.isToOne(e, f)) {
                EntitySpec target = e.related(f.coreItemType());
                if (target == null || target.pkField() == null) continue;
                t.column(column(target, target.pkField(), MyBatisSql.foreignKey(f, target)).nullable(!f.required()));
            }
        }
        t.primaryKey(MyBatisSql.column(pk));
        for (FieldSpec f : MyBatisSql.fields(e)) {
            if (!MyBatisSql.holdsKey(e, f)) continue;
            EntitySpec target = e.related(f.coreItemType());
            // A table of another domain is created by that domain's schema.
            if (target == null || target.pkField() == null || !tables.contains(target.getName())) continue;
            t.foreignKey(new Table.ForeignKey(Table.constraintName("fk_", name, MyBatisSql.springName(f.name())),
                    Collections.singletonList(MyBatisSql.keyColumn(e, f, target)), MyBatisSql.table(target),
                    Collections.singletonList(MyBatisSql.column(target.pkField()))));
        }
        for (FieldSpec f : MyBatisSql.fields(e)) {
            if (f != pk && f.hasDsl("unique") && !"false".equals(f.dslValue("unique")) && MyBatisSql.isValue(e, f)) {
                t.index(new Table.Index(Table.constraintName("uk_", name, MyBatisSql.springName(f.name())),
                        Collections.singletonList(MyBatisSql.column(f)), true));
            }
        }
        for (EntitySpec.IndexSpec idx : e.indexes()) {
            if (idx.columnList() == null || idx.columnList().trim().isEmpty()) continue;
            List<String> cols = new ArrayList<String>();
            for (String part : idx.columnList().split(",")) {
                String col = part.trim().split("\\s+")[0];
                if (!col.isEmpty()) cols.add(MyBatisSql.springName(col));
            }
            if (cols.isEmpty()) continue;
            boolean unique = "true".equalsIgnoreCase(idx.unique() == null ? "" : idx.unique().trim());
            String idxName = idx.name() != null && !idx.name().trim().isEmpty() ? idx.name().trim()
                    : Table.constraintName(unique ? "uk_" : "ix_", name, String.join("_", cols));
            t.index(new Table.Index(idxName, cols, unique));
        }
        return t;
    }

    /** The outbox table of each MyBatis domain whose outbox the domain rules wrote this run. */
    private static final java.util.Map<String, String> OUTBOXES = new java.util.concurrent.ConcurrentHashMap<String, String>();

    /** Says a MyBatis domain's outbox table, as the outbox rules write its mapper: the DDL adds it by name. */
    public static void outboxOf(String domain, String table) {
        OUTBOXES.put(domain, table);
    }

    /** The outbox table the run wrote for a MyBatis domain, or null. */
    public static String outboxOf(String domain) {
        return domain == null ? null : OUTBOXES.get(domain);
    }

    /** Forgets the outboxes of an earlier run in the same process. */
    public static void forgetOutboxes() {
        OUTBOXES.clear();
    }

    /**
     * The transactional outbox's table of a domain over MyBatis (the domain
     * rules' outbox: true): the columns the generated outbox mapper reads
     * and writes, and the index its relay reads the pending rows by.
     */
    public static Table outbox(String name, String source) {
        Table t = new Table(name, source);
        t.column(Column.of("id", ColumnType.BIGINT).nullable(false).identity(true));
        t.column(Column.of("aggregate_type", ColumnType.TEXT).length(100).nullable(false));
        t.column(Column.of("aggregate_id", ColumnType.TEXT).length(200));
        t.column(Column.of("event_type", ColumnType.TEXT).length(300).nullable(false));
        t.column(Column.of("payload", ColumnType.LONG_TEXT).nullable(false));
        t.column(Column.of("occurred_on", ColumnType.TIMESTAMP));
        t.column(Column.of("recorded_at", ColumnType.TIMESTAMP).nullable(false));
        t.column(Column.of("published_at", ColumnType.TIMESTAMP));
        t.column(Column.of("attempts", ColumnType.INTEGER).nullable(false));
        t.column(Column.of("last_error", ColumnType.TEXT).length(500));
        t.primaryKey("id");
        t.index(new Table.Index(Table.constraintName("ix_", name, "pending"),
                java.util.Arrays.asList("published_at", "id"), false));
        return t;
    }

    /** A value's column in the shared words: the JDBC type MyBatis binds it as, its size, an enum's code length. */
    static Column column(EntitySpec e, FieldSpec f, String name) {
        String jdbc = MyBatisSql.jdbcType(e, f);
        switch (jdbc) {
            case "VARCHAR": {
                int len = f.rawLength();
                boolean code = MyBatisSql.isEnum(e, f);
                if (code) {
                    String dbLen = e.enumDbLength(RuleText.simpleName(f.typeName()));
                    try {
                        len = dbLen == null || dbLen.isEmpty() ? 50 : Integer.parseInt(dbLen.trim());
                    } catch (NumberFormatException ex) {
                        len = 50;
                    }
                }
                return Column.of(name, ColumnType.TEXT).length(len > 0 ? len : 255).ascii(code);
            }
            case "CLOB": return Column.of(name, ColumnType.LONG_TEXT);
            case "BLOB": return Column.of(name, ColumnType.LONG_BINARY);
            case "NUMERIC": return Column.of(name, ColumnType.DECIMAL).decimal(f.precision() > 0 ? f.precision() : 38, 0);
            case "DECIMAL": return Column.of(name, ColumnType.DECIMAL).decimal(f.precision(), Math.max(0, f.scale()));
            case "TIMESTAMP_WITH_TIMEZONE": return Column.of(name, ColumnType.TIMESTAMP_TZ);
            case "BIGINT": case "INTEGER": case "SMALLINT": case "TINYINT": case "BOOLEAN": case "DOUBLE": case "REAL":
            case "DATE": case "TIME": case "TIMESTAMP":
                return Column.of(name, ColumnType.valueOf(jdbc));
            default:
                throw new IllegalArgumentException(e.getName() + "." + f.name() + ": no column type for " + jdbc);
        }
    }
}
