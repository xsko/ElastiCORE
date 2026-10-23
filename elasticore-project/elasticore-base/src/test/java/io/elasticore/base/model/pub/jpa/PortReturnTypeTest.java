package io.elasticore.base.model.pub.jpa;

import io.elasticore.base.extract.ModelExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a port method declares it returns: the DSL's words for one value become
 * Java types. "decimal" was written into the interface as it stood - a type
 * Java does not have - so the project stopped compiling.
 */
public class PortReturnTypeTest {

    private static final String ENV =
            "elasticore:\n"
            + "  enable: true\n"
            + "  config:\n"
            + "    domainName: portfx\n"
            + "    framework: springboot\n"
            + "    mode: jpa\n"
            + "    j2ee: jakarta\n"
            + "  namespace:\n"
            + "    entity: io.fx.entity\n"
            + "    dto: io.fx.dto\n"
            + "    enumeration: io.fx.enums\n"
            + "    repository: io.fx.repository\n"
            + "    service: io.fx.service\n"
            + "    control: io.fx.controller\n"
            + "    port: io.fx.port\n";

    private static final String PORT =
            "port:\n"
            + "  FxReportPort:\n"
            + "    meta: dbms\n"
            + "    methods:\n"
            + "      countItems:\n"
            + "        meta: method\n"
            + "        params:\n"
            + "        return: long\n"
            + "        nativeQuery: true\n"
            + "        query: SELECT COUNT(*) FROM fx_item\n"
            + "      totalPrice:\n"
            + "        meta: method\n"
            + "        params:\n"
            + "        return: decimal\n"
            + "        nativeQuery: true\n"
            + "        query: SELECT SUM(price) FROM fx_item\n"
            + "      maxPrice:\n"
            + "        meta: method\n"
            + "        params:\n"
            + "        return: bigdecimal\n"
            + "        nativeQuery: true\n"
            + "        query: SELECT MAX(price) FROM fx_item\n"
            + "      prices:\n"
            + "        meta: method\n"
            + "        params:\n"
            + "        return: List<decimal>\n"
            + "        nativeQuery: true\n"
            + "        query: SELECT price FROM fx_item\n"
            + "      nameOf:\n"
            + "        meta: method\n"
            + "        params:\n"
            + "          itemId: long\n"
            + "        return: string\n"
            + "        nativeQuery: true\n"
            + "        query: SELECT name FROM fx_item WHERE item_id = :itemId\n";

    @TempDir
    Path dir;

    @Test
    public void oneValueReturnsAreJavaTypes() throws Exception {
        Path domain = dir.resolve("src/main/resources/blueprint/portfx");
        Files.createDirectories(domain.resolve("port"));
        Files.write(domain.resolve("env.yml"), ENV.getBytes(StandardCharsets.UTF_8));
        Files.write(domain.resolve("port/report_port.yml"), PORT.getBytes(StandardCharsets.UTF_8));

        // Generate in a JVM of its own, as './gradlew elcore' does: the extractor
        // keeps the loaded model in static state that other tests here expect empty.
        Path log = dir.resolve("gen.log");
        Process p = new ProcessBuilder(
                Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx256m",
                "-Dfile.encoding=UTF-8",
                "-Delasticore.base.path=" + dir,
                "-cp", System.getProperty("java.class.path"),
                ModelExtractor.class.getName())
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        try {
            assertTrue(p.waitFor(120, TimeUnit.SECONDS), "generation did not finish");
        } finally {
            p.destroyForcibly();
        }
        String genLog = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), genLog);

        Path port = dir.resolve("src/main/java/io/fx/port/FxReportPort.java");
        assertTrue(Files.exists(port), "not generated: " + port + "\n" + genLog);
        String src = new String(Files.readAllBytes(port), StandardCharsets.UTF_8);

        assertTrue(src.contains("Long countItems("), src);
        assertTrue(src.contains("java.math.BigDecimal totalPrice("), src);
        assertTrue(src.contains("java.math.BigDecimal maxPrice("), src);
        assertTrue(src.contains("List<java.math.BigDecimal> prices("), src);
        assertTrue(src.contains("String nameOf("), src);
        assertFalse(src.contains(" decimal ") || src.contains("<decimal>") || src.contains("bigdecimal"), src);
    }
}
