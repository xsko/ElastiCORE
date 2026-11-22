package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a screen needs around labels (issue 268), in the TypeScript client:
 * the type's own label ({@code XxxMeta}), the checks as the server's
 * fieldErrors codes ({@code validateXxxCodes}) and an enumeration's labels in
 * other languages ({@code XxxLabels}). Without a label or a column nothing new
 * is written but the codes function.
 */
public class ScreenLabelsTypeScriptTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    private EntitySpec item(String... nameAndLine) {
        EntitySpec e = new EntitySpec("Item")
                .domain("shop", "Shop")
                .pkg("entity", "com.acme.shop.entity")
                .pkg("dto", "com.acme.shop.dto");
        for (int i = 0; i < nameAndLine.length; i += 2) e.addField(loader.parse(nameAndLine[i], nameAndLine[i + 1]));
        return e;
    }

    private static String typescript(EntitySpec e) {
        return RuleCatalog.standard()
                .engineFor("ts-model")
                .templateSet(SourceCode.TemplateSet.TYPESCRIPT)
                .run(e, "dto")
                .render();
    }

    private static String typescript(EnumSpec e) {
        return RuleCatalog.standard()
                .engineFor("ts-model")
                .templateSet(SourceCode.TemplateSet.TYPESCRIPT)
                .run(e, "enum")
                .render();
    }

    // ---- XxxMeta ----------------------------------------------------------------

    @Test
    public void theTypesLabelIsAConstant() {
        Map<String, String> labels = new TreeMap<String, String>();
        labels.put("en", "Item \"for\" sale");
        labels.put("zh-TW", "販售單位");
        EntitySpec e = item("name", "string(30) -- 이름").labelTranslations(labels);
        e.meta("label", "판매 단위");
        String ts = typescript(e);
        assertTrue(
                ts.contains("export const ItemMeta: { label: string; labels: Record<string, string> } = "
                        + "{ label: \"판매 단위\", labels: { en: \"Item \\\"for\\\" sale\", \"zh-TW\": \"販售單位\" } };"),
                ts);
    }

    @Test
    public void aLabelWithoutTranslationsHasNone() {
        EntitySpec e = item("name", "string(30) -- 이름");
        e.meta("label", "판매 단위");
        String ts = typescript(e);
        assertTrue(ts.contains("= { label: \"판매 단위\", labels: {} };"), ts);
    }

    @Test
    public void withoutALabelThereIsNoMeta() {
        String ts = typescript(item("name", "string(30) -- 이름"));
        assertFalse(ts.contains("ItemMeta"), ts);
    }

    // ---- validateXxxCodes ---------------------------------------------------------

    @Test
    public void validateStaysAsItWasAndTheCodesFollowIt() {
        String ts = typescript(item("name", "string(30)! -- 이름"));
        assertTrue(
                ts.contains(
                        "export function validateItem(value: Partial<ItemDTO> | null | undefined, partial = false): Record<string, string> {\n"
                                + "    const errors: Record<string, string> = {};\n"
                                + "    if (value == null) return errors;\n"
                                + "    if (partial ? value.name === '' : (value.name == null || value.name === '')) errors.name = \"이름 is required\";\n"
                                + "    else if (value.name != null && String(value.name).length > 30) errors.name = \"이름 is at most 30 characters\";\n"
                                + "    return errors;\n"
                                + "}\n"),
                ts);
        assertTrue(
                ts.contains(
                        "export function validateItemCodes(value: Partial<ItemDTO> | null | undefined, partial = false): "
                                + "Array<{ field: string; code: string; values: Record<string, number | string> }> {\n"
                                + "    const errors: Array<{ field: string; code: string; values: Record<string, number | string> }> = [];\n"
                                + "    if (value == null) return errors;\n"
                                + "    if (partial ? value.name === '' : (value.name == null || value.name === '')) errors.push({ field: \"name\", code: \"NotNull\", values: {} });\n"
                                + "    else if (value.name != null && String(value.name).length > 30) errors.push({ field: \"name\", code: \"Size\", values: { max: 30 } });\n"
                                + "    return errors;\n"
                                + "}"),
                ts);
    }

    @Test
    public void aRequiredNumberIsNotNull() {
        String ts = typescript(item("qty", "int!"));
        assertTrue(
                ts.contains(
                        "if (!partial && value.qty == null) errors.push({ field: \"qty\", code: \"NotNull\", values: {} });"),
                ts);
    }

    @Test
    public void bothLengthsAreTheValuesOfSize() {
        String ts = typescript(item("code", "string(10) @length(min=2)"));
        assertTrue(
                ts.contains(
                        "String(value.code).length > 10) errors.push({ field: \"code\", code: \"Size\", values: { min: 2, max: 10 } });"),
                ts);
        assertTrue(
                ts.contains(
                        "String(value.code).length < 2) errors.push({ field: \"code\", code: \"Size\", values: { min: 2, max: 10 } });"),
                ts);
    }

    @Test
    public void aBoundIsMinOrMaxWithItsValue() {
        String ts = typescript(item("qty", "int @min(1) @max(99)"));
        assertTrue(
                ts.contains(
                        "if (value.qty != null && Number(value.qty) < 1) errors.push({ field: \"qty\", code: \"Min\", values: { value: 1 } });"),
                ts);
        assertTrue(
                ts.contains(
                        "else if (value.qty != null && Number(value.qty) > 99) errors.push({ field: \"qty\", code: \"Max\", values: { value: 99 } });"),
                ts);
    }

    @Test
    public void aDecimalBoundIsDecimalMinAsTheServerSaysIt() {
        String ts = typescript(item("price", "decimal(10.2) @min(0.01)", "rate", "double @max(0.5)"));
        assertTrue(
                ts.contains("errors.push({ field: \"price\", code: \"DecimalMin\", values: { value: 0.01 } });"), ts);
        assertTrue(ts.contains("errors.push({ field: \"rate\", code: \"DecimalMax\", values: { value: 0.5 } });"), ts);
    }

    @Test
    public void aPatternGivesItsRegexp() {
        String ts = typescript(item("isbn", "string(13) @pattern('^[0-9]{13}$', 'ISBN must be 13 digits')"));
        assertTrue(
                ts.contains(
                        "errors.push({ field: \"isbn\", code: \"Pattern\", values: { regexp: \"^[0-9]{13}$\" } });"),
                ts);
    }

    @Test
    public void anEmailIsEmail() {
        String ts = typescript(item("mail", "string(100) @email"));
        assertTrue(ts.contains("errors.push({ field: \"mail\", code: \"Email\", values: {} });"), ts);
    }

    @Test
    public void aListThatMustHoldOneIsNotNullThenSize() {
        String ts = typescript(item("tags", "List<string> @minsize(1, 'at least one tag')"));
        assertTrue(
                ts.contains(
                        "if (!partial && (value.tags == null || value.tags.length < 1)) errors.tags = \"at least one tag\";"),
                ts);
        assertTrue(
                ts.contains(
                        "    if (!partial && value.tags == null) errors.push({ field: \"tags\", code: \"NotNull\", values: {} });\n"
                                + "    else if (!partial && value.tags != null && value.tags.length < 1) errors.push({ field: \"tags\", code: \"Size\", values: { min: 1 } });"),
                ts);
    }

    @Test
    public void aShownOnlyFieldIsNotChecked() {
        String ts = typescript(item("total", "int! @readonly"));
        String codes = ts.substring(ts.indexOf("validateItemCodes"));
        assertFalse(codes.contains("total"), codes);
    }

    // ---- XxxLabels ------------------------------------------------------------------

    private EnumSpec status() {
        return new EnumSpec("Status")
                .addField(loader.parse("code", "string(1) @code"))
                .addField(loader.parse("label", "string(20)"))
                .addField(loader.parse("label_en", "string(20)"))
                .addField(loader.parse("label_zh_TW", "string(20)"))
                .constant("ACTIVE", "\"A\"", "\"사용\"", "\"Active\"", "\"使用\"")
                .constant("STOPPED", "\"S\"", "\"중지\"", "\"Stopped \\\"x\\\"\"", "\"STOPPED\"")
                .jsonField("code")
                .dbField("code");
    }

    @Test
    public void anEnumerationsTranslationsAreKeyedAsItsLabel() {
        Map<String, Map<String, String>> all = new LinkedHashMap<String, Map<String, String>>();
        Map<String, String> active = new TreeMap<String, String>();
        active.put("en", "Active");
        active.put("zh-TW", "使用");
        all.put("ACTIVE", active);
        Map<String, String> stopped = new TreeMap<String, String>();
        stopped.put("en", "Stopped \"x\"");
        all.put("STOPPED", stopped);
        String ts = typescript(status().labelTranslations(all));
        assertTrue(ts.contains("[Status.ACTIVE]: \"사용\","), "XxxLabel is unchanged:\n" + ts);
        assertTrue(ts.contains("export const StatusLabels: Record<Status, Record<string, string>> = {"), ts);
        assertTrue(ts.contains("    [Status.ACTIVE]: { en: \"Active\", \"zh-TW\": \"使用\" },"), ts);
        assertTrue(ts.contains("    [Status.STOPPED]: { en: \"Stopped \\\"x\\\"\" },"), ts);
    }

    // ---- which columns translate the label column (EnumLabelColumns) ----------------

    private java.util.List<Field> fields(String... nameAndLine) {
        java.util.List<Field> out = new java.util.ArrayList<Field>();
        for (int i = 0; i < nameAndLine.length; i += 2) out.add(loader.parse(nameAndLine[i], nameAndLine[i + 1]));
        return out;
    }

    @Test
    public void onlyASnakeLanguageSuffixIsATranslation() {
        java.util.List<Field> f = fields(
                "code", "string(1) @code",
                "name", "string(20)",
                "name_en", "string(20)",
                "name_pt_br", "string(20)",
                "nameJa", "string(20)",
                "name_id", "string(20)",
                "name_long", "string(20)",
                "name_EN", "string(20)",
                "name_no", "int",
                "name_kor", "string(20)",
                "name_old", "string(20)",
                "name_cd", "string(20)",
                "name_seq", "long");
        Field label = EnumLabelColumns.labelColumn(f, "code");
        assertTrue(label == f.get(1), "name is the label column");
        Map<String, Integer> columns = EnumLabelColumns.columns(f, label, "code");
        assertTrue(
                columns.toString().equals("{en=2, id=5, pt-BR=3}"),
                "camelCase, a number, three letters and a non-ISO pair are not read; the first en is kept: " + columns);
        assertTrue(
                EnumLabelColumns.notLanguages(f, label, "code")
                        .equals(java.util.Arrays.asList("name_long", "name_kor", "name_old", "name_cd")),
                "the text columns only: " + EnumLabelColumns.notLanguages(f, label, "code"));
    }

    @Test
    public void aLanguageIsAnIsoLanguageFirst() {
        assertTrue(EnumLabelColumns.isLanguage("en"));
        assertTrue(EnumLabelColumns.isLanguage("pt_BR"));
        assertTrue(EnumLabelColumns.isLanguage("zh_TW"));
        assertTrue(EnumLabelColumns.isLanguage("no"), "Norwegian");
        assertTrue(EnumLabelColumns.isLanguage("id"), "Indonesian");
        assertFalse(EnumLabelColumns.isLanguage("kor"));
        assertFalse(EnumLabelColumns.isLanguage("old"));
        assertFalse(EnumLabelColumns.isLanguage("cd"));
        assertFalse(EnumLabelColumns.isLanguage("long"));
    }

    @Test
    public void aCommentedFieldIsTheLabelColumnAsXxxLabelShowsIt() {
        java.util.List<Field> f = fields("code", "string(1) @code", "title", "string(20) -- 제목", "label", "string(20)");
        assertTrue(EnumLabelColumns.labelColumn(f, "code") == f.get(1), "the -- comment is a @label");
        assertTrue(EnumLabelColumns.labelColumn(fields("code", "string(1) @code"), "code") == null);
    }

    @Test
    public void withoutColumnsThereAreNoTranslations() {
        String ts = typescript(status());
        assertTrue(ts.contains("export const StatusLabel"), ts);
        assertFalse(ts.contains("StatusLabels"), ts);
    }
}
