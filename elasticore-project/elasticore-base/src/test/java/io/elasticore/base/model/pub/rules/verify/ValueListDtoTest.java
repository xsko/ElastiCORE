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
 * A DTO's list of values - List<Long>, List<String>, or the DSL's List<long> -
 * is carried by the 3.0 code as a List of the value's type, and a long[] as an
 * array: 2.x dropped them without a word, as if they named a relation (issue
 * 158). A list of a type nobody knows is said.
 */
public class ValueListDtoTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Member:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      memberId: long @id @sequence",
            "      email: string(100)! @unique",
            "dto:",
            "  MemberDTO:",
            "    meta: dto @template(Member)",
            "    fields:",
            "      tagIds: List<Long>",
            "      tags: List<String>",
            "      codes: List<string>",
            "      scores: List<long>",
            "      nums: int[]",
            "      ids: long[]",
            "      typo: List<Strng>",
            "");

    private final List<File> projects = new ArrayList<File>();

    @Test
    public void javaCarriesTheListsOfValues() throws Exception {
        File project = project();
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        assertEquals(0, run(project, "java", "elasticore.rules.v3", log));
        String dto = found(project, "MemberDTO.java");
        assertTrue(dto.contains("private List<Long> tagIds;"), dto);
        assertTrue(dto.contains("private List<String> tags;"), dto);
        assertTrue(dto.contains("private List<String> codes;"), dto);
        assertTrue(dto.contains("private List<Long> scores;"), dto);
        assertTrue(dto.contains("private Integer[] nums;"), dto);
        assertTrue(dto.contains("private Long[] ids;"), dto);
        assertFalse(dto.contains("typo"), dto);
        String out = new String(log.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(
                out.contains("MemberDTO.typo: List<Strng> - no entity, DTO, enum or value type is named Strng"), out);
        assertFalse(out.contains("unresolved type 'long[]'"), out);
    }

    @Test
    public void kotlinCarriesThemToo() throws Exception {
        File project = project();
        assertEquals(0, run(project, "kotlin", "elasticore.rules.v3", new ByteArrayOutputStream()));
        String dto = found(project, "MemberDTO.kt");
        assertTrue(dto.contains("tagIds: List<Long>?"), dto);
        assertTrue(dto.contains("codes: List<String>?"), dto);
    }

    @Test
    public void typescriptReadsThemAsArrays() throws Exception {
        File project = project();
        assertEquals(0, run(project, "java", "elasticore.rules.ts", new ByteArrayOutputStream()));
        String dto = found(project, "MemberDTO.ts");
        assertTrue(dto.contains("tagIds?: number[]"), dto);
        assertTrue(dto.contains("tags?: string[]"), dto);
        assertTrue(dto.contains("codes?: string[]"), dto);
    }

    /** The 2.x-compatible output drops them as 2.x did - and says so. */
    @Test
    public void theTwoXOutputSaysWhatItLeavesOut() throws Exception {
        File project = project();
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        assertEquals(0, run(project, "java", "elasticore.rules.v2", log));
        String dto = found(project, "MemberDTO.java");
        assertFalse(dto.contains("tagIds"), dto);
        String out = new String(log.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(
                out.contains("MemberDTO.tagIds: a list of values is left out of the DTO in the 2.x-compatible output"),
                out);
    }
    // ------------------------------------------------------------------

    private File project() throws IOException {
        File project = Files.createTempDirectory("elcore-values").toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/member");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: member",
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    base: io.acme.member",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    private static int run(File project, String language, String output, ByteArrayOutputStream captured) {
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
        try {
            for (String k : keys) System.clearProperty(k);
            System.setOut(new PrintStream(captured, true, "UTF-8"));
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "layered");
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty(output, "true");
            return RulesGenRunner.run(new String[0]);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        } finally {
            System.setOut(out);
            out.print(new String(captured.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
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
