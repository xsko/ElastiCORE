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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An entity named like a class of the service package - AccidentService, a
 * repair item of an accident, beside the service of Accident, which is also
 * AccidentService. The core service of the first lives in that package, and a
 * class of its own package wins over an on-demand import, in Java and in
 * Kotlin alike: with only "import ...entity.*" every AccidentService in
 * AccidentServiceCoreService meant the service, and nothing compiled. 2.x
 * imports the entity by its single name, which wins over the package; the
 * clean and Kotlin service skeletons now do as well.
 */
public class EntityShadowedInServicePackageTest {

    private static final String MODEL = String.join("\n",
            "entity:",
            "  Accident:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      accidentId: long @id @sequence",
            "      title:      string(50)",
            "",
            "  AccidentService:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      accidentServiceId: long @id @sequence",
            "      accident:          Accident @ManyToOne",
            "      cost:              long",
            "");

    private static final List<File> projects = new ArrayList<File>();

    private static File java;
    private static File kotlin;

    @BeforeAll
    static void generate() throws Exception {
        java = run("elcore-shadow-java", "java");
        kotlin = run("elcore-shadow-kt", "kotlin");
    }

    @AfterAll
    static void clean() throws Exception {
        java.lang.reflect.Field tmp = io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        for (File project : projects) delete(project);
    }

    @Test
    public void theCleanCoreServiceImportsItsEntityByName() throws Exception {
        String core = found(java, "AccidentServiceCoreService.java");
        assertTrue(core.contains("import io.acme.bidding.entity.AccidentService;"), core);
    }

    @Test
    public void theKotlinCoreServiceImportsItsEntityByName() throws Exception {
        String core = found(kotlin, "AccidentServiceCoreService.kt");
        assertTrue(core.contains("import io.acme.bidding.entity.AccidentService\n")
                || core.contains("import io.acme.bidding.entity.AccidentService\r\n"), core);
    }

    /** The extension service of Accident is the class that shadowed the entity. */
    @Test
    public void theShadowingServiceIsThere() throws Exception {
        String service = found(java, "AccidentService.java", "service");
        assertTrue(service.contains("class AccidentService extends AccidentCoreService"), service);
    }

    private static File run(String name, String language) throws Exception {
        File project = Files.createTempDirectory(name).toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/bidding");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(new File(dir, "env.yml").toPath(), String.join("\n",
                "elasticore:", "  enable: true", "  config:", "    domainName: bidding", "    framework: springboot",
                "    mode: jpa", "    j2ee: jakarta", "  namespace:", "    base: io.acme.bidding",
                "    entity: io.acme.bidding.entity", "    service: io.acme.bidding.service", "")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));

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

    private static String found(File project, String fileName) throws IOException {
        return found(project, fileName, null);
    }

    /** The one file of that name, under a directory of that name when one is given. */
    private static String found(File project, String fileName, String parentDir) throws IOException {
        List<File> hits = new ArrayList<File>();
        collect(project, fileName, hits);
        if (parentDir != null) {
            List<File> under = new ArrayList<File>();
            for (File f : hits) if (f.getParentFile().getName().equals(parentDir)) under.add(f);
            hits = under;
        }
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
