package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.model.pub.rules.base.DtoBaseRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule about one field is written on that field - @min(0.01, '...') - and
 * holds everywhere: in the DTO (400) and, for a root, on every write (422).
 */
public class FieldBoundTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    private EntitySpec item() {
        String base = "com.acme.erp";
        return new EntitySpec("Item")
                .domain("erp", "Erp")
                .pkg("entity", base + ".domain.model")
                .pkg("dto", base + ".application.dto")
                .addField(loader.parse("itemCode", "string(20) @id"))
                .addField(loader.parse("unitPrice", "bigdecimal(12.2)! @min(0.01, '단가는 0보다 커야 한다')"))
                .addField(loader.parse("stock", "int @min(0) @max(9999)"));
    }

    @Test
    public void theValueAndTheMessageAreRead() {
        assertArrayEquals(new String[]{"0.01", "단가는 0보다 커야 한다"},
                DtoBaseRules.Bound.split("0.01, '단가는 0보다 커야 한다'"));
        assertArrayEquals(new String[]{"0", null}, DtoBaseRules.Bound.split("0"));
        assertArrayEquals(new String[]{"1", "a, b"}, DtoBaseRules.Bound.split("1, 'a, b'"));
    }

    @Test
    public void theDtoCarriesTheMessageAndABareBoundStaysAs2xWroteIt() {
        String dto = RuleCatalog.standard().engineFor("jpa-dto", "jpa-dto-file")
                .templateSet(SourceCode.TemplateSet.V3).run(item(), "dto").render();
        assertTrue(dto.contains("@DecimalMin(value=\"0.01\", message=\"단가는 0보다 커야 한다\")")
                || dto.contains("@DecimalMin(value = \"0.01\", message = \"단가는 0보다 커야 한다\")"), dto);
        assertTrue(dto.contains("@Min(0)"), dto);
        assertTrue(dto.contains("@Max(9999)"), dto);
    }

    private EntitySpec gauge() {
        return new EntitySpec("Gauge")
                .domain("erp", "Erp")
                .pkg("entity", "com.acme.erp.domain.model")
                .pkg("dto", "com.acme.erp.application.dto")
                .addField(loader.parse("gaugeId", "long @id"))
                .addField(loader.parse("rating", "double @min(0.5) @max(5.0)"))
                .addField(loader.parse("temperature", "float @min(-273.15, 'below absolute zero')"))
                .addField(loader.parse("weight", "Double @max(1.5)"))
                .addField(loader.parse("load", "Float @min(0.25)"))
                .addField(loader.parse("whole", "double @min(0) @max(-10)"));
    }

    /**
     * @Min/@Max take a long: a fraction on a double or a float did not compile.
     * It is a DecimalMin/DecimalMax, which Hibernate Validator checks on a
     * Number; a whole bound stays as it was.
     */
    @Test
    public void aFractionOnADoubleOrAFloatIsADecimalBound() {
        String dto = RuleCatalog.standard()
                .engineFor("jpa-dto", "jpa-dto-file")
                .templateSet(SourceCode.TemplateSet.V3)
                .run(gauge(), "dto")
                .render();
        assertTrue(dto.contains("@DecimalMin(\"0.5\")"), dto);
        assertTrue(dto.contains("@DecimalMax(\"5.0\")"), dto);
        assertTrue(
                dto.contains("@DecimalMin(value=\"-273.15\", message=\"below absolute zero\")")
                        || dto.contains("@DecimalMin(value = \"-273.15\", message = \"below absolute zero\")"),
                dto);
        assertTrue(dto.contains("@DecimalMax(\"1.5\")"), dto);
        assertTrue(dto.contains("@DecimalMin(\"0.25\")"), dto);
        assertTrue(dto.contains("@Min(0)"), dto);
        assertTrue(dto.contains("@Max(-10)"), dto);
        assertFalse(dto.contains("@Min(0."), dto);
        assertFalse(dto.contains("@Max(5."), dto);
        assertFalse(dto.contains("@Min(-273"), dto);
    }

    /** The compatible output is what 2.x writes, fraction and all. */
    @Test
    public void theCompatibleOutputKeepsWhat2xWrites() {
        String dto = RuleCatalog.standard()
                .engineFor("jpa-dto", "jpa-dto-file")
                .templateSet(SourceCode.TemplateSet.COMPATIBLE)
                .run(gauge(), "dto")
                .render();
        assertTrue(dto.contains("Min(0.5)"), dto);
        assertFalse(dto.contains("DecimalMin(\"0.5\")"), dto);
    }

    @Test
    public void aBoundIsWholeWhenItHasNoFraction() {
        assertTrue(DtoBaseRules.isWhole("0"));
        assertTrue(DtoBaseRules.isWhole("-273"));
        assertTrue(DtoBaseRules.isWhole("9999"));
        assertFalse(DtoBaseRules.isWhole("0.0"));
        assertFalse(DtoBaseRules.isWhole("-0.5"));
        assertFalse(DtoBaseRules.isWhole("1e3"));
        assertFalse(DtoBaseRules.isWhole("x1"));
        assertFalse(DtoBaseRules.isWhole("1x"));
    }

    /** How many members a collection holds is a rule about that field too. */
    @Test
    public void aCollectionSaysHowManyItHolds() {
        EntitySpec order = new EntitySpec("SalesOrder")
                .domain("erp", "Erp")
                .pkg("entity", "com.acme.erp.domain.model")
                .pkg("dto", "com.acme.erp.application.dto")
                .addField(loader.parse("orderId", "string(36) @id"))
                .addField(loader.parse("lines",
                        "List<SalesOrderLine> @OneToMany(mappedBy=\"order\") @minsize(1, '수주에는 품목이 하나 이상 있어야 한다')"));
        String out = RuleCatalog.standard().engineFor("ddd-invariant")
                .templateSet(SourceCode.TemplateSet.V3).run(order, "invariants").render();
        assertTrue(out.contains("salesOrder.getLines() == null || salesOrder.getLines().size() <= 0"), out);
        assertTrue(out.contains("\"수주에는 품목이 하나 이상 있어야 한다\""), out);

        String dto = RuleCatalog.standard().engineFor("jpa-dto", "jpa-dto-file")
                .templateSet(SourceCode.TemplateSet.V3).run(order, "dto").render();
        assertTrue(dto.contains("@Size(min=1, message=\"수주에는 품목이 하나 이상 있어야 한다\")"), dto);
    }

    @Test
    public void aRootChecksItsFieldBoundsOnEveryWrite() {
        String out = RuleCatalog.standard().engineFor("ddd-invariant")
                .templateSet(SourceCode.TemplateSet.V3).run(item(), "invariants").render();
        assertTrue(out.contains("item.getUnitPrice().compareTo(new java.math.BigDecimal(\"0.01\")) < 0"), out);
        assertTrue(out.contains("\"단가는 0보다 커야 한다\""), out);
        assertTrue(out.contains("item.getStock() < 0"), out);
        assertTrue(out.contains("stock must be at most 9999"), out);
        assertFalse(out.contains("${"), out);
    }
}
