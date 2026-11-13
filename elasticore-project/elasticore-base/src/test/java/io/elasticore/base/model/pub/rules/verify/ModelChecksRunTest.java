package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checks a v3 JPA run makes before it writes an entity's code: an exposed
 * composite key that holds a relation stops the run, a reserved table or
 * column is said, and a type the DSL does not have says what to write.
 */
public class ModelChecksRunTest {

    private static final String LEDGER = String.join(
            "\n",
            "entity:",
            "  Ledger:",
            "    meta: entity @expose(10)",
            "    fields:",
            "      ledgerId: long @id @sequence",
            "      name: string(20)",
            "");

    @Test
    @DisplayName("an exposed composite key that holds a relation stops the run, naming the way out")
    public void anExposedRelationInTheKeyStopsTheRun() throws Exception {
        String[] run = run(
                "layered",
                LEDGER
                        + String.join(
                                "\n",
                                "  LedgerLine:",
                                "    meta: entity @expose(10)",
                                "    fields:",
                                "      order: Ledger @id @ManyToOne",
                                "      itemSeq: int @id",
                                "      amount: int",
                                ""));
        assertEquals("1", run[0], run[1]);
        assertTrue(
                run[1].contains("[elcore3] Probe.LedgerLine: its composite key holds the relation order: Ledger"),
                run[1]);
        assertTrue(
                run[1].contains("key it by the related key's value (ledgerId: long @id), or do not expose it"), run[1]);
    }

    @Test
    @DisplayName("not exposed, or keyed by value, the same entity is generated")
    public void unexposedOrByValueIsGenerated() throws Exception {
        String[] hidden = run(
                "hexagonal",
                LEDGER
                        + String.join(
                                "\n",
                                "  LedgerLine:",
                                "    meta: entity",
                                "    fields:",
                                "      order: Ledger @id @ManyToOne",
                                "      itemSeq: int @id",
                                ""));
        assertEquals("0", hidden[0], hidden[1]);
        String[] byValue = run(
                "hexagonal",
                LEDGER
                        + String.join(
                                "\n",
                                "  LedgerLine:",
                                "    meta: entity @expose(10)",
                                "    fields:",
                                "      ledgerId: long @id",
                                "      itemSeq: int @id",
                                ""));
        assertEquals("0", byValue[0], byValue[1]);
        assertFalse(byValue[1].contains("composite key holds"), byValue[1]);
    }

    @Test
    @DisplayName("a reserved table or column is said once, and the run goes on")
    public void aReservedNameIsSaid() throws Exception {
        String[] run = run(
                "layered",
                String.join(
                        "\n",
                        "entity:",
                        "  Order:",
                        "    meta: entity @expose(10)",
                        "    fields:",
                        "      orderId: long @id @sequence",
                        "      group: string(10)",
                        "      code: uuid",
                        ""));
        assertEquals("0", run[0], run[1]);
        String said = "[Probe] Order: the table order (Oracle, PostgreSQL, SQL Server and H2), the column group"
                + " (Oracle, PostgreSQL, SQL Server and H2) are reserved words there and cannot be created unquoted -"
                + " name it otherwise with @table / @column.";
        assertTrue(run[1].contains(said), run[1]);
        assertEquals(run[1].indexOf(said), run[1].lastIndexOf(said), run[1]);
        assertTrue(
                run[1].contains("Order.code: unresolved type 'uuid' - no matching Entity, DTO, or Enum found in"
                        + " any domain - write string(36), or string @id @genid for a generated key instead"),
                run[1]);
    }

    /** The exit code and what the run printed. */
    private static String[] run(String architecture, String model) throws Exception {
        File project = Files.createTempDirectory("elcore-model-checks").toFile();
        File dir = new File(project, "src/main/resources/blueprint/probe");
        assertTrue(new File(dir, "model").mkdirs());
        String env = String.join(
                "\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: probe",
                "    framework: springboot",
                "    mode: jpa",
                "    j2ee: jakarta",
                "  namespace:",
                "    base: com.acme.probe",
                "");
        Files.write(new File(dir, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/entity.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));

        String[] keys = {
            "elasticore.base.path", "elasticore.architecture", "elasticore.rules.force", "elasticore.rules.v3"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        int status;
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            if ("layered".equals(architecture)) System.clearProperty("elasticore.architecture");
            else System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty("elasticore.rules.v3", "true");
            status = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
            java.lang.reflect.Field tmp =
                    io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
            tmp.setAccessible(true);
            tmp.set(null, null);
            delete(project);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        return new String[] {String.valueOf(status), log};
    }

    private static void delete(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) delete(c);
        assertTrue(f.delete() || !f.exists());
    }
}
