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
 * @forceupdate on an entity's relation and list fields: the 2.x generator's
 * entity-to-DTO line opened the mapper call and never closed it, so the
 * mapper did not compile, and the rule engine's 2.x-compatible code wrote a
 * setVal line instead (issue 166). Both now write the same, and it compiles.
 */
public class ForceUpdateRelationTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Customer:",
            "    meta: entity",
            "    fields:",
            "      customerId: long @id @sequence",
            "      name: string(50)",
            "  PurchaseOrder:",
            "    meta: entity",
            "    fields:",
            "      orderId: long @id @sequence",
            "      note: string(100) @forceupdate",
            "      customer: Customer @ManyToOne @forceupdate",
            "      lines: List<OrderLine> @OneToMany(mappedBy=\"order\") @forceupdate",
            "  OrderLine:",
            "    meta: entity",
            "    fields:",
            "      lineId: long @id @sequence",
            "      order: PurchaseOrder @ManyToOne",
            "      qty: int",
            "dto:",
            "  CustomerDTO:",
            "    meta: dto @template(Customer)",
            "  OrderLineDTO:",
            "    meta: dto @template(OrderLine)",
            "  PurchaseOrderDTO:",
            "    meta: dto @template(PurchaseOrder)",
            "    fields:",
            "      customer: CustomerDTO",
            "      lines: List<OrderLineDTO>",
            "");

    private final List<File> projects = new ArrayList<File>();

    @Test
    public void bothGeneratorsWriteTheSameLinesAndTheyCompile() throws Exception {
        File twoX = project(MODEL);
        String[] result = extract(twoX);
        assertEquals("0", result[0], result[1]);
        List<String> generated = setterLines(found(twoX, "SrdeskMapper.java"));

        File rules = project(MODEL);
        assertEquals(0, run(rules, "layered", "java", "elasticore.rules.v2", null));
        List<String> compatible = setterLines(found(rules, "SrdeskMapper.java"));

        for (String line : generated) {
            assertEquals(count(line, "("), count(line, ")"), "unbalanced: " + line);
        }
        assertTrue(
                generated.contains("setVal(from.getCustomer(), to::setCustomer, isSkipNull, e->toDTO(e,c));"),
                generated.toString());
        assertTrue(
                generated.contains("setVal(from.getLines(), to::setLines, isSkipNull, e->toOrderLineDTOList(e,c));"),
                generated.toString());
        assertTrue(generated.contains("to.setNote(from.getNote());"), generated.toString());
        for (String line : new String[] {
            "setVal(from.getCustomer(), to::setCustomer, isSkipNull, e->toDTO(e,c));",
            "setVal(from.getLines(), to::setLines, isSkipNull, e->toOrderLineDTOList(e,c));",
            "to.setNote(from.getNote());"
        }) {
            assertTrue(compatible.contains(line), "2.x-compatible: " + line + "\n" + compatible);
        }
    }

    /**
     * The 2.x generator in this JVM, with @forceupdate on the DTO's relation and
     * list fields as well - the DTO-to-entity lines, with and without the
     * EntityManager, read through a mapper method too.
     */
    @Test
    public void everyForcedLineOfTheTwoXGeneratorIsClosed() throws Exception {
        String model = MODEL.replace("      customer: CustomerDTO\n", "      customer: CustomerDTO @forceupdate\n")
                .replace("      lines: List<OrderLineDTO>\n", "      lines: List<OrderLineDTO> @forceupdate\n");
        File project = project(model);
        String basePath = System.getProperty("elasticore.base.path");
        System.setProperty("elasticore.base.path", project.getAbsolutePath());
        Object[] context = saveContext();
        try {
            new io.elasticore.base.extract.ModelExtractor(
                            new File(project, "src/main/resources/blueprint").getAbsolutePath())
                    .extract(new io.elasticore.base.extract.FileBasedSourceFileAccessFactory(
                            new File(project, "src/main/java").getAbsolutePath()));
        } finally {
            restoreContext(context);
            if (basePath == null) System.clearProperty("elasticore.base.path");
            else System.setProperty("elasticore.base.path", basePath);
        }
        String mapperText = found(project, "SrdeskMapper.java");
        List<String> lines = setterLines(mapperText);
        assertTrue(lines.size() >= 4, lines.toString());
        for (String line : lines) {
            assertEquals(count(line, "("), count(line, ")"), "unbalanced: " + line);
            if (line.contains("getCustomer()") || line.contains("getLines()")) {
                assertTrue(line.startsWith("setVal("), "a relation goes through setVal: " + line);
            }
        }
        assertTrue(lines.contains("to.setNote(from.getNote());"), lines.toString());
        // Each direction, the one with the EntityManager too: a forced value straight
        // through, every other value through setVal - in all three, and nowhere else.
        assertEquals(3, count(mapperText, "to.setNote(from.getNote());"), mapperText);
        assertEquals(0, count(mapperText, "setVal(from.getNote()"), mapperText);
        assertEquals(3, count(mapperText, "setVal(from.getOrderId(), to::setOrderId, isSkipNull);"), mapperText);
        assertEquals(0, count(mapperText, "to.setOrderId("), mapperText);
        assertEquals(3, count(mapperText, "setVal(from.getQty(), to::setQty, isSkipNull);"), mapperText);
    }

    private static final String[] CONTEXT = {"STATIC_DOMAIN_MAP", "context", "tmpCtx"};

    private static Object[] saveContext() throws Exception {
        Object[] saved = new Object[CONTEXT.length];
        for (int i = 0; i < CONTEXT.length; i++) {
            java.lang.reflect.Field f =
                    io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField(CONTEXT[i]);
            f.setAccessible(true);
            Object value = f.get(null);
            saved[i] = value instanceof java.util.Map
                    ? new java.util.HashMap<Object, Object>((java.util.Map<?, ?>) value)
                    : value;
        }
        return saved;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void restoreContext(Object[] saved) throws Exception {
        for (int i = 0; i < CONTEXT.length; i++) {
            java.lang.reflect.Field f =
                    io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField(CONTEXT[i]);
            f.setAccessible(true);
            if (saved[i] instanceof java.util.Map) {
                java.util.Map map = (java.util.Map) f.get(null);
                map.clear();
                map.putAll((java.util.Map) saved[i]);
            } else {
                f.set(null, saved[i]);
            }
        }
    }

    /** The mapper's statement lines that set a customer, lines or note, trimmed. */
    private static List<String> setterLines(String mapper) {
        List<String> out = new ArrayList<String>();
        for (String line : mapper.split("\n")) {
            String t = line.trim();
            if (t.contains("Customer(") || t.contains("Lines(") || t.contains("Note(")) out.add(t);
        }
        return out;
    }
    // ------------------------------------------------------------------

    private File project(String model) throws IOException {
        File project = Files.createTempDirectory("elcore-force").toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/srdesk");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: srdesk",
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    entity: io.acme.srdesk.entity",
                                "    dto: io.acme.srdesk.dto",
                                "    repository: io.acme.srdesk.repository",
                                "    service: io.acme.srdesk.service",
                                "    control: io.acme.srdesk.controller",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    /** From the line that starts with the head to the closing brace of that method. */
    private static String section(String source, String head) {
        int from = source.indexOf(head);
        assertTrue(from >= 0, head + "\n" + source);
        int to = source.indexOf("\n    }\n", from);
        return source.substring(from, to < 0 ? source.length() : to);
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) n++;
        return n;
    }

    private static int run(
            File project, String architecture, String language, String output, ByteArrayOutputStream captured) {
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
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
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

    /** The 2.x generator in a JVM of its own, as ./gradlew elcore runs it. Its exit code and log. */
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
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
