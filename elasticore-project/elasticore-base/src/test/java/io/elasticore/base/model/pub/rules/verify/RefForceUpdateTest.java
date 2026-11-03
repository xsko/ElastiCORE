package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.extract.ModelExtractor;
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
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * assigneeId: string @ref(assignee.employeeId) @forceupdate takes the relation
 * away when a full update sends no id, and a PATCH - which skips what it does
 * not send - leaves it. Both generators write the same else after the
 * attaching if, in both overloads (with and without an EntityManager), in
 * Java and Kotlin. What it cannot mean is refused: a relation that is
 * required, a target that is not the key, a path past relation.key. Where the
 * mapper does not write the relation at all, it is said that it does nothing.
 * The behavior itself runs in the template's AttachmentArticleForceUpdateTest.
 */
public class RefForceUpdateTest {

    private static final String DESK = String.join(
            "\n",
            "entity:",
            "  RfEmployee:",
            "    meta: entity",
            "    fields:",
            "      employeeId: string(20) @id",
            "      name: string(50)",
            "",
            "  RfTicket:",
            "    meta: entity",
            "    fields:",
            "      ticketId: long @id @sequence",
            "      title: string(100)",
            "      assignee: RfEmployee @ManyToOne",
            "      owner: RfEmployee! @ManyToOne",
            "      reviewer: RfEmployee @ManyToOne @cascade(ALL)",
            "",
            "dto:",
            "  RfTicketDTO:",
            "    meta: dto @template(RfTicket)",
            "    fields:",
            "      assigneeId: string(20) @ref(assignee.employeeId) @forceupdate",
            "      ownerId: string(20) @ref(owner.employeeId)",
            "      reviewerId: string(20) @ref(reviewer.employeeId) @forceupdate",
            "");

    private static final String CLEAR_JAVA =
            "        else if(!isSkipNull){\n            to.setAssignee(null);\n        }\n";

    private final List<File> projects = new ArrayList<File>();

    @Test
    public void aFullUpdateWithoutTheIdClearsTheRelationInBothOverloads() throws Exception {
        for (String output : new String[] {"elasticore.rules.v2", "elasticore.rules.v3"}) {
            File project = project(DESK, "jpa");
            String log = run(project, "layered", "java", output, 0);
            String mapper = found(project, "RfdeskMapper.java");
            assertEquals(2, count(mapper, CLEAR_JAVA), output + ":\n" + mapper);
            // Only the field that asks for it: ownerId has no @forceupdate.
            assertFalse(mapper.contains("to.setOwner(null)"), mapper);
            assertTrue(
                    log.contains("RfTicketDTO.reviewerId @ref(reviewer.employeeId) @forceupdate does nothing:"
                            + " reviewer cascades, so the mapper writes it whole"),
                    log);
        }
    }

    @Test
    public void kotlinClearsItToo() throws Exception {
        File project = project(DESK, "jpa");
        run(project, "layered", "kotlin", "elasticore.rules.v3", 0);
        String mapper = found(project, "RfdeskMapper.kt");
        assertEquals(
                2, count(mapper, "        else if(!isSkipNull){\n            to.assignee = null\n        }\n"), mapper);
    }

    /** mode: mybatis has no EntityManager, so its one overload clears it. */
    @Test
    public void myBatisClearsItToo() throws Exception {
        File project = project(DESK.replace("    meta: entity\n", "    meta: entity @expose(20)\n"), "mybatis");
        run(project, "layered", "java", "elasticore.rules.v3", 0);
        String mapper = found(project, "RfdeskMapper.java");
        assertEquals(1, count(mapper, CLEAR_JAVA), mapper);
        assertFalse(mapper.contains("EntityManager"), mapper);
    }

    @Test
    public void whatCannotBeClearedIsRefusedAndStopsTheRun() throws Exception {
        File project = project(
                DESK.replace("@ref(owner.employeeId)", "@ref(owner.employeeId) @forceupdate")
                        + String.join(
                                "\n",
                                "  RfNoteDTO:",
                                "    meta: dto @template(RfTicket)",
                                "    fields:",
                                "      assigneeName: string(50) @ref(assignee.name) @forceupdate",
                                "      deepId: string(20) @ref(assignee.manager.employeeId) @forceupdate",
                                ""),
                "jpa");
        String log = run(project, "layered", "java", "elasticore.rules.v3", 1);
        assertTrue(
                log.contains("RfTicketDTO.ownerId @ref(owner.employeeId) @forceupdate: owner is required,"
                        + " so it cannot be cleared"),
                log);
        assertTrue(
                log.contains("RfNoteDTO.assigneeName @ref(assignee.name) @forceupdate: only a relation's own key"
                        + " is cleared, and assignee.name is not assignee.employeeId"),
                log);
        assertTrue(
                log.contains("RfNoteDTO.deepId @ref(assignee.manager.employeeId) @forceupdate: only a relation's"
                        + " own key is cleared, and assignee.manager.employeeId goes further"),
                log);
        assertFalse(log.contains("RfTicketDTO.assigneeId @ref(assignee.employeeId) @forceupdate:"), log);
    }

    /** A DDD reference to another aggregate is held by its id field: the relation is not written. */
    @Test
    public void aReferenceHeldByItsIdIsSaidToDoNothing() throws Exception {
        File project = Files.createTempDirectory("elcore-forceref-ddd").toFile();
        projects.add(project);
        domain(
                project,
                "rfshop",
                null,
                String.join(
                        "\n",
                        "entity:",
                        "  RfCustomer:",
                        "    meta: entity @root @expose(20)",
                        "    fields:",
                        "      customerId: long @id @sequence",
                        "  RfOrder:",
                        "    meta: entity @root @expose(20)",
                        "    fields:",
                        "      orderId: long @id @sequence",
                        "      customer: RfCustomer @ManyToOne",
                        "",
                        "dto:",
                        "  RfOrderEditDTO:",
                        "    meta: dto @template(RfOrder)",
                        "    fields:",
                        "      customerKey: long @ref(customer.customerId) @forceupdate",
                        ""));
        String log = run(project, "hexagonal", "java", null, 0);
        assertTrue(
                log.contains("@ref(customer.customerId) @forceupdate does nothing: the relation is another"
                        + " aggregate's, held by its id field - send that id instead"),
                log);
    }

    /** A relation joined read-only on its id field's column: the clean output writes that field instead. */
    @Test
    public void aReadOnlyJoinIsSaidToDoNothingWhereItIsReadOnly() throws Exception {
        String joined = DESK.replace(
                "      assignee: RfEmployee @ManyToOne",
                "      assigneeKey: string(20)\n      assignee: RfEmployee @ManyToOne @joinBy(assigneeKey)");
        File clean = project(joined, "jpa");
        String log = run(clean, "layered", "java", "elasticore.rules.v3", 0);
        assertTrue(log.contains("assignee is joined read-only on assigneeKey, which is written instead"), log);

        // The compatible output writes the relation, as 2.x does, so it clears it.
        File compat = project(joined, "jpa");
        log = run(compat, "layered", "java", "elasticore.rules.v2", 0);
        assertFalse(log.contains("joined read-only"), log);
        assertEquals(2, count(found(compat, "RfdeskMapper.java"), CLEAR_JAVA));
    }

    /** The 2.x generator, as ./gradlew elcore runs it: the same else, and the same refusal. */
    @Test
    public void the2xGeneratorClearsTheSameAndRefusesTheSame() throws Exception {
        File project = project(DESK, "jpa");
        String[] run = extract(project);
        assertEquals("0", run[0], run[1]);
        String mapper = found(project, "RfdeskMapper.java");
        assertEquals(2, count(mapper, CLEAR_JAVA), mapper);
        assertFalse(mapper.contains("to.setOwner(null)"), mapper);

        File refused = project(DESK.replace("@ref(owner.employeeId)", "@ref(owner.employeeId) @forceupdate"), "jpa");
        run = extract(refused);
        assertFalse("0".equals(run[0]), run[1]);
        assertTrue(
                run[1].contains("RfTicketDTO.ownerId @ref(owner.employeeId) @forceupdate: owner is required"), run[1]);
    }

    private File project(String model, String mode) throws IOException {
        File project = Files.createTempDirectory("elcore-forceref").toFile();
        projects.add(project);
        domain(project, "rfdesk", "io.acme.rfdesk", model, mode);
        return project;
    }

    private static void domain(File project, String name, String ns, String model) throws IOException {
        domain(project, name, ns, model, "jpa");
    }

    private static void domain(File project, String name, String ns, String model, String mode) throws IOException {
        File dir = new File(project, "src/main/resources/blueprint/" + name);
        assertTrue(new File(dir, "model").mkdirs());
        String namespace = ns == null
                ? "    base: io.acme." + name
                : String.join(
                        "\n",
                        "    entity: " + ns + ".entity",
                        "    dto: " + ns + ".dto",
                        "    repository: " + ns + ".repository",
                        "    service: " + ns + ".service",
                        "    control: " + ns + ".controller");
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: " + name,
                                "    framework: springboot",
                                "    mode: " + mode,
                                "    j2ee: jakarta",
                                "  namespace:",
                                namespace,
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
    }

    /** One rule-engine run, its exit code checked; what it said, echoed. */
    private static String run(File project, String architecture, String language, String output, int expected)
            throws Exception {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v3",
            "elasticore.rules.v2"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        PrintStream out = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int status;
        try {
            for (String k : keys) System.clearProperty(k);
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            if (output != null) System.setProperty(output, "true");
            status = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        assertEquals(expected, status, log);
        io.elasticore.base.model.pub.rules.ModelCheck.reset();
        return log;
    }

    /**
     * The 2.x generator in a JVM of its own, as ./gradlew elcore runs it: the
     * extractor keeps the loaded model in static state. Its exit code and log.
     */
    private static String[] extract(File project) throws Exception {
        File log = new File(project, "gen.log");
        Process p = new ProcessBuilder(
                        Paths.get(System.getProperty("java.home"), "bin", "java")
                                .toString(),
                        "-Xmx256m",
                        "-Dfile.encoding=UTF-8",
                        "-Delasticore.base.path=" + project.getAbsolutePath(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        ModelExtractor.class.getName())
                .directory(project)
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start();
        try {
            assertTrue(p.waitFor(120, TimeUnit.SECONDS), "generation did not finish");
        } finally {
            p.destroyForcibly();
        }
        return new String[] {
            String.valueOf(p.exitValue()), new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8)
        };
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) n++;
        return n;
    }

    private static String found(File project, String fileName) throws IOException {
        List<File> hits = new ArrayList<File>();
        collect(project, fileName, hits);
        assertEquals(1, hits.size(), fileName + " in " + project + ": " + hits);
        return new String(Files.readAllBytes(hits.get(0).toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static void collect(File dir, String fileName, List<File> hits) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, fileName, hits);
            else if (f.getName().equals(fileName)) hits.add(f);
        }
    }

    @AfterEach
    void clean() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        io.elasticore.base.model.pub.rules.ModelCheck.reset();
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
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
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
