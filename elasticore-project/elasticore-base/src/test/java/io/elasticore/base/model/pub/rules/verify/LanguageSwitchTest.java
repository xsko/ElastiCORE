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
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A project that changes its language: the generated files of the old one
 * go, since the new language declares the same classes. They used to stay
 * on every run - the check for a kept source still using a dropped class
 * counted the new twin, which names the class it declares - and the build
 * then had each class twice (Redeclaration). An edited one stays, as any
 * edited file the DSL no longer produces does.
 */
public class LanguageSwitchTest {

    private static final String[] KEYS = {
        "elasticore.base.path",
        "elasticore.architecture",
        "elasticore.language",
        "elasticore.rules.force",
        "elasticore.rules.v2",
        "elasticore.rules.v3",
        "elasticore.rules.ts",
        "elasticore.rules.kt",
        "elasticore.publisher.rules.add",
        "elasticore.rules.list",
        "elasticore.merge"
    };

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Customer:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      customerId: long @id @sequence",
            "      name: string(50)! @search(like)",
            "",
            "  PurchaseOrder:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      orderId: long @id @sequence",
            "      orderNo: string(20)! @search(eq)",
            "      customer: Customer! @ManyToOne @search(eq)",
            "");

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
    public void javaToKotlinRemovesTheJavaFilesTheKotlinOnesReplace() throws Exception {
        File project = project();
        run(project, "java");
        Path dto = find(project, "src/main/java", "CustomerDTO.java");
        assertNotNull(dto);
        // The developer's own Java class, which names a generated one: it is
        // served by the Kotlin class of the same name afterwards.
        Path report = new File(project, "src/main/java/io/acme/dgshop/report/CustomerReport.java").toPath();
        Files.createDirectories(report.getParent());
        Files.write(
                report,
                ("package io.acme.dgshop.report;\n\nimport " + packageOf(dto) + ".CustomerDTO;\n\n"
                                + "public class CustomerReport {\n    CustomerDTO last;\n}\n")
                        .getBytes(StandardCharsets.UTF_8));

        String log = run(project, "kotlin");
        assertFalse(log.contains("still uses it"), log);
        assertTrue(log.contains("CustomerDTO.java  REMOVED"), log);
        assertEquals(new TreeSet<String>(), twins(project), log);
        assertTrue(Files.exists(report), "the developer's class stays");
        Map<String, String> cache =
                GeneratedCache.read(Files.readAllBytes(new File(project, ".elasticore/cache").toPath()));
        for (String key : cache.keySet()) assertFalse(key.endsWith(".java"), key + " left in the cache");

        // And the next run has nothing more to say about them.
        log = run(project, "kotlin");
        assertFalse(log.contains("no longer generated"), log);
        assertFalse(log.contains("REMOVED"), log);
    }

    @Test
    public void anEditedJavaFileStaysAndTheOthersGo() throws Exception {
        File project = project();
        run(project, "java");
        Path dto = find(project, "src/main/java", "CustomerDTO.java");
        assertNotNull(dto);
        Files.write(dto, (read(dto) + "// a note of the developer's\n").getBytes(StandardCharsets.UTF_8));

        String log = run(project, "kotlin");
        assertTrue(Files.exists(dto), log);
        assertTrue(read(dto).endsWith("// a note of the developer's\n"));
        assertTrue(
                log.contains(
                        "CustomerDTO.java was generated, the DSL no longer produces it, and it has been" + " edited"),
                log);
        assertTrue(log.contains("CustomerDTO.kt declares the same class now"), log);
        TreeSet<String> left = twins(project);
        assertEquals(1, left.size(), left.toString());
        assertTrue(left.first().endsWith("CustomerDTO.java"), left.toString());
        assertNull(find(project, "src/main/java", "PurchaseOrderDTO.java"), log);
    }

    @Test
    public void kotlinToJavaRemovesTheKotlinFiles() throws Exception {
        File project = project();
        run(project, "kotlin");
        assertNotNull(find(project, "src/main/kotlin", "CustomerDTO.kt"));

        String log = run(project, "java");
        assertFalse(log.contains("still uses it"), log);
        assertTrue(log.contains("CustomerDTO.kt  REMOVED"), log);
        assertEquals(new TreeSet<String>(), twins(project), log);
        assertNotNull(find(project, "src/main/java", "CustomerDTO.java"), log);
    }

    @Test
    public void aDroppedClassAJavaSourceUsesIsStillKept() throws Exception {
        // Not a language switch: the keep stands for a class nothing declares any more.
        File project = project();
        run(project, "kotlin");
        Path dto = find(project, "src/main/kotlin", "PurchaseOrderDTO.kt");
        assertNotNull(dto);
        Path report = new File(project, "src/main/java/io/acme/dgshop/report/OrderReport.java").toPath();
        Files.createDirectories(report.getParent());
        Files.write(
                report,
                ("package io.acme.dgshop.report;\n\nimport " + packageOfKotlin(dto) + ".PurchaseOrderDTO;\n\n"
                                + "public class OrderReport {\n    PurchaseOrderDTO last;\n}\n")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(
                new File(project, "src/main/resources/blueprint/dgshop/model/model.yml").toPath(),
                MODEL.substring(0, MODEL.indexOf("  PurchaseOrder:")).getBytes(StandardCharsets.UTF_8));

        String log = run(project, "kotlin");
        assertTrue(Files.exists(dto), log);
        assertTrue(
                log.contains("PurchaseOrderDTO.kt is no longer generated, but src/main/java/io/acme/dgshop/report/"
                        + "OrderReport.java still uses it - kept"),
                log);
    }

    // ---- helpers ----------------------------------------------------------------------

    /** The classes the project has in both languages, by the path of the Java or Kotlin one. */
    private static TreeSet<String> twins(File project) throws IOException {
        List<Path> java = all(new File(project, "src/main/java"), ".java");
        List<Path> kotlin = all(new File(project, "src/main/kotlin"), ".kt");
        TreeSet<String> kt = new TreeSet<String>();
        for (Path p : kotlin) kt.add(base(p));
        TreeSet<String> out = new TreeSet<String>();
        for (Path p : java) if (kt.contains(base(p))) out.add(p.toString().replace('\\', '/'));
        return out;
    }

    private static String base(Path p) {
        String n = p.getFileName().toString();
        return n.substring(0, n.lastIndexOf('.'));
    }

    private String run(File project, String language) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        int code;
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.language", language);
            System.clearProperty("elasticore.rules.force");
            System.clearProperty("elasticore.merge");
            code = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        assertEquals(0, code, log);
        return log;
    }

    private File project() throws IOException {
        File dir = Files.createTempDirectory("elcore-language").toFile();
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
                "    mode: jpa",
                "    j2ee: jakarta",
                "  namespace:",
                "    base: io.acme.dgshop",
                "");
        Files.write(new File(blueprint, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private static String packageOf(Path source) throws IOException {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("(?m)^package ([\\w.]+);").matcher(read(source));
        assertTrue(m.find(), source.toString());
        return m.group(1);
    }

    private static String packageOfKotlin(Path source) throws IOException {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("(?m)^package ([\\w.`]+)").matcher(read(source));
        assertTrue(m.find(), source.toString());
        return m.group(1).replace("`", "");
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    private static Path find(File project, String root, String name) throws IOException {
        File dir = new File(project, root);
        if (!dir.isDirectory()) return null;
        try (Stream<Path> all = Files.walk(dir.toPath())) {
            return all.filter(p -> p.getFileName().toString().equals(name))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static List<Path> all(File root, String extension) throws IOException {
        if (!root.isDirectory()) return new ArrayList<Path>();
        try (Stream<Path> all = Files.walk(root.toPath())) {
            return all.filter(p -> p.toString().endsWith(extension)).collect(Collectors.toList());
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
