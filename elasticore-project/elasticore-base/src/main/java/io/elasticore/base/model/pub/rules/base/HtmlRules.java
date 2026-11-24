package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.ModelCheck;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.mongo.MongoDocs;
import io.elasticore.base.model.pub.rules.mybatis.MyBatisSql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * An {@code html} field: markup from a rich-text editor, cleaned before it is saved (issues 183, 217).
 *
 * <pre>
 * Post:
 *   fields:
 *     content:     html! @safe(basic) @plain(contentText)
 *     contentText: text @search(like)
 * </pre>
 *
 * <p>The CoreService gets a pair of methods per html field, which the developer's service (PostService extends
 * PostCoreService) overrides - plain Java and Kotlin methods, nothing to implement:
 *
 * <pre>
 * protected String cleanContent(Post row, String html) { return Html.clean(html, Safelist.basic()); } // @safe
 * protected String plainContent(Post row, String html) { return Html.plain(html); }                  // @plain
 * </pre>
 *
 * <p>The entity sets the fields itself - under the DDD rule sets the {@code @plain} copy and a field no request edits
 * have setters of its own - from what the service hands it: {@code entity.cleanContent(html -> cleanContent(entity,
 * html), html -> plainContent(entity, html))}. Neither method is called with null; a field set to null leaves its copy
 * null. The service calls it after the request (or the command) changed the row and before the developer's
 * beforeSave, and only where the field changed: on a create unless it is null, on an update and an update by
 * condition where the request changed it (the value read is kept before the mapper writes the row), after a command
 * where the command changed it. A refusal thrown from the developer's method is answered as one from beforeSave, and
 * nothing is written.
 *
 * <p>An html field on a member or a value object is refused (prepareHtml): no service of theirs writes them. Under
 * MongoDB an entity without a collection of its own is kept inside the document that holds it, and the root's mapper
 * writes it from the request: the root's service gets the member's pair too and cleans such a member on every write
 * of the root (memberStatements). The runtime's {@code Html} is only compiled against jsoup, and the generated method
 * names the safelist, so a project with an html field and without jsoup stops where that line compiles. v3 and
 * Kotlin, JPA, MyBatis and MongoDB. The 2.x-compatible output stops the build here, and the 2.x generator on the
 * loader's DSL warning.
 */
public class HtmlRules implements CodeRules {

    /** The annotation the loader gives a field of type html. */
    public static final String HTML = "html";

    /**
     * The service skeletons' slots: the values an update read (before the mapper writes the row), the cleaning after
     * an update, and the cleaning after a create. Declared by each service file rule; empty, they leave no line.
     */
    public static final List<String> SLOTS = java.util.Collections.unmodifiableList(
            java.util.Arrays.asList("htmlBefore", "htmlClean", "htmlCleanCreate"));

    private static final String RUNTIME = "io.elasticore.springboot3.html.Html";

    /** The jsoup safelist an @safe value names - basic when it names none - or null for any other value. */
    public static String safelist(String safe) {
        String s = safe == null ? "" : safe.trim();
        if (s.isEmpty() || "basic".equals(s)) return "basic";
        return "relaxed".equals(s) ? "relaxed" : null;
    }

    /** The entity's html fields, a parent's (@extend) included, in the order they are declared. */
    public static List<FieldSpec> htmlFields(EntitySpec e) {
        List<FieldSpec> out = new ArrayList<FieldSpec>();
        for (FieldSpec f : e.allFields()) {
            if (f.hasDsl(HTML)) out.add(f);
        }
        return out;
    }

    /** The field @plain names, or null. */
    static String plainTarget(FieldSpec f) {
        String plain = f.dslValue("plain");
        return plain == null || plain.trim().isEmpty() ? null : plain.trim();
    }

    /**
     * The service cleans the row before each write. Before the commands are written (30), which clean after their
     * move too, and before the values the model derives (40).
     */
    @Rule(value = "service-html", target = "service", order = 26)
    void service(EntitySpec e) {
        List<FieldSpec> html = htmlFields(e);
        SourceCode c = ctx(e);
        if (c.compatible()) {
            if (html.isEmpty()) return;
            ModelCheck.fail(String.format(
                    "[%s] %s.%s: an html field is cleaned before it is saved by the 3.0 code; the 2.x-compatible"
                            + " output would store what a request sends - generate the 3.0 code (output v3), or"
                            + " declare it text",
                    e.domainClassName(), e.getName(), html.get(0).name()));
            return;
        }
        Syntax s = c.syntax();
        Map<String, EntitySpec> owners = new LinkedHashMap<String, EntitySpec>();
        List<String> members = MongoDocs.applies(e) ? memberStatements(s, e, owners) : new ArrayList<String>();
        if (html.isEmpty() && members.isEmpty()) return;

        List<String> before = new ArrayList<String>();
        List<String> clean = new ArrayList<String>();
        List<String> create = new ArrayList<String>();
        for (FieldSpec f : html) {
            String name = s.escapeName(f.name());
            String read = s.getter("entity", name);
            String kept = f.name() + "Before";
            String call = call(s, "entity", f);
            if (s == Syntax.KOTLIN) {
                before.add(String.format("val %s = %s", kept, read));
                clean.add(String.format("if (%s != %s) %s", read, kept, call));
                create.add(String.format("if (%s != null) %s", read, call));
            } else {
                before.add(String.format("String %s = %s;", kept, read));
                clean.add(String.format("if (!java.util.Objects.equals(%s, %s))", read, kept));
                clean.add("    " + s.statement(call));
                create.add(String.format("if (%s != null)", read));
                create.add("    " + s.statement(call));
            }
        }
        for (String line : before) c.varLine("htmlBefore", "%s", line);
        for (String line : clean) c.varLine("htmlClean", "%s", line);
        for (String line : create) c.varLine("htmlCleanCreate", "%s", line);
        for (String line : members) {
            c.varLine("htmlClean", "%s", line);
            c.varLine("htmlCleanCreate", "%s", line);
        }
        // A command's parameter may set the field: the value read before the command, the cleaning after it.
        if (!html.isEmpty()) {
            c.var("htmlBeforeCommand", "%s", String.join("\n        ", before));
            c.var("htmlAfterCommand", "%s", String.join("\n        ", clean));
        }

        for (FieldSpec f : html) c.addMethod(pair(s, e.getName(), e, f));
        for (Map.Entry<String, EntitySpec> owner : owners.entrySet()) {
            EntitySpec member = owner.getValue();
            String type = member.pkg("entity") + "." + member.getName();
            for (FieldSpec f : htmlFields(member)) c.addMethod(pair(s, type, member, f));
        }
    }

    /** The entity's call that hands it the service's pair: cleanContent(html -> cleanContent(entity, html), ...). */
    static String call(Syntax s, String row, FieldSpec f) {
        String cap = f.capitalName();
        String clean = "clean" + cap;
        String plain = "plain" + cap;
        boolean copy = plainTarget(f) != null;
        if (s == Syntax.KOTLIN) {
            if (!copy) return String.format("%s.%s { %s(%s, it) }", row, clean, clean, row);
            return String.format("%s.%s({ %s(%s, it) }, { %s(%s, it) })", row, clean, clean, row, plain, row);
        }
        if (!copy) return String.format("%s.%s(html -> %s(%s, html))", row, clean, clean, row);
        return String.format("%s.%s(html -> %s(%s, html), html -> %s(%s, html))", row, clean, clean, row, plain, row);
    }

    /** The service's pair for one field: cleanContent and, where @plain names a field, plainContent. */
    static String pair(Syntax s, String type, EntitySpec owner, FieldSpec f) {
        String cap = f.capitalName();
        String safelist = safelist(f.dslValue("safe"));
        String plain = plainTarget(f);
        String where = owner.getName() + "." + f.name();
        StringBuilder m = new StringBuilder();
        m.append("    /**\n")
                .append(String.format(
                        "     * Keeps %s to an allow-list (@safe(%s)) before the row is written: on a create, and on"
                                + "%n",
                        where, safelist == null ? "basic" : safelist))
                .append("     * an update or a command that changed it - never with null. Override it to keep the\n")
                .append("     * field your way (the row has the request's values; a new row has no key yet). A\n")
                .append(
                        "     * BadRequestException thrown here is answered as one from beforeSave: nothing is written.\n")
                .append("     */\n");
        String clean = String.format(
                "%s.clean(html, org.jsoup.safety.Safelist.%s())", RUNTIME, safelist == null ? "basic" : safelist);
        appendMethod(m, s, "clean" + cap, type, clean);
        if (plain != null) {
            m.append("\n\n")
                    .append("    /**\n")
                    .append(String.format(
                            "     * The text of %s without tags, kept in %s (@plain): gets what clean%s returned.%n",
                            where, plain, cap))
                    .append(String.format("     * %s.plainLines(html) keeps a line per paragraph.%n", RUNTIME))
                    .append("     */\n");
            appendMethod(m, s, "plain" + cap, type, String.format("%s.plain(html)", RUNTIME));
        }
        return m.toString().replace("\r\n", "\n");
    }

    private static void appendMethod(StringBuilder m, Syntax s, String name, String type, String body) {
        if (s == Syntax.KOTLIN) {
            m.append(String.format("    protected open fun %s(row: %s, html: String?): String? =%n", name, type))
                    .append(String.format("        %s", body));
        } else {
            m.append(String.format("    protected String %s(%s row, String html) {%n", name, type))
                    .append(String.format("        return %s;%n", body))
                    .append("    }");
        }
    }

    /**
     * MongoDB keeps a member - an entity without a collection of its own, an order's line - inside the document that
     * holds it (MongoDocs), and the root's mapper writes it from the request. Its html fields are cleaned with the
     * root's, on every write: each member's own method, handed the root service's pair for it - a collection's and a
     * single one's, and those of the members it holds in turn. Empty where no member has an html field, so such a
     * service is written as before. Members that hold each other (a line's parts holding lines) are kept to any
     * depth, which these lines cannot reach: an html field among them stops the build.
     */
    static List<String> memberStatements(Syntax s, EntitySpec root, Map<String, EntitySpec> owners) {
        List<String> out = new ArrayList<String>();
        List<EntitySpec> path = new ArrayList<EntitySpec>();
        path.add(root);
        members(s, root, "entity", 1, path, out, owners);
        return out;
    }

    /** The lines that clean the members a holder embeds, one statement or brace a line. */
    private static void members(
            Syntax s,
            EntitySpec holder,
            String ref,
            int depth,
            List<EntitySpec> path,
            List<String> out,
            Map<String, EntitySpec> owners) {
        for (FieldSpec f : MyBatisSql.fields(holder)) {
            if (!MongoDocs.isEmbeddedMember(holder, f)) continue;
            EntitySpec target = holder.related(f.coreItemType());
            if (target == null) continue;
            int seen = indexOf(path, target.getName());
            if (seen >= 0) {
                refuseCycle(path.subList(seen, path.size()), path.get(0));
                continue;
            }
            String m = depth == 1 ? "m" : "m" + depth;
            List<String> inner = new ArrayList<String>();
            for (FieldSpec h : htmlFields(target)) {
                inner.add(s.statement(call(s, m, h)));
                owners.put(target.getName(), target);
            }
            path.add(target);
            members(s, target, m, depth + 1, path, inner, owners);
            path.remove(path.size() - 1);
            if (inner.isEmpty()) continue;
            String read = s.getter(ref, f.name());
            String type = target.pkg("entity") + "." + target.getName();
            if (f.isList()) {
                out.add(
                        s == Syntax.KOTLIN
                                ? String.format("for (%s in %s ?: emptyList<%s>()) {", m, read, type)
                                : String.format("if (%s != null) for (%s %s : %s) {", read, type, m, read));
            } else if (s == Syntax.KOTLIN) {
                out.add(String.format("%s?.let { %s ->", read, m));
            } else {
                out.add(String.format("if (%s != null) {", read));
                out.add(String.format("    %s %s = %s;", type, m, read));
            }
            for (String line : inner) out.add("    " + line);
            out.add("}");
        }
    }

    private static int indexOf(List<EntitySpec> path, String name) {
        for (int i = 0; i < path.size(); i++) {
            if (path.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /** Members holding each other: their html would be stored uncleaned below the depth the lines reach. */
    private static void refuseCycle(List<EntitySpec> cycle, EntitySpec root) {
        StringBuilder names = new StringBuilder();
        FieldSpec html = null;
        EntitySpec owner = null;
        for (EntitySpec member : cycle) {
            names.append(member.getName()).append(" -> ");
            if (html == null && !htmlFields(member).isEmpty()) {
                html = htmlFields(member).get(0);
                owner = member;
            }
        }
        if (html == null) return;
        names.append(cycle.get(0).getName());
        ModelCheck.fail(String.format(
                "[%s] %s.%s: MongoDB keeps %s inside the %s document to any depth (%s), and the service cleans an"
                        + " html field only to a fixed depth - break the cycle, or declare the field text",
                root.domainClassName(), owner.getName(), html.name(), owner.getName(), root.getName(), names));
    }

    /** The entity's method per html field: it sets the field, and its @plain copy, from the functions it is handed. */
    @Rule(value = "entity-html", target = "entity", order = 98, phase = Rule.Phase.TAIL)
    void entity(EntitySpec e) {
        List<FieldSpec> html = htmlFields(e);
        if (html.isEmpty()) return;
        SourceCode c = ctx(e);
        if (c.compatible()) return;
        Syntax s = c.syntax();
        for (FieldSpec f : html) {
            String plain = plainTarget(f);
            String clean = "clean" + f.capitalName();
            c.varLine("fieldList", "")
                    .varLine("fieldList", "/**")
                    .varLine(
                            "fieldList",
                            " * Sets %s to what clean makes of it%s; %s",
                            f.name(),
                            plain == null ? "" : String.format(" and %s to what plain makes of that", plain),
                            plain == null ? "it is not" : "neither is")
                    .varLine(
                            "fieldList",
                            " * called with null. The service calls it with its %s%s when %s changed.",
                            clean,
                            plain == null ? "" : " and plain" + f.capitalName(),
                            f.name())
                    .varLine("fieldList", " */");
            if (s == Syntax.KOTLIN) {
                c.varLine(
                        "fieldList",
                        plain == null
                                ? String.format("fun %s(clean: (String) -> String?) {", clean)
                                : String.format(
                                        "fun %s(clean: (String) -> String?, plain: (String) -> String?) {", clean));
            } else {
                c.varLine(
                        "fieldList",
                        plain == null
                                ? String.format(
                                        "public void %s(java.util.function.UnaryOperator<String> clean) {", clean)
                                : String.format(
                                        "public void %s(java.util.function.UnaryOperator<String> clean,"
                                                + " java.util.function.UnaryOperator<String> plain) {",
                                        clean));
            }
            for (String line : statements(s, f)) c.varLine("fieldList", "    %s", line);
            c.varLine("fieldList", "}");
        }
    }

    /** What the entity's method does, one statement a line. */
    static List<String> statements(Syntax s, FieldSpec f) {
        List<String> out = new ArrayList<String>();
        String name = s.escapeName(f.name());
        String read = s.getter("this", name);
        String plain = plainTarget(f);
        if (s == Syntax.KOTLIN) {
            out.add(String.format("val html = %s?.let(clean)", read));
            out.add(s.setter("this", name, "html"));
            if (plain != null) out.add(s.setter("this", s.escapeName(plain), "html?.let(plain)"));
        } else {
            out.add(String.format("String html = %s == null ? null : clean.apply(%s);", read, read));
            out.add(s.statement(s.setter("this", name, "html")));
            if (plain != null) {
                out.add(s.statement(s.setter("this", plain, "html == null ? null : plain.apply(html)")));
            }
        }
        return out;
    }
}
