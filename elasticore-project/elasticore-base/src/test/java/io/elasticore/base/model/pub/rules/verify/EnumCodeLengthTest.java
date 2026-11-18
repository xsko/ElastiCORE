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
 * An enum value longer than its field stops elcore3, as it stops the 2.x
 * generator: the column is that long, so the row would fail on insert. Only
 * this finding fails the build; other DSL warnings stay warnings.
 */
public class EnumCodeLengthTest {

    private static String model(String codeType) {
        return String.join(
                "\n",
                "enumeration:",
                "  OrderStatus:",
                "    meta: enum @db(code)",
                "    fields:",
                "      code: " + codeType,
                "      label: string(20)",
                "    enum:",
                "      PLACED: P,Placed",
                "      CANCELLED: CXL,Cancelled",
                "",
                "entity:",
                "  PurchaseOrder:",
                "    meta: entity @expose(20)",
                "    fields:",
                "      orderId: long @id @sequence",
                "      status: OrderStatus",
                "");
    }

    private static String MODEL = model("string(2)");

    private final List<File> projects = new ArrayList<File>();

    @Test
    public void aValueLongerThanItsFieldFailsTheBuild() throws Exception {
        for (String[] output : new String[][] {{"layered", "java"}, {"hexagonal", "java"}, {"layered", "kotlin"}}) {
            MODEL = model("string(2)");
            ByteArrayOutputStream log = new ByteArrayOutputStream();
            assertEquals(
                    1,
                    run(
                            project(),
                            output[0],
                            output[1],
                            output[1].equals("java") && output[0].equals("layered") ? "elasticore.rules.v3" : null,
                            log),
                    output[0] + "/" + output[1]);
            String said = new String(log.toByteArray(), StandardCharsets.UTF_8);
            assertTrue(
                    said.contains(
                            "enum OrderStatus.CANCELLED: field 'code' value 'CXL' (length 3) exceeds max length 2"),
                    said);
            assertTrue(said.contains("Widen the field (code: string(3)) or shorten the value."), said);
            assertTrue(said.contains("[elcore3] the model check failed"), said);
        }
    }

    @Test
    public void aFieldLongEnoughBuilds() throws Exception {
        MODEL = model("string(3)");
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        assertEquals(0, run(project(), "layered", "java", "elasticore.rules.v3", log));
        assertFalse(new String(log.toByteArray(), StandardCharsets.UTF_8).contains("exceeds max length"));
    }

    /** Another DSL warning is still a warning: the build goes on. */
    @Test
    public void otherDslWarningsStayWarnings() throws Exception {
        MODEL = model("string(3)")
                .replace(
                        "      status: OrderStatus",
                        "      status: OrderStatus\n      note: string(10) @length(max=20)");
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        assertEquals(0, run(project(), "layered", "java", "elasticore.rules.v3", log));
        String said = new String(log.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(said.contains("the type says length 10 and @length says max=20"), said);
    }

    private File project() throws IOException {
        File project = Files.createTempDirectory("elcore-enumlen").toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/shop");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: shop",
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    entity: io.acme.shop.entity",
                                "    dto: io.acme.shop.dto",
                                "    repository: io.acme.shop.repository",
                                "    service: io.acme.shop.service",
                                "    control: io.acme.shop.controller",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    private static int run(
            File project, String architecture, String language, String output, ByteArrayOutputStream bytes) {
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
