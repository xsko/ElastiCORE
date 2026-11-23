package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The base of the merge is .elasticore/generated-base.zip; it used to be
 * .elasticore/cache. A project that has only the old file is read from it and
 * moved on its first build that writes - the same bytes under the new name,
 * the old one removed only after that, the attributes beside it renamed with
 * the developer's own lines kept. Both there: the new one is read, the old one
 * left with a warning. A run that writes nothing moves nothing.
 */
public class GeneratedBaseRenameTest {

    private static final String[] KEYS = {
        "elasticore.base.path",
        "elasticore.rules.force",
        "elasticore.merge",
        "elasticore.rules.v2",
        "elasticore.rules.v3",
        "elasticore.domain",
        "elasticore.rules.list",
        "elasticore.validate",
        "elasticore.validate.strict",
        "elasticore.migration.check"
    };

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Customer:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      customerId: long @id @sequence",
            "      name: string(50)! @search(like)",
            "  PurchaseOrder:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      orderId: long @id @sequence",
            "      orderNo: string(20)! @search(eq)",
            "      customer: Customer! @ManyToOne @search(eq)",
            "");

    private static final String XML = "PurchaseOrderRepository.xml";

    private static final String RENAMED = "[elcore3] .elasticore/cache was renamed to .elasticore/generated-base.zip"
            + " - commit both changes (the delete and the new file)";

    private final List<File> projects = new ArrayList<File>();
    private final String[] saved = save();

    @AfterEach
    void clean() throws IOException {
        for (int i = 0; i < KEYS.length; i++) {
            if (saved[i] == null) System.clearProperty(KEYS[i]);
            else System.setProperty(KEYS[i], saved[i]);
        }
        for (File p : projects) delete(p.toPath());
    }

    @Test
    public void aNewProjectWritesTheNewNameOnly() throws Exception {
        File project = project();
        String log = run(project, 0);
        assertTrue(base(project).isFile());
        assertFalse(old(project).exists());
        assertEquals(
                "generated-base.zip binary -filter\n", read(attributes(project).toPath()));
        assertFalse(log.contains("was renamed"), log);
        assertFalse(log.contains("no longer read"), log);
        // Taken back in where a project ignores *.zip.
        assertEquals("!generated-base.zip\n", read(new File(project, ".elasticore/.gitignore").toPath()));
    }

    @Test
    public void aRunOfOneDomainRenamesTooAndARefusedCheckOfOneDoesNot() throws Exception {
        File project = project();
        run(project, 0);
        toOldName(project, "cache binary\n");
        byte[] bytes = Files.readAllBytes(old(project).toPath());
        System.setProperty("elasticore.domain", "dgshop");

        // Refused before its preview begins: nothing moved.
        System.setProperty("elasticore.validate", "true");
        String log = run(project, 1);
        System.clearProperty("elasticore.validate");
        assertTrue(log.contains("run it without -Pdomain"), log);
        assertFalse(base(project).exists(), log);
        assertArrayEquals(bytes, Files.readAllBytes(old(project).toPath()));
        System.setProperty("elasticore.migration.check", "true");
        log = run(project, 1);
        System.clearProperty("elasticore.migration.check");
        assertFalse(base(project).exists(), log);

        log = run(project, 0);
        assertTrue(log.contains(RENAMED), log);
        assertFalse(old(project).exists());
        assertArrayEquals(bytes, Files.readAllBytes(base(project).toPath()));
    }

    @Test
    public void anOldBaseIsRenamedWithTheSameBytesOnTheFirstBuild() throws Exception {
        File project = project();
        run(project, 0);
        byte[] bytes = Files.readAllBytes(base(project).toPath());
        toOldName(project, "cache binary\n");

        // Nothing changed in the model: the new file is the old one's bytes, entry for entry.
        String log = run(project, 0);
        assertTrue(log.contains(RENAMED), log);
        assertEquals(1, count(log, "was renamed"), log);
        assertFalse(old(project).exists(), "the old base is removed once the new one is there");
        assertArrayEquals(bytes, Files.readAllBytes(base(project).toPath()));
        assertEquals(
                GeneratedCache.read(bytes),
                GeneratedCache.read(Files.readAllBytes(base(project).toPath())));
        assertEquals(
                "generated-base.zip binary -filter\n", read(attributes(project).toPath()));
        assertFalse(new File(project, ".elasticore/generated-base.zip.tmp").exists());

        // Said once: the next build has nothing to move.
        log = run(project, 0);
        assertFalse(log.contains("was renamed"), log);
        assertArrayEquals(bytes, Files.readAllBytes(base(project).toPath()));
    }

    @Test
    public void anEditIsMergedAcrossTheRename() throws Exception {
        File project = project();
        run(project, 0);
        // A project as the old name left it, with lines of the developer's own in the attributes.
        String attributes = "# ours\r\n*.bin binary\r\ncache binary\r\n";
        toOldName(project, attributes);
        String edited = xml(project).replace("</mapper>", "    <!-- reviewed by the DBA -->\n</mapper>");
        Files.write(xmlPath(project), edited.getBytes(StandardCharsets.UTF_8));
        model(project, MODEL + "      memo: string(200)\n");

        String log = run(project, 0);
        assertTrue(log.contains(XML + "  MERGED(your edits kept)"), log);
        String merged = xml(project);
        assertTrue(merged.contains("t.memo") && merged.contains("reviewed by the DBA"), merged);
        assertTrue(log.contains(RENAMED), log);
        assertFalse(old(project).exists());
        assertEquals(
                "# ours\r\n*.bin binary\r\ngenerated-base.zip binary -filter\r\n",
                read(attributes(project).toPath()));
        // The new base holds what was generated now, not the merge.
        String inBase =
                GeneratedCache.read(Files.readAllBytes(base(project).toPath())).get(key(project));
        assertTrue(inBase.contains("t.memo") && !inBase.contains("reviewed by the DBA"), inBase);
    }

    @Test
    public void bothNamesReadTheNewOneAndLeaveTheOld() throws Exception {
        File project = project();
        run(project, 0);
        // The old name merged back by a branch, holding nothing: were it read, the edit would have no base.
        byte[] stale = GeneratedCache.zip(new TreeMap<String, String>());
        Files.write(old(project).toPath(), stale);
        String edited = xml(project).replace("</mapper>", "    <!-- mine -->\n</mapper>");
        Files.write(xmlPath(project), edited.getBytes(StandardCharsets.UTF_8));
        model(project, MODEL + "      memo: string(200)\n");

        String log = runCapturingErr(project, 0);
        assertTrue(log.contains(XML + "  MERGED(your edits kept)"), log);
        assertTrue(
                log.contains("[elcore3] .elasticore/cache is no longer read - .elasticore/generated-base.zip is the"
                        + " base of the merge; delete .elasticore/cache and commit the delete"),
                log);
        assertFalse(log.contains("was renamed"), log);
        assertArrayEquals(stale, Files.readAllBytes(old(project).toPath()), "the old one is left as it was");
        assertTrue(xml(project).contains("<!-- mine -->") && xml(project).contains("t.memo"));
    }

    @Test
    public void anOldBaseThatCannotBeReadIsNamedAndKept() throws Exception {
        File project = project();
        run(project, 0);
        toOldName(project, "cache binary\n");
        Files.write(old(project).toPath(), "<<<<<<< HEAD\n".getBytes(StandardCharsets.UTF_8));
        String log = runCapturingErr(project, 1);
        assertTrue(log.contains(".elasticore/cache cannot be read"), log);
        assertTrue(old(project).isFile());
        assertFalse(base(project).exists());
    }

    @Test
    public void aRunThatWritesNothingMovesNothing() throws Exception {
        File project = project();
        run(project, 0);
        toOldName(project, "cache binary\n");
        byte[] bytes = Files.readAllBytes(old(project).toPath());

        System.setProperty("elasticore.rules.list", "true");
        String log = run(project, -1);
        System.clearProperty("elasticore.rules.list");
        assertFalse(log.contains("was renamed"), log);
        assertFalse(base(project).exists(), log);
        assertArrayEquals(bytes, Files.readAllBytes(old(project).toPath()));

        System.setProperty("elasticore.validate", "true");
        log = run(project, -1);
        System.clearProperty("elasticore.validate");
        assertFalse(log.contains("was renamed"), log);
        assertFalse(base(project).exists(), log);
        assertArrayEquals(bytes, Files.readAllBytes(old(project).toPath()));

        System.setProperty("elasticore.migration.check", "true");
        log = run(project, -1);
        System.clearProperty("elasticore.migration.check");
        assertFalse(log.contains("was renamed"), log);
        assertFalse(base(project).exists(), log);
        assertArrayEquals(bytes, Files.readAllBytes(old(project).toPath()));
        assertEquals("cache binary\n", read(attributes(project).toPath()));

        // The build that writes, after them: moved.
        assertTrue(run(project, 0).contains(RENAMED));
        assertArrayEquals(bytes, Files.readAllBytes(base(project).toPath()));
    }

    @Test
    public void aRunStoppedAfterTheNewIsWrittenLeavesTheOld() throws Exception {
        File project = Files.createTempDirectory("elcore-rename").toFile();
        projects.add(project);
        TreeMap<String, String> entries = new TreeMap<String, String>();
        entries.put("src/main/resources/a.xml", "<a/>\n");
        byte[] bytes = GeneratedCache.zip(entries);
        assertTrue(new File(project, ".elasticore").mkdirs());
        Files.write(old(project).toPath(), bytes);
        Files.write(attributes(project).toPath(), "cache binary\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(GeneratedCache.OLD_PATH, GeneratedCache.pathIn(project));
        GeneratedCache cache = GeneratedCache.load(project);
        assertTrue(cache.renaming());
        assertTrue(cache.existed());
        assertEquals("<a/>\n", cache.get("src/main/resources/a.xml"));
        cache.afterNewWritten(() -> {
            throw new IllegalStateException("the build was stopped");
        });
        assertThrows(IllegalStateException.class, cache::save);
        assertArrayEquals(bytes, Files.readAllBytes(old(project).toPath()), "the old base is still there");
        assertEquals("cache binary\n", read(attributes(project).toPath()));

        // The next run reads the new one, whole, and leaves the old one to the developer.
        GeneratedCache next = GeneratedCache.load(project);
        assertFalse(next.renaming());
        assertEquals(old(project).getAbsoluteFile(), next.oldLeftBeside());
        assertEquals(entries, new TreeMap<String, String>(contents(next)));
        assertEquals(GeneratedCache.PATH, GeneratedCache.pathIn(project));
    }

    @Test
    public void aNewFileThatDoesNotReadBackIsTakenBack() throws Exception {
        File project = Files.createTempDirectory("elcore-rename").toFile();
        projects.add(project);
        TreeMap<String, String> entries = new TreeMap<String, String>();
        entries.put("src/main/resources/a.xml", "<a/>\n");
        byte[] bytes = GeneratedCache.zip(entries);
        assertTrue(new File(project, ".elasticore").mkdirs());
        Files.write(old(project).toPath(), bytes);

        GeneratedCache cache = GeneratedCache.load(project);
        cache.afterNewWritten(() -> {
            try {
                Files.write(base(project).toPath(), new byte[] {1, 2, 3});
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        IOException refused = assertThrows(IOException.class, cache::save);
        assertTrue(refused.getMessage().contains(".elasticore/cache is kept"), refused.getMessage());
        assertFalse(base(project).exists(), "the new file that did not read back is gone");
        assertArrayEquals(bytes, Files.readAllBytes(old(project).toPath()));
        assertTrue(GeneratedCache.load(project).renaming(), "the next run reads the old one again");
    }

    @Test
    public void anOldNameThatIsNoLongerAFileIsLeftAlone() throws Exception {
        File project = Files.createTempDirectory("elcore-rename").toFile();
        projects.add(project);
        assertTrue(new File(project, ".elasticore").mkdirs());
        Files.write(old(project).toPath(), GeneratedCache.zip(new TreeMap<String, String>()));
        GeneratedCache cache = GeneratedCache.load(project);
        // Replaced by a folder of the same name after the run read it.
        Files.delete(old(project).toPath());
        assertTrue(new File(old(project), "kept").mkdirs());
        cache.save();
        assertEquals("it is not a regular file", cache.oldKeptBecause());
        assertEquals(old(project).getAbsoluteFile(), cache.oldLeftBeside());
        assertFalse(cache.renaming());
        assertTrue(new File(old(project), "kept").isDirectory());
        assertTrue(base(project).isFile());
        // Written without the attributes there: the new name's line.
        assertEquals(
                "generated-base.zip binary -filter\n", read(attributes(project).toPath()));
    }

    @Test
    public void neitherNameIsAFirstRun() throws Exception {
        File project = Files.createTempDirectory("elcore-rename").toFile();
        projects.add(project);
        GeneratedCache cache = GeneratedCache.load(project);
        assertFalse(cache.existed());
        assertFalse(cache.renaming());
        assertNull(cache.oldLeftBeside());
        assertEquals(GeneratedCache.PATH, GeneratedCache.pathIn(project));
        cache.save();
        assertFalse(new File(project, ".elasticore").exists(), "nothing to save, nothing written");
        cache.put("src/main/resources/a.xml", "<a/>\n");
        cache.save();
        assertTrue(base(project).isFile());
        assertFalse(old(project).exists());
        assertNull(cache.oldKeptBecause());
    }

    @Test
    public void theAttributesNameTheNewFileAndKeepEverythingElse() {
        assertEquals("generated-base.zip binary -filter\n", GeneratedCache.renamedAttributes("cache binary\n"));
        assertEquals(
                "/generated-base.zip -diff merge=ours", GeneratedCache.renamedAttributes("/cache -diff merge=ours"));
        assertEquals("  generated-base.zip\tbinary\r\n", GeneratedCache.renamedAttributes("  cache\tbinary\r\n"));
        String mine = "# cache binary\n*.zip binary\ncachefile binary\n\ncache binary\n";
        assertEquals(
                "# cache binary\n*.zip binary\ncachefile binary\n\ngenerated-base.zip binary -filter\n",
                GeneratedCache.renamedAttributes(mine));
        // Named already, or not at all: as it was.
        String both = "cache binary\ngenerated-base.zip binary\n";
        assertEquals(both, GeneratedCache.renamedAttributes(both));
        assertEquals(
                "/generated-base.zip binary\ncache binary",
                GeneratedCache.renamedAttributes("/generated-base.zip binary\ncache binary"));
        assertEquals("*.bin binary\n", GeneratedCache.renamedAttributes("*.bin binary\n"));
        assertEquals("", GeneratedCache.renamedAttributes(""));
    }

    private static Map<String, String> contents(GeneratedCache cache) {
        Map<String, String> all = new TreeMap<String, String>();
        for (String p : cache.paths()) all.put(p, cache.get(p));
        return all;
    }

    /** The project as the old name left it: the base under .elasticore/cache, the attributes as given. */
    private static void toOldName(File project, String attributes) throws IOException {
        Files.move(base(project).toPath(), old(project).toPath());
        Files.write(attributes(project).toPath(), attributes.getBytes(StandardCharsets.UTF_8));
    }

    private static File base(File project) {
        return new File(project, ".elasticore/generated-base.zip");
    }

    private static File old(File project) {
        return new File(project, ".elasticore/cache");
    }

    private static File attributes(File project) {
        return new File(project, ".elasticore/.gitattributes");
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) n++;
        return n;
    }

    private static String key(File project) throws IOException {
        return project.toPath().relativize(xmlPath(project)).toString().replace('\\', '/');
    }

    private static String xml(File project) throws IOException {
        return read(xmlPath(project));
    }

    private static Path xmlPath(File project) throws IOException {
        File root = new File(project, "src/main/resources");
        assertTrue(root.isDirectory(), "nothing was generated");
        try (Stream<Path> all = Files.walk(root.toPath())) {
            Path p = all.filter(f -> f.getFileName().toString().equals(XML))
                    .findFirst()
                    .orElse(null);
            assertNotNull(p, XML + " was not written");
            return p;
        }
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    private String run(File project, int expected) throws Exception {
        return run(project, expected, false);
    }

    private String runCapturingErr(File project, int expected) throws Exception {
        return run(project, expected, true);
    }

    private String run(File project, int expected, boolean err) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        PrintStream errOut = System.err;
        PrintStream capture = new PrintStream(bytes, true, "UTF-8");
        System.setOut(capture);
        if (err) System.setErr(capture);
        int code;
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.clearProperty("elasticore.rules.force");
            System.setProperty("elasticore.merge", "true");
            code = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
            System.setErr(errOut);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        if (expected >= 0) assertEquals(expected, code, log);
        return log;
    }

    private File project() throws IOException {
        File dir = Files.createTempDirectory("elcore-rename").toFile();
        projects.add(dir);
        File blueprint = new File(dir, "src/main/resources/blueprint/dgshop");
        assertTrue(new File(blueprint, "model").mkdirs());
        String env = String.join(
                "\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: dgshop",
                "    framework: springboot",
                "    mode: mybatis",
                "    j2ee: jakarta",
                "  namespace:",
                "    base: io.acme.dgshop",
                "    entity: io.acme.dgshop.entity",
                "    repository: io.acme.dgshop.repository",
                "");
        Files.write(new File(blueprint, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        model(dir, MODEL);
        return dir;
    }

    private static void model(File project, String model) throws IOException {
        Files.write(
                new File(project, "src/main/resources/blueprint/dgshop/model/model.yml").toPath(),
                model.getBytes(StandardCharsets.UTF_8));
    }

    private static String[] save() {
        String[] s = new String[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) s[i] = System.getProperty(KEYS[i]);
        return s;
    }

    private static void delete(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> all = Files.walk(root)) {
            List<Path> paths = new ArrayList<Path>();
            all.forEach(paths::add);
            for (int i = paths.size() - 1; i >= 0; i--) Files.deleteIfExists(paths.get(i));
        }
    }
}
