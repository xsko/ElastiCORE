package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.pub.ossie.OssieSemanticModelExporter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A label in other languages, generated: the domain's labels as a resource
 * bundle beside the message bundle, and the translations as the Ossie
 * synonyms of the field. A model with no translation writes neither.
 */
public class LabelTranslationGenerationTest {

    private static final String TRANSLATED = String.join(
            "\n",
            "entity:",
            "  Item:",
            "    meta: entity @root @expose(20) @label(en=Pet for sale, ja=販売単位) -- 판매 단위",
            "    fields:",
            "      itemId:   long @id @sequence",
            "      name:     string(30)! @label(en=Name) -- 이름",
            "      unitCost: int! @terms(price, 가격) @label(en=Unit cost, ja=単価, zh-TW=單價) -- 단가",
            "      note:     string(100) -- 메모",
            "");

    private static final String PLAIN = String.join(
            "\n",
            "entity:",
            "  Item:",
            "    meta: entity @root @expose(20) -- 판매 단위",
            "    fields:",
            "      itemId:   long @id @sequence",
            "      unitCost: int! -- 단가",
            "");

    @Test
    public void theLabelsAreABundleWithAFilePerLanguage() throws Exception {
        File project = generate(TRANSLATED);
        try {
            Properties base = bundle(project, "desk-labels.properties");
            assertEquals("판매 단위", base.getProperty("Item"));
            assertEquals("이름", base.getProperty("Item.name"));
            assertEquals("단가", base.getProperty("Item.unitCost"));
            assertEquals("메모", base.getProperty("Item.note"), "every label, translated or not");

            Properties en = bundle(project, "desk-labels_en.properties");
            assertEquals("Pet for sale", en.getProperty("Item"));
            assertEquals("Name", en.getProperty("Item.name"));
            assertEquals("Unit cost", en.getProperty("Item.unitCost"));
            assertNull(en.getProperty("Item.note"), "no translation: the default file answers");

            assertEquals("単価", bundle(project, "desk-labels_ja.properties").getProperty("Item.unitCost"));
            assertEquals("單價", bundle(project, "desk-labels_zh_TW.properties").getProperty("Item.unitCost"));

            // ASCII by construction, as the message bundle: any ResourceBundle encoding reads it.
            byte[] raw = Files.readAllBytes(
                    find(project, "desk-labels_ja.properties").toPath());
            for (byte b : raw) assertTrue(b >= 0, "a non-ASCII byte in the bundle");
        } finally {
            delete(project);
        }
    }

    @Test
    public void theTranslationsAreTheOssieSynonyms() throws Exception {
        File project = generate(TRANSLATED);
        try {
            String yaml = new String(
                    Files.readAllBytes(
                            new File(project, "src/main/resources/blueprint/" + OssieSemanticModelExporter.FILE_NAME)
                                    .toPath()),
                    StandardCharsets.UTF_8);
            // The business's words first, then the label in each language.
            String unitCost = yaml.substring(yaml.indexOf("- name: unit_cost"));
            unitCost = unitCost.substring(0, unitCost.indexOf("\n      - name: ", 5));
            assertTrue(unitCost.contains("synonyms: [\"price\", \"가격\", \"Unit cost\", \"単価\", \"單價\"]"), unitCost);
            assertTrue(unitCost.contains("description: \"단가\""), "the label stays the description:\n" + unitCost);
            assertTrue(yaml.contains("synonyms: [\"Name\"]"), yaml);
            assertTrue(yaml.contains("synonyms: [\"Pet for sale\", \"販売単位\"]"), yaml);
        } finally {
            delete(project);
        }
    }

    @Test
    public void aModelWithoutATranslationWritesNoLabelBundle() throws Exception {
        File project = generate(PLAIN);
        try {
            assertNull(find(project, "desk-labels.properties"));
            assertTrue(find(project, "desk-messages.properties") != null, "the message bundle is written as before");
            String yaml = new String(
                    Files.readAllBytes(
                            new File(project, "src/main/resources/blueprint/" + OssieSemanticModelExporter.FILE_NAME)
                                    .toPath()),
                    StandardCharsets.UTF_8);
            assertFalse(yaml.contains("synonyms"), yaml);
        } finally {
            delete(project);
        }
    }

    private static Properties bundle(File project, String name) throws Exception {
        File f = find(project, name);
        assertTrue(f != null, name + " was not written");
        Properties p = new Properties();
        InputStream in = new FileInputStream(f);
        try {
            p.load(in);
        } finally {
            in.close();
        }
        return p;
    }

    private static File find(File dir, String name) {
        List<File> todo = new ArrayList<File>();
        todo.add(dir);
        while (!todo.isEmpty()) {
            File d = todo.remove(0);
            File[] children = d.listFiles();
            if (children == null) continue;
            for (File c : children) {
                if (c.isDirectory()) todo.add(c);
                else if (c.getName().equals(name)) return c;
            }
        }
        return null;
    }

    private static File generate(String model) throws Exception {
        return generate(model, "jpa", "hexagonal", "java", "[hexagonal-springboot, ossie-export]");
    }

    /** Model information: every mode, both languages, every architecture write the same bundle. */
    @Test
    public void everyModeAndLanguageWritesTheBundle() throws Exception {
        String[][] runs = {
            {"mybatis", "layered", "java"},
            {"mongo", "layered", "java"},
            {"jpa", "ddd", "kotlin"},
            {"mongo", "layered", "kotlin"},
        };
        for (String[] run : runs) {
            String model = run[0].equals("mongo") ? TRANSLATED.replace("long @id @sequence", "string @id") : TRANSLATED;
            File project = generate(model, run[0], run[1], run[2], null);
            try {
                String what = String.join("/", run);
                assertEquals(
                        "Unit cost",
                        bundle(project, "desk-labels_en.properties").getProperty("Item.unitCost"),
                        what);
                assertEquals("단가", bundle(project, "desk-labels.properties").getProperty("Item.unitCost"), what);
            } finally {
                delete(project);
            }
        }
    }

    /** A language taken out of the DSL: the next run removes its file, which nobody edited. */
    @Test
    public void aLanguageTakenOutLosesItsFile() throws Exception {
        File project = generate(TRANSLATED);
        try {
            assertTrue(find(project, "desk-labels_ja.properties") != null);
            generateInto(
                    project,
                    TRANSLATED.replace(", ja=販売単位", "").replace(", ja=単価", ""),
                    "jpa",
                    "hexagonal",
                    "java",
                    "[hexagonal-springboot, ossie-export]");
            assertNull(find(project, "desk-labels_ja.properties"), "the Japanese file is left");
            assertTrue(find(project, "desk-labels_zh_TW.properties") != null);
        } finally {
            delete(project);
        }
    }

    private static File generate(String model, String mode, String architecture, String language, String rules)
            throws Exception {
        File project = Files.createTempDirectory("elcore-labels").toFile();
        generateInto(project, model, mode, architecture, language, rules);
        return project;
    }

    private static void generateInto(
            File project, String model, String mode, String architecture, String language, String rules)
            throws Exception {
        File dir = new File(project, "src/main/resources/blueprint/desk");
        assertTrue(new File(dir, "model").isDirectory() || new File(dir, "model").mkdirs());
        String env = String.join(
                "\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: desk",
                "    framework: springboot",
                "    mode: " + mode,
                "    j2ee: jakarta",
                rules == null ? "" : "    publisher:\n      rules: " + rules,
                "  namespace:",
                "    base: com.acme.desk",
                "");
        Files.write(new File(dir, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));

        String[] keys = {
            "elasticore.base.path", "elasticore.architecture", "elasticore.language", "elasticore.rules.force"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        int status;
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            status = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
            java.lang.reflect.Field tmp =
                    io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
            tmp.setAccessible(true);
            tmp.set(null, null);
            SpecFactory.valueObjectsInDto(false);
            SpecFactory.rootsSplitByDefault(false);
            SpecFactory.commandOnlyRoots(false);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        assertEquals(0, status, log);
    }

    private static void delete(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) delete(c);
        assertTrue(f.delete() || !f.exists(), "could not delete " + f);
    }
}
