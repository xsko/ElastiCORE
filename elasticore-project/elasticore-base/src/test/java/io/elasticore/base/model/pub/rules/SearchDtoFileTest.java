package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.model.pub.rules.base.SearchDtoBaseRules;
import io.elasticore.base.model.pub.rules.lang.java.SearchDtoFileRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The search DTO class around its properties, compared against the committed
 * ProductSrchDTO of the shop domain.
 */
public class SearchDtoFileTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    private EntitySpec productSrch() {
        String base = "io.elasticore.blueprint.domain.shop";
        return new EntitySpec("Product")
                .meta("searchable", "entity=Product, pageSize=20")
                .searchPageSize(20)
                .pkg("dto", base + ".dto")
                .addField(loader.parse("name", "string(200)! @search(like)"));
    }

    private String render(EntitySpec e) {
        return new RuleEngine()
                .register(new SearchDtoBaseRules())
                .register(new SearchDtoFileRules())
                .run(e, "srchdto").render();
    }

    /**
     * The declaration, exactly as committed:
     *   public  class ProductSrchDTO  implements java.io.Serializable,
     *       SortableObject, PageableObject  {
     */
    @Test
    public void declaration_namesBothCapabilities() {
        assertTrue(render(productSrch()).contains(
                "public  class ProductSrchDTO  implements java.io.Serializable,"
                        + " SortableObject, PageableObject  {"), render(productSrch()));
    }

    /** @sort without paging: @Builder.Default on the sort field still needs lombok.Builder. */
    @Test
    public void aDefaultOrderWithoutPagingImportsWhatItUses() {
        EntitySpec e = productSrch().searchPageSize(null).defaultSort("name+");
        String out = new RuleEngine()
                .register(new SearchDtoBaseRules())
                .register(new io.elasticore.base.model.pub.rules.lang.java.SearchDtoCleanFileRules())
                .run(e, "srchdto").render();
        assertTrue(out.contains("private String sortCode=\"name+\";"), out);
        assertTrue(out.contains("import lombok.Builder;"), out);
    }

    /** A search that does not page declares only the sort capability. */
    @Test
    public void searchWithoutPaging_declaresOnlySorting() {
        EntitySpec e = productSrch().searchPageSize(null);

        String out = render(e);
        assertTrue(out.contains("implements java.io.Serializable, SortableObject  {"), out);
        assertFalse(out.contains("PageableObject"), out);
        assertFalse(out.contains("pageNumber"), out);
    }

    /**
     * The tail of the class, exactly as committed:
     *   private String sortCode;
     *
     *   @Builder.Default
     *   private int pageNumber=0;
     *
     *   @Builder.Default
     *   private int pageSize=20;
     */
    @Test
    public void tail_carriesTheSortAndPagingProperties() {
        String out = render(productSrch());

        assertTrue(out.contains("\tprivate String sortCode;"), out);
        assertTrue(out.contains("\t@Builder.Default\n\tprivate int pageNumber=0;"), out);
        assertTrue(out.contains("\t@Builder.Default\n\tprivate int pageSize=20;"), out);
    }

    /** The template's own sort column and direction follow, as committed. */
    @Test
    public void templateSortFields_followTheGeneratedOnes() {
        String out = render(productSrch());

        assertTrue(out.contains("    private String sortColumn; // Column to sort by"), out);
        assertTrue(out.contains(
                "    private Boolean sortAscending; // Sort order (true: ascending, false: descending)"),
                out);
    }

    /** The search properties come first, above the sort code. */
    @Test
    public void searchProperties_comeFirst() {
        String out = render(productSrch());

        assertTrue(out.indexOf("private String name;") < out.indexOf("private String sortCode;"), out);
    }

    /** A project that named its own interfaces gets those names. */
    @Test
    public void customInterfaces_areNamedInstead() {
        EntitySpec e = productSrch()
                .sortableInterface("kr.co.acme.Sorted")
                .pageableInterface("kr.co.acme.Paged");

        assertTrue(render(e).contains("implements java.io.Serializable, Sorted, Paged  {"), render(e));
    }

    private String renderClean(EntitySpec e) {
        return new RuleEngine()
                .register(new SearchDtoBaseRules())
                .register(new io.elasticore.base.model.pub.rules.lang.java.SearchDtoCleanFileRules())
                .run(e, "srchdto")
                .render();
    }

    /**
     * The 3.0 search DTO lists what its sort may name - what the search's response shows - and SortableObject
     * refuses any other property (400) before the query is built.
     */
    @Test
    public void theCleanSearchDtoListsWhatItsSortMayName() {
        String out =
                renderClean(productSrch().sortableProperties(java.util.Arrays.asList("productId", "category.name")));
        assertTrue(
                out.contains(
                        "\tprivate static final java.util.Set<String> SORTABLE_PROPERTIES = java.util.Collections.unmodifiableSet(\n"
                                + "\t\t\tnew java.util.LinkedHashSet<String>(java.util.Arrays.asList(\n"
                                + "\t\t\t\t\t\"productId\",\n"
                                + "\t\t\t\t\t\"category.name\")));"),
                out);
        assertTrue(
                out.contains(
                        "\t@Override\n\tpublic java.util.Set<String> sortableProperties() {\n\t\treturn SORTABLE_PROPERTIES;\n\t}"),
                out);
        // Not worked out (the 2.x-compatible run, a search DTO of no entity): nothing listed, as before.
        assertFalse(renderClean(productSrch()).contains("sortableProperties"));
        // The 2.x-compatible file rule never lists it.
        assertFalse(render(productSrch().sortableProperties(java.util.Arrays.asList("productId")))
                .contains("sortableProperties"));
        // The project's own sort interface may not declare it: nothing is overridden (the run says so).
        String own = renderClean(productSrch()
                .sortableInterface("kr.co.acme.Sorted")
                .sortableProperties(java.util.Arrays.asList("productId")));
        assertTrue(own.contains("Sorted"), own);
        assertFalse(own.contains("sortableProperties"), own);
    }
}
