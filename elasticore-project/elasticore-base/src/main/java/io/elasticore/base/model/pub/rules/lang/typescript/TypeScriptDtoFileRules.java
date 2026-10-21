package io.elasticore.base.model.pub.rules.lang.typescript;

import io.elasticore.base.model.pub.rules.base.DtoBaseRules;
import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The DTO as the browser sees it (ts/dto.tmpl).
 *
 * A TypeScript interface, not a class: nothing is constructed on this side,
 * the values arrive as JSON and the type is what says what shape they have.
 *
 * What is shared with the JVM bundles is the field walk - which fields a DTO
 * carries, what a search DTO renames and pairs. What is not is everything the
 * shared rules annotate those fields with, which the renderer drops because
 * this language has no annotations.
 */
public class TypeScriptDtoFileRules implements CodeRules {

    @Rule(value = "ts-dto-file", target = "dto", order = 90, phase = Rule.Phase.TAIL)
    void dto(EntitySpec e) {
        SourceCode c = ctx(e);
        c.requireSyntax(Syntax.TYPESCRIPT, "ts-dto-file");
        if (e.hasMeta("searchable")) {
            c.dontMake();
            return;
        }

        String className = e.dtoClassName();
        String keyName = e.keyDtoClassName();
        if (keyName != null) {
            className = keyName;
            boolean anyKey = false;
            for (FieldSpec f : e.fields()) {
                if (f.hasDsl("id") || f.hasDsl("pk")) {
                    anyKey = true;
                } else {
                    c.removeField(f);
                }
            }
            if (!anyKey) {
                c.dontMake();
                return;
            }
        }

        write(c, e, className, keyName == null);
    }

    /**
     * A value object a DTO carries inline - an address, a card - as the
     * interface its JSON has. Java hands the embeddable class itself to the
     * DTO; a browser has no such class, so it gets the shape instead.
     */
    @Rule(value = "ts-value-object-file", target = "valueObject", order = 90, phase = Rule.Phase.TAIL)
    void valueObject(EntitySpec e) {
        SourceCode c = ctx(e);
        c.requireSyntax(Syntax.TYPESCRIPT, "ts-value-object-file");
        if (!e.hasMeta("embeddable")) {
            c.dontMake();
            return;
        }
        c.template("dto");
        write(c, e, e.getName(), true);
    }

    @Rule(value = "ts-srchdto-file", target = "srchdto", order = 90, phase = Rule.Phase.TAIL)
    void searchDto(EntitySpec e) {
        SourceCode c = ctx(e);
        c.requireSyntax(Syntax.TYPESCRIPT, "ts-srchdto-file");
        c.template("dto");
        // Every condition may be left out - that is what searching by fewer
        // of them is. The DSL's "!" says what an entity must hold, not what a
        // search must ask; the Java search DTO declares none of them required.
        for (FieldSpec f : e.fields()) c.field(f).optional();
        write(c, e, e.searchDtoClassName(), false);

        // Sorting and paging, as the Java search DTO carries them. Without
        // these a client could not ask for the second page at all.
        java.util.List<String> paging = new java.util.ArrayList<String>();
        paging.add("");
        paging.add("sortCode?: string;");
        if (e.searchPageSize() != null) {
            paging.add("");
            paging.add("/** Zero-based. */");
            paging.add("pageNumber?: number;");
            paging.add("");
            paging.add(String.format("/** %d when left out. */", e.searchPageSize()));
            paging.add("pageSize?: number;");
        }
        paging.add("");
        paging.add("sortColumn?: string;");
        paging.add("");
        paging.add("sortAscending?: boolean;");
        for (String line : paging) c.varLine("fieldList", line);
    }

    /**
     * The interface and the modules its own field types come from.
     *
     * Imports are collected here rather than through addImport, because what
     * a TypeScript file imports is a module path and the seam's importLine
     * takes a class name - so only the names this file actually mentions are
     * asked for, and each becomes its own module.
     */
    private void write(SourceCode c, EntitySpec e, String className, boolean withMeta) {
        Syntax syntax = c.syntax();

        // One interface is the request and the reply alike, and a write-only
        // value - a password - is in no reply. Declared required, a page
        // reading the reply was promised a field that is never there.
        for (FieldSpec f : e.fields()) {
            if (f.hasDslAny("writeonly", "nooutput", "password")) c.field(f).optional();
        }

        // Render first: what the file imports is what it ended up mentioning,
        // and a rule may have rewritten a field's type on the way - a relation
        // is declared as the counterpart's DTO, not as the entity the model
        // named. Reading the spec instead would import the type that was
        // replaced.
        String fields = RuleText.stripTrailing(c.renderFieldBlock(1));

        StringBuilder imports = new StringBuilder();
        for (String name : declaredTypesIn(fields)) {
            if (name.equals(className)) continue;
            imports.append(syntax.importLine(name)).append('\n');
        }

        c.var("className", className)
         .var("importList", imports.length() == 0 ? "" : imports.toString())
         .varLines("fieldList", RuleText.linesOf(fields));
        c.slot("metaList");
        if (withMeta) meta(c, e, className, fields);
    }

    /**
     * What the DSL says about each field, for the screen: its label and limits
     * as a constant, and the checks it can run before sending.
     *
     * The server checks the same things again; these are so a form can say
     * what is wrong beside the field, in the DSL's own words, before the
     * round trip. Only what a request may write is checked.
     */
    private void meta(SourceCode c, EntitySpec e, String className, String rendered) {
        // shown: the fields the constant describes - not a list. checked: the
        // fields validate() looks at - those, and a list with a @minsize the
        // server refuses (400) when it is missing or shorter.
        List<FieldSpec> shown = new ArrayList<FieldSpec>();
        List<FieldSpec> checked = new ArrayList<FieldSpec>();
        for (FieldSpec f : e.fields()) {
            if (f.isList() && minSize(f) < 1) continue;
            String name = c.syntax().escapeName(f.name());
            boolean declared = false;
            for (String line : RuleText.linesOf(rendered)) {
                String t = line.trim();
                if (t.startsWith(name + ":") || t.startsWith(name + "?:")) declared = true;
            }
            if (!declared) continue;
            if (!f.isList()) shown.add(f);
            checked.add(f);
        }
        String base = className.endsWith("DTO") ? className.substring(0, className.length() - 3) : className;

        c.varLine("metaList", "")
         .varLine("metaList", "/** Each field's label and limits, as the DSL declares them. */")
         .varLine("metaList", "export const %sFields = {", base);
        for (FieldSpec f : shown) {
            StringBuilder m = new StringBuilder();
            m.append("label: ").append(RuleText.quote(labelOf(f)));
            if (!writable(f)) {
                m.append(", readOnly: true");
            } else {
                if (f.required()) m.append(", required: true");
                if (f.validationMaxLength() >= 0 && f.isStringType()) m.append(", maxLength: ").append(f.validationMaxLength());
                if (f.minLength() > 0 && f.isStringType()) m.append(", minLength: ").append(f.minLength());
                String min = bound(f.dslValueAny("min", "minval", "minvalue"));
                if (min != null) m.append(", min: ").append(min);
                String max = bound(f.dslValueAny("max", "maxval", "maxvalue"));
                if (max != null) m.append(", max: ").append(max);
            }
            c.varLine("metaList", "    %s: { %s },", c.syntax().escapeName(f.name()), m);
        }
        c.varLine("metaList", "} as const;")
         .varLine("metaList", "")
         .varLine("metaList", "/**")
         .varLine("metaList", " * The DSL's checks, before sending: field -> message, empty when nothing is")
         .varLine("metaList", " * wrong. The server checks them again; this is so a form can say it first.")
         .varLine("metaList", " * partial: an update that sends only what changed - what is required is not asked for.")
         .varLine("metaList", " */")
         .varLine("metaList", "export function validate%s(value: Partial<%s> | null | undefined, partial = false): Record<string, string> {", base, className)
         .varLine("metaList", "    const errors: Record<string, string> = {};")
         .varLine("metaList", "    if (value == null) return errors;");
        for (FieldSpec f : checked) {
            if (!writable(f)) continue;
            String name = c.syntax().escapeName(f.name());
            String v = "value." + name;
            String label = labelOf(f);
            List<String> checks = new ArrayList<String>();
            if (f.isList()) {
                // @minsize(1, '...'): what the server's @NotNull and @Size refuse with 400, said first.
                // Missing counts as too short, so it also says what "!" on the list would.
                int least = minSize(f);
                String message = DtoBaseRules.Bound.split(f.dslValueAny("minsize"))[1];
                checks.add(String.format("if (!partial && (%s == null || %s.length < %d)) errors.%s = %s;",
                        v, v, least, name, RuleText.quote(message != null ? message : label + " needs at least " + least)));
                c.varLine("metaList", "    %s", checks.get(0));
                continue;
            }
            if (f.required()) {
                // Only a string can be empty; comparing anything else with '' does not compile.
                checks.add(f.isStringType()
                        ? String.format("if (partial ? %s === '' : (%s == null || %s === '')) errors.%s = %s;", v, v, v, name, RuleText.quote(label + " is required"))
                        : String.format("if (!partial && %s == null) errors.%s = %s;", v, name, RuleText.quote(label + " is required")));
            }
            if (f.isStringType()) {
                if (f.validationMaxLength() >= 0) {
                    checks.add(String.format("else if (%s != null && String(%s).length > %d) errors.%s = %s;",
                            v, v, f.validationMaxLength(), name,
                            RuleText.quote(label + " is at most " + f.validationMaxLength() + " characters")));
                }
                if (f.minLength() > 0) {
                    checks.add(String.format("else if (%s != null && %s !== '' && String(%s).length < %d) errors.%s = %s;",
                            v, v, v, f.minLength(), name,
                            RuleText.quote(label + " is at least " + f.minLength() + " characters")));
                }
                if (f.hasDsl("email")) {
                    checks.add(String.format("else if (%s && !/^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$/.test(String(%s))) errors.%s = 'Email format invalid';",
                            v, v, name));
                }
                // Read as the DTO's @Pattern reads it, so the two check the same thing.
                String regexp = f.hasDsl("pattern") ? f.patternRegexp() : null;
                if (regexp != null) {
                    String msg = f.patternMessage();
                    checks.add(String.format("else if (%s && !new RegExp(%s).test(String(%s))) errors.%s = %s;",
                            v, RuleText.quote("^(?:" + regexp + ")$"), v, name,
                            RuleText.quote(msg == null ? DtoBaseRules.notInTheExpectedForm(f) : msg)));
                }
            }
            String minDecl = f.dslValueAny("min", "minval", "minvalue");
            String min = bound(minDecl);
            if (min != null) {
                checks.add(String.format("else if (%s != null && Number(%s) < %s) errors.%s = %s;", v, v, min, name,
                        RuleText.quote(message(minDecl, label + " must be at least " + min))));
            }
            String maxDecl = f.dslValueAny("max", "maxval", "maxvalue");
            String max = bound(maxDecl);
            if (max != null) {
                checks.add(String.format("else if (%s != null && Number(%s) > %s) errors.%s = %s;", v, v, max, name,
                        RuleText.quote(message(maxDecl, label + " must be at most " + max))));
            }
            // A chain of else-ifs reports the first thing wrong with a field.
            for (int i = 0; i < checks.size(); i++) {
                String line = checks.get(i);
                if (i == 0 && line.startsWith("else ")) line = line.substring(5);
                c.varLine("metaList", "    %s", line);
            }
        }
        c.varLine("metaList", "    return errors;")
         .varLine("metaList", "}");
    }

    /** What a request may write: not shown-only, not derived, not the persistence layer's. */
    private static boolean writable(FieldSpec f) {
        if (f.hasDslAny("readonly", "noinput", "behavior", "statefield", "snapshot", "compute", "formula")) return false;
        if (f.dslValue("ref") != null || f.systemManaged()) return false;
        return true;
    }

    /** The least size @minsize(n) or @minsize(n, 'message') declares; 0 when none, or not a number. */
    private static int minSize(FieldSpec f) {
        String declared = f.dslValueAny("minsize");
        if (declared == null) return 0;
        try {
            return Integer.parseInt(DtoBaseRules.Bound.split(declared)[0].trim());
        } catch (NumberFormatException notANumber) {
            // a bound the server does not read as a number either
            return 0;
        }
    }

    private static String labelOf(FieldSpec f) {
        String label = f.label();
        return label == null || label.trim().isEmpty() ? f.name() : label.trim();
    }

    /** The number of @min(1) or @min(0.01, 'message'). */
    private static String bound(String declared) {
        if (declared == null) return null;
        String v = declared.split(",")[0].trim();
        return v.matches("-?\\d+(\\.\\d+)?") ? v : null;
    }

    /** The message of @min(0.01, 'message'), or the fallback. */
    private static String message(String declared, String fallback) {
        int comma = declared == null ? -1 : declared.indexOf(',');
        if (comma < 0) return fallback;
        String m = declared.substring(comma + 1).trim();
        if (m.length() > 1 && (m.charAt(0) == '\'' || m.charAt(0) == '"')) m = m.substring(1, m.length() - 1);
        return m.isEmpty() ? fallback : m;
    }

    /**
     * The generated types the rendered properties name.
     *
     * A property reads "name?: Type;" or "name: Type[];" - so the type is what
     * follows the colon, minus any array brackets. A TypeScript primitive
     * needs no module; everything else the model generated is one file each.
     */
    private Set<String> declaredTypesIn(String fields) {
        Set<String> names = new LinkedHashSet<String>();
        for (String line : RuleText.linesOf(fields)) {
            int colon = line.indexOf(':');
            int end = line.lastIndexOf(';');
            if (colon < 0 || end <= colon) continue;

            String type = line.substring(colon + 1, end).trim();
            while (type.endsWith("[]")) type = type.substring(0, type.length() - 2);
            if (type.isEmpty() || PRIMITIVES.contains(type)) continue;
            if (!Character.isUpperCase(type.charAt(0))) continue;
            names.add(type);
        }
        return names;
    }

    private static final Set<String> PRIMITIVES = new java.util.HashSet<String>(
            java.util.Arrays.asList("string", "number", "boolean", "unknown", "void", "any"));
}
