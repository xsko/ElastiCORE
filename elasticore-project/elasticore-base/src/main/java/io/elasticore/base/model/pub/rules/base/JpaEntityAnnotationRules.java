package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.util.StringUtils;

import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The class-level persistence annotations of an entity: what makes it a
 * table, how it inherits, whether it is audited, which listener it carries.
 *
 * These say nothing about the language the class is written in - an
 * annotation reads the same in Java and Kotlin - so they are a set of their
 * own, composed by whichever file rule renders the shell around them. The
 * Java-only parts, Lombok accessors and the Java class declaration, stay
 * with that file rule.
 */
public class JpaEntityAnnotationRules implements CodeRules {

    /**
     * What belongs to the class JPA maps to a table and not to a mapped
     * superclass - moved to the concrete class when an entity is split
     * (ddd-behavior). @Audited is not here: envers wants it on both.
     */
    public static final java.util.List<String> TABLE_LEVEL = java.util.Collections.unmodifiableList(
            java.util.Arrays.asList(
                    "jakarta.persistence.Entity", "jakarta.persistence.Table",
                    "jakarta.persistence.Inheritance", "jakarta.persistence.DiscriminatorColumn",
                    "jakarta.persistence.DiscriminatorValue", "jakarta.persistence.IdClass",
                    "org.hibernate.annotations.Immutable", "org.hibernate.annotations.DynamicUpdate",
                    "org.hibernate.annotations.DynamicInsert"));

    /** The class annotations of an entity, into the current artifact - for a rule writing that class elsewhere. */
    public static void persistenceAnnotations(EntitySpec e) {
        new JpaEntityAnnotationRules().classAnnotations(e);
    }

    /** Persistence class annotations by entity kind (entity/abstract/embeddable). */
    @Rule(value = "v3-class-annotations", order = 5)
    void classAnnotations(EntitySpec e) {
        SourceCode c = ctx(e);

        if (e.hasMeta("embeddable")) {
            c.ensureClassAnnotation(AnnoCode.of("jakarta.persistence.Embeddable"));
            return;
        }
        if (e.hasMeta("abstract")) {
            c.ensureClassAnnotation(AnnoCode.of("jakarta.persistence.MappedSuperclass"));
            return;
        }

        // A @discriminator field turns the entity into a single-table hierarchy.
        for (FieldSpec f : e.fields()) {
            if (!f.hasDsl("discriminator")) continue;
            c.ensureClassAnnotation(AnnoCode.raw("jakarta.persistence.Inheritance",
                    "@Inheritance(strategy = InheritanceType.SINGLE_TABLE)"));
            c.ensureClassAnnotation(AnnoCode.of("jakarta.persistence.DiscriminatorColumn")
                    .val("name", f.dbColumnName())
                    .valRaw("discriminatorType", "DiscriminatorType.%s",
                            f.typeName().toUpperCase(java.util.Locale.ROOT)));
            String value = f.dslValue("discriminator");
            if (value != null) {
                c.ensureClassAnnotation(AnnoCode.of("jakarta.persistence.DiscriminatorValue")
                        .value(value));
            }
            break;
        }

        if (e.hasMeta("audited")) {
            c.ensureClassAnnotation(AnnoCode.of("org.hibernate.envers.Audited"));
        }

        c.ensureClassAnnotation(AnnoCode.of("jakarta.persistence.Entity"));

        // @rollup: the subclass's columns are in its parent's table (single
        // table, JPA's default), told apart by the discriminator value - as
        // the 2.x output does it. It has no table of its own. The value is
        // 2.x's too: the discriminator, else the target, else the entity's
        // name - never @rollup(X)'s own value, which is no discriminator.
        if (e.hasMeta("rollup")) {
            String value = e.metaValue("rollup.discriminator");
            if (value == null) value = e.metaValue("rolluptarget");
            if (value == null || value.trim().isEmpty()) value = e.getName();
            c.ensureClassAnnotation(AnnoCode.of("jakarta.persistence.DiscriminatorValue").value(value.trim()));
        } else {
            classTable(c, e);
        }

        if (e.hasMeta("immutable")) {
            c.ensureClassAnnotation(AnnoCode.of("org.hibernate.annotations.Immutable"));
        } else if (!"false".equals(e.metaValue("dynamicupdate"))) {
            c.ensureClassAnnotation(AnnoCode.of("org.hibernate.annotations.DynamicUpdate"));
        }
        if (e.hasMeta("dynamicinsert")) {
            c.ensureClassAnnotation(AnnoCode.of("org.hibernate.annotations.DynamicInsert"));
        }
        if (e.pkCount() > 1) {
            c.ensureClassAnnotation(AnnoCode.raw("jakarta.persistence.IdClass",
                    "@IdClass(%s)", c.syntax().classLiteral(e.getName() + "Identity")));
        }
    }

    private void classTable(SourceCode c, EntitySpec e) {
        AnnoCode table = AnnoCode.of("jakarta.persistence.Table").val("name", tableName(e));
        if (e.schema() != null && !e.schema().isEmpty()) {
            table.val("schema", e.schema());
        }
        if (!e.indexes().isEmpty()) {
            // A named array parameter: braced in Java, bracketed in Kotlin.
            boolean kt = ctx(e).syntax() == Syntax.KOTLIN;
            table.valRaw("indexes", kt ? "[%s]" : "{%s}", renderIndexes(e));
            c.addImport("jakarta.persistence.Index");
        }
        c.ensureClassAnnotation(table);
    }

    /** Index list for the clean mode: simple names, one entry per line. */
    private String renderIndexes(EntitySpec e) {
        StringBuilder sb = new StringBuilder();
        List<EntitySpec.IndexSpec> list = e.indexes();
        for (int i = 0; i < list.size(); i++) {
            EntitySpec.IndexSpec idx = list.get(i);
            StringBuilder attrs = new StringBuilder();
            if (idx.name() != null && !idx.name().isEmpty()) {
                attrs.append(String.format("name = %s", RuleText.quote(idx.name())));
            }
            if (idx.columnList() != null && !idx.columnList().isEmpty()) {
                if (attrs.length() > 0) attrs.append(", ");
                attrs.append(String.format("columnList = %s", RuleText.quote(idx.columnList())));
            }
            if (idx.unique() != null && !idx.unique().isEmpty()) {
                if (attrs.length() > 0) attrs.append(", ");
                attrs.append(String.format("unique = %s", idx.unique()));
            }
            if (i > 0) sb.append(", ");
            // Nested inside another annotation's argument, Kotlin names the
            // annotation as a constructor call and drops the @.
            String at = ctx(e).syntax() == Syntax.KOTLIN ? "" : "@";
            sb.append(String.format("%sIndex(%s)", at, attrs));
        }
        return sb.toString();
    }

    /** Lombok accessors - suppress("v3-lombok") drops annotations AND imports. */

    /** Spring auditing listener for persistent classes. */
    @Rule(value = "v3-auditing-listener", order = 7)
    void auditingListener(EntitySpec e) {
        if (e.hasMeta("embeddable")) return;
        ctx(e).addEntityListener("AuditingEntityListener")
              .addImport("org.springframework.data.jpa.domain.support.AuditingEntityListener");
    }

    /** The table an entity maps to: what @table names, or its snake-cased name. */
    private String tableName(EntitySpec e) {
        String explicit = e.metaValue("table");
        if (explicit != null && !explicit.isEmpty()) return explicit;
        return String.format("%s%s", e.tablePrefix(), StringUtils.camelToSnake(e.getName()));
    }
}
