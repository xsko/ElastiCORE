package io.elasticore.base.model.pub.rules.schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which of the four databases reserves a word a table or a column is named
 * with (the generated SQL quotes nothing, as Hibernate does not by default) -
 * each from that database's own list of reserved words, for the words the
 * checks look for. The warning names the databases a domain writes for
 * (ddl.databases), or those that reserve the word when it writes for none:
 * comment, level and size are refused by Oracle alone (issue 168).
 */
public final class ReservedWords {

    private ReservedWords() {}

    private static Set<String> words(String... words) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(words)));
    }

    static final Set<String> ORACLE = words(
            "access",
            "add",
            "all",
            "alter",
            "and",
            "any",
            "as",
            "asc",
            "audit",
            "between",
            "by",
            "check",
            "cluster",
            "column",
            "comment",
            "compress",
            "connect",
            "create",
            "current",
            "date",
            "decimal",
            "default",
            "delete",
            "desc",
            "distinct",
            "drop",
            "else",
            "exclusive",
            "exists",
            "file",
            "float",
            "for",
            "from",
            "grant",
            "group",
            "having",
            "identified",
            "immediate",
            "in",
            "increment",
            "index",
            "initial",
            "insert",
            "integer",
            "intersect",
            "into",
            "is",
            "level",
            "like",
            "lock",
            "long",
            "minus",
            "mode",
            "modify",
            "noaudit",
            "nocompress",
            "not",
            "nowait",
            "null",
            "number",
            "of",
            "offline",
            "on",
            "online",
            "option",
            "or",
            "order",
            "prior",
            "privileges",
            "public",
            "raw",
            "rename",
            "resource",
            "revoke",
            "row",
            "rowid",
            "rownum",
            "rows",
            "select",
            "session",
            "set",
            "share",
            "size",
            "smallint",
            "start",
            "successful",
            "synonym",
            "sysdate",
            "table",
            "then",
            "to",
            "trigger",
            "uid",
            "union",
            "unique",
            "update",
            "user",
            "validate",
            "values",
            "varchar",
            "varchar2",
            "view",
            "whenever",
            "where",
            "with");

    static final Set<String> POSTGRESQL = words(
            "all",
            "and",
            "any",
            "as",
            "asc",
            "case",
            "check",
            "column",
            "constraint",
            "create",
            "cross",
            "current_date",
            "current_time",
            "current_timestamp",
            "current_user",
            "default",
            "desc",
            "distinct",
            "else",
            "end",
            "except",
            "for",
            "foreign",
            "from",
            "full",
            "grant",
            "group",
            "having",
            "in",
            "inner",
            "intersect",
            "into",
            "is",
            "join",
            "left",
            "like",
            "limit",
            "natural",
            "not",
            "null",
            "offset",
            "on",
            "or",
            "order",
            "outer",
            "primary",
            "references",
            "right",
            "select",
            "table",
            "then",
            "to",
            "union",
            "unique",
            "user",
            "where",
            "with");

    static final Set<String> SQLSERVER = words(
            "add",
            "all",
            "alter",
            "and",
            "any",
            "as",
            "asc",
            "between",
            "by",
            "case",
            "check",
            "column",
            "constraint",
            "create",
            "cross",
            "current",
            "current_date",
            "current_time",
            "current_timestamp",
            "current_user",
            "default",
            "delete",
            "desc",
            "distinct",
            "drop",
            "else",
            "end",
            "except",
            "exists",
            "file",
            "for",
            "foreign",
            "from",
            "full",
            "grant",
            "group",
            "having",
            "identity",
            "in",
            "index",
            "inner",
            "insert",
            "intersect",
            "into",
            "is",
            "join",
            "key",
            "left",
            "like",
            "not",
            "null",
            "of",
            "on",
            "option",
            "or",
            "order",
            "outer",
            "percent",
            "plan",
            "primary",
            "public",
            "references",
            "revoke",
            "right",
            "rule",
            "select",
            "set",
            "table",
            "then",
            "to",
            "top",
            "trigger",
            "union",
            "unique",
            "update",
            "user",
            "values",
            "view",
            "where",
            "with");

    static final Set<String> H2 = words(
            "all",
            "and",
            "any",
            "as",
            "between",
            "case",
            "check",
            "constraint",
            "cross",
            "current_date",
            "current_time",
            "current_timestamp",
            "current_user",
            "default",
            "distinct",
            "else",
            "end",
            "except",
            "exists",
            "for",
            "foreign",
            "from",
            "full",
            "group",
            "having",
            "in",
            "inner",
            "intersect",
            "is",
            "join",
            "key",
            "left",
            "like",
            "limit",
            "minus",
            "natural",
            "not",
            "null",
            "offset",
            "on",
            "or",
            "order",
            "primary",
            "right",
            "row",
            "rownum",
            "rows",
            "select",
            "set",
            "table",
            "to",
            "top",
            "union",
            "unique",
            "user",
            "values",
            "where",
            "with");

    private static final Map<Database, Set<String>> BY_DATABASE = new LinkedHashMap<Database, Set<String>>();

    static {
        BY_DATABASE.put(Database.ORACLE, ORACLE);
        BY_DATABASE.put(Database.POSTGRESQL, POSTGRESQL);
        BY_DATABASE.put(Database.SQLSERVER, SQLSERVER);
        BY_DATABASE.put(Database.H2, H2);
    }

    /** The databases the current domain writes its DDL for; empty when it writes none. */
    private static volatile List<Database> declared = Collections.emptyList();

    public static void declared(List<Database> databases) {
        declared = databases == null ? Collections.<Database>emptyList() : new ArrayList<Database>(databases);
    }

    /**
     * The databases to name for a word: of those the domain writes for, the
     * ones that reserve it; of all four, when it writes for none. Empty: no
     * database of those reserves it.
     */
    public static List<Database> refusing(String word) {
        String w = word.toLowerCase(java.util.Locale.ROOT);
        List<Database> out = new ArrayList<Database>();
        for (Map.Entry<Database, Set<String>> en : BY_DATABASE.entrySet()) {
            if (!declared.isEmpty() && !declared.contains(en.getKey())) continue;
            if (en.getValue().contains(w)) out.add(en.getKey());
        }
        return out;
    }

    /** Whether any of the four reserves the word. */
    static boolean anywhere(String word) {
        String w = word.toLowerCase(java.util.Locale.ROOT);
        for (Set<String> s : BY_DATABASE.values()) {
            if (s.contains(w)) return true;
        }
        return false;
    }

    /** "Oracle", "PostgreSQL and SQL Server". */
    public static String names(List<Database> databases) {
        List<String> names = new ArrayList<String>();
        for (Database db : databases) names.add(nameOf(db));
        if (names.size() <= 1) return names.isEmpty() ? "" : names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    private static String nameOf(Database db) {
        switch (db) {
            case ORACLE:
                return "Oracle";
            case POSTGRESQL:
                return "PostgreSQL";
            case SQLSERVER:
                return "SQL Server";
            default:
                return "H2";
        }
    }

    /**
     * The warning for names a table's DDL cannot create unquoted: each named
     * with the databases that refuse it, of those the domain writes for. Null
     * when none of them refuses any.
     */
    public static String warning(String domain, String entity, List<String[]> kindAndName) {
        List<String> said = new ArrayList<String>();
        for (String[] kn : kindAndName) {
            List<Database> by = refusing(kn[1]);
            if (by.isEmpty()) continue;
            said.add(String.format("the %s %s (%s)", kn[0], kn[1], names(by)));
        }
        if (said.isEmpty()) return null;
        return String.format(
                "[%s] %s: %s %s there and cannot be created unquoted - name it otherwise with @table / @column.",
                domain, entity, String.join(", ", said), said.size() > 1 ? "are reserved words" : "is a reserved word");
    }
}
