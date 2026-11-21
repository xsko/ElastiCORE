package io.elasticore.base.model.pub.rules.lang.typescript;

import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EnumSpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Lines;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.util.CodeStringBuilder;
import io.elasticore.base.util.StringList;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The enumeration as the browser sees it (ts/enum.tmpl).
 *
 * Written as a frozen object and a union type rather than a TypeScript enum:
 * what crosses the wire is the value the server serialises, and a const object
 * keeps that value visible while the union type keeps the checking. A
 * TypeScript enum would introduce a second numbering that means nothing to
 * either side.
 *
 * The constant carries the field the DSL marked @json, since that is the one
 * the server writes into JSON.
 */
public class TypeScriptEnumRules implements CodeRules {

    private static final String NEXT = Lines.of(",", "    ");

    @Rule(value = "ts-enum", target = "enum", order = 10)
    void enumeration(EnumSpec e) {
        ctx(e).requireSyntax(Syntax.TYPESCRIPT, "ts-enum");
        FieldSpec wire = e.field(e.jsonFieldName());

        StringList constants = StringList.create(NEXT);
        for (EnumSpec.Constant c : e.constants()) {
            constants.add(String.format("%s: %s", c.name(), wireValue(e, c, wire)));
        }

        ctx(e).var("className", e.getName())
              .var("enumConstant", constants.toString())
              .var("extraLine", lookup(e, wire));
    }

    /**
     * What the server sends for this constant: the @json field's value where
     * the DSL named one, and the constant's own name where it did not - which
     * is what Jackson writes for a plain enum.
     */
    private String wireValue(EnumSpec e, EnumSpec.Constant c, FieldSpec wire) {
        if (wire != null) {
            int i = e.fields().indexOf(wire);
            if (i >= 0 && i < c.values().size()) {
                return RuleText.quote(unquoted(c.values().get(i)));
            }
        }
        return RuleText.quote(c.name());
    }

    private String unquoted(String v) {
        if (v != null && v.length() > 1 && v.charAt(0) == '"' && v.endsWith("\"")) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    /**
     * The reverse lookup the UI needs to render a value it was handed. The
     * JVM enums carry the same thing as a from&lt;Field&gt; method.
     */
    private String lookup(EnumSpec e, FieldSpec wire) {
        String name = e.getName();
        CodeStringBuilder cb = new CodeStringBuilder("{", "}");

        cb.line("/** The constant a wire value stands for, or undefined. */");
        cb.line("export function %sOf(value: string | null | undefined): %s | undefined",
                uncapital(name), name).block();
        cb.line("if (value == null) return undefined;");
        cb.line("return Object.values(%s).find((v) => v === value) as %s | undefined;",
                name, name);
        cb.end();

        return cb.toString() + "\n\n" + labels(e, wire);
    }

    /**
     * What a screen shows for each value: the field the DSL names @label - or
     * one called label, name, desc or description - and the constant's name
     * in words where there is none. Keyed by the wire value, which is what a
     * DTO holds.
     */
    private String labels(EnumSpec e, FieldSpec wire) {
        FieldSpec label = labelField(e, wire);
        int at = label == null ? -1 : e.fields().indexOf(label);
        StringBuilder out = new StringBuilder();
        out.append("/** What a screen shows for each value. */\n");
        out.append(String.format("export const %sLabel: Record<%s, string> = {%n", e.getName(), e.getName()));
        for (EnumSpec.Constant c : e.constants()) {
            String text = at >= 0 && at < c.values().size() ? unquoted(c.values().get(at)) : words(c.name());
            out.append(String.format("    [%s.%s]: %s,%n", e.getName(), c.name(), RuleText.quote(text == null ? c.name() : text.trim())));
        }
        out.append("};");
        translations(e, out);
        return out.toString();
    }

    /**
     * The label in other languages, from the enumeration's label_en, label_ja
     * columns: by the same keys as XxxLabel, each language a key ("zh-TW"
     * quoted). Nothing when the enumeration has no such column.
     */
    private void translations(EnumSpec e, StringBuilder out) {
        java.util.Map<String, java.util.Map<String, String>> all = e.labelTranslations();
        if (all.isEmpty()) return;
        out.append(String.format(
                "%n%n/** What a screen shows for each value in other languages: language -> label. */%n"));
        out.append(String.format(
                "export const %sLabels: Record<%s, Record<string, string>> = {%n", e.getName(), e.getName()));
        for (EnumSpec.Constant c : e.constants()) {
            java.util.Map<String, String> texts = all.get(c.name());
            out.append(String.format(
                    "    [%s.%s]: %s,%n",
                    e.getName(),
                    c.name(),
                    TypeScriptDtoFileRules.languages(
                            texts == null ? java.util.Collections.<String, String>emptyMap() : texts)));
        }
        out.append("};");
    }

    private FieldSpec labelField(EnumSpec e, FieldSpec wire) {
        java.util.List<io.elasticore.base.model.entity.Field> fields =
                new java.util.ArrayList<io.elasticore.base.model.entity.Field>();
        for (FieldSpec f : e.fields()) fields.add(f.raw());
        io.elasticore.base.model.entity.Field label = io.elasticore.base.model.pub.rules.EnumLabelColumns.labelColumn(
                fields, wire == null ? null : wire.name());
        int at = fields.indexOf(label);
        return at < 0 ? null : e.fields().get(at);
    }

    /** MASTER_CARD -> Master card. */
    private static String words(String constant) {
        String lower = constant.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String uncapital(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
