package io.elasticore.base.model.pub.rules.ddd;

import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Syntax;

/**
 * The small grammar the DDD rules read conditions in.
 *
 * Two patterns state a rule about an aggregate and differ only in what is
 * done with the answer: an invariant refuses a write when it is false, a
 * specification hands it out as something to ask. Sharing the reading means
 * the two cannot drift into accepting different dialects of what looks like
 * one language - which is the way a DSL acquires corners nobody documented.
 *
 * <h3>What it accepts, and why so little</h3>
 *
 * <pre>
 *   items.size &gt; 0        a to-many with at least that many members
 *   totalAmount &gt; 100     a number above a bound
 *   notes notNull         a field that is set
 *   items notEmpty        a to-many with anything in it
 * </pre>
 *
 * Field, comparison, literal - and no more. An expression language would
 * have to be parsed, and a parser that is nearly right is worse than a form
 * that is obviously limited: the DSL would start looking like a place to put
 * logic, which is the one thing it is not.
 *
 * The comparisons are {@code > < >= <= ==}. The annotation parser reads an
 * '=' in the arguments as the start of a key/value list, except in the
 * condition annotations, where one that is part of {@code == >= <=} is a
 * comparison (AbstractModelLoader.hasAssignment).
 *
 * <h3>One grammar, three spellings</h3>
 *
 * The generated code lives outside the type it asks about, so every field is
 * read through whatever that language calls an accessor. Java calls a getter.
 * Kotlin names the property, which is nullable, so a dereference carries the
 * assertion. TypeScript names the property too, and differs in two ways that
 * are facts about the wire rather than about the language:
 *
 * <ul>
 *   <li>a collection's count is {@code .length}, not {@code size}</li>
 *   <li>a decimal arrives as a <em>string</em>, because JSON has one number
 *       type and it is a double - so the server sends {@code "14.49"} and a
 *       comparison has to say {@code Number(...)} before it means anything</li>
 * </ul>
 *
 * That second one is why this is worth generating at all on that side. A
 * developer writing the same condition by hand in a browser writes
 * {@code order.totalAmount > 100}, which compares a string to a number and is
 * wrong in a way TypeScript will not complain about.
 */
final class DddCondition {

    private DddCondition() {
    }

    /**
     * The condition as source, or null when it is not in the grammar.
     *
     * @param negated when true, returns the expression that is true exactly
     *                when the condition does <em>not</em> hold. Written by
     *                inverting the comparison rather than by wrapping the
     *                whole thing in a '!', which is exact where negating a
     *                rendered expression is how wrong null handling gets in.
     */
    static String expression(EntitySpec e, String condition, String var,
                             boolean negated, Syntax syntax) {
        if (condition == null) return null;

        String[] parts = condition.trim().split("\\s+");

        if (parts.length == 2) {
            FieldSpec f = fieldOf(e, parts[0]);
            if (f == null) return null;

            if ("notEmpty".equalsIgnoreCase(parts[1])) {
                if (!f.isList()) return null;
                return guard(var, f, syntax, negated) + emptiness(var, f, syntax, negated);
            }
            if ("notNull".equalsIgnoreCase(parts[1])) {
                return String.format("%s %s null",
                        read(var, f, syntax, false), negated ? "==" : "!=");
            }
            return null;
        }

        if (parts.length != 3) return null;

        String operator = parts[1];
        String comparison = comparison(operator, negated);
        if (comparison == null) return null;

        String bound = parts[2];
        if (!isNumber(bound)) return null;
        boolean equality = "==".equals(comparison) || "!=".equals(comparison);
        // TypeScript compares strictly; == would convert.
        if (equality && syntax == Syntax.TYPESCRIPT) comparison = comparison + "=";

        String term = parts[0];
        boolean size = term.endsWith(".size");
        FieldSpec f = fieldOf(e, size ? term.substring(0, term.length() - ".size".length()) : term);
        if (f == null) return null;

        // Null is a violation and is not a satisfaction: an aggregate with no
        // collection has not kept a promise about its members, and is not a
        // candidate a specification about them should select.
        String guard = guard(var, f, syntax, negated);

        // Kotlin has no == between two kinds of number (a Long and the Int literal 0, an Int and 1.5);
        // compareTo has. Only an Int against a whole number is compared as written.
        boolean whole = bound.indexOf('.') < 0;
        boolean kotlinEquality = equality && syntax == Syntax.KOTLIN;

        if (size) {
            if (!f.isList()) return null;
            if (kotlinEquality && !whole) {
                return guard + String.format("%s.compareTo(%s) %s 0", count(var, f, syntax), bound, comparison);
            }
            return guard + String.format("%s %s %s",
                    count(var, f, syntax), comparison, bound);
        }
        if (f.isList()) return null;

        if (f.isBigDecimal()) {
            return guard + decimalComparison(var, f, syntax, bound, comparison);
        }
        if (f.isMoney()) {
            if (syntax == Syntax.TYPESCRIPT) {
                return guard + String.format("Number(%s) %s %s", read(var, f, syntax, true), comparison, bound);
            }
            return guard + String.format("%s.compareTo(io.elasticore.springboot3.domain.Money.of(\"%s\")) %s 0",
                    read(var, f, syntax, true), bound, comparison);
        }
        if (f.isQuantity()) {
            if (syntax == Syntax.TYPESCRIPT) {
                return guard + String.format("%s %s %s", read(var, f, syntax, true), comparison, bound);
            }
            if (kotlinEquality) {
                return guard
                        + String.format("%s.value().compareTo(%s) %s 0", read(var, f, syntax, true), bound, comparison);
            }
            return guard + String.format("%s.value() %s %s", read(var, f, syntax, true), comparison, bound);
        }
        if (!isComparableNumber(f)) return null;

        String simple = f.typeName().substring(f.typeName().lastIndexOf('.') + 1);
        boolean anInt = "Int".equals(simple) || "Integer".equals(simple);
        if (kotlinEquality && !(anInt && whole)) {
            return guard + String.format("%s.compareTo(%s) %s 0", read(var, f, syntax, true), bound, comparison);
        }
        return guard + String.format("%s %s %s",
                read(var, f, syntax, true), comparison, bound);
    }

    /** The operator as written, or the one that holds exactly when it does not; null when not one. */
    static String comparison(String operator, boolean negated) {
        String[] pairs = {">", "<=", "<", ">=", ">=", "<", "<=", ">", "==", "!=", "!=", "=="};
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i].equals(operator)) return negated ? pairs[i + 1] : operator;
        }
        return null;
    }

    /** The null test in front of every comparison, in the direction asked for. */
    private static String guard(String var, FieldSpec f, Syntax syntax, boolean negated) {
        return String.format("%s %s null %s ", read(var, f, syntax, false),
                negated ? "==" : "!=", negated ? "||" : "&&");
    }

    /**
     * How generated code outside the type reads one field off it.
     *
     * @param asserted whether the read is about to be dereferenced, which in
     *                 Kotlin is where the nullable property needs its
     *                 assertion. The guard in front has already established
     *                 that it holds.
     */
    static String read(String var, FieldSpec f, Syntax syntax, boolean asserted) {
        if (syntax == Syntax.KOTLIN) {
            return String.format("%s.%s%s", var, f.name(), asserted ? "!!" : "");
        }
        if (syntax == Syntax.TYPESCRIPT) {
            return String.format("%s.%s", var, f.name());
        }
        return String.format("%s.get%s()", var, f.capitalName());
    }

    /** How many members a to-many holds. */
    private static String count(String var, FieldSpec f, Syntax syntax) {
        String read = read(var, f, syntax, true);
        if (syntax == Syntax.TYPESCRIPT) return read + ".length";
        if (syntax == Syntax.KOTLIN) return read + ".size";
        return read + ".size()";
    }

    /** Whether a to-many is empty, or is not, as each language asks it. */
    private static String emptiness(String var, FieldSpec f, Syntax syntax, boolean negated) {
        if (syntax == Syntax.TYPESCRIPT) {
            // No isEmpty on an array, and the count is the honest test anyway.
            return String.format("%s %s 0", count(var, f, syntax), negated ? "===" : ">");
        }
        String read = read(var, f, syntax, true);
        return (negated ? "" : "!") + read + ".isEmpty()";
    }

    /**
     * A decimal compared against a bound.
     *
     * On the wire it is a string - JSON's only number is a double, so a server
     * that sent 14.49 as a number would be sending a different value than it
     * holds. Reading it back as a number is the browser's business and is
     * where a hand-written comparison goes wrong silently: "14.49" &gt; 100 is
     * a string-to-number comparison TypeScript accepts and JavaScript answers
     * false to, whatever the amount.
     */
    private static String decimalComparison(String var, FieldSpec f, Syntax syntax,
                                            String bound, String comparison) {
        if (syntax == Syntax.TYPESCRIPT) {
            return String.format("Number(%s) %s %s", read(var, f, syntax, true), comparison, bound);
        }
        return String.format("%s.compareTo(%sjava.math.BigDecimal(\"%s\")) %s 0",
                read(var, f, syntax, true),
                syntax == Syntax.KOTLIN ? "" : "new ", bound, comparison);
    }

    static FieldSpec fieldOf(EntitySpec e, String name) {
        for (FieldSpec f : e.fields()) {
            if (f.name().equals(name)) return f;
        }
        return null;
    }

    /**
     * A number the generated comparison can be written against directly.
     *
     * The wrapper types compare with the operators; a primitive would too,
     * but the null guard in front of it would not compile, so this asks the
     * type rather than assuming either. Kotlin spells two of them its own way.
     */
    private static boolean isComparableNumber(FieldSpec f) {
        String type = f.typeName();
        if (type == null) return false;

        int dot = type.lastIndexOf('.');
        String simple = dot < 0 ? type : type.substring(dot + 1);

        return "Integer".equals(simple) || "Long".equals(simple)
                || "Short".equals(simple) || "Byte".equals(simple)
                || "Double".equals(simple) || "Float".equals(simple)
                || "Int".equals(simple);
    }

    private static boolean isNumber(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '.' || ch == '-') continue;
            if (ch < '0' || ch > '9') return false;
        }
        return true;
    }
}
