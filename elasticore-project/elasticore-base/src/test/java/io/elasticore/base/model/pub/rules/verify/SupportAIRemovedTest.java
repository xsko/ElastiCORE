package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
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
 * supportAI was removed. A domain that still sets it gets the plain extension
 * service - no Spring AI tools, no Spring AI import - from every generator,
 * and the run that writes the services says, once per domain, that the
 * setting is ignored. A domain without it, and the TypeScript client's run,
 * say nothing.
 */
public class SupportAIRemovedTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Accident:",
            "    meta: entity @expose -- car accident",
            "    fields:",
            "      accidentId: long @id @sequence",
            "");

    private static final String REMOVED = "[ElastiCORE] domain 'claim': supportAI was removed and is ignored";

    private static final List<File> projects = new ArrayList<File>();

    @AfterAll
    static void cleanUp() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        for (File project : projects) delete(project);
    }

    // The runs whose words are read happen in the tests themselves, so that a
    // mutation test sees which test runs the lines that say them.
    @Test
    public void theRuleEngineWritesThePlainServiceAndSaysSoOnce() throws Exception {
        for (String run : new String[] {"elasticore.rules.v2", "elasticore.rules.v3"}) {
            File project = project("elcore-ai-removed", true);
            String log = logOf(project, run);
            assertTrue(log.contains(REMOVED), log);
            assertEquals(log.indexOf(REMOVED), log.lastIndexOf(REMOVED), "once per domain:\n" + log);
            assertPlain(found(project, "AccidentService.java"));
        }
    }

    @Test
    public void aDomainWithoutSupportAIIsNotTold() throws Exception {
        String log = logOf(project("elcore-ai-off", false), "elasticore.rules.v3");
        assertFalse(log.contains("supportAI"), log);
    }

    /** What both generators call: said for "true" alone, and it answers whether it was said. */
    @Test
    public void theWarningIsSaidForTrueAlone() throws Exception {
        for (String value : new String[] {"true", "false", null}) {
            java.io.PrintStream out = System.out;
            java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
            System.setOut(new java.io.PrintStream(captured, true, "UTF-8"));
            boolean said;
            try {
                said = io.elasticore.base.model.pub.jpa.ServiceSrcPublisher.warnIfSupportAI("claim", value);
            } finally {
                System.setOut(out);
            }
            String log = new String(captured.toByteArray(), StandardCharsets.UTF_8);
            assertEquals("true".equals(value), said, String.valueOf(value));
            assertEquals("true".equals(value), log.contains(REMOVED), log);
        }
    }

    /** The TypeScript client's run writes no service, so it has nothing to say about it. */
    @Test
    public void theTypeScriptRunSaysNothing() throws Exception {
        String log = logOf(project("elcore-ai-ts", true), "elasticore.rules.ts");
        assertFalse(log.contains("supportAI"), log);
    }

    /** The 2.x generator (./gradlew elcore) says it too, and writes the plain service. */
    @Test
    public void theTemplateGeneratorWritesThePlainServiceAndSaysSoOnce() throws Exception {
        File project = project("elcore-ai-removed-2x", true);
        // A JVM of its own, as './gradlew elcore' runs: the extractor keeps the
        // loaded model in static state that the other tests expect empty.
        File log = new File(project, "gen.log");
        Process p = new ProcessBuilder(
                        new File(new File(System.getProperty("java.home"), "bin"), "java").getPath(),
                        "-Xmx256m",
                        "-Dfile.encoding=UTF-8",
                        "-Delasticore.base.path=" + project.getAbsolutePath(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        io.elasticore.base.extract.ModelExtractor.class.getName())
                .directory(project)
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start();
        try {
            assertTrue(p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS), "generation did not finish");
        } finally {
            // Waited for, so the log is no longer held open when the folder is removed.
            p.destroyForcibly().waitFor();
        }
        String genLog = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), genLog);
        assertTrue(genLog.contains(REMOVED), genLog);
        assertEquals(genLog.indexOf(REMOVED), genLog.lastIndexOf(REMOVED), "once per domain:\n" + genLog);
        assertPlain(found(project, "AccidentService.java"));
    }

    private static void assertPlain(String service) {
        assertTrue(service.contains("class AccidentService extends AccidentCoreService"), service);
        assertFalse(service.contains("@Tool") || service.contains("org.springframework.ai"), service);
    }

    private static File project(String name, boolean supportAI) throws Exception {
        File project = Files.createTempDirectory(name).toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/claim");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: claim",
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "    supportAI: " + supportAI,
                                "  namespace:",
                                "    entity: io.acme.claim.entity",
                                "    dto: io.acme.claim.dto",
                                "    repository: io.acme.claim.repository",
                                "    service: io.acme.claim.service",
                                "    control: io.acme.claim.controller",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    private static String logOf(File project, String run) throws Exception {
        java.io.PrintStream out = System.out;
        java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(captured, true, "UTF-8"));
        try {
            generate(project, run);
        } finally {
            System.setOut(out);
        }
        return new String(captured.toByteArray(), StandardCharsets.UTF_8);
    }

    /** {@code run}: elasticore.rules.v2 (the 2.x bundle), .v3, or .ts (the TypeScript client). */
    private static void generate(File project, String run) throws Exception {
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
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "layered");
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty(run, "true");
            assertEquals(0, RulesGenRunner.run(new String[0]), "the run failed: " + project);
        } finally {
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
        return new String(Files.readAllBytes(hits.get(0).toPath()), StandardCharsets.UTF_8);
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
