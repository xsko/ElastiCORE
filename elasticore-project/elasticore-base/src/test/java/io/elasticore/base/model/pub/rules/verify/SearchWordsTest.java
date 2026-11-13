package io.elasticore.base.model.pub.rules.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.elasticore.base.model.pub.rules.schema.DdlConfig;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/** {@code @search(words: ...)}: one search box over several fields of a search DTO (issue 182). */
public class SearchWordsTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  WsMember:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      memberId: long @id @sequence",
            "      nickname: string(50)",
            "      age: int",
            "  WsPost:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      postId: long @id @sequence",
            "      title: string(200)!",
            "      body: string(2000)",
            "      author: WsMember @manyToOne",
            "",
            "dto:",
            "  WsPostSrchDTO:",
            "    meta: dto @searchable(entity=WsPost, pageSize=20)",
            "    fields:",
            "      keyword: string(50) @search(words:title, body, author.nickname, author.age, subject, title.x)",
            "      title: string @search(like)",
            "");

    private static final List<File> projects = new ArrayList<File>();

    @AfterAll
    static void cleanUp() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        for (File project : projects) delete(project);
    }

    @Test
    public void theJavaMapperLooksForEveryWordInTheFields() throws Exception {
        String[] log = new String[1];
        File project = run("elcore-words-java", "layered", "java", "jpa", log);
        String mapper = searching(project, ".java");
        assertTrue(
                mapper.contains("String keyword = searchDTO.getKeyword();\n"
                        + "        if(hasValue(keyword)){\n"
                        + "            sp = sp.and((r,q,c) -> io.elasticore.springboot3.mapper.Words.match(r, q, c, keyword,"
                        + " \"title\", \"body\", \"author.nickname\"));\n"
                        + "        }"),
                mapper);
        // The other conditions are unchanged beside it.
        assertTrue(mapper.contains("likeText(trim(title).toLowerCase(java.util.Locale.ROOT))"), mapper);
        String dto = found(project, "WsPostSrchDTO.java");
        assertTrue(dto.contains("private String keyword;"), dto);
        assertTrue(dto.contains("@Size(max=50)"), dto);
        assertTrue(
                dto.contains("every word (the first five) in any of title, body, author.nickname field:keyword"), dto);
        // A path that names no text field is said, and left out.
        assertTrue(log[0].contains("WsPostSrchDTO.keyword @search(words: ...): author.age - age is not text"), log[0]);
        assertTrue(log[0].contains("subject - WsPost has no field subject"), log[0]);
        assertTrue(log[0].contains("title.x - title is not a relation"), log[0]);
    }

    @Test
    public void theKotlinMapperToo() throws Exception {
        File project = run("elcore-words-kt", "hexagonal", "kotlin", "jpa", new String[1]);
        String mapper = searching(project, ".kt");
        assertTrue(
                mapper.contains("io.elasticore.springboot3.mapper.Words.match(r, q, c, keyword!!,"
                        + " \"title\", \"body\", \"author.nickname\")"),
                mapper);
    }

    @Test
    public void myBatisSaysItDoesNotSearchByIt() throws Exception {
        String[] log = new String[1];
        File project = run("elcore-words-mybatis", "layered", "java", "mybatis", log);
        assertTrue(log[0].contains("WsPost.keyword: @search(words: ...) is not searched under mode: mybatis"), log[0]);
        List<File> hits = new ArrayList<File>();
        collect(project, "WsPostMapper.xml", hits);
        for (File xml : hits) assertFalse(read(xml).contains("keyword"), read(xml));
    }

    /** A typo in the only path: searching by nothing would match every row, so the build stops. */
    @Test
    public void noFieldLeftStopsTheBuild() throws Exception {
        String[] log = new String[1];
        run(
                "elcore-words-none",
                "layered",
                "java",
                "jpa",
                log,
                MODEL.replace("words:title, body, author.nickname, author.age, subject, title.x", "words:titel"),
                false,
                1);
        assertTrue(
                log[0].contains(
                        "WsPostSrchDTO.keyword @search(words:...) names no text field of the entity to" + " search"),
                log[0]);
    }

    /** The 2.x-compatible output does not search by it, so the search DTO does not offer it either. */
    @Test
    public void theCompatibleOutputLeavesThePropertyOut() throws Exception {
        String[] log = new String[1];
        File project = run("elcore-words-v2", "layered", "java", "jpa", log, MODEL, true, 0);
        assertTrue(log[0].contains("@search(words: ...) is not searched in the 2.x-compatible output"), log[0]);
        String dto = found(project, "WsPostSrchDTO.java");
        assertFalse(dto.contains("keyword"), dto);
        assertTrue(dto.contains("private String title;"), dto);
    }

    private static File run(String name, String architecture, String language, String mode, String[] log)
            throws Exception {
        return run(name, architecture, language, mode, log, MODEL, false, 0);
    }

    private static File run(
            String name,
            String architecture,
            String language,
            String mode,
            String[] log,
            String model,
            boolean v2,
            int exit)
            throws Exception {
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
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));

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
        PrintStream out = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty(v2 ? "elasticore.rules.v2" : "elasticore.rules.v3", "true");
            System.setProperty(DdlConfig.DATABASES, "postgresql");
            assertEquals(exit, RulesGenRunner.run(new String[0]), "the run's exit: " + project);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
            io.elasticore.base.model.pub.rules.ModelCheck.reset();
        }
        log[0] = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log[0]);
        return project;
    }

    /** The generated file that searches WsPost: the mapper holding toSpec(WsPostSrchDTO). */
    private static String searching(File project, String extension) throws IOException {
        List<File> all = new ArrayList<File>();
        collectAll(new File(project, "src/main"), extension, all);
        for (File f : all) {
            String text = read(f);
            // The mapper's own: the service calls toSpec too, and the files come in no fixed order.
            if (text.contains("Specification<WsPost> toSpec(WsPostSrchDTO")
                    || text.contains("fun toSpec(searchDTO: WsPostSrchDTO")) return text;
        }
        throw new AssertionError("no toSpec for WsPostSrchDTO in " + all);
    }

    private static void collectAll(File dir, String extension, List<File> hits) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collectAll(f, extension, hits);
            else if (f.getName().endsWith(extension)) hits.add(f);
        }
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
