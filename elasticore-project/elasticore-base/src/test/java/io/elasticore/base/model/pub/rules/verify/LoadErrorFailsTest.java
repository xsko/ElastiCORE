package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A model file that does not parse used to be a "LOAD ERROR" line the build
 * walked past: generation succeeded without anything the file declared, and
 * the compile failed far away, on the hand-written code that used it. The rule
 * engine now ends the run non-zero, naming the file and the parser's message.
 * Found writing a recipe whose label held ": " (a YAML mapping).
 */
public class LoadErrorFailsTest {

    private File project;

    @Test
    public void aModelFileThatDoesNotParseFailsTheRun() throws Exception {
        project = Files.createTempDirectory("elcore-load-error").toFile();
        File blueprint = new File(project, "src/main/resources/blueprint/ldshop");
        assertTrue(new File(blueprint, "model").mkdirs());
        write(new File(blueprint, "env.yml"), String.join("\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: ldshop",
                "    framework: springboot",
                "    mode: jpa",
                "    j2ee: jakarta",
                "  namespace:",
                "    base: io.acme.ldshop",
                ""));
        write(new File(blueprint, "model/good.yml"), String.join("\n",
                "entity:",
                "  LdItem:",
                "    meta: entity @root @editable @expose(20)",
                "    fields:",
                "      itemId: long @id @sequence",
                "      name:   string(30)!",
                ""));
        // A label with ": " in it - a mapping where YAML expects a value.
        write(new File(blueprint, "model/broken.yml"), String.join("\n",
                "entity:",
                "  LdOrder:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      orderId:  long @id @sequence",
                "      memberId: long -- who ordered it: the signed-in member",
                ""));

        java.io.PrintStream out = System.out;
        java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        int status;
        System.setOut(new java.io.PrintStream(captured, true, "UTF-8"));
        try {
            status = run();
        } finally {
            System.setOut(out);
        }
        String log = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        assertEquals(1, status, "the build must stop");
        assertTrue(log.contains("the model check failed"), log);
        assertTrue(log.contains("broken.yml - not read, nothing it declares was generated: mapping values are not allowed here"), log);
        // The good file still generates, so every finding is said in one run.
        assertTrue(new File(project, "src/main/java/io/acme/ldshop").exists());
    }

    private int run() {
        String[] keys = {"elasticore.base.path", "elasticore.architecture", "elasticore.language", "elasticore.rules.force"};
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "hexagonal");
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.force", "true");
            return RulesGenRunner.run(new String[0]);
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
    }

    private static void write(File f, String text) throws Exception {
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    void clean() throws Exception {
        java.lang.reflect.Field tmp = io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        io.elasticore.base.model.pub.rules.ModelCheck.reset();
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
        if (project == null || !project.exists()) return;
        Files.walkFileTree(project.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, java.io.IOException e) throws java.io.IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
