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
 * A DTO names the fields it takes of its template, @only(note, deliveryDate),
 * instead of removing the others one by one with "--". A field added to the
 * entity later is then not in the DTO, nor in a request that binds it, until
 * someone lists it. Only what is copied is filtered: a field the DTO declares
 * is always kept, and may override a listed one. Read in the model, so the 2.x
 * generator, its compatible output and the 3.0 code all take the same fields;
 * the mapper maps the DTO's fields and needs nothing of its own.
 */
public class DtoOnlyTest {

    private static final String CRM = String.join(
            "\n",
            "entity:",
            "  OyCustomer:",
            "    meta: entity",
            "    fields:",
            "      customerId: long @id @sequence",
            "      name: string(50)",
            "      email: string(100)",
            "");

    private static final String DESK = String.join(
            "\n",
            "entity:",
            "  OyOrder:",
            "    meta: entity",
            "    fields:",
            "      orderId: long @id @sequence",
            "      note: string(200) @search(like)",
            "      deliveryDate: date",
            "      status: string(10) @search(eq)",
            "      total: bigdecimal(12.2)",
            "      addedLater: string(20) -- a field the entity got after the DTO was written",
            "",
            "dto:",
            "  OyOrderNoteDTO:",
            "    meta: dto @template(OyOrder) @only(note, deliveryDate)",
            "    fields:",
            "      deliveryDate: datetime -- declared here, so the DTO's own wins",
            "      reason: string(100)",
            "",
            "  OyOrderSrchDTO:",
            "    meta: dto @searchable(entity=OyOrder) @only(status)",
            "",
            "  OyCustomerCardDTO:",
            "    meta: dto @template(OyCustomer) @only(name)",
            "");

    private final List<File> projects = new ArrayList<File>();

    @Test
    public void aDtoTakesTheListedFieldsOfItsTemplateAndItsOwn() throws Exception {
        for (boolean v3 : new boolean[] {false, true}) {
            File project = project(DESK);
            assertEquals(0, run(project, "layered", v3 ? "elasticore.rules.v3" : "elasticore.rules.v2", null));
            String dto = found(project, "OyOrderNoteDTO.java");
            assertTrue(dto.contains(" note;"), dto);
            assertTrue(dto.contains("LocalDateTime deliveryDate"), "the DTO's own type wins:\n" + dto);
            assertTrue(dto.contains(" reason;"), dto);
            for (String left : new String[] {" orderId", " status", " total", " addedLater"}) {
                assertFalse(dto.contains(left + ";") || dto.contains(left + " ="), left + " was copied:\n" + dto);
            }
        }
    }

    @Test
    public void aSearchDtoTakesTheListedFieldsOnly() throws Exception {
        File project = project(DESK);
        assertEquals(0, run(project, "layered", "elasticore.rules.v3", null));
        String dto = found(project, "OyOrderSrchDTO.java");
        assertTrue(dto.contains(" status;"), dto);
        assertFalse(dto.contains(" note;"), "note searches by like, and is not listed:\n" + dto);
    }

    @Test
    public void aTemplateOfAnotherDomainIsReadOnceEveryDomainIsLoaded() throws Exception {
        File project = project(DESK);
        String dto = generated(project, "OyCustomerCardDTO.java");
        assertTrue(dto.contains(" name;"), dto);
        assertFalse(dto.contains(" email;") || dto.contains(" customerId;"), dto);
    }

    /** A DDD reference is held by its id, customerId @idof(customer): naming the relation keeps the id. */
    @Test
    public void aRelationNameKeepsTheFieldThatHoldsItsId() throws Exception {
        File project = Files.createTempDirectory("elcore-only-ddd").toFile();
        projects.add(project);
        domain(
                project,
                "shop",
                null,
                String.join(
                        "\n",
                        "entity:",
                        "  ShCustomer:",
                        "    meta: entity @root @expose(20)",
                        "    fields:",
                        "      customerId: long @id @sequence",
                        "      name: string(50)",
                        "  ShOrder:",
                        "    meta: entity @root @expose(20)",
                        "    fields:",
                        "      orderId: long @id @sequence",
                        "      note: string(200)",
                        "      status: string(10)",
                        "      customer: ShCustomer @ManyToOne",
                        "",
                        "dto:",
                        "  ShOrderNoteDTO:",
                        "    meta: dto @template(ShOrder) @only(note, customer)",
                        ""));
        assertEquals(0, run(project, "hexagonal", null, null));
        String dto = found(project, "ShOrderNoteDTO.java");
        assertTrue(dto.contains(" note;") && dto.contains(" customerId;"), dto);
        assertFalse(dto.contains(" status;"), dto);
    }

    @Test
    public void whatCannotBeKeptIsSaidAndStopsTheRun() throws Exception {
        File project = project(DESK
                + String.join(
                        "\n",
                        "  OyNoSuchFieldDTO:",
                        "    meta: dto @template(OyOrder) @only(note, nothing)",
                        "",
                        "  OyNoTemplateDTO:",
                        "    meta: dto @only(note)",
                        "    fields:",
                        "      note: string(200)",
                        "",
                        "  OyWithExtendDTO:",
                        "    meta: dto @template(OyOrder) @extend(OyOrderNoteDTO) @only(note)",
                        "",
                        "  OyRemovedDTO:",
                        "    meta: dto @template(OyOrder) @only(note, status)",
                        "    fields:",
                        "      status: --",
                        "",
                        "  OyEmptyDTO:",
                        "    meta: dto @template(OyOrder) @only",
                        "",
                        "  OyNoModelDTO:",
                        "    meta: dto @template(OyMissing) @only(note)",
                        ""));
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        assertEquals(1, run(project, "layered", "elasticore.rules.v3", captured));
        String log = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(log.contains("OyNoSuchFieldDTO: @only(nothing) - OyOrder has no field nothing"), log);
        assertTrue(
                log.contains("OyNoTemplateDTO: @only keeps fields of a @template, @searchable or @searchResult"), log);
        assertTrue(log.contains("OyWithExtendDTO: @only cannot go with @extend"), log);
        assertTrue(log.contains("OyRemovedDTO.status is removed with -- and kept by @only"), log);
        assertTrue(log.contains("OyEmptyDTO: @only names no field"), log);
        assertTrue(log.contains("OyNoModelDTO: @only(note) - OyMissing has no field note"), log);
        assertFalse(log.contains("OyOrderNoteDTO: @only") || log.contains("OyCustomerCardDTO: @only"), log);
    }

    /** The 2.x generator, as ./gradlew elcore runs it: the same fields, and the same refusal. */
    @Test
    public void the2xGeneratorTakesTheSameFieldsAndRefusesTheSame() throws Exception {
        File project = project(DESK);
        String[] run = extract(project);
        assertEquals("0", run[0], run[1]);
        String dto = found(project, "OyOrderNoteDTO.java");
        assertTrue(
                dto.contains(" note;") && dto.contains("LocalDateTime deliveryDate") && dto.contains(" reason;"), dto);
        assertFalse(dto.contains(" status;") || dto.contains(" addedLater;") || dto.contains(" total;"), dto);

        File refused = project(DESK
                + String.join(
                        "\n", "  OyNoSuchFieldDTO:", "    meta: dto @template(OyOrder) @only(note, nothing)", ""));
        run = extract(refused);
        assertFalse("0".equals(run[0]), run[1]);
        assertTrue(run[1].contains("OyNoSuchFieldDTO: @only(nothing) - OyOrder has no field nothing"), run[1]);
    }

    /** Two domains: oycrm holds OyCustomer, oydesk the model given. */
    private File project(String desk) throws Exception {
        File project = Files.createTempDirectory("elcore-only").toFile();
        projects.add(project);
        domain(project, "oycrm", "io.acme.oycrm", CRM);
        domain(project, "oydesk", "io.acme.oydesk", desk);
        return project;
    }

    private static void domain(File project, String name, String ns, String model) throws IOException {
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
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "  namespace:",
                                namespace,
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
    }

    /** One rule-engine run; its output captured when asked for, and echoed. */
    private static int run(File project, String architecture, String output, ByteArrayOutputStream captured) {
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
        ByteArrayOutputStream bytes = captured != null ? captured : new ByteArrayOutputStream();
        try {
            for (String k : keys) System.clearProperty(k);
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.force", "true");
            if (output != null) System.setProperty(output, "true");
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

    /** The file the 3.0 code writes for it. */
    private static String generated(File project, String fileName) throws IOException {
        assertEquals(0, run(project, "layered", "elasticore.rules.v3", null));
        return found(project, fileName);
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
