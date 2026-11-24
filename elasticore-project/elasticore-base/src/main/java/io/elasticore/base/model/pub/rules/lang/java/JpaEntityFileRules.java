package io.elasticore.base.model.pub.rules.lang.java;

import io.elasticore.base.model.pub.rules.base.JpaEntityAnnotationRules;
import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.util.StringUtils;

import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Full-file entity rendering for the 3.0 clean mode (v3/entity.tmpl).
 *
 * Unlike the 2.x template, the v3 skeleton contains NO fixed imports and
 * NO fixed class annotations: everything is rule-emitted, so suppressing
 * a rule (e.g. v3-lombok) removes both the annotations and their imports.
 * The import block lists exactly the types the surviving output uses.
 *
 * Compose with the field rules: engineFor("jpa-entity", "jpa-entity-file").
 * The 2.x-compatible mode (template-owned imports, byte-identical output)
 * remains untouched and gated by elcoreRulesCheck.
 */
public class JpaEntityFileRules implements CodeRules {

    @Rule(value = "v3-lombok", order = 6)
    void lombok(EntitySpec e) {
        ctx(e).ensureClassAnnotation(AnnoCode.of("lombok.Getter"))
              .ensureClassAnnotation(AnnoCode.of("lombok.Setter"))
              .ensureClassAnnotation(AnnoCode.of("lombok.NoArgsConstructor"));
    }

    @Rule(value = "v3-file-vars", order = 90, phase = Rule.Phase.TAIL)
    void fileVars(EntitySpec e) {
        SourceCode c = ctx(e);

        // A relation field's own declared type wants an import the same way
        // a DTO field's does: a list needs java.util.List, an enum resolves
        // through the domain's enum package, and another entity - same
        // domain or across one - resolves through the component registry.
        for (FieldSpec f : e.fields()) {
            RuleText.registerFieldTypeImport(c, e, f, e.pkg("entity"));
        }

        String modifier = e.hasMeta("abstract") ? "public abstract" : "public";
        String extendsClause = e.parentName() == null
                ? ""
                : String.format(" extends %s", e.parentName());

        // A parent declared in another domain's entity package - a shared
        // base class - needs the same import a field's own type would.
        RuleText.registerParentImport(c, e.parentName(), e.pkg("entity"));

        c.var("packageName", e.pkg("entity"))
         .var("className", e.getName())
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("classDeclaration", "%s class %s%s implements java.io.Serializable {",
                 modifier, e.getName(), extendsClause)
         .var("importList", RuleText.stripTrailing(
                 c.renderImports() + JpaEntityAnnotationRules.nativeLineImports(e, c.syntax())))
         // The field slot sits inside the class body, so each line takes the
         // slot's indentation rather than carrying its own.
         .varLines("fieldList", RuleText.linesOf(RuleText.stripTrailing(c.renderFieldBlock())));
    }

}
