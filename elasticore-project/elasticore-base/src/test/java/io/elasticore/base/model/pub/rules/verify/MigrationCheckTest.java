package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.extract.FileBasedSourceFileAccessFactory;
import io.elasticore.base.extract.ModelExtractor;
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
 * class of the developer's that calls helper.getCode() for it. Each test runs
 * the whole scenario itself.
 */
public class MigrationCheckTest {

    private static final String PKG = "com/acme/shop";

    private File project;

    /** A project the 2.x generator wrote, the developer's code in it, and the check's output. */
    private String check() throws Exception {
        project = Files.createTempDirectory("elcore-migration-check").toFile();
        File dir = new File(project, "src/main/resources/blueprint/shop");
        assertTrue(new File(dir, "model").mkdirs());
        write(
                "src/main/resources/blueprint/shop/env.yml",
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
                        ""));
        write(
                "src/main/resources/blueprint/shop/model/model.yml",
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
                        ""));

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
        // Stamped as the 2.2 generator stamps (the hash does not read the version): a project 2.x generated.
        restamp(new File(project, "src/main/java"));

        // A class of the developer's that reads codes through the helper,
        write(
                "src/main/java/" + PKG + "/CodeReader.java",
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
                        ""));
        // one that calls a getCode() of something else - no use of the repository,
        write(
                "src/main/java/" + PKG + "/Labels.java",
                String.join(
                        "\n",
                        "package com.acme.shop;",
                        "",
                        "public class Labels {",
                        "    public String of(com.acme.shop.entity.Code c) { return c.getCodeId() + c.getCode(); }",
                        "}",
                        ""));
        // and two generated files the developer wrote more into.
        appendMethod("src/main/java/" + PKG + "/service/PurchaseOrderCoreService.java");
        appendMethod("src/main/java/" + PKG + "/dto/PurchaseOrderDTO.java");

        return run(true);
    }

    private static void restamp(File dir) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                restamp(f);
            } else if (f.getName().endsWith(".java")) {
                String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                int eol = text.indexOf('\n');
                if (text.startsWith("//ecd:") && eol > 0) {
                    String stamp = text.substring(0, eol).replace("_V3.0", "_V2.2");
                    Files.write(f.toPath(), (stamp + text.substring(eol)).getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    private void appendMethod(String path) throws IOException {
        File f = new File(project, path);
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        int end = text.lastIndexOf('}');
        Files.write(
                f.toPath(),
                (text.substring(0, end)
                                + "    /** Written by hand. */\n    public String ping() {\n        return \"pong\";\n    }\n}\n")
                        .getBytes(StandardCharsets.UTF_8));
    }

    private void write(String path, String text) throws IOException {
        File f = new File(project, path);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    /** The migration check (true) or the 3.0 build itself (false), and what it printed. */
    private String run(boolean checking) throws Exception {
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
        int status;
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            if (checking) {
                // The build still keeps 2.x's code: the check runs the 3.0 build all the same.
                System.setProperty("elasticore.rules.v2", "true");
                System.setProperty(RulesGenRunner.PROP_MIGRATION_CHECK, "true");
            } else {
                System.setProperty("elasticore.rules.v3", "true");
            }
            System.setOut(new PrintStream(
                    captured, true, java.nio.charset.Charset.defaultCharset().name()));
            status = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
        String printed = new String(captured.toByteArray(), java.nio.charset.Charset.defaultCharset());
        assertEquals(0, status, printed);
        return printed;
    }

    private String report() throws IOException {
        return new String(
                Files.readAllBytes(new File(project, "build/elasticore/migration-check.md").toPath()),
                StandardCharsets.UTF_8);
    }

    @AfterEach
    public void clean() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        delete(project);
    }

    @Test
    public void itWritesNothingButTheReport() throws Exception {
        Map<String, byte[]> before = null;
        String output;
        // The snapshot is of the project as the check finds it: taken right before it runs.
        project = null;
        String first = check();
        assertTrue(first.contains("[elcore3] migration check (nothing was written):"), first);
        before = snapshot(project);
        output = run(true);
        Map<String, byte[]> after = snapshot(project);
        after.keySet().removeIf(k -> k.startsWith("build/elasticore/"));
        before.keySet().removeIf(k -> k.startsWith("build/elasticore/"));
        assertEquals(before.keySet(), after.keySet(), "files added or removed: " + output);
        for (Map.Entry<String, byte[]> en : before.entrySet()) {
            assertTrue(java.util.Arrays.equals(en.getValue(), after.get(en.getKey())), en.getKey() + " was changed");
        }
        assertFalse(new File(project, ".elasticore").exists(), "no cache: the project has not moved");
        assertTrue(output.contains("[elcore3] upgrade   : the code 2.x generates"), "read as the bases: " + output);
    }

    @Test
    public void theReportNamesTheRepositoryYourCodeStillCallsAndNothingElse() throws Exception {
        String output = check();
        String report = report();
        // The build rewrites the helper without getCode() and no longer writes CodeRepository, which the 2.2
        // stamp keeps on disk; CodeReader, which calls helper.getCode(), would not compile.
        assertTrue(
                report.contains("`src/main/java/com/acme/shop/repository/CodeRepository.java` (left as it is, no"
                        + " longer written) - used by `src/main/java/com/acme/shop/CodeReader.java`\n"),
                report + "\n---\n" + output);
        assertFalse(report.contains("Labels.java"), "a getCode() not on a helper is no use: " + report);
        assertTrue(report.contains("`@repository`"), report);
        assertTrue(report.contains("- removed or no longer written, and used by your code: 1"), report);
        assertFalse(
                output.contains("ShopRepositoryHelper.java still uses it"),
                "the helper the build rewrites is read as it would be written: " + output);
        assertTrue(output.contains("removed or no longer written and used by your code: 1"), output);
        // The two edited files merge; the imports only the generated code used go with it, not listed.
        assertTrue(report.contains("- MERGED: 2"), report);
        assertTrue(report.contains("- merged files that would lose an import: 0"), report);
    }

    /** Each file's status as the check printed it equals what the build then prints. */
    @Test
    public void theBuildDoesWhatTheCheckSaid() throws Exception {
        String checked = check();
        String built = run(false);
        Map<String, String> fromCheck = statuses(checked);
        Map<String, String> fromBuild = statuses(built);
        assertEquals(fromBuild, fromCheck, "check:\n" + checked + "\nbuild:\n" + built);
        // A file whose 2.x base the 3.0 build reproduces is merged with the developer's method,
        assertEquals("MERGED(your", fromCheck.get("src/main/java/com/acme/shop/dto/PurchaseOrderDTO.java"), checked);
        // and so is the service: nothing is left as the 2.x code.
        assertEquals(
                "MERGED(your",
                fromCheck.get("src/main/java/com/acme/shop/service/PurchaseOrderCoreService.java"),
                checked);
        String left = report().substring(report().indexOf("## Left as the 2.x code"));
        assertTrue(
                left.startsWith("## Left as the 2.x code - edited, and their base cannot be confirmed\n\nNone.\n"),
                left);
        assertTrue(
                new File(project, "src/main/java/" + PKG + "/repository/CodeRepository.java").exists(),
                "the build leaves the 2.2 file, as the check said");
        String helper = new String(
                Files.readAllBytes(
                        new File(project, "src/main/java/" + PKG + "/repository/ShopRepositoryHelper.java").toPath()),
                StandardCharsets.UTF_8);
        assertFalse(helper.contains("CodeRepository"), "and the helper no longer holds it: " + helper);
        String merged = new String(
                Files.readAllBytes(new File(project, "src/main/java/" + PKG + "/dto/PurchaseOrderDTO.java").toPath()),
                StandardCharsets.UTF_8);
        assertTrue(merged.contains("public String ping()"), merged);
    }

    private static Map<String, String> statuses(String printed) {
        java.util.regex.Pattern status =
                java.util.regex.Pattern.compile("(?m)^\\s+(?:\\S+\\s+)?(src/\\S+)\\s{2}(\\S+)");
        Map<String, String> out = new TreeMap<String, String>();
        java.util.regex.Matcher m = status.matcher(printed);
        while (m.find()) out.put(m.group(1), m.group(2));
        return out;
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
