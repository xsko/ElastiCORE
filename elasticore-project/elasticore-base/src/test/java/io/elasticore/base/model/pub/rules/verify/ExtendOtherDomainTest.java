package io.elasticore.base.model.pub.rules.verify;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue 331: an @abstract parent kept in another domain (common) and extended by entities of others. By its simple
 * name the MyBatis entity said extends AuditEntity without importing it; by its domain-qualified name
 * (common:AuditEntity) MyBatis refused it as table inheritance. Both now name the parent simply and import it, in
 * MyBatis, MongoDB and JPA, and its columns are the row's.
 */
public class ExtendOtherDomainTest {

    private static final String COMMON = String.join(
            "\n",
            "entity:",
            "  AuditEntity:",
            "    meta: entity @abstract",
            "    fields:",
            "      createdBy: string(30) @updatable(false)",
            "");

    private static String agency(String parent) {
        return String.join(
                "\n",
                "entity:",
                "  UserAccount:",
                "    meta: entity @root @editable @expose(20) @extend(" + parent + ")",
                "    fields:",
                "      userId: string(20) @id",
                "      name:   string(50)",
                "");
    }

    @Test
    public void myBatisImportsTheParentByEitherName() throws Exception {
        for (String parent : new String[] {"AuditEntity", "common:AuditEntity"}) {
            File project = project("mybatis", agency(parent));
            String log = generate(project);
            assertFalse(log.contains("which is not @abstract"), parent + "\n" + log);
            String entity = found(project, "UserAccount.java");
            assertTrue(entity.contains("import com.acme.common.entity.AuditEntity;"), parent + "\n" + entity);
            assertTrue(entity.contains("class UserAccount extends AuditEntity implements"), parent + "\n" + entity);
            assertFalse(entity.contains("common:"), entity);
            // The parent stays in common: one class, no copy in agency.
            assertTrue(found(project, "AuditEntity.java").contains("package com.acme.common.entity;"), parent);
            // The parent's columns are the row's.
            String xml = found(project, "UserAccountRepository.xml");
            assertTrue(xml.contains("created_by"), parent + "\n" + xml);
        }
    }

    @Test
    public void jpaAndMongoNameItSimplyToo() throws Exception {
        for (String mode : new String[] {"jpa", "mongo"}) {
            File project = project(mode, agency("common:AuditEntity"));
            generate(project);
            String entity = found(project, "UserAccount.java");
            assertTrue(entity.contains("import com.acme.common.entity.AuditEntity;"), mode + "\n" + entity);
            assertTrue(entity.contains("class UserAccount extends AuditEntity implements"), mode + "\n" + entity);
            assertTrue(found(project, "AuditEntity.java").contains("package com.acme.common.entity;"), mode);
        }
    }

    // ---- the harness ----------------------------------------------------------------

    private final List<File> projects = new ArrayList<File>();

    private File project(String mode, String agencyModel) throws IOException {
        File project = Files.createTempDirectory("elcore-extend").toFile();
        projects.add(project);
        domain(project, "common", mode, COMMON);
        domain(project, "agency", mode, agencyModel);
        return project;
    }

    private static void domain(File project, String name, String mode, String model) throws IOException {
        File dir = new File(project, "src/main/resources/blueprint/" + name);
        assertTrue(new File(dir, "model").mkdirs());
        String env = String.join(
                "\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: " + name,
                "    framework: springboot",
                "    mode: " + mode,
                "    j2ee: jakarta",
                "  namespace:",
                "    entity: com.acme." + name + ".entity",
                "    dto: com.acme." + name + ".dto",
                "    repository: com.acme." + name + ".repository",
                "    service: com.acme." + name + ".service",
                "    control: com.acme." + name + ".controller",
                "");
        Files.write(new File(dir, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
    }

    private static String generate(File project) throws Exception {
        String[] keys = {
            "elasticore.base.path", "elasticore.architecture", "elasticore.language", "elasticore.rules.force"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        int code;
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.force", "true");
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            code = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        assertEquals(0, code, log);
        return log;
    }

    private static String found(File project, String fileName) throws IOException {
        List<File> hits = new ArrayList<File>();
        collect(project, fileName, hits);
        assertEquals(1, hits.size(), fileName + " in " + project + ": " + hits);
        return new String(Files.readAllBytes(hits.get(0).toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static void collect(File dir, String name, List<File> hits) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, name, hits);
            else if (f.getName().equals(name)) hits.add(f);
        }
    }

    @AfterEach
    void clean() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        io.elasticore.base.model.pub.rules.ModelCheck.reset();
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
