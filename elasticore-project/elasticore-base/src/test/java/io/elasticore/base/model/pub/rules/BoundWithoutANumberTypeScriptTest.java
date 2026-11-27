package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bound written without a number - {@code @min(,)} - is no bound in the
 * TypeScript client, as a bound that is not a number is. Reading it took the
 * text before the first comma with split(","), which for "," is no text at all,
 * and the generation stopped with an ArrayIndexOutOfBoundsException.
 */
public class BoundWithoutANumberTypeScriptTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    private String typescript(String name, String line) {
        EntitySpec e = new EntitySpec("Item")
                .domain("shop", "Shop")
                .pkg("entity", "com.acme.shop.entity")
                .pkg("dto", "com.acme.shop.dto")
                .addField(loader.parse(name, line));
        return RuleCatalog.standard()
                .engineFor("ts-model")
                .templateSet(SourceCode.TemplateSet.TYPESCRIPT)
                .run(e, "dto")
                .render();
    }

    @Test
    public void aBoundOfOnlyACommaIsNoBound() {
        String ts = typescript("qty", "int @min(,) @max(,,)");
        assertTrue(ts.contains("qty: { label: \"qty\" },"), ts);
        assertFalse(ts.contains("Number(value.qty)"), ts);
    }

    @Test
    public void aBoundThatIsNotANumberIsNoBoundEither() {
        String ts = typescript("qty", "int @min(ten) @max(x, 'too many')");
        assertTrue(ts.contains("qty: { label: \"qty\" },"), ts);
        assertFalse(ts.contains("Number(value.qty)"), ts);
    }

    @Test
    public void aNumberIsStillABound() {
        String ts = typescript("qty", "int @min(1) @max(9, 'too many')");
        assertTrue(ts.contains("qty: { label: \"qty\", min: 1, max: 9 },"), ts);
        assertTrue(ts.contains("Number(value.qty) > 9) errors.qty = \"too many\";"), ts);
    }
}
