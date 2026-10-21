package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.model.pub.rules.lang.java.JpaEntityClassRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Class-level entity annotations, the layer getEntityAnnotation produces.
 * Nothing of it existed in the rules: no indexes, no schema, no @Audited,
 * no inheritance, no @Immutable, no @DynamicInsert, no roll-up value.
 *
 * The first three cases are pinned to sources committed in
 * elasticore-template, so the expected text is not invented.
 */
public class EntityClassAnnotationTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    private String render(EntitySpec e) {
        return new RuleEngine().register(new JpaEntityClassRules())
                .run(e, "entity").renderBlock("classAnnotations");
    }

    // ---- golden-backed cases -------------------------------------------

    /**
     * Article.java in elasticore-template, from
     *   meta: entity @audited @Index(name=.., columnList=.., unique="true")
     *                          @Index(name=.., columnList=..)
     * with table.prefix cms_.
     */
    @Test
    public void auditedEntityWithIndexes_matchesCommittedArticle() {
        EntitySpec e = new EntitySpec("Article")
                .tablePrefix("cms_")
                .meta("audited", null)
                .index("idx_cms_art_slug", "slug", "true")
                .index("idx_cms_art_pub_date", "publish_date", null)
                .addField(loader.parse("articleId", "string(36) @id"));

        String expected =
                "@Audited\n"
              + "@Entity\n"
              + "@jakarta.persistence.Table(name=\"cms_article\",\n"
              + "  indexes = {\n"
              + "    @jakarta.persistence.Index(name=\"idx_cms_art_slug\", columnList=\"slug\", unique=true)\n"
              + "   ,@jakarta.persistence.Index(name=\"idx_cms_art_pub_date\", columnList=\"publish_date\")\n"
              + "  }\n"
              + "  )\n"
              + "@org.hibernate.annotations.DynamicUpdate\n";

        assertEquals(expected, render(e));
    }

    /** Bookmark.java: the plain entity form. */
    @Test
    public void plainEntity_matchesCommittedBookmark() {
        EntitySpec e = new EntitySpec("Bookmark")
                .tablePrefix("cms_")
                .addField(loader.parse("bookmarkId", "long @id @sequence"));

        String expected =
                "@Entity\n"
              + "@jakarta.persistence.Table(name=\"cms_bookmark\")\n"
              + "@org.hibernate.annotations.DynamicUpdate\n";

        assertEquals(expected, render(e));
    }

    /** ContentBase.java: an abstract parent gets @MappedSuperclass and nothing else. */
    @Test
    public void abstractEntity_matchesCommittedContentBase() {
        EntitySpec e = new EntitySpec("ContentBase")
                .tablePrefix("cms_")
                .meta("abstract", null)
                .addField(loader.parse("createdBy", "string(50)"));

        assertEquals("@MappedSuperclass\n", render(e));
    }

    // ---- features the template project does not exercise ---------------

    @Test
    public void discriminatorField_producesSingleTableInheritance() {
        EntitySpec e = new EntitySpec("Payment")
                .tablePrefix("pay_")
                .meta("table", "payment")
                .addField(loader.parse("paymentId", "long @id"))
                .addField(loader.parse("kind", "string(10) @discriminator(CARD)"));

        String out = render(e);

        assertTrue(out.contains("@Inheritance(strategy = InheritanceType.SINGLE_TABLE)"), out);
        assertTrue(out.contains(
                "@DiscriminatorColumn(name = \"kind\", discriminatorType = DiscriminatorType.STRING)"), out);
        assertTrue(out.contains("@DiscriminatorValue(\"CARD\")"), out);
        assertTrue(out.contains("@Entity"), out);
    }

    /** An abstract entity that is a hierarchy root stays an @Entity. */
    @Test
    public void abstractWithDiscriminator_staysEntity() {
        EntitySpec e = new EntitySpec("Payment")
                .meta("abstract", null)
                .addField(loader.parse("kind", "string(10) @discriminator"));

        String out = render(e);
        assertTrue(out.contains("@Entity"), out);
        assertFalse(out.contains("@MappedSuperclass"), out);
    }

    @Test
    public void schema_isCarriedIntoTable() {
        EntitySpec e = new EntitySpec("Order")
                .meta("table", "tb_order")
                .schema("sales")
                .addField(loader.parse("orderId", "long @id"));

        assertTrue(render(e).contains(
                "@jakarta.persistence.Table(name=\"tb_order\", schema=\"sales\")"), render(e));
    }

    /** @immutable maps a view and suppresses @DynamicUpdate. */
    @Test
    public void immutable_emitsViewTableAndSuppressesDynamicUpdate() {
        EntitySpec e = new EntitySpec("OrderView")
                .meta("immutable", "v_order")
                .addField(loader.parse("orderId", "long @id"));

        String out = render(e);
        assertTrue(out.contains("@org.hibernate.annotations.Immutable"), out);
        assertTrue(out.contains("@jakarta.persistence.Table(name=\"v_order\")"), out);
        assertFalse(out.contains("DynamicUpdate"), out);
    }

    @Test
    public void dynamicUpdateFalse_suppressesTheAnnotation() {
        EntitySpec e = new EntitySpec("Order")
                .meta("dynamicupdate", "false")
                .addField(loader.parse("orderId", "long @id"));

        assertFalse(render(e).contains("DynamicUpdate"), render(e));
    }

    @Test
    public void dynamicInsert_isEmitted() {
        EntitySpec e = new EntitySpec("Order")
                .meta("dynamicinsert", null)
                .addField(loader.parse("orderId", "long @id"));

        assertTrue(render(e).contains("@org.hibernate.annotations.DynamicInsert"), render(e));
    }

    @Test
    public void embeddable_emitsEmbeddableWithoutEntity() {
        EntitySpec e = new EntitySpec("Address")
                .meta("embeddable", null)
                .addField(loader.parse("zipCode", "string(7)"));

        String out = render(e);
        assertTrue(out.contains("@Embeddable"), out);
        assertFalse(out.contains("@Entity"), out);
    }

    @Test
    public void rollUp_emitsDiscriminatorValueAndNoTable() {
        EntitySpec e = new EntitySpec("PremiumOrder")
                .meta("rollup", null)
                .meta("rolluptarget", "Order")
                .addField(loader.parse("orderId", "long @id"));

        String out = render(e);
        assertTrue(out.contains("@DiscriminatorValue(\"Order\")"), out);
        assertFalse(out.contains("persistence.Table"), out);
    }

    /** @rollup(Item)'s own value is no discriminator: the entity's name, as 2.x writes it. */
    @Test
    public void rollUp_ownValueIsNotTheDiscriminator() {
        EntitySpec e = new EntitySpec("PremiumOrder")
                .meta("rollup", "Order")
                .addField(loader.parse("orderId", "long @id"));

        String out = render(e);
        assertTrue(out.contains("@DiscriminatorValue(\"PremiumOrder\")"), out);
    }

    @Test
    public void compositeKey_emitsIdClass() {
        EntitySpec e = new EntitySpec("ProjectAssignment")
                .tablePrefix("hr_")
                .addField(loader.parse("empId", "string(36) @id"))
                .addField(loader.parse("projectCode", "string(20) @id"));

        assertTrue(render(e).contains("@IdClass(ProjectAssignmentIdentity.class)"), render(e));
    }
}
