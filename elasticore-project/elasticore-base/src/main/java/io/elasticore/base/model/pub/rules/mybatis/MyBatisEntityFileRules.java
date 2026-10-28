package io.elasticore.base.model.pub.rules.mybatis;

import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.base.RuleText;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The entity of a MyBatis domain: the class the JPA rules describe - its
 * fields, their types and initial values - without what only JPA reads.
 *
 * The field rules (jpa-entity) run as for JPA; this set then takes off every
 * JPA, Hibernate and Spring Data annotation and the lifecycle callbacks they
 * would have called, and writes the plain class on the v3 entity skeleton.
 * A to-one relation stays the target's class: the mapper fills it from the
 * join, and a write reads its key.
 */
public class MyBatisEntityFileRules implements CodeRules {

    @Rule(value = "mybatis-lombok", target = "entity", order = 6)
    void lombok(EntitySpec e) {
        ctx(e).ensureClassAnnotation(AnnoCode.of("lombok.Getter"))
              .ensureClassAnnotation(AnnoCode.of("lombok.Setter"))
              .ensureClassAnnotation(AnnoCode.of("lombok.NoArgsConstructor"));
    }

    /** Whether a type belongs to a persistence API that MyBatis code does not have. */
    static boolean persistenceOnly(String fqcn) {
        return fqcn.startsWith("jakarta.persistence.") || fqcn.startsWith("javax.persistence.")
                || fqcn.startsWith("org.hibernate.") || fqcn.startsWith("org.springframework.data.")
                // a native annotation the DSL handed through as written (@jpa:...)
                || fqcn.startsWith("native:");
    }

    @Rule(value = "mybatis-entity-plain", target = "entity", order = 80, phase = Rule.Phase.TAIL)
    void plain(EntitySpec e) {
        ctx(e).removeAnnotations(new java.util.function.Predicate<String>() {
            public boolean test(String fqcn) {
                return persistenceOnly(fqcn);
            }
        });
        // A @formula field stays, unmapped: JPA computes it in its SELECT,
        // MyBatis reads only the table's columns, so it is null - and the
        // domain mapper, which reads it for the DTO, still compiles.
    }

    @Rule(value = "mybatis-entity-file", target = "entity", order = 90, phase = Rule.Phase.TAIL)
    void fileVars(EntitySpec e) {
        SourceCode c = ctx(e);
        for (FieldSpec f : e.fields()) {
            RuleText.registerFieldTypeImport(c, e, f, e.pkg("entity"));
        }
        String modifier = e.hasMeta("abstract") ? "public abstract" : "public";
        c.var("packageName", e.pkg("entity"))
         .var("className", e.getName())
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("classDeclaration", "%s class %s implements java.io.Serializable {", modifier, e.getName())
         .var("importList", RuleText.stripTrailing(c.renderImports()))
         .varLines("fieldList", RuleText.linesOf(RuleText.stripTrailing(c.renderFieldBlock())));
    }
}
