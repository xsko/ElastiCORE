package io.elasticore.base.model.pub.rules.lang.kotlin;

import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.SourceCode;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Full-file DTO rendering in Kotlin (kt/dto.tmpl).
 *
 * The field rules are the Java ones: which fields survive, what @JsonFormat a
 * date carries, what bounds a decimal declares - none of that depends on the
 * language the file is written in. What differs is the class around them, and
 * one thing that is not merely syntax: Lombok is an annotation processor for
 * Java sources and does nothing here, so the accessors it would have written
 * are Kotlin properties instead and its annotations are not emitted.
 *
 * Compose with the field rules: engineFor("jpa-dto", "kt-dto-file").
 */
public class KotlinDtoFileRules implements CodeRules {

    @Rule(value = "kt-dto-file", target = "dto", order = 90, phase = Rule.Phase.TAIL)
    void file(EntitySpec e) {
        if (e.hasMeta("searchable")) {
            ctx(e).dontMake();
            return;
        }

        SourceCode c = ctx(e);
        c.requireSyntax(Syntax.KOTLIN, "kt-dto-file");
        String className = e.dtoClassName();

        // The key DTO is this DTO re-rendered with only its identity fields,
        // so it shares everything but the name and the field list.
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

        // A DTO leaves nulls out of its JSON unless the model asks for them.
        if (!e.hasMeta("includenull")) {
            c.ensureClassAnnotation(AnnoCode.of("com.fasterxml.jackson.annotation.JsonInclude")
                    .valueRaw("com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL"));
        }

        for (FieldSpec f : e.fields()) {
            RuleText.registerFieldTypeImport(c, e, f, e.pkg("dto"));
        }
        RuleText.registerParentImport(c, parentName(e), e.pkg("dto"));

        c.var("packageName", e.pkg("dto"))
         .var("className", className)
         .var("classDeclaration", classDeclaration(e, className))
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("importList", RuleText.stripTrailing(c.renderImports()))
         .varLines("fieldList", RuleText.linesOf(RuleText.stripTrailing(c.renderFieldBlock(1))));
    }

    /**
     * Declared open unless abstract, because the DSL's @extend names one DTO
     * as another's parent and Kotlin closes a class to extension by default.
     * The entity gets this from the all-open compiler plugin; a DTO is not a
     * persistence type and no plugin preset covers it.
     */
    private String classDeclaration(EntitySpec e, String className) {
        return KotlinClass.declaration(
                className,
                e.hasMeta("abstract") ? "abstract" : "open",
                parentName(e),
                interfaces(e));
    }

    /** Serializable always, plus whatever the DSL's @implements adds. */
    private String[] interfaces(EntitySpec e) {
        String declared = e.metaValue("implements");
        if (declared == null || declared.isEmpty()) {
            return new String[] {"java.io.Serializable"};
        }
        String[] extra = declared.split(",");
        String[] all = new String[extra.length + 1];
        all[0] = "java.io.Serializable";
        for (int i = 0; i < extra.length; i++) {
            all[i + 1] = extra[i].trim();
        }
        return all;
    }

    /**
     * A DTO of its own declares no parent; one derived from a template
     * inherits whatever the DSL's @extend names.
     */
    private String parentName(EntitySpec e) {
        if (e.hasMeta("dto")) return null;
        return e.metaValue("extend");
    }
}
