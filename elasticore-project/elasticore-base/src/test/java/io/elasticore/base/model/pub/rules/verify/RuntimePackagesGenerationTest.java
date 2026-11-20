package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * elasticore { runtimePackages } on a real model: the pet store's blueprint,
 * generated as its build generates it (hexagonal Java, Spring Boot 4) with
 * the runtime's three packages mapped. Nothing of the runtime is left in the
 * generated sources, in the merge cache, or anywhere else the run wrote; and
 * the classes the project has to provide are listed. The ddd sample's
 * blueprint (mapping: xml, so orm.xml) and a Kotlin build the same; the code
 * 2.x generates is left as it is.
 */
public class RuntimePackagesGenerationTest {

    private static final String MAPPING = "io.elasticore.springboot3=com.test.springboot,"
            + "io.elasticore.runtime=com.test.runtime,"
            + "io.elasticore.base.util=com.test.util";

    private static File project;
    private static String log = "";

    /** Every file the run wrote, by path - the DSL and the copied guides left out. */
    private static Map<String, String> written;

    @BeforeAll
    static void generate() throws Exception {
        File blueprint = new File(
                repositoryRoot(),
                "elasticore-project/elasticore-sample-petstore/src/main/resources/blueprint/petstore");
        assertTrue(blueprint.isDirectory(), blueprint.getPath());
        project = Files.createTempDirectory("elcore-rtpkg").toFile();
        copyTree(blueprint.toPath(), new File(project, "src/main/resources/blueprint/petstore").toPath());
        log = run(project, MAPPING, null);
        assertTrue(log.startsWith("exit 0\n"), log);
        written = snapshot(project);
    }

    static String run(File dir, String mapping, String v2) {
        return run(dir, mapping, v2, v2 != null ? null : "hexagonal", v2 != null ? null : "java");
    }

    static String run(File dir, String mapping, String v2, String architecture, String language) {
        boolean petstore = new File(dir, "src/main/resources/blueprint/petstore").isDirectory();
        String[][] props = {
            {"elasticore.base.path", dir.getAbsolutePath()},
            {"elasticore.architecture", architecture},
            {"elasticore.language", language},
            // The pet store's build is on Spring Boot 4; the ddd sample's on 3.
            {"elasticore.springBoot", petstore ? "4" : null},
            {"elasticore.rules.v2", v2},
            {RuntimePackages.PROPERTY, mapping},
        };
        String[] saved = new String[props.length];
        for (int i = 0; i < props.length; i++) {
            saved[i] = System.getProperty(props[i][0]);
            if (props[i][1] == null) System.clearProperty(props[i][0]);
            else System.setProperty(props[i][0], props[i][1]);
        }
        PrintStream out = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        int status;
        try {
            System.setOut(new PrintStream(captured, true, "UTF-8"));
            status = RulesGenRunner.run(new String[0]);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        } finally {
            System.setOut(out);
            for (int i = 0; i < props.length; i++) {
                if (saved[i] == null) System.clearProperty(props[i][0]);
                else System.setProperty(props[i][0], saved[i]);
            }
        }
        String text = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        out.print(text);
        return "exit " + status + "\n" + text;
    }

    @AfterAll
    static void clean() throws Exception {
        resetContext();
        delete(project);
    }

    @Test
    public void noRuntimePackageIsLeft() {
        List<String> left = new ArrayList<String>();
        int sources = 0;
        for (Map.Entry<String, String> en : written.entrySet()) {
            // The blueprint's schema.yml describes the model, the runtime's type names
            // included; it is not compiled, and is no generated code.
            if (en.getKey().startsWith("src/main/resources/blueprint/")) continue;
            if (en.getKey().endsWith(".java")) sources++;
            for (String pkg :
                    new String[] {"io.elasticore.springboot3", "io.elasticore.runtime", "io.elasticore.base.util"}) {
                if (en.getValue().contains(pkg)) left.add(en.getKey() + ": " + pkg);
            }
        }
        assertTrue(left.isEmpty(), "still naming the runtime: " + left);
        assertTrue(sources > 50, "too few sources to say anything: " + sources);
        assertFalse(log.contains("runtimePackages: the generated code still names"), log);
    }

    @Test
    public void theMappedNamesAreThere() {
        String errors = file("src/main/java/io/elasticore/sample/petstore/adapter/in/web/PetstoreApiErrors.java");
        assertTrue(errors.contains("com.test.springboot."), errors);
        StringBuilder all = new StringBuilder();
        for (String text : written.values()) all.append(text);
        assertTrue(
                all.indexOf("import com.test.springboot.validation.NotFoundException;") >= 0,
                "no NotFoundException import");
        assertTrue(all.indexOf("import com.test.springboot.domain.Money;") >= 0, "no Money import");
        assertTrue(all.indexOf("com.test.springboot.security.") >= 0, "no security class");
    }

    @Test
    public void theCacheHoldsWhatIsOnDisk() {
        // The merge's base is the rewritten text: the next run sees no change of its own to merge.
        String cache = written.get(GeneratedCache.DIR + "/" + GeneratedCache.NAME);
        assertTrue(cache != null, "no merge cache written: " + written.keySet());
        assertFalse(cache.contains("io.elasticore.springboot3"), "the cache holds the runtime's names");
        assertTrue(cache.contains("com.test.springboot."), "the cache does not hold the mapped names");
    }

    @Test
    public void theClassesToProvideAreListed() {
        String list = written.get(RuntimePackages.REPORT);
        assertTrue(list != null, "no " + RuntimePackages.REPORT + ": " + written.keySet());
        assertTrue(list.contains("com.test.springboot.validation.NotFoundException\n"), list);
        assertTrue(list.contains("com.test.springboot.domain.Money\n"), list);
        for (String line : list.split("\n")) {
            assertTrue(
                    line.startsWith("com.test.springboot.")
                            || line.startsWith("com.test.runtime.")
                            || line.startsWith("com.test.util."),
                    line);
        }
        assertTrue(log.contains("runtimePackages: the generated code uses "), log);
        assertTrue(log.contains("  com.test.springboot.validation.NotFoundException"), log);
        assertTrue(log.contains("[elcore3] runtime   : io.elasticore.base.util -> com.test.util"), log);
    }

    @Test
    public void aSecondRunWritesNothing() throws Exception {
        resetContext();
        String again = run(project, MAPPING, null);
        assertTrue(again.startsWith("exit 0\n") && again.contains("=== elcore3: 0 written / "), again);
        assertEquals(written, snapshot(project), "a second run with the same mapping changed what is on disk");
    }

    @Test
    public void aMistypedMappingStopsTheRun() throws Exception {
        File empty = Files.createTempDirectory("elcore-rtpkg").toFile();
        try {
            resetContext();
            String said = run(empty, "com.acme=com.test", null);
            assertTrue(said.startsWith("exit 1\n"), said);
            assertTrue(said.contains("runtimePackages: the key 'com.acme' is not an ElastiCORE runtime package"), said);
            assertFalse(new File(empty, "src/main/java").exists(), "a refused mapping wrote sources");
        } finally {
            delete(empty);
        }
    }

    /**
     * mapping: xml puts the converters in orm.xml, which is written apart from
     * the sources: rewritten there too, and listed - a converter named only
     * there was missing from the list, and the application failed to start.
     */
    @Test
    public void theOrmXmlIsRewrittenAndItsConvertersListed() throws Exception {
        File blueprint =
                new File(repositoryRoot(), "elasticore-project/elasticore-template-ddd/src/main/resources/blueprint");
        assertTrue(blueprint.isDirectory(), blueprint.getPath());
        File ddd = Files.createTempDirectory("elcore-rtpkg").toFile();
        try {
            copyTree(blueprint.toPath(), new File(ddd, "src/main/resources/blueprint").toPath());
            resetContext();
            String said = run(ddd, MAPPING, null);
            assertTrue(said.startsWith("exit 0\n"), said);
            Map<String, String> files = snapshot(ddd);
            String orm = files.get("src/main/resources/META-INF/orm.xml");
            assertTrue(orm != null, "no orm.xml: " + files.keySet());
            assertTrue(orm.contains("converter=\"com.test.springboot.util.StringArrayConverter\""), orm);
            assertFalse(orm.contains("io.elasticore.springboot3"), orm);
            String list = files.get(RuntimePackages.REPORT);
            assertTrue(list.contains("com.test.springboot.util.StringArrayConverter\n"), list);
            for (Map.Entry<String, String> en : files.entrySet()) {
                if (en.getKey().startsWith("src/main/resources/blueprint/")) continue;
                assertFalse(en.getValue().contains("io.elasticore.springboot3"), en.getKey());
                assertFalse(en.getValue().contains("io.elasticore.runtime"), en.getKey());
                assertFalse(en.getValue().contains("io.elasticore.base.util"), en.getKey());
            }
        } finally {
            resetContext();
            delete(ddd);
        }
    }

    @Test
    public void aKotlinBuildIsRewrittenToo() throws Exception {
        File kt = Files.createTempDirectory("elcore-rtpkg").toFile();
        try {
            writeSmallModel(kt);
            resetContext();
            String said = run(kt, MAPPING, null, "layered", "kotlin");
            assertTrue(said.startsWith("exit 0\n"), said);
            int sources = 0;
            boolean mapped = false;
            for (Map.Entry<String, String> en : snapshot(kt).entrySet()) {
                if (!en.getKey().endsWith(".kt")) continue;
                sources++;
                assertFalse(en.getValue().contains("io.elasticore.springboot3"), en.getKey());
                assertFalse(en.getValue().contains("io.elasticore.runtime"), en.getKey());
                assertFalse(en.getValue().contains("io.elasticore.base.util"), en.getKey());
                mapped |= en.getValue().contains("import com.test.springboot.");
            }
            assertTrue(sources > 3, "no Kotlin written: " + sources);
            assertTrue(mapped, "no Kotlin source imports the mapped runtime");
            assertTrue(said.contains("runtimePackages: the generated code uses "), said);
        } finally {
            resetContext();
            delete(kt);
        }
    }

    private static void writeSmallModel(File dir) throws IOException {
        File domain = new File(dir, "src/main/resources/blueprint/shop");
        assertTrue(new File(domain, "model").mkdirs());
        Files.write(
                new File(domain, "env.yml").toPath(),
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
                                "    base: com.acme.shop",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(
                new File(domain, "model/model.yml").toPath(),
                String.join(
                                "\n",
                                "entity:",
                                "  Item:",
                                "    meta: entity @expose(20)",
                                "    fields:",
                                "      itemId: long @id @sequence",
                                "      name:   string(30)!",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void theCodeTwoXGeneratesKeepsItsPackages() throws Exception {
        File twoX = Files.createTempDirectory("elcore-rtpkg").toFile();
        try {
            writeSmallModel(twoX);
            resetContext();
            String said = run(twoX, MAPPING, "true");
            assertTrue(said.startsWith("exit 0\n"), said);
            assertTrue(said.contains("[elcore3] runtimePackages is ignored"), said);
            StringBuilder all = new StringBuilder();
            for (String text : snapshot(twoX).values()) all.append(text);
            assertTrue(all.indexOf("io.elasticore.springboot3.") >= 0, "the 2.x code no longer names its runtime");
            assertTrue(all.indexOf("com.test.") < 0, "the 2.x code was rewritten");
            assertFalse(new File(twoX, RuntimePackages.REPORT).exists(), "a 2.x run listed runtime classes");
        } finally {
            resetContext();
            delete(twoX);
        }
    }

    // ---- helpers ------------------------------------------------------------

    private static String file(String path) {
        String text = written.get(path);
        assertTrue(text != null, "not generated: " + path);
        return text;
    }

    private static Map<String, String> snapshot(File dir) throws IOException {
        final Map<String, String> files = new TreeMap<String, String>();
        final Path root = dir.toPath();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = root.relativize(file).toString().replace('\\', '/');
                boolean dsl = rel.startsWith("src/main/resources/blueprint/petstore/");
                boolean guide = rel.startsWith("src/main/resources/blueprint/") && rel.endsWith(".md");
                if (!dsl && !guide) {
                    String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
                    // The stamp's time differs on every run by design.
                    if (text.startsWith("//ecd:")) text = text.substring(text.indexOf('\n') + 1);
                    files.put(rel, text);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private static void copyTree(final Path from, final Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Removes a directory this test created under the system temp directory - nothing else. */
    static void delete(File dir) throws IOException {
        if (dir == null || !dir.exists()) return;
        String temp = new File(System.getProperty("java.io.tmpdir")).getCanonicalPath();
        if (!dir.getCanonicalPath().startsWith(temp) || !dir.getName().startsWith("elcore-rtpkg")) {
            throw new IllegalStateException("refusing to delete " + dir);
        }
        Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void resetContext() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
    }

    static File repositoryRoot() {
        File dir = new File("").getAbsoluteFile();
        while (dir != null && !new File(dir, "settings.gradle").isFile()) dir = dir.getParentFile();
        return dir;
    }
}
