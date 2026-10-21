package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.model.pub.rules.base.SearchDtoBaseRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The search DTO, whose properties come from the @search conditions of the
 * entity. Expected text is taken from the committed shop ProductSrchDTO.
 */
public class SearchDtoTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    private String render(EntitySpec e) {
        return new RuleEngine().register(new SearchDtoBaseRules())
                .run(e, "srchdto").renderFieldBlock(1);
    }

    private EntitySpec srch(String name) {
        return new EntitySpec(name).meta("searchable", "entity=" + name + ", pageSize=20");
    }

    /** A search DTO of an exposed entity, which derives a condition per type. */
    private EntitySpec exposed(String name) {
        return srch(name).meta("expose", "20");
    }

    // ---- one property per condition ---------------------------------------

    /** ProductSrchDTO.name, from "name: string(200)! @search(like)". */
    @Test
    public void likeCondition_documentsPatternMatching() {
        EntitySpec e = srch("Product").addField(loader.parse("name", "string(200)! @search(like)"));

        String out = render(e);
        assertTrue(out.contains("@Schema(description = \"Field matches pattern. field:name\"  )"), out);
        assertTrue(out.contains("private String name;"), out);
    }

    /** The clean output's like ignores case, and its search DTO says so; %_% still matches the pattern. */
    @Test
    public void likeCondition_cleanOutputIgnoresCase() {
        String out = renderClean(srch("Product").addField(loader.parse("name", "string(200)! @search(like)")));
        assertTrue(out.contains("@Schema(description = \"Field matches value (case-insensitive) with identical content"
                + " between start and end. field:name\"  )"), out);
        String keeps = renderClean(srch("Product").addField(loader.parse("name", "string(200)! @search(%_%)")));
        assertTrue(!keeps.contains("case-insensitive"), keeps);
    }

    /** ProductSrchDTO.sku, from "sku: string(50)! @search(eq)". */
    @Test
    public void equalityCondition_documentsExactMatching() {
        EntitySpec e = srch("Product").addField(loader.parse("sku", "string(50)! @search(eq)"));

        assertTrue(render(e).contains("@Schema(description = \"Field equals value. field:sku\"  )"),
                render(e));
    }

    /** ProductSrchDTO.stockQuantity, from "@search(>=)". */
    @Test
    public void comparisonCondition_documentsItsDirection() {
        EntitySpec e = srch("Product")
                .addField(loader.parse("stockQuantity", "int @default(0) @search(>=)"));

        String out = render(e);
        assertTrue(out.contains(
                "@Schema(description = \"Field is greater than or equal to value. field:stockQuantity\"  )"), out);
        assertTrue(out.contains("private Integer stockQuantity;"), out);
    }

    /**
     * A search property is never required, even when the entity's field is:
     * an absent value means "any".
     */
    @Test
    public void requiredField_producesAnOptionalProperty() {
        EntitySpec e = srch("Product").addField(loader.parse("name", "string(200)! @search(like)"));

        assertFalse(render(e).contains("requiredMode"), render(e));
    }

    // ---- ranges -----------------------------------------------------------

    /**
     * ProductSrchDTO.price, from "@search(between)", is a pair:
     *   @Schema(description = "Field is between two values (inclusive). field:price"  )
     *   private java.math.BigDecimal priceFrom;
     *
     *   private java.math.BigDecimal priceTo;
     */
    @Test
    public void betweenCondition_producesBothEnds() {
        EntitySpec e = srch("Product")
                .addField(loader.parse("price", "bigdecimal(12.2)! @min(0) @search(between)"));

        String out = render(e);
        assertTrue(out.contains(
                "@Schema(description = \"Field is between two values (inclusive). field:price\"  )"), out);
        assertTrue(out.contains("private java.math.BigDecimal priceFrom;"), out);
        assertTrue(out.contains("private java.math.BigDecimal priceTo;"), out);
    }

    /** A temporal range carries the format annotations on both ends. */
    @Test
    public void temporalRange_formatsBothEnds() {
        EntitySpec e = srch("Product")
                .addField(loader.parse("launchDate", "date @search(between)"));

        String out = render(e);
        assertTrue(out.contains("private java.time.LocalDate launchDateFrom;"), out);
        assertTrue(out.contains("private java.time.LocalDate launchDateTo;"), out);

        int patterns = out.split("DateTimeFormat\\(pattern = \"yyyy-MM-dd\"\\)", -1).length - 1;
        assertTrue(patterns == 2, "both ends should be formatted, found " + patterns + "\n" + out);
    }

    // ---- lists -------------------------------------------------------------

    /** An equality on a list searches for one element of it. */
    @Test
    public void equalityOnAList_searchesForOneElement() {
        EntitySpec e = srch("Order")
                .addField(loader.parse("statuses", "List<String> @search(eq)"));

        assertTrue(render(e).contains("private String statusesItem;"), render(e));
    }

    // ---- aliases -----------------------------------------------------------

    /** A second value in the condition names the property the caller sends. */
    @Test
    public void conditionAlias_namesTheProperty() {
        EntitySpec e = srch("Order")
                .addField(loader.parse("customerFirstName", "string @search(like,customerName)"));

        assertTrue(render(e).contains("private String customerName;"), render(e));
    }

    // ---- the @searchable default ------------------------------------------

    /**
     * An @expose search DTO derives a condition per type: text by pattern,
     * temporal by range, everything else by equality.
     */
    @Test
    public void exposedDto_derivesAConditionPerType() {
        EntitySpec e = exposed("Product")
                .addField(loader.parse("uuid", "string(36)"))
                .addField(loader.parse("lastRestocked", "datetime"))
                .addField(loader.parse("weight", "float"));

        String out = render(e);
        assertTrue(out.contains("private String uuid;"), out);
        assertTrue(out.contains("private java.time.LocalDateTime lastRestockedFrom;"), out);
        assertTrue(out.contains("private java.time.LocalDateTime lastRestockedTo;"), out);
        assertTrue(out.contains("private Float weight;"), out);
    }

    /**
     * ProductSrchDTO.createdBy shows the wording a derived text condition
     * carries: it decides between like and equal from the value it is given.
     */
    @Test
    public void derivedTextCondition_documentsItsOwnChoice() {
        EntitySpec e = exposed("Product").addField(loader.parse("createdBy", "string(50)"));

        assertTrue(render(e).contains(
                "@Schema(description = \"Use 'like' if value has %, else 'equal' field:createdBy\"  )"),
                render(e));
    }

    // ---- emptiness: @search(isnull) ----------------------------------------

    /** The clean (3.0) output, the only one that knows the isnull keyword. */
    private String renderClean(EntitySpec e) {
        return new RuleEngine().templateSet(SourceCode.TemplateSet.V3)
                .register(new SearchDtoBaseRules())
                .run(e, "srchdto").renderFieldBlock(1);
    }

    /**
     * An emptiness check is a yes or no, not a value of the field's type:
     * absent searches by nothing, true finds the empty rows, false the set
     * ones. No date format and no example - there is no date to send.
     */
    @Test
    public void isnullCondition_producesTriStateBoolean() {
        EntitySpec e = srch("Loan").addField(loader.parse("returnedDate",
                "date @search(isnull) -- returned on, empty until returned"));

        String out = renderClean(e);
        assertTrue(out.contains(
                "@Schema(description = \"returned on, empty until returned Field is empty (true) or set (false). field:returnedDate\"  )\n"
                + "private Boolean returnedDateEmpty;\n"), out);
        assertFalse(out.contains("returnedDateFrom"), out);
        assertFalse(out.contains("returnedDateTo"), out);
        assertFalse(out.contains("LocalDate"), out);
        assertFalse(out.contains("DateTimeFormat"), out);
        assertFalse(out.contains("JsonFormat"), out);
        assertFalse(out.contains("example"), out);
    }

    /** The alias names the property, as it does for every other condition. */
    @Test
    public void isnullCondition_aliasNamesTheProperty() {
        EntitySpec e = srch("Loan").addField(loader.parse("returnedDate", "date @search(isnull,open)"));

        String out = renderClean(e);
        assertTrue(out.contains("private Boolean openEmpty;"), out);
        assertTrue(out.contains("field:returnedDate"), out);
        assertFalse(out.contains("returnedDateEmpty"), out);
    }

    /**
     * The 2.x-compatible output does not know the keyword and keeps reading
     * it as an equality on the field's own type, word for word as before.
     */
    @Test
    public void isnullCondition_compatibleOutputUnchanged() {
        EntitySpec e = srch("Loan").addField(loader.parse("returnedDate", "date @search(isnull)"));

        String out = render(e);
        assertTrue(out.contains(
                "@Schema(description = \"Use 'like' if value has %, else 'equal' field:returnedDate\"  , example=\"yyyy-MM-dd\")\n"
                + "@org.springframework.format.annotation.DateTimeFormat(pattern = \"yyyy-MM-dd\")\n"
                + "@com.fasterxml.jackson.annotation.JsonFormat(pattern = \"yyyy-MM-dd\")\n"
                + "private java.time.LocalDate returnedDate;\n"), out);
        assertFalse(out.contains("Empty"), out);
    }

    /**
     * The keyword changes nothing about what an @expose DTO derives: a date
     * without @search is still a range, and only the field that asks for the
     * emptiness check gets one.
     */
    @Test
    public void exposedDto_dateStillDerivesBetween() {
        EntitySpec e = exposed("Loan")
                .addField(loader.parse("loanDate", "date"))
                .addField(loader.parse("returnedDate", "date @search(isnull)"));

        String out = renderClean(e);
        assertTrue(out.contains(
                "@Schema(description = \"Field is between two values (inclusive). field:loanDate\"  , example=\"yyyy-MM-dd\")"), out);
        assertTrue(out.contains("LocalDate loanDateFrom;"), out);
        assertTrue(out.contains("LocalDate loanDateTo;"), out);
        assertTrue(out.contains(
                "@Schema(description = \"Field is empty (true) or set (false). field:returnedDate\"  )\n"
                + "private Boolean returnedDateEmpty;\n"), out);
        assertFalse(out.contains("returnedDateFrom"), out);
    }

    /** Without @expose only the annotated fields become properties. */
    @Test
    public void plainDto_keepsOnlyTheAnnotatedFields() {
        EntitySpec e = new EntitySpec("Product")
                .addField(loader.parse("name", "string(200) @search(like)"))
                .addField(loader.parse("uuid", "string(36)"));

        String out = render(e);
        assertTrue(out.contains("private String name;"), out);
        assertFalse(out.contains("uuid"), out);
    }
}
