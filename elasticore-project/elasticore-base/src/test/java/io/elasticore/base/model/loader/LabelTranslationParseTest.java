package io.elasticore.base.model.loader;

import io.elasticore.base.model.core.Annotation;
import io.elasticore.base.model.core.LabelTranslations;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A label in other languages: {@code @label(en="Unit cost", ja=単価) -- 단가}.
 * The "--" text stays the label; each language=text pair is a translation.
 * Before, the explicit @label won and, having only key=value pairs, had no
 * value - the "--" label was lost from every output.
 */
public class LabelTranslationParseTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String line) {
            return super.parseFieldLine(name, line, null);
        }

        Map<String, Annotation> meta(String line) {
            return super.getAnnotationObj(line);
        }

        Annotation params(String name, String parameters) {
            return super.parseParameters(name, parameters);
        }
    }

    private final TestLoader loader = new TestLoader();

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    public void theDashLabelStaysTheLabelAndThePairsAreItsTranslations() {
        Field f = loader.parse("unitCost", "money(10.2)! @search(eq) @label(en=\"Unit cost\", ja=単価) -- 단가");
        assertEquals("단가", f.getAnnotationValue("label"));
        assertEquals("단가", f.getDescription());
        assertEquals(map("en", "Unit cost", "ja", "単価"), LabelTranslations.of(f.getAnnotation("label")));
        assertEquals("Unit cost", f.getAnnotationValue("label.en"));
        assertTrue(f.hasAnnotation("search"), "the other marks of the line are read as before");
    }

    @Test
    public void anUnquotedValueRunsToTheNextCommaSpacesIncluded() {
        Field f = loader.parse("unitCost", "money(10.2) @label(en=Unit cost, de=Preis pro Stueck) -- 단가");
        assertEquals(map("de", "Preis pro Stueck", "en", "Unit cost"), LabelTranslations.of(f.getAnnotation("label")));
    }

    @Test
    public void aQuotedValueKeepsItsComma() {
        Field f = loader.parse("unitCost", "money(10.2) @label(en=\"Cost, per unit\", fr='Prix, unitaire') -- 단가");
        assertEquals(
                map("en", "Cost, per unit", "fr", "Prix, unitaire"), LabelTranslations.of(f.getAnnotation("label")));
    }

    @Test
    public void regionAndScriptTagsAreLanguages() {
        Field f = loader.parse("name", "string(30) @label(zh-TW=名稱, pt_BR=Nome, zh-Hans=名称) -- 이름");
        assertEquals(
                map("pt-BR", "Nome", "zh-Hans", "名称", "zh-TW", "名稱"), LabelTranslations.of(f.getAnnotation("label")));
        assertEquals("이름", f.getAnnotationValue("label"));
    }

    @Test
    public void theLabelMayBeWrittenInsideWithoutADashComment() {
        Field f = loader.parse("unitCost", "money(10.2) @label(단가, en=Unit cost)");
        assertEquals("단가", f.getAnnotationValue("label"));
        assertEquals(map("en", "Unit cost"), LabelTranslations.of(f.getAnnotation("label")));
    }

    @Test
    public void onlyTranslationsAndNoDashLabelLeaveTheLabelUnwritten() {
        Field f = loader.parse("unitCost", "money(10.2) @label(en=Unit cost)");
        assertNull(f.getAnnotationValue("label"));
        assertEquals(map("en", "Unit cost"), LabelTranslations.of(f.getAnnotation("label")));
    }

    @Test
    public void anExplicitLabelWithItsOwnTextStillWinsOverTheDash() {
        // The first @label on the line that gives a label is the label.
        Field f = loader.parse("unitCost", "money(10.2) @label(단가, en=Unit cost) -- 다른 이름");
        assertEquals("단가", f.getAnnotationValue("label"));
    }

    @Test
    public void theDescriptionAfterTheBarIsKept() {
        Field f = loader.parse("unitCost", "money(10.2) @label(en=Unit cost) -- 단가 | VAT 포함 판매가");
        assertEquals("단가", f.getAnnotationValue("label"));
        assertEquals("VAT 포함 판매가", f.getAnnotationValue("desc"));
        assertEquals(map("en", "Unit cost"), LabelTranslations.of(f.getAnnotation("label")));
    }

    @Test
    public void aMetaLineTakesTranslationsToo() {
        Map<String, Annotation> meta = loader.meta("entity @root @label(en=Pet for sale, ja=販売単位) -- 판매 단위");
        assertEquals("판매 단위", meta.get("label").getValue());
        assertEquals(map("en", "Pet for sale", "ja", "販売単位"), LabelTranslations.of(meta.get("label")));
        assertTrue(meta.containsKey("root"));
    }

    // ---- what is not a translation is read as it always was ---------------------------

    @Test
    public void aPlainDashLabelIsUnchanged() {
        Field f = loader.parse("unitCost", "money(10.2) -- 단가, 원 단위");
        assertEquals("단가, 원 단위", f.getAnnotationValue("label"));
        assertTrue(LabelTranslations.of(f.getAnnotation("label")).isEmpty());
    }

    @Test
    public void aKeyThatIsNotALanguageKeepsTheOldReading() {
        // Not a language tag, so not a translation: the 2.x key=value reading.
        Annotation a = loader.params("label", "Name=Cost, x=1");
        assertEquals("Cost", a.getProperties().getProperty("Name"));
        assertNull(a.getValue());
        assertTrue(LabelTranslations.of(a).isEmpty());

        Field f = loader.parse("ratio", "int -- 비율 a=b");
        assertTrue(LabelTranslations.of(f.getAnnotation("label")).isEmpty());
    }

    @Test
    public void otherAnnotationsAreNotReadAsTranslations() {
        Annotation a = loader.params("search", "en=Unit cost");
        assertEquals("Unit", a.getProperties().getProperty("en"));
    }

    @Test
    public void anApostropheInAnUnquotedValueIsALetter() {
        Field f = loader.parse("owner", "string(30) @label(en=Owner's name, fr=Nom de l'article, ja=所有者名) -- 소유자명");
        assertEquals(
                map("en", "Owner's name", "fr", "Nom de l'article", "ja", "所有者名"),
                LabelTranslations.of(f.getAnnotation("label")));
    }

    @Test
    public void twoExplicitLabelsAreOne() {
        Field f = loader.parse("unitCost", "money(10.2) @label(en=Unit cost) @label(ja=単価) -- 단가");
        assertEquals("단가", f.getAnnotationValue("label"));
        assertEquals(map("en", "Unit cost", "ja", "単価"), LabelTranslations.of(f.getAnnotation("label")));
    }

    @Test
    public void aTagIsOneFormHoweverItIsWritten() {
        Field f = loader.parse("unitCost", "money(10.2) @label(EN=Unit cost, en_us=Unit price, ZH-hant-tw=單價) -- 단가");
        assertEquals(
                map("en", "Unit cost", "en-US", "Unit price", "zh-Hant-TW", "單價"),
                LabelTranslations.of(f.getAnnotation("label")));
    }

    /** The @label the loader builds from "--" is never read as translations. */
    @Test
    public void aDashCommentWithEqualsIsNoTranslation() {
        Field sum = loader.parse("total", "long -- sum = price * qty");
        assertTrue(LabelTranslations.of(sum.getAnnotation("label")).isEmpty());
        Field io = loader.parse("ioType", "string(3) -- in=입고, out=출고");
        assertTrue(LabelTranslations.of(io.getAnnotation("label")).isEmpty());
        Map<String, Annotation> meta = loader.meta("entity @root -- qty=1");
        assertTrue(LabelTranslations.of(meta.get("label")).isEmpty());
    }

    @Test
    public void anExplicitLabelWithAKeyThatIsNoLanguageIsReadAsBefore() {
        // note is no language tag: no part is a translation, and the line reads as in 2.x.
        Field f = loader.parse("unitCost", "money(10.2) @label(en=Unit cost, note=price) -- 단가");
        assertTrue(LabelTranslations.of(f.getAnnotation("label")).isEmpty());
        assertEquals("Unit", f.getAnnotationValue("label.en"));
    }

    @Test
    public void anEmptyTranslationIsDropped() {
        Field f = loader.parse("unitCost", "money(10.2) @label(en=\"\", ja=単価) -- 단가");
        assertEquals(map("ja", "単価"), LabelTranslations.of(f.getAnnotation("label")));
    }
}
