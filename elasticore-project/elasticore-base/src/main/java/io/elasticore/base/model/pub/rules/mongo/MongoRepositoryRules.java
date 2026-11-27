package io.elasticore.base.model.pub.rules.mongo;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.base.ServiceAccessRules;
import io.elasticore.base.model.pub.rules.base.SoftDelete;
import io.elasticore.base.model.pub.rules.mybatis.MyBatisSql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The repository of a MongoDB domain's entity: Spring Data's MongoRepository,
 * and - for an entity with a service - the search the service runs with
 * MongoTemplate, as static methods beside it.
 *
 * The search is the search DTO's conditions (MyBatisSearchRules reads them as
 * for MyBatis: operator, entity path, search DTO property, type) as one
 * Criteria; a sort may name the document's values, its embedded values' and
 * a reference's id, and nothing else - what a request sends is never a field
 * name the query takes as it is.
 */
public class MongoRepositoryRules implements CodeRules {

    public static final String TARGET = "mongoRepository";

    /** Where the runner puts the search DTO's conditions for the entity. */
    public static final String SEARCH_CONFIG = "mongo.search";

    /** "true" for an entity with a service, whose repository carries the search. */
    public static final String SERVICED_CONFIG = "mongo.serviced";

    private static final String I = "    ";
    private static final String TEXT = "io.elasticore.springboot3.mongo.MongoText";
    private static final java.util.regex.Pattern LINES = java.util.regex.Pattern.compile("\n");
    private static final java.util.regex.Pattern TABS = java.util.regex.Pattern.compile("\t");
    private static final java.util.regex.Pattern DOTS = java.util.regex.Pattern.compile("\\.");

    @Rule(value = "mongo-repository", target = TARGET, order = 10)
    void repository(EntitySpec e) {
        SourceCode c = ctx(e);
        c.template("mongo_repository");
        FieldSpec pk = e.pkField();
        String entity = e.getName();
        c.var("packageName", e.pkg("repository"))
                .var("entityPackageName", e.pkg("entity"))
                .var("entityClassName", entity)
                .var("className", "%sRepository", entity)
                .var("collection", MongoDocs.collection(e));
        if (c.syntax() == io.elasticore.base.model.pub.rules.Syntax.KOTLIN) {
            c.var("keyType", c.syntax().typeName(pk.typeName()));
            c.var("body", RuleText.stripTrailing(MongoKotlinRepository.body(c, e, conditions(e))));
            return;
        }
        c.var("keyType", c.syntax().nullableType(pk.typeName()));

        StringBuilder body = new StringBuilder();
        String login = ServiceAccessRules.loginField(e);
        if (login != null) {
            FieldSpec field = field(e, login);
            String filter = "'" + stored(e, field) + "': ?0";
            if (SoftDelete.applies(e)) filter += ", '" + stored(e, SoftDelete.field(e)) + "': null";
            body.append("\n")
                    .append(I)
                    .append("/** The documents whose ")
                    .append(login)
                    .append(" is the one given: signing in reads it. */\n")
                    .append(I)
                    .append("@org.springframework.data.mongodb.repository.Query(\"{ ")
                    .append(filter)
                    .append(" }\")\n")
                    .append(I)
                    .append("List<")
                    .append(entity)
                    .append("> findAllByLogin(String value);\n");
        }
        if ("true".equals(e.config(SERVICED_CONFIG))) search(c, e, body);
        c.var("body", RuleText.stripTrailing(body.toString()));
    }

    /** The search methods: criteria, hasCondition and sort. */
    private void search(SourceCode c, EntitySpec e, StringBuilder body) {
        String entity = e.getName();
        String srch = e.pkg("dto") + "." + e.searchDtoClassName();
        List<Condition> conditions = new ArrayList<Condition>();
        for (String[] cond : conditions(e)) {
            Condition resolved = resolve(e, cond);
            if (resolved != null) conditions.add(resolved);
        }

        body.append("\n")
                .append(I)
                .append("/** The properties a sort may name, and the one each sorts by. */\n")
                .append(I)
                .append("Map<String, String> SORT_FIELDS = sortFields();\n\n");

        body.append(I)
                .append("/**\n")
                .append(I)
                .append(" * The documents a search matches - the owner's only when owner is given -\n")
                .append(I)
                .append(" * as one Criteria; a condition without a value is left out.\n")
                .append(I)
                .append(" */\n")
                .append(I)
                .append("static Criteria criteria(")
                .append(srch)
                .append(" s, Object owner) {\n")
                .append(I)
                .append(I)
                .append("List<Criteria> and = new ArrayList<>();\n");
        if (!conditions.isEmpty()) {
            body.append(I).append(I).append("if (s != null) {\n");
            for (Condition cond : conditions) cond.write(body, I + I + I);
            body.append(I).append(I).append("}\n");
        }
        if (SoftDelete.applies(e)) {
            body.append(I)
                    .append(I)
                    .append("and.add(Criteria.where(\"")
                    .append(SoftDelete.field(e).name())
                    .append("\").is(null));\n");
        }
        FieldSpec owner = ServiceAccessRules.ownerField(e);
        if (owner != null) {
            body.append(I)
                    .append(I)
                    .append("if (owner != null) and.add(Criteria.where(\"")
                    .append(owner.name())
                    .append("\").is(owner));\n");
        }
        body.append(I)
                .append(I)
                .append("return and.isEmpty() ? new Criteria() : new Criteria().andOperator(and);\n")
                .append(I)
                .append("}\n\n");

        // hasCondition: whether the search narrows the documents at all.
        StringBuilder test = new StringBuilder();
        for (Condition cond : conditions) {
            for (String t : cond.tests()) {
                if (test.length() > 0)
                    test.append("\n").append(I).append(I).append(I).append("|| ");
                test.append(t);
            }
        }
        body.append(I)
                .append("/** Whether the search narrows the documents at all: an update by condition needs it to. */\n")
                .append(I)
                .append("static boolean hasCondition(")
                .append(srch)
                .append(" s) {\n")
                .append(I)
                .append(I)
                .append("if (s == null) return false;\n")
                .append(I)
                .append(I)
                .append("return ")
                .append(test.length() == 0 ? "false" : test.toString())
                .append(";\n")
                .append(I)
                .append("}\n\n");

        body.append(I)
                .append("/** The order of a sort, from SORT_FIELDS only; null when unsorted. */\n")
                .append(I)
                .append("static Sort sort(Sort sort) {\n")
                .append(I)
                .append(I)
                .append("if (sort == null || sort.isUnsorted()) return null;\n")
                .append(I)
                .append(I)
                .append("List<Sort.Order> orders = new ArrayList<>();\n")
                .append(I)
                .append(I)
                .append("for (Sort.Order order : sort) {\n")
                .append(I)
                .append(I)
                .append(I)
                .append("String field = SORT_FIELDS.get(order.getProperty());\n")
                .append(I)
                .append(I)
                .append(I)
                .append("if (field == null)\n")
                .append(I)
                .append(I)
                .append(I)
                .append(I)
                .append("throw new BadRequestException(\"")
                .append(entity)
                .append(" cannot be sorted by '\" + order.getProperty()\n")
                .append(I)
                .append(I)
                .append(I)
                .append(I)
                .append(I)
                .append(I)
                .append("+ \"'; sort by one of \" + SORT_FIELDS.keySet());\n")
                .append(I)
                .append(I)
                .append(I)
                .append("orders.add(order.isAscending() ? Sort.Order.asc(field) : Sort.Order.desc(field));\n")
                .append(I)
                .append(I)
                .append("}\n")
                .append(I)
                .append(I)
                .append("return Sort.by(orders);\n")
                .append(I)
                .append("}\n\n");

        body.append(I)
                .append("private static Map<String, String> sortFields() {\n")
                .append(I)
                .append(I)
                .append("Map<String, String> m = new LinkedHashMap<>();\n");
        for (Map.Entry<String, String> s : sortFields(e).entrySet()) {
            body.append(I)
                    .append(I)
                    .append("m.put(")
                    .append(RuleText.quote(s.getKey()))
                    .append(", ")
                    .append(RuleText.quote(s.getValue()))
                    .append(");\n");
        }
        body.append(I)
                .append(I)
                .append("return Collections.unmodifiableMap(m);\n")
                .append(I)
                .append("}\n");

        // A reference is searched by the document it points at, of which only the id is stored.
        java.util.Set<String> refs = new java.util.LinkedHashSet<String>();
        for (Condition cond : conditions) {
            if (cond.ref != null && refs.add(cond.ref.getName())) {
                EntitySpec t = cond.ref;
                String type = t.pkg("entity") + "." + t.getName();
                String key = c.syntax().nullableType(t.pkField().typeName());
                body.append("\n")
                        .append(I)
                        .append("/** A ")
                        .append(t.getName())
                        .append(" that stands for its id in a query: a reference stores the id. */\n")
                        .append(I)
                        .append("private static ")
                        .append(type)
                        .append(" ")
                        .append(refMethod(t))
                        .append("(")
                        .append(key)
                        .append(" id) {\n")
                        .append(I)
                        .append(I)
                        .append(type)
                        .append(" ref = new ")
                        .append(type)
                        .append("();\n")
                        .append(I)
                        .append(I)
                        .append(c.syntax()
                                .statement(c.syntax().setter("ref", t.pkField().name(), "id")))
                        .append("\n")
                        .append(I)
                        .append(I)
                        .append("return ref;\n")
                        .append(I)
                        .append("}\n");
            }
        }
    }

    static String refMethod(EntitySpec target) {
        return RuleText.uncapital(target.getName()) + "Ref";
    }

    /**
     * What a sort may name, by property: the document's own values, its
     * embedded values' parts and a reference's id - stored in the document.
     */
    static Map<String, String> sortFields(EntitySpec e) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (FieldSpec f : MyBatisSql.fields(e)) {
            if (MongoDocs.isTransient(e, f)) continue;
            if (MyBatisSql.isValue(e, f)) {
                m.put(f.name(), f.name());
            } else if (MyBatisSql.isEmbedded(e, f)) {
                for (MyBatisSql.Part p : MyBatisSql.parts(e, f)) {
                    m.put(f.name() + "." + p.field.name(), f.name() + "." + p.field.name());
                }
            } else if (MongoDocs.isReference(e, f)) {
                EntitySpec t = e.related(f.coreItemType());
                if (t != null && t.pkField() != null)
                    m.put(f.name() + "." + t.pkField().name(), f.name());
            }
        }
        return m;
    }

    /** The search conditions the runner handed over: operator, path, property, type. */
    // Empty parts, trailing ones included, are skipped or dropped anyway: split as it is.
    @SuppressWarnings("StringSplitter")
    static List<String[]> conditions(EntitySpec e) {
        List<String[]> out = new ArrayList<String[]>();
        String text = e.config(SEARCH_CONFIG);
        if (text == null || text.isEmpty()) return out;
        for (String line : LINES.split(text)) {
            String[] parts = TABS.split(line);
            if (parts.length == 4) out.add(parts);
        }
        return out;
    }

    /**
     * The search conditions the Criteria holds for an entity - the operator as it applies it (Condition.applied),
     * the entity path, the search DTO property, the type - and, below, what its sort may name: what GET
     * /schema/info says its search applies (issue 373). A path that is not stored stopped the build already.
     */
    public static List<String[]> searched(EntitySpec e) {
        List<String[]> out = new ArrayList<String[]>();
        for (String[] cond : conditions(e)) {
            Condition resolved = resolve(e, cond);
            if (resolved != null) out.add(new String[] {resolved.applied(), cond[1], cond[2], cond[3]});
        }
        return out;
    }

    /** The properties a sort of the entity's search may name (SORT_FIELDS). */
    public static java.util.Set<String> sortable(EntitySpec e) {
        return sortFields(e).keySet();
    }

    /** A field of the entity, its inherited ones included. */
    static FieldSpec field(EntitySpec e, String name) {
        for (FieldSpec f : MyBatisSql.fields(e)) {
            if (f.name().equals(name)) return f;
        }
        return null;
    }

    /** The name a value is stored under in the document. */
    static String stored(EntitySpec e, FieldSpec f) {
        if (f == null) return "_id";
        if (f == e.pkField()) return "_id";
        String named = MongoDocs.explicitName(f);
        return named != null ? named : f.name();
    }

    /**
     * Where a condition's path lies in the document, or null - and the build
     * stopped - when it is not stored there: a value of the document or of
     * an embedded value, or a reference's id.
     */
    // Empty parts, trailing ones included, are skipped or dropped anyway: split as it is.
    @SuppressWarnings("StringSplitter")
    static Condition resolve(EntitySpec e, String[] cond) {
        String[] seg = DOTS.split(cond[1]);
        FieldSpec f = field(e, seg[0]);
        Condition out = null;
        if (f != null && !MongoDocs.isTransient(e, f)) {
            if (seg.length == 1 && MyBatisSql.isValue(e, f)) {
                out = new Condition(cond, f.name(), MongoDocs.converted(e, f), null);
            } else if (seg.length == 2 && MyBatisSql.isEmbedded(e, f)) {
                for (MyBatisSql.Part p : MyBatisSql.parts(e, f)) {
                    if (p.field.name().equals(seg[1])) {
                        out = new Condition(cond, f.name() + "." + seg[1], MongoDocs.converted(e, p.field), null);
                    }
                }
            } else if (seg.length == 2 && MongoDocs.isReference(e, f)) {
                EntitySpec t = e.related(f.coreItemType());
                if (t != null && t.pkField() != null && t.pkField().name().equals(seg[1])) {
                    out = new Condition(cond, f.name(), false, t);
                }
            }
        }
        if (out == null) {
            io.elasticore.base.model.pub.rules.ModelCheck.fail(String.format(
                    "[elcore3] %s.%s: mode: mongo searches the document, its embedded values and a reference's id;"
                            + " %s is not stored in the %s document - search by a value of %s",
                    e.domainClassName(), e.getName(), cond[1], e.getName(), e.getName()));
        }
        return out;
    }

    /** One condition of the search: the operator, the stored path, how the value is written. */
    static final class Condition {
        final String op;
        final String prop;
        final String type;
        final String path;
        final boolean converted;
        final EntitySpec ref;

        Condition(String[] cond, String path, boolean converted, EntitySpec ref) {
            this.op = cond[0];
            this.prop = cond[2];
            this.type = cond[3];
            this.path = path;
            this.converted = converted;
            this.ref = ref;
        }

        /**
         * The operator as the query applies it: a text match - auto included - on a value that is not text, or
         * that is stored converted, is an equality (textMatch, and auto's is). Java and Kotlin write it alike.
         */
        String applied() {
            switch (op) {
                case "like":
                case "ilike":
                case "ieq":
                case "prefix":
                case "iprefix":
                case "suffix":
                case "isuffix":
                case "auto":
                    return text() && !converted ? op : "eq";
                default:
                    return op;
            }
        }

        private String getter(String suffix) {
            return "s.get" + RuleText.capital(prop + suffix) + "()";
        }

        private boolean text() {
            return "String".equals(RuleText.simpleName(type));
        }

        /** The value as the document holds it. */
        private String value(String v) {
            if (ref != null) return refMethod(ref) + "(" + v + ")";
            if (converted) return MongoDocs.VALUE_CONVERTER + ".stored(" + v + ")";
            return v;
        }

        private String hasValue(String v) {
            if ("in".equals(op)) return "(" + v + " != null && !" + v + ".isEmpty())";
            if (text()) return "(" + v + " != null && !" + v + ".trim().isEmpty())";
            return v + " != null";
        }

        /** What hasCondition tests of it. */
        List<String> tests() {
            List<String> out = new ArrayList<String>();
            if ("between".equals(op)) {
                out.add(getter("From") + " != null");
                out.add(getter("To") + " != null");
            } else if ("isnull".equals(op)) {
                out.add(getter("Empty") + " != null");
            } else {
                out.add(hasValue(getter("")));
            }
            return out;
        }

        void write(StringBuilder b, String indent) {
            String where = "Criteria.where(\"" + path + "\")";
            if ("between".equals(op)) {
                String from = getter("From"), to = getter("To");
                b.append(indent)
                        .append("if (")
                        .append(from)
                        .append(" != null) and.add(")
                        .append(where)
                        .append(".gte(")
                        .append(value(from))
                        .append("));\n");
                b.append(indent)
                        .append("if (")
                        .append(to)
                        .append(" != null) and.add(")
                        .append(where)
                        .append(".lte(")
                        .append(value(to))
                        .append("));\n");
                return;
            }
            if ("isnull".equals(op)) {
                String empty = getter("Empty");
                b.append(indent)
                        .append("if (")
                        .append(empty)
                        .append(" != null) and.add(Boolean.TRUE.equals(")
                        .append(empty)
                        .append(") ? ")
                        .append(where)
                        .append(".is(null) : ")
                        .append(where)
                        .append(".ne(null));\n");
                return;
            }
            String v = getter("");
            String test = hasValue(v);
            String expr;
            switch (op) {
                case "ne":
                    expr = ".ne(" + value(v) + ")";
                    break;
                case "gt":
                    expr = ".gt(" + value(v) + ")";
                    break;
                case "ge":
                    expr = ".gte(" + value(v) + ")";
                    break;
                case "lt":
                    expr = ".lt(" + value(v) + ")";
                    break;
                case "le":
                    expr = ".lte(" + value(v) + ")";
                    break;
                case "like":
                    expr = textMatch(v, true, true, false);
                    break;
                case "ilike":
                    expr = textMatch(v, true, true, true);
                    break;
                case "ieq":
                    expr = textMatch(v, false, false, true);
                    break;
                case "prefix":
                    expr = textMatch(v, false, true, false);
                    break;
                case "iprefix":
                    expr = textMatch(v, false, true, true);
                    break;
                case "suffix":
                    expr = textMatch(v, true, false, false);
                    break;
                case "isuffix":
                    expr = textMatch(v, true, false, true);
                    break;
                case "in":
                    expr = ref != null
                            ? ".in(" + v + ".stream().map(" + "x -> " + refMethod(ref)
                                    + "(x)).collect(java.util.stream.Collectors.toList()))"
                            : converted
                                    ? ".in(" + MongoDocs.VALUE_CONVERTER + ".storedAll(" + v + "))"
                                    : ".in(" + v + ")";
                    break;
                case "auto":
                    expr = text() && !converted ? ".regex(" + TEXT + ".like(" + v + "))" : ".is(" + value(v) + ")";
                    break;
                default:
                    expr = ".is(" + value(v) + ")";
            }
            b.append(indent)
                    .append("if (")
                    .append(test)
                    .append(") and.add(")
                    .append(where)
                    .append(expr)
                    .append(");\n");
        }

        private String textMatch(String v, boolean anyBefore, boolean anyAfter, boolean ignoreCase) {
            if (!text() || converted) return ".is(" + value(v) + ")";
            return ".regex(" + TEXT + ".match(" + v + ".trim(), " + anyBefore + ", " + anyAfter + ", " + ignoreCase
                    + "))";
        }
    }
}
