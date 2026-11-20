package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static io.elasticore.base.model.pub.rules.verify.RuntimePackagesGenerationTest.delete;
import static io.elasticore.base.model.pub.rules.verify.RuntimePackagesGenerationTest.resetContext;
import static io.elasticore.base.model.pub.rules.verify.RuntimePackagesGenerationTest.run;
import static io.elasticore.base.model.pub.rules.verify.RuntimePackagesGenerationTest.snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * elasticore { runtimePackages } in the projects it meets: one generated
 * before the setting and edited since, a Kotlin domain model mapped in XML, a
 * copy of the runtime under the project's own package, a key that would
 * rename the project, and files a run keeps without writing them.
 */
public class RuntimePackagesProjectTest {

    private static final String MAPPING = "io.elasticore.springboot3=com.test.springboot,"
            + "io.elasticore.runtime=com.test.runtime,"
            + "io.elasticore.base.util=com.test.util";

    private static final String ITEM = String.join(
            "\n",
            "  Item:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      itemId: long @id @sequence",
            "      name:   string(30)!",
            "");

    private static final String STOCK = String.join(
            "\n",
            "  Stock:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      stockId: long @id @sequence",
            "      units:   int",
            "");

    private File project;

    @BeforeEach
    void create() throws IOException {
        project = Files.createTempDirectory("elcore-rtpkg").toFile();
    }

    @AfterEach
    void clean() throws Exception {
        resetContext();
        delete(project);
    }

    private void model(String base, String envExtra, String entities) throws IOException {
        File domain = new File(project, "src/main/resources/blueprint/shop");
        if (!domain.isDirectory()) assertTrue(new File(domain, "model").mkdirs());
        Files.write(
                new File(domain, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: shop",
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                envExtra,
                                "  namespace:",
                                "    base: " + base,
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(
                new File(domain, "model/model.yml").toPath(),
                ("entity:\n" + entities).getBytes(StandardCharsets.UTF_8));
    }

    private String generate(String mapping, String architecture, String language) throws Exception {
        resetContext();
        return run(project, mapping, null, architecture, language);
    }

    private static File find(File root, String suffix) throws IOException {
        try (java.util.stream.Stream<java.nio.file.Path> all = Files.walk(root.toPath())) {
            return all.filter(p -> p.toString().replace('\\', '/').endsWith(suffix))
                    .findFirst()
                    .map(java.nio.file.Path::toFile)
                    .orElse(null);
        }
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static void write(File f, String text) throws IOException {
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Turned on in a project generated before, whose developer edited a
     * merged file: next to a runtime name in a method's annotation, and in
     * its imports. The edits stay, the names are the project's, and there is
     * no conflict - the base and the file are renamed as the new text is.
     */
    @Test
    public void turnedOnOverEditsTheEditsStayAndNothingConflicts() throws Exception {
        model("com.acme.shop", "", ITEM);
        String first = generate(null, "hexagonal", "java");
        assertTrue(first.startsWith("exit 0\n"), first);

        File errors = find(project, "ShopApiErrors.java");
        assertTrue(errors != null, "no ShopApiErrors.java");
        String text = read(errors);
        String inline = "@ExceptionHandler(io.elasticore.springboot3.validation.NotFoundException.class)";
        assertTrue(text.contains(inline), text);
        String edited = text.replace(inline, "// mine: the 404 answer\n    " + inline)
                .replaceFirst("(?m)^(import [^\\n]+;\\n)", "$1import java.util.UUID;\n");
        assertTrue(edited.contains("import java.util.UUID;"), edited);
        write(errors, edited);

        String second = generate(MAPPING, "hexagonal", "java");
        String after = read(errors);
        assertTrue(second.startsWith("exit 0\n"), second);
        assertFalse(after.contains("<<<<<<<"), after);
        assertTrue(after.contains("// mine: the 404 answer"), after);
        assertTrue(after.contains("import java.util.UUID;"), after);
        assertTrue(after.contains("@ExceptionHandler(com.test.springboot.validation.NotFoundException.class)"), after);
        for (Map.Entry<String, String> en : snapshot(project).entrySet()) {
            if (en.getKey().startsWith("src/main/resources/blueprint/")) continue;
            assertFalse(en.getValue().contains("io.elasticore.springboot3"), en.getKey());
        }
        // The DSL did not change: the edited file stays the developer's, renamed.
        assertTrue(second.contains("ShopApiErrors.java  UNCHANGED(your edits kept)"), second);
        assertFalse(second.contains("runtimePackages: io.elasticore"), second);
    }

    /**
     * The developer changed the very line after a runtime name - the
     * handler's signature under its annotation. Changes on adjacent lines
     * are one hunk to a line merge: without renaming the base and the file
     * as the new text is, that was a conflict the setting made.
     */
    @Test
    public void anEditOnTheLineNextToARuntimeNameMerges() throws Exception {
        model("com.acme.shop", "", ITEM);
        assertTrue(generate(null, "hexagonal", "java").startsWith("exit 0\n"));
        File errors = find(project, "ShopApiErrors.java");
        String text = read(errors);
        String inline = "@ExceptionHandler(io.elasticore.springboot3.validation.NotFoundException.class)";
        int at = text.indexOf(inline);
        assertTrue(at >= 0, text);
        int next = text.indexOf('\n', at) + 1;
        int end = text.indexOf('\n', next);
        String signature = text.substring(next, end);
        assertTrue(signature.contains("("), signature);
        write(errors, text.substring(0, end) + " // mine" + text.substring(end));

        String second = generate(MAPPING, "hexagonal", "java");
        String after = read(errors);
        assertTrue(second.startsWith("exit 0\n"), second);
        assertFalse(after.contains("<<<<<<<"), after);
        // The developer's line, renamed with the rest.
        assertTrue(
                after.contains(signature.replace("io.elasticore.springboot3.", "com.test.springboot.") + " // mine"),
                after);
        assertTrue(second.contains("ShopApiErrors.java  UNCHANGED(your edits kept)"), second);
        assertTrue(after.contains("@ExceptionHandler(com.test.springboot.validation.NotFoundException.class)"), after);
        assertFalse(after.contains("io.elasticore.springboot3"), after);
    }

    /** mapping: xml in Kotlin puts PersistentModel on the domain classes: the mapped one. */
    @Test
    public void aKotlinXmlMappingNamesTheMappedPersistentModel() throws Exception {
        model("com.acme.shop", "    mapping: xml", ITEM);
        String said = generate(MAPPING, "hexagonal", "kotlin");
        assertTrue(said.startsWith("exit 0\n"), said);
        boolean marked = false;
        for (Map.Entry<String, String> en : snapshot(project).entrySet()) {
            if (en.getKey().startsWith("src/main/resources/blueprint/")) continue;
            assertFalse(en.getValue().contains("io.elasticore.springboot3"), en.getKey());
            marked |= en.getValue().contains("@com.test.springboot.entity.PersistentModel");
        }
        assertTrue(marked, "no domain class carries the mapped PersistentModel");
        assertTrue(read(new File(project, RuntimePackages.REPORT))
                .contains("com.test.springboot.entity.PersistentModel\n"));
        assertFalse(said.contains("runtimePackages: io.elasticore"), said);
    }

    /** A copy of the runtime under the project's own base package is the runtime still: listed. */
    @Test
    public void aCopyUnderTheProjectsPackageIsListed() throws Exception {
        model("com.acme.shop", "", ITEM);
        String said = generate("io.elasticore.springboot3=com.acme.shop.runtime", "hexagonal", "java");
        assertTrue(said.startsWith("exit 0\n"), said);
        String list = read(new File(project, RuntimePackages.REPORT));
        assertTrue(list.contains("com.acme.shop.runtime.validation.NotFoundException\n"), list);
        for (String line : list.split("\n", -1)) {
            if (!line.isEmpty()) assertTrue(line.startsWith("com.acme.shop.runtime."), line);
        }
    }

    /** A key that covers the project's own packages would rename the project: refused before anything is written. */
    @Test
    public void aKeyOverTheProjectsPackagesIsRefused() throws Exception {
        model("io.elasticore.sample.shop", "", ITEM);
        String said = generate("io.elasticore.sample=com.x", "hexagonal", "java");
        assertTrue(said.startsWith("exit 1\n"), said);
        assertTrue(
                said.contains("runtimePackages: the key 'io.elasticore.sample' covers io.elasticore.sample.shop"),
                said);
        assertFalse(new File(project, "src/main/java").exists(), "sources were written");
    }

    /**
     * A file the DSL no longer produces, kept because a source of the
     * developer's still uses it, is not written by the run: it still names the
     * runtime as it was generated, and the run says so.
     */
    @Test
    public void aFileKeptInUseIsScanned() throws Exception {
        model("com.acme.shop", "", ITEM + STOCK);
        assertTrue(generate(null, "hexagonal", "java").startsWith("exit 0\n"));
        File controller = find(project, "/StockController.java");
        assertTrue(controller != null, "no StockController.java");
        String pkg = read(controller).replaceAll("(?s)^.*?package ([\\w.]+);.*$", "$1");
        assertTrue(read(controller).contains("io.elasticore.springboot3."), "the controller names no runtime class");
        File mine = new File(project, "src/main/java/com/acme/shop/Mine.java");
        write(
                mine,
                "package com.acme.shop;\n\nimport " + pkg + ".StockController;\n\n"
                        + "public class Mine {\n    StockController controller;\n}\n");

        model("com.acme.shop", "", ITEM);
        String said = generate(MAPPING, "hexagonal", "java");
        assertTrue(said.startsWith("exit 0\n"), said);
        assertTrue(controller.isFile(), "the controller in use was removed");
        assertTrue(said.contains("StockController.java"), said);
        assertTrue(
                said.matches("(?s).*runtimePackages: io\\.elasticore\\.springboot3\\.\\w+ is still named in.*"), said);
    }

    @Test
    public void unsetTheOutputIsAsBefore() throws Exception {
        model("com.acme.shop", "", ITEM);
        assertTrue(generate(null, "hexagonal", "java").startsWith("exit 0\n"));
        Map<String, String> before = snapshot(project);
        String again = generate(null, "hexagonal", "java");
        assertTrue(again.contains("=== elcore3: 0 written / "), again);
        assertEquals(before, snapshot(project));
        assertFalse(new File(project, RuntimePackages.REPORT).exists());
    }
}
