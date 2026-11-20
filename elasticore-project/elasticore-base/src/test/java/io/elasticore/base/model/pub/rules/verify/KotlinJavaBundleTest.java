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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Kotlin build whose domain names a Java bundle in its env.yml - the
 * catalog domain of elasticore-template-ddd pins jpa-springboot-v3 - is
 * stopped before a file is written, with the name to write instead. It
 * used to write .kt files holding Java and Lombok, which only the Kotlin
 * compiler found.
 */
public class KotlinJavaBundleTest {

    private static final String[] KEYS = {
        "elasticore.base.path",
        "elasticore.architecture",
        "elasticore.language",
        "elasticore.rules.force",
        "elasticore.rules.v2",
        "elasticore.rules.v3",
        "elasticore.rules.ts",
        "elasticore.rules.kt",
        "elasticore.publisher.rules.add",
        "elasticore.rules.list"
    };

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Note:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      noteId: long @id @sequence",
            "      title: string(100)! @search(like)",
            "");

    private final List<File> projects = new ArrayList<File>();
    private final String[] saved = save();

    @AfterEach
    void clean() throws IOException {
        for (int i = 0; i < KEYS.length; i++) {
            if (saved[i] == null) System.clearProperty(KEYS[i]);
            else System.setProperty(KEYS[i], saved[i]);
        }
        for (File p : projects) delete(p.toPath());
    }

    @Test
    public void aKotlinBuildNamingTheJavaLayeredBundleStopsBeforeWriting() throws Exception {
        File project = project("    publisher:\n      rules: [jpa-springboot-v3]");
        System.setProperty("elasticore.language", "kotlin");
        String log = run(project, 1);
        assertTrue(log.contains("domain 'dgshop' names jpa-springboot-v3 in publisher.rules, which writes Java"), log);
        assertTrue(log.contains("name jpa-springboot-kt instead"), log);
        assertTrue(log.contains("No source was written."), log);
        assertFalse(wroteSource(project), "a source was written: " + log);
    }

    @Test
    public void theArchitectureBundlesAndAJavaOnlySetAreNamedWithTheirKotlinOnes() throws Exception {
        String[][] cases = {
            {"ddd-springboot", "ddd-springboot-kt"},
            {"hexagonal-springboot, ossie-export", "hexagonal-springboot-kt"},
            {"jpa-springboot-kt, ddd-aggregate, ddd-value-object", "ddd-value-object-kt"},
            {"jpa-springboot", "jpa-springboot-kt"},
        };
        for (String[] c : cases) {
            File project = project("    publisher:\n      rules: [" + c[0] + "]");
            System.setProperty("elasticore.language", "kotlin");
            String log = run(project, 1);
            assertTrue(log.contains("which writes Java") && log.contains("name " + c[1] + " instead"), log);
            assertFalse(wroteSource(project), "a source was written: " + log);
        }
    }

    @Test
    public void aDomainNamingNoRulesGetsTheKotlinBundle() throws Exception {
        File project = project("");
        System.setProperty("elasticore.language", "kotlin");
        String log = run(project, 0);
        assertNotNull(find(project, "Note.kt"), log);
    }

    @Test
    public void aJavaSetTheBuildAddsOrNamesIsRefusedToo() throws Exception {
        String[][] cases = {
            {
                "elasticore.publisher.rules.add",
                "ddd-value-object",
                "the build adds ddd-value-object",
                "ddd-value-object-kt"
            },
            {"elasticore.publisher.rules", "jpa-springboot-v3", "the build names jpa-springboot-v3", "jpa-springboot-kt"
            },
        };
        for (String[] c : cases) {
            File project = project("");
            System.setProperty("elasticore.language", "kotlin");
            System.setProperty(c[0], c[1]);
            try {
                String log = run(project, 1);
                assertTrue(log.contains(c[2] + " in " + c[0] + ", which writes Java"), log);
                assertTrue(log.contains("name " + c[3] + " instead"), log);
                assertFalse(wroteSource(project), "a source was written: " + log);
            } finally {
                System.clearProperty(c[0]);
            }
        }
    }

    @Test
    public void aKotlinBundleWithLanguageNeutralSetsIsGenerated() throws Exception {
        File project =
                project("    publisher:\n      rules: [jpa-springboot-kt, ddd-aggregate, ddd-reference, ossie-export]");
        System.setProperty("elasticore.language", "kotlin");
        String log = run(project, 0);
        assertNotNull(find(project, "Note.kt"), log);
        assertFalse(log.contains("which writes Java"), log);
    }

    @Test
    public void aJavaBuildNamingTheJavaBundleIsUnchanged() throws Exception {
        File project = project("    publisher:\n      rules: [jpa-springboot-v3]");
        System.setProperty("elasticore.language", "java");
        String log = run(project, 0);
        assertNotNull(find(project, "Note.java"), log);
    }

    // ---- helpers ----------------------------------------------------------------------

    private String run(File project, int expected) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        int code;
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.rules.force", "true");
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
        File dir = Files.createTempDirectory("elcore-kt-java").toFile();
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

    private static boolean wroteSource(File project) throws IOException {
        File src = new File(project, "src/main");
        try (Stream<Path> all = Files.walk(src.toPath())) {
            return all.anyMatch(
                    p -> p.toString().endsWith(".kt") || p.toString().endsWith(".java"));
        }
    }

    private static Path find(File project, String name) throws IOException {
        try (Stream<Path> all = Files.walk(new File(project, "src/main").toPath())) {
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

    private static void delete(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> all = Files.walk(root)) {
            List<Path> paths = new ArrayList<Path>();
            all.forEach(paths::add);
            for (int i = paths.size() - 1; i >= 0; i--) Files.deleteIfExists(paths.get(i));
        }
    }
}
