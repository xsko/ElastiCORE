package io.elasticore.base.model.pub.rules.lang.typescript;

import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EnumSpec;
import io.elasticore.base.model.pub.rules.FieldFacts;
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

        // What the server sends for each constant (FieldFacts.enumWireValue).
        StringList constants = StringList.create(NEXT);
        for (EnumSpec.Constant c : e.constants()) {
            constants.add(String.format("%s: %s", c.name(), RuleText.quote(FieldFacts.enumWireValue(e, c))));
        }

        ctx(e).var("className", e.getName())
                .var("enumConstant", constants.toString())
                .var("extraLine", lookup(e));
    }

    /**
     * The reverse lookup the UI needs to render a value it was handed. The
     * JVM enums carry the same thing as a from&lt;Field&gt; method.
     */
    private String lookup(EnumSpec e) {
        String name = e.getName();
        CodeStringBuilder cb = new CodeStringBuilder("{", "}");

        cb.line("/** The constant a wire value stands for, or undefined. */");
        cb.line("export function %sOf(value: string | null | undefined): %s | undefined",
                uncapital(name), name).block();
        cb.line("if (value == null) return undefined;");
        cb.line("return Object.values(%s).find((v) => v === value) as %s | undefined;",
                name, name);
        cb.end();

        return cb.toString() + "\n\n" + labels(e);
    }

    /**
     * What a screen shows for each value (FieldFacts.enumLabel), keyed by the
     * wire value, which is what a DTO holds.
     */
    private String labels(EnumSpec e) {
        StringBuilder out = new StringBuilder();
        out.append("/** What a screen shows for each value. */\n");
        out.append(String.format("export const %sLabel: Record<%s, string> = {%n", e.getName(), e.getName()));
        for (EnumSpec.Constant c : e.constants()) {
            out.append(String.format(
                    "    [%s.%s]: %s,%n", e.getName(), c.name(), RuleText.quote(FieldFacts.enumLabel(e, c))));
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
        if (e.labelTranslations().isEmpty()) return;
        out.append(String.format(
                "%n%n/** What a screen shows for each value in other languages: language -> label. */%n"));
        out.append(String.format(
                "export const %sLabels: Record<%s, Record<string, string>> = {%n", e.getName(), e.getName()));
        for (EnumSpec.Constant c : e.constants()) {
            out.append(String.format(
                    "    [%s.%s]: %s,%n",
                    e.getName(), c.name(), TypeScriptDtoFileRules.languages(FieldFacts.enumLabels(e, c))));
        }
        out.append("};");
    }

    private String uncapital(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
