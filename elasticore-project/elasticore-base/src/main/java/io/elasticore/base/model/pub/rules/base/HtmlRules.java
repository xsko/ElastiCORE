package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.ModelCheck;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import java.util.ArrayList;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * An {@code html} field: markup from a rich-text editor, cleaned before it is saved (issue 183).
 *
 * <pre>
 * Post:
 *   fields:
 *     content:     html! @safe(basic) @plain(contentText)
 *     contentText: text @search(like)
 * </pre>
 *
 * <p>The entity gets {@code cleanHtml()}: each html field kept to a jsoup safelist - {@code @safe(basic)}, the
 * default, or {@code relaxed} - and, where {@code @plain(field)} says so, its text without tags written to that field,
 * which no request writes (SpecFactory.prepareHtml). The method is the entity's, as derive() is: under the DDD rule
 * sets its setters are its own. The CoreService calls it on every write of the row - a save, an update, an update by
 * condition, and after a lifecycle command, whose parameter may set the field - before the row is written and
 * before the developer's beforeSave.
 *
 * <p>An html field on a member or a value object is refused (prepareHtml): no service of theirs writes them. The
 * runtime's {@code Html} is only compiled against jsoup, and the generated line names the safelist, so a project with
 * an html field and without jsoup stops where that line compiles. v3 and Kotlin, JPA and MyBatis. The
 * 2.x-compatible output stops the build here, and the 2.x generator on the loader's DSL warning.
 */
public class HtmlRules implements CodeRules {

    /** The annotation the loader gives a field of type html. */
    public static final String HTML = "html";

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

    /**
     * The service cleans the row before each write. Before the commands are written (30), which clean after their
     * move too, and before the values the model derives (40).
     */
    @Rule(value = "service-html", target = "service", order = 26)
    void service(EntitySpec e) {
        List<FieldSpec> html = htmlFields(e);
        if (html.isEmpty()) return;
        SourceCode c = ctx(e);
        if (c.compatible()) {
            ModelCheck.fail(String.format(
                    "[%s] %s.%s: an html field is cleaned before it is saved by the 3.0 code; the 2.x-compatible"
                            + " output would store what a request sends - generate the 3.0 code (output v3), or"
                            + " declare it text",
                    e.domainClassName(), e.getName(), html.get(0).name()));
            return;
        }
        String call = c.syntax().statement("entity.cleanHtml()");
        c.varLine("deriveBeforeSave", "%s", call);
        c.var("htmlAfterCommand", "%s", call);
    }

    /** The entity's cleanHtml(). */
    @Rule(value = "entity-html", target = "entity", order = 98, phase = Rule.Phase.TAIL)
    void entity(EntitySpec e) {
        List<FieldSpec> html = htmlFields(e);
        if (html.isEmpty()) return;
        SourceCode c = ctx(e);
        if (c.compatible()) return;
        Syntax s = c.syntax();
        c.varLine("fieldList", "")
                .varLine("fieldList", "/**")
                .varLine(
                        "fieldList",
                        " * Keeps each html field to its safelist (@safe) and writes its text without tags")
                .varLine("fieldList", " * where @plain says. Called by the service before each write; call it after")
                .varLine("fieldList", " * setting one yourself.")
                .varLine("fieldList", " */");
        c.varLine("fieldList", s == Syntax.KOTLIN ? "fun cleanHtml() {" : "public void cleanHtml() {");
        for (String line : statements(s, html)) c.varLine("fieldList", "    %s", line);
        c.varLine("fieldList", "}");
    }

    /** What cleanHtml() does, one statement a line. */
    static List<String> statements(Syntax s, List<FieldSpec> html) {
        List<String> out = new ArrayList<String>();
        for (FieldSpec f : html) {
            String name = s.escapeName(f.name());
            String safelist = safelist(f.dslValue("safe"));
            String cleaned = String.format(
                    "%s.clean(%s, org.jsoup.safety.Safelist.%s())",
                    RUNTIME, s.getter("this", name), safelist == null ? "basic" : safelist);
            out.add(s.statement(s.setter("this", name, cleaned)));
            String plain = f.dslValue("plain");
            if (plain != null && !plain.trim().isEmpty()) {
                out.add(s.statement(s.setter(
                        "this",
                        s.escapeName(plain.trim()),
                        String.format("%s.plain(%s)", RUNTIME, s.getter("this", name)))));
            }
        }
        return out;
    }
}
