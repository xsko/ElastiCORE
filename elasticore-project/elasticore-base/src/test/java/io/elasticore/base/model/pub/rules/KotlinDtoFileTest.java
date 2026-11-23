package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The DTO artifact rendered in Kotlin, through the same field rules the Java
 * modes use.
 *
 * What these hold down is the split: the decisions a field rule makes arrive
 * unchanged, and everything the language owns - properties, supertypes, the
 * absence of Lombok - is Kotlin's.
 */
public class KotlinDtoFileTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String line) {
            return super.parseFieldLine(name, line, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    private EntitySpec order() {
        return new EntitySpec("Order")
                .domain("sales", "Sales")
                .pkg("dto", "io.elasticore.blueprint.domain.sales.dto")
                .addField(loader.parse("orderId", "long @id"))
                .addField(loader.parse("note", "string(200)"));
    }

    private String render(EntitySpec e) {
        return RuleCatalog.standard()
                .engineFor("jpa-springboot-kt")
                .templateSet(SourceCode.TemplateSet.KOTLIN)
                .run(e, "dto")
                .render();
    }

    @Test
    public void packageAndImports_carryNoSemicolon() {
        String out = render(order());

        assertTrue(out.contains("package io.elasticore.blueprint.domain.sales.dto\n"), out);
        assertFalse(out.contains("import java.io.Serializable;"), out);
    }

    /**
     * Declared open: the DSL's @extend names one DTO as another's parent, and
     * a Kotlin class is final unless it says otherwise.
     */
    @Test
    public void classIsOpenSoExtendCanNameIt() {
        assertTrue(render(order()).contains("open class OrderDTO : java.io.Serializable {"),
                render(order()));
    }

    @Test
    public void fields_becomeNullableProperties() {
        String out = render(order());

        assertTrue(out.contains("var note: String? = null"), out);
        assertFalse(out.contains("private String note;"), out);
    }

    /**
     * Lombok is a Java annotation processor: it would write accessors that
     * Kotlin already has, and its annotations would do nothing here.
     */
    @Test
    public void lombokIsNotEmitted() {
        String out = render(order());

        assertFalse(out.contains("lombok"), out);
        assertFalse(out.contains("@Getter"), out);
        assertFalse(out.contains("@SuperBuilder"), out);
    }

    /** A decision a shared field rule made still arrives. */
    @Test
    public void sharedRuleDecisions_arrive() {
        assertTrue(render(order()).contains("JsonInclude"), render(order()));
    }

    private EntitySpec orderSrch() {
        return new EntitySpec("Order")
                .domain("sales", "Sales")
                .meta("searchable", "entity=Order, pageSize=30")
                .searchPageSize(30)
                .pkg("dto", "io.elasticore.blueprint.domain.sales.dto")
                .addField(loader.parse("note", "string(200) @search(like)"));
    }

    private String renderSearch(EntitySpec e) {
        return RuleCatalog.standard()
                .engineFor("jpa-springboot-kt")
                .templateSet(SourceCode.TemplateSet.KOTLIN)
                .run(e, "srchdto")
                .render();
    }

    /**
     * The sort properties every search DTO ends with, declared through the
     * language seam rather than written as text.
     */
    @Test
    public void searchDto_endsWithKotlinSortProperties() {
        String out = renderSearch(orderSrch().searchPageSize(null));

        assertTrue(out.contains("var sortCode: String? = null"), out);
        assertTrue(out.contains("var sortColumn: String? = null"), out);
        assertFalse(out.contains("private String sortCode;"), out);
    }

    /** A paged search states its defaults in the declaration, not in Lombok. */
    @Test
    public void pagedSearchDto_statesDefaultsInTheDeclaration() {
        String out = renderSearch(orderSrch());

        assertTrue(out.contains("var pageNumber: Int = 0"), out);
        assertTrue(out.contains("var pageSize: Int = 30"), out);
        assertFalse(out.contains("@Builder.Default"), out);
    }

    /** What the sort may name - the response's values - is listed, and SortableObject refuses any other. */
    @Test
    public void searchDto_listsWhatItsSortMayName() {
        String out = renderSearch(orderSrch().sortableProperties(java.util.Arrays.asList("orderId", "customer.name")));
        assertTrue(
                out.contains("override fun sortableProperties(): Set<String> = setOf(\n"
                        + "            \"orderId\",\n"
                        + "            \"customer.name\")"),
                out);
        // Not worked out (the run did not): nothing listed, any property sorts as before.
        assertFalse(renderSearch(orderSrch()).contains("sortableProperties"));
        // The project's own sort interface may not declare it: nothing is overridden (the run says so).
        String own = renderSearch(orderSrch()
                .sortableInterface("com.acme.Sorting")
                .sortableProperties(java.util.Arrays.asList("orderId")));
        assertTrue(own.contains("Sorting"), own);
        assertFalse(own.contains("sortableProperties"), own);
    }

    /** The interfaces follow the class name, as Kotlin writes them. */
    @Test
    public void searchDto_namesItsInterfacesAfterTheClass() {
        String out = renderSearch(orderSrch().searchPageSize(null));

        assertTrue(out.contains("open class OrderSrchDTO : java.io.Serializable, SortableObject {"), out);
    }
}
