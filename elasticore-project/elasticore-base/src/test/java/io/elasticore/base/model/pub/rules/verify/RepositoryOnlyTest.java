package io.elasticore.base.model.pub.rules.verify;

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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code meta: entity @repository}: the repository and the domain helper's
 * getter, and nothing else - no service, no controller, no API. The 3.0 code
 * wrote a repository only for an entity with {@code @expose} or
 * {@code @service}, so code moved from 2.x - where every keyed entity had one -
 * calling {@code helper.getCode()} for an entity it reads did not compile;
 * {@code @expose} opened an API it did not want, {@code @service} wrote a
 * service it did not use.
 */
public class RepositoryOnlyTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Order:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      orderId: long @id @sequence",
            "      note: string(100)",
            "  Code:",
            "    meta: entity @repository -- a lookup table hand code reads",
            "    fields:",
            "      codeId: string(10) @id",
            "      label: string(50)",
            "  Plain:",
            "    meta: entity",
            "    fields:",
            "      plainId: long @id",
            "");

    private static final List<File> projects = new ArrayList<File>();
    private static File java;
    private static File kotlin;
    private static File mybatis;
    private static String mybatisLog;

    @BeforeAll
    static void generate() throws Exception {
        java = run("elcore-repo-only-java", "java", "jpa");
        kotlin = run("elcore-repo-only-kt", "kotlin", "jpa");
        mybatis = run("elcore-repo-only-mybatis", "java", "mybatis");
    }

    @AfterAll
    static void clean() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        for (File project : projects) delete(project);
    }

    @Test
    public void aRepositoryAndTheHelpersGetterAndNothingElse() throws Exception {
        String repository = found(java, "CodeRepository.java");
        assertTrue(repository.contains("interface CodeRepository"), repository);
        String helper = found(java, "ShopRepositoryHelper.java");
        assertTrue(helper.contains("CodeRepository code"), helper);
        assertTrue(helper.contains("OrderRepository order"), helper);
        assertFalse(helper.contains("PlainRepository"), helper);
        assertEquals(0, count(java, "CodeCoreService.java"));
        assertEquals(0, count(java, "CodeService.java"));
        assertEquals(0, count(java, "CodeController.java"));
        // An entity with neither keeps having none.
        assertEquals(0, count(java, "PlainRepository.java"));
    }

    @Test
    public void inKotlinToo() throws Exception {
        String repository = found(kotlin, "CodeRepository.kt");
        assertTrue(repository.contains("interface CodeRepository"), repository);
        String helper = found(kotlin, "ShopRepositoryHelper.kt");
        assertTrue(helper.contains("CodeRepository"), helper);
        assertEquals(0, count(kotlin, "CodeCoreService.kt"));
        assertEquals(0, count(kotlin, "CodeController.kt"));
    }

    @Test
    public void overMyBatisItIsSaidNotDoneYet() throws Exception {
        assertTrue(mybatisLog.contains("@repository is not written under mode: mybatis yet"), mybatisLog);
        assertEquals(0, count(mybatis, "CodeRepository.java"));
        String helper = found(mybatis, "ShopRepositoryHelper.java");
        assertFalse(helper.contains("CodeRepository"), helper);
    }

    private static File run(String name, String language, String mode) throws Exception {
        File project = Files.createTempDirectory(name).toFile();
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
                                "    mode: " + mode,
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    base: io.acme.shop",
                                "    entity: io.acme.shop.entity",
                                "    dto: io.acme.shop.dto",
                                "    repository: io.acme.shop.repository",
                                "    service: io.acme.shop.service",
                                "    control: io.acme.shop.control",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));

        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v3",
            "elasticore.rules.v2",
            "elasticore.rules.ts"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        PrintStream out = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "layered");
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty("elasticore.rules.v3", "true");
            System.setOut(new PrintStream(captured, true, "UTF-8"));
            assertEquals(0, RulesGenRunner.run(new String[0]), "the run failed: " + project);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
        if ("mybatis".equals(mode)) mybatisLog = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        return project;
    }

    private static String found(File project, String fileName) throws IOException {
        List<File> hits = new ArrayList<File>();
        collect(project, fileName, hits);
        assertEquals(1, hits.size(), fileName + " in " + project + ": " + hits);
        return new String(Files.readAllBytes(hits.get(0).toPath()), StandardCharsets.UTF_8);
    }

    private static int count(File project, String fileName) {
        List<File> hits = new ArrayList<File>();
        collect(project, fileName, hits);
        return hits.size();
    }

    private static void collect(File dir, String fileName, List<File> hits) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collect(child, fileName, hits);
            else if (child.getName().equals(fileName)) hits.add(child);
        }
    }

    private static void delete(File project) throws IOException {
        if (project == null || !project.exists()) return;
        Files.walkFileTree(project.toPath(), new SimpleFileVisitor<Path>() {
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
