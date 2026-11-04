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
 * A relation searched by {@code @search} is searched through its flattened
 * id, boardBoardId: the search DTO derived from the entity carries the id
 * and not the relation. When the model also declares the entity's DTO
 * ({@code PostDTO: meta: dto @template(Post)}), the relation reached the
 * mapper's search conditions as it was, which read
 * {@code searchDTO.getBoard()} - a property the search DTO does not have, so
 * the generated code did not compile; under mode: mybatis the run stopped.
 * 2.x searches by the flattened id alone in both cases.
 */
public class TemplateDtoSearchRelationTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Board:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      boardId: long @id @sequence",
            "      name: string(50)",
            "  Post:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      postId: long @id @sequence",
            "      title: string(100) @search(like)",
            "      board: Board! @search(eq)",
            "dto:",
            "  PostDTO:",
            "    meta: dto @template(Post)",
            "");

    private static final List<File> projects = new ArrayList<File>();

    private static File compat;
    private static File clean;
    private static File hexagonal;
    private static File kotlin;
    private static File mybatis;
    private static int mybatisCode;
    private static String mybatisLog;

    @BeforeAll
    static void generate() throws Exception {
        compat = project("elcore-tsr-compat", "jpa");
        assertEquals(0, run(compat, "elasticore.rules.v2", "layered", "java")[0]);
        clean = project("elcore-tsr-v3", "jpa");
        assertEquals(0, run(clean, "elasticore.rules.v3", "layered", "java")[0]);
        hexagonal = project("elcore-tsr-hex", "jpa");
        assertEquals(0, run(hexagonal, "elasticore.rules.v3", "hexagonal", "java")[0]);
        kotlin = project("elcore-tsr-kt", "jpa");
        assertEquals(0, run(kotlin, "elasticore.rules.v3", "layered", "kotlin")[0]);
        mybatis = project("elcore-tsr-mybatis", "mybatis");
        Object[] mb = run(mybatis, "elasticore.rules.v3", "layered", "java");
        mybatisCode = (Integer) mb[0];
        mybatisLog = (String) mb[1];
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
    public void theSearchDtoCarriesTheFlattenedId() throws Exception {
        for (File project : new File[] {compat, clean}) {
            String dto = found(project, "PostSrchDTO.java");
            assertTrue(dto.contains("private Long boardBoardId;"), dto);
            assertFalse(dto.contains("private Board board;"), dto);
        }
        // DDD: the reference to another aggregate is its identity, boardId.
        String dto = found(hexagonal, "PostSrchDTO.java");
        assertTrue(dto.contains("private Long boardId;"), dto);
        assertFalse(dto.contains("private Board board;"), dto);
    }

    @Test
    public void theMapperSearchesByTheFlattenedIdAlone() throws Exception {
        for (File project : new File[] {compat, clean}) {
            String spec = postSpec(project);
            assertTrue(spec.contains("searchDTO.getBoardBoardId()"), spec);
            assertTrue(spec.contains("r.get(\"board\").get(\"boardId\")"), spec);
            assertFalse(spec.contains("searchDTO.getBoard()"), spec);
        }
        String spec = postSpec(hexagonal);
        assertTrue(spec.contains("searchDTO.getBoardId()"), spec);
        assertFalse(spec.contains("searchDTO.getBoard()"), spec);
    }

    @Test
    public void theKotlinMapperSearchesByTheFlattenedIdAlone() throws Exception {
        String mapper = mapperOf(kotlin, ".kt");
        assertTrue(mapper.contains("searchDTO.boardBoardId"), mapper);
        assertFalse(mapper.contains("searchDTO.board\n"), mapper);
    }

    @Test
    public void myBatisSearchesByTheFlattenedIdAlone() throws Exception {
        assertEquals(0, mybatisCode, mybatisLog);
        assertFalse(mybatisLog.contains("board is further than that"), mybatisLog);
        String xml = found(mybatis, "PostRepository.xml");
        assertTrue(xml.contains("boardBoardId"), xml);
    }

    /** The mapper's search conditions of the Post search DTO. */
    private static String postSpec(File project) throws IOException {
        String mapper = mapperOf(project, ".java");
        int start = mapper.indexOf("toSpec(PostSrchDTO searchDTO");
        assertTrue(start >= 0, mapper);
        int end = mapper.indexOf("return sp;", start);
        return mapper.substring(start, end);
    }

    private static String mapperOf(File project, String extension) throws IOException {
        List<File> hits = new ArrayList<File>();
        collect(project, "ForumMapper" + extension, hits);
        assertEquals(1, hits.size(), "the mapper in " + project + ": " + hits);
        return read(hits.get(0));
    }

    private static File project(String name, String mode) throws IOException {
        File project = Files.createTempDirectory(name).toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/forum");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: forum",
                                "    framework: springboot",
                                "    mode: " + mode,
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    base: io.acme.forum",
                                "    entity: io.acme.forum.entity",
                                "    dto: io.acme.forum.dto",
                                "    repository: io.acme.forum.repository",
                                "    service: io.acme.forum.service",
                                "    control: io.acme.forum.controller",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    /** The run's exit code and what it printed. */
    private static Object[] run(File project, String rules, String architecture, String language) throws Exception {
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
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        int code;
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty(rules, "true");
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            code = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
            io.elasticore.base.model.pub.rules.ModelCheck.reset();
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        return new Object[] {code, log};
    }

    private static String read(File file) throws IOException {
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
