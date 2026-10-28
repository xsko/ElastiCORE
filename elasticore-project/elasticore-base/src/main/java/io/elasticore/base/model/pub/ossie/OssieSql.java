package io.elasticore.base.model.pub.ossie;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether an expression can honestly be declared in the Ossie language.
 *
 * The core spec defines the semantics of one dialect: OSSIE_SQL_2026, the
 * portable expression language of core-spec/expression_language.md, written
 * as an expression's top-level sql with no dialect named. Every other dialect
 * name - ANSI_SQL among them - is a free string for a vendor's language; a
 * consumer treats those expressions as opaque and may ignore them (spec.md,
 * "Dialects"). So what this exporter writes in the Ossie language is the
 * top-level sql, and only what that language cannot say is an ANSI_SQL
 * dialect entry.
 *
 * What it cannot say (expression_language.md, "Not Supported in
 * Expressions"): SELECT / FROM / JOIN, GROUP BY, WHERE, subqueries, CTEs, the
 * set operations, DDL and DML. Each of those is a query, and every query
 * carries one of the words below outside its string literals; an expression
 * - columns, arithmetic, comparisons, CASE, the functions, EXTRACT(year FROM
 * d) included - carries none. A column name such as select_flag is not a
 * word on its own and does not count.
 */
final class OssieSql {

    /** The dialect name for SQL the Ossie language cannot express; opaque to consumers. */
    static final String OPAQUE = "ANSI_SQL";

    private static final Pattern QUERY_WORD = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_])(select|insert|update|delete|merge|create|alter|drop|union|intersect|except|with)(?![A-Za-z0-9_])");

    private static final Pattern QUOTED = Pattern.compile("'(?:[^']|'')*'|\"(?:[^\"\\\\]|\\\\.)*\"");

    private OssieSql() {
    }

    /** Whether the expression stays inside the Ossie language: no query word outside its string literals. */
    static boolean portable(String sql) {
        if (sql == null) return true;
        Matcher quoted = QUOTED.matcher(sql);
        String bare = quoted.replaceAll(" ");
        return !QUERY_WORD.matcher(bare).find();
    }
}
