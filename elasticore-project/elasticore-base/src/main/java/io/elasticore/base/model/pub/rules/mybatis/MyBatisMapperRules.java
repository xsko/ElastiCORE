package io.elasticore.base.model.pub.rules.mybatis;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.base.ServiceAccessRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The MyBatis mapper of an entity: the interface the service calls
 * (target "mybatisRepository", in the repository package, named as the JPA
 * repository is) and the XML beside it with its statements (target
 * "mybatisXml", in the resources under the same package, which is where
 * MyBatis looks for it).
 *
 * <ul>
 *   <li>One row with its to-one relations: every relation the row holds the key
 *       of is LEFT JOINed and read whole, one level deep, so a page of rows is
 *       one statement - there is no lazy loading to fall into N+1 with. A
 *       relation of the joined row comes with its key only. An embedded value
 *       is read from its columns, in the row and in a joined row.</li>
 *   <li>Search: the conditions of the search DTO (MyBatisSearchRules), each
 *       applied only when its property has a value, and the owner's rows only
 *       where read is the owner's.</li>
 *   <li>Sort: by the properties of the entity, through a list of their
 *       columns; anything else is a 400, so no text of the request reaches
 *       the SQL.</li>
 *   <li>Page: LIMIT/OFFSET, or OFFSET/FETCH where the database is Oracle,
 *       Tibero, SQL Server or DB2 (the runtime library tells MyBatis which).</li>
 *   <li>Write: insert (the key back from the database when it generates it),
 *       update of every column, and delete by key; a version is compared and
 *       counted up in the same UPDATE.</li>
 *   <li>Collections: the rows of another entity's collection are read for a
 *       list of its keys at once (findAllBy{back}In), so a page of orders and
 *       their lines is three statements; a collection of aggregate members is
 *       deleted with its root (deleteAllBy{back}).</li>
 * </ul>
 *
 * Every statement is one the four databases the schema is written for run
 * alike - H2, PostgreSQL, Oracle and SQL Server - and the ones they do not
 * (the page) choose by the database id.
 *
 * The runner hands the search conditions over as the entity's config value
 * "mybatis.search", the collections that point at the entity as
 * "mybatis.inbound", whether the entity has a service of its own as
 * "mybatis.serviced", and the related entities through knownRelated.
 */
public class MyBatisMapperRules implements CodeRules {

    public static final String REPOSITORY = "mybatisRepository";
    public static final String XML = "mybatisXml";

    /** Where the runner puts the search DTO's conditions for the entity. */
    public static final String SEARCH_CONFIG = "mybatis.search";

    /** The fields of this entity other entities' collections are mapped by, one per line. */
    public static final String INBOUND_CONFIG = "mybatis.inbound";

    /** "false" for an entity without a service: a member read and written through its root. */
    public static final String SERVICED_CONFIG = "mybatis.serviced";

    /** PostgreSQL cuts a name longer than this, which would leave a column the result map cannot find. */
    static final int ALIAS_LIMIT = 63;

    private static final String I = "    ";

    /** Whether the entity has its own service, search DTO and API; a member has none of them. */
    static boolean serviced(EntitySpec e) {
        return !"false".equals(e.config(SERVICED_CONFIG));
    }

    // ---- the interface -----------------------------------------------------

    @Rule(value = "mybatis-repository", target = REPOSITORY, order = 10)
    void repository(EntitySpec e) {
        SourceCode c = ctx(e);
        boolean serviced = serviced(e);
        c.template(serviced ? "mybatis_repository" : "mybatis_repository_member");
        FieldSpec pk = e.pkField();
        String keyType = c.syntax().typeName(pk.typeName());
        String entity = e.getName();

        c.var("packageName", e.pkg("repository"))
         .var("entityPackageName", e.pkg("entity"))
         .var("dtoPackageName", e.pkg("dto"))
         .var("entityClassName", entity)
         .var("className", "%sRepository", entity)
         .var("keyType", keyType);

        c.slot("inboundFinders");
        for (FieldSpec back : inbound(e)) {
            EntitySpec root = e.related(back.coreItemType());
            String rootKey = c.syntax().nullableType(root.pkField().typeName());
            String by = RuleText.capital(back.name());
            c.varLine("inboundFinders", "")
             .varLine("inboundFinders", "/** The rows whose %s is one of the given keys: what reads the %s rows' collection of these. */",
                     back.name(), root.getName())
             .varLine("inboundFinders", "List<%s> findAllBy%sIn(@Param(\"keys\") java.util.Collection<%s> keys);",
                     entity, by, rootKey)
             .varLine("inboundFinders", "")
             .varLine("inboundFinders", "/** Deletes the rows of one %s: its collection, replaced or deleted with it. */",
                     root.getName())
             .varLine("inboundFinders", "int deleteAllBy%s(@Param(\"key\") %s key);", by, rootKey);
        }

        if (!serviced) return;

        String srch = e.searchDtoClassName();
        c.var("searchDTOClassName", srch);

        for (Map.Entry<String, String> s : sortColumns(e).entrySet()) {
            c.varLine("sortColumnList", "m.put(%s, %s);", RuleText.quote(s.getKey()), RuleText.quote(s.getValue()));
        }

        String login = ServiceAccessRules.loginField(e);
        c.slot("loginFinder");
        if (login != null) {
            c.varLine("loginFinder", "")
             .varLine("loginFinder", "/** The rows whose %s is the one given: signing in reads it. */", login)
             .varLine("loginFinder", "List<%s> findAllByLogin(@Param(\"value\") String %s);", entity, login);
        }

        // Only the conditions the XML writes: one it cannot write must not
        // count, or an update by condition naming only it would update
        // every row (resolved() stops the build for it).
        List<String[]> conditions = resolved(e, joins(e));
        StringBuilder test = new StringBuilder();
        for (String[] cond : conditions) {
            for (String prop : properties(cond)) {
                if (test.length() > 0) test.append("\n            || ");
                String getter = "search.get" + Character.toUpperCase(prop.charAt(0)) + prop.substring(1) + "()";
                test.append(hasValueJava(cond[3], getter, "in".equals(cond[0])));
            }
        }
        c.var("conditionTest", test.length() == 0 ? "false" : test.toString());
    }

    /** The search DTO properties one condition reads. */
    private static List<String> properties(String[] cond) {
        List<String> out = new ArrayList<String>();
        if ("between".equals(cond[0])) {
            out.add(cond[2] + "From");
            out.add(cond[2] + "To");
        } else if ("isnull".equals(cond[0])) {
            out.add(cond[2] + "Empty");
        } else {
            out.add(cond[2]);
        }
        return out;
    }

    private static String hasValueJava(String type, String getter, boolean list) {
        if (list) return "(" + getter + " != null && !" + getter + ".isEmpty())";
        if ("String".equals(RuleText.simpleName(type))) return "(" + getter + " != null && !" + getter + ".trim().isEmpty())";
        return getter + " != null";
    }

    /**
     * What a sort may name, by property: the row's own values and those of
     * its embedded values, its relations' keys, and the values of the rows it
     * joins - the paths JPA's Sort reads.
     */
    static Map<String, String> sortColumns(EntitySpec e) {
        // A large value is not in the list: Oracle cannot sort by one.
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (FieldSpec f : e.fields()) {
            if (MyBatisSql.isValue(e, f)) {
                if (!MyBatisSql.isLob(e, f)) m.put(f.name(), MyBatisSql.ROOT + "." + MyBatisSql.column(f));
            } else if (MyBatisSql.isEmbedded(e, f)) {
                for (MyBatisSql.Part p : MyBatisSql.parts(e, f)) {
                    if (!MyBatisSql.isLob(e, p.field)) m.put(f.name() + "." + p.field.name(), MyBatisSql.ROOT + "." + p.column);
                }
            }
        }
        for (Join jn : joins(e)) {
            FieldSpec tpk = jn.target.pkField();
            m.put(jn.field.name() + "." + tpk.name(), MyBatisSql.ROOT + "." + jn.key);
            for (FieldSpec tf : jn.target.fields()) {
                if (tf == tpk) continue;
                if (MyBatisSql.isValue(jn.target, tf)) {
                    if (!MyBatisSql.isLob(jn.target, tf)) {
                        m.put(jn.field.name() + "." + tf.name(), jn.alias + "." + MyBatisSql.column(tf));
                    }
                } else if (MyBatisSql.isEmbedded(jn.target, tf)) {
                    for (MyBatisSql.Part p : MyBatisSql.parts(jn.target, tf)) {
                        if (MyBatisSql.isLob(jn.target, p.field)) continue;
                        m.put(jn.field.name() + "." + tf.name() + "." + p.field.name(), jn.alias + "." + p.column);
                    }
                }
            }
        }
        return m;
    }

    // ---- the XML -----------------------------------------------------------

    @Rule(value = "mybatis-xml", target = XML, order = 10)
    void xml(EntitySpec e) {
        SourceCode c = ctx(e);
        c.template("mybatis_xml");
        boolean serviced = serviced(e);
        String entity = e.getName();
        String entityType = e.pkg("entity") + "." + entity;
        String table = MyBatisSql.table(e);
        FieldSpec pk = e.pkField();
        String pkColumn = MyBatisSql.column(pk);
        FieldSpec version = MyBatisSql.version(e);

        c.var("namespace", "%s.%sRepository", e.pkg("repository"), entity)
         .var("entityClassName", entity);

        List<Join> joins = joins(e);

        StringBuilder x = new StringBuilder();

        // resultMap - the DTD's order: id, the results, then the associations
        x.append(I).append("<resultMap id=\"row\" type=\"").append(entityType).append("\">\n");
        x.append(I).append(I).append("<id property=\"").append(pk.name()).append("\" column=\"").append(pkColumn).append("\"/>\n");
        for (FieldSpec f : e.fields()) {
            if (f != pk && MyBatisSql.isValue(e, f)) {
                x.append(I).append(I).append(result(e, f, MyBatisSql.column(f))).append("\n");
            }
        }
        for (FieldSpec f : e.fields()) {
            if (MyBatisSql.isEmbedded(e, f)) embeddedResult(x, e, f, I + I);
        }
        for (Join jn : joins) {
            EntitySpec t = jn.target;
            x.append(I).append(I).append("<association property=\"").append(jn.field.name())
             .append("\" javaType=\"").append(t.pkg("entity")).append('.').append(t.getName())
             .append("\" columnPrefix=\"").append(jn.prefix).append("\">\n");
            x.append(I).append(I).append(I).append("<id property=\"").append(t.pkField().name())
             .append("\" column=\"").append(MyBatisSql.column(t.pkField())).append("\"/>\n");
            for (FieldSpec tf : t.fields()) {
                if (tf != t.pkField() && MyBatisSql.isValue(t, tf)) {
                    x.append(I).append(I).append(I).append(result(t, tf, MyBatisSql.column(tf))).append("\n");
                }
            }
            for (FieldSpec tf : t.fields()) {
                if (tf == t.pkField()) continue;
                if (MyBatisSql.isEmbedded(t, tf)) {
                    embeddedResult(x, t, tf, I + I + I);
                } else if (MyBatisSql.isJoined(t, tf)) {
                    // A relation of the joined row: its key, which is a column of that row.
                    EntitySpec tt = t.related(tf.coreItemType());
                    if (tt == null || tt.pkField() == null) continue;
                    x.append(I).append(I).append(I).append("<association property=\"").append(tf.name())
                     .append("\" javaType=\"").append(tt.pkg("entity")).append('.').append(tt.getName()).append("\">\n")
                     .append(I).append(I).append(I).append(I).append("<id property=\"").append(tt.pkField().name())
                     .append("\" column=\"").append(MyBatisSql.keyColumn(t, tf, tt)).append("\"/>\n")
                     .append(I).append(I).append(I).append("</association>\n");
                }
            }
            x.append(I).append(I).append("</association>\n");
        }
        x.append(I).append("</resultMap>\n\n");

        // columns and from
        List<String> cols = new ArrayList<String>();
        for (FieldSpec f : e.fields()) {
            if (MyBatisSql.isValue(e, f)) {
                cols.add(MyBatisSql.ROOT + "." + MyBatisSql.column(f));
            } else if (MyBatisSql.isEmbedded(e, f)) {
                for (MyBatisSql.Part p : MyBatisSql.parts(e, f)) cols.add(MyBatisSql.ROOT + "." + p.column);
            }
        }
        for (Join jn : joins) {
            for (String col : joinedColumns(jn)) cols.add(jn.alias + "." + col + " AS " + jn.prefix + col);
        }
        x.append(I).append("<sql id=\"columns\">\n");
        for (int i = 0; i < cols.size(); i++) {
            x.append(I).append(I).append(cols.get(i)).append(i < cols.size() - 1 ? "," : "").append("\n");
        }
        x.append(I).append("</sql>\n\n");

        x.append(I).append("<sql id=\"from\">\n");
        x.append(I).append(I).append(table).append(' ').append(MyBatisSql.ROOT).append("\n");
        for (Join jn : joins) {
            x.append(I).append(I).append("LEFT JOIN ").append(MyBatisSql.table(jn.target)).append(' ').append(jn.alias)
             .append(" ON ").append(jn.alias).append('.').append(MyBatisSql.column(jn.target.pkField()))
             .append(" = ").append(MyBatisSql.ROOT).append('.').append(jn.key).append("\n");
        }
        x.append(I).append("</sql>\n\n");

        if (serviced) where(x, e, joins);

        // order and page
        x.append(I).append("<sql id=\"orderBy\">\n")
         .append(I).append(I).append("ORDER BY <choose><when test=\"orderBy != null\">${orderBy}</when><otherwise>")
         .append(MyBatisSql.ROOT).append('.').append(pkColumn).append("</otherwise></choose>\n")
         .append(I).append("</sql>\n\n");
        if (serviced) {
            x.append(I).append("<sql id=\"page\">\n")
             .append(I).append(I).append("<if test=\"limit != null\">\n")
             .append(I).append(I).append(I).append("<choose>\n")
             .append(I).append(I).append(I).append(I).append("<when test=\"_databaseId == 'oracle' or _databaseId == 'sqlserver' or _databaseId == 'db2'\">OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY</when>\n")
             .append(I).append(I).append(I).append(I).append("<otherwise>LIMIT #{limit} OFFSET #{offset}</otherwise>\n")
             .append(I).append(I).append(I).append("</choose>\n")
             .append(I).append(I).append("</if>\n")
             .append(I).append("</sql>\n\n");
        }

        String select = "SELECT <include refid=\"columns\"/> FROM <include refid=\"from\"/>";
        String pkParam = "#{id," + "jdbcType=" + MyBatisSql.paramJdbcType(e, pk) + "}";

        statement(x, "select", "findById", "resultMap=\"row\"",
                select + " WHERE " + MyBatisSql.ROOT + "." + pkColumn + " = " + pkParam);
        statement(x, "select", "countById", "resultType=\"long\"",
                "SELECT COUNT(*) FROM " + table + " WHERE " + pkColumn + " = " + pkParam);
        statement(x, "select", "findAll", "resultMap=\"row\"",
                select + " <include refid=\"orderBy\"/>");
        if (serviced) {
            statement(x, "select", "findBySearch", "resultMap=\"row\"",
                    select + " <include refid=\"where\"/> <include refid=\"orderBy\"/> <include refid=\"page\"/>");
            statement(x, "select", "countBySearch", "resultType=\"long\"",
                    "SELECT COUNT(*) FROM <include refid=\"from\"/> <include refid=\"where\"/>");
            String login = ServiceAccessRules.loginField(e);
            if (login != null) {
                FieldSpec lf = field(e, login);
                if (lf != null) statement(x, "select", "findAllByLogin", "resultMap=\"row\"",
                        select + " WHERE " + MyBatisSql.ROOT + "." + MyBatisSql.column(lf) + " = #{value}");
            }
        }
        for (FieldSpec back : inbound(e)) {
            EntitySpec root = e.related(back.coreItemType());
            String fk = MyBatisSql.keyColumn(e, back, root);
            String keyJdbc = MyBatisSql.paramJdbcType(root, root.pkField());
            String by = RuleText.capital(back.name());
            statement(x, "select", "findAllBy" + by + "In", "resultMap=\"row\"",
                    select + " WHERE " + MyBatisSql.ROOT + "." + fk
                            + " IN <foreach collection=\"keys\" item=\"k\" open=\"(\" separator=\",\" close=\")\">#{k,jdbcType="
                            + keyJdbc + "}</foreach> ORDER BY " + MyBatisSql.ROOT + "." + pkColumn);
            statement(x, "delete", "deleteAllBy" + by, "",
                    "DELETE FROM " + table + " WHERE " + fk + " = #{key,jdbcType=" + keyJdbc + "}");
        }

        // insert
        boolean generated = MyBatisSql.generatedKey(pk);
        List<String> insertCols = new ArrayList<String>();
        List<String> insertVals = new ArrayList<String>();
        for (FieldSpec f : e.fields()) {
            if (f == pk && generated) continue;
            if (MyBatisSql.isValue(e, f)) {
                insertCols.add(MyBatisSql.column(f));
                String value = param(e, f, f.name());
                insertVals.add(f == version ? "COALESCE(" + value + ", 0)" : value);
            } else if (MyBatisSql.isEmbedded(e, f)) {
                for (MyBatisSql.Part p : MyBatisSql.parts(e, f)) {
                    insertCols.add(p.column);
                    insertVals.add(param(e, p.field, f.name() + "." + p.field.name()));
                }
            } else if (MyBatisSql.isToOne(e, f) && e.related(f.coreItemType()) != null
                    && e.related(f.coreItemType()).pkField() != null) {
                EntitySpec t = e.related(f.coreItemType());
                insertCols.add(MyBatisSql.foreignKey(f, t));
                insertVals.add(param(t, t.pkField(), f.name() + "." + t.pkField().name()));
            }
        }
        String insertAttrs = generated
                ? "useGeneratedKeys=\"true\" keyProperty=\"" + pk.name() + "\" keyColumn=\"" + pkColumn + "\""
                : "";
        statement(x, "insert", "insert", insertAttrs,
                "INSERT INTO " + table + " (" + join(insertCols) + ")\n" + I + I + "VALUES (" + join(insertVals) + ")");

        // update
        List<String> sets = new ArrayList<String>();
        for (FieldSpec f : e.fields()) {
            if (f == pk) continue;
            // Kept as first written, as JPA's @Column(updatable = false) keeps it.
            if (!MyBatisSql.updatable(f)) continue;
            if (f == version) {
                String vc = MyBatisSql.column(f);
                sets.add(vc + " = COALESCE(" + vc + ", 0) + 1");
            } else if (MyBatisSql.isValue(e, f)) {
                sets.add(MyBatisSql.column(f) + " = " + param(e, f, f.name()));
            } else if (MyBatisSql.isEmbedded(e, f)) {
                for (MyBatisSql.Part p : MyBatisSql.parts(e, f)) {
                    sets.add(p.column + " = " + param(e, p.field, f.name() + "." + p.field.name()));
                }
            } else if (MyBatisSql.isToOne(e, f) && e.related(f.coreItemType()) != null
                    && e.related(f.coreItemType()).pkField() != null) {
                EntitySpec t = e.related(f.coreItemType());
                sets.add(MyBatisSql.foreignKey(f, t) + " = " + param(t, t.pkField(), f.name() + "." + t.pkField().name()));
            }
        }
        String where = " WHERE " + pkColumn + " = " + param(e, pk, pk.name());
        if (version != null) {
            String vc = MyBatisSql.column(version);
            where += " <choose><when test=\"" + version.name() + " != null\">AND " + vc + " = " + param(e, version, version.name())
                    + "</when><otherwise>AND " + vc + " IS NULL</otherwise></choose>";
        }
        if (!sets.isEmpty()) {
            statement(x, "update", "update", "",
                    "UPDATE " + table + " SET\n" + I + I + I + joinLines(sets) + "\n" + I + I + where.trim());
        }
        statement(x, "delete", "deleteById", "", "DELETE FROM " + table + " WHERE " + pkColumn + " = " + pkParam);

        c.var("body", RuleText.stripTrailing(x.toString()));
    }

    /** The WHERE clause of a search: the search DTO's conditions and the owner's rows. */
    private void where(StringBuilder x, EntitySpec e, List<Join> joins) {
        String entity = e.getName();
        x.append(I).append("<sql id=\"where\">\n");
        x.append(I).append(I).append("<where>\n");
        List<String[]> conditions = resolved(e, joins);
        if (!conditions.isEmpty()) {
            x.append(I).append(I).append(I).append("<if test=\"s != null\">\n");
            int b = 0;
            for (String[] cond : conditions) {
                Path p = resolve(e, joins, cond[1]);
                x.append(condition(cond, p, "b" + (++b)));
            }
            x.append(I).append(I).append(I).append("</if>\n");
        }
        FieldSpec ownerField = ServiceAccessRules.ownerField(e);
        if (ownerField != null && !MyBatisSql.isValue(e, ownerField)) {
            // The service narrows a search to the owner's rows through this
            // condition; without a column it would narrow nothing.
            io.elasticore.base.model.pub.rules.ModelCheck.fail(String.format(
                    "[elcore3] %s.%s: @owner(%s) is not a column of the row, so mode: mybatis cannot keep a search"
                            + " to the owner's rows - name a field the row holds", e.domainClassName(), entity,
                    ownerField.name()));
        }
        if (ownerField != null && MyBatisSql.isValue(e, ownerField)) {
            x.append(I).append(I).append(I).append("<if test=\"owner != null\">AND ").append(MyBatisSql.ROOT).append('.')
             .append(MyBatisSql.column(ownerField)).append(" = #{owner}</if>\n");
        }
        x.append(I).append(I).append("</where>\n");
        x.append(I).append("</sql>\n\n");
    }

    /** The result mapping of an embedded value: an association of its own columns, null when every one is. */
    private static void embeddedResult(StringBuilder x, EntitySpec e, FieldSpec f, String indent) {
        EntitySpec type = e.related(f.coreItemType());
        if (type == null) return;
        x.append(indent).append("<association property=\"").append(f.name()).append("\" javaType=\"")
         .append(type.pkg("entity")).append('.').append(type.getName()).append("\">\n");
        for (MyBatisSql.Part p : MyBatisSql.parts(e, f)) {
            x.append(indent).append(I).append(result(e, p.field, p.column)).append("\n");
        }
        x.append(indent).append("</association>\n");
    }

    /** The columns read of a joined row: its values, its embedded values' and its relations' keys. */
    private static List<String> joinedColumns(Join jn) {
        List<String> out = new ArrayList<String>();
        for (FieldSpec tf : jn.target.fields()) {
            if (MyBatisSql.isValue(jn.target, tf)) {
                out.add(MyBatisSql.column(tf));
            } else if (MyBatisSql.isEmbedded(jn.target, tf)) {
                for (MyBatisSql.Part p : MyBatisSql.parts(jn.target, tf)) out.add(p.column);
            } else if (MyBatisSql.isToOne(jn.target, tf)) {
                // (A relation read through an id field has its key among the values.)
                EntitySpec tt = jn.target.related(tf.coreItemType());
                if (tt != null && tt.pkField() != null) out.add(MyBatisSql.foreignKey(tf, tt));
            }
        }
        return out;
    }

    /** The to-one relations read with the row, each under an alias of its own (j1, j2 ...). */
    static List<Join> joins(EntitySpec e) {
        List<Join> joins = new ArrayList<Join>();
        int n = 0;
        for (FieldSpec f : e.fields()) {
            if (!MyBatisSql.isJoined(e, f)) continue;
            n++;
            EntitySpec target = e.related(f.coreItemType());
            if (target == null || target.pkField() == null) {
                SourceCode.dslWarnOnce(String.format("[%s] %s.%s: %s is not an entity with a key the MyBatis mapper can"
                        + " join; the relation is left out of the row.", e.domainClassName(), e.getName(), f.name(),
                        f.coreItemType()));
                continue;
            }
            String alias = "j" + n;
            String key = MyBatisSql.keyColumn(e, f, target);
            Join jn = new Join(f, target, alias, f.name() + "__", key);
            // A column label PostgreSQL would cut short is read under the
            // alias's prefix instead, which every database keeps whole.
            for (String col : joinedColumns(jn)) {
                if ((jn.prefix + col).length() > ALIAS_LIMIT) {
                    jn = new Join(f, target, alias, alias + "_", key);
                    break;
                }
            }
            joins.add(jn);
        }
        return joins;
    }

    /**
     * The search conditions the XML can write. One whose path reaches no
     * column - deeper than a joined row - stops the build: left out, it
     * would search by nothing and widen an update or a delete by condition
     * to the whole table.
     */
    static List<String[]> resolved(EntitySpec e, List<Join> joins) {
        List<String[]> out = new ArrayList<String[]>();
        for (String[] cond : conditions(e)) {
            Path p = resolve(e, joins, cond[1]);
            if (p != null && lob(p) && !TEXT_MATCHES.contains(cond[0])) {
                // Oracle compares no CLOB: a search by one would fail on every
                // request there. Left out of the WHERE clause and of
                // hasCondition alike, so it never narrows - nor seems to.
                SourceCode.dslWarnOnce(String.format(
                        "[%s] %s.%s is a large value (a LOB on Oracle), searched by text only - @search(like),"
                                + " a prefix or a suffix; its %s condition is left out under mode: mybatis",
                        e.domainClassName(), e.getName(), cond[1], cond[0]));
            } else if (p != null) {
                out.add(cond);
            } else {
                io.elasticore.base.model.pub.rules.ModelCheck.fail(String.format(
                        "[elcore3] %s.%s: mode: mybatis searches the row and the rows it joins; %s is further"
                                + " than that - search by a column of %s or of a row it points at",
                        e.domainClassName(), e.getName(), cond[1], e.getName()));
            }
        }
        return out;
    }

    /** The conditions a large value can be searched by: LIKE, and whether it is empty. */
    static final java.util.Set<String> TEXT_MATCHES = new java.util.HashSet<String>(java.util.Arrays.asList(
            "like", "ilike", "prefix", "iprefix", "suffix", "isuffix", "auto", "isnull"));

    private static boolean lob(Path p) {
        return p.leaf != null && p.owner != null && MyBatisSql.isLob(p.owner, p.leaf);
    }

    /** One to-one relation read with the row. */
    static final class Join {
        final FieldSpec field;
        final EntitySpec target;
        final String alias;
        final String prefix;
        /** The row's column that holds the joined row's key. */
        final String key;

        Join(FieldSpec field, EntitySpec target, String alias, String prefix, String key) {
            this.field = field;
            this.target = target;
            this.alias = alias;
            this.prefix = prefix;
            this.key = key;
        }
    }

    /** Where an entity path lies: its column, the field it ends at and the entity that field is typed by. */
    static final class Path {
        final String column;
        final FieldSpec leaf;
        final EntitySpec owner;

        Path(String column, FieldSpec leaf, EntitySpec owner) {
            this.column = column;
            this.leaf = leaf;
            this.owner = owner;
        }
    }

    /**
     * The column an entity path reads, or null when it reaches none: the
     * row's own (name, embedded.name), a relation's key on the row
     * (relation.key), or a joined row's (relation.name,
     * relation.embedded.name).
     */
    static Path resolve(EntitySpec e, List<Join> joins, String path) {
        String[] seg = path.split("\\.");
        Path own = within(e, MyBatisSql.ROOT, seg, 0);
        if (own != null || seg.length < 2) return own;
        for (Join jn : joins) {
            if (!jn.field.name().equals(seg[0])) continue;
            if (seg.length == 2 && seg[1].equals(jn.target.pkField().name())) {
                return new Path(MyBatisSql.ROOT + "." + jn.key,
                        jn.target.pkField(), jn.target);
            }
            return within(jn.target, jn.alias, seg, 1);
        }
        return null;
    }

    /**
     * A value of one row, or a value of one of its embedded values, from
     * segment i on - typed by the row, which knows its enums and the
     * package they are in.
     */
    private static Path within(EntitySpec row, String alias, String[] seg, int i) {
        FieldSpec f = field(row, seg[i]);
        if (f == null) return null;
        if (seg.length == i + 1) {
            return MyBatisSql.isValue(row, f) ? new Path(alias + "." + MyBatisSql.column(f), f, row) : null;
        }
        if (seg.length == i + 2 && MyBatisSql.isEmbedded(row, f)) {
            for (MyBatisSql.Part p : MyBatisSql.parts(row, f)) {
                if (p.field.name().equals(seg[i + 1])) return new Path(alias + "." + p.column, p.field, row);
            }
        }
        return null;
    }

    /** The fields of the entities whose collections are mapped by them, which the runner handed over. */
    static List<FieldSpec> inbound(EntitySpec e) {
        List<FieldSpec> out = new ArrayList<FieldSpec>();
        String text = e.config(INBOUND_CONFIG);
        if (text == null || text.isEmpty()) return out;
        for (String name : text.split("\n")) {
            FieldSpec f = field(e, name.trim());
            if (f == null || !MyBatisSql.isJoined(e, f)) continue;
            EntitySpec root = e.related(f.coreItemType());
            if (root == null || root.pkField() == null) continue;
            out.add(f);
        }
        return out;
    }

    private static void statement(StringBuilder x, String kind, String id, String attrs, String sql) {
        x.append(I).append('<').append(kind).append(" id=\"").append(id).append('"')
         .append(attrs.isEmpty() ? "" : " " + attrs).append(">\n")
         .append(I).append(I).append(sql).append('\n')
         .append(I).append("</").append(kind).append(">\n\n");
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p);
        }
        return sb.toString();
    }

    private static String joinLines(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(",\n").append(I).append(I).append(I);
            sb.append(p);
        }
        return sb.toString();
    }

    /** A result mapping of a value column; an enum, Money or Quantity is read through its handler. */
    private static String result(EntitySpec e, FieldSpec f, String column) {
        StringBuilder sb = new StringBuilder("<result property=\"").append(f.name()).append("\" column=\"").append(column).append('"');
        if (MyBatisSql.isEnum(e, f)) {
            sb.append(" javaType=\"").append(enumType(e, f)).append("\" typeHandler=\"").append(MyBatisSql.ENUM_HANDLER).append('"');
        } else if (MyBatisSql.typeHandler(e, f) != null) {
            sb.append(" typeHandler=\"").append(MyBatisSql.typeHandler(e, f)).append('"');
        }
        return sb.append("/>").toString();
    }

    private static String enumType(EntitySpec e, FieldSpec f) {
        String simple = RuleText.simpleName(f.typeName());
        String pkg = e.pkg("enumeration");
        if (pkg == null || pkg.isEmpty()) pkg = e.pkg("entity");
        String known = RuleText.componentPackage(simple);
        return (known != null ? known : pkg) + "." + simple;
    }

    /** A bound parameter with its JDBC type, and the handler for an enum, Money or Quantity. */
    private static String param(EntitySpec e, FieldSpec f, String expression) {
        if (MyBatisSql.isEnum(e, f)) {
            return "#{" + expression + ",javaType=" + enumType(e, f) + ",typeHandler=" + MyBatisSql.ENUM_HANDLER
                    + ",jdbcType=VARCHAR}";
        }
        String handler = MyBatisSql.typeHandler(e, f);
        return "#{" + expression + (handler != null ? ",typeHandler=" + handler : "")
                + ",jdbcType=" + MyBatisSql.paramJdbcType(e, f) + "}";
    }

    /**
     * A search value: bound as the column's type when the search DTO holds
     * it in that type, and by its JDBC type alone when the DTO holds another
     * - a BigDecimal searching a Money column binds as a number, not through
     * the Money handler.
     */
    private static String searchParam(Path p, String type, String expression) {
        if (p.leaf == null || p.owner == null) return "#{" + expression + "}";
        boolean handled = MyBatisSql.typeHandler(p.owner, p.leaf) != null && !MyBatisSql.isEnum(p.owner, p.leaf);
        if (handled && !RuleText.simpleName(type).equals(RuleText.simpleName(p.leaf.typeName()))) {
            return "#{" + expression + ",jdbcType=" + MyBatisSql.paramJdbcType(p.owner, p.leaf) + "}";
        }
        return param(p.owner, p.leaf, expression);
    }

    private static FieldSpec field(EntitySpec e, String name) {
        for (FieldSpec f : e.fields()) if (f.name().equals(name)) return f;
        return null;
    }

    /** The search conditions the runner handed over: operator, path, property, type. */
    static List<String[]> conditions(EntitySpec e) {
        List<String[]> out = new ArrayList<String[]>();
        String text = e.config(SEARCH_CONFIG);
        if (text == null || text.isEmpty()) return out;
        for (String line : text.split("\n")) {
            String[] parts = line.split("\t");
            if (parts.length == 4) out.add(parts);
        }
        return out;
    }

    /** One condition of the WHERE clause, applied when its property has a value. */
    private static String condition(String[] cond, Path path, String bind) {
        String op = cond[0];
        String prop = "s." + cond[2];
        String column = path.column;
        boolean text = "String".equals(RuleText.simpleName(cond[3]));
        String I4 = I + I + I + I;
        String value = searchParam(path, cond[3], prop);
        StringBuilder sb = new StringBuilder();
        if ("between".equals(op)) {
            String from = prop + "From", to = prop + "To";
            sb.append(I4).append("<if test=\"").append(from).append(" != null\">AND ").append(column).append(" &gt;= ")
              .append(searchParam(path, cond[3], from)).append("</if>\n");
            sb.append(I4).append("<if test=\"").append(to).append(" != null\">AND ").append(column).append(" &lt;= ")
              .append(searchParam(path, cond[3], to)).append("</if>\n");
            return sb.toString();
        }
        if ("isnull".equals(op)) {
            String empty = prop + "Empty";
            sb.append(I4).append("<if test=\"").append(empty).append(" != null\"><choose><when test=\"").append(empty)
              .append("\">AND ").append(column).append(" IS NULL</when><otherwise>AND ").append(column)
              .append(" IS NOT NULL</otherwise></choose></if>\n");
            return sb.toString();
        }
        String test = "in".equals(op)
                ? prop + " != null and " + prop + ".size() &gt; 0"
                : text ? prop + " != null and " + prop + ".trim() != ''" : prop + " != null";
        sb.append(I4).append("<if test=\"").append(test).append("\">");
        String lower = "toLowerCase(@java.util.Locale@ROOT)";
        switch (op) {
            case "ne": sb.append("AND ").append(column).append(" &lt;&gt; ").append(value); break;
            case "gt": sb.append("AND ").append(column).append(" &gt; ").append(value); break;
            case "ge": sb.append("AND ").append(column).append(" &gt;= ").append(value); break;
            case "lt": sb.append("AND ").append(column).append(" &lt; ").append(value); break;
            case "le": sb.append("AND ").append(column).append(" &lt;= ").append(value); break;
            case "like":
                sb.append(bindLike(bind, "'%' + " + prop + ".trim() + '%'")).append("AND ").append(column).append(" LIKE #{").append(bind).append("}");
                break;
            case "ilike":
                sb.append(bindLike(bind, "'%' + " + prop + ".trim()." + lower + " + '%'")).append("AND LOWER(").append(column).append(") LIKE #{").append(bind).append("}");
                break;
            case "ieq":
                sb.append(bindLike(bind, prop + "." + lower)).append("AND LOWER(").append(column).append(") = #{").append(bind).append("}");
                break;
            case "prefix":
                sb.append(bindLike(bind, prop + ".trim() + '%'")).append("AND ").append(column).append(" LIKE #{").append(bind).append("}");
                break;
            case "iprefix":
                sb.append(bindLike(bind, prop + ".trim()." + lower + " + '%'")).append("AND LOWER(").append(column).append(") LIKE #{").append(bind).append("}");
                break;
            case "suffix":
                sb.append(bindLike(bind, "'%' + " + prop + ".trim()")).append("AND ").append(column).append(" LIKE #{").append(bind).append("}");
                break;
            case "isuffix":
                sb.append(bindLike(bind, "'%' + " + prop + ".trim()." + lower)).append("AND LOWER(").append(column).append(") LIKE #{").append(bind).append("}");
                break;
            case "in": {
                String item = searchParam(path, path.leaf != null ? path.leaf.typeName() : cond[3], "v");
                sb.append("AND ").append(column).append(" IN <foreach collection=\"").append(prop)
                  .append("\" item=\"v\" open=\"(\" separator=\",\" close=\")\">").append(item).append("</foreach>");
                break;
            }
            case "auto":
                if (lob(path)) {
                    // LIKE alone: without a % it matches the whole value, as = would.
                    sb.append("AND ").append(column).append(" LIKE ").append(value);
                } else if (text) {
                    sb.append("<choose><when test=\"").append(prop).append(".startsWith('%') or ").append(prop)
                      .append(".endsWith('%')\">AND ").append(column).append(" LIKE ").append(value)
                      .append("</when><otherwise>AND ").append(column).append(" = ").append(value).append("</otherwise></choose>");
                } else {
                    sb.append("AND ").append(column).append(" = ").append(value);
                }
                break;
            default:
                sb.append("AND ").append(column).append(" = ").append(value);
        }
        return sb.append("</if>\n").toString();
    }

    private static String bindLike(String name, String expression) {
        return "<bind name=\"" + name + "\" value=\"" + expression + "\"/>";
    }
}
