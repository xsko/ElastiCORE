package io.elasticore.base.model.pub.rules.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.elasticore.base.model.pub.rules.schema.DdlConfig;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/** A condition of @invariant and @specification compares with ==, >= and <= too. */
public class ConditionComparisonTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  CcOrder:",
            "    meta: entity @root @expose(20) @invariant(total >= 0, 'never below zero')"
                    + " @invariant(quantity <= 10, 'ten at most') @invariant(rate == 1, 'one = one')"
                    + " @invariant(lines.size >= 1, 'a line') @specification(exactlyThree, quantity == 3)"
                    + " @invariant(total != 7, 'not seven') @specification(oneLine, lines.size == 1)",
            "    fields:",
            "      orderId: long @id @sequence",
            "      total: Long",
            "      quantity: Integer",
            "      rate: Double",
            "      lines: List<CcLine> @OneToMany @cascade(ALL)",
            "  CcLine:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      lineId: long @id @sequence",
            "      label: string(20)",
            "");

    private static final List<File> projects = new ArrayList<File>();

    @AfterAll
    static void cleanUp() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        for (File project : projects) delete(project);
    }

    @Test
    public void javaComparesWithEachOperator() throws Exception {
        File project = run("elcore-cond-java", "java");
        String invariants = found(project, "CcOrderInvariants.java");
        // The violation is the comparison turned around.
        assertTrue(invariants.contains("ccOrder.getTotal() == null || ccOrder.getTotal() < 0"), invariants);
        assertTrue(invariants.contains("ccOrder.getQuantity() == null || ccOrder.getQuantity() > 10"), invariants);
        assertTrue(invariants.contains("ccOrder.getRate() == null || ccOrder.getRate() != 1"), invariants);
        assertTrue(invariants.contains("ccOrder.getLines().size() < 1"), invariants);
        assertTrue(invariants.contains("one = one"), invariants);
        assertTrue(invariants.contains("invariant.CcOrder.total-ge-0"), invariants);
        assertTrue(invariants.contains("ccOrder.getTotal() == null || ccOrder.getTotal() == 7"), invariants);
        String specs = found(project, "CcOrderSpecifications.java");
        assertTrue(specs.contains("ccOrder.getQuantity() != null && ccOrder.getQuantity() == 3"), specs);
    }

    @Test
    public void typeScriptComparesStrictly() throws Exception {
        File project = run("elcore-cond-ts", "ts");
        StringBuilder ts = new StringBuilder();
        List<File> all = new ArrayList<File>();
        collectTs(project, all);
        for (File f : all) ts.append(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        assertTrue(ts.indexOf(".quantity === 3") > 0, ts.toString());
        assertTrue(ts.indexOf(".quantity == 3") < 0, ts.toString());
    }

    private static void collectTs(File dir, List<File> hits) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collectTs(f, hits);
            else if (f.getName().endsWith(".ts")) hits.add(f);
        }
    }

    @Test
    public void kotlinComparesADoubleWithCompareTo() throws Exception {
        File project = run("elcore-cond-kt", "kotlin");
        String invariants = found(project, "CcOrderInvariants.kt");
        assertTrue(invariants.contains("ccOrder.rate == null || ccOrder.rate!!.compareTo(1) != 0"), invariants);
        assertTrue(invariants.contains("ccOrder.quantity == null || ccOrder.quantity!! > 10"), invariants);
        // A Long against the Int literal: compareTo, as == would not compile; != turned around is ==.
        assertTrue(invariants.contains("ccOrder.total == null || ccOrder.total!!.compareTo(7) == 0"), invariants);
        // An Int against a whole number, and a count, as written.
        String specs = found(project, "CcOrderSpecifications.kt");
        assertTrue(specs.contains("ccOrder.quantity!! == 3"), specs);
        assertTrue(specs.contains("ccOrder.lines!!.size == 1"), specs);
    }

    private static File run(String name, String language) throws Exception {
        File project = Files.createTempDirectory(name).toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/sales");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: sales",
                                "    framework: springboot",
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    base: io.acme.sales",
                                "    entity: io.acme.sales.entity",
                                "    dto: io.acme.sales.dto",
                                "    repository: io.acme.sales.repository",
                                "    service: io.acme.sales.service",
                                "    control: io.acme.sales.controller",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));

        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v3",
            "elasticore.rules.v2",
            "elasticore.rules.ts",
            DdlConfig.DATABASES
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        PrintStream out = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "ddd");
            System.setProperty("elasticore.rules.force", "true");
            if ("ts".equals(language)) {
                System.setProperty("elasticore.rules.ts", "true");
            } else {
                System.setProperty("elasticore.language", language);
                System.setProperty("elasticore.rules.v3", "true");
            }
            System.setProperty(DdlConfig.DATABASES, "postgresql");
            assertEquals(0, RulesGenRunner.run(new String[0]), "the run's exit: " + project);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
            io.elasticore.base.model.pub.rules.ModelCheck.reset();
        }
        out.print(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        return project;
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
