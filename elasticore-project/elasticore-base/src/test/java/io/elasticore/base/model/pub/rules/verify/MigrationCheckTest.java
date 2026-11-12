package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.extract.FileBasedSourceFileAccessFactory;
import io.elasticore.base.extract.ModelExtractor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * elcoreMigrationCheck on a project the 2.x generator wrote, with code of the
 * developer's in it: the first 3.0 build is run with nothing written, and the
 * report says what it would do - the repository 2.x wrote for an entity with
 * neither @expose nor @service, which the 3.0 code does not write, and the
 * class of the developer's that calls helper.getCode() for it.
 */
public class MigrationCheckTest {

    private static final String PKG = "com/acme/shop";

    private static File project;
    private static String output;
    private static Map<String, byte[]> before;
    private static Map<String, byte[]> after;
    private static int status;

    @BeforeAll
    static void checkAProject2xGenerated() throws Exception {
        project = Files.createTempDirectory("elcore-migration-check").toFile();
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
                                "    entity: com.acme.shop.entity",
                                "    enumeration: com.acme.shop.entity",
                                "    dto: com.acme.shop.dto",
                                "    repository: com.acme.shop.repository",
                                "    service: com.acme.shop.service",
                                "    control: com.acme.shop.control",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(
                new File(dir, "model/model.yml").toPath(),
                String.join(
                                "\n",
                                "entity:",
                                "  PurchaseOrder:",
                                "    meta: entity @expose(20)",
                                "    fields:",
                                "      orderId: long @id @sequence",
                                "      note: string(100)",
                                "  Code:",
                                "    meta: entity",
                                "    fields:",
                                "      codeId: string(10) @id",
                                "      label: string(50)",
                                "")
                        .getBytes(StandardCharsets.UTF_8));

        // The 2.x generator writes the project, as it did before 3.0.
        String basePath = System.getProperty("elasticore.base.path");
        System.setProperty("elasticore.base.path", project.getAbsolutePath());
        Object[] context = saveContext();
        try {
            new ModelExtractor(new File(project, "src/main/resources/blueprint").getAbsolutePath())
                    .extract(
                            new FileBasedSourceFileAccessFactory(new File(project, "src/main/java").getAbsolutePath()));
        } finally {
            restoreContext(context);
            if (basePath == null) System.clearProperty("elasticore.base.path");
            else System.setProperty("elasticore.base.path", basePath);
        }
        assertTrue(
                new File(project, "src/main/java/" + PKG + "/repository/CodeRepository.java").isFile(),
                "2.x writes a repository for every entity with a key");

        // A class of the developer's that reads codes through the helper.
        Files.write(
                new File(project, "src/main/java/" + PKG + "/CodeReader.java").toPath(),
                String.join(
                                "\n",
                                "package com.acme.shop;",
                                "",
                                "import com.acme.shop.repository.ShopRepositoryHelper;",
                                "",
                                "public class CodeReader {",
                                "    private final ShopRepositoryHelper helper;",
                                "    public CodeReader(ShopRepositoryHelper helper) { this.helper = helper; }",
                                "    public long count() { return helper.getCode().count(); }",
                                "}",
                                "")
                        .getBytes(StandardCharsets.UTF_8));

        // And a generated service the developer wrote more into: the build merges it.
        File controller = new File(project, "src/main/java/" + PKG + "/service/PurchaseOrderCoreService.java");
        String text = new String(Files.readAllBytes(controller.toPath()), StandardCharsets.UTF_8);
        int end = text.lastIndexOf('}');
        Files.write(
                controller.toPath(),
                (text.substring(0, end)
                                + "    /** Written by hand. */\n    public String ping() {\n        return \"pong\";\n    }\n}\n")
                        .getBytes(StandardCharsets.UTF_8));

        before = snapshot(project);
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v3",
            "elasticore.rules.v2",
            "elasticore.rules.ts",
            RulesGenRunner.PROP_MIGRATION_CHECK
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        PrintStream out = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            // The build still keeps 2.x's code: the check runs the 3.0 build all the same.
            System.setProperty("elasticore.rules.v2", "true");
            System.setProperty(RulesGenRunner.PROP_MIGRATION_CHECK, "true");
            System.setOut(new PrintStream(captured, true, "UTF-8"));
            status = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
        output = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        after = snapshot(project);
        cacheAfterCheck = new File(project, ".elasticore").exists();

        // Then the build itself, which the check foretold.
        ByteArrayOutputStream built = new ByteArrayOutputStream();
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.rules.v3", "true");
            System.setOut(new PrintStream(built, true, "UTF-8"));
            buildStatus = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
        buildOutput = new String(built.toByteArray(), StandardCharsets.UTF_8);
    }

    private static boolean cacheAfterCheck;
    private static String buildOutput;
    private static int buildStatus;

    /** Each file's status as the check printed it equals what the build printed. */
    @Test
    public void theBuildDoesWhatTheCheckSaid() {
        assertEquals(0, buildStatus, buildOutput);
        java.util.regex.Pattern status = java.util.regex.Pattern.compile("(?m)^\\s+\\S+\\s+(src/\\S+)\\s{2}(\\S+)");
        Map<String, String> checked = new TreeMap<String, String>();
        Map<String, String> builtStatus = new TreeMap<String, String>();
        java.util.regex.Matcher m = status.matcher(output);
        while (m.find()) checked.put(m.group(1), m.group(2));
        m = status.matcher(buildOutput);
        while (m.find()) builtStatus.put(m.group(1), m.group(2));
        assertEquals(builtStatus, checked, "check:\n" + output + "\nbuild:\n" + buildOutput);
        // Edited, and its 2.x base not reproduced byte for byte: the build leaves it, as the check said.
        assertEquals(
                "SKIP(user-modified;",
                checked.get("src/main/java/com/acme/shop/service/PurchaseOrderCoreService.java"),
                output);
        assertFalse(
                new File(project, "src/main/java/" + PKG + "/repository/CodeRepository.java").exists(),
                "the build removed what the check said it would");
    }

    @AfterAll
    static void clean() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        delete(project);
    }

    @Test
    public void itWritesNothingButTheReport() {
        assertEquals(0, status, output);
        assertTrue(output.contains("[elcore3] migration check (nothing was written):"), output);
        Map<String, byte[]> changed = new TreeMap<String, byte[]>(after);
        changed.keySet().removeIf(k -> k.startsWith("build/elasticore/"));
        assertEquals(before.keySet(), changed.keySet(), "files added or removed: " + output);
        for (Map.Entry<String, byte[]> en : before.entrySet()) {
            assertTrue(java.util.Arrays.equals(en.getValue(), changed.get(en.getKey())), en.getKey() + " was changed");
        }
        assertFalse(cacheAfterCheck, "no cache: the project has not moved");
    }

    @Test
    public void theReportNamesTheRepositoryTheCodeOfYoursStillCalls() throws Exception {
        String report = new String(
                Files.readAllBytes(new File(project, "build/elasticore/migration-check.md").toPath()),
                StandardCharsets.UTF_8);
        // The build rewrites the helper without getCode() and removes CodeRepository (nothing generated
        // names it any more); CodeReader, which calls helper.getCode(), would not compile.
        assertTrue(
                report.contains("`src/main/java/com/acme/shop/repository/CodeRepository.java` (removed) - used by"
                        + " `src/main/java/com/acme/shop/CodeReader.java`"),
                report + "\n---\n" + output);
        assertTrue(report.contains("`@repository`"), report);
        assertTrue(report.contains("- removed or no longer written, and used by your code: 1"), report);
        assertFalse(
                output.contains("ShopRepositoryHelper.java still uses it"),
                "the helper the build rewrites is read as it would be written: " + output);
        // The service the developer edited stays the 2.x code: said, with what to do.
        String left = report.substring(report.indexOf("## Left as the 2.x code"));
        assertTrue(left.startsWith("## Left as the 2.x code - edited, and their base cannot be confirmed\n"), left);
        assertTrue(left.contains("- `src/main/java/com/acme/shop/service/PurchaseOrderCoreService.java`"), left);
    }

    private static String firstLine(File f) throws IOException {
        String t = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        int eol = t.indexOf('\n');
        return eol < 0 ? t : t.substring(0, eol);
    }

    private static Map<String, byte[]> snapshot(File root) throws IOException {
        final Map<String, byte[]> files = new TreeMap<String, byte[]>();
        final Path base = root.toPath();
        Files.walkFileTree(base, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                files.put(base.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private static final String[] CONTEXT = {"STATIC_DOMAIN_MAP", "context", "tmpCtx"};

    private static Object[] saveContext() throws Exception {
        Object[] saved = new Object[CONTEXT.length];
        for (int i = 0; i < CONTEXT.length; i++) {
            java.lang.reflect.Field f =
                    io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField(CONTEXT[i]);
            f.setAccessible(true);
            Object value = f.get(null);
            saved[i] = value instanceof Map ? new java.util.HashMap<Object, Object>((Map<?, ?>) value) : value;
        }
        return saved;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void restoreContext(Object[] saved) throws Exception {
        for (int i = 0; i < CONTEXT.length; i++) {
            java.lang.reflect.Field f =
                    io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField(CONTEXT[i]);
            f.setAccessible(true);
            if (saved[i] instanceof Map) {
                Map map = (Map) f.get(null);
                map.clear();
                map.putAll((Map) saved[i]);
            } else {
                f.set(null, saved[i]);
            }
        }
    }

    private static void delete(File f) throws IOException {
        if (f == null || !f.exists()) return;
        Files.walkFileTree(f.toPath(), new SimpleFileVisitor<Path>() {
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
