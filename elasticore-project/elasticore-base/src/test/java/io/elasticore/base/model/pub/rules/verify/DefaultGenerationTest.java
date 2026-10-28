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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generation a layered Java project gets since 3.0.261028-rc3: the 3.0 code
 * unless it asks for 2.x's - and never 3.0's code written over a project that
 * holds what 2.x generated and asked for nothing.
 *
 * Until then a project that said nothing got the 2.x-compatible code, and
 * the 3.0 code took -Pv3 on every run, which nobody starting a project on 3.0
 * could be expected to know. The opposite default has its own trap, which
 * the last two tests are about: an upgrade that rewrote a 2.x project's
 * sources and changed how its search and refusals behave.
 */
public class DefaultGenerationTest {

    private static final String[] KEYS = {"elasticore.base.path", "elasticore.architecture",
            "elasticore.language", "elasticore.rules.force", "elasticore.rules.v2", "elasticore.rules.v3",
            "elasticore.rules.ts", "elasticore.rules.kt", "elasticore.publisher.rules.add", "elasticore.rules.list"};

    private static final String MODEL = String.join("\n",
            "entity:",
            "  Note:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      noteId: long @id @sequence",
            "      title: string(100)! @search(like)",
            "");

    private final List<File> projects = new ArrayList<File>();

    @AfterEach
    void clean() throws IOException {
        for (File p : projects) delete(p.toPath());
    }

    @Test
    public void aProjectThatSaysNothingGetsTheV3Code() throws Exception {
        File project = project(null);
        String log = run(project);
        assertTrue(log.contains("mode      : v3 - the default"), log);
        assertV3(project, log);
    }

    @Test
    public void v2KeepsTheCode2xGenerates() throws Exception {
        File project = project(null);
        String log = run(project, "elasticore.rules.v2");
        assertTrue(log.contains("mode      : 2.x-compatible - -Pv2 / output = 'v2'"), log);
        assertCompatible(project, log);
    }

    @Test
    public void aDomainNamingThe2xBundleKeepsIt() throws Exception {
        // What the initialization wizard wrote for layered Java before 3.0.261028-rc3.
        File project = project("jpa-springboot");
        String log = run(project);
        assertTrue(log.contains("2.x-compatible - domain 'dgshop' names jpa-springboot"), log);
        assertCompatible(project, log);
    }

    @Test
    public void aDeclaredLayeredArchitectureIsV3Too() throws Exception {
        File project = project(null);
        System.setProperty("elasticore.architecture", "layered");
        System.setProperty("elasticore.language", "java");
        String log = run(project);
        assertV3(project, log);
    }

    @Test
    public void bothGenerationsAtOnceAreRefused() throws Exception {
        File project = project(null);
        System.setProperty("elasticore.rules.v2", "true");
        System.setProperty("elasticore.rules.v3", "true");
        assertEquals(1, RulesGenRunner.run(new String[0]));
    }

    @Test
    public void v2IsNotAHexagon() throws Exception {
        File project = project(null);
        System.setProperty("elasticore.base.path", project.getAbsolutePath());
        System.setProperty("elasticore.architecture", "hexagonal");
        System.setProperty("elasticore.rules.v2", "true");
        assertEquals(1, RulesGenRunner.run(new String[0]));
    }

    @Test
    public void aProjectHolding2xCodeIsStoppedNotRewritten() throws Exception {
        File project = project(null);
        File old = twoXFile(project);
        String before = new String(Files.readAllBytes(old.toPath()), StandardCharsets.UTF_8);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        int code;
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.rules.force", "true");
            code = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        assertEquals(1, code, log);
        assertEquals(before, new String(Files.readAllBytes(old.toPath()), StandardCharsets.UTF_8));
        // Nothing of the 3.0 code beside it either.
        assertNull(find(project, "Note.java"), "an entity was written: " + log);
    }

    @Test
    public void aProjectHolding2xCodeMovesWhenItSaysSo() throws Exception {
        File project = project(null);
        twoXFile(project);
        String log = run(project, "elasticore.rules.v3");
        assertTrue(log.contains("mode      : v3 - -Pv3 / output = 'v3'"), log);
        assertV3(project, log);
    }

    @Test
    public void v2IsNotAV3BundleADomainNames() throws Exception {
        // What the wizard writes for layered Java since 3.0.261028-rc3, then output = 'v2'.
        File project = project("jpa-springboot-v3");
        System.setProperty("elasticore.base.path", project.getAbsolutePath());
        System.setProperty("elasticore.rules.v2", "true");
        assertEquals(1, RulesGenRunner.run(new String[0]));
        assertNull(find(project, "Note.java"));
    }

    @Test
    public void v2IsNotTheProjectsOwnRules() throws Exception {
        File project = project(null);
        System.setProperty("elasticore.base.path", project.getAbsolutePath());
        System.setProperty("elasticore.rules.v2", "true");
        System.setProperty("elasticore.publisher.rules.add", "ossie-export");
        assertEquals(1, RulesGenRunner.run(new String[0]));
    }

    @Test
    public void aLastRunInThe2xModeStopsTheDefaultToo() throws Exception {
        // A 3.0 pre-release that generated the 2.x-compatible code stamps
        // _V3.0; what it left in build/elasticore/server.mode says which.
        File project = project(null);
        File mode = new File(project, "build/elasticore/server.mode");
        assertTrue(mode.getParentFile().mkdirs());
        Files.write(mode.toPath(), "compatible".getBytes(StandardCharsets.UTF_8));
        System.setProperty("elasticore.base.path", project.getAbsolutePath());
        System.setProperty("elasticore.rules.force", "true");
        assertEquals(1, RulesGenRunner.run(new String[0]));
        assertNull(find(project, "Note.java"));
    }

    @Test
    public void aClientOfA2xServerFollowsIt() throws Exception {
        // The TypeScript client writes no Java: it is not stopped, it follows the server it has.
        File project = project(null);
        twoXFile(project);
        String log = run(project, "elasticore.rules.ts");
        assertTrue(log.contains("typescript, for a server in 2.x-compatible - the server holds code 2.x generated"), log);
    }

    @Test
    public void aListingIsNotStopped() throws Exception {
        // -PlistRules writes nothing, so there is nothing to protect.
        File project = project(null);
        twoXFile(project);
        String log = run(project, "elasticore.rules.list");
        assertFalse(log.contains("holds the code 2.x generates"), log);
    }

    @Test
    public void aMongoDomainIsRefusedNotWrittenAsJpa() throws Exception {
        // mode: mongo is MongoDB code, which only the 2.x generator writes. The
        // rule engine read no mode at all: the 3.0 default wrote JPA entities,
        // repositories and services for a MongoDB domain without a word.
        File project = project(null, "mongo");
        System.setProperty("elasticore.base.path", project.getAbsolutePath());
        System.setProperty("elasticore.rules.force", "true");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        int code;
        try {
            code = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        assertEquals(1, code, log);
        assertTrue(log.contains("mode: mongo"), log);
        assertNull(find(project, "Note.java"), "JPA code was written for a MongoDB domain: " + log);
    }

    @Test
    public void aDiagramModeBesideJpaIsSaidNotDropped() throws Exception {
        // uml and dsl are documents the 2.x generator writes beside the code;
        // the rule engine writes the code and says what it leaves out.
        File project = project(null, "jpa,uml");
        String log = run(project);
        assertTrue(log.contains("mode uml"), log);
        assertNotNull(find(project, "Note.java"), log);
    }

    @Test
    public void aMyBatisDomainUnderTheTwoXSwitchIsRefused() throws Exception {
        // MyBatis code is 3.0 code: -Pv2 asks for 2.x's JPA code, and the
        // refusal must not send the domain to the 2.x generator either.
        File project = project(null, "mybatis");
        System.setProperty("elasticore.base.path", project.getAbsolutePath());
        System.setProperty("elasticore.rules.force", "true");
        System.setProperty("elasticore.rules.v2", "true");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        int code;
        try {
            code = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        assertEquals(1, code, log);
        assertTrue(log.contains("is mode: mybatis, but the code 2.x generates"), log);
        assertNull(find(project, "Note.java"), "code was written for a refused domain: " + log);
    }

    @Test
    public void jpaBesideMongoStillWritesTheJpaCode() throws Exception {
        // 2.x writes both for "jpa,mongo"; the rule engine writes the JPA half
        // and says the rest is the 2.x generator's - as before the axis existed.
        File project = project(null, "jpa,mongo");
        String log = run(project);
        assertTrue(log.contains("mode mongo"), log);
        assertNotNull(find(project, "Note.java"), log);
    }

    @Test
    public void theMarkerIsReadAsTheGeneratorWroteIt() throws Exception {
        File project = project(null);
        List<String> roots = new ArrayList<String>();
        roots.add(project.getAbsolutePath());
        assertNull(RulesGenRunner.twoXGenerated(roots));
        twoXFile(project);
        assertEquals("io/acme/dgshop/entity/Legacy.java", RulesGenRunner.twoXGenerated(roots));
    }

    // ---- helpers ------------------------------------------------------------------------

    private void assertV3(File project, String log) throws IOException {
        Path entity = find(project, "Note.java");
        assertNotNull(entity, log);
        String text = new String(Files.readAllBytes(entity), StandardCharsets.UTF_8);
        // The v3 entity imports what it uses; 2.x's template imports both packages whole.
        assertFalse(text.contains("import jakarta.persistence.*;"), text);
    }

    private void assertCompatible(File project, String log) throws IOException {
        Path entity = find(project, "Note.java");
        assertNotNull(entity, log);
        String text = new String(Files.readAllBytes(entity), StandardCharsets.UTF_8);
        assertTrue(text.contains("import jakarta.persistence.*;"), text);
    }

    private String run(File project, String... switches) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        int code;
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.rules.force", "true");
            for (String s : switches) System.setProperty(s, "true");
            code = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        assertEquals(0, code, log);
        return log;
    }

    /** A source file as 2.x leaves it: the hash line names the generator that wrote it. */
    private static File twoXFile(File project) throws IOException {
        File file = new File(project, "src/main/java/io/acme/dgshop/entity/Legacy.java");
        assertTrue(file.getParentFile().mkdirs());
        Files.write(file.toPath(), ("//ecd:-441213192H20240414181942_V2.1\n"
                + "package io.acme.dgshop.entity;\n\npublic class Legacy {\n}\n").getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private File project(String rules) throws IOException {
        return project(rules, "jpa");
    }

    private File project(String rules, String mode) throws IOException {
        File dir = Files.createTempDirectory("elcore-default").toFile();
        projects.add(dir);
        File blueprint = new File(dir, "src/main/resources/blueprint/dgshop");
        assertTrue(new File(blueprint, "model").mkdirs());
        List<String> env = new ArrayList<String>();
        env.add("elasticore:");
        env.add("  enable: true");
        env.add("  config:");
        env.add("    domainName: dgshop");
        env.add("    framework: springboot");
        env.add("    mode: " + mode);
        env.add("    j2ee: jakarta");
        if (rules != null) {
            env.add("    publisher:");
            env.add("      rules: [" + rules + "]");
        }
        env.add("  namespace:");
        env.add("    base: io.acme.dgshop");
        env.add("");
        Files.write(new File(blueprint, "env.yml").toPath(), String.join("\n", env).getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private static Path find(File project, String name) throws IOException {
        File java = new File(project, "src/main/java");
        if (!java.isDirectory()) return null;
        try (Stream<Path> all = Files.walk(java.toPath())) {
            return all.filter(p -> p.getFileName().toString().equals(name)).findFirst().orElse(null);
        }
    }

    private final String[] saved = save();

    private static String[] save() {
        String[] s = new String[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) s[i] = System.getProperty(KEYS[i]);
        return s;
    }

    @AfterEach
    void restore() {
        for (int i = 0; i < KEYS.length; i++) {
            if (saved[i] == null) System.clearProperty(KEYS[i]);
            else System.setProperty(KEYS[i], saved[i]);
        }
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
