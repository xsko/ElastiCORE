package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * -Pmode=valid on the rule engine: everything generated in memory, compared
 * with the tree, and nothing written.
 *
 * The mode used to reach this engine and be read by nothing in it, so a
 * check asked to write nothing regenerated the project. That is why every
 * run here is first held to the tree byte for byte, directories included: a
 * check that writes is worse than none, because it is trusted - a CI gate
 * built on it passes by repairing what it was meant to find.
 */
public class ValidationModeTest {

    private File project;
    /** What the last run printed. */
    private String log = "";

    private static final String ENV = String.join("\n",
            "elasticore:",
            "  enable: true",
            "  config:",
            "    domainName: shop",
            "    framework: springboot",
            "    mode: jpa",
            "    j2ee: jakarta",
            "  namespace:",
            "    base: io.acme.shop",
            "");

    private static final String PRODUCT = String.join("\n",
            "  Product:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      productId: long @id @sequence",
            "      name:      string(100)!",
            "      price:     long",
            "");

    private static final String MODEL = "entity:\n" + PRODUCT;

    @BeforeEach
    void newProject() throws IOException {
        project = Files.createTempDirectory("elcore-valid").toFile();
    }

    @Test
    @DisplayName("a tree in step with the model passes, and is left byte for byte")
    public void aTreeInStepPasses() throws Exception {
        model(MODEL);
        assertEquals(0, run(null), log);

        // A guide is copied by generation and must not be by a check.
        File guide = new File(project, "src/main/resources/blueprint/elasticore-dsl-guide.md");
        assertTrue(guide.isFile(), "generation deployed no guide to remove: " + guide);
        assertTrue(guide.delete());

        Map<String, String> before = snapshot();
        assertEquals(0, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertFalse(guide.exists(), "validation copied the guides");
        assertTrue(log.contains("[VALID] Validation PASSED"), log);
    }

    @Test
    @DisplayName("a model changed since generation fails, and nothing is written")
    public void aChangedModelFails() throws Exception {
        model(MODEL);
        assertEquals(0, run(null), log);

        model(MODEL.replace("      price:     long\n", "      price:     long\n      stock:     int\n"));
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        // schema.yml too: it would change with the model, and is a side-car.
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertTrue(log.contains("  WOULD WRITE"), log);
        assertTrue(log.contains("[VALID] Validation FAILED"), log);

        // Generation is what brings the tree back in step.
        assertEquals(0, run(null), log);
        assertEquals(0, run("valid"), log);
    }

    @Test
    @DisplayName("generated files that are missing fail, and neither they nor their directory are written")
    public void missingFilesFail() throws Exception {
        model(MODEL);
        assertEquals(0, run(null), log);

        File dtoDir = find("ProductDTO.java").getParentFile();
        deleteTree(dtoDir.toPath());
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertFalse(dtoDir.exists(), "validation made a directory");
        assertTrue(log.contains("WOULD WRITE(new)"), log);
    }

    @Test
    @DisplayName("a managed file edited by hand fails - generation would leave it as it is")
    public void aHandEditedManagedFileFails() throws Exception {
        model(MODEL);
        assertEquals(0, run(null), log);

        File dto = find("ProductDTO.java");
        Files.write(dto.toPath(), (read(dto) + "// mine\n").getBytes(StandardCharsets.UTF_8));
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertTrue(log.contains("USER-MODIFIED(differs from the DSL"), log);
    }

    @Test
    @DisplayName("a file that is the developer's, edited, passes")
    public void theDevelopersOwnFilePasses() throws Exception {
        model(MODEL);
        assertEquals(0, run(null), log);

        File service = find("ProductService.java");
        Files.write(service.toPath(), (read(service) + "// mine\n").getBytes(StandardCharsets.UTF_8));
        assertEquals(0, run("valid"), log);
        assertTrue(log.contains("SKIP(user-owned)"), log);
    }

    @Test
    @DisplayName("a generated file the model no longer produces fails, and is not removed")
    public void aFileTheModelDroppedFails() throws Exception {
        model(MODEL + String.join("\n",
                "  Tag:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      tagId: long @id @sequence",
                "      label: string(50)",
                ""));
        assertEquals(0, run(null), log);
        File tagDto = find("TagDTO.java");

        model(MODEL);
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertTrue(tagDto.isFile(), "validation removed a file");
        assertTrue(log.contains("WOULD REMOVE(the DSL no longer produces it)"), log);
    }

    @Test
    @DisplayName("a file written once and never edited, which the model has changed, fails - and is not rewritten")
    public void anUneditedOwnedFileFails() throws Exception {
        model(MODEL.replace("@root @expose(20)", "@root @expose(20) @editable"));
        assertEquals(0, run(null), log);
        File controller = find("ProductController.java");

        // Without @editable the controller loses its write endpoints; nobody
        // edited it, so generation would write it again.
        model(MODEL);
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertTrue(log.contains("WOULD REGENERATE(owned, never edited)"), log);
        assertTrue(controller.isFile());
    }

    @Test
    @DisplayName("a DSL warning fails the check, as it fails the 2.x one, although generation goes on")
    public void aDslWarningFails() throws Exception {
        model(MODEL.replace("@root @expose(20)", "@root @expose(20) @sort(nope+)"));
        assertEquals(0, run(null), log);
        assertTrue(log.contains("@sort(nope+): nope is not a field of Product"), "the model was meant to draw a warning: " + log);

        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertTrue(log.contains("[VALID] DSL Validation: "), log);
    }

    @Test
    @DisplayName("-Pmode=clean is refused, and nothing is written or removed")
    public void cleanIsRefused() throws Exception {
        model(MODEL);
        assertEquals(0, run(null), log);

        Map<String, String> before = snapshot();
        assertEquals(1, run("clean"), log);
        assertEquals(before, snapshot(), "-Pmode=clean touched the tree");
        assertTrue(log.contains("-Pmode=clean is a mode of the 2.x generator"), log);
    }

    // ---- the harness --------------------------------------------------

    private void model(String model) throws IOException {
        File dir = new File(project, "src/main/resources/blueprint/shop");
        assertTrue(new File(dir, "model").isDirectory() || new File(dir, "model").mkdirs());
        Files.write(new File(dir, "env.yml").toPath(), ENV.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/entity.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
    }

    /** One run of the engine, as the plugin starts it, with -Pmode as given (null for none). */
    private int run(String mode) {
        String[] keys = {"elasticore.base.path", "elasticore.architecture", "elasticore.language", "mode"};
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        java.io.PrintStream out = System.out;
        java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "hexagonal");
            System.setProperty("elasticore.language", "java");
            if (mode == null) System.clearProperty("mode");
            else System.setProperty("mode", mode);
            System.setOut(new java.io.PrintStream(captured, true, "UTF-8"));
            return RulesGenRunner.run(new String[0]);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
            log = new String(captured.toByteArray(), StandardCharsets.UTF_8);
            out.print(log);
        }
    }

    /** Every file by its content hash, and every directory, under the project. */
    private Map<String, String> snapshot() throws IOException {
        final Map<String, String> tree = new TreeMap<String, String>();
        final Path root = project.toPath();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                tree.put(root.relativize(dir).toString().replace('\\', '/') + "/", "dir");
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                tree.put(root.relativize(file).toString().replace('\\', '/'), sha256(Files.readAllBytes(file)));
                return FileVisitResult.CONTINUE;
            }
        });
        return tree;
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The one generated source of this name. */
    private File find(final String fileName) throws IOException {
        final List<File> found = new ArrayList<File>();
        Files.walkFileTree(new File(project, "src/main/java").toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().equals(fileName)) found.add(file.toFile());
                return FileVisitResult.CONTINUE;
            }
        });
        assertEquals(1, found.size(), "expected one " + fileName + ", found " + found + "\n" + log);
        return found.get(0);
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static void deleteTree(Path top) throws IOException {
        Files.walkFileTree(top, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @AfterEach
    void removeProject() throws IOException {
        if (project != null && project.isDirectory()) deleteTree(project.toPath());
    }

    @AfterAll
    static void reset() throws Exception {
        java.lang.reflect.Field tmp = io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
    }
}
