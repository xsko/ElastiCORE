package io.elasticore.base.model.pub.rules.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.elasticore.base.Version;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TreeCheckRunnerTest {

    @TempDir
    Path dir;

    /** A file this generator stamps that 2.x never wrote is an extra file, whatever 3.x version runs. */
    @Test
    void aFileOnlyTheRuleEngineWritesIsExtraUnderThisGeneratorsVersion() throws Exception {
        File golden = dir.resolve("golden").toFile();
        File generated = dir.resolve("generated").toFile();
        String body = "package a;\n\npublic class ItemDTO {}\n";
        write(new File(golden, "a/ItemDTO.java"), "//ecd:1H20240101_V2.2\n" + body);
        write(new File(generated, "a/ItemDTO.java"), "//ecd:1H20261120_V" + Version.MAJOR + "\n" + body);
        write(
                new File(generated, "a/ItemExtra.java"),
                "//ecd:2H20261120_V" + Version.MAJOR + "\npackage a;\n\npublic class ItemExtra {}\n");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        int failures;
        try {
            failures = new TreeCheckRunner().run(golden, generated);
        } finally {
            System.setOut(out);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        assertEquals(1, failures, log);
        assertTrue(log.contains("1 identical") && log.contains("1 extra"), log);
        assertTrue(log.contains("EXTRA a/ItemExtra.java"), log);
    }

    private static void write(File file, String text) throws Exception {
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }
}
