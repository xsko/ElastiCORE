package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.mybatis.MyBatisSchemaRules;
import io.elasticore.base.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * What a JPA entity cannot be generated as, or cannot be created as, said
 * before its code is written - rather than found by the compiler or by the
 * database at the first start.
 */
public final class EntityChecks {

    private EntityChecks() {}

    /**
     * Why an exposed entity's composite key cannot hold a relation, or null.
     * The controller takes each key field as a path variable, and a relation
     * there is the related entity itself, which a path cannot carry: the
     * controller does not compile.
     */
    public static String relationInExposedKey(EntitySpec e) {
        if (e.pkCount() < 2) return null;
        List<String> relations = new ArrayList<String>();
        for (FieldSpec f : e.pkFields()) {
            if (f.isEntityRef()) relations.add(f.name() + ": " + f.coreItemType());
        }
        if (relations.isEmpty()) return null;
        return String.format(
                "[elcore3] %s.%s: its composite key holds the relation %s, and an exposed entity's controller takes"
                        + " each key field in the path, which a relation cannot be - key it by the related key's value"
                        + " (%s), or do not expose it",
                e.domainClassName(), e.getName(), String.join(", ", relations), example(e));
    }

    /** The key by value a relation in the key would be written as: ledgerId: long @id. */
    private static String example(EntitySpec e) {
        for (FieldSpec f : e.pkFields()) {
            if (f.isEntityRef()) return StringUtils.uncapitalize(f.coreItemType()) + "Id: long @id";
        }
        return "";
    }

    /**
     * The table and the columns of a JPA entity that a database of the four
     * reserves, as MyBatis says them: Hibernate quotes nothing by default, so
     * CREATE TABLE "order" or a column "group" is refused there.
     */
    public static String reservedNames(EntitySpec e) {
        if (e.hasMeta("embeddable")) return null;
        List<String> found = new ArrayList<String>();
        // A plain @abstract is a frame with no table of its own; @abstract(entity) has one.
        if (!e.hasMeta("abstract") || e.isAbstractEntity()) {
            String table = tableName(e);
            String bare = table.indexOf('.') >= 0 ? table.substring(table.lastIndexOf('.') + 1) : table;
            if (MyBatisSchemaRules.RESERVED.contains(bare.toLowerCase(java.util.Locale.ROOT))) {
                found.add("the table " + bare);
            }
        }
        for (FieldSpec f : e.fields()) {
            if (!isOneColumn(e, f)) continue;
            String column = f.dbColumnName() != null ? f.dbColumnName() : StringUtils.camelToSnake(f.name());
            if (MyBatisSchemaRules.RESERVED.contains(column.toLowerCase(java.util.Locale.ROOT))) {
                found.add("the column " + column);
            }
        }
        if (found.isEmpty()) return null;
        return String.format(
                "[%s] %s: %s %s reserved by Oracle, PostgreSQL or SQL Server and cannot be created there - name it"
                        + " otherwise with @table / @column.",
                e.domainClassName(), e.getName(), String.join(", ", found), found.size() > 1 ? "are" : "is");
    }

    /**
     * A field written as one column of the entity's own table, named by its
     * {@code @column} or by its own name: a value of a DSL type or an enum. A
     * relation, a collection, a value type (embedded, Money) or a formula is not.
     */
    private static boolean isOneColumn(EntitySpec e, FieldSpec f) {
        if (f.isList() || f.isMoney() || f.isQuantity() || f.hasDsl("formula") || f.hasDsl("transient")) return false;
        return !"UNKNOWN".equals(f.baseTypeName()) || e.isKnownEnum(f.coreItemType());
    }

    /** The table the entity maps to, as JpaEntityAnnotationRules writes it. */
    private static String tableName(EntitySpec e) {
        return e.tableName();
    }

    /**
     * An @table name that already starts with the table.prefix: the prefix is
     * put before it all the same (as 2.x did), so the table is el_el_note.
     * Said, with the line that names el_note.
     */
    public static String prefixTwice(EntitySpec e) {
        String explicit = e.metaValue("table");
        String prefix = e.tablePrefix();
        if (explicit == null || prefix == null || prefix.isEmpty()) return null;
        if (!explicit.toLowerCase(java.util.Locale.ROOT).startsWith(prefix.toLowerCase(java.util.Locale.ROOT))) {
            return null;
        }
        return String.format(
                "[%s] %s: @table(%s) with table.prefix %s maps to the table %s - the prefix comes before the @table"
                        + " name, as in 2.x. If the table is %s, write @table(%s).",
                e.domainClassName(), e.getName(), explicit, prefix, e.tableName(), explicit,
                explicit.substring(prefix.length()));
    }
}
