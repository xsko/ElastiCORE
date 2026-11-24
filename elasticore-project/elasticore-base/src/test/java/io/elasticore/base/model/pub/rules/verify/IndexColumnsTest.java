package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.pub.rules.schema.DdlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue 323: an @Index naming a column its table does not have passed generation and compilation,
 * and the table was then missing at run time. It is said, with the columns there are, whether or not
 * the project writes a DDL; a MyBatis domain, whose indexes are only in its DDL, when it writes one.
 * Issue 320: a lifecycle whose commands are not written - no behaviour class - is said too.
 */
public class IndexColumnsTest {

    /** The reported model: product_code is the reference's key, and layered names its column product_product_code. */
    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Product:",
            "    meta: entity @root @editable @expose(20)",
            "    fields:",
            "      productCode: string(20) @id",
            "  Coverage:",
            "    meta: entity @root @editable @expose(50)"
                    + " @Index(name=\"uk_coverage_product_code\", columnList=\"product_code, coverage_code\", unique=true)",
            "    fields:",
            "      coverageId:   long @id @sequence",
            "      product:      Product @ManyToOne",
            "      coverageCode: string(30)!",
            "");

    /** Indexes on columns the table has: the reference's own column and a value's, by its field's name too. */
    private static final String RIGHT = String.join(
            "\n",
            "entity:",
            "  Product:",
            "    meta: entity @root @editable @expose(20)",
            "    fields:",
            "      productCode: string(20) @id",
            "  Coverage:",
            "    meta: entity @root @editable @expose(50)"
                    + " @Index(name=\"uk_coverage_product_code\", columnList=\"product_product_code, coverage_code\", unique=true)"
                    + " @Index(name=\"ix_coverage_code\", columnList=\"coverageCode\")",
            "    fields:",
            "      coverageId:   long @id @sequence",
            "      product:      Product @ManyToOne",
            "      coverageCode: string(30)!",
            "");

    @Test
    @DisplayName("layered JPA, no DDL written: the missing column is said, with the reference's column")
    public void aColumnTheTableDoesNotHaveIsSaid() throws Exception {
        String log = generate(project("jpa", MODEL), null);
        assertTrue(
                log.contains("[product] Coverage @Index(uk_coverage_product_code): coverage has no column product_code"
                        + " (perhaps product_product_code) - creating the table fails on it; name the columns as the"
                        + " table has them: coverage_id, product_product_code, coverage_code"),
                log);
        // coverage_code is there: only the one column is said.
        assertFalse(log.contains("no column coverage_code"), log);
    }

    @Test
    @DisplayName("columns the table has, by column or by field name: nothing is said")
    public void columnsTheTableHasAreNotSaid() throws Exception {
        String log = generate(project("jpa", RIGHT), null);
        assertFalse(log.contains("@Index("), log);
    }

    /** The columns are the ones each architecture and language generates: hexagonal Java and layered Kotlin. */
    @Test
    @DisplayName("hexagonal and Kotlin: their own sources are read - a wrong column said, a right one not")
    public void eachArchitectureAndLanguageIsReadAsGenerated() throws Exception {
        String model = MODEL.replace("product_code, coverage_code", "no_such_column, coverage_code");
        String[][] runs = {{"elasticore.architecture=hexagonal"}, {"elasticore.language=kotlin"}};
        for (String[] run : runs) {
            String log = generate(project("jpa", model), null, run);
            assertTrue(
                    log.contains("Coverage @Index(uk_coverage_product_code): coverage has no column no_such_column"),
                    run[0] + "\n" + log);
            assertFalse(log.contains("no column coverage_code"), run[0] + "\n" + log);
        }
    }

    @Test
    @DisplayName("with a DDL written, the index is said once")
    public void withADdlTheIndexIsSaidOnce() throws Exception {
        String log = generate(project("jpa", MODEL), "h2");
        String said = "Coverage @Index(uk_coverage_product_code): coverage has no column product_code";
        assertEquals(log.indexOf(said), log.lastIndexOf(said), log);
        assertTrue(log.contains(said), log);
    }

    @Test
    @DisplayName("MyBatis: the index is in the DDL only - said when the domain writes one")
    public void myBatisIsCheckedWhenItWritesADdl() throws Exception {
        String model = MODEL.replace("product_code, coverage_code", "no_such_column, coverage_code");
        String log = generate(project("mybatis", model), "h2");
        assertTrue(
                log.contains("Coverage @Index(uk_coverage_product_code): coverage has no column no_such_column"), log);
        String without = generate(project("mybatis", model), null);
        assertFalse(without.contains("@Index("), without);
    }

    /** Issue 320: layered with ddd-state added sets the initial state only, and says what is not written. */
    @Test
    @DisplayName("a lifecycle on a root without a behaviour class says its commands are not written")
    public void aLifecycleWithoutItsCommandsIsSaid() throws Exception {
        String model = String.join(
                "\n",
                "enumeration:",
                "  DeviceStatus:",
                "    meta: enum @db(code) @json(code)",
                "    fields:",
                "      code: string(10) @code",
                "      label: string(50)",
                "    enum:",
                "      REQUESTED: REQUESTED,Requested",
                "      APPROVED: APPROVED,Approved",
                "      REJECTED: REJECTED,Rejected",
                "      REVOKED: REVOKED,Revoked",
                "entity:",
                "  Device:",
                "    meta: entity @root @editable @expose(20)",
                "    fields:",
                "      deviceId:   long @id @sequence",
                "      status:     DeviceStatus",
                "      approvedAt: timestamp @stamp(APPROVED)",
                "    states:",
                "      status:",
                "        initial: REQUESTED",
                "        approve: REQUESTED -> APPROVED",
                "        reject:  REQUESTED -> REJECTED",
                "        revoke:  APPROVED -> REVOKED",
                "        delete:  REJECTED, REVOKED",
                "");
        File project = project("jpa", model);
        String log = generate(project, null, "elasticore.publisher.rules.add=ddd-state");
        assertTrue(
                log.contains(
                        "[Product] Device states.status: the commands approve, reject, revoke are written only for a"
                                + " root with a behaviour class (a root that is not @editable, with architecture = 'ddd' or"
                                + " 'hexagonal'); Device gets its initial state only."),
                log);
        assertTrue(
                log.contains("Device.approvedAt @stamp: written only for a root with a behaviour class (a root that is"
                        + " not @editable, with architecture = 'ddd' or 'hexagonal'); the field is not stamped"),
                log);
        assertTrue(
                log.contains("Device states.status.delete: written only for a root with a behaviour class (a root"
                        + " that is not @editable"),
                log);
        // Without the rule set, the warning says what adding it gives.
        String plain = generate(project("jpa", model), null);
        assertTrue(
                plain.contains(
                        "The jpa-springboot-v3 bundle does not carry ddd-state, and adding it to this bundle sets"
                                + " the initial state only: the commands, the moves they check, @stamp and the delete: guard are"
                                + " written for a root with a behaviour class - a root that is not @editable, with architecture ="
                                + " 'ddd' or 'hexagonal'."),
                plain);
        assertFalse(plain.contains("or add the rule set"), plain);
    }

    // ---- the harness ----------------------------------------------------------------

    private final List<File> projects = new ArrayList<File>();

    private File project(String mode, String model) throws IOException {
        File project = Files.createTempDirectory("elcore-index").toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/product");
        assertTrue(new File(dir, "model").mkdirs());
        List<String> env = new ArrayList<String>(Arrays.asList(
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: product",
                "    framework: springboot",
                "    mode: " + mode,
                "    j2ee: jakarta",
                "  namespace:",
                "    entity: com.acme.product.entity",
                "    dto: com.acme.product.dto",
                "    repository: com.acme.product.repository",
                "    service: com.acme.product.service",
                "    control: com.acme.product.controller",
                ""));
        Files.write(new File(dir, "env.yml").toPath(), String.join("\n", env).getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    /** One layered Java generation, a DDL for the databases given (or none); its log. */
    private static String generate(File project, String databases, String... switches) throws Exception {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.publisher.rules.add",
            "elasticore.ddl.now",
            DdlConfig.DATABASES
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        int code;
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty("elasticore.ddl.now", "20260101000000");
            if (databases != null) System.setProperty(DdlConfig.DATABASES, databases);
            for (String s : switches) {
                int eq = s.indexOf('=');
                System.setProperty(s.substring(0, eq), s.substring(eq + 1));
            }
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            code = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        assertEquals(0, code, log);
        return log;
    }

    @AfterEach
    void clean() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        io.elasticore.base.model.pub.rules.ModelCheck.reset();
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
        for (File project : projects) delete(project);
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
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
