package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.pub.rules.schema.DdlConfig;
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
 * An enum field's column is as long as the enum's {@code @db} code says. 2.x
 * keeps that length apart and writes it only when nothing else is said about
 * the column, so a required, unique or not updatable enum got the default
 * length 255. The 3.0 code gives the column its length whatever else is said;
 * the compatible output keeps 2.x's.
 */
public class EnumColumnLengthTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Board:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      boardId: long @id @sequence",
            "      boardType: BoardType!",
            "      readLevel: BoardType @unique",
            "      writeLevel: BoardType @updatable(false)",
            "      viewLevel: BoardType",
            "      label: string(30)!",
            "enumeration:",
            "  BoardType:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(3) @code",
            "      name: string(20)",
            "    enum:",
            "      NOTICE: NTC,Notice",
            "      FREE: FRE,Free",
            "");

    private static final List<File> projects = new ArrayList<File>();

    private static File compat;
    private static File clean;
    private static File kotlin;

    @BeforeAll
    static void generate() throws Exception {
        compat = run("elcore-enum-length-compat", "elasticore.rules.v2", "java");
        clean = run("elcore-enum-length-v3", "elasticore.rules.v3", "java");
        kotlin = run("elcore-enum-length-kt", "elasticore.rules.v3", "kotlin");
    }

    @AfterAll
    static void cleanUp() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        for (File project : projects) delete(project);
    }

    @Test
    public void theColumnOfAnEnumHasItsLengthWhateverElseIsSaid() throws Exception {
        for (String entity : new String[] {found(clean, "Board.java"), found(kotlin, "Board.kt")}) {
            assertTrue(entity.contains("@Column(nullable = false, length = 3)"), entity);
            assertTrue(entity.contains("@Column(length = 3, unique = true)"), entity);
            assertTrue(entity.contains("@Column(updatable = false, length = 3)"), entity);
            assertTrue(entity.contains("@Column(length = 3)"), entity);
            // A string's own length is not the enum's.
            assertTrue(entity.contains("nullable = false, length = 30)"), entity);
        }
    }

    @Test
    public void theSchemaHasNoDefaultLengthForAnEnum() throws Exception {
        String ddl = read(new File(clean, "src/main/resources/db/schema-postgresql.sql"));
        assertFalse(ddl.contains("VARCHAR(255)"), ddl);
        assertEquals(4, count(ddl, "VARCHAR(3)"), ddl);
    }

    @Test
    public void theCompatibleOutputKeeps2xsColumns() throws Exception {
        String entity = found(compat, "Board.java");
        assertTrue(entity.contains("@Column(nullable = false)\n"), entity);
        assertTrue(entity.contains("@Column(unique = true)\n"), entity);
        assertTrue(entity.contains("@Column(updatable = false)\n"), entity);
        assertEquals(1, count(entity, "@Column(length = 3)"), entity);
    }

    private static File run(String name, String rules, String language) throws Exception {
        File project = Files.createTempDirectory(name).toFile();
        projects.add(project);
        domain(project, "board", MODEL);

        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v3",
            "elasticore.rules.v2",
            "elasticore.rules.ts",
            DdlConfig.DATABASES
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "layered");
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty(rules, "true");
            System.setProperty(DdlConfig.DATABASES, "postgresql");
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
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: " + name,
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    entity: io.acme." + name + ".entity",
                                "    dto: io.acme." + name + ".dto",
                                "    repository: io.acme." + name + ".repository",
                                "    service: io.acme." + name + ".service",
                                "    control: io.acme." + name + ".controller",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) n++;
        return n;
    }

    private static String read(File file) throws IOException {
        assertTrue(file.isFile(), file + " was not written");
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String found(File project, String fileName) throws IOException {
        List<File> hits = new ArrayList<File>();
        collect(project, fileName, hits);
        assertEquals(1, hits.size(), fileName + " in " + project + ": " + hits);
        return read(hits.get(0));
    }

    private static void collect(File dir, String fileName, List<File> hits) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, fileName, hits);
            else if (f.getName().equals(fileName)) hits.add(f);
        }
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
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
