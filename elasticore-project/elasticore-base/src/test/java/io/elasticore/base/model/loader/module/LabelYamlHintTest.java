package io.elasticore.base.model.loader.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.elasticore.base.model.entity.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue 345, its follow-up: a "--" label that holds ": " makes YAML read a mapping. The
 * file does not load, and the parser's message points into the middle of the label without
 * saying the label is the cause. The load error now says so - and only then. And what the
 * DSL guide says about the characters a label cannot hold unquoted, checked by loading.
 */
public class LabelYamlHintTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private File file;

    private static String model(String fieldLine) {
        return String.join(
                "\n",
                "entity:",
                "  Upload:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      uploadId: long @id @sequence",
                "      " + fieldLine,
                "");
    }

    /** The hint the loader adds for this file, after the parse failed as it fails in the loader. */
    private String hintFor(String text) throws Exception {
        file = File.createTempFile("label-hint", ".yml");
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        Throwable failed = assertThrows(Throwable.class, () -> YAML.readValue(file, LinkedHashMap.class));
        return YamlFileMainModelLoader.labelHint(file, failed);
    }

    @Test
    public void aColonInALabelIsNamed() throws Exception {
        assertEquals(
                "the label of versionNo contains ': ' - quote the whole value or reword it",
                hintFor(model("versionNo: int -- version: raised when the same kind is uploaded again")));
        assertEquals(
                "the label of fileName contains ': ' - quote the whole value or reword it",
                hintFor(model("fileName: string(200)! @search(like) -- 파일명: 올린 이름 그대로")));
    }

    @Test
    public void anErrorOutsideALabelGetsNoHint() throws Exception {
        // Before the "--": the type itself holds the ": ".
        assertNull(hintFor(model("versionNo: int: x -- version")));
        // No label on the line at all.
        assertNull(hintFor(model("versionNo: int: x")));
        // Not a field line's error.
        assertNull(hintFor("entity:\n  Upload: [unclosed\n"));
    }

    @Test
    public void theLineAndColumnDecide() {
        String line = "      versionNo: int -- version: raised";
        int colon = line.indexOf(": raised");
        assertEquals(
                "the label of versionNo contains ': ' - quote the whole value or reword it",
                YamlFileMainModelLoader.labelHint(Arrays.asList(line), 0, colon));
        assertNull(YamlFileMainModelLoader.labelHint(Arrays.asList(line), 0, line.indexOf(" --")));
        assertNull(YamlFileMainModelLoader.labelHint(Arrays.asList(line), 1, colon), "a line the file does not have");
        assertNull(
                YamlFileMainModelLoader.labelHint(Arrays.asList("      - versionNo -- a: b"), 0, 22),
                "not a field line");
    }

    // ---- what the DSL guide says, by loading the field line as the loader does --------------------------

    private static class Loader extends AbstractModelLoader {
        Field field(String yaml, String name) throws Exception {
            Map<?, ?> root = YAML.readValue(yaml, LinkedHashMap.class);
            Map<?, ?> upload = (Map<?, ?>) ((Map<?, ?>) root.get("entity")).get("Upload");
            Object line = ((Map<?, ?>) upload.get("fields")).get(name);
            return parseFieldLine(name, String.valueOf(line), null);
        }
    }

    @Test
    public void aHashAfterASpaceCutsTheLabelSilently() throws Exception {
        Field f = new Loader().field(model("price: long -- price #2 of the list"), "price");
        assertEquals("price", f.getAnnotationValue("label"), "YAML reads ' #' as the start of a comment");
    }

    @Test
    public void theWholeValueQuotedKeepsTheLabel() throws Exception {
        Loader loader = new Loader();
        assertEquals(
                "version: raised when the same kind is uploaded again",
                loader.field(
                                model("versionNo: \"int -- version: raised when the same kind is uploaded again\""),
                                "versionNo")
                        .getAnnotationValue("label"));
        assertEquals(
                "price #2 of the list",
                loader.field(model("price: \"long -- price #2 of the list\""), "price")
                        .getAnnotationValue("label"));
        Field single = loader.field(model("owner: 'string(30)! -- owner''s name: as signed'"), "owner");
        assertEquals("owner's name: as signed", single.getAnnotationValue("label"));
        assertTrue(single.hasAnnotation("notnull"), "the rest of the line is read as before");
    }

    @AfterEach
    void clean() throws Exception {
        if (file != null) Files.deleteIfExists(file.toPath());
    }
}
