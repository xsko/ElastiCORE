package io.elasticore.base.model.loader;

import io.elasticore.base.model.core.Annotation;
import io.elasticore.base.model.core.LabelTranslations;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue 345: the "--" text is the label as written, whatever it holds. Before,
 * the loader wrote it into the line as {@code @label(<text>)} and parsed that
 * as annotation parameters, so a label with '=' became key=value pairs and the
 * label itself a fragment ("day)") - in the entity, schema.yml, the DDL comment
 * and the migration, without a warning.
 */
public class DashLabelAsWrittenTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String line) {
            return super.parseFieldLine(name, line, null);
        }

        Map<String, Annotation> meta(String line) {
            return super.getAnnotationObj(line);
        }
    }

    private final TestLoader loader = new TestLoader();

    private static final String MONTHS =
            "shortest policy period in calendar months (1 = to the same day of the next month, or that month's last day)";

    @Test
    public void aLabelWithEqualsAndCommasIsTheWholeText() {
        Field f = loader.parse("minTermMonths", "int @min(1) @column(MIN_TERM_MCNT) -- " + MONTHS);
        assertEquals(MONTHS, f.getAnnotationValue("label"));
        assertEquals(MONTHS, f.getDescription());
        Annotation label = f.getAnnotation("label");
        assertTrue(label.getProperties() == null || label.getProperties().isEmpty(), "no made-up keys");
        assertTrue(LabelTranslations.of(label).isEmpty());
        assertTrue(f.hasAnnotation("min") && f.hasAnnotation("column"), "the annotations before it are read as before");
    }

    @Test
    public void aFormulaIsALabel() {
        assertEquals(
                "sum = price * qty",
                loader.parse("total", "long -- sum = price * qty").getAnnotationValue("label"));
        assertEquals(
                "in=입고, out=출고",
                loader.parse("ioType", "string(3) -- in=입고, out=출고").getAnnotationValue("label"));
        assertEquals("비율 a=b", loader.parse("ratio", "int -- 비율 a=b").getAnnotationValue("label"));
    }

    @Test
    public void parenthesesAreLetters() {
        assertEquals(
                "1) first step",
                loader.parse("step", "string(10) -- 1) first step").getAnnotationValue("label"));
        assertEquals(
                "f(g(h(x))) applied",
                loader.parse("value", "string(10) -- f(g(h(x))) applied").getAnnotationValue("label"));
        assertEquals("open (", loader.parse("value", "string(10) -- open (").getAnnotationValue("label"));
    }

    @Test
    public void theDescriptionAfterTheBarIsAsWrittenToo() {
        Field f = loader.parse("ioType", "string(3)! -- in/out | in=입고, out=출고 (a = b)");
        assertEquals("in/out", f.getAnnotationValue("label"));
        assertEquals("in=입고, out=출고 (a = b)", f.getAnnotationValue("desc"));
        assertTrue(f.hasAnnotation("notnull"));
    }

    @Test
    public void aMetaLineKeepsItsLabel() {
        Map<String, Annotation> meta = loader.meta("entity @root @expose(20) -- rate entry (qty=1, per order)");
        assertEquals("rate entry (qty=1, per order)", meta.get("label").getValue());
        assertTrue(meta.containsKey("root") && meta.containsKey("expose"));
        assertEquals("entity", meta.get("type").getValue());
    }

    @Test
    public void aLineThatStartsWithTheCommentKeepsItsLabel() {
        Map<String, Annotation> meta = loader.meta("-- a = b, c = d");
        assertEquals("a = b, c = d", meta.get("label").getValue());
    }

    @Test
    public void anExplicitLabelKeepsItsReadingBesideIt() {
        // The written @label is still read as translations; the "--" text is the label, as written.
        Field f = loader.parse("unitCost", "money(10.2) @label(en=Unit cost) -- price = net + VAT");
        assertEquals("price = net + VAT", f.getAnnotationValue("label"));
        assertEquals("Unit cost", f.getAnnotationValue("label.en"));
        // And one that is no translation keeps its 2.x key=value reading.
        Field old = loader.parse("unitCost", "money(10.2) @label(en=Unit cost, note=price) -- a = b");
        assertEquals("Unit", old.getAnnotationValue("label.en"));
    }
}
