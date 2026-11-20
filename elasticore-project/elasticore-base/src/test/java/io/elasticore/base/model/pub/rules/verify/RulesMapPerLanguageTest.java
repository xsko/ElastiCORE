package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * publisher.rules as a map of per-language lists (kt:, ts:). A run that finds
 * no key of its own in the map runs as if the domain named no rules - the
 * default of its architecture and language. It used to read the map's text
 * as a rule name and stop: a Java build's elcore3 with any map, a Kotlin
 * build's with only ts:, both failed with "Unknown rule set '{ts=[ts-model]}'".
 */
public class RulesMapPerLanguageTest {

    private static final String[] KEYS = {
        "elasticore.base.path",
        "elasticore.architecture",
        "elasticore.language",
        "elasticore.rules.force",
        "elasticore.rules.v2",
        "elasticore.rules.v3",
        "elasticore.rules.ts",
        "elasticore.rules.kt",
        "elasticore.publisher.rules",
        "elasticore.publisher.rules.add",
        "elasticore.rules.list"
    };

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Customer:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      customerId: long @id @sequence",
            "      name: string(100)!",
            "  Note:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      noteId: long @id @sequence",
            "      title: string(100)! @search(like)",
            "      customer: Customer @ManyToOne",
            "");

    private static final String TS_ONLY = map("        ts: [ts-model]");
    private static final String KT_AND_TS =
            map("        kt: [jpa-springboot-kt, ddd-aggregate]", "        ts: [ts-model]");

    private final List<File> projects = new ArrayList<File>();
    private final String[] saved = save();

    @AfterEach
    void clean() throws IOException {
        restore();
        for (File p : projects) delete(p.toPath());
    }

    @Test
    public void aJavaBuildWithAMapHoldingOnlyTsRunsItsDefault() throws Exception {
        String expected = listing("java", "");
        assertEquals(expected, listing("java", TS_ONLY));
        File project = project(TS_ONLY);
        String log = run(project, "java", 0);
        assertFalse(log.contains("Unknown rule set"), log);
        assertNotNull(find(project, "Note.java"), log);
    }

    @Test
    public void aJavaBuildWithKtAndTsRunsItsDefault() throws Exception {
        String expected = listing("java", "");
        assertEquals(expected, listing("java", KT_AND_TS));
        File project = project(KT_AND_TS);
        String log = run(project, "java", 0);
        assertNotNull(find(project, "Note.java"), log);
        assertNull(find(project, "Note.kt"), log);
    }

    @Test
    public void aKotlinBuildWithAMapHoldingOnlyTsRunsItsDefault() throws Exception {
        String expected = listing("kotlin", "");
        assertEquals(expected, listing("kotlin", TS_ONLY));
        File project = project(TS_ONLY);
        String log = run(project, "kotlin", 0);
        assertNotNull(find(project, "Note.kt"), log);
    }

    @Test
    public void aKotlinBuildRunsItsKtList() throws Exception {
        assertEquals("[rules] dgshop: jpa-springboot-kt + ddd-aggregate", listing("kotlin", KT_AND_TS));
        File project = project(KT_AND_TS);
        String log = run(project, "kotlin", 0);
        assertNotNull(find(project, "Note.kt"), log);
    }

    @Test
    public void theTypeScriptRunOfAJavaBuildRunsItsTsList() throws Exception {
        String custom = map("        ts: [jpa-dto, jpa-dto-file-ts]");
        assertEquals("[rules] dgshop: jpa-dto + jpa-dto-file-ts", listing("typescript", custom));
        // And without ts: its default, as with no publisher.rules.
        assertEquals(listing("typescript", ""), listing("typescript", map("        kt: [jpa-springboot-kt]")));
    }

    @Test
    public void theTypeScriptClientOfAJavaBuildWithAMapIsTheOneWithoutIt() throws Exception {
        // The client describes the server: a map holding no server key leaves the
        // server's rules - the ddd default, references by identity - as they are.
        System.setProperty("elasticore.architecture", "ddd");
        File plain = project("");
        run(plain, "typescript", 0);
        File mapped = project(TS_ONLY);
        run(mapped, "typescript", 0);
        Map<String, String> expected = files(plain, ".ts");
        assertFalse(expected.isEmpty(), "no .ts file written");
        assertEquals(expected, files(mapped, ".ts"));
    }

    @Test
    public void theListFormIsUnchanged() throws Exception {
        assertEquals(
                "[rules] dgshop: jpa-springboot-v3 + ddd-aggregate",
                listing("java", "    publisher:\n      rules: [jpa-springboot-v3, ddd-aggregate]"));
        assertEquals(
                "[rules] dgshop: jpa-springboot-kt + ddd-aggregate",
                listing("kotlin", "    publisher:\n      rules: [jpa-springboot-kt, ddd-aggregate]"));
        // A Kotlin build naming a Java bundle in the list still stops (KotlinJavaBundleTest).
        File project = project("    publisher:\n      rules: [jpa-springboot-v3]");
        String log = run(project, "kotlin", 1);
        assertTrue(log.contains("which writes Java"), log);
    }

    @Test
    public void aKotlinBuildNamingAJavaBundleUnderKtStops() throws Exception {
        File project = project(map("        kt: [jpa-springboot-v3]"));
        String log = run(project, "kotlin", 1);
        assertTrue(log.contains("which writes Java") && log.contains("name jpa-springboot-kt instead"), log);
    }

    @Test
    public void aKeyNoRunReadsIsWarnedAbout() throws Exception {
        File project = project(map("        java: [jpa-springboot-v3]", "        ts: [ts-model]"));
        String log = run(project, "java", 0);
        assertTrue(log.contains("publisher.rules has a key no run reads: java"), log);
        assertTrue(log.contains("the keys are kt (a Kotlin build's elcore3) and ts (elcore3ts)"), log);
        assertNotNull(find(project, "Note.java"), log);

        File kotlin = project(map("        kts: [jpa-springboot-kt]"));
        log = run(kotlin, "kotlin", 0);
        assertTrue(log.contains("publisher.rules has a key no run reads: kts"), log);
        assertNotNull(find(kotlin, "Note.kt"), log);
    }

    @Test
    public void theOssieExportFollowsTheListTheRunTakes() throws Exception {
        String rules = map("        kt: [jpa-springboot-kt, ossie-export]");
        File kotlin = project(rules);
        String log = run(kotlin, "kotlin", 0);
        assertNotNull(find(kotlin, "semantic_model.yml"), log);
        File java = project(rules);
        log = run(java, "java", 0);
        assertNull(find(java, "semantic_model.yml"), log);
    }

    // ---- helpers ----------------------------------------------------------------------

    private static String map(String... lines) {
        return "    publisher:\n      rules:\n" + String.join("\n", lines);
    }

    /** The -PlistRules line of a run in this language over a fresh project. */
    private String listing(String language, String publisher) throws Exception {
        File project = project(publisher);
        System.setProperty("elasticore.rules.list", "true");
        try {
            String log = run(project, language, 0);
            for (String line : log.split("\\r?\\n")) {
                if (line.startsWith("[rules] "))
                    return line.replaceAll("\\s+\\(suppressed.*$", "").trim();
            }
            throw new AssertionError("no [rules] line: " + log);
        } finally {
            System.clearProperty("elasticore.rules.list");
        }
    }

    private String run(File project, String language, int expected) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        int code;
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.rules.force", "true");
            System.clearProperty("elasticore.rules.ts");
            if ("typescript".equals(language)) {
                System.clearProperty("elasticore.language");
                System.setProperty("elasticore.rules.ts", "true");
            } else {
                System.setProperty("elasticore.language", language);
            }
            code = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        assertEquals(expected, code, log);
        return log;
    }

    private File project(String publisher) throws IOException {
        File dir = Files.createTempDirectory("elcore-rules-map").toFile();
        projects.add(dir);
        File blueprint = new File(dir, "src/main/resources/blueprint/dgshop");
        assertTrue(new File(blueprint, "model").mkdirs());
        String env = String.join(
                "\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: dgshop",
                "    framework: springboot",
                "    mode: jpa",
                "    j2ee: jakarta",
                publisher,
                "  namespace:",
                "    base: io.acme.dgshop",
                "");
        Files.write(new File(blueprint, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    /** Every file of this extension the run wrote, by its path in the project. */
    private static Map<String, String> files(File project, String extension) throws IOException {
        Map<String, String> out = new TreeMap<String, String>();
        Path root = project.toPath();
        try (Stream<Path> all = Files.walk(root)) {
            for (Path p : (Iterable<Path>) all::iterator) {
                if (!Files.isRegularFile(p) || !p.toString().endsWith(extension)) continue;
                out.put(
                        root.relativize(p).toString().replace('\\', '/'),
                        new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private static Path find(File project, String name) throws IOException {
        try (Stream<Path> all = Files.walk(project.toPath())) {
            return all.filter(p -> p.getFileName().toString().equals(name))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static String[] save() {
        String[] s = new String[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) s[i] = System.getProperty(KEYS[i]);
        return s;
    }

    private void restore() {
        for (int i = 0; i < KEYS.length; i++) {
            if (saved[i] == null) System.clearProperty(KEYS[i]);
            else System.setProperty(KEYS[i], saved[i]);
        }
    }

    private static void delete(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> all = Files.walk(root)) {
            List<Path> paths = new ArrayList<Path>();
            all.forEach(paths::add);
            for (int i = paths.size() - 1; i >= 0; i--) Files.deleteIfExists(paths.get(i));
        }
    }
}
