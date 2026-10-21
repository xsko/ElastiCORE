package io.elasticore.base.model.pub.rules.lang.java;

import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.util.StringUtils;

import java.util.List;
import java.util.Locale;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Class-level entity annotations, ported 1:1 from
 * EntitySrcFilePublisher#getEntityAnnotation into the "classAnnotations"
 * line block that fills the ${classAnnotationList} template slot.
 *
 * Emission order is the publisher's:
 *   @Inheritance / @DiscriminatorColumn / @DiscriminatorValue  (from a
 *       field carrying @discriminator)
 *   @Audited
 *   @MappedSuperclass or @Entity
 *   @Table with its indexes and schema, @Immutable, @DynamicUpdate,
 *       @DynamicInsert                                    (entity classes)
 *   @Embeddable
 *   @DiscriminatorValue                                   (roll-up)
 *   @IdClass
 *
 * Names are emitted the way 2.x emits them, fully qualified for Table and
 * Index, so the compatibility mode reproduces the committed sources.
 */
public class JpaEntityClassRules implements CodeRules {

    private static final String BLOCK = "classAnnotations";

    /** The EE namespace 2.x threads through as j2eePkgNm. */
    private static final String EE = "jakarta";

    @Rule(value = "jpa-class-annotations", target = "entity", order = 1)
    void classAnnotations(EntitySpec e) {
        SourceCode c = ctx(e);

        boolean isInheritance = discriminator(c, e);
        boolean hasPkField = hasPrimaryKey(e);

        if (e.hasMeta("audited")) {
            c.line(BLOCK, "@Audited");
            c.addImport("org.hibernate.envers.Audited");
        }

        boolean isEntityClass = true;
        if (!isInheritance && !hasPkField && e.hasMeta("abstract")) {
            isEntityClass = false;
            c.line(BLOCK, "@MappedSuperclass");
        } else if (!e.hasMeta("embeddable")) {
            c.line(BLOCK, "@Entity");
        }

        if (isEntityClass) {
            tableAndHibernateFlags(c, e);
        }

        if (e.hasMeta("embeddable")) {
            c.line(BLOCK, "@Embeddable");
        }

        rollUpDiscriminator(c, e);

        if (e.pkCount() > 1) {
            c.line(BLOCK, "@IdClass(%sIdentity.class)", e.getName());
        }
    }

    /** A field marked @discriminator turns the entity into a single-table hierarchy. */
    /**
     * Annotations the DSL hands to the class as written. 2.x appends these
     * after everything getEntityAnnotation collected, so they close the block.
     */
    @Rule(value = "jpa-class-native-annotation", target = "entity", order = 2)
    void nativeAnnotations(EntitySpec e) {
        for (String line : e.nativeEntityAnnotationLines()) {
            ctx(e).line(BLOCK, "%s", line);
        }
    }

    private boolean discriminator(SourceCode c, EntitySpec e) {
        for (FieldSpec f : e.fields()) {
            if (!f.hasDsl("discriminator")) continue;

            c.line(BLOCK, "@Inheritance(strategy = InheritanceType.SINGLE_TABLE)");
            c.line(BLOCK, "@DiscriminatorColumn(name = %s, discriminatorType = DiscriminatorType.%s)",
                    RuleText.quote(f.dbColumnName()), f.typeName().toUpperCase(Locale.ROOT));

            String defaultValue = f.dslValue("discriminator");
            if (defaultValue != null) {
                c.line(BLOCK, "@DiscriminatorValue(%s)", RuleText.quote(defaultValue));
            }
            return true;
        }
        return false;
    }

    private boolean hasPrimaryKey(EntitySpec e) {
        for (FieldSpec f : e.fields()) {
            if (f.hasDsl("id") || f.hasDsl("pk")) return true;
        }
        return false;
    }

    private void tableAndHibernateFlags(SourceCode c, EntitySpec e) {
        boolean isRollUp = e.hasMeta("rollup");
        String tableMeta = e.metaValue("table");

        if (!isRollUp) {
            table(c, e, tableMeta);

            if (e.hasMeta("immutable")) {
                c.line(BLOCK, "@org.hibernate.annotations.Immutable");
                if (tableMeta == null) {
                    String viewName = e.metaValue("immutable");
                    if (viewName != null) {
                        c.line(BLOCK, "@%s.persistence.Table(name=%s)", EE, RuleText.quote(viewName));
                    }
                }
            }
        }

        if (!"false".equals(e.metaValue("dynamicupdate"))) {
            if (!e.hasMeta("immutable")) {
                c.line(BLOCK, "@org.hibernate.annotations.DynamicUpdate");
            }
        }
        if (e.hasMeta("dynamicinsert")) {
            c.line(BLOCK, "@org.hibernate.annotations.DynamicInsert");
        }
    }

    /** @Table carries the name, the schema and the index block, in that order. */
    private void table(SourceCode c, EntitySpec e, String tableMeta) {
        String tableName = tableMeta;
        String prefix = e.tablePrefix();
        if (prefix != null && !prefix.isEmpty()) {
            if (tableName == null) {
                tableName = StringUtils.camelToSnake(e.getName());
            }
            tableName = String.format("%s%s", prefix, tableName);
        }

        StringBuilder attrs = new StringBuilder();
        if (tableName != null) {
            attrs.append(String.format("name=%s", RuleText.quote(tableName)));
        }
        if (e.schema() != null && !e.schema().isEmpty()) {
            if (attrs.length() > 0) attrs.append(", ");
            attrs.append(String.format("schema=%s", RuleText.quote(e.schema())));
        }

        List<EntitySpec.IndexSpec> indexes = e.indexes();
        if (indexes.isEmpty() && attrs.length() == 0) {
            return;
        }

        if (indexes.isEmpty()) {
            c.line(BLOCK, "@%s.persistence.Table(%s)", EE, attrs.toString());
            return;
        }

        if (attrs.length() > 0) attrs.append(",");
        c.line(BLOCK, "@%s.persistence.Table(%s", EE, attrs.toString());
        c.line(BLOCK, "  indexes = {");
        for (int i = 0; i < indexes.size(); i++) {
            EntitySpec.IndexSpec idx = indexes.get(i);
            c.line(BLOCK, "   %s@%s.persistence.Index(%s)",
                    (i == 0 ? " " : ","), EE, indexAttributes(idx));
        }
        c.line(BLOCK, "  }");
        c.line(BLOCK, "  )");
    }

    private String indexAttributes(EntitySpec.IndexSpec idx) {
        StringBuilder sb = new StringBuilder();
        if (idx.name() != null && !idx.name().isEmpty()) {
            sb.append(String.format("name=%s", RuleText.quote(idx.name())));
        }
        if (idx.columnList() != null && !idx.columnList().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            String value = idx.columnList();
            if (!(value.startsWith("\"") && value.endsWith("\""))) {
                value = RuleText.quote(value);
            }
            sb.append(String.format("columnList=%s", value));
        }
        if (idx.unique() != null && !idx.unique().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(String.format("unique=%s", idx.unique()));
        }
        return sb.toString();
    }

    /**
     * A roll-up entity carries its discriminator value at class level: the
     * discriminator, else the target, else the entity's name - as 2.x
     * (EntitySrcFilePublisher), which never reads @rollup(X)'s own value.
     */
    private void rollUpDiscriminator(SourceCode c, EntitySpec e) {
        if (!e.hasMeta("rollup")) return;

        String value = e.metaValue("rollup.discriminator");
        if (value == null) value = e.metaValue("rolluptarget");
        if (value == null) value = e.getName();

        c.line(BLOCK, "@DiscriminatorValue(%s)", RuleText.quote(value));
    }
}
