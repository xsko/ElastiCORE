package io.elasticore.base.model.pub.rules.mongo;

import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.base.ServiceAccessRules;
import io.elasticore.base.model.pub.rules.base.SoftDelete;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The body of a MongoDB repository in Kotlin (kt/mongo_repository.tmpl): the
 * login finder, and - for an entity with a service - the search that
 * MongoRepositoryRules writes in Java, as the interface's companion object.
 *
 * A Java interface holds static methods; a Kotlin one holds a companion
 * object, whose functions carry @JvmStatic so that both languages call them
 * the same way: OrderRepository.criteria(search, owner).
 */
final class MongoKotlinRepository {

    private static final String I = "    ";
    private static final String TEXT = "io.elasticore.springboot3.mongo.MongoText";

    private MongoKotlinRepository() {}

    static String body(SourceCode c, EntitySpec e, List<String[]> rawConditions) {
        StringBuilder body = new StringBuilder();
        String entity = e.getName();
        String login = ServiceAccessRules.loginField(e);
        if (login != null) {
            FieldSpec field = MongoRepositoryRules.field(e, login);
            String filter = "'" + MongoRepositoryRules.stored(e, field) + "': ?0";
            if (SoftDelete.applies(e)) {
                filter += ", '" + MongoRepositoryRules.stored(e, SoftDelete.field(e)) + "': null";
            }
            body.append("\n")
                    .append(I)
                    .append("/** The documents whose ")
                    .append(login)
                    .append(" is the one given: signing in reads it. */\n")
                    .append(I)
                    .append("@org.springframework.data.mongodb.repository.Query(\"{ ")
                    .append(filter.replace("$", "\\$"))
                    .append(" }\")\n")
                    .append(I)
                    .append("fun findAllByLogin(value: String?): List<")
                    .append(entity)
                    .append(">\n");
        }
        if ("true".equals(e.config(MongoRepositoryRules.SERVICED_CONFIG))) search(e, rawConditions, body);
        return body.toString();
    }

    private static void search(EntitySpec e, List<String[]> rawConditions, StringBuilder body) {
        String entity = e.getName();
        String srch = e.pkg("dto") + "." + e.searchDtoClassName();
        List<MongoRepositoryRules.Condition> conditions = new ArrayList<MongoRepositoryRules.Condition>();
        for (String[] cond : rawConditions) {
            MongoRepositoryRules.Condition resolved = MongoRepositoryRules.resolve(e, cond);
            if (resolved != null) conditions.add(resolved);
        }
        String i2 = I + I;
        String i3 = i2 + I;

        body.append("\n").append(I).append("companion object {\n\n");

        // @JvmField: Java reads it as the Java repository's constant, OrderRepository.SORT_FIELDS.
        body.append(i2)
                .append("/** The properties a sort may name, and the one each sorts by. */\n")
                .append(i2)
                .append("@JvmField\n")
                .append(i2)
                .append("val SORT_FIELDS: Map<String, String> = sortFields()\n\n");

        body.append(i2)
                .append("/**\n")
                .append(i2)
                .append(" * The documents a search matches - the owner's only when owner is given -\n")
                .append(i2)
                .append(" * as one Criteria; a condition without a value is left out.\n")
                .append(i2)
                .append(" */\n")
                .append(i2)
                .append("@JvmStatic\n")
                .append(i2)
                .append("fun criteria(s: ")
                .append(srch)
                .append("?, owner: Any?): Criteria {\n")
                .append(i3)
                .append("val and = ArrayList<Criteria>()\n");
        if (!conditions.isEmpty()) {
            body.append(i3).append("if (s != null) {\n");
            for (MongoRepositoryRules.Condition cond : conditions) write(cond, body, i3 + I);
            body.append(i3).append("}\n");
        }
        if (SoftDelete.applies(e)) {
            body.append(i3)
                    .append("and.add(Criteria.where(\"")
                    .append(SoftDelete.field(e).name())
                    .append("\").`is`(null))\n");
        }
        FieldSpec owner = ServiceAccessRules.ownerField(e);
        if (owner != null) {
            body.append(i3)
                    .append("if (owner != null) and.add(Criteria.where(\"")
                    .append(owner.name())
                    .append("\").`is`(owner))\n");
        }
        body.append(i3)
                .append("return if (and.isEmpty()) Criteria() else Criteria().andOperator(and)\n")
                .append(i2)
                .append("}\n\n");

        StringBuilder test = new StringBuilder();
        for (MongoRepositoryRules.Condition cond : conditions) {
            for (String t : tests(cond)) {
                if (test.length() > 0) test.append("\n").append(i3).append(I).append("|| ");
                test.append(t);
            }
        }
        body.append(i2)
                .append("/** Whether the search narrows the documents at all: an update by condition needs it to. */\n")
                .append(i2)
                .append("@JvmStatic\n")
                .append(i2)
                .append("fun hasCondition(s: ")
                .append(srch)
                .append("?): Boolean {\n")
                .append(i3)
                .append("if (s == null) return false\n")
                .append(i3)
                .append("return ")
                .append(test.length() == 0 ? "false" : test.toString())
                .append("\n")
                .append(i2)
                .append("}\n\n");

        body.append(i2)
                .append("/** The order of a sort, from SORT_FIELDS only; null when unsorted. */\n")
                .append(i2)
                .append("@JvmStatic\n")
                .append(i2)
                .append("fun sort(sort: Sort?): Sort? {\n")
                .append(i3)
                .append("if (sort == null || sort.isUnsorted) return null\n")
                .append(i3)
                .append("val orders = ArrayList<Sort.Order>()\n")
                .append(i3)
                .append("for (order in sort) {\n")
                .append(i3)
                .append(I)
                .append("val field = SORT_FIELDS[order.property]\n")
                .append(i3)
                .append(I)
                .append(I)
                .append("?: throw BadRequestException(\"")
                .append(entity)
                .append(" cannot be sorted by '\" + order.property\n")
                .append(i3)
                .append(I)
                .append(I)
                .append(I)
                .append(I)
                .append("+ \"'; sort by one of \" + SORT_FIELDS.keys)\n")
                .append(i3)
                .append(I)
                .append("orders.add(if (order.isAscending) Sort.Order.asc(field) else Sort.Order.desc(field))\n")
                .append(i3)
                .append("}\n")
                .append(i3)
                .append("return Sort.by(orders)\n")
                .append(i2)
                .append("}\n\n");

        body.append(i2)
                .append("private fun sortFields(): Map<String, String> {\n")
                .append(i3)
                .append("val m = LinkedHashMap<String, String>()\n");
        for (Map.Entry<String, String> s : MongoRepositoryRules.sortFields(e).entrySet()) {
            body.append(i3)
                    .append("m[")
                    .append(quote(s.getKey()))
                    .append("] = ")
                    .append(quote(s.getValue()))
                    .append("\n");
        }
        body.append(i3)
                .append("return java.util.Collections.unmodifiableMap(m)\n")
                .append(i2)
                .append("}\n");

        // A reference is searched by the document it points at, of which only the id is stored.
        java.util.Set<String> refs = new java.util.LinkedHashSet<String>();
        for (MongoRepositoryRules.Condition cond : conditions) {
            if (cond.ref != null && refs.add(cond.ref.getName())) {
                EntitySpec t = cond.ref;
                String type = t.pkg("entity") + "." + t.getName();
                String key = io.elasticore.base.model.pub.rules.Syntax.KOTLIN.nullableType(
                        t.pkField().typeName());
                body.append("\n")
                        .append(i2)
                        .append("/** A ")
                        .append(t.getName())
                        .append(" that stands for its id in a query: a reference stores the id. */\n")
                        .append(i2)
                        .append("private fun ")
                        .append(MongoRepositoryRules.refMethod(t))
                        .append("(id: ")
                        .append(key)
                        .append("): ")
                        .append(type)
                        .append(" {\n")
                        .append(i3)
                        .append("val ref = ")
                        .append(type)
                        .append("()\n")
                        .append(i3)
                        .append("ref.")
                        .append(t.pkField().name())
                        .append(" = id\n")
                        .append(i3)
                        .append("return ref\n")
                        .append(i2)
                        .append("}\n");
            }
        }
        body.append(I).append("}\n");
    }

    /** A Kotlin string literal: a $ would start a template. */
    private static String quote(String s) {
        return RuleText.quote(s).replace("$", "\\$");
    }

    private static String read(MongoRepositoryRules.Condition c, String suffix) {
        return "s." + c.prop + suffix;
    }

    private static boolean text(MongoRepositoryRules.Condition c) {
        return "String".equals(RuleText.simpleName(c.type));
    }

    /** The value as the document holds it. */
    private static String value(MongoRepositoryRules.Condition c, String v) {
        if (c.ref != null) return MongoRepositoryRules.refMethod(c.ref) + "(" + v + ")";
        if (c.converted) return MongoDocs.VALUE_CONVERTER + ".stored(" + v + ")";
        return v;
    }

    private static String hasValue(MongoRepositoryRules.Condition c, String v) {
        if ("in".equals(c.op)) return "!" + v + ".isNullOrEmpty()";
        if (text(c)) return "!" + v + ".isNullOrBlank()";
        return v + " != null";
    }

    private static List<String> tests(MongoRepositoryRules.Condition c) {
        List<String> out = new ArrayList<String>();
        if ("between".equals(c.op)) {
            out.add(read(c, "From") + " != null");
            out.add(read(c, "To") + " != null");
        } else if ("isnull".equals(c.op)) {
            out.add(read(c, "Empty") + " != null");
        } else {
            out.add(hasValue(c, read(c, "")));
        }
        return out;
    }

    private static void write(MongoRepositoryRules.Condition c, StringBuilder b, String indent) {
        String where = "Criteria.where(\"" + c.path + "\")";
        if ("between".equals(c.op)) {
            // Read once into a value: Spring Data MongoDB 5 (Boot 4) declares the operand non-null,
            // and a property of another object is not smart-cast after a test.
            b.append(indent)
                    .append(read(c, "From"))
                    .append("?.let { v -> and.add(")
                    .append(where)
                    .append(".gte(")
                    .append(value(c, "v"))
                    .append(")) }\n");
            b.append(indent)
                    .append(read(c, "To"))
                    .append("?.let { v -> and.add(")
                    .append(where)
                    .append(".lte(")
                    .append(value(c, "v"))
                    .append(")) }\n");
            return;
        }
        if ("isnull".equals(c.op)) {
            String empty = read(c, "Empty");
            b.append(indent)
                    .append("if (")
                    .append(empty)
                    .append(" != null) and.add(if (")
                    .append(empty)
                    .append(" == true) ")
                    .append(where)
                    .append(".`is`(null) else ")
                    .append(where)
                    .append(".ne(null))\n");
            return;
        }
        String v = read(c, "");
        String local = "v";
        String expr;
        switch (c.op) {
            case "ne":
                expr = ".ne(" + value(c, local) + ")";
                break;
            case "gt":
                expr = ".gt(" + value(c, local) + ")";
                break;
            case "ge":
                expr = ".gte(" + value(c, local) + ")";
                break;
            case "lt":
                expr = ".lt(" + value(c, local) + ")";
                break;
            case "le":
                expr = ".lte(" + value(c, local) + ")";
                break;
            case "like":
                expr = textMatch(c, local, true, true, false);
                break;
            case "ilike":
                expr = textMatch(c, local, true, true, true);
                break;
            case "ieq":
                expr = textMatch(c, local, false, false, true);
                break;
            case "prefix":
                expr = textMatch(c, local, false, true, false);
                break;
            case "iprefix":
                expr = textMatch(c, local, false, true, true);
                break;
            case "suffix":
                expr = textMatch(c, local, true, false, false);
                break;
            case "isuffix":
                expr = textMatch(c, local, true, false, true);
                break;
            case "in":
                expr = c.ref != null
                        ? ".`in`(" + local + ".map { " + MongoRepositoryRules.refMethod(c.ref) + "(it) })"
                        : c.converted
                                ? ".`in`(" + MongoDocs.VALUE_CONVERTER + ".storedAll(" + local + "))"
                                : ".`in`(" + local + ")";
                break;
            case "auto":
                expr = text(c) && !c.converted
                        ? ".regex(" + TEXT + ".like(" + local + "))"
                        : ".`is`(" + value(c, local) + ")";
                break;
            default:
                expr = ".`is`(" + value(c, local) + ")";
        }
        // Read once: a property of another object is not smart-cast after a test.
        String test = "in".equals(c.op) ? local + ".isNotEmpty()" : text(c) ? local + ".isNotBlank()" : null;
        b.append(indent)
                .append(v)
                .append("?.let { ")
                .append(local)
                .append(" -> ")
                .append(test == null ? "" : "if (" + test + ") ")
                .append("and.add(")
                .append(where)
                .append(expr)
                .append(") }\n");
    }

    private static String textMatch(
            MongoRepositoryRules.Condition c, String v, boolean anyBefore, boolean anyAfter, boolean ignoreCase) {
        if (!text(c) || c.converted) return ".`is`(" + value(c, v) + ")";
        return ".regex(" + TEXT + ".match(" + v + ".trim(), " + anyBefore + ", " + anyAfter + ", " + ignoreCase + "))";
    }
}
