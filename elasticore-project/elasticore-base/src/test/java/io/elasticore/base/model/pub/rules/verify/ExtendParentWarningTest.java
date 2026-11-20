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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An @extend(Parent) that names no Entity or DTO of any domain is a DSL
 * warning, as an unresolved field type is - and, like it, fails
 * elcoreValidate -Pstrict. It used to pass without a word, and the build
 * found the missing class. A warning, not an error: a class written by hand
 * may be the parent on purpose.
 */
public class ExtendParentWarningTest {

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
        "elasticore.rules.list",
        RulesGenRunner.PROP_VALIDATE,
        RulesGenRunner.PROP_VALIDATE_STRICT
    };

    private static final String SAID =
            "[dgshop] Note: unresolved @extend parent 'Missing' - no matching Entity or DTO found in any domain";

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
    public void anUnknownParentIsSaidAndTheRunGoesOn() throws Exception {
        File project = project(model("Missing", ""));
        String log = run(project, 0);
        assertTrue(log.contains(SAID), log);
        assertEquals(log.indexOf(SAID), log.lastIndexOf(SAID), log);
    }

    @Test
    public void anUnknownParentOfADtoIsSaidToo() throws Exception {
        File project = project(model(
                null,
                String.join(
                        "\n",
                        "dto:",
                        "  NoteView:",
                        "    meta: dto @extend(MissingView)",
                        "    fields:",
                        "      title: string",
                        "")));
        String log = run(project, 0);
        assertTrue(
                log.contains("[dgshop] NoteView: unresolved @extend parent 'MissingView' - no matching Entity or DTO"
                        + " found in any domain"),
                log);
    }

    @Test
    public void validateStrictCountsIt() throws Exception {
        File project = project(model("Missing", ""));
        run(project, 0);
        System.setProperty(RulesGenRunner.PROP_VALIDATE, "true");
        System.setProperty(RulesGenRunner.PROP_VALIDATE_STRICT, "true");
        String log = run(project, 1);
        assertTrue(log.contains("DSL warning(s) (-Pstrict)"), log);
        assertTrue(log.contains("unresolved @extend parent 'Missing'"), log);

        // Without -Pstrict a warning fails nothing, as before.
        System.clearProperty(RulesGenRunner.PROP_VALIDATE_STRICT);
        log = run(project, 0);
        assertFalse(log.contains("DSL warning(s) (-Pstrict)"), log);
    }

    @Test
    public void aKnownParentAFullClassNameOrAnotherDomainsIsNotSaid() throws Exception {
        String base = String.join(
                "\n", "  Base:", "    meta: entity @abstract", "    fields:", "      createdBy: string(20)", "");
        for (String parent : new String[] {"Base", "com.acme.support.HandWritten", "other:Base"}) {
            File project = project(model(parent, "").replace("entity:\n", "entity:\n" + base));
            String log = run(project, -1);
            assertFalse(log.contains("unresolved @extend parent"), parent + ": " + log);
        }
    }

    // ---- helpers ----------------------------------------------------------------------

    private static String model(String parent, String more) {
        return String.join(
                "\n",
                "entity:",
                "  Note:",
                "    meta: entity @expose(20)" + (parent == null ? "" : " @extend(" + parent + ")"),
                "    fields:",
                "      noteId: long @id @sequence",
                "      title: string(100)! @search(like)",
                "",
                more);
    }

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
        if (expected >= 0) assertEquals(expected, code, log);
        return log;
    }

    private File project(String model) throws IOException {
        File dir = Files.createTempDirectory("elcore-extend").toFile();
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
                "  namespace:",
                "    base: io.acme.dgshop",
                "");
        Files.write(new File(blueprint, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
        return dir;
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
