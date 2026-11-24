package io.elasticore.base.model.pub.rules.lang.kotlin;

import io.elasticore.base.model.pub.rules.base.JpaEntityAnnotationRules;
import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.SourceCode;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Full-file entity rendering in Kotlin (kt/entity.tmpl).
 *
 * Only the class declaration and the file's own slots differ from the Java
 * clean mode: the persistence annotations, the column and relation rules,
 * the import collection - all of it is shared, because an annotation reads
 * the same in both languages and a rule that decides @Column(name = "x")
 * has nothing to say about which language writes it.
 *
 * What Kotlin needs of its own is the supertype list, which follows the
 * class name rather than preceding it, and calls the parent's constructor.
 *
 * Compose with the field rules: engineFor("jpa-entity", "jpa-entity-file-kt").
 */
public class KotlinEntityFileRules implements CodeRules {

    /**
     * A collection of entities is a MutableList.
     *
     * Kotlin's List is covariant, and against an open element type - every
     * entity is opened by the all-open plugin - the field compiles to
     * java.util.List&lt;? extends Category&gt;. Hibernate reads that wildcard as
     * a raw collection and refuses to build the persistence unit:
     * "Collection 'Category.subCategories' is declared with a raw type".
     * Nothing had booted a generated Kotlin entity with such a collection
     * until an HTTP test did. MutableList is invariant, so no wildcard is
     * written. Runs before the aggregate rules, which may narrow it further.
     */
    @Rule(value = "kt-entity-collections", order = 80)
    void invariantCollections(EntitySpec e, FieldSpec f) {
        SourceCode c = ctx(e);
        if (c.syntax() != Syntax.KOTLIN || !f.isList() || !f.isEntityRef()) return;
        c.field(f).type(String.format("MutableList<%s>", f.coreItemType()));
    }

    @Rule(value = "kt-entity-file", order = 90, phase = Rule.Phase.TAIL)
    void fileVars(EntitySpec e) {
        SourceCode c = ctx(e);
        c.requireSyntax(Syntax.KOTLIN, "kt-entity-file");

        // The same question a Java artifact asks of its fields, with the same
        // answer: the type a field declares is the type the file imports.
        for (FieldSpec f : e.fields()) {
            RuleText.registerFieldTypeImport(c, e, f, e.pkg("entity"));
        }
        RuleText.registerParentImport(c, e.parentName(), e.pkg("entity"));

        c.var("packageName", e.pkg("entity"))
         .var("className", e.getName())
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("classDeclaration", classDeclaration(e))
         .var("importList", RuleText.stripTrailing(
                 c.renderImports() + JpaEntityAnnotationRules.nativeLineImports(e, c.syntax())))
         .varLines("fieldList", RuleText.linesOf(RuleText.stripTrailing(c.renderFieldBlock())));
    }

    /**
     * The class is not declared open: a JPA entity is opened by the all-open
     * compiler plugin, which a Kotlin JPA project applies anyway because the
     * same requirement holds for every entity it writes. A DTO has no such
     * plugin and so declares itself open - see KotlinDtoFileRules.
     */
    private String classDeclaration(EntitySpec e) {
        return KotlinClass.declaration(
                e.getName(),
                e.hasMeta("abstract") ? "abstract" : "",
                e.parentName(),
                "java.io.Serializable");
    }
}
