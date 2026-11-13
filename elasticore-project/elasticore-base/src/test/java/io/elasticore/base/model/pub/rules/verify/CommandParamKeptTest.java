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
 * A lifecycle command's parameter: one named as a field of the entity, of its
 * type, is kept by the generated move; any other is said, as the move cannot
 * know where it goes and dropped it without a word (issue 170).
 */
public class CommandParamKeptTest {

    private static final String MODEL = String.join(
            "\n",
            "enumeration:",
            "  CkStatus:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(3) @code",
            "    enum:",
            "      PENDING:  PND",
            "      APPROVED: APR",
            "      REJECTED: REJ",
            "",
            "entity:",
            "  CkClaim:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      claimId: long @id @sequence",
            "      status: CkStatus",
            "      rejectReason: string(500)",
            "    states:",
            "      status:",
            "        initial: PENDING",
            "        approve: PENDING -> APPROVED",
            "        reject:  PENDING -> REJECTED",
            "    commands:",
            "      reject:",
            "        rejectReason: string(500)!",
            "        note: string(100)",
            "");

    private final List<File> projects = new ArrayList<File>();

    @Test
    public void javaKeepsTheNamedParameterAndSaysTheOther() throws Exception {
        File project = project();
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        assertEquals(0, run(project, "java", log));
        String base = found(project, "CkClaimBase.java");
        String reject = section(base, "public void reject(");
        assertTrue(reject.contains("this.rejectReason = rejectReason;"), reject);
        assertFalse(reject.contains("this.note"), reject);
        // Kept after the move: a refused command keeps nothing.
        assertTrue(reject.indexOf("moveStatus(") < reject.indexOf("this.rejectReason"), reject);
        String out = new String(log.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(out.contains("CkClaim.reject(note): the generated move does not keep note"), out);
        assertFalse(out.contains("reject(rejectReason"), out);
    }

    @Test
    public void kotlinKeepsItToo() throws Exception {
        File project = project();
        assertEquals(0, run(project, "kotlin", new ByteArrayOutputStream()));
        String base = found(project, "CkClaimBase.kt");
        String reject = section(base, "open fun reject(");
        assertTrue(reject.contains("this.rejectReason = rejectReason"), reject);
    }

    // ------------------------------------------------------------------

    private static String section(String source, String head) {
        int from = source.indexOf(head);
        assertTrue(from >= 0, head + "\n" + source);
        int to = source.indexOf("\n    }", from);
        return source.substring(from, to < 0 ? source.length() : to);
    }

    private File project() throws IOException {
        File project = Files.createTempDirectory("elcore-command").toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/ckdesk");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: ckdesk",
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    base: io.acme.ckdesk",
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
        try {
            for (String k : keys) System.clearProperty(k);
            System.setOut(new PrintStream(captured, true, "UTF-8"));
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "hexagonal");
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
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
