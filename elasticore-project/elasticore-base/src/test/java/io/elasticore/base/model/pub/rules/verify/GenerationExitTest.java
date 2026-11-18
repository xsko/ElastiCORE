package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.extract.ModelExtractor;
import io.elasticore.base.model.pub.rules.ModelCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a generation ends. The 2.x generator (ModelExtractor, what './gradlew elcore' and output = 'v2' run)
 * ended 0 after an exception it only printed - and threw a second one, hiding the first, when the
 * exception had no message - so a failed generation let the build go on. An enum value longer than its
 * field is a DSL warning there, which ends the run 1; elcore3 refuses it as a model check, -PlistRules too.
 */
public class GenerationExitTest {

    private static final String ENTITY = String.join(
            "\n",
            "entity:",
            "  PurchaseOrder:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      orderId: long @id @sequence",
            "      status: OrderStatus",
            "");

    private static String model(String cancelled) {
        return String.join(
                        "\n",
                        "enumeration:",
                        "  OrderStatus:",
                        "    meta: enum @db(code)",
                        "    fields:",
                        "      code: string(2)",
                        "      label: string(20)",
                        "    enum:",
                        "      PLACED: P,Placed",
                        "      CANCELLED: " + cancelled,
                        "")
                + "\n"
                + ENTITY;
    }

    private static final String TOO_LONG =
            "[shop] enum OrderStatus.CANCELLED: field 'code' value 'CXL' (length 3) exceeds max length 2";

    private static final String NO_VALUE = "[shop] enum OrderStatus.CANCELLED: field 'code' has no value on the"
            + " line, so the constant's name 'CANCELLED' (length 9) stands in and exceeds max length 2";

    private final List<File> projects = new ArrayList<File>();

    /** A model file that is not YAML: nothing it declares is generated, and the run says so and ends 1. */
    @Test
    public void aModelFileNotReadEndsNonZero() throws Exception {
        Result r = extractInItsOwnJvm(project("entity:\n  PurchaseOrder: [unclosed\n"));
        assertEquals(1, r.exit, r.log);
        assertTrue(r.log.contains("[ERROR] LOAD ERROR "), r.log);
        assertTrue(r.log.contains("model.yml - not read, nothing it declares was generated: "), r.log);
        assertTrue(r.log.contains("[ERROR] ElastiCORE generation failed"), r.log);
    }

    /**
     * A generation that throws ends non-zero with its cause: a field typed List<> fails the 2.x generator
     * ("toName is null"), which printed the stack trace and ended 0.
     */
    @Test
    public void aGenerationThatThrowsEndsNonZeroWithItsCause() throws Exception {
        Result r = extractInItsOwnJvm(project(model("C,Cancelled").replace("status: OrderStatus", "status: List<>")));
        assertEquals(1, r.exit, r.log);
        assertTrue(
                r.log.contains(
                        "[ERROR] ElastiCORE generation failed: java.lang.IllegalArgumentException: toName is null"),
                r.log);
    }

    /** A source that cannot be written is a generation that failed. */
    @Test
    public void aSourceNotWrittenEndsNonZero() throws Exception {
        File project = project(model("C,Cancelled"));
        // The source root is a file: no source can be written under it.
        Files.write(new File(project, "src/main/java").toPath(), new byte[0]);
        Result r = extractInItsOwnJvm(project);
        assertEquals(1, r.exit, r.log);
        assertTrue(
                r.log.contains(
                        "[ERROR] not written: io.acme.shop.entity.PurchaseOrder - java.io.FileNotFoundException"),
                r.log);
        assertTrue(r.log.contains("[ERROR] ElastiCORE generation failed"), r.log);
    }

    @Test
    public void aModelThatGeneratesEndsZero() throws Exception {
        Result r = extractInItsOwnJvm(project(model("C,Cancelled")));
        assertEquals(0, r.exit, r.log);
        assertFalse(r.log.contains("[ERROR]"), r.log);
    }

    /** 2.x is unchanged: the finding is a DSL warning, not a model check, and the warning ends the run 1. */
    @Test
    public void the2xGeneratorWarnsAndEndsOne() throws Exception {
        File project = project(model("CXL,Cancelled"));
        String log = extractInThisJvm(project);
        assertTrue(log.contains(TOO_LONG), log);
        assertTrue(ModelCheck.failures().isEmpty(), ModelCheck.failures().toString());

        Result r = extractInItsOwnJvm(project(model("CXL,Cancelled")));
        assertEquals(1, r.exit, r.log);
        assertTrue(r.log.contains(TOO_LONG), r.log);
        assertTrue(r.log.contains("[ERROR] DSL Validation: 1 warning(s) found."), r.log);
    }

    /** A value the line leaves out is said to be missing, not reported as the constant's name. */
    @Test
    public void aMissingValueIsSaidToBeMissing() throws Exception {
        String log = extractInThisJvm(project(model("")));
        assertTrue(log.contains(NO_VALUE), log);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(1, rulesRun(project(model("")), out, false));
        String said = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(said.contains(NO_VALUE + ". Give CANCELLED a value for 'code' of at most 2 characters."), said);
    }

    @Test
    public void elcore3NamesTheDomain() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(1, rulesRun(project(model("CXL,Cancelled")), out, false));
        String said = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(
                said.contains(TOO_LONG + ". Make field 'code' at least 3 characters long or shorten the value."), said);
    }

    /** -PlistRules writes nothing, and a model the check refuses still ends it 1. */
    @Test
    public void aListingOfARefusedModelEndsOne() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(1, rulesRun(project(model("CXL,Cancelled")), out, true));
        String said = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(said.contains("[elcore3] the model check failed (1)"), said);

        ModelCheck.reset();
        ByteArrayOutputStream fine = new ByteArrayOutputStream();
        assertEquals(0, rulesRun(project(model("C,Cancelled")), fine, true));
    }

    // ------------------------------------------------------------------

    private static final class Result {
        final int exit;
        final String log;

        Result(int exit, String log) {
            this.exit = exit;
            this.log = log;
        }
    }

    /** './gradlew elcore' runs the extractor's main in a JVM of its own; its static model state stays there. */
    private static Result extractInItsOwnJvm(File project) throws Exception {
        File log = new File(project, "gen.log");
        Process p = new ProcessBuilder(
                        new File(new File(System.getProperty("java.home"), "bin"), "java").getPath(),
                        "-Xmx256m",
                        "-Dfile.encoding=UTF-8",
                        "-Delasticore.base.path=" + project.getAbsolutePath(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        ModelExtractor.class.getName(),
                        new File(project, "src/main/java").getAbsolutePath())
                .directory(project)
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start();
        try {
            assertTrue(p.waitFor(120, TimeUnit.SECONDS), "generation did not finish");
        } finally {
            p.destroyForcibly().waitFor();
        }
        return new Result(p.exitValue(), new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8));
    }

    /** The 2.x generator in this JVM, its output captured. */
    private static String extractInThisJvm(File project) throws Exception {
        String basePath = System.getProperty("elasticore.base.path");
        System.setProperty("elasticore.base.path", project.getAbsolutePath());
        Object[] context = saveContext();
        PrintStream out = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            new ModelExtractor(new File(project, "src/main/resources/blueprint").getAbsolutePath())
                    .extract(new io.elasticore.base.extract.FileBasedSourceFileAccessFactory(
                            new File(project, "src/main/java").getAbsolutePath()));
        } finally {
            System.setOut(out);
            restoreContext(context);
            io.elasticore.base.util.ConsoleLog.clear();
            if (basePath == null) System.clearProperty("elasticore.base.path");
            else System.setProperty("elasticore.base.path", basePath);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        return log;
    }

    private static int rulesRun(File project, ByteArrayOutputStream bytes, boolean listing) {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v3",
            "elasticore.rules.v2",
            "elasticore.rules.ts",
            "elasticore.rules.list"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        PrintStream out = System.out;
        try {
            for (String k : keys) System.clearProperty(k);
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "layered");
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty("elasticore.rules.v3", "true");
            if (listing) System.setProperty("elasticore.rules.list", "true");
            return RulesGenRunner.run(new String[0]);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        } finally {
            System.setOut(out);
            out.print(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
    }

    private static final String[] CONTEXT = {"STATIC_DOMAIN_MAP", "context", "tmpCtx"};

    private static Object[] saveContext() throws Exception {
        Object[] saved = new Object[CONTEXT.length];
        for (int i = 0; i < CONTEXT.length; i++) {
            java.lang.reflect.Field f =
                    io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField(CONTEXT[i]);
            f.setAccessible(true);
            Object value = f.get(null);
            saved[i] = value instanceof java.util.Map
                    ? new java.util.HashMap<Object, Object>((java.util.Map<?, ?>) value)
                    : value;
        }
        return saved;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void restoreContext(Object[] saved) throws Exception {
        for (int i = 0; i < CONTEXT.length; i++) {
            java.lang.reflect.Field f =
                    io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField(CONTEXT[i]);
            f.setAccessible(true);
            if (saved[i] instanceof java.util.Map) {
                java.util.Map map = (java.util.Map) f.get(null);
                map.clear();
                map.putAll((java.util.Map) saved[i]);
            } else {
                f.set(null, saved[i]);
            }
        }
    }

    private File project(String model) throws IOException {
        File project = Files.createTempDirectory("elcore-exit").toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/shop");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: shop",
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    entity: io.acme.shop.entity",
                                "    dto: io.acme.shop.dto",
                                "    repository: io.acme.shop.repository",
                                "    service: io.acme.shop.service",
                                "    control: io.acme.shop.controller",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    @AfterEach
    void clean() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        ModelCheck.reset();
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
        for (File project : projects) delete(project);
    }

    private static void delete(File root) throws IOException {
        if (!root.exists()) return;
        Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {
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
}
