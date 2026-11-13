package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.pub.rules.ModelCheck;
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
 * A search value is no longer than the column it is compared with: a string
 * search field gets the declared length as its @Size, as the DTO does - in the
 * 3.0 code; the 2.x-compatible search DTO stays as 2.x wrote it (issue 169).
 */
public class SearchSizeTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  SzPost:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      postId: long @id @sequence",
            "      title: string(200)! @search(like)",
            "      nickname: string(30) @search(eq)",
            "      views: int @search(>=)",
            "dto:",
            "  SzPostKeywordSrchDTO:",
            "    meta: dto @searchable(entity=SzPost, pageSize=20)",
            "    fields:",
            "      keyword: string(50) @search(like) @alias(title)",
            "");

    private final List<File> projects = new ArrayList<File>();

    @Test
    public void theSearchFieldsHaveTheirLength() throws Exception {
        File project = project();
        assertEquals(0, run(project, "java", "elasticore.rules.v3"));
        String srch = found(project, "SzPostSrchDTO.java");
        assertTrue(srch.contains("@Size(max=200)"), srch);
        assertTrue(srch.contains("@Size(max=30)"), srch);
        assertEquals(2, count(srch, "@Size("), srch);
        String keyword = found(project, "SzPostKeywordSrchDTO.java");
        assertTrue(keyword.contains("@Size(max=50)"), keyword);
    }

    @Test
    public void kotlinAlike() throws Exception {
        File project = project();
        assertEquals(0, run(project, "kotlin", "elasticore.rules.v3"));
        assertTrue(found(project, "SzPostSrchDTO.kt").contains("@Size(max=200)"));
    }

    @Test
    public void theTwoXOutputIsAsItWas() throws Exception {
        File project = project();
        assertEquals(0, run(project, "java", "elasticore.rules.v2"));
        assertFalse(found(project, "SzPostSrchDTO.java").contains("@Size("));
    }

    // ------------------------------------------------------------------

    private static int count(String text, String part) {
        int n = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) n++;
        return n;
    }

    private File project() throws IOException {
        File project = Files.createTempDirectory("elcore-size").toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/szdesk");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: szdesk",
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    base: io.acme.szdesk",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    private static int run(File project, String language, String output) {
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
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            for (String k : keys) System.clearProperty(k);
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
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
            out.print(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
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
        ModelCheck.reset();
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
