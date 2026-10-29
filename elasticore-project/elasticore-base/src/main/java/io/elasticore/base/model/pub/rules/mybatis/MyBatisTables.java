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
        for (FieldSpec f : e.fields()) {
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
        for (FieldSpec f : e.fields()) {
            if (!MyBatisSql.isJoined(e, f)) continue;
            EntitySpec target = e.related(f.coreItemType());
            // A table of another domain is created by that domain's schema.
            if (target == null || target.pkField() == null || !tables.contains(target.getName())) continue;
            t.foreignKey(new Table.ForeignKey(Table.constraintName("fk_", name, MyBatisSql.springName(f.name())),
                    Collections.singletonList(MyBatisSql.keyColumn(e, f, target)), MyBatisSql.table(target),
                    Collections.singletonList(MyBatisSql.column(target.pkField()))));
        }
        for (FieldSpec f : e.fields()) {
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
