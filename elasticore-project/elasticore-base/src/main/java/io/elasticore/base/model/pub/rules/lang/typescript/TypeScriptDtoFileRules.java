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
        for (FieldSpec f : e.fields()) {
            String shape = keyShape(f, c.syntax());
            if (shape != null) c.field(f).type(shape);
        }
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
     * A relation a search compares by, as the server reads it: the Java
     * search DTO holds the entity, and a search sends its key alone -
     * {"insurer": {"id": "C1"}}. The browser has no entity interfaces, so the
     * property is that key's shape rather than a type no file declares. Null
     * for a field that names no entity with a key (an enum, a DTO, a value).
     */
    private static String keyShape(FieldSpec f, Syntax syntax) {
        if (f.isBaseType() || f.isList()) return null;
        io.elasticore.base.model.DataModelComponent named;
        try {
            io.elasticore.base.ECoreModelContext ctx =
                    io.elasticore.base.model.core.BaseECoreModelContext.getContext();
            named = ctx == null ? null : ctx.findModelComponent(f.typeName());
        } catch (RuntimeException notInTheModel) {
            return null;
        }
        if (!(named instanceof io.elasticore.base.model.entity.Entity)) return null;
        io.elasticore.base.model.core.ListMap<String, io.elasticore.base.model.entity.Field> fields =
                ((io.elasticore.base.model.entity.Entity) named).getAllFieldListMap();
        if (fields == null) return null;
        List<String> keys = new ArrayList<String>();
        for (io.elasticore.base.model.entity.Field k : fields.getList()) {
            if (k.hasAnnotation("id") || k.hasAnnotation("pk")) {
                keys.add(String.format("%s: %s", syntax.escapeName(k.getName()),
                        syntax.typeName(k.getTypeInfo().getDefaultTypeName())));
            }
        }
        if (keys.isEmpty()) return null;
        StringBuilder shape = new StringBuilder("{ ");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) shape.append(", ");
            shape.append(keys.get(i));
        }
        return shape.append(" }").toString();
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

        // The type's own label, for a page title or a column of types: only where the DSL gives one.
        String typeLabel = e.metaValue("label");
        java.util.Map<String, String> typeLabels = e.labelTranslations();
        if (typeLabel == null || typeLabel.trim().isEmpty()) {
            io.elasticore.base.model.core.Annotation entityLabel = entityLabel(e);
            typeLabel = entityLabel == null ? null : entityLabel.getValue();
            typeLabels = io.elasticore.base.model.core.LabelTranslations.of(entityLabel);
        }
        if (typeLabel != null && !typeLabel.trim().isEmpty()) {
            c.varLine("metaList", "")
                    .varLine(
                            "metaList",
                            "/** The type's label, as the DSL declares it, and in other languages: language -> label. */")
                    .varLine(
                            "metaList",
                            "export const %sMeta: { label: string; labels: Record<string, string> } = { label: %s, labels: %s };",
                            base,
                            string(typeLabel.trim()),
                            languages(typeLabels));
        }

        c.varLine("metaList", "")
         .varLine("metaList", "/** Each field's label and limits, as the DSL declares them. */")
         .varLine("metaList", "export const %sFields = {", base);
        for (FieldSpec f : shown) {
            StringBuilder m = new StringBuilder();
            m.append("label: ").append(RuleText.quote(labelOf(f)));
            // @label(en="Unit cost"): the label in the other languages the DSL gives it.
            java.util.Map<String, String> translations = f.labelTranslations();
            if (!translations.isEmpty()) m.append(", labels: ").append(languages(translations));
            // Markup the server cleaned before saving: a screen may render it as HTML.
            if (f.hasDsl(io.elasticore.base.model.pub.rules.base.HtmlRules.HTML)) m.append(", html: true");
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
        // What validateXxxCodes says for the same checks: the code and values per field, in order.
        List<String> codeLines = new ArrayList<String>();
        for (FieldSpec f : checked) {
            if (!writable(f)) continue;
            String name = c.syntax().escapeName(f.name());
            String v = "value." + name;
            String label = labelOf(f);
            Checks checks = new Checks(name, f.name());
            if (f.isList()) {
                // @minsize(1, '...'): what the server's @NotNull and @Size refuse with 400, said first.
                // Missing counts as too short, so it also says what "!" on the list would.
                int least = minSize(f);
                String message = DtoBaseRules.Bound.split(f.dslValueAny("minsize"))[1];
                c.varLine(
                        "metaList",
                        "    %s",
                        String.format(
                                "if (!partial && (%s == null || %s.length < %d)) errors.%s = %s;",
                                v,
                                v,
                                least,
                                name,
                                RuleText.quote(message != null ? message : label + " needs at least " + least)));
                // The server's two constraints, each by its own code.
                checks.add(String.format("!partial && %s == null", v), null, "NotNull", "{}");
                checks.add(
                        String.format("!partial && %s != null && %s.length < %d", v, v, least),
                        null,
                        "Size",
                        String.format("{ min: %d }", least));
                codeLines.addAll(checks.codes());
                continue;
            }
            if (f.required()) {
                // Only a string can be empty; comparing anything else with '' does not compile.
                checks.add(
                        f.isStringType()
                                ? String.format("partial ? %s === '' : (%s == null || %s === '')", v, v, v)
                                : String.format("!partial && %s == null", v),
                        RuleText.quote(label + " is required"),
                        "NotNull",
                        "{}");
            }
            if (f.isStringType()) {
                // One @Size carries both lengths, so a value too long and one too short name both.
                String size = sizeValues(f);
                if (f.validationMaxLength() >= 0) {
                    checks.add(
                            String.format("%s != null && String(%s).length > %d", v, v, f.validationMaxLength()),
                            RuleText.quote(label + " is at most " + f.validationMaxLength() + " characters"),
                            "Size",
                            size);
                }
                if (f.minLength() > 0) {
                    checks.add(
                            String.format("%s != null && %s !== '' && String(%s).length < %d", v, v, v, f.minLength()),
                            RuleText.quote(label + " is at least " + f.minLength() + " characters"),
                            "Size",
                            size);
                }
                if (f.hasDsl("email")) {
                    checks.add(
                            String.format("%s && !/^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$/.test(String(%s))", v, v),
                            "'Email format invalid'",
                            "Email",
                            "{}");
                }
                // Read as the DTO's @Pattern reads it, so the two check the same thing.
                String regexp = f.hasDsl("pattern") ? f.patternRegexp() : null;
                if (regexp != null) {
                    String msg = f.patternMessage();
                    checks.add(
                            String.format(
                                    "%s && !new RegExp(%s).test(String(%s))",
                                    v, RuleText.quote("^(?:" + regexp + ")$"), v),
                            RuleText.quote(msg == null ? DtoBaseRules.notInTheExpectedForm(f) : msg),
                            "Pattern",
                            String.format("{ regexp: %s }", RuleText.quote(regexp)));
                }
            }
            String minDecl = f.dslValueAny("min", "minval", "minvalue");
            String min = bound(minDecl);
            if (min != null) {
                checks.add(
                        String.format("%s != null && Number(%s) < %s", v, v, min),
                        RuleText.quote(message(minDecl, label + " must be at least " + min)),
                        DtoBaseRules.decimalBound(f, min, false) ? "DecimalMin" : "Min",
                        String.format("{ value: %s }", min));
            }
            String maxDecl = f.dslValueAny("max", "maxval", "maxvalue");
            String max = bound(maxDecl);
            if (max != null) {
                checks.add(
                        String.format("%s != null && Number(%s) > %s", v, v, max),
                        RuleText.quote(message(maxDecl, label + " must be at most " + max)),
                        DtoBaseRules.decimalBound(f, max, false) ? "DecimalMax" : "Max",
                        String.format("{ value: %s }", max));
            }
            for (String line : checks.messages()) c.varLine("metaList", "    %s", line);
            codeLines.addAll(checks.codes());
        }
        c.varLine("metaList", "    return errors;")
         .varLine("metaList", "}");

        // The same checks as the server's fieldErrors say them: a screen in another
        // language says each in its own words, from the code and the constraint's values.
        String fieldError = "Array<{ field: string; code: string; values: Record<string, number | string> }>";
        c.varLine("metaList", "")
                .varLine("metaList", "/**")
                .varLine(
                        "metaList",
                        " * The same checks as validate%s, as the server's fieldErrors codes: the field,",
                        base)
                .varLine(
                        "metaList",
                        " * the constraint (NotNull, Size, Min, Max, Pattern ...) and its values (Size: min,")
                .varLine("metaList", " * max; Min, Max: value; Pattern: regexp). Empty when nothing is wrong.")
                .varLine("metaList", " */")
                .varLine(
                        "metaList",
                        "export function validate%sCodes(value: Partial<%s> | null | undefined, partial = false): %s {",
                        base,
                        className,
                        fieldError)
                .varLine("metaList", "    const errors: %s = [];", fieldError)
                .varLine("metaList", "    if (value == null) return errors;");
        for (String line : codeLines) c.varLine("metaList", "    %s", line);
        c.varLine("metaList", "    return errors;").varLine("metaList", "}");
    }

    /**
     * The checks of one field: each a condition, the message validateXxx sets
     * and the code validateXxxCodes adds. A chain of else-ifs reports the
     * first thing wrong with a field, in both.
     */
    private static final class Checks {
        private final String name;
        private final String field;
        private final List<String> messages = new ArrayList<String>();
        private final List<String> codes = new ArrayList<String>();

        Checks(String name, String field) {
            this.name = name;
            this.field = field;
        }

        /** message: the TypeScript expression validateXxx sets, or null when it says the field another way. */
        void add(String condition, String message, String code, String values) {
            String start = codes.isEmpty() ? "if" : "else if";
            if (message != null) {
                messages.add(String.format(
                        "%s (%s) errors.%s = %s;", messages.isEmpty() ? "if" : "else if", condition, name, message));
            }
            codes.add(String.format(
                    "%s (%s) errors.push({ field: %s, code: %s, values: %s });",
                    start, condition, RuleText.quote(field), RuleText.quote(code), values));
        }

        List<String> messages() {
            return messages;
        }

        List<String> codes() {
            return codes;
        }
    }

    /** The values of the server's @Size on a text: the least and the most length it declares. */
    private static String sizeValues(FieldSpec f) {
        List<String> values = new ArrayList<String>();
        if (f.minLength() > 0) values.add("min: " + f.minLength());
        if (f.validationMaxLength() >= 0) values.add("max: " + f.validationMaxLength());
        StringBuilder sb = new StringBuilder("{ ");
        for (int i = 0; i < values.size(); i++) sb.append(i == 0 ? "" : ", ").append(values.get(i));
        return sb.append(" }").toString();
    }

    /** A language -> text map as a TypeScript object: { en: "Unit cost", "zh-TW": "..." }; {} when empty. */
    static String languages(java.util.Map<String, String> translations) {
        if (translations.isEmpty()) return "{}";
        StringBuilder m = new StringBuilder("{ ");
        boolean first = true;
        for (java.util.Map.Entry<String, String> t : translations.entrySet()) {
            if (!first) m.append(", ");
            first = false;
            m.append(key(t.getKey())).append(": ").append(string(t.getValue()));
        }
        return m.append(" }").toString();
    }

    /** A language as an object key: zh-TW is not a name, so it is quoted, as a TypeScript key must be. */
    static String key(String language) {
        return language.indexOf('-') < 0 ? language : RuleText.quote(language);
    }

    /** A text as a TypeScript string, its backslashes and double quotes escaped. */
    static String string(String text) {
        return RuleText.quote(text.replace("\\", "\\\\").replace("\"", "\\\""));
    }

    /**
     * The meta label of the entity a DTO stands for, or null: a DTO without a
     * label of its own is shown by its entity's.
     */
    private static io.elasticore.base.model.core.Annotation entityLabel(EntitySpec e) {
        io.elasticore.base.model.DataModelComponent named;
        try {
            io.elasticore.base.ECoreModelContext ctx = io.elasticore.base.model.core.BaseECoreModelContext.getContext();
            named = ctx == null ? null : ctx.findModelComponent(e.getName());
        } catch (RuntimeException notInTheModel) {
            return null;
        }
        if (!(named instanceof io.elasticore.base.model.entity.Entity)) return null;
        io.elasticore.base.model.MetaInfo meta = ((io.elasticore.base.model.entity.Entity) named).getMetaInfo();
        java.util.Map<String, io.elasticore.base.model.core.Annotation> map =
                meta == null ? null : meta.getMetaAnnotationMap();
        return map == null ? null : map.get("label");
    }

    /** What a request may write: not shown-only, not derived, not the persistence layer's. */
    private static boolean writable(FieldSpec f) {
        if (f.hasDslAny("readonly", "noinput", "behavior", "statefield", "snapshot", "compute", "formula")) return false;
        if (f.dslValue("ref") != null || f.systemManaged()) return false;
        // A stamp is the lifecycle's to write - where there is one to write it (a split root).
        if (f.hasDsl(io.elasticore.base.model.pub.rules.ddd.DddStateRules.STAMP)
                && io.elasticore.base.model.pub.rules.ddd.DddBehaviorRules.splits(f.entity())) return false;
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
