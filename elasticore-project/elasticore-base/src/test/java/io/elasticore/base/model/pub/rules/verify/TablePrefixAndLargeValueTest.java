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
 * The 3.0 code names an entity's table as 2.x does - table.prefix, then the
 * @table name or the entity's - so a project moving from 2.x keeps its
 * tables; an @table that already starts with the prefix is said. And clob /
 * blob are the database's long text / binary in the row, not a @Lob that
 * PostgreSQL keeps apart as a large object with only its number in the column.
 */
public class TablePrefixAndLargeValueTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  TpNote:",
            "    meta: entity @table(note)",
            "    fields:",
            "      noteId: long @id @sequence",
            "      body: clob",
            "      image: blob",
            "",
            "  TpTag:",
            "    meta: entity",
            "    fields:",
            "      tagId: long @id @sequence",
            "      name: string(30)",
            "",
            "  TpServer:",
            "    meta: entity @table(el_mcp_server_def)",
            "    fields:",
            "      serverId: long @id @sequence",
            "      name: string(30)",
            "");

    private final List<File> projects = new ArrayList<File>();

    @Test
    public void theJavaEntitiesKeepTheTwoXTablesAndStoreLargeValuesInTheRow() throws Exception {
        File project = project("jpa");
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        assertEquals(0, run(project, "java", log));

        String note = found(project, "TpNote.java");
        assertTrue(note.contains("\"el_note\""), note);
        String tag = found(project, "TpTag.java");
        assertTrue(tag.contains("\"el_tp_tag\""), tag);
        String server = found(project, "TpServer.java");
        assertTrue(server.contains("\"el_el_mcp_server_def\""), server);

        assertFalse(note.contains("Lob"), note);
        assertTrue(note.contains("SqlTypes.LONG32VARCHAR"), note);
        assertTrue(note.contains("SqlTypes.LONG32VARBINARY"), note);

        String out = new String(log.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(
                out.contains("TpServer: @table(el_mcp_server_def) with table.prefix el_ maps to the table"
                        + " el_el_mcp_server_def"),
                out);
        assertTrue(out.contains("write @table(mcp_server_def)"), out);
        assertFalse(out.contains("TpNote: @table(note) with"), out);
    }

    @Test
    public void theKotlinEntityAlike() throws Exception {
        File project = project("jpa");
        assertEquals(0, run(project, "kotlin", null));
        String note = found(project, "TpNote.kt");
        assertTrue(note.contains("\"el_note\""), note);
        assertFalse(note.contains("Lob"), note);
        assertTrue(note.contains("SqlTypes.LONG32VARCHAR"), note);
    }

    @Test
    public void theMyBatisSqlNamesTheSameTable() throws Exception {
        File project = project("mybatis");
        assertEquals(0, run(project, "java", null));
        String sql = found(project, "tpdesk-schema.sql");
        assertTrue(sql.contains("el_note"), listing(project) + "\n" + sql);
        assertTrue(sql.contains("el_el_mcp_server_def"), sql);
    }

    // ------------------------------------------------------------------

    private File project(String mode) throws IOException {
        File project = Files.createTempDirectory("elcore-prefix").toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/tpdesk");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: tpdesk",
                                "    framework: springboot",
                                "    mode: " + mode,
                                "    j2ee: jakarta",
                                "    table:",
                                "      prefix: el_",
                                "  namespace:",
                                "    base: io.acme.tpdesk",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    private static int run(File project, String language, ByteArrayOutputStream captured) {
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
        ByteArrayOutputStream bytes = captured != null ? captured : new ByteArrayOutputStream();
        try {
            for (String k : keys) System.clearProperty(k);
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "layered");
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty("elasticore.rules.v3", "true");
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
        collect(project, fileName, hits, false);
        assertEquals(1, hits.size(), fileName + " in " + project + ": " + hits);
        return new String(Files.readAllBytes(hits.get(0).toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String listing(File project) {
        List<File> hits = new ArrayList<File>();
        collect(project, "", hits, true);
        StringBuilder sb = new StringBuilder();
        for (File f : hits) sb.append(f.getPath()).append('\n');
        return sb.toString();
    }

    private static void collect(File dir, String name, List<File> hits, boolean suffix) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, name, hits, suffix);
            else if (suffix ? f.getName().endsWith(name) : f.getName().equals(name)) hits.add(f);
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
