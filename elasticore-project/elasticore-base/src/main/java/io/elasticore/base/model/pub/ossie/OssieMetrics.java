package io.elasticore.base.model.pub.ossie;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The metrics: section of the DSL, read into Apache Ossie metrics.
 *
 * A metric is one line - an aggregate over one entity, optionally filtered,
 * or arithmetic over metrics declared before it:
 *
 * <pre>
 * metrics:
 *   revenue:      sum(PurchaseOrder.total) where status != CANCELLED @terms(sales) -- Revenue
 *   orderCount:   count(PurchaseOrder) where status != CANCELLED -- Orders
 *   averageOrder: revenue / orderCount -- Average order
 * </pre>
 *
 * Aggregates are sum, avg, min, max and count, and count may count a field's
 * distinct values: count(distinct Login.userId). A filter is conditions on the
 * entity's fields, compared with =, !=, &lt;, &lt;=, &gt;, &gt;= against a value, or
 * with in (...) and not in (...) against a list of them. A value is a number, a
 * 'text', true/false or an enum constant (written as the constant, stored as
 * its code). Conditions join with "and" and "or", "and" binding tighter as in
 * SQL; parentheses group them, and "not" goes before a parenthesis. The SQL is
 * ANSI and names the dataset and its columns, as Ossie's own examples do:
 * SUM(orders.amount).
 *
 * A line that cannot be read is said and left out - the export never guesses.
 */
final class OssieMetrics {

    /** What the exporter knows about the model, asked by name. */
    interface Model {
        /** The dataset an entity is exported as, or null. */
        String dataset(String entity);

        /** The column of an entity's field, or null when it has none. */
        String column(String entity, String field);

        /** The Ossie data type of an entity's field, or null. */
        String datatype(String entity, String field);

        /**
         * The SQL literal an enum constant is stored as in the field's column
         * - its @db code ('CAN'), its name, or its ordinal (2) - or null when
         * the field is no enum or the enum has no such constant.
         */
        String enumLiteral(String entity, String field, String constant);

        /** Whether the field's type is an enumeration. */
        boolean isEnum(String entity, String field);

        /** The column of the entity's key (the first, for a composite one), or null. */
        default String keyColumn(String entity) {
            return null;
        }
    }

    static final class Metric {
        final String name;
        final String sql;
        final String datatype;
        final String label;
        final List<String> terms;
        final String declared;
        /** The datasets it aggregates over, also through the metrics it is made of. */
        final List<String> datasets;

        Metric(
                String name,
                String sql,
                String datatype,
                String label,
                List<String> terms,
                String declared,
                List<String> datasets) {
            this.name = name;
            this.sql = sql;
            this.datatype = datatype;
            this.label = label;
            this.terms = terms;
            this.declared = declared;
            this.datasets = datasets;
        }
    }

    private static final Pattern AGGREGATE = Pattern.compile(
            "(?i)^(sum|avg|min|max|count)\\s*\\(\\s*(distinct\\s+)?([A-Za-z_]\\w*)(?:\\.([A-Za-z_]\\w*))?\\s*\\)"
                    + "(?:\\s+where\\s+(.+))?$");
    private static final Pattern CONDITION = Pattern.compile("^([A-Za-z_]\\w*)\\s*(==|=|!=|<>|>=|<=|>|<)\\s*(.+)$");
    private static final Pattern IN_LIST = Pattern.compile("(?i)^([A-Za-z_]\\w*)\\s+(not\\s+)?in\\s*\\((.*)\\)$");
    private static final Pattern NOT_GROUP = Pattern.compile("(?i)^not\\s*(\\(.*)$");
    private static final Pattern ARITHMETIC = Pattern.compile(
            "^([A-Za-z_]\\w*|\\d+(?:\\.\\d+)?)\\s*([-+*/])\\s*([A-Za-z_]\\w*|\\d+(?:\\.\\d+)?)$");
    private static final Pattern TERMS = Pattern.compile("@terms\\s*\\(([^)]*)\\)");

    private final Model model;
    private final Map<String, Metric> done = new LinkedHashMap<String, Metric>();
    private final List<String> problems = new ArrayList<String>();

    OssieMetrics(Model model) {
        this.model = model;
    }

    /** Problems found so far, one sentence each. */
    List<String> problems() {
        return problems;
    }

    /** Reads one metric; null - and a problem said - when it cannot be read. */
    Metric read(String name, String line) {
        String text = line;
        String label = null;
        int dash = outsideQuotes(text, " -- ");
        if (dash < 0 && text.startsWith("-- ")) dash = 0;
        if (dash >= 0) {
            label = text.substring(dash + (dash == 0 ? 3 : 4)).trim();
            text = text.substring(0, dash);
        }
        List<String> terms = terms(text);
        text = TERMS.matcher(text).replaceAll("").trim();

        // Not an aggregate at all (no problem said): then it is arithmetic over metrics.
        int before = problems.size();
        Metric m = aggregate(name, text, label, terms, line);
        if (m == null && problems.size() == before) m = arithmetic(name, text, label, terms, line);
        if (m != null) done.put(name, m);
        return m;
    }

    static List<String> terms(String text) {
        List<String> out = new ArrayList<String>();
        Matcher t = TERMS.matcher(text == null ? "" : text);
        while (t.find()) {
            for (String part : t.group(1).split(",")) {
                String s = unquote(part.trim());
                if (!s.isEmpty() && !out.contains(s)) out.add(s);
            }
        }
        return out;
    }

    private Metric aggregate(String name, String text, String label, List<String> terms, String line) {
        Matcher a = AGGREGATE.matcher(text);
        if (!a.matches()) return null;
        String fn = a.group(1).toUpperCase();
        boolean distinct = a.group(2) != null;
        String entity = a.group(3);
        String field = a.group(4);
        String where = a.group(5);

        if (distinct && !"COUNT".equals(fn)) {
            return fail(name, "distinct goes only with count, not with " + fn.toLowerCase());
        }
        String ds = model.dataset(entity);
        if (ds == null) return fail(name, entity + " is not an entity with a table");
        if (distinct && field == null) {
            return fail(name, "count(distinct ...) needs a field: count(distinct " + entity + ".field)");
        }
        String column = null;
        if (field != null) {
            column = model.column(entity, field);
            if (column == null) return fail(name, entity + " has no column for " + field);
        } else if (!"COUNT".equals(fn)) {
            return fail(name, fn.toLowerCase() + " needs a field: " + fn.toLowerCase() + "(" + entity + ".field)");
        }
        if (("SUM".equals(fn) || "AVG".equals(fn)) && !NUMBERS.contains(String.valueOf(model.datatype(entity, field)))) {
            return fail(name, fn.toLowerCase() + " needs a number, and " + entity + "." + field + " is "
                    + model.datatype(entity, field));
        }

        String condition = null;
        if (where != null) {
            condition = filter(name, entity, ds, where);
            if (condition == null) return null;
        }

        String target = column != null ? ds + "." + column : null;
        String sql;
        // count(Entity) counts the rows by their key, which is never null:
        // COUNT(*) names no dataset, and a BI tool could not tell which table
        // it counts. A condition already names the dataset's columns.
        if (target == null && condition == null && model.keyColumn(entity) != null) {
            target = ds + "." + model.keyColumn(entity);
        }
        if ("COUNT".equals(fn)) {
            String counts = distinct ? "COUNT(DISTINCT " : "COUNT(";
            if (condition == null) sql = target != null ? counts + target + ")" : "COUNT(*)";
            else sql = counts + "CASE WHEN " + condition + " THEN " + (target != null ? target : "1") + " END)";
        } else {
            sql = fn + "(" + (condition == null ? target : "CASE WHEN " + condition + " THEN " + target + " END") + ")";
        }

        String datatype;
        if ("COUNT".equals(fn)) datatype = "Integer";
        else if ("AVG".equals(fn)) datatype = "Float".equals(model.datatype(entity, field)) ? "Float" : "Decimal";
        else datatype = model.datatype(entity, field);
        return new Metric(name, sql, datatype, label, terms, line, java.util.Collections.singletonList(ds));
    }

    /** A filter as SQL, or null with the problem said. */
    private String filter(String metric, String entity, String ds, String where) {
        if (!balanced(where)) {
            fail(metric, "the parentheses of '" + where + "' do not balance");
            return null;
        }
        return anyOf(metric, entity, ds, where);
    }

    /** Conditions joined by "or", which binds loosest. */
    private String anyOf(String metric, String entity, String ds, String text) {
        List<String> out = new ArrayList<String>();
        for (String part : splitOnWord(text, "or")) {
            if (part.trim().isEmpty()) return nothingBeside(metric, "or", text);
            String sql = allOf(metric, entity, ds, part);
            if (sql == null) return null;
            out.add(sql);
        }
        return join(out, " OR ");
    }

    /** Conditions joined by "and". */
    private String allOf(String metric, String entity, String ds, String text) {
        List<String> out = new ArrayList<String>();
        for (String part : splitOnWord(text, "and")) {
            if (part.trim().isEmpty()) return nothingBeside(metric, "and", text);
            String sql = term(metric, entity, ds, part.trim());
            if (sql == null) return null;
            out.add(sql);
        }
        return join(out, " AND ");
    }

    private String nothingBeside(String metric, String word, String text) {
        fail(metric, "'" + text.trim() + "' has an " + word + " with nothing on one side of it");
        return null;
    }

    /** One condition, a list of values, or a group of conditions. */
    private String term(String metric, String entity, String ds, String text) {
        Matcher not = NOT_GROUP.matcher(text);
        if (not.matches()) {
            String group = group(metric, entity, ds, not.group(1));
            return group == null ? null : "NOT " + group;
        }
        if (text.startsWith("(")) return group(metric, entity, ds, text);
        Matcher list = IN_LIST.matcher(text);
        if (list.matches()) return inList(metric, entity, ds, list.group(1), list.group(2) != null, list.group(3));
        return condition(metric, entity, ds, text);
    }

    private String group(String metric, String entity, String ds, String text) {
        if (closingOf(text) != text.length() - 1) {
            fail(metric, "'" + text + "' is not FIELD OP VALUE");
            return null;
        }
        String inside = text.substring(1, text.length() - 1);
        if (inside.trim().isEmpty()) {
            fail(metric, "'" + text + "' has nothing in its parentheses");
            return null;
        }
        String sql = anyOf(metric, entity, ds, inside);
        return sql == null ? null : "(" + sql + ")";
    }

    /** Every value is read as a single one is, so a constant the enum does not have is named. */
    private String inList(String metric, String entity, String ds, String field, boolean not, String list) {
        String column = model.column(entity, field);
        if (column == null) {
            fail(metric, entity + " has no column for " + field);
            return null;
        }
        if (list.trim().isEmpty()) {
            fail(metric, field + " in () lists no value");
            return null;
        }
        List<String> values = new ArrayList<String>();
        for (String each : splitOutsideQuotes(list, ",")) {
            if (each.trim().isEmpty()) {
                fail(metric, field + " in (" + list.trim() + ") has an empty value");
                return null;
            }
            String value = literal(metric, entity, field, each.trim());
            if (value == null) return null;
            values.add(value);
        }
        return ds + "." + column + (not ? " NOT IN (" : " IN (") + join(values, ", ") + ")";
    }

    private String condition(String metric, String entity, String ds, String text) {
        Matcher c = CONDITION.matcher(text);
        if (!c.matches()) {
            fail(metric, "'" + text + "' is not FIELD OP VALUE");
            return null;
        }
        String field = c.group(1);
        String op = c.group(2);
        String value = c.group(3).trim();
        String column = model.column(entity, field);
        if (column == null) {
            fail(metric, entity + " has no column for " + field);
            return null;
        }
        if ("==".equals(op)) op = "=";
        if ("!=".equals(op)) op = "<>";

        String sqlValue = literal(metric, entity, field, value);
        return sqlValue == null ? null : ds + "." + column + " " + op + " " + sqlValue;
    }

    /** A value as SQL, or null with the problem said. */
    private String literal(String metric, String entity, String field, String value) {
        String sqlValue;
        if (value.matches("-?\\d+(\\.\\d+)?")) sqlValue = value;
        else if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) sqlValue = value.toUpperCase();
        else if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            String inner = value.substring(1, value.length() - 1);
            // A quote inside must be doubled; one alone means more than one value was written.
            if (inner.replace("''", "").contains("'")) {
                fail(metric, "cannot read the value " + value + " - write one value, a quote inside doubled ('')");
                return null;
            }
            sqlValue = value;
        } else if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            String inner = value.substring(1, value.length() - 1);
            if (inner.contains("\"")) {
                fail(metric, "cannot read the value " + value + " - write one value");
                return null;
            }
            sqlValue = "'" + inner.replace("'", "''") + "'";
        } else if (value.matches("[A-Za-z_]\\w*")) {
            if (!model.isEnum(entity, field)) {
                fail(metric, entity + "." + field + " is not an enum, so " + value + " needs quotes");
                return null;
            }
            sqlValue = model.enumLiteral(entity, field, value);
            if (sqlValue == null) {
                fail(metric, entity + "." + field + " has no constant " + value);
                return null;
            }
        } else {
            fail(metric, "cannot read the value " + value);
            return null;
        }
        return sqlValue;
    }

    private Metric arithmetic(String name, String text, String label, List<String> terms, String line) {
        Matcher m = ARITHMETIC.matcher(text);
        if (!m.matches()) {
            return fail(name, "'" + text + "' is neither an aggregate - sum|avg|min|max|count(Entity.field) [where ...] -"
                    + " nor metric OP metric");
        }
        String[] sides = {m.group(1), m.group(3)};
        String[] sql = new String[2];
        String[] types = new String[2];
        List<String> datasets = new ArrayList<String>();
        for (int i = 0; i < 2; i++) {
            if (sides[i].matches("\\d+(\\.\\d+)?")) {
                sql[i] = sides[i];
                types[i] = sides[i].contains(".") ? "Decimal" : "Integer";
                continue;
            }
            Metric other = done.get(sides[i]);
            if (other == null) return fail(name, sides[i] + " is not a metric declared before it");
            sql[i] = "(" + other.sql + ")";
            types[i] = other.datatype;
            for (String ds : other.datasets) if (!datasets.contains(ds)) datasets.add(ds);
        }
        String op = m.group(2);
        // Two whole numbers divide as whole numbers in most databases (3 / 4 = 0): the ratio is made a decimal first.
        boolean whole = "Integer".equals(types[0]) && "Integer".equals(types[1]);
        String expression = "/".equals(op)
                ? (whole ? "CAST(" + sql[0] + " AS DECIMAL(38, 10))" : sql[0]) + " / NULLIF(" + sql[1] + ", 0)"
                : sql[0] + " " + op + " " + sql[1];
        String datatype = "/".equals(op) ? ("Float".equals(types[0]) || "Float".equals(types[1]) ? "Float" : "Decimal")
                : ("Integer".equals(types[0]) && "Integer".equals(types[1]) ? "Integer" : "Decimal");
        return new Metric(name, expression, datatype, label, terms, line, datasets);
    }

    private static final java.util.Set<String> NUMBERS =
            new java.util.HashSet<String>(java.util.Arrays.asList("Integer", "Decimal", "Float"));

    /** Where a separator first stands outside quotes, or -1. */
    static int outsideQuotes(String text, String separator) {
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (text.regionMatches(true, i, separator, 0, separator.length())) {
                return i;
            }
        }
        return -1;
    }

    /** The parts between separators that stand outside quotes. */
    static List<String> splitOutsideQuotes(String text, String separator) {
        List<String> parts = new ArrayList<String>();
        String rest = text;
        for (int at = outsideQuotes(rest, separator); at >= 0; at = outsideQuotes(rest, separator)) {
            parts.add(rest.substring(0, at));
            rest = rest.substring(at + separator.length());
        }
        parts.add(rest);
        return parts;
    }

    /** Whether every parenthesis outside quotes has its other half. */
    private static boolean balanced(String text) {
        char quote = 0;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth < 0) {
                return false;
            }
        }
        return depth == 0;
    }

    /** Where the parenthesis that opens the text closes, in a text that balances. */
    private static int closingOf(String text) {
        char quote = 0;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** The parts between a word that joins conditions: outside quotes and parentheses, in any case. */
    private static List<String> splitOnWord(String text, String word) {
        List<String> parts = new ArrayList<String>();
        char quote = 0;
        int depth = 0;
        int from = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0 && joinsAt(text, i, word)) {
                parts.add(text.substring(from, i));
                from = i + word.length();
                i = from - 1;
            }
        }
        parts.add(text.substring(from));
        return parts;
    }

    /**
     * Whether the word stands here as a word that joins. Right after an operator
     * it is a value, an enum constant such as region = OR, and right before one
     * it is a field's name: both read as they always did.
     */
    private static boolean joinsAt(String text, int at, String word) {
        if (!text.regionMatches(true, at, word, 0, word.length())) return false;
        int end = at + word.length();
        if (at > 0 && !endsAWord(text.charAt(at - 1))) return false;
        if (end < text.length() && !endsAWord(text.charAt(end))) return false;
        String before = text.substring(0, at).trim();
        String after = text.substring(end).trim();
        boolean isValue = !before.isEmpty() && "=<>!".indexOf(before.charAt(before.length() - 1)) >= 0;
        boolean isField = !after.isEmpty() && "=<>!".indexOf(after.charAt(0)) >= 0;
        return !isValue && !isField;
    }

    private static boolean endsAWord(char c) {
        return Character.isWhitespace(c) || c == '(' || c == ')';
    }

    private Metric fail(String metric, String why) {
        problems.add("metric " + metric + ": " + why + "; it is left out.");
        return null;
    }

    private static String unquote(String s) {
        String t = s.trim();
        if (t.length() >= 2 && (t.startsWith("'") && t.endsWith("'") || t.startsWith("\"") && t.endsWith("\""))) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static String join(List<String> items, String sep) {
        StringBuilder b = new StringBuilder();
        for (String s : items) {
            if (b.length() > 0) b.append(sep);
            b.append(s);
        }
        return b.toString();
    }
}
