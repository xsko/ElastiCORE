package io.elasticore.base.model.pub.rules.lang.java;

import io.elasticore.base.model.pub.rules.base.DtoBaseRules;
import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Full-file DTO rendering for the 3.0 clean mode (v3/dto.tmpl).
 *
 * The field annotations come from {@link DtoBaseRules}, shared with the
 * compatibility mode since a field's own DSL never depends on which skeleton
 * it lands in. What differs here is the class around them: the annotations
 * are rule-emitted rather than template-fixed, and the import list is built
 * from what actually survived - the used types, not the 2.x wildcard set.
 *
 * A search DTO is a different artifact with its own template, so it is not
 * produced here - the compatibility mode returns early for it too.
 */
public class DtoCleanFileRules implements CodeRules {

    @Rule(value = "v3-dto-file", target = "dto", order = 90, phase = Rule.Phase.TAIL)
    void file(EntitySpec e) {
        if (e.hasMeta("searchable")) {
            ctx(e).dontMake();
            return;
        }

        SourceCode c = ctx(e);
        String className = e.dtoClassName();

        // The key DTO carries the DTO's own imports and class annotations -
        // the same parameters re-rendered with the field list swapped - so
        // only the name and the fields differ.
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

        c.ensureClassAnnotation(AnnoCode.of("lombok.Getter"))
         .ensureClassAnnotation(AnnoCode.of("lombok.Setter"))
         .ensureClassAnnotation(AnnoCode.of("lombok.experimental.SuperBuilder"))
         .ensureClassAnnotation(AnnoCode.of("lombok.NoArgsConstructor"))
         .ensureClassAnnotation(AnnoCode.of("lombok.AllArgsConstructor"));

        // A DTO leaves nulls out of its JSON unless the model asks for them.
        // The value is written fully qualified so the class annotation needs
        // no second import for the nested Include type.
        if (!e.hasMeta("includenull")) {
            c.ensureClassAnnotation(AnnoCode.of("com.fasterxml.jackson.annotation.JsonInclude")
                    .valueRaw("com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL"));
        }

        for (FieldSpec f : e.fields()) {
            RuleText.registerFieldTypeImport(c, e, f, e.pkg("dto"));
        }
        RuleText.registerParentImport(c, parentOf(e), e.pkg("dto"));

        c.var("packageName", e.pkg("dto"))
         .var("className", className)
         .var("abstract", e.hasMeta("abstract") ? "abstract" : "")
         .var("extendInfo", extendClause(e))
         .var("implementInfo", implementClause(e))
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("importList", RuleText.stripTrailing(c.renderImports()))
         // The field slot sits inside the class body, so each line takes the
         // slot's indentation rather than carrying its own.
         .varLines("fieldList", RuleText.linesOf(RuleText.stripTrailing(c.renderFieldBlock(1))));
    }

    private String extendClause(EntitySpec e) {
        String parent = parentOf(e);
        return parent == null ? "" : String.format("extends %s", parent);
    }

    /**
     * A DTO of its own declares no parent; one derived from a template
     * inherits whatever the DSL's @extend names.
     */
    private String parentOf(EntitySpec e) {
        if (e.hasMeta("dto")) return null;
        return e.metaValue("extend");
    }

    /** Serializable always, plus whatever @implements adds. */
    private String implementClause(EntitySpec e) {
        String declared = e.metaValue("implements");
        String extra = (declared == null || declared.isEmpty()) ? "" : String.format(",%s", declared);
        return String.format("implements java.io.Serializable%s", extra);
    }
}
