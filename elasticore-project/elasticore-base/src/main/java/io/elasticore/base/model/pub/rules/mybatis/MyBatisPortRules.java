package io.elasticore.base.model.pub.rules.mybatis;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.ModelCheck;
import io.elasticore.base.model.pub.rules.PortSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.base.RepositoryBaseRules;
import io.elasticore.base.model.pub.rules.base.RuleText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * A database port (port: meta dbms) of a MyBatis domain, as MyBatis code: a
 * mapper whose XML holds the port's SQL, and an adapter - the port's
 * implementation, a bean - that calls it. The SQL is the DSL's as written;
 * what changes is who runs it: MyBatis, with the domain's other statements,
 * where the port runtime ran it through a proxy.
 *
 * <ul>
 *   <li>:name binds the parameter of that name - or, for a method that takes
 *       one object (a search DTO), its property of that name; ${name} is put
 *       into the text as the runtime puts it.</li>
 *   <li>A line ending /* if:name *&#47; is kept when name has a value (a text
 *       that is not empty); a line starting with it keeps the lines down to
 *       the one marked if-end. A condition that is an expression rather than
 *       a name is the port runtime's (JEXL), and is refused here.</li>
 *   <li>A list, a page (a count and a page of rows) and one row are read as
 *       maps and turned into the method's type as the runtime turns them
 *       (PortRows): a DTO by column label, camel-cased; a value from the first
 *       column. A method named for a write that answers int runs an UPDATE,
 *       INSERT or DELETE - once per element for a list of objects.</li>
 *   <li>The sort a call names (sortCode, or sortColumn and sortAscending) is
 *       appended as ORDER BY where the SQL names none, after it is checked
 *       term by term as the runtime checks it.</li>
 * </ul>
 */
public class MyBatisPortRules implements CodeRules {

    public static final String MAPPER = "mybatisPortMapper";
    public static final String XML = "mybatisPortXml";
    public static final String ADAPTER = "mybatisPortAdapter";

    private static final String I = "    ";

    /** Whether a port is written as MyBatis code: a database port of a MyBatis domain. */
    public static boolean applies(PortSpec p) {
        return p != null && "dbms".equals(p.protocol()) && io.elasticore.base.model.pub.rules.RulesConfig.mybatis(p.mode())
                && !p.operations().isEmpty();
    }

    /** Where the mapper and the adapter go: with the domain's other adapters, else its mappers. */
    public static String packageOf(PortSpec p) {
        String pkg = p.pkg("persistence");
        return pkg == null || pkg.isEmpty() ? p.pkg("repository") : pkg;
    }

    // ---- reading one operation ------------------------------------------------

    enum Kind { WRITE, PAGE, LIST, VALUE, ROW }

    static final class Op {
        PortSpec.Operation op;
        Kind kind;
        String returnType;
        String item;
        /** The one object a method takes - its properties are the names - or null when it takes values by name. */
        String[] object;
        /** A list of objects a write runs once for each of. */
        boolean batch;
        boolean sorts;
        /** A list or page of one value per row: read as that value from the first column, NULL as null. */
        boolean values;
        String sql;
    }

    static Op read(PortSpec p, PortSpec.Operation op) {
        Op o = new Op();
        o.op = op;
        o.returnType = nullable(op.returnType());
        String arg = RepositoryBaseRules.typeArgument(o.returnType);
        o.item = arg != null ? arg : o.returnType;
        String lower = op.name().toLowerCase(Locale.ROOT);
        boolean writes = lower.contains("update") || lower.contains("delete") || lower.contains("insert");
        // A method that answers nothing runs its statement for what it does.
        if ("void".equals(o.returnType)) o.kind = Kind.WRITE;
        else if (("Integer".equals(o.returnType) || "int".equals(op.returnType())) && writes) o.kind = Kind.WRITE;
        else if (o.returnType.startsWith("Page<")) o.kind = Kind.PAGE;
        else if (o.returnType.startsWith("List<") || o.returnType.startsWith("java.util.List<")) o.kind = Kind.LIST;
        else if (singleValue(o.returnType)) o.kind = Kind.VALUE;
        else o.kind = Kind.ROW;
        List<String[]> params = new ArrayList<String[]>();
        for (String[] param : op.params()) {
            if (!"Pageable".equals(PortSpec.Operation.typeOf(param).trim())) params.add(param);
        }
        if (params.size() == 1 && !PortSpec.Operation.isSimple(params.get(0))) {
            String type = PortSpec.Operation.typeOf(params.get(0)).trim();
            if (o.kind == Kind.WRITE && (type.startsWith("List<") || type.startsWith("java.util.List<"))) {
                String element = RepositoryBaseRules.typeArgument(type);
                if (element != null && !PortSpec.Operation.isSimple(new String[]{element, "x"})) o.batch = true;
            }
            if (!type.startsWith("List<") && !type.startsWith("java.util.List<")) o.object = params.get(0);
            else if (o.batch) o.object = new String[]{RepositoryBaseRules.typeArgument(type), "row"};
        }
        o.sql = op.sql();
        if (o.sql == null || o.sql.trim().isEmpty()) {
            fail(p, op, "has no query; under mode: mybatis a database port's method runs the SQL its query names");
            return null;
        }
        // A sort comes with the call: an object's sortCode or sortColumn, or a parameter of that name.
        boolean carriesSort = o.object != null;
        for (String[] param : op.params()) {
            if ("sortCode".equals(param[1]) || "sortColumn".equals(param[1])) carriesSort = true;
        }
        o.sorts = carriesSort && o.kind != Kind.WRITE && o.kind != Kind.VALUE && !hasOrderBy(o.sql);
        o.values = (o.kind == Kind.LIST || o.kind == Kind.PAGE) && singleValue(o.item);
        return o;
    }

    private static boolean singleValue(String type) {
        String t = RuleText.simpleName(type.trim());
        return java.util.Arrays.asList("Long", "Integer", "Short", "Byte", "Double", "Float", "Boolean", "String",
                "Character", "BigDecimal", "BigInteger", "LocalDate", "LocalDateTime", "LocalTime", "Instant",
                "OffsetDateTime", "ZonedDateTime", "Date").contains(t);
    }

    private static String nullable(String type) {
        if (type == null) return "void";
        switch (type.trim()) {
            case "long": return "Long";
            case "int": return "Integer";
            case "short": return "Short";
            case "double": return "Double";
            case "float": return "Float";
            case "boolean": return "Boolean";
            default: return type.trim();
        }
    }

    /** Whether the SQL orders its rows itself, outside any parenthesis (its last ORDER BY). */
    private static boolean hasOrderBy(String sql) {
        return orderByAt(sql) >= 0;
    }

    private static final Pattern ORDER_BY = Pattern.compile("\\border\\s+by\\b");
    private static final Pattern PAGES = Pattern.compile("\\b(limit|offset|fetch)\\b");

    /**
     * The SQL in lower case with what is not its top level blanked out - the
     * text in parentheses, quotes and comments - each at its own place.
     */
    static String topLevel(String sql) {
        char[] out = sql.toLowerCase(Locale.ROOT).toCharArray();
        int depth = 0;
        for (int i = 0; i < out.length; i++) {
            char ch = out[i];
            int end = -1;
            if (ch == '\'' || ch == '"') {
                end = i + 1;
                while (end < out.length && out[end] != ch) end++;
            } else if (ch == '-' && i + 1 < out.length && out[i + 1] == '-') {
                end = i;
                while (end < out.length && out[end] != '\n') end++;
            } else if (ch == '/' && i + 1 < out.length && out[i + 1] == '*') {
                end = sql.indexOf("*/", i + 2);
                end = end < 0 ? out.length : end + 1;
            }
            if (end >= 0) {
                for (int j = i; j <= end && j < out.length; j++) if (out[j] != '\n') out[j] = ' ';
                i = end;
                continue;
            }
            if (ch == '(') depth++;
            if (depth > 0 && ch != '\n') out[i] = ' ';
            if (ch == ')') depth--;
        }
        return new String(out);
    }

    /** Where the SQL's own ORDER BY starts - the last one at its top level; -1 for none. */
    static int orderByAt(String sql) {
        Matcher m = ORDER_BY.matcher(topLevel(sql));
        int at = -1;
        while (m.find()) at = m.start();
        return at;
    }

    /**
     * The SQL without its own ORDER BY, for the count of a page: SQL Server
     * refuses one inside a derived table, and a count needs no order. One
     * that a LIMIT, OFFSET or FETCH follows stays: it says which rows there
     * are, and SQL Server takes it there. A conditional block the ORDER BY
     * stood in is still closed.
     */
    static String withoutOrderBy(String sql) {
        int at = orderByAt(sql);
        if (at < 0 || PAGES.matcher(topLevel(sql).substring(at)).find()) return sql;
        String head = sql.substring(0, at);
        int open = 0;
        for (String line : head.replace("\r\n", "\n").split("\n")) {
            if (line.trim().indexOf("/*") == 0 && IF.matcher(line).find()) open++;
            else if (line.contains("/*") && line.indexOf("if-end") > 0) open--;
        }
        StringBuilder out = new StringBuilder(head.trim());
        for (int i = 0; i < open; i++) out.append("\n/* if-end */");
        return out.toString();
    }

    private static void fail(PortSpec p, PortSpec.Operation op, String why) {
        ModelCheck.fail(String.format("[elcore3] port %s.%s %s", p.id(), op.name(), why));
    }

    // ---- the SQL as MyBatis XML -----------------------------------------------

    private static final Pattern IF = Pattern.compile("/\\* if:(.*?) \\*/");
    private static final Pattern BIND = Pattern.compile("(?<![:\\w]):([A-Za-z_][A-Za-z0-9_]*)|\\$\\{(\\w+)}");

    /** The port's SQL with its binds and conditional lines in MyBatis's words. */
    static String translate(PortSpec p, Op o) {
        StringBuilder out = new StringBuilder();
        boolean block = false;
        for (String line : o.sql.replace("\r\n", "\n").split("\n")) {
            if (block) {
                if (line.contains("/*") && line.indexOf("if-end") > 0) {
                    out.append("</if>\n");
                    block = false;
                } else {
                    out.append(text(p, o, line)).append('\n');
                }
                continue;
            }
            Matcher m = IF.matcher(line);
            if (m.find()) {
                String test = test(p, o, m.group(1).trim());
                if (line.trim().indexOf("/*") == 0) {
                    out.append("<if test=\"").append(test).append("\">\n");
                    block = true;
                } else {
                    String kept = line.substring(0, m.start()).replaceAll("\\s+$", "");
                    out.append("<if test=\"").append(test).append("\">").append(text(p, o, kept)).append("</if>\n");
                }
                continue;
            }
            out.append(text(p, o, line)).append('\n');
        }
        if (block) fail(p, o.op, "opens a /* if: */ block that no /* if-end */ line closes");
        return out.toString().trim();
    }

    /** A condition the runtime keeps a line by: a name with a value. An expression is JEXL's, and refused. */
    private static String test(PortSpec p, Op o, String condition) {
        if (!condition.matches("[a-zA-Z_$][a-zA-Z\\d_$]*")) {
            fail(p, o.op, "keeps a line by the expression '" + condition + "', which is the port runtime's (JEXL);"
                    + " under mode: mybatis a condition names a value - /* if:name */");
            return "false";
        }
        return "@io.elasticore.springboot3.bean.PortRows@present(" + expression(p, o, condition) + ")";
    }

    /** A line's text, its binds made MyBatis's and what XML reserves escaped. */
    private static String text(PortSpec p, Op o, String line) {
        StringBuilder sb = new StringBuilder();
        Matcher m = BIND.matcher(line);
        int at = 0;
        int scanned = 0;
        boolean quoted = false;
        while (m.find()) {
            // A :name inside a quoted literal is the literal's.
            for (int i = scanned; i < m.start(); i++) if (line.charAt(i) == '\'') quoted = !quoted;
            scanned = m.end();
            if (quoted) continue;
            sb.append(escape(line.substring(at, m.start())));
            if (m.group(1) != null) sb.append("#{").append(expression(p, o, m.group(1))).append('}');
            else sb.append("${").append(expression(p, o, m.group(2))).append('}');
            at = m.end();
        }
        sb.append(escape(line.substring(at)));
        return sb.toString();
    }

    /** What a name of the SQL reads: the parameter of that name, or the one object's property of that name. */
    private static String expression(PortSpec p, Op o, String name) {
        if (o.object != null) return "p." + name;
        for (String[] param : o.op.params()) {
            if (param[1].equals(name)) return name;
        }
        fail(p, o.op, "names :" + name + ", which is none of its parameters");
        return name;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final String PAGE = "<choose>"
            + "<when test=\"_databaseId == 'oracle' or _databaseId == 'sqlserver' or _databaseId == 'db2'\">"
            + " OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY</when>"
            + "<otherwise> LIMIT #{limit} OFFSET #{offset}</otherwise></choose>";

    // ---- the files --------------------------------------------------------------

    @Rule(value = "mybatis-port-mapper", target = MAPPER, order = 10)
    void mapper(PortSpec p) {
        SourceCode c = ctx(p);
        c.template("mybatis_portMapper");
        StringBuilder body = new StringBuilder();
        for (PortSpec.Operation op : p.operations()) {
            Op o = read(p, op);
            if (o == null) continue;
            String params = mapperParams(o);
            body.append(I).append("/** ").append(op.description()).append(" */\n");
            switch (o.kind) {
                case WRITE:
                    body.append(I).append("int ").append(op.name()).append('(').append(params).append(");\n\n");
                    break;
                case VALUE:
                    body.append(I).append("List<").append(o.returnType).append("> ").append(op.name()).append('(')
                        .append(params).append(");\n\n");
                    break;
                case PAGE:
                    body.append(I).append("List<").append(rowType(o)).append("> ").append(op.name()).append("Rows(")
                        .append(join(params, "@Param(\"orderBy\") String orderBy, @Param(\"offset\") long offset,"
                                + " @Param(\"limit\") int limit")).append(");\n\n");
                    body.append(I).append("long ").append(op.name()).append("Count(").append(params).append(");\n\n");
                    break;
                default:
                    body.append(I).append("List<").append(rowType(o)).append("> ").append(op.name()).append('(')
                        .append(o.sorts ? join(params, "@Param(\"orderBy\") String orderBy") : params).append(");\n\n");
            }
        }
        c.var("packageName", packageOf(p))
         .var("className", p.getName() + "Mapper")
         .var("portName", p.getName())
         .var("importList", imports(p))
         .var("body", RuleText.stripTrailing(body.toString()));
    }

    @Rule(value = "mybatis-port-xml", target = XML, order = 10)
    void xml(PortSpec p) {
        SourceCode c = ctx(p);
        c.template("mybatis_portXml");
        StringBuilder x = new StringBuilder();
        for (PortSpec.Operation op : p.operations()) {
            Op o = read(p, op);
            if (o == null) continue;
            String sql = translate(p, o);
            String order = o.sorts ? "<if test=\"orderBy != null\"> ORDER BY ${orderBy}</if>" : "";
            switch (o.kind) {
                case WRITE: {
                    String verb = o.sql.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
                    String kind = "insert".equals(verb) ? "insert" : "delete".equals(verb) ? "delete" : "update";
                    statement(x, kind, op.name(), "", sql);
                    break;
                }
                case VALUE:
                    statement(x, "select", op.name(), "resultType=\"" + valueType(o.returnType) + "\"", sql);
                    break;
                case PAGE: {
                    // SQL Server pages only ordered rows: an order of no column where none is named.
                    String unordered = hasOrderBy(o.sql) ? ""
                            : o.sorts ? "<if test=\"orderBy == null and _databaseId == 'sqlserver'\"> ORDER BY (SELECT NULL)</if>"
                            : "<if test=\"_databaseId == 'sqlserver'\"> ORDER BY (SELECT NULL)</if>";
                    statement(x, "select", op.name() + "Rows", resultType(o),
                            sql + "\n" + order + unordered + PAGE);
                    String counted = o.sql;
                    o.sql = withoutOrderBy(counted);
                    String countSql = translate(p, o);
                    o.sql = counted;
                    statement(x, "select", op.name() + "Count", "resultType=\"long\"",
                            "SELECT COUNT(*) FROM (\n" + countSql + "\n" + I + I + ") X");
                    break;
                }
                default:
                    statement(x, "select", op.name(), resultType(o),
                            order.isEmpty() ? sql : sql + "\n" + order);
            }
        }
        c.var("namespace", packageOf(p) + "." + p.getName() + "Mapper")
         .var("portName", p.getName())
         .var("body", RuleText.stripTrailing(x.toString()));
    }

    @Rule(value = "mybatis-port-adapter", target = ADAPTER, order = 10)
    void adapter(PortSpec p) {
        SourceCode c = ctx(p);
        c.template("mybatis_portAdapter");
        StringBuilder body = new StringBuilder();
        for (PortSpec.Operation op : p.operations()) {
            Op o = read(p, op);
            if (o == null) continue;
            StringBuilder sig = new StringBuilder();
            StringBuilder args = new StringBuilder();
            for (String[] param : op.params()) {
                if (sig.length() > 0) sig.append(", ");
                sig.append(param[0]).append(' ').append(param[1]);
                if ("Pageable".equals(PortSpec.Operation.typeOf(param).trim())) continue;
                if (args.length() > 0) args.append(", ");
                args.append(param[1]);
            }
            body.append('\n').append(I).append("@Override\n")
                .append(I).append("public ").append(o.returnType).append(' ').append(op.name()).append('(').append(sig)
                .append(") {\n");
            String call = args.toString();
            String input = input(o);
            switch (o.kind) {
                case WRITE: {
                    boolean answers = !"void".equals(o.returnType);
                    if (o.batch) {
                        String list = o.op.params().get(0)[1];
                        body.append(I).append(I).append("if (").append(list).append(" == null) return").append(answers ? " 0" : "").append(";\n")
                            .append(I).append(I).append("int total = 0;\n")
                            .append(I).append(I).append("for (").append(o.object[0]).append(" row : ").append(list)
                            .append(") if (row != null) total += mapper.").append(op.name()).append("(row);\n");
                        if (answers) body.append(I).append(I).append("return total;\n");
                    } else {
                        body.append(I).append(I).append(answers ? "return " : "").append("mapper.").append(op.name()).append('(')
                            .append(call).append(");\n");
                    }
                    break;
                }
                case VALUE:
                    body.append(I).append(I).append("List<").append(o.returnType).append("> rows = mapper.").append(op.name())
                        .append('(').append(call).append(");\n")
                        .append(I).append(I).append("return rows.isEmpty() ? null : rows.get(0);\n");
                    break;
                case PAGE:
                    body.append(I).append(I).append("Object portInput = ").append(input).append(";\n")
                        .append(I).append(I).append("Pageable pageable = PortRows.pageable(").append(pageableOf(o)).append(");\n")
                        .append(I).append(I).append("long total = mapper.").append(op.name()).append("Count(").append(call).append(");\n")
                        .append(I).append(I).append("List<").append(rowType(o)).append("> rows = mapper.").append(op.name())
                        .append("Rows(").append(join(call, "PortRows.orderBy(portInput), pageable.getOffset(), pageable.getPageSize()"))
                        .append(");\n")
                        .append(I).append(I).append("return new PageImpl<>(")
                        .append(o.values ? "rows" : "PortRows.list(rows, " + o.item + ".class)")
                        .append(", pageable, total);\n");
                    break;
                case LIST:
                    if (o.values) {
                        body.append(I).append(I).append("return mapper.").append(op.name()).append('(')
                            .append(o.sorts ? join(call, "PortRows.orderBy(" + input + ")") : call).append(");\n");
                        break;
                    }
                    body.append(I).append(I).append("return PortRows.list(mapper.").append(op.name()).append('(')
                        .append(o.sorts ? join(call, "PortRows.orderBy(" + input + ")") : call).append("), ")
                        .append(erasure(o.item)).append(".class);\n");
                    break;
                default:
                    body.append(I).append(I).append("return PortRows.first(mapper.").append(op.name()).append('(')
                        .append(o.sorts ? join(call, "PortRows.orderBy(" + input + ")") : call).append("), ")
                        .append(erasure(o.returnType)).append(".class);\n");
            }
            body.append(I).append("}\n");
        }
        c.var("packageName", packageOf(p))
         .var("className", p.getName() + "Adapter")
         .var("portName", p.getName())
         .var("portImport", p.pkg("port") + "." + p.getName())
         .var("mapperName", p.getName() + "Mapper")
         .var("importList", imports(p))
         .var("body", RuleText.stripTrailing(body.toString()));
    }

    /** What the mapper answers one row as: the value itself, or the row's columns by label. */
    private static String rowType(Op o) {
        return o.values ? o.item : "java.util.LinkedHashMap<String, Object>";
    }

    private static String resultType(Op o) {
        return "resultType=\"" + (o.values ? valueType(o.item) : "java.util.LinkedHashMap") + "\"";
    }

    /** What the call carries, as the runtime reads its sort and page: the one object, or the values by name. */
    private static String input(Op o) {
        if (o.object != null) return o.op.params().get(0)[1];
        StringBuilder sb = new StringBuilder("io.elasticore.springboot3.bean.PortRows.named(");
        boolean first = true;
        for (String[] param : o.op.params()) {
            if (!first) sb.append(", ");
            sb.append('"').append(param[1]).append("\", ").append(param[1]);
            first = false;
        }
        return sb.append(')').toString();
    }

    /** What the page is read off: a Pageable the method takes, else the call's input. */
    private static String pageableOf(Op o) {
        for (String[] param : o.op.params()) {
            if ("Pageable".equals(PortSpec.Operation.typeOf(param).trim())) return param[1];
        }
        return "portInput";
    }

    private static String erasure(String type) {
        int lt = type.indexOf('<');
        return lt < 0 ? type : type.substring(0, lt);
    }

    private static String mapperParams(Op o) {
        if (o.object != null) return "@Param(\"p\") " + o.object[0] + " p";
        StringBuilder sb = new StringBuilder();
        for (String[] param : o.op.params()) {
            if ("Pageable".equals(PortSpec.Operation.typeOf(param).trim())) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append("@Param(\"").append(param[1]).append("\") ").append(param[0]).append(' ').append(param[1]);
        }
        return sb.toString();
    }

    private static String valueType(String type) {
        String t = RuleText.simpleName(type.trim());
        switch (t) {
            case "Long": return "long";
            case "Integer": return "int";
            case "Short": return "short";
            case "Double": return "double";
            case "Float": return "float";
            case "Boolean": return "boolean";
            case "String": return "string";
            case "BigDecimal": case "BigInteger": return "java.math." + t;
            case "LocalDate": case "LocalDateTime": case "LocalTime": case "Instant": case "OffsetDateTime":
            case "ZonedDateTime": return "java.time." + t;
            case "Date": return "java.util.Date";
            case "Character": return "java.lang.Character";
            case "Byte": return "byte";
            default: return type;
        }
    }

    /** The port's model packages, as the port imports them: with a wildcard each. */
    private static String imports(PortSpec p) {
        StringBuilder sb = new StringBuilder();
        for (String key : new String[]{"entity", "dto", "enumeration"}) {
            String pkg = p.pkg(key);
            if (pkg != null && !pkg.isEmpty() && sb.indexOf("import " + pkg + ".*;") < 0) {
                sb.append("import ").append(pkg).append(".*;\n");
            }
        }
        return RuleText.stripTrailing(sb.toString());
    }

    private static String join(String a, String b) {
        if (a == null || a.isEmpty()) return b;
        return a + ", " + b;
    }

    private static void statement(StringBuilder x, String kind, String id, String attrs, String sql) {
        x.append(I).append('<').append(kind).append(" id=\"").append(id).append('"')
         .append(attrs.isEmpty() ? "" : " " + attrs).append(">\n")
         .append(I).append(I).append(sql.replace("\n", "\n" + I + I)).append('\n')
         .append(I).append("</").append(kind).append(">\n\n");
    }
}
