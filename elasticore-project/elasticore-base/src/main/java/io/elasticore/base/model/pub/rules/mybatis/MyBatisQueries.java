package io.elasticore.base.model.pub.rules.mybatis;

import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.ModelCheck;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.base.RepositoryBaseRules;
import io.elasticore.base.model.pub.rules.base.RuleText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The queries repository.yml declares for an entity, as MyBatis statements:
 * a method of the mapper interface under the name and signature the DSL gives
 * - the one Spring Data would have - and the statement in the XML.
 *
 * <ul>
 *   <li>A method without a query is read from its name, as Spring Data reads
 *       it: find/read/get/query/search/stream, count, exists and delete,
 *       First/Top n, Distinct, conditions joined by And and Or (Is, Equals,
 *       Not, LessThan, LessThanEqual, GreaterThan, GreaterThanEqual, Before,
 *       After, Between, IsNull, IsNotNull, Like, NotLike, StartingWith,
 *       EndingWith, Containing, NotContaining, In, NotIn, True, False,
 *       IgnoreCase, AllIgnoreCase) and OrderBy. A property is a field of the
 *       row, of one of its embedded values, or of a row it joins - the paths a
 *       search reaches. A page (pageable: true) is a count and a page of rows,
 *       sorted by the Pageable after the name's own order.</li>
 *   <li>A method with nativeQuery: true runs its SQL as written, binding
 *       :name (or ?1) to the parameter of that name (or place). Rows of the
 *       entity are read whole again by their keys, in the order the query
 *       gave them, so the rows they join come with them; a DTO is filled by
 *       column label; anything else is the first column. An UPDATE, DELETE or
 *       INSERT answers the rows it changed.</li>
 *   <li>JPQL is refused: MyBatis runs SQL, and a query that is neither would
 *       be left out without a word.</li>
 * </ul>
 */
public final class MyBatisQueries {

    private MyBatisQueries() {
    }

    private static final String I = "    ";

    /** What a statement answers with. */
    enum Kind { ROWS, COUNT, EXISTS, DELETE, NATIVE_ROWS, NATIVE_DTO, NATIVE_VALUE, NATIVE_WRITE }

    /** One condition of a name: the property's path, the operator, its parameters and whether case is ignored. */
    static final class Condition {
        String path;
        String op;
        boolean ignoreCase;
        final List<Param> params = new ArrayList<Param>();
    }

    /** A parameter of the method: its name and its type as the DSL wrote it. */
    static final class Param {
        final String name;
        final String type;

        Param(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    /** One declared query, read. */
    static final class Query {
        String name;
        String returnType;
        /** list, optional, one, page or value - how the answer is held. */
        String container;
        /** The entity, a DTO, or a value type - what one row of the answer is. */
        String item;
        Kind kind;
        final List<Param> params = new ArrayList<Param>();
        boolean pageable;
        String doc;
        // read from the name
        boolean distinct;
        Integer top;
        final List<List<Condition>> or = new ArrayList<List<Condition>>();
        final List<String[]> orderBy = new ArrayList<String[]>();
        // written
        String sql;
    }

    // ---- reading the DSL ---------------------------------------------------------

    /** The declared queries of the entity, read; one MyBatis cannot run stops the build, named. */
    static List<Query> of(EntitySpec e) {
        List<Query> out = new ArrayList<Query>();
        for (io.elasticore.base.model.repo.Method m : e.queryMethods()) {
            if (!m.isEnable()) continue;
            // A conditional query is assembled at runtime by a dbms port, not declared.
            if (m.getQuery() != null && m.isNeedQueryAnnotation() && m.getQuery().indexOf("/* if:") > 0) continue;
            Query q = read(e, m);
            if (q != null) out.add(q);
        }
        return out;
    }

    private static Query read(EntitySpec e, io.elasticore.base.model.repo.Method m) {
        Query q = new Query();
        q.name = m.getName();
        q.doc = m.getMetaInfo() == null ? null : m.getDescription();
        io.elasticore.base.model.core.Items<io.elasticore.base.model.entity.Field> ps = m.getParams();
        if (ps != null) {
            for (io.elasticore.base.model.entity.Field f : ps.getItemList()) {
                q.params.add(new Param(f.getName(), f.getTypeInfo().getDefaultTypeName()));
            }
        }
        String declared = m.getReturnType();
        if (declared == null || declared.trim().isEmpty()) declared = "void";
        String query = m.getQuery();
        boolean hasQuery = query != null && !query.trim().isEmpty();
        if (hasQuery && !m.isNative()) {
            fail(e, q, "is JPQL, and mode: mybatis runs SQL - write it in the database's SQL and set nativeQuery: true"
                    + " (or drop the query and let the name say it)");
            return null;
        }
        // The shapes the JPA port gives the same declaration, so the ports agree.
        declared = RepositoryBaseRules.pageableOrList(e, m, declared.trim());
        q.returnType = declared;
        if (declared.matches("(java\\.util\\.stream\\.)?Stream<.*")) {
            fail(e, q, "answers a Stream, which a MyBatis mapper does not return; answer a List");
            return null;
        }
        String arg = RepositoryBaseRules.typeArgument(declared);
        q.container = declared.startsWith("Page<") ? "page"
                : declared.startsWith("Optional<") ? "optional"
                : declared.startsWith("List<") || declared.startsWith("java.util.List<")
                  || declared.startsWith("Collection<") || declared.startsWith("Set<") ? "list"
                : "one";
        q.item = arg != null ? arg : declared;
        q.pageable = !m.isNative() && m.isPageable();
        boolean root = e.getName().equals(q.item);
        boolean dto = e.isKnownDto(q.item);

        if (hasQuery) {
            q.sql = query.trim();
            boolean select = m.getQueryInfo() == null || m.getQueryInfo().isSelectQuery();
            if (!select) {
                if (!isCount(declared) && !"void".equals(declared)) {
                    fail(e, q, "changes rows; it answers how many (int, long) or nothing (void), not " + declared);
                    return null;
                }
                q.kind = Kind.NATIVE_WRITE;
            } else if ("page".equals(q.container)) {
                fail(e, q, "is a native query answering a Page, which MyBatis cannot count; answer a List"
                        + " and page it in the SQL, or let the name say the query");
                return null;
            } else if (root) {
                q.kind = Kind.NATIVE_ROWS;
                if (e.pkField() == null) {
                    fail(e, q, "answers rows of " + e.getName() + ", which has no key to read them whole by");
                    return null;
                }
            } else if (dto) {
                q.kind = Kind.NATIVE_DTO;
            } else if (e.isKnownEntity(q.item)) {
                fail(e, q, "answers rows of " + q.item + "; declare it on " + q.item + " in repository.yml");
                return null;
            } else {
                q.kind = Kind.NATIVE_VALUE;
            }
            for (Param p : q.params) {
                if (isCollection(p.type) && collectionItem(p.type) == null) {
                    fail(e, q, "has a parameter " + p.name + " of type " + p.type + " whose items the SQL cannot bind");
                    return null;
                }
            }
            return q;
        }
        return derived(e, m, q, root, dto) ? q : null;
    }

    private static final Pattern SUBJECT = Pattern.compile(
            "^(find|read|get|query|search|stream|count|exists|delete|remove)(.*?)By(.+)$");

    /**
     * Whether a declared deleteBy... of an aggregate root deletes row by row:
     * where the domain rules bind aggregates, the adapter reads the rows it
     * names and deletes each as delete() does - its delete guard, its members,
     * then its row - as Spring Data JPA's derived delete removes each entity.
     * Elsewhere it is one DELETE statement.
     */
    public static boolean deletesRowByRow(EntitySpec e, String methodName) {
        if (!MyBatisSql.aggregatesBound() || !e.aggregateRoot() || e.pkField() == null) return false;
        for (io.elasticore.base.model.repo.Method m : e.queryMethods()) {
            if (!m.isEnable() || !m.getName().equals(methodName)) continue;
            if (m.getQuery() != null && !m.getQuery().trim().isEmpty()) return false;
            Matcher mt = SUBJECT.matcher(methodName);
            return mt.matches() && ("delete".equals(mt.group(1)) || "remove".equals(mt.group(1)));
        }
        return false;
    }

    /** A query read from the method's name, as Spring Data's PartTree reads it. */
    private static boolean derived(EntitySpec e, io.elasticore.base.model.repo.Method m, Query q,
                                   boolean root, boolean dto) {
        Matcher mt = SUBJECT.matcher(q.name);
        if (!mt.matches()) {
            fail(e, q, "has no query, and its name is not one a query is read from (findBy..., countBy...,"
                    + " existsBy..., deleteBy...)");
            return false;
        }
        String verb = mt.group(1);
        String subject = mt.group(2);
        String rest = mt.group(3);
        q.distinct = subject.contains("Distinct");
        Matcher topm = Pattern.compile("(First|Top)(\\d*)").matcher(subject);
        if (topm.find()) q.top = topm.group(2).isEmpty() ? 1 : Integer.parseInt(topm.group(2));

        if ("count".equals(verb)) {
            q.kind = Kind.COUNT;
            if (!isCount(q.returnType)) {
                fail(e, q, "counts; it answers long or int, not " + q.returnType);
                return false;
            }
        } else if ("exists".equals(verb)) {
            q.kind = Kind.EXISTS;
            if (!"boolean".equals(q.returnType) && !"Boolean".equals(q.returnType)) {
                fail(e, q, "asks whether one exists; it answers boolean, not " + q.returnType);
                return false;
            }
        } else if ("delete".equals(verb) || "remove".equals(verb)) {
            q.kind = Kind.DELETE;
            if (!isCount(q.returnType) && !"void".equals(q.returnType)) {
                fail(e, q, "deletes rows; it answers how many (int, long) or nothing (void), not " + q.returnType);
                return false;
            }
            for (FieldSpec f : MyBatisSql.collections(e)) {
                if (deletesRowByRow(e, q.name)) break;
                if (MyBatisSql.isMemberCollection(e, f)) {
                    fail(e, q, "deletes rows of " + e.getName() + ", whose members (" + f.name() + ") a DELETE would"
                            + " leave behind - delete each through the service, which deletes its members first");
                    return false;
                }
            }
        } else {
            q.kind = Kind.ROWS;
            if (!root) {
                fail(e, q, dto ? "answers " + q.item + ", and a query read from a name answers rows of "
                                + e.getName() + " - give it a nativeQuery that selects the DTO's columns"
                        : "answers " + q.returnType + "; a query read from a name answers rows of " + e.getName());
                return false;
            }
            if ("page".equals(q.container) && !q.pageable) {
                fail(e, q, "answers a Page; say pageable: true so it takes the Pageable");
                return false;
            }
            if (q.pageable && !MyBatisMapperRules.serviced(e)) {
                fail(e, q, "is paged, and " + e.getName() + " has no service of its own to sort by");
                return false;
            }
        }

        String predicate = rest;
        int order = lastCamelIndex(predicate, "OrderBy");
        String orderPart = null;
        if (order >= 0) {
            orderPart = predicate.substring(order + "OrderBy".length());
            predicate = predicate.substring(0, order);
        }
        boolean allIgnoreCase = false;
        for (String suffix : new String[]{"AllIgnoreCase", "AllIgnoringCase"}) {
            if (predicate.endsWith(suffix)) {
                allIgnoreCase = true;
                predicate = predicate.substring(0, predicate.length() - suffix.length());
            }
        }
        int next = 0;
        if (predicate.isEmpty() && q.kind != Kind.ROWS && q.kind != Kind.COUNT) {
            fail(e, q, "names no condition, and would " + (q.kind == Kind.DELETE ? "delete" : "ask about")
                    + " every row; say the condition (deleteByStatus, existsByEmail)");
            return false;
        }
        for (String orPart : predicate.isEmpty() ? java.util.Collections.<String>emptyList() : splitCamel(predicate, "Or")) {
            List<Condition> group = new ArrayList<Condition>();
            for (String part : splitCamel(orPart, "And")) {
                Condition c = condition(e, q, part, allIgnoreCase);
                if (c == null) return false;
                int arity = arity(c.op);
                for (int i = 0; i < arity; i++) {
                    if (next >= q.params.size()) {
                        fail(e, q, "needs a parameter for " + part + " that params does not give");
                        return false;
                    }
                    c.params.add(q.params.get(next++));
                }
                group.add(c);
            }
            q.or.add(group);
        }
        if (next < q.params.size()) {
            fail(e, q, "has " + q.params.size() + " parameters and its name reads " + next);
            return false;
        }
        if (orderPart != null) {
            Matcher om = Pattern.compile("([A-Z][A-Za-z0-9_]*?)(Asc|Desc)(?=[A-Z]|$)").matcher(orderPart);
            int at = 0;
            while (om.find()) {
                if (om.start() != at) break;
                q.orderBy.add(new String[]{property(e, q, om.group(1)), om.group(2)});
                at = om.end();
            }
            if (at != orderPart.length()) {
                // The last one may leave its direction unsaid: ascending.
                String tail = orderPart.substring(at);
                if (!tail.isEmpty()) q.orderBy.add(new String[]{property(e, q, tail), "Asc"});
            }
            for (String[] o : q.orderBy) {
                if (o[0] == null) return false;
            }
        }
        return true;
    }

    /** The operators of a condition, longest first, by the name Spring Data gives them. */
    private static final String[][] OPERATORS = {
            {"IsNotNull", "notnull"}, {"NotNull", "notnull"}, {"IsNull", "null"}, {"Null", "null"},
            {"IsLessThanEqual", "le"}, {"LessThanEqual", "le"}, {"IsLessThan", "lt"}, {"LessThan", "lt"},
            {"IsGreaterThanEqual", "ge"}, {"GreaterThanEqual", "ge"}, {"IsGreaterThan", "gt"}, {"GreaterThan", "gt"},
            {"IsBefore", "lt"}, {"Before", "lt"}, {"IsAfter", "gt"}, {"After", "gt"},
            {"IsBetween", "between"}, {"Between", "between"},
            {"IsNotLike", "notlike"}, {"NotLike", "notlike"}, {"IsLike", "like"}, {"Like", "like"},
            {"IsStartingWith", "prefix"}, {"StartingWith", "prefix"}, {"StartsWith", "prefix"},
            {"IsEndingWith", "suffix"}, {"EndingWith", "suffix"}, {"EndsWith", "suffix"},
            {"IsNotContaining", "notcontains"}, {"NotContaining", "notcontains"}, {"NotContains", "notcontains"},
            {"IsContaining", "contains"}, {"Containing", "contains"}, {"Contains", "contains"},
            {"IsNotIn", "notin"}, {"NotIn", "notin"}, {"IsIn", "in"}, {"In", "in"},
            {"IsTrue", "true"}, {"True", "true"}, {"IsFalse", "false"}, {"False", "false"},
            {"IsNot", "ne"}, {"Not", "ne"}, {"Is", "eq"}, {"Equals", "eq"},
    };

    private static Condition condition(EntitySpec e, Query q, String part, boolean allIgnoreCase) {
        Condition c = new Condition();
        String p = part;
        for (String suffix : new String[]{"IgnoreCase", "IgnoringCase"}) {
            if (p.endsWith(suffix)) {
                c.ignoreCase = true;
                p = p.substring(0, p.length() - suffix.length());
            }
        }
        c.op = "eq";
        for (String[] op : OPERATORS) {
            if (p.endsWith(op[0]) && p.length() > op[0].length()) {
                // "Is" or "Not" closing a property name of its own (an "isbn") is the name.
                String before = p.substring(0, p.length() - op[0].length());
                if (property(e, null, p) != null && property(e, null, before) == null) break;
                c.op = op[1];
                p = before;
                break;
            }
        }
        c.ignoreCase = c.ignoreCase || (allIgnoreCase && !"null".equals(c.op) && !"notnull".equals(c.op));
        c.path = property(e, q, p);
        return c.path == null ? null : c;
    }

    private static int arity(String op) {
        switch (op) {
            case "null": case "notnull": case "true": case "false": return 0;
            case "between": return 2;
            default: return 1;
        }
    }

    /**
     * The path of a property a name spells - CustomerName is customer.name
     * when the entity has no customerName - against the row, its embedded
     * values and the rows it joins. Null, said when q is given, when none.
     */
    static String property(EntitySpec e, Query q, String camel) {
        List<MyBatisMapperRules.Join> joins = MyBatisMapperRules.joins(e);
        for (String path : candidates(camel.replace("_", ""), e)) {
            if (MyBatisMapperRules.resolve(e, joins, path) != null || relation(e, path) != null) return path;
        }
        if (q != null) {
            fail(e, q, "reads a property " + RuleText.uncapital(camel) + " that is not a column of " + e.getName()
                    + " nor of a row it joins");
        }
        return null;
    }

    /** The relation a path names, when it names one the row holds the key of. */
    private static FieldSpec relation(EntitySpec e, String path) {
        if (path.contains(".")) return null;
        for (FieldSpec f : MyBatisSql.fields(e)) {
            if (f.name().equals(path) && MyBatisSql.holdsKey(e, f)) return f;
        }
        return null;
    }

    /** The dotted paths a camel-case name may mean: the whole name first, then split after each field it starts with. */
    private static List<String> candidates(String camel, EntitySpec e) {
        List<String> out = new ArrayList<String>();
        if (camel.isEmpty()) return out;
        String whole = RuleText.uncapital(camel);
        out.add(whole);
        for (FieldSpec f : MyBatisSql.fields(e)) {
            String cap = RuleText.capital(f.name());
            if (camel.length() > cap.length() && camel.startsWith(cap)
                    && Character.isUpperCase(camel.charAt(cap.length()))) {
                String tail = camel.substring(cap.length());
                EntitySpec t = e.related(f.coreItemType());
                if (t != null) {
                    for (String sub : candidates(tail, t)) out.add(f.name() + "." + sub);
                } else {
                    out.add(f.name() + "." + RuleText.uncapital(tail));
                }
            }
        }
        return out;
    }

    /** Splits a name at a keyword that stands between two capitalised words (And, Or). */
    private static List<String> splitCamel(String text, String keyword) {
        List<String> out = new ArrayList<String>();
        int from = 0;
        for (int i = 1; i + keyword.length() < text.length(); i++) {
            if (text.startsWith(keyword, i) && Character.isUpperCase(text.charAt(i + keyword.length()))
                    && (Character.isLowerCase(text.charAt(i - 1)) || Character.isDigit(text.charAt(i - 1)))) {
                out.add(text.substring(from, i));
                from = i + keyword.length();
                i = from;
            }
        }
        out.add(text.substring(from));
        return out;
    }

    /** Where a keyword starts a capitalised word, the last time; 0 too - findAllByOrderByX orders all. */
    private static int lastCamelIndex(String text, String keyword) {
        int i = text.lastIndexOf(keyword);
        while (i >= 0) {
            boolean after = i + keyword.length() < text.length() && Character.isUpperCase(text.charAt(i + keyword.length()));
            if (after) return i;
            i = text.lastIndexOf(keyword, i - 1);
        }
        return -1;
    }

    private static boolean isCount(String type) {
        return Arrays.asList("long", "Long", "int", "Integer").contains(type == null ? "" : type.trim());
    }

    private static boolean isCollection(String type) {
        String t = type == null ? "" : type.trim();
        return t.startsWith("List<") || t.startsWith("Collection<") || t.startsWith("Set<")
                || t.startsWith("java.util.List<") || t.startsWith("java.util.Collection<");
    }

    private static String collectionItem(String type) {
        return RepositoryBaseRules.typeArgument(type);
    }

    private static void fail(EntitySpec e, Query q, String why) {
        ModelCheck.fail(String.format("[elcore3] %s repository %s.%s %s", e.domainClassName(), e.getName(), q.name, why));
    }

    // ---- the mapper interface ----------------------------------------------------

    /** Whether one of the queries reads rows whole again by their keys. */
    static boolean readsWhole(List<Query> queries) {
        for (Query q : queries) if (q.kind == Kind.NATIVE_ROWS) return true;
        return false;
    }

    /** The interface's methods for the queries, and the imports they need. */
    static void interfaceLines(SourceCode c, EntitySpec e, List<Query> queries) {
        c.slot("declaredQueries");
        Set<String> imports = new LinkedHashSet<String>();
        String entity = e.getName();
        String keyType = e.pkField() == null ? "Object" : c.syntax().nullableType(e.pkField().typeName());
        for (Query q : queries) {
            for (Param p : q.params) importsOf(e, p.type, imports);
            importsOf(e, q.returnType, imports);
            String params = javaParams(q.params, true);
            String args = args(q.params);
            String doc = q.doc != null && !q.doc.isEmpty() ? q.doc : "Declared in the repository DSL.";
            c.varLine("declaredQueries", "");
            c.varLine("declaredQueries", "/** %s */", doc);
            switch (q.kind) {
                case ROWS: {
                    if ("page".equals(q.container)) {
                        imports.add("org.springframework.data.domain.Page");
                        imports.add("org.springframework.data.domain.PageImpl");
                        imports.add("org.springframework.data.domain.Pageable");
                        c.varLine("declaredQueries", "default %s %s(%s) {", q.returnType, q.name,
                                plainParams(q.params, "Pageable pageable"))
                         .varLine("declaredQueries", "    if (pageable.isUnpaged()) {")
                         .varLine("declaredQueries", "        List<%s> all = %sRows(%s);", entity, q.name,
                                 join(args, "orderBy(pageable.getSort()), null, null"))
                         .varLine("declaredQueries", "        return new PageImpl<>(all, pageable, all.size());")
                         .varLine("declaredQueries", "    }")
                         .varLine("declaredQueries", "    long total = %sCount(%s);", q.name, args)
                         .varLine("declaredQueries", "    List<%s> rows = total <= pageable.getOffset() ? Collections.<%s>emptyList()",
                                 entity, entity)
                         .varLine("declaredQueries", "            : %sRows(%s);", q.name,
                                 join(args, "orderBy(pageable.getSort()), pageable.getOffset(), pageable.getPageSize()"))
                         .varLine("declaredQueries", "    return new PageImpl<>(rows, pageable, total);")
                         .varLine("declaredQueries", "}")
                         .varLine("declaredQueries", "")
                         .varLine("declaredQueries", "List<%s> %sRows(%s);", entity, q.name, join(params,
                                 "@Param(\"orderBy\") String orderBy, @Param(\"offset\") Long offset, @Param(\"limit\") Integer limit"))
                         .varLine("declaredQueries", "")
                         .varLine("declaredQueries", "long %sCount(%s);", q.name, params);
                    } else if (q.pageable) {
                        imports.add("org.springframework.data.domain.Pageable");
                        // A list that takes a Pageable: the page of rows, without the count.
                        c.varLine("declaredQueries", "default %s %s(%s) {", q.returnType, q.name,
                                plainParams(q.params, "Pageable pageable"))
                         .varLine("declaredQueries", "    return pageable.isUnpaged() ? %sRows(%s)", q.name,
                                 join(args, "orderBy(pageable.getSort()), null, null"))
                         .varLine("declaredQueries", "            : %sRows(%s);", q.name,
                                 join(args, "orderBy(pageable.getSort()), pageable.getOffset(), pageable.getPageSize()"))
                         .varLine("declaredQueries", "}")
                         .varLine("declaredQueries", "")
                         .varLine("declaredQueries", "List<%s> %sRows(%s);", entity, q.name, join(params,
                                 "@Param(\"orderBy\") String orderBy, @Param(\"offset\") Long offset, @Param(\"limit\") Integer limit"));
                    } else {
                        c.varLine("declaredQueries", "%s %s(%s);", q.returnType, q.name, params);
                    }
                    break;
                }
                case DELETE:
                    if (deletesRowByRow(e, q.name)) {
                        // The rows it names: the adapter deletes each one whole.
                        c.varLine("declaredQueries", "List<%s> %sRows(%s);", entity, q.name, params);
                        break;
                    }
                    c.varLine("declaredQueries", "%s %s(%s);", q.returnType, q.name, params);
                    break;
                case COUNT:
                    c.varLine("declaredQueries", "%s %s(%s);", q.returnType, q.name, params);
                    break;
                case EXISTS:
                    c.varLine("declaredQueries", "default %s %s(%s) {", q.returnType, q.name, plainParams(q.params, null))
                     .varLine("declaredQueries", "    return %sCount(%s) > 0;", q.name, args)
                     .varLine("declaredQueries", "}")
                     .varLine("declaredQueries", "")
                     .varLine("declaredQueries", "long %sCount(%s);", q.name, params);
                    break;
                case NATIVE_ROWS: {
                    String rows = String.format("whole(%sRows(%s))", q.name, args);
                    c.varLine("declaredQueries", "default %s %s(%s) {", q.returnType, q.name, plainParams(q.params, null));
                    if ("list".equals(q.container)) {
                        c.varLine("declaredQueries", "    return %s;", rows);
                    } else {
                        c.varLine("declaredQueries", "    List<%s> rows = %s;", entity, rows)
                         .varLine("declaredQueries", "    if (rows.size() > 1)")
                         .varLine("declaredQueries", "        throw new org.springframework.dao.IncorrectResultSizeDataAccessException(1, rows.size());")
                         .varLine("declaredQueries", "    return %s;", "optional".equals(q.container)
                                 ? "rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0))"
                                 : "rows.isEmpty() ? null : rows.get(0)");
                    }
                    c.varLine("declaredQueries", "}")
                     .varLine("declaredQueries", "")
                     .varLine("declaredQueries", "List<%s> %sRows(%s);", entity, q.name, params);
                    break;
                }
                default:
                    c.varLine("declaredQueries", "%s %s(%s);", q.returnType, q.name, params);
            }
        }
        if (readsWhole(queries)) {
            imports.add("java.util.ArrayList");
            c.varLine("declaredQueries", "")
             .varLine("declaredQueries", "/** The rows of these keys, each read whole with the rows it joins. */")
             .varLine("declaredQueries", "List<%s> findAllByIdIn(@Param(\"keys\") java.util.Collection<%s> keys);", entity, keyType)
             .varLine("declaredQueries", "")
             .varLine("declaredQueries", "/** Rows a native query read, read again whole by their keys, in the order it gave them. */")
             .varLine("declaredQueries", "default List<%s> whole(List<%s> rows) {", entity, entity)
             .varLine("declaredQueries", "    Map<%s, %s> byKey = new LinkedHashMap<>();", keyType, entity)
             .varLine("declaredQueries", "    List<%s> keys = new ArrayList<>();", keyType)
             .varLine("declaredQueries", "    for (%s row : rows) if (row != null && row.%s() != null) keys.add(row.%s());", entity,
                     getter(e.pkField()), getter(e.pkField()))
             .varLine("declaredQueries", "    for (int from = 0; from < keys.size(); from += 500) {")
             .varLine("declaredQueries", "        for (%s row : findAllByIdIn(keys.subList(from, Math.min(keys.size(), from + 500))))", entity)
             .varLine("declaredQueries", "            byKey.put(row.%s(), row);", getter(e.pkField()))
             .varLine("declaredQueries", "    }")
             .varLine("declaredQueries", "    List<%s> out = new ArrayList<>();", entity)
             .varLine("declaredQueries", "    for (%s key : keys) if (byKey.containsKey(key)) out.add(byKey.get(key));", keyType)
             .varLine("declaredQueries", "    return out;")
             .varLine("declaredQueries", "}");
        }
        if (!queries.isEmpty()) {
            imports.add("java.util.Collections");
            imports.add("java.util.LinkedHashMap");
            imports.add("java.util.Map");
        }
        // The serviced mapper's template imports these already.
        if (MyBatisMapperRules.serviced(e)) {
            imports.removeAll(Arrays.asList("java.util.Collections", "java.util.LinkedHashMap", "java.util.Map",
                    "java.util.List", "java.util.Optional", "org.springframework.data.domain.Sort"));
        }
        imports.removeAll(Arrays.asList("java.util.List", "java.util.Optional"));
        StringBuilder sb = new StringBuilder();
        for (String imp : imports) sb.append("\nimport ").append(imp).append(';');
        c.var("queryImportList", sb.toString());
    }

    private static String getter(FieldSpec f) {
        return "get" + RuleText.capital(f.name());
    }

    private static void importsOf(EntitySpec e, String type, Set<String> imports) {
        if (type == null) return;
        for (String t : type.replace("<", " ").replace(">", " ").replace(",", " ").split("\\s+")) {
            if (t.isEmpty() || t.contains(".") || !Character.isUpperCase(t.charAt(0))) continue;
            switch (t) {
                case "BigDecimal": case "BigInteger": imports.add("java.math." + t); break;
                case "LocalDate": case "LocalDateTime": case "LocalTime": case "Instant": case "OffsetDateTime":
                case "ZonedDateTime": imports.add("java.time." + t); break;
                case "UUID": case "Date": case "Collection": case "Set": imports.add("java.util." + t); break;
                case "Page": case "Pageable": imports.add("org.springframework.data.domain." + t); break;
                case "List": case "Optional": case "Map": case "String": case "Long": case "Integer": case "Boolean":
                case "Double": case "Float": case "Short": case "Byte": case "Object": case "Character":
                    break;
                default: {
                    String pkg = RuleText.componentPackage(t);
                    if (pkg != null && !pkg.isEmpty()) imports.add(pkg + "." + t);
                }
            }
        }
    }

    private static String javaParams(List<Param> params, boolean annotated) {
        StringBuilder sb = new StringBuilder();
        for (Param p : params) {
            if (sb.length() > 0) sb.append(", ");
            if (annotated) sb.append("@Param(\"").append(p.name).append("\") ");
            sb.append(p.type).append(' ').append(p.name);
        }
        return sb.toString();
    }

    private static String plainParams(List<Param> params, String extra) {
        String s = javaParams(params, false);
        return extra == null ? s : join(s, extra);
    }

    private static String args(List<Param> params) {
        StringBuilder sb = new StringBuilder();
        for (Param p : params) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.name);
        }
        return sb.toString();
    }

    private static String join(String a, String b) {
        if (a == null || a.isEmpty()) return b;
        if (b == null || b.isEmpty()) return a;
        return a + ", " + b;
    }

    // ---- the statements ----------------------------------------------------------

    /** The XML statements of the queries. */
    static void statements(StringBuilder x, EntitySpec e, List<MyBatisMapperRules.Join> joins, List<Query> queries) {
        String table = MyBatisSql.table(e);
        String select = "SELECT <include refid=\"columns\"/> FROM <include refid=\"from\"/>";
        FieldSpec pk = e.pkField();
        for (Query q : queries) {
            switch (q.kind) {
                case ROWS: {
                    String where = where(e, joins, q, true);
                    String order = order(e, joins, q);
                    String head = q.distinct ? select.replace("SELECT ", "SELECT DISTINCT ") : select;
                    if (q.pageable) {
                        statement(x, "select", q.name + "Rows", "resultMap=\"row\"",
                                head + where + " " + order + " " + PAGE);
                        if ("page".equals(q.container)) {
                            statement(x, "select", q.name + "Count", "resultType=\"long\"",
                                    "SELECT COUNT(" + (q.distinct ? "DISTINCT " + MyBatisSql.ROOT + "." + MyBatisSql.column(pk) : "*")
                                            + ") FROM <include refid=\"from\"/>" + where);
                        }
                    } else {
                        statement(x, "select", q.name, "resultMap=\"row\"",
                                (head + where + " " + order).trim() + (q.top != null ? " " + top(q.top) : ""));
                    }
                    break;
                }
                case COUNT:
                    statement(x, "select", q.name, "resultType=\"" + countType(q.returnType) + "\"",
                            "SELECT COUNT(" + (q.distinct && pk != null ? "DISTINCT " + MyBatisSql.ROOT + "." + MyBatisSql.column(pk) : "*")
                                    + ") FROM <include refid=\"from\"/>" + where(e, joins, q, true));
                    break;
                case EXISTS:
                    statement(x, "select", q.name + "Count", "resultType=\"long\"",
                            "SELECT COUNT(*) FROM <include refid=\"from\"/>" + where(e, joins, q, true));
                    break;
                case DELETE:
                    if (deletesRowByRow(e, q.name)) {
                        statement(x, "select", q.name + "Rows", "resultMap=\"row\"", select + where(e, joins, q, true)
                                + " ORDER BY " + MyBatisSql.ROOT + "." + MyBatisSql.column(pk));
                        break;
                    }
                    statement(x, "delete", q.name, "",
                            "DELETE FROM " + table + where(e, joins, q, false));
                    break;
                case NATIVE_ROWS:
                    statement(x, "select", q.name + "Rows", "resultMap=\"row\"", nativeSql(e, q));
                    break;
                case NATIVE_DTO:
                    statement(x, "select", q.name, "resultType=\"" + dtoType(e, q.item) + "\"", nativeSql(e, q));
                    break;
                case NATIVE_VALUE:
                    statement(x, "select", q.name, "resultType=\"" + valueType(e, q.item) + "\"", nativeSql(e, q));
                    break;
                case NATIVE_WRITE: {
                    String verb = q.sql.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
                    String kind = "insert".equals(verb) ? "insert" : "delete".equals(verb) ? "delete" : "update";
                    statement(x, kind, q.name, "", nativeSql(e, q));
                    break;
                }
                default:
            }
        }
        if (readsWhole(queries) && pk != null) {
            statement(x, "select", "findAllByIdIn", "resultMap=\"row\"",
                    select + " WHERE " + MyBatisSql.ROOT + "." + MyBatisSql.column(pk)
                            + " IN <foreach collection=\"keys\" item=\"k\" open=\"(\" separator=\",\" close=\")\">#{k,jdbcType="
                            + MyBatisSql.paramJdbcType(e, pk) + "}</foreach>");
        }
    }

    /** The page of a paged query, as the search's page is written. */
    private static final String PAGE = "<if test=\"limit != null\"><choose>"
            + "<when test=\"_databaseId == 'oracle' or _databaseId == 'sqlserver' or _databaseId == 'db2'\">"
            + "OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY</when>"
            + "<otherwise>LIMIT #{limit} OFFSET #{offset}</otherwise></choose></if>";

    private static String top(int n) {
        return "<choose><when test=\"_databaseId == 'oracle' or _databaseId == 'sqlserver' or _databaseId == 'db2'\">"
                + "OFFSET 0 ROWS FETCH NEXT " + n + " ROWS ONLY</when><otherwise>LIMIT " + n + "</otherwise></choose>";
    }

    /** The WHERE clause of a name's conditions; a DELETE names the table's own columns, without the alias. */
    private static String where(EntitySpec e, List<MyBatisMapperRules.Join> joins, Query q, boolean aliased) {
        if (q.or.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" WHERE ");
        boolean grouped = q.or.size() > 1;
        for (int g = 0; g < q.or.size(); g++) {
            if (g > 0) sb.append(" OR ");
            if (grouped) sb.append('(');
            List<Condition> group = q.or.get(g);
            for (int i = 0; i < group.size(); i++) {
                if (i > 0) sb.append(" AND ");
                sb.append(condition(e, joins, q, group.get(i), aliased));
            }
            if (grouped) sb.append(')');
        }
        return sb.toString();
    }

    private static String condition(EntitySpec e, List<MyBatisMapperRules.Join> joins, Query q, Condition c,
                                    boolean aliased) {
        String column;
        String value;
        String second = null;
        FieldSpec rel = relation(e, c.path);
        MyBatisMapperRules.Path path = MyBatisMapperRules.resolve(e, joins, c.path);
        if (path == null && rel != null) {
            // The relation itself: its key column against the key of the row given.
            EntitySpec target = e.related(rel.coreItemType());
            column = MyBatisSql.ROOT + "." + MyBatisSql.keyColumn(e, rel, target);
            Param p = c.params.isEmpty() ? null : c.params.get(0);
            value = p == null ? null : "#{" + p.name + "." + target.pkField().name() + ",jdbcType="
                    + MyBatisSql.paramJdbcType(target, target.pkField()) + "}";
            if (!"eq".equals(c.op) && !"ne".equals(c.op) && !"null".equals(c.op) && !"notnull".equals(c.op)) {
                fail(e, q, "compares the relation " + c.path + " by " + c.op + "; a relation is compared by its key only");
            }
        } else {
            column = path.column;
            value = c.params.isEmpty() ? null : param(path, c.params.get(0));
            if (c.params.size() > 1) second = param(path, c.params.get(1));
        }
        if (!aliased) {
            if (!column.startsWith(MyBatisSql.ROOT + ".")) {
                fail(e, q, "deletes by " + c.path + ", a column of a row it joins; a DELETE reads its own row only");
                return "1 = 0";
            }
            column = column.substring(MyBatisSql.ROOT.length() + 1);
        }
        String col = c.ignoreCase ? "LOWER(" + column + ")" : column;
        String lower = c.ignoreCase && value != null ? "LOWER(" + value + ")" : value;
        Param p = c.params.isEmpty() ? null : c.params.get(0);
        switch (c.op) {
            case "null": return column + " IS NULL";
            case "notnull": return column + " IS NOT NULL";
            case "true": return "<bind name=\"qTrue\" value=\"true\"/>" + column + " = #{qTrue,jdbcType=BOOLEAN}";
            case "false": return "<bind name=\"qFalse\" value=\"false\"/>" + column + " = #{qFalse,jdbcType=BOOLEAN}";
            case "ne": return col + " &lt;&gt; " + lower;
            case "lt": return column + " &lt; " + value;
            case "le": return column + " &lt;= " + value;
            case "gt": return column + " &gt; " + value;
            case "ge": return column + " &gt;= " + value;
            case "between": return column + " BETWEEN " + value + " AND " + second;
            case "like": return col + " LIKE " + lower;
            case "notlike": return col + " NOT LIKE " + lower;
            case "prefix": return like(col, p, "", "%", c.ignoreCase, false);
            case "suffix": return like(col, p, "%", "", c.ignoreCase, false);
            case "contains": return like(col, p, "%", "%", c.ignoreCase, false);
            case "notcontains": return like(col, p, "%", "%", c.ignoreCase, true);
            case "in": case "notin": {
                // An empty list is IN (), which no database reads: none is in it, all are not.
                String item = path == null ? "#{v}" : MyBatisMapperRules.paramOf(path.owner, path.leaf, "v");
                boolean notIn = "notin".equals(c.op);
                return "<choose><when test=\"" + p.name + " == null or " + p.name + ".isEmpty()\">"
                        + (notIn ? "1 = 1" : "1 = 0") + "</when><otherwise>"
                        + column + (notIn ? " NOT IN " : " IN ") + "<foreach collection=\"" + p.name
                        + "\" item=\"v\" open=\"(\" separator=\",\" close=\")\">" + item + "</foreach></otherwise></choose>";
            }
            default: return col + " = " + lower;
        }
    }

    /**
     * A LIKE of a parameter wrapped in %, bound so the % is the statement's,
     * not the caller's: the value's own % and _ are escaped and match as
     * written, as Spring Data JPA matches them.
     */
    private static String like(String column, Param p, String before, String after, boolean ignoreCase, boolean not) {
        String expr = "@io.elasticore.springboot3.mybatis.Rows@like(" + p.name + ", '" + before + "', '" + after + "', "
                + ignoreCase + ", _databaseId)";
        String bind = p.name + "Like";
        return "<bind name=\"" + bind + "\" value=\"" + expr + "\"/>" + column + (not ? " NOT LIKE " : " LIKE ")
                + "#{" + bind + "} ESCAPE '!'";
    }

    private static String param(MyBatisMapperRules.Path path, Param p) {
        return MyBatisMapperRules.paramOf(path.owner, path.leaf, p.name);
    }

    /** The name's own order, then the Pageable's; the key's when neither says one. */
    private static String order(EntitySpec e, List<MyBatisMapperRules.Join> joins, Query q) {
        List<String> parts = new ArrayList<String>();
        for (String[] o : q.orderBy) {
            MyBatisMapperRules.Path p = MyBatisMapperRules.resolve(e, joins, o[0]);
            if (p == null) {
                fail(e, q, "orders by " + o[0] + ", which is not a column it reads");
                continue;
            }
            parts.add(p.column + ("Desc".equals(o[1]) ? " DESC" : " ASC"));
        }
        String fixed = String.join(", ", parts);
        String key = e.pkField() == null ? null : MyBatisSql.ROOT + "." + MyBatisSql.column(e.pkField());
        if (!q.pageable) {
            if (!fixed.isEmpty()) return "ORDER BY " + fixed;
            return q.top != null && key != null ? "ORDER BY " + key : "";
        }
        if (!fixed.isEmpty()) return "ORDER BY " + fixed + "<if test=\"orderBy != null\">, ${orderBy}</if>";
        return "ORDER BY <choose><when test=\"orderBy != null\">${orderBy}</when><otherwise>" + key
                + "</otherwise></choose>";
    }

    private static String countType(String declared) {
        String t = declared.trim();
        return "int".equals(t) || "Integer".equals(t) ? "int" : "long";
    }

    private static String dtoType(EntitySpec e, String dto) {
        String pkg = RuleText.componentPackage(dto);
        if (pkg == null || pkg.isEmpty()) pkg = e.pkg("dto");
        return pkg + "." + dto;
    }

    private static String valueType(EntitySpec e, String type) {
        String t = type.trim();
        switch (t) {
            case "long": case "Long": return "long";
            case "int": case "Integer": return "int";
            case "boolean": case "Boolean": return "boolean";
            case "double": case "Double": return "double";
            case "String": return "string";
            case "BigDecimal": return "java.math.BigDecimal";
            case "LocalDate": case "LocalDateTime": case "LocalTime": case "Instant": return "java.time." + t;
            default: {
                String pkg = RuleText.componentPackage(t);
                return pkg != null && !pkg.isEmpty() ? pkg + "." + t : t;
            }
        }
    }

    // ---- native SQL --------------------------------------------------------------

    private static final Pattern NAMED = Pattern.compile("(?<![:\\w]):([A-Za-z_][A-Za-z0-9_]*)|\\?(\\d+)");

    /**
     * The SQL as written, its parameters bound: :name by name, ?1 by place,
     * each with its JDBC type (a null reaches Oracle typed). A collection
     * parameter is a list of binds. The text stays as written inside CDATA,
     * so a &lt; in it is SQL's, not XML's.
     */
    static String nativeSql(EntitySpec e, Query q) {
        Map<String, Param> byName = new LinkedHashMap<String, Param>();
        for (Param p : q.params) byName.put(p.name, p);
        StringBuilder out = new StringBuilder("<![CDATA[");
        String sql = q.sql;
        Matcher m = NAMED.matcher(sql);
        int at = 0;
        int scanned = 0;
        boolean inString = false;
        while (m.find()) {
            // A :name inside a quoted literal is the literal's.
            for (int i = scanned; i < m.start(); i++) if (sql.charAt(i) == '\'') inString = !inString;
            scanned = m.end();
            if (inString) continue;
            Param p;
            if (m.group(1) != null) {
                p = byName.get(m.group(1));
            } else {
                int n = Integer.parseInt(m.group(2));
                p = n >= 1 && n <= q.params.size() ? q.params.get(n - 1) : null;
            }
            if (p == null) {
                fail(e, q, "binds " + m.group() + ", which is not one of its params");
                continue;
            }
            out.append(cdata(sql.substring(at, m.start())));
            if (isCollection(p.type)) {
                out.append("]]><foreach collection=\"").append(p.name).append("\" item=\"v\" separator=\",\">")
                   .append(bindOf(e, collectionItem(p.type), "v")).append("</foreach><![CDATA[");
            } else {
                out.append(bindOf(e, p.type, p.name));
            }
            at = m.end();
        }
        out.append(cdata(sql.substring(at))).append("]]>");
        return out.toString().replace("<![CDATA[]]>", "");
    }

    private static String cdata(String text) {
        return text.replace("]]>", "]]]]><![CDATA[>");
    }

    /** A bound parameter of a type the DSL names: a value, an enum through its handler, an entity by its key. */
    private static String bindOf(EntitySpec e, String type, String expression) {
        String t = RuleText.simpleName(type == null ? "" : type.trim());
        if (e.isKnownEnum(t)) {
            String pkg = RuleText.componentPackage(t);
            if (pkg == null || pkg.isEmpty()) pkg = e.pkg("enumeration") == null || e.pkg("enumeration").isEmpty()
                    ? e.pkg("entity") : e.pkg("enumeration");
            return "#{" + expression + ",javaType=" + pkg + "." + t + ",typeHandler=" + MyBatisSql.ENUM_HANDLER
                    + ",jdbcType=VARCHAR}";
        }
        EntitySpec target = e.getName().equals(t) ? e : e.related(t);
        if (target != null && target.pkField() != null) {
            return "#{" + expression + "." + target.pkField().name() + ",jdbcType="
                    + MyBatisSql.paramJdbcType(target, target.pkField()) + "}";
        }
        String jdbc = JDBC.get(t);
        return "#{" + expression + (jdbc != null ? ",jdbcType=" + jdbc : "") + "}";
    }

    private static final Map<String, String> JDBC = new LinkedHashMap<String, String>();

    static {
        JDBC.put("String", "VARCHAR");
        JDBC.put("Long", "BIGINT");
        JDBC.put("long", "BIGINT");
        JDBC.put("Integer", "INTEGER");
        JDBC.put("int", "INTEGER");
        JDBC.put("Short", "SMALLINT");
        JDBC.put("short", "SMALLINT");
        JDBC.put("Boolean", "BOOLEAN");
        JDBC.put("boolean", "BOOLEAN");
        JDBC.put("Double", "DOUBLE");
        JDBC.put("double", "DOUBLE");
        JDBC.put("Float", "REAL");
        JDBC.put("float", "REAL");
        JDBC.put("BigDecimal", "DECIMAL");
        JDBC.put("BigInteger", "NUMERIC");
        JDBC.put("LocalDate", "DATE");
        JDBC.put("LocalDateTime", "TIMESTAMP");
        JDBC.put("Instant", "TIMESTAMP");
        JDBC.put("Date", "TIMESTAMP");
        JDBC.put("LocalTime", "TIME");
    }

    private static void statement(StringBuilder x, String kind, String id, String attrs, String sql) {
        x.append(I).append('<').append(kind).append(" id=\"").append(id).append('"')
         .append(attrs.isEmpty() ? "" : " " + attrs).append(">\n")
         .append(I).append(I).append(sql).append('\n')
         .append(I).append("</").append(kind).append(">\n\n");
    }
}
