package io.elasticore.base.model.pub.rules.mybatis;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.DomainSpec;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The tables of a MyBatis domain as CREATE TABLE statements, one file per
 * database: src/main/resources/db/{domain}-schema.sql for H2 and
 * {domain}-schema-postgresql.sql, -oracle.sql, -sqlserver.sql.
 *
 * MyBatis creates no table, as JPA's ddl-auto does; this is the schema the
 * generated mappers read and write, to run with spring.sql.init or to start a
 * migration from. Each file names the types the way that database's
 * Hibernate dialect does (MyBatisSql.ddlType) - Oracle NUMBER(19) and
 * VARCHAR2, SQL Server NVARCHAR and DATETIME2, PostgreSQL TEXT and BYTEA - so
 * a table can be read by either persistence, and declares a generated key
 * that database's way. The foreign keys come after every table, so the
 * tables can be created in any order; the indexes the entity declares
 * (@Index) after them.
 *
 * The runner sets the database as the domain's config value "mybatis.vendor"
 * before each file.
 */
public class MyBatisSchemaRules implements CodeRules {

    public static final String TARGET = "mybatisSchema";

    /** The database the file is written for, a MyBatisSql.Vendor name; H2 when unset. */
    public static final String VENDOR_CONFIG = "mybatis.vendor";

    @Rule(value = "mybatis-schema", target = TARGET, order = 10)
    void schema(DomainSpec d) {
        SourceCode c = ctx(d);
        c.template("mybatis_schema");
        MyBatisSql.Vendor v = vendor(d.config(VENDOR_CONFIG));
        StringBuilder sb = new StringBuilder();
        Set<String> tables = new HashSet<String>();
        for (EntitySpec e : d.entities()) {
            if (!tableOf(e)) continue;
            tables.add(e.getName());
            if (sb.length() > 0) sb.append("\n");
            sb.append(createTable(e, v));
        }
        if (sb.length() == 0) {
            c.dontMake();
            return;
        }
        // The relations, once every table exists: a row still referenced
        // cannot be deleted (409), as under JPA, whose schema has them too.
        StringBuilder keys = new StringBuilder();
        for (EntitySpec e : d.entities()) {
            if (!tableOf(e)) continue;
            for (FieldSpec f : e.fields()) {
                if (!MyBatisSql.isJoined(e, f)) continue;
                EntitySpec t = e.related(f.coreItemType());
                // A table of another domain is created by that domain's schema.
                if (t == null || t.pkField() == null || !tables.contains(t.getName())) continue;
                String table = MyBatisSql.table(e);
                keys.append("ALTER TABLE ").append(table).append(" ADD CONSTRAINT ")
                    .append(constraintName("fk_", table, f.name())).append(" FOREIGN KEY (")
                    .append(MyBatisSql.keyColumn(e, f, t)).append(") REFERENCES ").append(MyBatisSql.table(t))
                    .append(" (").append(MyBatisSql.column(t.pkField())).append(");\n");
            }
        }
        if (keys.length() > 0) sb.append("\n").append(keys);
        StringBuilder indexes = new StringBuilder();
        for (EntitySpec e : d.entities()) {
            if (tableOf(e)) indexes.append(indexes(e, v));
        }
        if (indexes.length() > 0) sb.append("\n").append(indexes);
        for (EntitySpec e : d.entities()) {
            if (tableOf(e)) warnReserved(e);
        }
        c.var("domainName", d.className())
         .var("database", databaseName(v))
         .var("body", sb.toString().trim());
    }

    static MyBatisSql.Vendor vendor(String name) {
        if (name == null || name.isEmpty()) return MyBatisSql.Vendor.H2;
        return MyBatisSql.Vendor.valueOf(name);
    }

    private static String databaseName(MyBatisSql.Vendor v) {
        switch (v) {
            case POSTGRESQL: return "PostgreSQL";
            case ORACLE: return "Oracle 12c or later";
            case SQLSERVER: return "SQL Server 2016 or later";
            default: return "H2";
        }
    }

    /**
     * {prefix}{table}_{field}, cut to 30 characters - Oracle's limit before
     * 12.2 - with a hash of the whole name keeping two long ones apart.
     */
    static String constraintName(String prefix, String table, String field) {
        String name = (prefix + table.replace('.', '_') + "_" + MyBatisSql.springName(field))
                .toLowerCase(Locale.ROOT);
        if (name.length() <= 30) return name;
        String hash = Integer.toHexString(name.hashCode() & 0x7fffffff);
        return name.substring(0, 29 - hash.length()) + "_" + hash;
    }

    /** As above, for a foreign key. */
    static String constraintName(String table, String field) {
        return constraintName("fk_", table, field);
    }

    /** An entity with a table of its own: keyed, not embedded, not abstract. */
    static boolean tableOf(EntitySpec e) {
        return e.pkField() != null && !e.hasMeta("embeddable") && !e.hasMeta("abstract")
                && MyBatisSql.unsupported(e).isEmpty();
    }

    static String createTable(EntitySpec e) {
        return createTable(e, MyBatisSql.Vendor.H2);
    }

    static String createTable(EntitySpec e, MyBatisSql.Vendor v) {
        List<String> lines = new ArrayList<String>();
        FieldSpec pk = e.pkField();
        List<String> uniques = new ArrayList<String>();
        for (FieldSpec f : e.fields()) {
            if (MyBatisSql.isValue(e, f)) {
                StringBuilder col = new StringBuilder(MyBatisSql.column(f)).append(' ').append(MyBatisSql.ddlType(e, f, v));
                if (f == pk && MyBatisSql.generatedKey(pk)) col.append(MyBatisSql.identityClause(v));
                if (f == pk || f.required()) col.append(" NOT NULL");
                lines.add(col.toString());
            } else if (MyBatisSql.isEmbedded(e, f)) {
                // A part is required only where the value is: an optional
                // address leaves every one of its columns empty.
                for (MyBatisSql.Part p : MyBatisSql.parts(e, f)) {
                    lines.add(p.column + " " + MyBatisSql.ddlType(e, p.field, v)
                            + (f.required() && p.field.required() ? " NOT NULL" : ""));
                }
            } else if (MyBatisSql.isToOne(e, f)) {
                EntitySpec t = e.related(f.coreItemType());
                if (t == null || t.pkField() == null) continue;
                String col = MyBatisSql.foreignKey(f, t) + " " + MyBatisSql.ddlType(t, t.pkField(), v)
                        + (f.required() ? " NOT NULL" : "");
                lines.add(col);
            }
        }
        lines.add("PRIMARY KEY (" + MyBatisSql.column(pk) + ")");
        for (FieldSpec f : e.fields()) {
            if (f != pk && f.hasDsl("unique") && !"false".equals(f.dslValue("unique")) && MyBatisSql.isValue(e, f)) {
                // SQL Server's UNIQUE takes one NULL only; the others any
                // number. There a nullable column is kept unique by a filtered
                // index, written with the indexes.
                if (v == MyBatisSql.Vendor.SQLSERVER && !f.required()) continue;
                lines.add("UNIQUE (" + MyBatisSql.column(f) + ")");
            }
        }
        StringBuilder sb = new StringBuilder("CREATE TABLE ").append(MyBatisSql.table(e)).append(" (\n");
        for (int i = 0; i < lines.size(); i++) {
            sb.append("    ").append(lines.get(i)).append(i < lines.size() - 1 ? "," : "").append('\n');
        }
        return sb.append(");\n").toString();
    }

    /**
     * The indexes the entity declares with @Index(name, columnList, unique),
     * and on SQL Server the unique ones of nullable columns, filtered so that
     * rows without a value do not collide.
     */
    static String indexes(EntitySpec e, MyBatisSql.Vendor v) {
        StringBuilder sb = new StringBuilder();
        String table = MyBatisSql.table(e);
        for (EntitySpec.IndexSpec idx : e.indexes()) {
            if (idx.columnList() == null || idx.columnList().trim().isEmpty()) continue;
            List<String> cols = new ArrayList<String>();
            for (String part : idx.columnList().split(",")) {
                String col = part.trim().split("\\s+")[0];
                if (!col.isEmpty()) cols.add(MyBatisSql.springName(col));
            }
            if (cols.isEmpty()) continue;
            boolean unique = "true".equalsIgnoreCase(idx.unique() == null ? "" : idx.unique().trim());
            String name = idx.name() != null && !idx.name().trim().isEmpty()
                    ? idx.name().trim()
                    : constraintName(unique ? "uk_" : "ix_", table, String.join("_", cols));
            sb.append(unique ? "CREATE UNIQUE INDEX " : "CREATE INDEX ").append(name).append(" ON ").append(table)
              .append(" (").append(String.join(", ", cols)).append(")");
            if (unique && v == MyBatisSql.Vendor.SQLSERVER) {
                List<String> nullable = new ArrayList<String>();
                for (String col : cols) {
                    FieldSpec f = byColumn(e, col);
                    if (f == null || !f.required()) nullable.add(col + " IS NOT NULL");
                }
                if (!nullable.isEmpty()) sb.append(" WHERE ").append(String.join(" AND ", nullable));
            }
            sb.append(";\n");
        }
        if (v == MyBatisSql.Vendor.SQLSERVER) {
            for (FieldSpec f : e.fields()) {
                if (f == e.pkField() || !f.hasDsl("unique") || "false".equals(f.dslValue("unique"))
                        || !MyBatisSql.isValue(e, f) || f.required()) continue;
                String col = MyBatisSql.column(f);
                sb.append("CREATE UNIQUE INDEX ").append(constraintName("uk_", table, f.name())).append(" ON ")
                  .append(table).append(" (").append(col).append(") WHERE ").append(col).append(" IS NOT NULL;\n");
            }
        }
        return sb.toString();
    }

    private static FieldSpec byColumn(EntitySpec e, String column) {
        for (FieldSpec f : e.fields()) {
            if (MyBatisSql.isValue(e, f) && MyBatisSql.column(f).equals(column)) return f;
        }
        return null;
    }

    /**
     * Words one of the four databases reserves, which a table or a column
     * named so cannot be created with there unquoted (the generated SQL
     * quotes nothing, as Hibernate does not by default).
     */
    static final Set<String> RESERVED = new HashSet<String>(Arrays.asList(
            "access", "add", "all", "alter", "and", "any", "as", "asc", "audit", "between", "by", "case", "check",
            "cluster", "column", "comment", "compress", "connect", "constraint", "create", "cross", "current",
            "current_date", "current_time", "current_timestamp", "current_user", "date", "decimal", "default",
            "delete", "desc", "distinct", "drop", "else", "end", "except", "exclusive", "exists", "file", "float",
            "for", "foreign", "from", "full", "grant", "group", "having", "identified", "identity", "immediate",
            "in", "increment", "index", "initial", "inner", "insert", "integer", "intersect", "into", "is", "join",
            "key", "left", "level", "like", "limit", "lock", "long", "minus", "mode", "modify", "natural", "noaudit",
            "nocompress", "not", "nowait", "null", "number", "of", "offline", "offset", "on", "online", "option",
            "or", "order", "outer", "percent", "plan", "primary", "prior", "privileges", "public", "raw",
            "references", "rename", "resource", "revoke", "right", "row", "rowid", "rownum", "rows", "rule",
            "select", "session", "set", "share", "size", "smallint", "start", "successful", "synonym", "sysdate",
            "table", "then", "to", "top", "trigger", "uid", "union", "unique", "update", "user", "validate",
            "values", "varchar", "varchar2", "view", "whenever", "where", "with"));

    /** Warns of a table or column a database of the four will refuse by its name. */
    static void warnReserved(EntitySpec e) {
        String table = MyBatisSql.table(e);
        String bare = table.indexOf('.') >= 0 ? table.substring(table.indexOf('.') + 1) : table;
        List<String> found = new ArrayList<String>();
        if (RESERVED.contains(bare)) found.add("the table " + bare);
        for (FieldSpec f : e.fields()) {
            if (MyBatisSql.isValue(e, f) && RESERVED.contains(MyBatisSql.column(f))) {
                found.add("the column " + MyBatisSql.column(f));
            } else if (MyBatisSql.isEmbedded(e, f)) {
                for (MyBatisSql.Part p : MyBatisSql.parts(e, f)) {
                    if (RESERVED.contains(p.column)) found.add("the column " + p.column);
                }
            }
        }
        if (found.isEmpty()) return;
        SourceCode.dslWarnOnce(String.format("[%s] %s: %s %s reserved by Oracle, PostgreSQL or SQL Server and cannot be"
                        + " created there - name it otherwise with @table / @column.", e.domainClassName(), e.getName(),
                String.join(", ", found), found.size() > 1 ? "are" : "is"));
    }
}
