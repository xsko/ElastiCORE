package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
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
 * A DTO extending a DTO of another domain names its parent by the simple
 * name, so the parent has to be imported. The clean output builds its import
 * list from what the class uses and counted the field types only; the
 * 2.x-compatible output gets there with a wildcard of the parent's package.
 * A parent of the same domain is in the same package and is not imported.
 */
public class DtoParentImportTest {

    private static final String AUTH = String.join("\n",
            "dto:",
            "  SignInRequest:",
            "    meta: dto",
            "    fields:",
            "      loginId:  string(50)",
            "      password: string(100)",
            "");

    private static final String APP = String.join("\n",
            "dto:",
            "  AppSignInRequest:",
            "    meta: dto @extend(SignInRequest)",
            "    fields:",
            "      deviceId: string(100)",
            "  AppDevice:",
            "    meta: dto",
            "    fields:",
            "      deviceId: string(100)",
            "  AppTablet:",
            "    meta: dto @extend(AppDevice)",
            "    fields:",
            "      screen: string(20)",
            "");

    private static final List<File> projects = new ArrayList<File>();

    private static File java;
    private static File kotlin;

    @BeforeAll
    static void generate() throws Exception {
        java = run("elcore-dtoparent-java", "java");
        kotlin = run("elcore-dtoparent-kt", "kotlin");
    }

    @AfterAll
    static void clean() throws Exception {
        java.lang.reflect.Field tmp = io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        for (File project : projects) delete(project);
    }

    @Test
    public void theCleanOutputImportsAParentOfAnotherDomain() throws Exception {
        String dto = found(java, "AppSignInRequest.java");
        assertTrue(dto.contains("extends SignInRequest"), dto);
        assertTrue(dto.contains("import io.acme.auth.dto.SignInRequest;"), dto);
    }

    @Test
    public void kotlinImportsAParentOfAnotherDomain() throws Exception {
        String dto = found(kotlin, "AppSignInRequest.kt");
        assertTrue(dto.contains("SignInRequest()"), dto);
        assertTrue(dto.contains("import io.acme.auth.dto.SignInRequest"), dto);
    }

    @Test
    public void aParentOfTheSameDomainIsNotImported() throws Exception {
        String dto = found(java, "AppTablet.java");
        assertTrue(dto.contains("extends AppDevice"), dto);
        assertFalse(dto.contains("import io.acme.app.dto.AppDevice"), dto);
        String kt = found(kotlin, "AppTablet.kt");
        assertFalse(kt.contains("import io.acme.app.dto.AppDevice"), kt);
    }

    private static File run(String name, String language) throws Exception {
        File project = Files.createTempDirectory(name).toFile();
        projects.add(project);
        domain(project, "auth", AUTH);
        domain(project, "app", APP);

        String[] keys = {"elasticore.base.path", "elasticore.architecture", "elasticore.language",
                "elasticore.rules.force", "elasticore.rules.v3", "elasticore.rules.ts"};
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "layered");
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty("elasticore.rules.v3", "true");
            assertEquals(0, RulesGenRunner.run(new String[0]), "the run failed: " + project);
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
        return project;
    }

    private static void domain(File project, String name, String model) throws IOException {
        File dir = new File(project, "src/main/resources/blueprint/" + name);
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(new File(dir, "env.yml").toPath(), String.join("\n",
                "elasticore:", "  enable: true", "  config:", "    domainName: " + name, "    framework: springboot",
                "    mode: jpa", "    j2ee: jakarta", "  namespace:", "    base: io.acme." + name,
                "    dto: io.acme." + name + ".dto", "")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
    }

    private static String found(File project, String fileName) throws IOException {
        List<File> hits = new ArrayList<File>();
        collect(project, fileName, hits);
        assertEquals(1, hits.size(), fileName + " in " + project + ": " + hits);
        return new String(Files.readAllBytes(hits.get(0).toPath()), StandardCharsets.UTF_8);
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
