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
 * @expose(pageSize=50, methods="...") gives the search DTO its page size and
 * the controller its endpoints - the page size was lost beside methods= (20,
 * issue 160). 2.x's @expose(50) still pages by 50.
 */
public class ExposePageSizeTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Board:",
            "    meta: entity @root @expose(pageSize=50, methods=\"create,search,update,delete\")",
            "    fields:",
            "      boardId: long @id @sequence",
            "      name: string(30)!",
            "",
            "  Shelf:",
            "    meta: entity @root @expose(50)",
            "    fields:",
            "      shelfId: long @id @sequence",
            "      name: string(30)!",
            "",
            "  Crate:",
            "    meta: entity @root @expose(40, methods=\"read,search\")",
            "    fields:",
            "      crateId: long @id @sequence",
            "      name: string(30)!",
            "",
            "  Tray:",
            "    meta: entity @root @expose(methods=\"read,search\")",
            "    fields:",
            "      trayId: long @id @sequence",
            "      name: string(30)!",
            "");

    private final List<File> projects = new ArrayList<File>();

    @Test
    public void thePageSizeIsKeptBesideMethods() throws Exception {
        File project = project();
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        assertEquals(0, run(project, log));

        assertTrue(found(project, "BoardSrchDTO.java").contains("private int pageSize=50;"));
        assertTrue(found(project, "ShelfSrchDTO.java").contains("private int pageSize=50;"));
        assertTrue(found(project, "CrateSrchDTO.java").contains("private int pageSize=40;"));
        assertTrue(found(project, "TraySrchDTO.java").contains("private int pageSize=20;"));

        // methods= still decides the endpoints: Board has no read by key, Crate no create.
        String board = found(project, "BoardController.java");
        assertFalse(board.contains("@GetMapping(\"/{boardId}\")"), board);
        assertTrue(board.contains("@PostMapping"), board);
        String crate = found(project, "CrateController.java");
        assertTrue(crate.contains("@GetMapping(\"/{crateId}\")"), crate);
        assertFalse(crate.contains("create("), crate);

        // pageSize= is one of @expose's words: nothing is said about it.
        String out = new String(log.toByteArray(), StandardCharsets.UTF_8);
        assertFalse(out.contains("@expose(pageSize=...)"), out);
    }

    // ------------------------------------------------------------------

    private File project() throws IOException {
        File project = Files.createTempDirectory("elcore-expose").toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/shelf");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: shelf",
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    base: io.acme.shelf",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    private static int run(File project, ByteArrayOutputStream captured) {
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
            System.setProperty("elasticore.architecture", "hexagonal");
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty("elasticore.rules.v3", "true");
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
