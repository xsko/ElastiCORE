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
            "elasticore.ddl.databases", "elasticore.ddl.migration", "elasticore.rules.v2", "elasticore.rules.v3",
            "elasticore.rules.ts", "elasticore.rules.tsWithBuild", "elasticore.domain", "elasticore.ddl.only",
            "elasticore.publisher.rules.add", "elasticore.architecture"};

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
    private boolean force;
    /** Runs without elasticore.merge, as a build that says nothing of it. */
    private boolean unsaid;

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
        assertTrue(log.contains(XML + "  WROTE"), log);
        String xml = xml(project);
        assertTrue(xml.startsWith("<!DOCTYPE mapper"), xml);
        assertFalse(xml.contains("//ecd:"), xml);
        Path schema = resource(project, "dgshop-schema.sql");
        assertNotNull(schema);
        assertFalse(read(schema).contains("//ecd:"), read(schema));

        File cache = new File(project, ".elasticore/cache");
        assertTrue(cache.isFile());
        assertEquals("cache binary\n", new String(Files.readAllBytes(new File(project, ".elasticore/.gitattributes").toPath()),
                StandardCharsets.UTF_8));
        Map<String, String> entries = GeneratedCache.read(Files.readAllBytes(cache.toPath()));
        String key = key(project);
        assertEquals(xml.replace("\r\n", "\n"), entries.get(key));
        // A plain zip any tool reads, stored - the Java sources in it too, without their stamp.
        boolean java = false;
        try (ZipFile zip = new ZipFile(cache)) {
            Enumeration<? extends ZipEntry> all = zip.entries();
            while (all.hasMoreElements()) {
                ZipEntry e = all.nextElement();
                assertEquals(ZipEntry.STORED, e.getMethod());
                if (e.getName().endsWith(".java")) java = true;
            }
        }
        assertTrue(java);

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
    public void onByDefaultForTheThreeZeroCodeAndOffForTwoX() throws Exception {
        File project = project(CUSTOMER + ORDER);
        unsaid = true;
        run(project, 0);
        assertFalse(xml(project).contains("//ecd:"));
        assertTrue(new File(project, ".elasticore/cache").isFile());
        assertFalse(read(java(project, "PurchaseOrder.java")).contains("//ecd:"));

        // The code 2.x generates: stamped as 2.x stamps it, no cache.
        File twoX = project(CUSTOMER + ORDER, "jpa", "");
        System.setProperty("elasticore.rules.v2", "true");
        run(twoX, 0);
        assertFalse(new File(twoX, ".elasticore").exists());
        assertTrue(read(java(twoX, "PurchaseOrder.java")).startsWith("//ecd:"));

        // Said off, the 3.0 code is stamped as before.
        File off = project(CUSTOMER + ORDER);
        System.clearProperty("elasticore.rules.v2");
        unsaid = false;
        System.setProperty("elasticore.merge", "false");
        run(off, 0);
        assertTrue(xml(off).startsWith("<!-- //ecd:"), xml(off));
        assertFalse(new File(off, ".elasticore").exists());
    }

    @Test
    public void aJavaSourceKeepsItsEditsAndImportsBesideTheDsls() throws Exception {
        File project = project(CUSTOMER + ORDER, "jpa", "");
        run(project, 0);
        Path dto = java(project, "PurchaseOrderDTO.java");
        String generated = read(dto);
        // An import after the last one, a method before the closing brace.
        int lastImport = generated.lastIndexOf("\nimport ");
        int eol = generated.indexOf('\n', lastImport + 1);
        int close = generated.lastIndexOf('}');
        String edited = generated.substring(0, eol + 1) + "import java.util.Objects;\n"
                + generated.substring(eol + 1, close)
                + "    public boolean sameNo(String no) { return Objects.equals(no, getOrderNo()); }\n"
                + generated.substring(close);
        Files.write(dto, edited.getBytes(StandardCharsets.UTF_8));

        // A date field: an import the DSL adds beside the developer's, a field where they added a method.
        model(project, CUSTOMER + ORDER + "      madeOn: date\n");
        String log = run(project, 0);
        assertTrue(log.contains("PurchaseOrderDTO.java  MERGED(your edits kept)"), log);
        String merged = read(dto);
        assertTrue(merged.contains("import java.util.Objects;") && merged.contains("import java.time.LocalDate;"), merged);
        assertTrue(merged.contains("madeOn") && merged.contains("public boolean sameNo("), merged);
        assertFalse(merged.contains("<<<<<<<") || merged.contains("//ecd:"), merged);
    }

    @Test
    public void aSkeletonTakesTheDslsChangesWhereTheyMergeAndIsLeftWhereTheyDoNot() throws Exception {
        File project = project(CUSTOMER + ORDER, "jpa", "");
        run(project, 0);
        Path control = java(project, "CustomerController.java");
        String skeleton = read(control);
        assertTrue(skeleton.contains("// Initially generated by ElastiCORE;") && !skeleton.contains("//ecd:"), skeleton);
        assertTrue(skeleton.contains("@DeleteMapping"), skeleton);

        // Never edited: it follows the DSL - @readonly takes the writes away.
        model(project, CUSTOMER.replace("@expose(20)", "@expose(20) @readonly") + ORDER);
        String log = run(project, 0);
        assertFalse(read(control).contains("@DeleteMapping"), log + read(control));

        // Edited away from what the DSL changes: the change comes in, the edit stays.
        model(project, CUSTOMER + ORDER);
        run(project, 0);
        String writable = read(control);
        int open = writable.indexOf('\n', writable.indexOf("class CustomerController"));
        Files.write(control, (writable.substring(0, open + 1) + "    // the team's own endpoints\n" + writable.substring(open + 1))
                .getBytes(StandardCharsets.UTF_8));
        model(project, CUSTOMER.replace("@expose(20)", "@expose(20) @readonly") + ORDER);
        log = run(project, 0);
        assertTrue(log.contains("CustomerController.java  MERGED(your edits kept)"), log);
        assertFalse(read(control).contains("@DeleteMapping"), read(control));
        assertTrue(read(control).contains("// the team's own endpoints"), read(control));

        // Edited where the DSL changes it: the developer's file is left as it is, and said - no marks.
        model(project, CUSTOMER + ORDER);
        run(project, 0);
        String again = read(control);
        String mine = again.replaceFirst("(@DeleteMapping[^\\r\\n]*)", "$1 // deletes softly in our build");
        assertFalse(mine.equals(again), again);
        Files.write(control, mine.getBytes(StandardCharsets.UTF_8));
        model(project, CUSTOMER.replace("@expose(20)", "@expose(20) @readonly") + ORDER);
        log = run(project, 0);
        assertTrue(log.contains("CustomerController.java  KEPT(yours"), log);
        assertEquals(mine, read(control));
        assertFalse(read(control).contains("<<<<<<<"));
        assertTrue(log.contains("CustomerController.java: the DSL changes this file of yours"), log);
    }

    @Test
    public void aRuleAddedLaterReachesAnEditedSkeleton() throws Exception {
        File project = project(CUSTOMER + ORDER, "jpa", "");
        run(project, 0);
        Path control = java(project, "CustomerController.java");
        String written = read(control);
        int open = written.indexOf('\n', written.indexOf("class CustomerController"));
        String mine = written.substring(0, open + 1) + "    // the team's own endpoints\n" + written.substring(open + 1);
        Files.write(control, mine.getBytes(StandardCharsets.UTF_8));

        // The rule comes after the edit: it lands in the developer's controller, the edit stays.
        System.setProperty("elasticore.publisher.rules.add", "com.acme.probe.AddedLaterRules");
        String log = run(project, 0);
        assertTrue(log.contains("CustomerController.java  MERGED(your edits kept)"), log);
        String merged = read(control).replace("\r\n", "\n");
        assertTrue(merged.contains("@WebAdapter\npublic class CustomerController {")
                && merged.contains("import com.acme.arch.WebAdapter;"), merged);
        assertTrue(merged.contains("// the team's own endpoints"), merged);

        // Taken out again, it leaves the controller as the developer had it.
        System.clearProperty("elasticore.publisher.rules.add");
        log = run(project, 0);
        assertTrue(log.contains("CustomerController.java  MERGED(your edits kept)"), log);
        assertEquals(mine.replace("\r\n", "\n"), read(control).replace("\r\n", "\n"));
    }

    @Test
    public void supportAiTurnedOnLaterReachesAnEditedService() throws Exception {
        File project = project(CUSTOMER + ORDER, "jpa", "    supportAI: false");
        run(project, 0);
        Path service = java(project, "CustomerService.java");
        String written = read(service);
        assertTrue(written.contains("// Initially generated by ElastiCORE;") && !written.contains("@Tool"), written);
        int close = written.lastIndexOf('}');
        String mine = written.substring(0, close) + "    public int vipCount() { return 0; }\n" + written.substring(close);
        Files.write(service, mine.getBytes(StandardCharsets.UTF_8));

        Path env = new File(project, "src/main/resources/blueprint/dgshop/env.yml").toPath();
        Files.write(env, read(env).replace("supportAI: false", "supportAI: true").getBytes(StandardCharsets.UTF_8));
        String log = run(project, 0);
        assertTrue(log.contains("CustomerService.java  MERGED(your edits kept)"), log);
        String merged = read(service);
        assertTrue(merged.contains("@Tool") && merged.contains("public int vipCount()"), merged);
    }

    @Test
    public void aFactoryWithItsBodyWrittenFollowsTheSignatureOrSaysSo() throws Exception {
        System.setProperty("elasticore.architecture", "ddd");
        String customer = CUSTOMER.replace("@expose(20)", "@expose(20) @factory(register)");
        File project = project(customer + ORDER, "jpa", "");
        run(project, 0);
        Path factory = java(project, "CustomerFactory.java");
        String withEmail = customer.replace("      name: string(50)! @search(like)\n",
                "      name: string(50)! @search(like)\n      email: string(80)!\n") + ORDER;

        // Never edited: the signature follows the root's required fields.
        model(project, withEmail);
        run(project, 0);
        assertTrue(read(factory).contains("register(String name, String email)"), read(factory));

        // The body written: a new required field changes the line above it, so the file is kept and said.
        model(project, customer + ORDER);
        run(project, 0);
        String written = read(factory);
        java.util.regex.Matcher stub = java.util.regex.Pattern
                .compile("(?s)throw new UnsupportedOperationException\\(.*?\"\\);").matcher(written);
        assertTrue(stub.find(), written);
        String mine = written.substring(0, stub.start()) + "return new Customer();" + written.substring(stub.end());
        Files.write(factory, mine.getBytes(StandardCharsets.UTF_8));
        model(project, withEmail);
        String log = run(project, 0);
        assertTrue(log.contains("CustomerFactory.java  KEPT(yours"), log);
        assertTrue(log.contains("CustomerFactory.java: the DSL changes this file of yours where you changed it too"), log);
        assertEquals(mine, read(factory));
    }

    @Test
    public void aRuleAddedLaterReachesTheEditedClassOfASplitRoot() throws Exception {
        System.setProperty("elasticore.architecture", "ddd");
        File project = project(CUSTOMER + ORDER, "jpa", "");
        run(project, 0);
        Path entity = java(project, "Customer.java");
        String written = read(entity);
        assertTrue(written.contains("// Initially generated by ElastiCORE;") && written.contains("extends CustomerBase"),
                written);
        int close = written.lastIndexOf('}');
        String mine = written.substring(0, close) + "    public boolean vip() { return false; }\n" + written.substring(close);
        Files.write(entity, mine.getBytes(StandardCharsets.UTF_8));

        System.setProperty("elasticore.publisher.rules.add", "com.acme.probe.AddedLaterRules");
        String log = run(project, 0);
        assertTrue(log.contains("Customer.java  MERGED(your edits kept)"), log);
        String merged = read(entity).replace("\r\n", "\n");
        assertTrue(merged.contains("@AggregateRoot\n") && merged.contains("import org.jmolecules.ddd.annotation.AggregateRoot;"),
                merged);
        assertTrue(merged.contains("public boolean vip()"), merged);
        assertFalse(log.contains("add them by hand"), log);
        assertTrue(read(java(project, "CustomerBase.java")).contains("@AggregateRoot"), log);
    }

    @Test
    public void aStampedJavaSourceIsTakenOverWhenItsBaseIsConfirmed() throws Exception {
        File project = project(CUSTOMER + ORDER, "jpa", "");
        System.setProperty("elasticore.merge", "false");
        run(project, 0);
        Path dto = java(project, "PurchaseOrderDTO.java");
        Path srch = java(project, "PurchaseOrderSrchDTO.java");
        String stamped = read(dto);
        assertTrue(stamped.startsWith("//ecd:"), stamped);
        String edited = stamped.substring(0, stamped.lastIndexOf('}')) + "    // mine\n}\n";
        Files.write(dto, edited.getBytes(StandardCharsets.UTF_8));
        // The other is edited and then the model changes before merging is on: its base is not this run's.
        Files.write(srch, (read(srch).substring(0, read(srch).lastIndexOf('}')) + "    // mine too\n}\n")
                .getBytes(StandardCharsets.UTF_8));

        System.setProperty("elasticore.merge", "true");
        String log = run(project, 0);
        // The model as it was: what this run generates is what the stamp's hash names - the base.
        assertTrue(log.contains("PurchaseOrderDTO.java  UNCHANGED(your edits kept)"), log);
        assertFalse(read(dto).contains("//ecd:"), read(dto));
        assertTrue(read(dto).contains("// mine"), read(dto));
        model(project, CUSTOMER + ORDER + "      madeOn: date\n");
        log = run(project, 0);
        assertTrue(log.contains("PurchaseOrderDTO.java  MERGED(your edits kept)"), log);
        assertTrue(read(dto).contains("madeOn") && read(dto).contains("// mine"), read(dto));

        // A base no run can produce again: left to the stamp's rules, as before.
        File other = project(CUSTOMER + ORDER, "jpa", "");
        System.setProperty("elasticore.merge", "false");
        run(other, 0);
        Path otherDto = java(other, "PurchaseOrderDTO.java");
        String otherText = read(otherDto);
        String mineStamped = otherText.substring(0, otherText.lastIndexOf('}')) + "    // mine\n}\n";
        Files.write(otherDto, mineStamped.getBytes(StandardCharsets.UTF_8));
        model(other, CUSTOMER + ORDER + "      madeOn: date\n");
        System.setProperty("elasticore.merge", "true");
        log = run(other, 0);
        assertTrue(log.contains("PurchaseOrderDTO.java  SKIP(user-modified"), log);
        assertEquals(mineStamped, read(otherDto));
        // And the summary says where it stands.
        assertTrue(log.contains("/ 1 still stamped (edited, base not confirmed) ==="), log);
        assertTrue(log.contains("still stamped: src/main/java/io/acme/dgshop/application/dto/PurchaseOrderDTO.java"), log);
    }

    @Test
    public void aTwoXProjectMovingToThreeZeroMergesItsEditsIntoTheNewCode() throws Exception {
        File project = project(CUSTOMER + ORDER, "jpa", "");
        System.setProperty("elasticore.rules.v2", "true");
        System.setProperty("elasticore.merge", "false");
        run(project, 0);
        // As 2.x stamped them.
        try (Stream<Path> all = Files.walk(new File(project, "src/main/java").toPath())) {
            for (Path p : (Iterable<Path>) all::iterator) {
                if (!p.toString().endsWith(".java")) continue;
                String s = read(p);
                if (s.startsWith("//ecd:")) Files.write(p, s.replaceFirst("_V3\\.0", "_V2.2").getBytes(StandardCharsets.UTF_8));
            }
        }
        Path dto = java(project, "PurchaseOrderDTO.java");
        String twoX = read(dto);
        Files.write(dto, (twoX.substring(0, twoX.lastIndexOf('}')) + "    // kept through the upgrade\n}\n")
                .getBytes(StandardCharsets.UTF_8));

        System.clearProperty("elasticore.rules.v2");
        System.setProperty("elasticore.rules.v3", "true");
        System.clearProperty("elasticore.merge");
        unsaid = true;
        // Over one domain, the other domains' edited files would never find their bases.
        System.setProperty("elasticore.domain", "dgshop");
        String refused = runCapturingErr(project, 1);
        assertTrue(refused.contains("run it without -Pdomain once"), refused);
        assertTrue(read(dto).startsWith("//ecd:"), "nothing was written");
        assertFalse(new File(project, ".elasticore/cache").exists());
        System.clearProperty("elasticore.domain");

        String log = run(project, 0);
        assertTrue(log.contains("[elcore3] upgrade   : the code 2.x generates"), log);
        String moved = read(dto);
        assertFalse(moved.contains("//ecd:"), moved);
        assertTrue(moved.contains("// kept through the upgrade"), moved);
        assertTrue(log.contains("PurchaseOrderDTO.java  MERGED(your edits kept)")
                || log.contains("PurchaseOrderDTO.java  UNCHANGED(your edits kept)"), log);
        assertTrue(new File(project, ".elasticore/cache").isFile());

        // The upgrade run generated what a run without it does: the next one changes nothing.
        byte[] cache = Files.readAllBytes(new File(project, ".elasticore/cache").toPath());
        String again = run(project, 0);
        assertFalse(again.contains("[elcore3] upgrade"), again);
        assertFalse(java.util.regex.Pattern.compile("\\.java  (WROTE|MERGED)").matcher(again).find(), again);
        assertArrayEquals(cache, Files.readAllBytes(new File(project, ".elasticore/cache").toPath()));
    }

    @Test
    public void everyKindOfGeneratedTextIsMerged() throws Exception {
        // The project DDL and the messages, edited, then the model changed.
        System.setProperty("elasticore.ddl.databases", "h2,postgresql");
        File project = project(CUSTOMER + ORDER, "mybatis", "");
        run(project, 0);
        Path ddl = new File(project, "src/main/resources/db/schema-postgresql.sql").toPath();
        Path messages = resource(project, "dgshop-messages.properties");
        assertNotNull(messages);
        assertFalse(read(ddl).contains("//ecd:"), read(ddl));
        assertFalse(read(messages).contains("//ecd:"), read(messages));
        Files.write(ddl, ("-- reviewed\n" + read(ddl)).getBytes(StandardCharsets.UTF_8));
        Files.write(messages, (read(messages) + "custom.greeting=hello\n").getBytes(StandardCharsets.UTF_8));

        model(project, CUSTOMER + ORDER + "      memo: string(200)\n");
        String log = run(project, 0);
        assertTrue(read(ddl).contains("-- reviewed") && read(ddl).contains("memo"), read(ddl));
        assertTrue(read(messages).contains("custom.greeting=hello"), read(messages));
        assertTrue(log.contains("schema-postgresql.sql  MERGED(your edits kept)"), log);
    }

    @Test
    public void editsOfAFileThatWasAlwaysOverwrittenSurviveALostCache() throws Exception {
        File project = project(CUSTOMER + ORDER);
        run(project, 0);
        Path messages = resource(project, "dgshop-messages.properties");
        assertNotNull(messages);
        String edited = read(messages) + "custom.greeting=hello\n";
        Files.write(messages, edited.getBytes(StandardCharsets.UTF_8));

        // The cache deleted - or replaced by the other side of a git conflict - with its folder left.
        Files.delete(new File(project, ".elasticore/cache").toPath());
        String log = run(project, 0);
        assertEquals(edited, read(messages), log);
        assertTrue(log.contains("dgshop-messages.properties: not in .elasticore/cache"), log);

        // The folder gone as well: the project's first merging run, which writes it as it always did.
        delete(new File(project, ".elasticore").toPath());
        run(project, 0);
        assertFalse(read(messages).contains("custom.greeting"), read(messages));
    }

    @Test
    public void aRunOverOneDomainRemovesNothing() throws Exception {
        File project = project(CUSTOMER + ORDER + coupon("Coupon"));
        run(project, 0);
        Path coupon = resource(project, "CouponRepository.xml");
        model(project, CUSTOMER + ORDER);
        System.setProperty("elasticore.domain", "dgshop");
        try {
            run(project, 0);
        } finally {
            System.clearProperty("elasticore.domain");
        }
        assertTrue(Files.exists(coupon), "a run over one domain cannot tell another's file from a stale one");
        run(project, 0);
        assertFalse(Files.exists(coupon));
    }

    @Test
    public void aCacheCutShortOrNamingPathsOutsideIsRefused() throws Exception {
        java.util.TreeMap<String, String> entries = new java.util.TreeMap<String, String>();
        entries.put("src/main/resources/a.xml", "<a/>\n");
        entries.put("src/main/resources/b.xml", "<b/>\n");
        byte[] whole = GeneratedCache.zip(entries);
        assertEquals(entries, GeneratedCache.read(whole));
        assertRefused(java.util.Arrays.copyOf(whole, whole.length - 30));
        assertRefused(java.util.Arrays.copyOf(whole, 40));
        for (String outside : new String[]{"../evil.xml", "src/../../evil.xml", "/etc/evil", "C:/evil.xml"}) {
            java.util.TreeMap<String, String> bad = new java.util.TreeMap<String, String>();
            bad.put(outside, "x");
            assertRefused(GeneratedCache.zip(bad));
        }
    }

    private static void assertRefused(byte[] cache) {
        try {
            GeneratedCache.read(cache);
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError("read a cache it should have refused");
    }

    @Test
    public void aConflictInTheDdlAloneFailsTheBuild() throws Exception {
        // A project that keeps 2.x's code and merges its DDL: the code comes from
        // a run that writes no DDL and merges nothing (in a build, the 2.x
        // generator), the DDL from elcoreDdl alone, after it.
        System.setProperty("elasticore.rules.v2", "true");
        File project = project(CUSTOMER + ORDER, "jpa", "");
        System.setProperty("elasticore.merge", "false");
        run(project, 0);
        System.setProperty("elasticore.merge", "true");
        System.setProperty("elasticore.ddl.databases", "h2");
        System.setProperty("elasticore.ddl.only", "true");
        try {
            run(project, 0);
            Path ddl = new File(project, "src/main/resources/db/schema-h2.sql").toPath();
            String generated = read(ddl);
            assertFalse(generated.contains("//ecd:"), generated);
            String line = null;
            for (String l : generated.split("\r?\n")) if (l.contains("order_no")) line = l;
            assertNotNull(line, generated);
            Files.write(ddl, generated.replace(line, line + " -- the business key").getBytes(StandardCharsets.UTF_8));

            System.clearProperty("elasticore.ddl.only");
            System.clearProperty("elasticore.ddl.databases");
            model(project, CUSTOMER + ORDER.replace("orderNo:", "orderCode:"));
            System.setProperty("elasticore.merge", "false");
            run(project, 0);
            System.setProperty("elasticore.merge", "true");
            System.setProperty("elasticore.ddl.databases", "h2");
            System.setProperty("elasticore.ddl.only", "true");
            String log = run(project, 1);
            assertTrue(log.contains("schema-h2.sql: 1 place(s)"), log);
            assertTrue(read(ddl).contains("<<<<<<< yours"), read(ddl));
        } finally {
            System.clearProperty("elasticore.ddl.only");
        }
    }

    @Test
    public void theOrmXmlIsMerged() throws Exception {
        File project = project(CUSTOMER + ORDER, "jpa", "    mapping: xml");
        run(project, 0);
        Path orm = new File(project, "src/main/resources/META-INF/orm.xml").toPath();
        String generated = read(orm);
        assertTrue(generated.contains("Generated by ElastiCORE (mapping: xml)"), generated);
        Files.write(orm, generated.replace("</entity-mappings>", "    <!-- mine -->\n</entity-mappings>")
                .getBytes(StandardCharsets.UTF_8));

        model(project, CUSTOMER + ORDER + "      memo: string(200)\n");
        String log = run(project, 0);
        String merged = read(orm);
        assertTrue(merged.contains("<!-- mine -->") && merged.contains("memo"), log + merged);
    }

    @Test
    public void aRunOverOneDomainKeepsTheOtherDomainsMappingsInTheOrmXml() throws Exception {
        File project = project(CUSTOMER + ORDER, "jpa", "    mapping: xml");
        billing(project, "jpa", "    mapping: xml");
        run(project, 0);
        Path orm = new File(project, "src/main/resources/META-INF/orm.xml").toPath();
        String whole = read(orm);
        assertTrue(whole.contains("io.acme.billing.entity.Invoice") && whole.contains("io.acme.dgshop.entity.Customer"), whole);

        model(project, CUSTOMER + ORDER + "      memo: string(200)\n");
        System.setProperty("elasticore.domain", "dgshop");
        try {
            run(project, 0);
        } finally {
            System.clearProperty("elasticore.domain");
        }
        String one = read(orm);
        assertTrue(one.contains("io.acme.billing.entity.Invoice"), "the other domain's mapping went: " + one);
        assertTrue(one.contains("name=\"memo\""), one);

        // A whole run writes what the run over one domain left.
        String log = run(project, 0);
        assertEquals(one, read(orm), log);
        assertTrue(log.contains("orm.xml  UNCHANGED"), log);
    }

    @Test
    public void aRunOverOneDomainKeepsTheOtherDomainsMappingsBesideTheDevelopersOwnOrmXml() throws Exception {
        File project = project(CUSTOMER + ORDER, "jpa", "    mapping: xml");
        billing(project, "jpa", "    mapping: xml");
        Path own = new File(project, "src/main/resources/META-INF/orm.xml").toPath();
        Files.createDirectories(own.getParent());
        String mine = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<entity-mappings/>\n";
        Files.write(own, mine.getBytes(StandardCharsets.UTF_8));
        run(project, 0);
        Path beside = new File(project, "src/main/resources/META-INF/elasticore-orm.xml").toPath();
        assertTrue(read(beside).contains("io.acme.billing.entity.Invoice"), read(beside));

        model(project, CUSTOMER + ORDER + "      memo: string(200)\n");
        System.setProperty("elasticore.domain", "dgshop");
        try {
            run(project, 0);
        } finally {
            System.clearProperty("elasticore.domain");
        }
        String one = read(beside);
        assertTrue(one.contains("io.acme.billing.entity.Invoice") && one.contains("name=\"memo\""), one);
        assertEquals(mine, read(own));
    }

    @Test
    public void aRunOverOneDomainDropsWhatThatDomainNoLongerMapsAndKeepsEditsToTheOthers() throws Exception {
        File project = project(CUSTOMER + ORDER, "jpa", "    mapping: xml");
        billing(project, "jpa", "    mapping: xml");
        run(project, 0);
        Path orm = new File(project, "src/main/resources/META-INF/orm.xml").toPath();
        String invoice = "<entity class=\"io.acme.billing.entity.Invoice\" access=\"FIELD\">";
        String whole = read(orm).replace("\r\n", "\n");
        assertTrue(whole.contains(invoice + "\n"), whole);
        Files.write(orm, whole.replace(invoice + "\n", invoice + "\n        <!-- billing's note -->\n")
                .getBytes(StandardCharsets.UTF_8));

        // dgshop loses PurchaseOrder and moves its classes to another package.
        model(project, CUSTOMER);
        Path env = new File(project, "src/main/resources/blueprint/dgshop/env.yml").toPath();
        Files.write(env, read(env).replace("entity: io.acme.dgshop.entity", "entity: io.acme.dgshop.model")
                .getBytes(StandardCharsets.UTF_8));
        System.setProperty("elasticore.domain", "dgshop");
        String log;
        try {
            log = run(project, 0);
        } finally {
            System.clearProperty("elasticore.domain");
        }
        String one = read(orm);
        assertTrue(one.contains("<!-- billing's note -->") && one.contains("io.acme.billing.entity.Invoice"), log + one);
        assertTrue(one.contains("io.acme.dgshop.model.Customer"), one);
        assertFalse(one.contains("io.acme.dgshop.entity."), "the moved classes' old mapping went: " + one);
        assertFalse(one.contains("PurchaseOrder"), "the removed entity's mapping went: " + one);
    }

    @Test
    public void aRunOverOneDomainKeepsTheOtherDomainsMappingsWithoutTheCache() throws Exception {
        System.setProperty("elasticore.merge", "false");
        File project = project(CUSTOMER + ORDER, "jpa", "    mapping: xml");
        billing(project, "jpa", "    mapping: xml");
        run(project, 0);
        Path orm = new File(project, "src/main/resources/META-INF/orm.xml").toPath();
        model(project, CUSTOMER + ORDER + "      memo: string(200)\n");
        System.setProperty("elasticore.domain", "dgshop");
        try {
            run(project, 0);
        } finally {
            System.clearProperty("elasticore.domain");
        }
        String one = read(orm);
        assertTrue(one.contains("io.acme.billing.entity.Invoice") && one.contains("name=\"memo\""), one);
        run(project, 0);
        assertEquals(one, read(orm));
    }

    @Test
    public void theClientAndTheServerShareTheCache() throws Exception {
        File project = project(CUSTOMER + ORDER, "jpa", "");
        run(project, 0);
        System.setProperty("elasticore.rules.ts", "true");
        run(project, 0);
        Path ts = typescript(project, "PurchaseOrderDTO.ts");
        assertNotNull(ts, "the client was not written");
        assertFalse(read(ts).contains("//ecd:"), read(ts));
        Files.write(ts, (read(ts) + "// mine\n").getBytes(StandardCharsets.UTF_8));

        // The server's run: the client's files are not its stale ones.
        System.clearProperty("elasticore.rules.ts");
        String log = run(project, 0);
        assertTrue(Files.exists(ts), log);
        assertFalse(log.contains("PurchaseOrderDTO.ts was generated"), log);
        Map<String, String> entries = GeneratedCache.read(Files.readAllBytes(new File(project, ".elasticore/cache").toPath()));
        assertNotNull(entries.get(keyOf(project, ts)));

        // The model changes: the server's run, which does not write the client,
        // knows the client - stampless - from the cache, and says it is stale.
        model(project, CUSTOMER + ORDER + "      memo: string(200)\n");
        log = runCapturingErr(project, 0);
        assertTrue(log.contains("made from an older model"), log);

        // And the client's run keeps the server's.
        System.setProperty("elasticore.rules.ts", "true");
        log = run(project, 0);
        assertTrue(read(ts).contains("// mine") && read(ts).contains("memo"), log + read(ts));
        entries = GeneratedCache.read(Files.readAllBytes(new File(project, ".elasticore/cache").toPath()));
        boolean server = false;
        for (String k : entries.keySet()) if (!k.endsWith(".ts")) server = true;
        assertTrue(server, entries.keySet().toString());
    }

    @Test
    public void customBlocksOfAStampedFileAreKept() throws Exception {
        File project = project(CUSTOMER + ORDER);
        System.setProperty("elasticore.merge", "false");
        run(project, 0);
        String stamped = xml(project);
        // A custom block, which 2.x left out of the hash.
        String withBlock = stamped.replace("</mapper>",
                "    <!-- // !---------- -->\n    <select id=\"mine\" resultType=\"long\">SELECT 1</select>\n"
                        + "    <!-- // ------------! -->\n</mapper>");
        Files.write(xmlPath(project), withBlock.getBytes(StandardCharsets.UTF_8));

        System.setProperty("elasticore.merge", "true");
        model(project, CUSTOMER + ORDER + "      memo: string(200)\n");
        String log = run(project, 0);
        String merged = xml(project);
        assertFalse(merged.contains("//ecd:"), merged);
        assertTrue(merged.contains("<select id=\"mine\"") && merged.contains("t.memo"), log + merged);
    }

    @Test
    public void forceDoesNotThrowEditsAway() throws Exception {
        // The samples' builds set elasticore.rules.force on every run.
        force = true;
        File project = project(CUSTOMER + ORDER);
        run(project, 0);
        String edited = xml(project).replaceFirst("(</resultMap>\\r?\\n)", "$1\n    <!-- test-->\n");
        Files.write(xmlPath(project), edited.getBytes(StandardCharsets.UTF_8));

        String log = run(project, 0);
        assertTrue(log.contains("UNCHANGED(your edits kept)"), log);
        assertEquals(edited, xml(project));

        model(project, CUSTOMER + ORDER + "      memo: string(200)\n");
        log = run(project, 0);
        assertTrue(log.contains("MERGED(your edits kept)"), log);
        String merged = xml(project);
        assertTrue(merged.contains("<!-- test-->") && merged.contains("t.memo"), merged);
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
    public void aClassNoLongerProducedStaysWhileACodeOfTheDevelopersUsesIt() throws Exception {
        File project = project(CUSTOMER + ORDER + coupon("Coupon"));
        run(project, 0);
        Path dto = java(project, "CouponDTO.java");
        Path xml = resource(project, "CouponRepository.xml");
        assertNotNull(xml);
        java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("(?m)^package ([\\w.]+);").matcher(read(dto));
        assertTrue(pkg.find());
        // The developer's report, in a package of its own, reads the DTO.
        Path report = new File(project, "src/main/java/io/acme/dgshop/report/CouponReport.java").toPath();
        Files.createDirectories(report.getParent());
        Files.write(report, ("package io.acme.dgshop.report;\n\nimport " + pkg.group(1) + ".CouponDTO;\n\n"
                + "public class CouponReport {\n    CouponDTO last;\n}\n").getBytes(StandardCharsets.UTF_8));

        model(project, CUSTOMER + ORDER);
        String log = run(project, 0);
        assertTrue(Files.exists(dto), log);
        assertTrue(log.contains("CouponDTO.java is no longer generated, but src/main/java/io/acme/dgshop/report/"
                + "CouponReport.java still uses it - kept"), log);
        assertFalse(Files.exists(xml), "what nothing uses goes as before");
        assertTrue(Files.exists(report));

        // Once the report no longer needs it, it goes.
        Files.write(report, "package io.acme.dgshop.report;\n\npublic class CouponReport {\n}\n"
                .getBytes(StandardCharsets.UTF_8));
        log = run(project, 0);
        assertFalse(Files.exists(dto), log);
        assertTrue(log.contains("CouponDTO.java  REMOVED"), log);
        Map<String, String> entries = GeneratedCache.read(Files.readAllBytes(new File(project, ".elasticore/cache").toPath()));
        assertNull(entries.get(keyOf(project, dto)));
    }

    @Test
    public void theCodeThatKeepsAClassIsNamedInTheProjectAlsoThroughALink() throws Exception {
        // The project reached through a link - as /var is /private/var on macOS,
        // or an 8.3 short name on Windows: the sources are read by their canonical
        // file, and the line still names the developer's file in the project.
        File project = project(CUSTOMER + ORDER + coupon("Coupon"));
        File link = new File(Files.createTempDirectory("elcore-merge-link").toFile(), "project");
        projects.add(link.getParentFile());
        try {
            Files.createSymbolicLink(link.toPath(), project.toPath());
        } catch (IOException | UnsupportedOperationException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "no symbolic link here: " + e);
        }
        run(link, 0);
        Path dto = java(project, "CouponDTO.java");
        java.util.regex.Matcher pkg =
                java.util.regex.Pattern.compile("(?m)^package ([\\w.]+);").matcher(read(dto));
        assertTrue(pkg.find());
        Path report = new File(project, "src/main/java/io/acme/dgshop/report/CouponReport.java").toPath();
        Files.createDirectories(report.getParent());
        Files.write(
                report,
                ("package io.acme.dgshop.report;\n\nimport " + pkg.group(1) + ".CouponDTO;\n\n"
                                + "public class CouponReport {\n    CouponDTO last;\n}\n")
                        .getBytes(StandardCharsets.UTF_8));

        model(project, CUSTOMER + ORDER);
        String log = run(link, 0);
        assertTrue(
                log.contains("CouponDTO.java is no longer generated, but src/main/java/io/acme/dgshop/report/"
                        + "CouponReport.java still uses it - kept"),
                log);
    }

    @Test
    public void aKeptMapperKeepsItsXmlAndWhatItUses() throws Exception {
        File project = project(CUSTOMER + ORDER + coupon("Coupon"));
        run(project, 0);
        Path repo = java(project, "CouponRepository.java");
        Path xml = resource(project, "CouponRepository.xml");
        Path core = java(project, "CouponCoreService.java");
        assertNotNull(xml);
        report(project, "import " + packageOf(repo) + ".CouponRepository;", "CouponRepository repo;");

        model(project, CUSTOMER + ORDER);
        String log = run(project, 0);
        assertTrue(Files.exists(repo), log);
        assertTrue(Files.exists(xml), "the mapper's statements stay with it");
        assertTrue(log.contains("CouponRepository.xml is no longer generated, but"), log);
        assertFalse(Files.exists(core), "the service nothing kept uses goes");
    }

    @Test
    public void theCodeTwoXGeneratesTidiesUpAsItAlwaysHas() throws Exception {
        File project = project(CUSTOMER + ORDER + coupon("Coupon"), "jpa", "");
        System.setProperty("elasticore.rules.v2", "true");
        System.setProperty("elasticore.merge", "false");
        run(project, 0);
        Path repo = java(project, "CouponRepository.java");
        report(project, "import " + packageOf(repo) + ".CouponRepository;", "CouponRepository repo;");

        model(project, CUSTOMER + ORDER);
        String log = run(project, 0);
        assertFalse(Files.exists(repo), log);
        assertFalse(log.contains("still uses it"), log);
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

    /** The developer's class in a package of its own, using what the lines say. */
    private static Path report(File project, String imports, String field) throws IOException {
        Path report = new File(project, "src/main/java/io/acme/dgshop/report/CouponReport.java").toPath();
        Files.createDirectories(report.getParent());
        Files.write(report, ("package io.acme.dgshop.report;\n\n" + imports + "\n\npublic class CouponReport {\n    "
                + field + "\n}\n").getBytes(StandardCharsets.UTF_8));
        return report;
    }

    private static String packageOf(Path source) throws IOException {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^package ([\\w.]+);").matcher(read(source));
        assertTrue(m.find(), source.toString());
        return m.group(1);
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

    private static Path java(File project, String name) throws IOException {
        Path p = under(new File(project, "src/main/java"), name);
        assertNotNull(p, name + " was not written");
        return p;
    }

    private static Path typescript(File project, String name) throws IOException {
        return under(new File(project, "src/main/ts"), name);
    }

    private static Path resource(File project, String name) throws IOException {
        return under(new File(project, "src/main/resources"), name);
    }

    private static Path under(File root, String name) throws IOException {
        if (!root.isDirectory()) return null;
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
            if (force) System.setProperty("elasticore.rules.force", "true");
            else System.clearProperty("elasticore.rules.force");
            if (unsaid) System.clearProperty("elasticore.merge");
            else if (System.getProperty("elasticore.merge") == null) System.setProperty("elasticore.merge", "true");
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
        return project(model, "mybatis", "");
    }

    private File project(String model, String mode, String config) throws IOException {
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
                "    mode: " + mode,
                "    j2ee: jakarta",
                config,
                "  namespace:",
                "    base: io.acme.dgshop",
                "    entity: io.acme.dgshop.entity",
                "    repository: io.acme.dgshop.repository",
                "");
        Files.write(new File(blueprint, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        model(dir, model);
        return dir;
    }

    /** A second domain, billing, beside dgshop in the same module. */
    private static void billing(File project, String mode, String config) throws IOException {
        File blueprint = new File(project, "src/main/resources/blueprint/billing");
        assertTrue(new File(blueprint, "model").mkdirs());
        String env = String.join("\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: billing",
                "    framework: springboot",
                "    mode: " + mode,
                "    j2ee: jakarta",
                config,
                "  namespace:",
                "    base: io.acme.billing",
                "    entity: io.acme.billing.entity",
                "    repository: io.acme.billing.repository",
                "");
        Files.write(new File(blueprint, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        String model = String.join("\n",
                "entity:",
                "  Invoice:",
                "    meta: entity @expose(20)",
                "    fields:",
                "      invoiceId: long @id @sequence",
                "      total: long!",
                "");
        Files.write(new File(blueprint, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
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
