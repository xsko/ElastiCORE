package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * env.yml config: schemaInfo: model (issue 373), generated: the domain's schema file beside the controllers and the
 * controller's one line that answers from it - in every mode, language and architecture. Without the setting the
 * controller says what it always said and no file is written; the 2.x code, a value the setting does not take and a
 * controller the developer keeps without the call are said.
 */
public class SchemaInfoGenerationTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Product:",
            "    meta: entity @root @expose(20) -- Product",
            "    fields:",
            "      productId: long @id @sequence",
            "      name:      string(30)! @search(like)",
            "  Item:",
            "    meta: entity @root @expose(20) @label(en=Item for sale) -- 판매 단위",
            "    fields:",
            "      itemId:   long @id @sequence",
            "      name:     string(30)! @length(min=2) -- 이름",
            "      unitCost: int! @min(1) @max(99) @label(en=Unit cost) -- 단가",
            "      status:   ItemStatus",
            "      product:  Product @ManyToOne",
            "  Hidden:",
            "    meta: entity @root @expose(20, schema=false)",
            "    fields:",
            "      hiddenId: long @id @sequence",
            "      note:     string(20)",
            "enumeration:",
            "  ItemStatus:",
            "    meta: enum @json(code)",
            "    fields:",
            "      code:  string(2) @code",
            "      label: string(20)",
            "    enum:",
            "      ON_SALE: ON, On sale",
            "      OFF:     OF, Off",
            "dto:",
            "  ItemDTO:",
            "    meta: dto @template(Item)",
            "    fields:",
            "      productName: string @ref(product.name)",
            "");

    private static final String SETTING = "    schemaInfo: model\n";

    /** The JPA hexagon in Java: the file, the line, and no DTOUtils left in the controller. */
    @Test
    public void theControllerAnswersFromTheFileBesideIt() throws Exception {
        File project = Files.createTempDirectory("elcore-schema").toFile();
        try {
            String log = generate(project, MODEL, "jpa", "hexagonal", "java", SETTING, false);
            String json = read(find(project, "desk-schema.json"));
            assertTrue(find(project, "desk-schema.json")
                    .getPath()
                    .replace('\\', '/')
                    .endsWith("src/main/resources/elasticore/desk-schema.json"));
            assertTrue(json.startsWith("{\n  \"metaVersion\": 1,\n  \"domain\": \"desk\","), json);
            // The entity, its label in the DSL's text (Korean, escaped: the file is ASCII) and its translation.
            assertTrue(json.contains("\"label\": \"\\ud310\\ub9e4 \\ub2e8\\uc704\""), json);
            assertTrue(json.contains("\"en\": \"Item for sale\""), json);
            assertTrue(json.contains("\"path\": \"/api/desk/item\""), json);
            for (byte b : json.getBytes(StandardCharsets.UTF_8)) assertTrue(b >= 0, "a non-ASCII byte");
            // The field facts.
            assertTrue(json.contains("\"maxLength\": 30,\n          \"minLength\": 2,"), json);
            assertTrue(json.contains("\"min\": 1,\n          \"max\": 99,"), json);
            assertTrue(json.contains("\"enum\": \"ItemStatus\""), json);
            assertTrue(
                    json.contains("\"value\": \"ON\",\n              \"name\": \"ON_SALE\",\n"
                            + "              \"label\": \"On sale\""),
                    json);
            assertTrue(json.contains("\"entity\": \"Product\""), json);
            assertTrue(json.contains("\"search\": \"/api/desk/product/search\""), json);
            assertTrue(json.contains("\"display\": {\n              \"field\": \"productName\","), json);
            // schema=false: no endpoint, no entry.
            assertFalse(json.contains("\"Hidden\""), json);

            String controller = read(find(project, "ItemController.java"));
            assertTrue(
                    controller.contains(
                            "        return io.elasticore.springboot3.schema.SchemaInfo.answer(\"desk\", \"Item\");\n"),
                    controller);
            assertFalse(controller.contains("DTOUtils"), controller);
            assertFalse(log.contains("schemaInfo"), log);
        } finally {
            delete(project);
        }
    }

    /** Model information: every mode, both languages, every architecture answer from the same file. */
    @Test
    public void everyModeLanguageAndArchitecture() throws Exception {
        String[][] runs = {
            {"mybatis", "layered", "java", "ItemController.java"},
            {"mybatis", "hexagonal", "java", "ItemController.java"},
            {"mongo", "layered", "java", "ItemController.java"},
            {"jpa", "ddd", "kotlin", "ItemController.kt"},
            {"jpa", "ddd", "java", "ItemController.java"},
            {"jpa", "layered", "java", "ItemController.java"},
            {"mongo", "layered", "kotlin", "ItemController.kt"},
        };
        for (String[] run : runs) {
            String what = String.join("/", run);
            String model = run[0].equals("mongo") ? MODEL.replace("long @id @sequence", "string @id") : MODEL;
            File project = Files.createTempDirectory("elcore-schema").toFile();
            try {
                generate(project, model, run[0], run[1], run[2], SETTING, false);
                File file = find(project, "desk-schema.json");
                assertNotNull(file, what);
                String json = read(file);
                assertTrue(json.contains("\"Item\": {"), what + "\n" + json);
                assertTrue(json.contains("\"maxLength\": 30"), what + "\n" + json);
                assertTrue(json.contains("\"en\": \"Unit cost\""), what + "\n" + json);
                assertTrue(json.contains("\"name\": \"ON_SALE\""), what + "\n" + json);
                String controller = read(find(project, run[3]));
                assertTrue(
                        controller.contains("io.elasticore.springboot3.schema.SchemaInfo.answer(\"desk\", \"Item\")"),
                        what + "\n" + controller);
                assertFalse(controller.contains("DTOUtils"), what + "\n" + controller);
            } finally {
                delete(project);
            }
        }
    }

    /** The setting taken out: the controller says what it always said again, and the file goes. */
    @Test
    public void theSettingTakenOutTakesTheFileAway() throws Exception {
        File project = Files.createTempDirectory("elcore-schema").toFile();
        try {
            generate(project, MODEL, "jpa", "layered", "java", SETTING, false);
            assertNotNull(find(project, "desk-schema.json"));
            String log = generate(project, MODEL, "jpa", "layered", "java", "", false);
            assertNull(find(project, "desk-schema.json"), log);
            assertTrue(read(find(project, "ItemController.java"))
                    .contains("        return ResponseEntity.ok(DTOUtils.getMetaInfo(ItemDTO.class));\n"));
        } finally {
            delete(project);
        }
    }

    /** An enumeration of another domain: its values are named by that domain's label bundle. */
    @Test
    public void anEnumerationOfAnotherDomainIsNamedByItsDomain() throws Exception {
        File project = Files.createTempDirectory("elcore-schema").toFile();
        try {
            File common = new File(project, "src/main/resources/blueprint/common");
            assertTrue(new File(common, "model").mkdirs());
            Files.write(
                    new File(common, "env.yml").toPath(),
                    String.join(
                                    "\n",
                                    "elasticore:",
                                    "  enable: true",
                                    "  config:",
                                    "    domainName: common",
                                    "    framework: springboot",
                                    "    mode: jpa",
                                    "    j2ee: jakarta",
                                    "  namespace:",
                                    "    base: com.acme.common",
                                    "")
                            .getBytes(StandardCharsets.UTF_8));
            Files.write(
                    new File(common, "model/model.yml").toPath(),
                    String.join(
                                    "\n",
                                    "entity:",
                                    "  Office:",
                                    "    meta: entity @root @expose(20) -- Office",
                                    "    fields:",
                                    "      officeId: long @id @sequence",
                                    "enumeration:",
                                    "  Currency:",
                                    "    meta: enum @json(code)",
                                    "    fields:",
                                    "      code:     string(3) @code",
                                    "      label:    string(20)",
                                    "      label_ko: string(20)",
                                    "    enum:",
                                    "      KRW: KRW, Won, Won-ko",
                                    "      USD: USD, Dollar, Dollar-ko",
                                    "")
                            .getBytes(StandardCharsets.UTF_8));
            String model = MODEL.replace(
                    "      product:  Product @ManyToOne\n",
                    "      product:  Product @ManyToOne\n      currency: Currency\n");
            generate(project, model, "jpa", "hexagonal", "java", SETTING, false);
            String json = read(find(project, "desk-schema.json"));
            assertTrue(json.contains("\"enum\": \"Currency\",\n          \"enumDomain\": \"common\","), json);
            assertTrue(json.contains("\"enum\": \"ItemStatus\",\n          \"enumDomain\": \"desk\","), json);
            // The bundle it is named by.
            assertNotNull(find(project, "common-labels_ko.properties"));
        } finally {
            delete(project);
        }
    }

    /** Unset: the line the controller always had, and no file. */
    @Test
    public void withoutTheSettingNothingChanges() throws Exception {
        File project = Files.createTempDirectory("elcore-schema").toFile();
        try {
            generate(project, MODEL, "jpa", "hexagonal", "java", "", false);
            assertNull(find(project, "desk-schema.json"));
            String controller = read(find(project, "ItemController.java"));
            assertTrue(
                    controller.contains("        return ResponseEntity.ok(DTOUtils.getMetaInfo(ItemDTO.class));\n"),
                    controller);
            assertTrue(controller.contains("import io.elasticore.runtime.utils.DTOUtils;"), controller);

            File kt = Files.createTempDirectory("elcore-schema").toFile();
            try {
                generate(kt, MODEL, "jpa", "layered", "kotlin", "", false);
                assertTrue(read(find(kt, "ItemController.kt"))
                        .contains("        return ResponseEntity.ok(DTOUtils.getMetaInfo(ItemDTO::class.java))\n"));
            } finally {
                delete(kt);
            }
        } finally {
            delete(project);
        }
    }

    /** The 2.x code has no such answer: said, and nothing written. A value the setting does not take is said. */
    @Test
    public void theTwoXCodeAndAnUnknownValueAreSaid() throws Exception {
        File project = Files.createTempDirectory("elcore-schema").toFile();
        try {
            String log = generate(project, MODEL, "jpa", "layered", "java", SETTING, true);
            assertTrue(log.contains("[desk] config: schemaInfo: model is written for the 3.0 code"), log);
            assertNull(find(project, "desk-schema.json"));
        } finally {
            delete(project);
        }
        File other = Files.createTempDirectory("elcore-schema").toFile();
        try {
            String log = generate(other, MODEL, "jpa", "layered", "java", "    schemaInfo: models\n", false);
            assertTrue(log.contains("[desk] env.yml config.schemaInfo 'models' is not a value it takes"), log);
            assertNull(find(other, "desk-schema.json"));
            assertTrue(read(find(other, "ItemController.java")).contains("DTOUtils.getMetaInfo(ItemDTO.class)"));
        } finally {
            delete(other);
        }
    }

    /** A controller the developer keeps, and whose GET /schema/info does not call the runtime, is named. */
    @Test
    public void aKeptControllerWithoutTheCallIsNamed() throws Exception {
        File project = Files.createTempDirectory("elcore-schema").toFile();
        try {
            generate(project, MODEL, "jpa", "layered", "java", "", false);
            File file = find(project, "ItemController.java");
            String edited = read(file)
                    .replace(
                            "        return ResponseEntity.ok(DTOUtils.getMetaInfo(ItemDTO.class));",
                            "        return ResponseEntity.ok(DTOUtils.getMetaInfo(ItemDTO.class)); // mine");
            Files.write(file.toPath(), edited.getBytes(StandardCharsets.UTF_8));
            // The developer changed the very line the generator now changes: the merge keeps theirs.
            String log = generate(project, MODEL, "jpa", "layered", "java", SETTING, false);
            assertTrue(read(file).contains("DTOUtils.getMetaInfo(ItemDTO.class)); // mine"), read(file));
            assertTrue(
                    log.contains("ItemController.java is yours, and its GET /schema/info does not call"
                            + " SchemaInfo.answer"),
                    log);
            assertTrue(
                    log.contains("Return io.elasticore.springboot3.schema.SchemaInfo.answer(\"desk\", \"Item\")"), log);
            assertNotNull(find(project, "desk-schema.json"));

            // The line written as the generator writes it: nothing to say.
            Files.write(
                    file.toPath(),
                    read(file)
                            .replace(
                                    "        return ResponseEntity.ok(DTOUtils.getMetaInfo(ItemDTO.class)); // mine",
                                    "        return io.elasticore.springboot3.schema.SchemaInfo.answer(\"desk\", \"Item\");")
                            .getBytes(StandardCharsets.UTF_8));
            log = generate(project, MODEL, "jpa", "layered", "java", SETTING, false);
            assertFalse(log.contains("is yours, and its GET /schema/info"), log);
        } finally {
            delete(project);
        }
    }

    // ---- running the generator ------------------------------------------------------

    private static String read(File f) throws Exception {
        assertNotNull(f);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
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

    private static String generate(
            File project, String model, String mode, String architecture, String language, String config, boolean v2)
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
                config,
                "  namespace:",
                "    base: com.acme.desk",
                "");
        Files.write(new File(dir, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));

        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v2"
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
            if (v2) System.setProperty("elasticore.rules.v2", "true");
            else System.clearProperty("elasticore.rules.v2");
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
            // And the context the run left: a later test that looks a type's label up must not find this model's.
            java.lang.reflect.Field ctx =
                    io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("context");
            ctx.setAccessible(true);
            ctx.set(null, null);
            SpecFactory.valueObjectsInDto(false);
            SpecFactory.rootsSplitByDefault(false);
            SpecFactory.commandOnlyRoots(false);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        assertEquals(0, status, log);
        return log;
    }

    private static void delete(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) delete(c);
        assertTrue(f.delete() || !f.exists(), "could not delete " + f);
    }
}
