package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The TypeScript client gives a screen the label in each language the DSL
 * writes: {@code <Base>Fields.unitCost.labels.en}. Without a translation the
 * entry is what it was.
 */
public class LabelTranslationTypeScriptTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    private String typescript(String... nameAndLine) {
        EntitySpec e = new EntitySpec("Item")
                .domain("shop", "Shop")
                .pkg("entity", "com.acme.shop.entity")
                .pkg("dto", "com.acme.shop.dto");
        for (int i = 0; i < nameAndLine.length; i += 2) e.addField(loader.parse(nameAndLine[i], nameAndLine[i + 1]));
        return RuleCatalog.standard()
                .engineFor("ts-model")
                .templateSet(SourceCode.TemplateSet.TYPESCRIPT)
                .run(e, "dto")
                .render();
    }

    @Test
    public void eachLanguageIsALabelOfTheField() {
        String ts = typescript("unitCost", "int! @label(en=Unit cost, ja=単価, zh-TW=單價) -- 단가");
        assertTrue(
                ts.contains("unitCost: { label: \"단가\", labels: { en: \"Unit cost\", ja: \"単価\", \"zh-TW\": \"單價\" }"),
                ts);
    }

    @Test
    public void aQuoteInATranslationIsEscaped() {
        String ts = typescript("name", "string(30) @label(en='The \"name\"') -- 이름");
        assertTrue(ts.contains("labels: { en: \"The \\\"name\\\"\" }"), ts);
    }

    @Test
    public void withoutATranslationTheEntryIsUnchanged() {
        String ts = typescript("unitCost", "int! -- 단가");
        assertTrue(ts.contains("unitCost: { label: \"단가\", required: true }"), ts);
        assertFalse(ts.contains("labels:"), ts);
    }
}
