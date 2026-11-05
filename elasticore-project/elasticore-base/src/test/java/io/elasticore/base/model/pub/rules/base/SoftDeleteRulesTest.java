package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.ModelCheck;
import io.elasticore.base.model.pub.rules.RuleCatalog;
import io.elasticore.base.model.pub.rules.SourceCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** @deleted, rule by rule, on a model built in memory. */
public class SoftDeleteRulesTest {

    @BeforeEach
    void start() {
        // timestamp is a type of the rule engine, as a 3.0 build reads it.
        io.elasticore.base.model.entity.BaseFieldType.ruleEngineTypes(true);
        ModelCheck.reset();
    }

    @AfterEach
    void forget() {
        io.elasticore.base.model.entity.BaseFieldType.ruleEngineTypes(false);
        ModelCheck.reset();
    }

    private static EntitySpec product(String... extra) {
        String base = "com.acme.shop";
        EntitySpec e = new EntitySpec("Product")
                .domain("shop", "Shop")
                .meta("expose", "")
                .pkg("entity", base + ".entity")
                .pkg("repository", base + ".repository")
                .pkg("dto", base + ".dto")
                .field("productId", "long @id @sequence")
                .field("name", "string(30)!");
        for (int i = 0; i < extra.length; i += 2) e.field(extra[i], extra[i + 1]);
        return e;
    }

    private static String render(EntitySpec e, String bundle, String target) {
        SourceCode.TemplateSet set = bundle.endsWith("-kt") ? SourceCode.TemplateSet.KOTLIN : SourceCode.TemplateSet.V3;
        return RuleCatalog.standard()
                .engineFor(bundle)
                .templateSet(set)
                .run(e, target)
                .render()
                .replace("\r", "")
                .replaceAll("(?m)^[ \t]+", "");
    }

    // ---- what is honoured -------------------------------------------------

    @Test
    public void aTimeFieldIsTheOneThatSaysWhen() {
        EntitySpec e = product("deletedAt", "timestamp @deleted");
        assertTrue(SoftDelete.applies(e));
        assertEquals("deletedAt", SoftDelete.field(e).name());
        assertEquals("deleted_at", SoftDelete.column(e));
        assertEquals("t.deleted_at IS NULL", SoftDelete.live(e, "t"));
        assertEquals("deleted_at IS NULL", SoftDelete.live(e, null));
        assertEquals("deleted_at IS NULL", SoftDelete.live(e, ""));
        assertTrue(SoftDelete.applies(product("retiredAt", "datetime @deleted")));
        assertFalse(SoftDelete.applies(product()));
        assertNull(SoftDelete.field(null));
        assertFalse(SoftDelete.applies(null));
    }

    @Test
    public void theDeleteWritesTheTimeByKeyAndVersion() {
        String plain = render(product("deletedAt", "timestamp @deleted"), "jpa-springboot-v3", "entity");
        assertTrue(plain.contains("WHERE product_id = ? AND deleted_at IS NULL\")"), plain);
        String versioned = render(
                product("version", "long @jpa:Version", "deletedAt", "timestamp @deleted"),
                "jpa-springboot-v3",
                "entity");
        assertTrue(
                versioned.contains(
                        "UPDATE product SET deleted_at = CURRENT_TIMESTAMP WHERE product_id = ? AND version = ?"
                                + " AND deleted_at IS NULL\")"),
                versioned);
    }

    @Test
    public void whatCannotBeHonouredIsSaid() {
        assertEquals(null, SoftDelete.problem(product()));
        assertTrue(SoftDelete.problem(product("a", "timestamp @deleted", "b", "datetime @deleted"))
                .contains("has @deleted on a and b - one field holds when a row was deleted"));
        assertTrue(SoftDelete.problem(product("flag", "boolean @deleted")).contains(".flag @deleted is"));
        assertTrue(SoftDelete.problem(product("day", "date @deleted")).contains("datetime or timestamp"));
        assertTrue(SoftDelete.problem(new EntitySpec("Log").field("at", "timestamp @deleted"))
                .contains("needs a key of one field"));
        assertTrue(SoftDelete.problem(new EntitySpec("Pair")
                        .field("a", "long @id")
                        .field("b", "long @id")
                        .field("at", "timestamp @deleted"))
                .contains("composite key is not supported yet"));
        assertTrue(SoftDelete.problem(product("deletedAt", "timestamp @deleted").meta("embeddable", ""))
                .contains("@embeddable"));
        assertTrue(SoftDelete.problem(product("deletedAt", "timestamp @deleted").meta("rollup", ""))
                .contains("@rollup entity is not supported yet"));
        assertTrue(SoftDelete.problem(product("kind", "string(3) @discriminator(P)", "deletedAt", "timestamp @deleted"))
                .contains("single-table hierarchy"));
        assertTrue(SoftDelete.problem(product("deletedAt", "timestamp @deleted").config("mapping", "xml"))
                .contains("mapping: xml is not supported yet"));
        // MyBatis writes its own statements, mapping: xml or not.
        assertNull(SoftDelete.problem(product("deletedAt", "timestamp @deleted")
                .config("mapping", "xml")
                .config("mode", "mybatis")));
        // A parent is checked in what extends it.
        EntitySpec parent = new EntitySpec("Base").meta("abstract", "").field("deletedAt", "timestamp @deleted");
        assertNull(SoftDelete.problem(parent));
        assertFalse(SoftDelete.applies(parent));
    }

    @Test
    public void theBuildStopsOnWhatCannotBeHonoured() {
        SoftDelete.check(product("flag", "boolean @deleted"));
        assertEquals(1, ModelCheck.failures().size());
        assertTrue(
                ModelCheck.failures().get(0).startsWith("[elcore3] Product.flag @deleted is"),
                ModelCheck.failures().toString());
        ModelCheck.reset();
        SoftDelete.check(product("deletedAt", "timestamp @deleted"));
        assertTrue(ModelCheck.failures().isEmpty());
    }

    @Test
    public void aRowItWouldRemoveOutrightMustBeKeptToo() {
        EntitySpec order = product(
                        "deletedAt",
                        "timestamp @deleted",
                        "lines",
                        "List<Line> @OneToMany(mappedBy=\"order\") @cascade",
                        "notes",
                        "List<Note> @OneToMany(mappedBy=\"order\", orphanRemoval=true)",
                        "tags",
                        "List<Tag> @OneToMany(mappedBy=\"order\")",
                        "kept",
                        "List<Kept> @OneToMany(mappedBy=\"order\") @cascade")
                .knownEntity("Line")
                .knownEntity("Note")
                .knownEntity("Tag")
                .knownEntity("Kept")
                .knownSoftDeleted("Kept", "deleted_at");
        assertEquals(Arrays.asList("lines (Line)", "notes (Note)"), SoftDelete.removedWithIt(order));
        SoftDelete.check(order);
        assertEquals(1, ModelCheck.failures().size());
        assertTrue(
                ModelCheck.failures()
                        .get(0)
                        .contains("a delete would remove lines (Line), notes (Note) outright with it"
                                + " - mark those entities @deleted too"),
                ModelCheck.failures().toString());

        ModelCheck.reset();
        EntitySpec one = product(
                        "deletedAt",
                        "timestamp @deleted",
                        "lines",
                        "List<Line> @OneToMany(mappedBy=\"order\") @cascade")
                .knownEntity("Line");
        SoftDelete.check(one);
        assertTrue(
                ModelCheck.failures().get(0).contains("mark that entity @deleted too"),
                ModelCheck.failures().toString());

        // A member of the aggregate goes with its root, cascade said or not.
        EntitySpec root = product(
                        "deletedAt", "timestamp @deleted", "items", "List<Item> @OneToMany(mappedBy=\"order\")")
                .knownEntity("Item")
                .knownAggregate("Item", "Product")
                .knownAggregate("Product", "Product");
        assertEquals(Collections.singletonList("items (Item)"), SoftDelete.removedWithIt(root));
        assertTrue(SoftDelete.removedWithIt(
                        product("lines", "List<Line> @OneToMany @cascade").knownEntity("Line"))
                .isEmpty());
    }

    @Test
    public void aToOneMemberGoesWithItsRootAndAPersistCascadeDeletesNothing() {
        EntitySpec root = product("deletedAt", "timestamp @deleted", "shipping", "Address @OneToOne")
                .knownEntity("Address")
                .knownAggregate("Address", "Product")
                .knownAggregate("Product", "Product");
        assertEquals(Collections.singletonList("shipping (Address)"), SoftDelete.removedWithIt(root));
        EntitySpec persist = product(
                        "deletedAt",
                        "timestamp @deleted",
                        "lines",
                        "List<Line> @OneToMany(mappedBy=\"order\") @cascade(PERSIST)",
                        "parts",
                        "List<Part> @OneToMany(mappedBy=\"order\") @cascade(REMOVE)")
                .knownEntity("Line")
                .knownEntity("Part");
        assertEquals(Collections.singletonList("parts (Part)"), SoftDelete.removedWithIt(persist));
        EntitySpec onetoone = product(
                        "deletedAt", "timestamp @deleted", "card", "Card @OneToOne(cascade=CascadeType.ALL)")
                .knownEntity("Card");
        assertEquals(Collections.singletonList("card (Card)"), SoftDelete.removedWithIt(onetoone));
    }

    @Test
    public void aKeyTheRequestGivesNeedsAVersionUnderJpa() {
        EntitySpec given =
                new EntitySpec("Country").field("isoCode", "string(2) @id").field("deletedAt", "timestamp @deleted");
        assertTrue(
                SoftDelete.problem(given).contains("a key the request gives (isoCode) needs a version"),
                SoftDelete.problem(given));
        assertNull(SoftDelete.problem(new EntitySpec("Country")
                .field("isoCode", "string(2) @id")
                .field("version", "long @jpa:Version")
                .field("deletedAt", "timestamp @deleted")));
        assertNull(SoftDelete.problem(new EntitySpec("Country")
                .field("isoCode", "string(2) @id")
                .field("deletedAt", "timestamp @deleted")
                .config("mode", "mybatis")));
    }

    // ---- JPA ----------------------------------------------------------------

    @Test
    public void jpaKeepsTheRowOnRemove() {
        String java = render(product("deletedAt", "timestamp @deleted"), "jpa-springboot-v3", "entity");
        assertTrue(
                java.contains(
                        "@SQLDelete(sql = \"UPDATE product SET deleted_at = CURRENT_TIMESTAMP WHERE product_id = ? AND deleted_at IS NULL\")"),
                java);
        assertTrue(
                java.contains("@Column(name = \"deleted_at\", insertable = false, updatable = false)")
                        || java.contains("@Column(insertable = false, updatable = false)"),
                java);
        assertFalse(java.contains("SQLRestriction"), java);
        String plain = render(product(), "jpa-springboot-v3", "entity");
        assertFalse(plain.contains("SQLDelete") || plain.contains("insertable = false"), plain);
    }

    @Test
    public void aCollectionOfKeptRowsLeavesTheDeletedOut() {
        EntitySpec order = product(
                        "lines",
                        "List<Line> @OneToMany(mappedBy=\"order\")",
                        "tags",
                        "List<Tag> @OneToMany(mappedBy=\"order\")")
                .knownEntity("Line")
                .knownEntity("Tag")
                .knownSoftDeleted("Line", "removed_at");
        String java = render(order, "jpa-springboot-v3", "entity");
        assertTrue(
                java.matches(
                        "(?s).*@SQLRestriction\\(\"removed_at IS NULL\"\\)\\s+(@\\S+\\s+)*private List<Line> lines.*"),
                java);
        assertFalse(java.matches("(?s).*@SQLRestriction\\([^)]*\\)\\s+(@\\S+\\s+)*private List<Tag> tags.*"), java);
        String mybatis = render(order.config("mode", "mybatis"), "mybatis-springboot", "entity");
        assertFalse(mybatis.contains("SQLRestriction"), mybatis);
    }

    @Test
    public void theRepositoryFindsNoDeletedRow() {
        EntitySpec e = product("deletedAt", "timestamp @deleted");
        String java = render(e, "jpa-springboot-v3", "repository");
        assertTrue(
                java.contains(
                        "@Override\n@Query(\"SELECT e FROM Product e WHERE e.productId = :id AND e.deletedAt IS NULL\")\n"
                                + "java.util.Optional<Product> findById(@Param(\"id\") Long id);"),
                java);
        assertTrue(
                java.contains(
                        "@Query(\"SELECT COUNT(e) > 0 FROM Product e WHERE e.productId = :id AND e.deletedAt IS NULL\")\n"
                                + "boolean existsById(@Param(\"id\") Long id);"),
                java);
        assertTrue(
                java.contains(
                        "@Query(\"SELECT e FROM Product e WHERE e.deletedAt IS NULL\")\njava.util.List<Product> findAll();"),
                java);
        assertTrue(java.contains("java.util.List<Product> findAll(org.springframework.data.domain.Sort sort);"), java);
        assertTrue(java.contains("Page<Product> findAll(Pageable pageable);"), java);
        assertTrue(
                java.contains("@Query(\"SELECT COUNT(e) FROM Product e WHERE e.deletedAt IS NULL\")\nlong count();"),
                java);

        String kt = render(e, "jpa-springboot-kt", "repository");
        assertTrue(
                kt.contains("@Query(\"SELECT e FROM Product e WHERE e.productId = :id AND e.deletedAt IS NULL\")\n"
                        + "override fun findById(@Param(\"id\") id: Long): java.util.Optional<Product>\n"),
                kt);
        assertTrue(kt.contains("override fun existsById(@Param(\"id\") id: Long): Boolean"), kt);
        assertTrue(kt.contains("override fun findAll(): List<Product>"), kt);
        assertTrue(kt.contains("override fun findAll(sort: org.springframework.data.domain.Sort): List<Product>"), kt);
        assertTrue(kt.contains("override fun findAll(pageable: Pageable): Page<Product>"), kt);
        assertTrue(kt.contains("override fun count(): Long"), kt);
        assertFalse(kt.contains("@Override"), kt);

        String plain = render(product(), "jpa-springboot-v3", "repository");
        assertFalse(plain.contains("findById"), plain);
    }

    // ---- MyBatis ------------------------------------------------------------

    @Test
    public void theMapperDeletesByWritingTheTimeAndReadsOnlyTheLive() {
        EntitySpec e = product("deletedAt", "timestamp @deleted").config("mode", "mybatis");
        String xml = render(e, "mybatis-springboot", "mybatisXml");
        assertTrue(xml.contains("WHERE t.product_id = #{id,jdbcType=BIGINT} AND t.deleted_at IS NULL"), xml);
        assertTrue(
                xml.contains(
                        "SELECT COUNT(*) FROM product WHERE product_id = #{id,jdbcType=BIGINT} AND deleted_at IS NULL"),
                xml);
        assertTrue(xml.contains("WHERE t.deleted_at IS NULL <include refid=\"orderBy\"/>"), xml);
        assertTrue(xml.contains("<update id=\"deleteById\">"), xml);
        assertTrue(
                xml.contains(
                        "UPDATE product SET deleted_at = CURRENT_TIMESTAMP WHERE product_id = #{id,jdbcType=BIGINT} AND deleted_at IS NULL"),
                xml);
        assertFalse(xml.contains("DELETE FROM product"), xml);
        // The one statement that sees a deleted row: what names it checks it still exists.
        assertTrue(xml.contains("<select id=\"findAnyById\" resultMap=\"row\">"), xml);
        // Never written by an insert or an update.
        assertFalse(xml.matches("(?s).*INSERT INTO product \\([^)]*deleted_at.*"), xml);
        assertFalse(xml.matches("(?s).*deleted_at = #\\{deletedAt.*"), xml);
        String repository = render(e, "mybatis-springboot", "mybatisRepository");
        assertTrue(repository.contains("Optional<Product> findAnyById(@Param(\"id\") Long id);"), repository);

        String plain = render(product().config("mode", "mybatis"), "mybatis-springboot", "mybatisXml");
        assertTrue(plain.contains("DELETE FROM product WHERE product_id"), plain);
        assertFalse(plain.contains("findAnyById") || plain.contains("IS NULL"), plain);
    }

    @Test
    public void searchDtoLeavesTheDeletedFieldOut() {
        String srch = render(product("deletedAt", "timestamp @deleted"), "jpa-springboot-v3", "srchdto");
        assertFalse(srch.contains("deletedAt"), srch);
        String plain = render(product("closedAt", "timestamp"), "jpa-springboot-v3", "srchdto");
        assertTrue(plain.contains("closedAt"), plain);
    }
}
