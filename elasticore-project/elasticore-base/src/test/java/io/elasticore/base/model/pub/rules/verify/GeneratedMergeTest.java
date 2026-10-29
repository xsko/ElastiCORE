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
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * elasticore.merge: a generated mapper the developer edited follows the DSL
 * and keeps the edits, whatever they were, against .elasticore/cache - what
 * was generated last. Each case of a generated file: written, regenerated,
 * edited, edited where the DSL changes it too, deleted, no longer produced,
 * in the way of one, written before the project merged, cloned.
 */
public class GeneratedMergeTest {

    private static final String[] KEYS = {"elasticore.base.path", "elasticore.rules.force", "elasticore.merge",
            "elasticore.ddl.databases", "elasticore.ddl.migration"};

    private static final String ORDER = String.join("\n",
            "  PurchaseOrder:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      orderId: long @id @sequence",
            "      orderNo: string(20)! @search(eq)",
            "      amount: long! @search(between)",
            "      customer: Customer! @ManyToOne @search(eq)",
            "");

    private static final String CUSTOMER = String.join("\n",
            "entity:",
            "  Customer:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      customerId: long @id @sequence",
            "      name: string(50)! @search(like)",
            "");

    private static String coupon(String name) {
        return String.join("\n",
                "  " + name + ":",
                "    meta: entity @expose(20)",
                "    fields:",
                "      " + Character.toLowerCase(name.charAt(0)) + name.substring(1) + "Id: long @id @sequence",
                "      code: string(20)!",
                "");
    }

    private static final String XML = "PurchaseOrderRepository.xml";

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
    public void aMapperIsWrittenWithoutAStampAndKeptInTheCache() throws Exception {
        File project = project(CUSTOMER + ORDER);
        String log = run(project, 0);
        assertTrue(log.contains(XML + "  WROTE") || log.contains("WROTE"), log);
        String xml = xml(project);
        assertTrue(xml.startsWith("<!DOCTYPE mapper"), xml);
        assertFalse(xml.contains("//ecd:"), xml);

        File cache = new File(project, ".elasticore/cache");
        assertTrue(cache.isFile());
        assertEquals("cache binary\n", new String(Files.readAllBytes(new File(project, ".elasticore/.gitattributes").toPath()),
                StandardCharsets.UTF_8));
        Map<String, String> entries = GeneratedCache.read(Files.readAllBytes(cache.toPath()));
        String key = key(project);
        assertEquals(xml.replace("\r\n", "\n"), entries.get(key));
        // A plain zip: any tool reads it; every entry carries the format's first day, never a local time.
        try (ZipFile zip = new ZipFile(cache)) {
            Enumeration<? extends ZipEntry> all = zip.entries();
            while (all.hasMoreElements()) {
                ZipEntry e = all.nextElement();
                assertEquals(ZipEntry.STORED, e.getMethod());
                assertTrue(e.getName().endsWith(".xml"), e.getName());
            }
        }

        // Nothing changed: the mapper and the cache are the same bytes.
        byte[] before = Files.readAllBytes(cache.toPath());
        byte[] xmlBefore = Files.readAllBytes(xmlPath(project));
        long modified = cache.lastModified();
        log = run(project, 0);
        assertTrue(log.contains("UNCHANGED"), log);
        assertArrayEquals(before, Files.readAllBytes(cache.toPath()));
        assertArrayEquals(xmlBefore, Files.readAllBytes(xmlPath(project)));
        assertEquals(modified, cache.lastModified(), "an unchanged cache is not rewritten");
    }

    @Test
    public void anyEditIsKeptWhileTheDslChangesTheFile() throws Exception {
        File project = project(CUSTOMER + ORDER);
        run(project, 0);
        String generated = xml(project);

        // A comment, a statement of the developer's own, an edited line.
        String edited = generated
                .replaceFirst("(<mapper namespace=\"[^\"]*\">)", "$1\n    <!-- reviewed by the DBA -->")
                .replace("</mapper>", "    <select id=\"countBig\" resultType=\"long\">\n"
                        + "        SELECT COUNT(*) FROM purchase_order WHERE amount &gt; 1000\n"
                        + "    </select>\n\n</mapper>")
                .replace("WHERE t.order_id = #{id,jdbcType=BIGINT}", "WHERE t.order_id = #{id,jdbcType=BIGINT} /* by key */");
        assertFalse(edited.equals(generated));
        Files.write(xmlPath(project), edited.getBytes(StandardCharsets.UTF_8));

        // The DSL unchanged: the file is not touched.
        String log = run(project, 0);
        assertTrue(log.contains("UNCHANGED(your edits kept)"), log);
        assertEquals(edited, xml(project));

        // A new field: its column comes in, every edit stays.
        model(project, CUSTOMER + ORDER + "      memo: string(200)\n");
        log = run(project, 0);
        assertTrue(log.contains("MERGED(your edits kept)"), log);
        String merged = xml(project);
        assertTrue(merged.contains("t.memo"), merged);
        assertTrue(merged.contains("<!-- reviewed by the DBA -->"), merged);
        assertTrue(merged.contains("<select id=\"countBig\""), merged);
        assertTrue(merged.contains("/* by key */"), merged);
        assertFalse(merged.contains("<<<<<<<"), merged);

        // The cache holds what was generated, not the merge.
        String inCache = GeneratedCache.read(Files.readAllBytes(new File(project, ".elasticore/cache").toPath())).get(key(project));
        assertTrue(inCache.contains("t.memo"), inCache);
        assertFalse(inCache.contains("reviewed by the DBA"), inCache);

        // The field taken out again: its column goes, the edits stay.
        model(project, CUSTOMER + ORDER);
        log = run(project, 0);
        merged = xml(project);
        assertFalse(merged.contains("t.memo"), merged);
        assertTrue(merged.contains("<!-- reviewed by the DBA -->") && merged.contains("/* by key */"), merged);
    }

    @Test
    public void theSameLinesChangedOnBothSidesStopTheBuildUntilResolved() throws Exception {
        File project = project(CUSTOMER + ORDER);
        run(project, 0);
        String generated = xml(project);
        String line = "<result property=\"orderNo\" column=\"order_no\"/>";
        assertTrue(generated.contains(line), generated);
        Files.write(xmlPath(project), generated.replace(line, line + " <!-- the business key -->")
                .getBytes(StandardCharsets.UTF_8));

        // The DSL renames the field: the line the developer edited changes too.
        model(project, CUSTOMER + ORDER.replace("orderNo:", "orderCode:"));
        String log = run(project, 1);
        assertTrue(log.contains("CONFLICT"), log);
        String marked = xml(project);
        assertTrue(marked.contains("<<<<<<< yours") && marked.contains(">>>>>>> elasticore"), marked);
        assertTrue(marked.contains("the business key") && marked.contains("column=\"order_code\""), marked);

        // Run again without resolving: still stopped, the file untouched.
        log = run(project, 1);
        assertTrue(log.contains("CONFLICT(unresolved)"), log);
        assertEquals(marked, xml(project));

        // Resolved - the new column, the developer's comment: their file from now on.
        String resolved = resolve(marked);
        assertTrue(resolved.contains("column=\"order_code\"/> <!-- the business key -->"), resolved);
        Files.write(xmlPath(project), resolved.getBytes(StandardCharsets.UTF_8));
        log = run(project, 0);
        assertTrue(log.contains("UNCHANGED(your edits kept)"), log);
        assertEquals(resolved, xml(project));
    }

    @Test
    public void aDeletedFileIsWrittenAgain() throws Exception {
        File project = project(CUSTOMER + ORDER);
        run(project, 0);
        String generated = xml(project);
        Files.delete(xmlPath(project));
        String log = run(project, 0);
        assertTrue(log.contains("WROTE"), log);
        assertEquals(generated, xml(project));
    }

    @Test
    public void aFileNoLongerProducedGoesUnlessItWasEdited() throws Exception {
        File project = project(CUSTOMER + ORDER + coupon("Coupon") + coupon("Gift"));
        run(project, 0);
        Path coupon = resource(project, "CouponRepository.xml");
        Path gift = resource(project, "GiftRepository.xml");
        assertNotNull(coupon);
        assertNotNull(gift);
        String giftKey = keyOf(project, gift);
        Files.write(gift, (read(gift) + "<!-- still used by the batch -->\n").getBytes(StandardCharsets.UTF_8));

        model(project, CUSTOMER + ORDER);
        String log = run(project, 0);
        assertFalse(Files.exists(coupon), log);
        assertTrue(log.contains("CouponRepository.xml  REMOVED"), log);
        assertTrue(Files.exists(gift), log);
        assertTrue(log.contains("GiftRepository.xml was generated, the DSL no longer produces it"), log);
        Map<String, String> entries = GeneratedCache.read(Files.readAllBytes(new File(project, ".elasticore/cache").toPath()));
        assertNull(entries.get(giftKey), "an edited file the DSL dropped is the developer's");
        assertNull(entries.get(keyOf(project, coupon)));
        assertNotNull(entries.get(key(project)));

        // The next run leaves it alone and says nothing more of it.
        log = run(project, 0);
        assertTrue(Files.exists(gift));
        assertFalse(log.contains("GiftRepository.xml was generated"), log);
    }

    @Test
    public void aFileInTheWayIsNotOverwritten() throws Exception {
        File project = project(CUSTOMER + ORDER);
        run(project, 0);
        // The developer's own mapper where a new entity's will go.
        Path dir = xmlPath(project).getParent();
        Path mine = dir.resolve("CouponRepository.xml");
        Files.write(mine, "<mapper namespace=\"mine\"/>\n".getBytes(StandardCharsets.UTF_8));
        model(project, CUSTOMER + ORDER + coupon("Coupon"));
        String log = run(project, 0);
        assertTrue(log.contains("SKIP(not in the cache)"), log);
        assertEquals("<mapper namespace=\"mine\"/>\n", read(mine));
    }

    @Test
    public void aStampedFileIsTakenOverAndAnEditedOneLeft() throws Exception {
        File project = project(CUSTOMER + ORDER + coupon("Coupon"));
        System.setProperty("elasticore.merge", "false");
        run(project, 0);
        String stamped = xml(project);
        assertTrue(stamped.startsWith("<!-- //ecd:"), stamped);
        Path coupon = resource(project, "CouponRepository.xml");
        String couponEdited = read(coupon).replace("</mapper>", "<!-- mine -->\n</mapper>");
        Files.write(coupon, couponEdited.getBytes(StandardCharsets.UTF_8));

        System.setProperty("elasticore.merge", "true");
        String log = run(project, 0);
        String taken = xml(project);
        assertFalse(taken.contains("//ecd:"), taken);
        assertEquals(stamped.substring(stamped.indexOf('\n') + 1).replace("\r\n", "\n"), taken.replace("\r\n", "\n"));
        assertTrue(log.contains("CouponRepository.xml: not in .elasticore/cache"), log);
        assertEquals(couponEdited, read(coupon));
    }

    @Test
    public void aCloneMergesAgainstTheCacheItWasGiven() throws Exception {
        File project = project(CUSTOMER + ORDER);
        run(project, 0);
        // What a clone has: the sources and the committed cache, nothing else.
        File clone = Files.createTempDirectory("elcore-merge-clone").toFile();
        projects.add(clone);
        copy(new File(project, "src").toPath(), new File(clone, "src").toPath());
        copy(new File(project, ".elasticore").toPath(), new File(clone, ".elasticore").toPath());

        String edited = xml(clone).replace("</mapper>", "    <!-- added in the clone -->\n</mapper>");
        Files.write(xmlPath(clone), edited.getBytes(StandardCharsets.UTF_8));
        model(clone, CUSTOMER + ORDER + "      memo: string(200)\n");
        String log = run(clone, 0);
        assertTrue(log.contains("MERGED(your edits kept)"), log);
        String merged = xml(clone);
        assertTrue(merged.contains("t.memo") && merged.contains("added in the clone"), merged);
    }

    @Test
    public void anUnreadableCacheStopsTheRun() throws Exception {
        File project = project(CUSTOMER + ORDER);
        run(project, 0);
        Files.write(new File(project, ".elasticore/cache").toPath(), "<<<<<<< HEAD\n".getBytes(StandardCharsets.UTF_8));
        String edited = xml(project) + "<!-- mine -->\n";
        Files.write(xmlPath(project), edited.getBytes(StandardCharsets.UTF_8));
        String log = runCapturingErr(project, 1);
        assertTrue(log.contains(".elasticore/cache cannot be read"), log);
        assertEquals(edited, xml(project));
    }

    /** Takes both sides of each conflict, the developer's line with the generated column. */
    private static String resolve(String marked) {
        StringBuilder sb = new StringBuilder();
        String[] lines = marked.split("\n", -1);
        List<String> yours = new ArrayList<String>();
        List<String> theirs = new ArrayList<String>();
        int state = 0;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].replace("\r", "");
            if (l.startsWith("<<<<<<<")) { state = 1; continue; }
            if (l.equals("=======") && state == 1) { state = 2; continue; }
            if (l.startsWith(">>>>>>>")) {
                for (String t : theirs) {
                    String comment = null;
                    for (String y : yours) if (y.contains("<!-- the business key -->")) comment = " <!-- the business key -->";
                    sb.append(t).append(comment != null && t.contains("order_code") ? comment : "").append('\n');
                }
                yours.clear();
                theirs.clear();
                state = 0;
                continue;
            }
            if (state == 1) yours.add(l);
            else if (state == 2) theirs.add(l);
            else {
                sb.append(l);
                if (i < lines.length - 1) sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String key(File project) throws IOException {
        return keyOf(project, xmlPath(project));
    }

    private static String keyOf(File project, Path file) {
        return project.toPath().relativize(file).toString().replace('\\', '/');
    }

    private static String xml(File project) throws IOException {
        return read(xmlPath(project));
    }

    private static Path xmlPath(File project) throws IOException {
        Path p = resource(project, XML);
        assertNotNull(p, XML + " was not written");
        return p;
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    private static Path resource(File project, String name) throws IOException {
        File root = new File(project, "src/main/resources");
        try (Stream<Path> all = Files.walk(root.toPath())) {
            return all.filter(p -> p.getFileName().toString().equals(name)).findFirst().orElse(null);
        }
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
            if (System.getProperty("elasticore.merge") == null) System.setProperty("elasticore.merge", "true");
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

    private File project(String model) throws IOException {
        File dir = Files.createTempDirectory("elcore-merge").toFile();
        projects.add(dir);
        File blueprint = new File(dir, "src/main/resources/blueprint/dgshop");
        assertTrue(new File(blueprint, "model").mkdirs());
        String env = String.join("\n",
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
        model(dir, model);
        return dir;
    }

    private static void model(File project, String model) throws IOException {
        Files.write(new File(project, "src/main/resources/blueprint/dgshop/model/model.yml").toPath(),
                model.getBytes(StandardCharsets.UTF_8));
    }

    private static void copy(Path from, Path to) throws IOException {
        try (Stream<Path> all = Files.walk(from)) {
            for (Path p : (Iterable<Path>) all::iterator) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target);
            }
        }
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
