package io.elasticore.base.model.pub.rules.ddd;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Values the model derives rather than receives:
 *
 * <pre>
 * OrderLine:
 *   fields:
 *     unitCost: money(10.2) @snapshot(item.unitCost)    # the price when ordered, kept
 *     subTotal: money(12.2) @compute(unitCost * quantity)
 * PurchaseOrder:
 *   fields:
 *     totalWithoutVat: money(12.2) @compute(sum(lines.subTotal))
 * </pre>
 *
 * @snapshot copies a value from the row a reference points at, once - a
 * later price change leaves the line as it was sold. A reference to a row
 * that does not exist is refused (code reference.*, which the API answers
 * 404). @compute is worked out again every
 * time: a * b, a + b, a - b over the entity's own fields or numbers, or
 * sum(members.field) over a collection of the aggregate.
 *
 * Both are written as derive(lookup) on the entity - members first, so a sum
 * sees the lines it adds up - and the CoreService calls it before each save.
 * Both fields are the entity's to set: no request writes them.
 *
 * (@formula is something else: Hibernate's SQL expression, read from the
 * database.)
 */
public class DddDeriveRules implements CodeRules {

    private static final String LOOKUP = "io.elasticore.springboot3.domain.Lookup";
    private static final String MONEY = "io.elasticore.springboot3.domain.Money";
    private static final String QUANTITY = "io.elasticore.springboot3.domain.Quantity";

    private static final Pattern SUM = Pattern.compile("^sum\\(\\s*(\\w+)\\s*\\.\\s*(\\w+)\\s*\\)$");
    private static final Pattern BINARY = Pattern.compile("^([\\w.]+)\\s*([*+-])\\s*([\\w.]+)$");

    /** Whether the entity has derive(): values of its own, or members that do. */
    public static boolean derives(EntitySpec e) {
        if (e.derives(e.getName())) return true;
        for (FieldSpec f : e.fields()) {
            if (memberDerives(e, f)) return true;
        }
        return false;
    }

    private static boolean memberDerives(EntitySpec e, FieldSpec f) {
        return f.isList() && DddCollectionRules.owns(e, f) && e.derives(f.coreItemType());
    }

    /** The service fills them in before every save. */
    @Rule(value = "ddd-derive-service", target = "service", order = 40)
    void beforeSave(EntitySpec e) {
        if (!derives(e)) return;
        SourceCode c = ctx(e);
        c.varLine("deriveBeforeSave", "%s", call(c));
    }

    /** entity.derive(...) as the service writes it. */
    static String call(SourceCode c) {
        String em = c.varValue("emRef");
        if (em == null || em.isEmpty()) em = "entityManager";
        return c.syntax() == Syntax.KOTLIN
                ? String.format("entity.derive { type, id -> %s.find(type, id) }", em)
                : String.format("entity.derive(%s::find);", em);
    }

    @Rule(value = "ddd-derive", target = "entity", order = 98, phase = Rule.Phase.TAIL)
    void derive(EntitySpec e) {
        if (!derives(e)) return;
        SourceCode c = ctx(e);
        boolean kt = c.syntax() == Syntax.KOTLIN;

        List<String> body = new ArrayList<String>();
        // 1. Taken from what the row refers to.
        for (FieldSpec f : e.fields()) {
            String ref = f.dslValue("snapshot");
            if (ref != null) snapshot(e, f, ref.trim(), kt, body);
        }
        // 2. The members, so what is added up below is up to date.
        for (FieldSpec f : e.fields()) {
            if (!memberDerives(e, f)) continue;
            if (kt) {
                body.add(String.format("this.%s?.forEach { it.derive(lookup) }", f.name()));
            } else {
                body.add(String.format("if (this.%s != null) for (%s member : this.%s) member.derive(lookup);",
                        f.name(), f.coreItemType(), f.name()));
            }
        }
        // 3. Worked out, in the order they are declared.
        for (FieldSpec f : e.fields()) {
            String expr = f.dslValue("compute");
            if (expr != null) compute(e, f, expr.trim(), kt, body);
        }

        c.varLine("fieldList", "")
         .varLine("fieldList", "/**")
         .varLine("fieldList", " * Fills in what the DSL derives: @snapshot values from the rows they refer to,")
         .varLine("fieldList", " * then the @compute ones. Called by the service before each save; call it after")
         .varLine("fieldList", " * changing what they are worked out from.")
         .varLine("fieldList", " */");
        c.varLine("fieldList", kt ? "fun derive(lookup: %s) {" : "public void derive(%s lookup) {", LOOKUP);
        for (String line : body) c.varLine("fieldList", "    %s", line);
        c.varLine("fieldList", "}");
    }

    /** item.unitCost: through the identity the reference is held by, or the relation itself. */
    private void snapshot(EntitySpec e, FieldSpec f, String ref, boolean kt, List<String> body) {
        int dot = ref.indexOf('.');
        if (dot <= 0) {
            warn(e, f, "@snapshot(" + ref + ") is not relation.field");
            return;
        }
        String relation = ref.substring(0, dot);
        String source = ref.substring(dot + 1);
        FieldSpec rel = field(e, relation);
        if (rel == null || rel.isList()) {
            warn(e, f, "@snapshot(" + ref + "): " + relation + " is not a single relation of " + e.getName());
            return;
        }
        String type = rel.typeName();
        FieldSpec id = null;
        for (FieldSpec other : e.fields()) {
            if (relation.equals(other.dslValue("idof"))) id = other;
        }
        String target = f.name();
        if (kt) {
            if (id != null) {
                body.add(String.format("if (this.%s == null && this.%s != null) {", target, id.name()));
                body.add(String.format("    val source = lookup.find(%s::class.java, this.%s) as %s?", type, id.name(), type));
                body.add(String.format("        ?: throw io.elasticore.springboot3.validation.InvariantViolationException(\"%s\", \"%s \" + this.%s + \" does not exist\", this.%s.toString())",
                        DddReferenceRules.missingCode(e, relation), type, id.name(), id.name()));
                DddReferenceRules.recordMissing(e, relation, type);
                body.add(String.format("    this.%s = source.%s", target, source));
                body.add("}");
            } else {
                body.add(String.format("if (this.%s == null) this.%s = this.%s?.%s", target, target, relation, source));
            }
        } else {
            String getter = "get" + cap(source) + "()";
            if (id != null) {
                body.add(String.format("if (this.%s == null && this.%s != null) {", target, id.name()));
                body.add(String.format("    %s source = (%s) lookup.find(%s.class, this.%s);", type, type, type, id.name()));
                body.add("    if (source == null)");
                body.add(String.format("        throw new io.elasticore.springboot3.validation.InvariantViolationException(\"%s\", \"%s \" + this.%s + \" does not exist\", String.valueOf(this.%s));",
                        DddReferenceRules.missingCode(e, relation), type, id.name(), id.name()));
                DddReferenceRules.recordMissing(e, relation, type);
                body.add(String.format("    this.%s = source.%s;", target, getter));
                body.add("}");
            } else {
                body.add(String.format("if (this.%s == null && this.%s != null) this.%s = this.%s.%s;",
                        target, relation, target, relation, getter));
            }
        }
    }

    private void compute(EntitySpec e, FieldSpec f, String expr, boolean kt, List<String> body) {
        Matcher sum = SUM.matcher(expr);
        if (sum.matches()) {
            sum(e, f, sum.group(1), sum.group(2), kt, body);
            return;
        }
        Matcher bin = BINARY.matcher(expr);
        if (!bin.matches()) {
            warn(e, f, "@compute(" + expr + ") is not a * b, a + b, a - b or sum(members.field)");
            return;
        }
        Operand a = operand(e, bin.group(1));
        Operand b = operand(e, bin.group(3));
        for (Operand o : new Operand[]{a, b}) {
            if (o != null && o.field != null && o.field.dslValue("compute") != null
                    && e.fields().indexOf(o.field) > e.fields().indexOf(f)) {
                warn(e, f, "@compute(" + expr + ") reads " + o.field.name()
                        + ", which is worked out after it - declare " + o.field.name() + " first");
            }
        }
        if (a == null || b == null) {
            warn(e, f, "@compute(" + expr + "): an operand is neither a field of " + e.getName() + " nor a number");
            return;
        }
        String op = bin.group(2);
        String result = arithmetic(a, op, b, kt);
        if (result == null) {
            warn(e, f, "@compute(" + expr + "): " + a.kind + " " + op + " " + b.kind + " is not worked out");
            return;
        }
        List<String> nullable = new ArrayList<String>();
        if (a.field != null) nullable.add("this." + a.field.name() + " == null");
        if (b.field != null && (a.field == null || !b.field.name().equals(a.field.name()))) nullable.add("this." + b.field.name() + " == null");
        String guard = join(nullable, " || ");
        if (kt) {
            body.add(guard.isEmpty()
                    ? String.format("this.%s = %s", f.name(), result)
                    : String.format("this.%s = if (%s) null else %s", f.name(), guard, result));
        } else {
            body.add(guard.isEmpty()
                    ? String.format("this.%s = %s;", f.name(), result)
                    : String.format("this.%s = (%s) ? null : %s;", f.name(), guard, result));
        }
    }

    private void sum(EntitySpec e, FieldSpec f, String members, String field, boolean kt, List<String> body) {
        FieldSpec list = field(e, members);
        if (list == null || !list.isList()) {
            warn(e, f, "@compute(sum(" + members + "." + field + ")): " + members + " is not a collection of " + e.getName());
            return;
        }
        String kind = kind(f);
        String zero;
        String add;
        if ("money".equals(kind)) {
            zero = MONEY + ".ZERO";
            add = "%s.plus(%s)";
        } else if ("decimal".equals(kind)) {
            zero = "java.math.BigDecimal.ZERO";
            add = "%s.add(%s)";
        } else if ("quantity".equals(kind)) {
            zero = QUANTITY + ".ZERO";
            add = "%s.plus(%s)";
        } else if ("long".equals(kind) || "int".equals(kind)) {
            zero = "long".equals(kind) ? "0L" : "0";
            add = "%s + %s";
        } else {
            warn(e, f, "@compute(sum(...)) on " + f.typeName() + " is not worked out");
            return;
        }
        String acc = f.name() + "Sum";
        String item = members + "Item";
        if (kt) {
            body.add(String.format("var %s = %s", acc, zero));
            body.add(String.format("this.%s?.forEach { %s -> %s.%s?.let { %s = %s } }", members, item, item, field, acc,
                    String.format(add, acc, "it")));
            body.add(String.format("this.%s = %s", f.name(), acc));
        } else {
            String type = "money".equals(kind) ? MONEY : "decimal".equals(kind) ? "java.math.BigDecimal"
                    : "quantity".equals(kind) ? QUANTITY : kind;
            String getter = item + ".get" + cap(field) + "()";
            body.add(String.format("%s %s = %s;", type, acc, zero));
            body.add(String.format("if (this.%s != null) for (%s %s : this.%s) if (%s != null) %s = %s;",
                    members, list.coreItemType(), item, members, getter, acc, String.format(add, acc, getter)));
            body.add(String.format("this.%s = %s;", f.name(), acc));
        }
    }

    /** One side of a binary expression: a field of the entity or a number. */
    private static final class Operand {
        FieldSpec field;
        String literal;
        String kind;

        String ref(boolean kt) {
            return field != null ? "this." + field.name() + (kt ? "!!" : "") : literal;
        }
    }

    private static Operand operand(EntitySpec e, String text) {
        Operand o = new Operand();
        if (text.matches("\\d+(\\.\\d+)?")) {
            o.literal = text;
            o.kind = text.contains(".") ? "decimal-literal" : "int-literal";
            return o;
        }
        FieldSpec f = field(e, text);
        if (f == null || f.isList()) return null;
        o.field = f;
        o.kind = kind(f);
        return o;
    }

    private static String kind(FieldSpec f) {
        if (f.isMoney()) return "money";
        if (f.isQuantity()) return "quantity";
        if (f.isBigDecimal()) return "decimal";
        String t = f.typeName() == null ? "" : f.typeName().replace("?", "").trim();
        if (t.equals("long") || t.equals("Long")) return "long";
        if (t.equals("int") || t.equals("Integer")) return "int";
        if (t.equals("double") || t.equals("Double")) return "double";
        return t;
    }

    /** The expression in the types' own arithmetic, or null when there is none. */
    private static String arithmetic(Operand a, String op, Operand b, boolean kt) {
        String x = a.ref(kt);
        String y = b.ref(kt);
        String bd = "java.math.BigDecimal";
        if ("money".equals(a.kind)) {
            if ("*".equals(op)) {
                if ("quantity".equals(b.kind) || "decimal".equals(b.kind)) return x + ".times(" + y + ")";
                if ("long".equals(b.kind) || "int".equals(b.kind)) return x + ".times(" + (kt ? y + ".toLong()" : "(long) " + y) + ")";
                if ("int-literal".equals(b.kind)) return x + ".times(" + y + "L)";
                if ("decimal-literal".equals(b.kind)) return x + ".times(" + (kt ? bd + "(\"" + y + "\")" : "new " + bd + "(\"" + y + "\")") + ")";
                return null;
            }
            if ("money".equals(b.kind)) return x + ("+".equals(op) ? ".plus(" : ".minus(") + y + ")";
            return null;
        }
        if ("decimal".equals(a.kind)) {
            String other;
            if ("decimal".equals(b.kind)) other = y;
            else if ("quantity".equals(b.kind)) other = kt ? bd + ".valueOf(" + y + ".value().toLong())" : bd + ".valueOf(" + y + ".value())";
            else if ("long".equals(b.kind) || "int".equals(b.kind)) other = kt ? bd + ".valueOf(" + y + ".toLong())" : bd + ".valueOf(" + y + ")";
            else if (b.kind.endsWith("literal")) other = kt ? bd + "(\"" + y + "\")" : "new " + bd + "(\"" + y + "\")";
            else return null;
            String method = "*".equals(op) ? "multiply" : "+".equals(op) ? "add" : "subtract";
            return x + "." + method + "(" + other + ")";
        }
        boolean na = "long".equals(a.kind) || "int".equals(a.kind) || "double".equals(a.kind) || "int-literal".equals(a.kind);
        boolean nb = "long".equals(b.kind) || "int".equals(b.kind) || "double".equals(b.kind) || "int-literal".equals(b.kind);
        if (na && nb) return x + " " + op + " " + y;
        return null;
    }

    private static FieldSpec field(EntitySpec e, String name) {
        for (FieldSpec f : e.fields()) {
            if (f.name().equals(name)) return f;
        }
        return null;
    }

    private static void warn(EntitySpec e, FieldSpec f, String what) {
        io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format("[%s] %s.%s: %s; it is left as it is.",
                e.domainClassName(), e.getName(), f.name(), what));
    }

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String join(List<String> items, String sep) {
        StringBuilder out = new StringBuilder();
        for (String s : items) {
            if (out.length() > 0) out.append(sep);
            out.append(s);
        }
        return out.toString();
    }
}
