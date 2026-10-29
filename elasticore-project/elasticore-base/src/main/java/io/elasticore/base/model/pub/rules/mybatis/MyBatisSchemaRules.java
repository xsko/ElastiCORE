package io.elasticore.base.model.pub.rules.mybatis;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.DomainSpec;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.schema.Database;
import io.elasticore.base.model.pub.rules.schema.Schema;
import io.elasticore.base.model.pub.rules.schema.SchemaWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The tables of a MyBatis domain as CREATE TABLE statements for H2
 * (src/main/resources/db/{domain}-schema.sql): MyBatis creates no table, as
 * JPA's ddl-auto does, and an application needs one schema to start on.
 *
 * The schema for another database, and its migrations, are the project's
 * DDL - elasticore { ddl { databases = [...] } } in the build, which writes
 * every domain's tables, JPA and MyBatis alike, in one file per database
 * (DdlRun). A domain the DDL covers gets no file of its own here.
 */
public class MyBatisSchemaRules implements CodeRules {

    public static final String TARGET = "mybatisSchema";

    @Rule(value = "mybatis-schema", target = TARGET, order = 10)
    void schema(DomainSpec d) {
        SourceCode c = ctx(d);
        c.template("mybatis_schema");
        Schema schema = MyBatisTables.of(d.entities());
        if (schema.isEmpty()) {
            c.dontMake();
            return;
        }
        for (EntitySpec e : d.entities()) {
            if (MyBatisTables.tableOf(e)) warnReserved(e);
        }
        c.var("domainName", d.className())
         .var("database", Database.H2.title)
         .var("body", SchemaWriter.body(schema, Database.H2));
    }

    /** As above, for one entity: its CREATE TABLE in H2's words. */
    static String createTable(EntitySpec e) {
        return SchemaWriter.createTable(MyBatisTables.table(e, new HashSet<String>()), Database.H2);
    }

    /**
     * Words one of the four databases reserves, which a table or a column
     * named so cannot be created with there unquoted (the generated SQL
     * quotes nothing, as Hibernate does not by default).
     */
    public static final Set<String> RESERVED = new HashSet<String>(Arrays.asList(
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
