package io.elasticore.base.extract;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * -Pmode=valid on the 2.x extractor writes nothing and answers with its exit
 * status.
 *
 * It wrote where a check of a missing file reaches: the package directory of
 * a file it only looked for, and the service test of testCode: true. A tree in
 * step never showed it - every directory and file was already there - and the
 * case that did is the one a check exists for. It also passed where it had
 * checked nothing: no model, a model file that does not parse.
 *
 * Each run is a JVM of its own, as the Gradle task starts it: the extractor
 * keeps the loaded model in static state, and its verdict is System.exit.
 */
public class ExtractorValidationTest {

    private static final String ENV = String.join("\n",
            "elasticore:",
            "  enable: true",
            "  config:",
            "    domainName: shop",
            "    framework: springboot",
            "    mode: jpa",
            "    j2ee: jakarta",
            "    testCode: \"true\"",
            "  namespace:",
            "    base: io.acme.shop",
            "");

    private static final String MODEL = String.join("\n",
            "entity:",
            "  Product:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      productId: long @id @sequence",
            "      name:      string(100)!",
            "      price:     long",
            "");

    @TempDir
    Path dir;

    /** Where a file taken away waits, inside the project and outside every snapshot. */
    private static final String ASIDE = ".aside";

    /** What the last run printed. */
    private String log = "";

    @Test
    public void aCheckOfMissingFilesWritesNoneOfThem() throws Exception {
        model(MODEL);
        assertEquals(0, extract(null), log);

        // What a check of missing files used to write: the DTO's package
        // directory and the service test.
        File dto = find("src/main/java", "ProductDTO.java");
        File test = find("src/test/java", "ProductServiceTest.java");
        Path aside = Files.createDirectories(dir.resolve(ASIDE));
        Files.move(dto.getParentFile().toPath(), aside.resolve("dto"), StandardCopyOption.ATOMIC_MOVE);
        Files.move(test.toPath(), aside.resolve("test.java"));

        Map<String, String> before = snapshot();
        assertEquals(1, extract("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree:\n" + log);
        assertFalse(dto.getParentFile().exists(), "validation made a directory");
        assertFalse(test.exists(), "validation wrote the service test");
        // The service test is said like any other missing file.
        assertTrue(log.contains(test.getName()), log);
        assertTrue(log.contains("[VALID] Validation FAILED"), log);
    }

    @Test
    public void aTreeInStepPasses() throws Exception {
        model(MODEL);
        assertEquals(0, extract(null), log);
        Map<String, String> before = snapshot();
        assertEquals(0, extract("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree:\n" + log);
        assertTrue(log.contains("[VALID] Validation PASSED"), log);
    }

    @Test
    public void whitespacePassesAndAPreservationBlockFails() throws Exception {
        model(MODEL);
        assertEquals(0, extract(null), log);
        File dto = find("src/main/java", "ProductDTO.java");
        String original = new String(Files.readAllBytes(dto.toPath()), StandardCharsets.UTF_8);

        // The 2.x comparison reads a difference of whitespace or of line order
        // as none - unlike the rule engine's, which ignores only line endings.
        assertTrue(original.contains("private "), original);
        Files.write(dto.toPath(), original.replace("private ", "  private ").getBytes(StandardCharsets.UTF_8));
        assertEquals(0, extract("valid"), log);
        Files.write(dto.toPath(), swapFirstTwoFields(original).getBytes(StandardCharsets.UTF_8));
        assertEquals(0, extract("valid"), log);

        // And it fails a preservation block, which the rule engine passes.
        int last = original.lastIndexOf('}');
        Files.write(dto.toPath(), (original.substring(0, last)
                + "// !---------- mine\n    public String label() { return \"x\"; }\n// ------------!\n"
                + original.substring(last)).getBytes(StandardCharsets.UTF_8));
        assertEquals(1, extract("valid"), log);
        assertTrue(log.contains("(custom code block)"), log);
    }

    @Test
    public void aRunThatBreaksFails() throws Exception {
        model(MODEL);
        assertEquals(0, extract(null), log);
        // An env.yml that does not parse: whatever it stops, the check has
        // not compared the tree, and must not answer that it did.
        Files.write(dir.resolve("src/main/resources/blueprint/shop/env.yml"),
                "elasticore: [\n".getBytes(StandardCharsets.UTF_8));
        Map<String, String> before = snapshot();
        assertEquals(1, extract("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree:\n" + log);
        assertTrue(log.contains("[VALID] Validation FAILED: the run itself failed"), log);
    }

    /** The source with its first two field declarations in each other's place. */
    private static String swapFirstTwoFields(String source) {
        String[] lines = source.split("\n", -1);
        int first = -1;
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].trim().startsWith("private ")) continue;
            if (first < 0) {
                first = i;
                continue;
            }
            String held = lines[first];
            lines[first] = lines[i];
            lines[i] = held;
            return String.join("\n", lines);
        }
        throw new AssertionError("fewer than two fields in:\n" + source);
    }

    @Test
    public void aMultiModuleCheckWithNoModelFails() throws Exception {
        Path module = dir.resolve("mod-empty");
        Files.createDirectories(module.resolve("src/main/java"));
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid", false, "elasticore.modules=" + module), log);
        assertEquals(before, snapshot(), "validation wrote to the tree:\n" + log);
        assertTrue(log.contains("no model DSL found - nothing was checked"), log);
    }

    @Test
    public void aCheckWithNoModelFails() throws Exception {
        Files.createDirectories(dir.resolve("src/main/java"));
        Map<String, String> before = snapshot();
        assertEquals(1, extract("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree:\n" + log);
        assertTrue(log.contains("no model DSL found - nothing was checked"), log);
    }

    @Test
    public void aModelFileThatDoesNotParseFails() throws Exception {
        model(MODEL);
        assertEquals(0, extract(null), log);
        // An unclosed flow sequence: the file is skipped with a LOAD ERROR,
        // and its entities were compared with nothing.
        Files.write(dir.resolve("src/main/resources/blueprint/shop/model/broken.yml"),
                "entity: [Product\n".getBytes(StandardCharsets.UTF_8));
        Map<String, String> before = snapshot();
        assertEquals(1, extract("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree:\n" + log);
        assertTrue(log.contains("LOAD ERROR"), log);
        assertTrue(log.contains("model file(s) could not be read"), log);
    }

    @Test
    public void aCheckWithoutASourceRootStillWritesNothing() throws Exception {
        model(MODEL);
        assertEquals(0, extract(null), log);
        File dto = find("src/main/java", "ProductDTO.java");
        Path aside = Files.createDirectories(dir.resolve(ASIDE));
        Files.move(dto.toPath(), aside.resolve("dto.java"));

        // No argument: the extractor finds the source root itself. The check
        // used to take the generation branch here and write.
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid", false), log);
        assertEquals(before, snapshot(), "validation wrote to the tree:\n" + log);
        assertFalse(dto.exists(), "validation wrote the missing file");
    }

    // ---- the harness --------------------------------------------------

    private void model(String model) throws IOException {
        Path domain = dir.resolve("src/main/resources/blueprint/shop");
        Files.createDirectories(domain.resolve("model"));
        Files.write(domain.resolve("env.yml"), ENV.getBytes(StandardCharsets.UTF_8));
        Files.write(domain.resolve("model/entity.yml"), model.getBytes(StandardCharsets.UTF_8));
    }

    /** The extractor as the plugin starts it: the source root as its argument, -Pmode as given. */
    private int extract(String mode) throws Exception {
        return run(mode, true);
    }

    private int run(String mode, boolean sourceRoot, String... properties) throws Exception {
        List<String> command = new ArrayList<String>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Xmx256m");
        command.add("-Dfile.encoding=UTF-8");
        command.add("-Delasticore.base.path=" + dir);
        if (mode != null) command.add("-Dmode=" + mode);
        for (String p : properties) command.add("-D" + p);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(ModelExtractor.class.getName());
        if (sourceRoot) command.add(dir.resolve("src/main/java").toString());

        Path out = Files.createTempFile("extractor", ".log");
        Process p = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(out.toFile())
                .start();
        try {
            assertTrue(p.waitFor(120, TimeUnit.SECONDS), "the extractor did not finish");
        } finally {
            p.destroyForcibly();
        }
        log = new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
        Files.delete(out);
        System.out.print(log);
        return p.exitValue();
    }

    /** Every file by its content hash, and every directory, under the project. */
    private Map<String, String> snapshot() throws IOException {
        final Map<String, String> tree = new TreeMap<String, String>();
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                if (d.getFileName() != null && ASIDE.equals(d.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                tree.put(dir.relativize(d).toString().replace('\\', '/') + "/", "dir");
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                tree.put(dir.relativize(file).toString().replace('\\', '/'), sha256(Files.readAllBytes(file)));
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

    /** The one file of this name under a source root. */
    private File find(String root, final String fileName) throws IOException {
        final List<File> found = new ArrayList<File>();
        Files.walkFileTree(dir.resolve(root), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().equals(fileName)) found.add(file.toFile());
                return FileVisitResult.CONTINUE;
            }
        });
        assertEquals(1, found.size(), "expected one " + fileName + " under " + root + ", found " + found + "\n" + log);
        return found.get(0);
    }
}
