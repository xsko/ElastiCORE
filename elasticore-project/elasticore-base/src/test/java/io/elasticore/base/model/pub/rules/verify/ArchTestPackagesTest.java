package io.elasticore.base.model.pub.rules.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/**
 * archTest (issue 173): besides the namespaces, the generated architecture test checks the packages the
 * project's own code adds under its base, placed by their first segment - so a hand-written domain.policy class
 * that depends on infrastructure.mail is caught. A package no segment places is said once; a layered layout is
 * read from its namespaces alone; the test already written follows the three-way merge like any owned file.
 */
public class ArchTestPackagesTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Post:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      postId: long @id @sequence",
            "      title:  string(200)!",
            "");

    private static final String HEXAGONAL = "    base: com.acme.forum\n";

    private static final String LAYERED = String.join(
            "\n",
            "    entity: com.acme.forum.entity",
            "    enumeration: com.acme.forum.entity",
            "    dto: com.acme.forum.dto",
            "    repository: com.acme.forum.repository",
            "    service: com.acme.forum.service",
            "    control: com.acme.forum.controller",
            "");

    private static final String TEST = "src/test/java/com/acme/forum/ForumArchitectureTest.java";

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
    public void aHandWrittenDomainPolicyThatUsesInfrastructureMailIsCaught() throws Exception {
        File project = project("elcore-arch-hex", HEXAGONAL);
        // The project's own code, beside what the generator writes.
        source(project, "src/main/java/com/acme/forum/ForumApplication.java", "com.acme.forum", "ForumApplication");
        source(
                project,
                "src/main/java/com/acme/forum/domain/policy/PostingPolicy.java",
                "com.acme.forum.domain.policy",
                "PostingPolicy",
                "import com.acme.forum.infrastructure.mail.SmtpMailSender;");
        source(
                project,
                "src/main/java/com/acme/forum/infrastructure/mail/SmtpMailSender.java",
                "com.acme.forum.infrastructure.mail",
                "SmtpMailSender");
        source(
                project,
                "src/main/java/com/acme/forum/adapter/in/mq/PostListener.java",
                "com.acme.forum.adapter.in.mq",
                "PostListener");
        source(
                project,
                "src/main/java/com/acme/forum/adapter/out/search/SearchClient.java",
                "com.acme.forum.adapter.out.search",
                "SearchClient");
        source(
                project,
                "src/main/java/com/acme/forum/domain/model/PostRules.java",
                "com.acme.forum.domain.model",
                "PostRules");
        source(project, "src/main/java/com/acme/forum/config/WebConfig.java", "com.acme.forum.config", "WebConfig");
        source(project, "src/test/java/com/acme/forum/fixture/Fixtures.java", "com.acme.forum.fixture", "Fixtures");

        String[] log = new String[1];
        run(project, "java", log);
        String test = read(new File(project, TEST));

        String domainRule = rule(test, "domainDependsOnNothingOutside");
        assertTrue(
                domainRule.contains(".that().resideInAnyPackage(\"com.acme.forum.domain.model..\", "
                        + "\"com.acme.forum.domain.event..\", \"com.acme.forum.domain.service..\", "
                        + "\"com.acme.forum.domain.repository..\", \"com.acme.forum.domain.policy..\")"),
                domainRule);
        assertTrue(domainRule.contains("\"com.acme.forum.infrastructure.mail..\""), domainRule);
        assertTrue(domainRule.contains("\"com.acme.forum.adapter.in.mq..\""), domainRule);
        assertTrue(domainRule.contains("\"com.acme.forum.adapter.out.search..\""), domainRule);

        String applicationRule = rule(test, "applicationDependsOnPortsNotAdapters");
        assertTrue(
                applicationRule.contains(".that().resideInAnyPackage(\"com.acme.forum.application..\")"),
                applicationRule);
        assertTrue(
                applicationRule.contains("\"com.acme.forum.adapter.in.web..\", \"com.acme.forum.adapter.in.mq..\""),
                applicationRule);
        assertTrue(
                applicationRule.contains("\"com.acme.forum.infrastructure.persistence..\", "
                        + "\"com.acme.forum.adapter.out.search..\", \"com.acme.forum.infrastructure.mail..\""),
                applicationRule);

        assertTrue(rule(test, "domainNamesNoWeb").contains("\"com.acme.forum.domain.policy..\""), test);
        assertTrue(
                test.contains(" * namespaces the domain's env.yml declares, and the packages of your own\n"
                        + " * code under them, by their first segment. Yours from here: add rules.\n"),
                test);

        // The base (the application class), a package under a namespace and a test's are none of its business.
        assertFalse(test.contains("\"com.acme.forum..\""), test);
        assertFalse(test.contains("domain.model.PostRules"), test);
        assertFalse(test.contains("fixture"), test);
        // One it cannot place is said, once.
        String said = "com.acme.forum.config: ForumArchitectureTest places a package of your own by its first segment"
                + " after com.acme.forum (domain, application, adapter, infrastructure), and this one has none of them";
        assertTrue(log[0].contains(said), log[0]);
        assertFalse(test.contains("config"), test);
        assertFalse(log[0].contains("com.acme.forum.fixture"), log[0]);
    }

    @Test
    public void kotlinReadsAKeywordInAPackage() throws Exception {
        File project = project("elcore-arch-kt", HEXAGONAL);
        write(
                project,
                "src/main/kotlin/com/acme/forum/adapter/in/mq/PostListener.kt",
                "package com.acme.forum.adapter.`in`.mq\n\nclass PostListener\n");
        write(
                project,
                "src/main/kotlin/com/acme/forum/domain/policy/PostingPolicy.kt",
                "package com.acme.forum.domain.policy\n\nclass PostingPolicy\n");
        run(project, "kotlin", new String[1]);
        String test = read(new File(project, "src/test/kotlin/com/acme/forum/ForumArchitectureTest.kt"));
        assertTrue(
                rule(test, "applicationDependsOnPortsNotAdapters").contains("\"com.acme.forum.adapter.in.mq..\""),
                test);
        assertTrue(rule(test, "domainDependsOnNothingOutside").contains("\"com.acme.forum.domain.policy..\""), test);
        assertFalse(test.contains("`"), test);
    }

    @Test
    public void withoutCodeOfItsOwnTheTestIsWrittenAsBefore() throws Exception {
        File project = project("elcore-arch-none", HEXAGONAL);
        source(project, "src/main/java/com/acme/forum/ForumApplication.java", "com.acme.forum", "ForumApplication");
        String[] log = new String[1];
        run(project, "java", log);
        String test = read(new File(project, TEST));
        assertTrue(test.contains(" * namespaces the domain's env.yml declares. Yours from here: add rules.\n"), test);
        assertTrue(
                rule(test, "applicationDependsOnPortsNotAdapters")
                        .contains(".should().dependOnClassesThat()"
                                + ".resideInAnyPackage(\"com.acme.forum.adapter.in.web..\", "
                                + "\"com.acme.forum.infrastructure.persistence..\");"),
                test);
        assertFalse(log[0].contains("places a package of your own"), log[0]);
    }

    /** A layered layout's segments name no layers: its own packages are left alone, and nothing is said. */
    @Test
    public void aLayeredLayoutIsReadFromItsNamespacesAlone() throws Exception {
        File project = project("elcore-arch-layered", LAYERED);
        source(project, "src/main/java/com/acme/forum/util/Strings.java", "com.acme.forum.util", "Strings");
        source(
                project,
                "src/main/java/com/acme/forum/domain/policy/PostingPolicy.java",
                "com.acme.forum.domain.policy",
                "PostingPolicy");
        String[] log = new String[1];
        run(project, "java", log);
        String test = read(new File(project, TEST));
        assertFalse(test.contains("util"), test);
        assertFalse(test.contains("policy"), test);
        assertTrue(test.contains(" * namespaces the domain's env.yml declares. Yours from here: add rules.\n"), test);
        assertFalse(log[0].contains("places a package of your own"), log[0]);
    }

    /**
     * Where the project put its web and its persistence under segments of their own - interfaces.rest, say - those
     * segments name the layers too: the code it adds beside them goes with them.
     */
    @Test
    public void theSegmentsOfItsWebAndPersistenceNameTheirLayers() throws Exception {
        File project = project(
                "elcore-arch-segments",
                String.join(
                        "\n",
                        "    base: com.acme.forum",
                        "    control: com.acme.forum.interfaces.rest",
                        "    repository: com.acme.forum.storage.jpa",
                        "    persistence: com.acme.forum.db.mapping",
                        ""));
        source(
                project,
                "src/main/java/com/acme/forum/interfaces/mq/PostListener.java",
                "com.acme.forum.interfaces.mq",
                "PostListener");
        source(
                project,
                "src/main/java/com/acme/forum/storage/cache/PostCache.java",
                "com.acme.forum.storage.cache",
                "PostCache");
        source(project, "src/main/java/com/acme/forum/db/audit/AuditLog.java", "com.acme.forum.db.audit", "AuditLog");
        String[] log = new String[1];
        run(project, "java", log);
        String test = read(new File(project, TEST));

        // The adapters come first, then persistence, whose list the two placed with it end.
        String applicationRule = rule(test, "applicationDependsOnPortsNotAdapters");
        assertTrue(
                applicationRule.contains("\"com.acme.forum.interfaces.rest..\", \"com.acme.forum.interfaces.mq..\""),
                applicationRule);
        assertTrue(
                applicationRule.contains("\"com.acme.forum.db.audit..\", \"com.acme.forum.storage.cache..\");"),
                applicationRule);
        assertFalse(log[0].contains("places a package of your own"), log[0]);
    }

    /**
     * Written once, then owned: a test nobody edited takes the new package lists when the build runs again; an
     * edited one goes through the three-way merge, keeping its own rule and taking the lists.
     */
    @Test
    public void theTestAlreadyWrittenFollowsTheMerge() throws Exception {
        File untouched = project("elcore-arch-again", HEXAGONAL);
        run(untouched, "java", new String[1]);
        assertFalse(read(new File(untouched, TEST)).contains("policy"));
        source(
                untouched,
                "src/main/java/com/acme/forum/domain/policy/PostingPolicy.java",
                "com.acme.forum.domain.policy",
                "PostingPolicy");
        run(untouched, "java", new String[1]);
        assertTrue(rule(read(new File(untouched, TEST)), "domainDependsOnNothingOutside")
                .contains("\"com.acme.forum.domain.policy..\""));

        File edited = project("elcore-arch-edited", HEXAGONAL);
        run(edited, "java", new String[1]);
        File file = new File(edited, TEST);
        String mine = "\n    /** Ours. */\n    @ArchTest\n    static final ArchRule ours = noClasses()\n"
                + "            .that().resideInAnyPackage(\"com.acme.forum.application..\")\n"
                + "            .should().dependOnClassesThat().resideInAnyPackage(\"java.sql..\");\n";
        String text = read(file);
        Files.write(
                file.toPath(),
                (text.substring(0, text.lastIndexOf('}')) + mine + "}\n").getBytes(StandardCharsets.UTF_8));
        source(
                edited,
                "src/main/java/com/acme/forum/domain/policy/PostingPolicy.java",
                "com.acme.forum.domain.policy",
                "PostingPolicy");
        run(edited, "java", new String[1]);
        String merged = read(file);
        assertTrue(merged.contains("static final ArchRule ours = noClasses()"), merged);
        assertTrue(
                rule(merged, "domainDependsOnNothingOutside").contains("\"com.acme.forum.domain.policy..\""), merged);
    }

    @Test
    public void aPackageIsReadAsTheJvmNamesIt() {
        assertEquals(
                "com.acme.forum.adapter.in.mq",
                RulesGenRunner.declaredPackage("// x\npackage com.acme.forum.adapter.`in`.mq\n\nclass A\n"));
        assertEquals("com.acme.forum", RulesGenRunner.declaredPackage("package com.acme.forum;\nclass A {}\n"));
        assertEquals("", RulesGenRunner.declaredPackage("class A {}\n"));
    }

    // ------------------------------------------------------------------

    /** The rule's declaration, from its name to the end of its statement. */
    private static String rule(String test, String name) {
        int from = test.indexOf(" " + name + " ");
        if (from < 0) from = test.indexOf(" " + name + ":");
        assertTrue(from >= 0, name + "\n" + test);
        int to = test.indexOf("\n\n", from);
        return test.substring(from, to < 0 ? test.length() : to);
    }

    private static File project(String name, String namespaces) throws IOException {
        File project = Files.createTempDirectory(name).toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/forum");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: forum",
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "    archTest: true",
                                "  namespace:",
                                namespaces)
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    private static void source(File project, String path, String pkg, String cls, String... imports)
            throws IOException {
        StringBuilder s = new StringBuilder("package ").append(pkg).append(";\n\n");
        for (String i : imports) s.append(i).append('\n');
        s.append("\npublic class ").append(cls).append(" {\n}\n");
        write(project, path, s.toString());
    }

    private static void write(File project, String path, String text) throws IOException {
        File f = new File(project, path);
        assertTrue(f.getParentFile().isDirectory() || f.getParentFile().mkdirs());
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static void run(File project, String language, String[] log) throws Exception {
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
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "hexagonal");
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            assertEquals(0, RulesGenRunner.run(new String[0]), "the run's exit: " + project);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
            io.elasticore.base.model.pub.rules.ModelCheck.reset();
            io.elasticore.base.util.ConsoleLog.clearKey("DSL_WARN");
        }
        log[0] = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log[0]);
    }

    private static String read(File file) throws IOException {
        assertTrue(file.isFile(), file + " was not written");
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
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
