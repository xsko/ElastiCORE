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
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * domainService:, processManager: and @specification are deprecated in 3.0 and removed in 3.1 (issue 370).
 * A project that uses them is told so once per declaration, with what to write instead - and its build is
 * the same as before: the same files, and elcoreValidate -Pstrict still passes, because a notice is not a
 * DSL warning.
 */
public class DeprecatedDslTest {

    private static final String ENV = String.join(
            "\n",
            "elasticore:",
            "  enable: true",
            "  config:",
            "    domainName: shop",
            "    framework: springboot",
            "    mode: jpa",
            "    j2ee: jakarta",
            "  namespace:",
            "    base: io.acme.shop",
            "");

    private static final String ENTITIES = String.join(
            "\n",
            "entity:",
            "  Customer:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      customerId: long @id @sequence",
            "      name: string(40)",
            "  Product:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      productId: long @id @sequence",
            "      price: bigdecimal(14.2)",
            "  Order:",
            "    meta: entity @root @expose(20) @table(shop_order) @event(Placed) @specification(highValue, totalAmount > 100)"
                    + " @specification(large, totalAmount > 1000)",
            "    fields:",
            "      orderId: long @id @sequence",
            "      totalAmount: bigdecimal(14.2)",
            "");

    private static final String SERVICES = String.join(
            "\n",
            "domainService:",
            "  Pricing:",
            "    uses: [Order, Product]",
            "processManager:",
            "  Loyalty:",
            "    on: [OrderPlaced]",
            "    uses: [Customer]",
            "");

    private static final List<File> projects = new ArrayList<File>();

    @AfterAll
    static void cleanUp() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
        for (File project : projects) delete(project);
    }

    @Test
    public void eachNoticeNamesTheDomainTheDeclarationAndWhatToWriteInstead() {
        String service = DeprecatedDsl.domainService("shop", "Pricing", Arrays.asList("Order", "Product"));
        assertTrue(service.startsWith("[shop] domainService Pricing is deprecated and is removed in 3.1"), service);
        assertTrue(service.contains("still generated as before"), service);
        assertTrue(service.contains("@Service class in the domain.service package"), service);
        assertTrue(service.contains("repository ports of Order, Product in its constructor"), service);

        String pm = DeprecatedDsl.processManager("shop", "Loyalty", Arrays.asList("OrderPlaced"));
        assertTrue(pm.startsWith("[shop] processManager Loyalty is deprecated and is removed in 3.1"), pm);
        assertTrue(pm.contains("@Component"), pm);
        assertTrue(pm.contains("(OrderPlaced)"), pm);
        assertTrue(pm.contains("@TransactionalEventListener(phase = AFTER_COMMIT)"), pm);
        assertTrue(pm.contains("@Transactional(propagation = REQUIRES_NEW)"), pm);
        assertTrue(pm.contains("a plain @EventListener when it must run in the caller's transaction"), pm);

        String spec = DeprecatedDsl.specification("shop", "Order", "highValue, totalAmount > 100");
        assertTrue(spec.startsWith("[shop] Order @specification(highValue) is deprecated and is removed in 3.1"), spec);
        assertTrue(spec.contains("static Predicate<Order> highValue() method of your own"), spec);
        assertTrue(spec.contains("plus the TypeScript function if screens need it"), spec);
    }

    @Test
    public void aNoticeIsNotADslWarning() {
        io.elasticore.base.util.ConsoleLog.clear();
        try {
            DeprecatedDsl.record(
                    Arrays.asList(DeprecatedDsl.domainService("shop", "Pricing", Arrays.asList("Order"))),
                    new java.util.HashSet<String>());
            assertEquals(0, io.elasticore.base.util.ConsoleLog.countStoredLogkey("DSL_WARN"));
            assertEquals(1, io.elasticore.base.util.ConsoleLog.countStoredLogkey(DeprecatedDsl.NOTICE));
        } finally {
            io.elasticore.base.util.ConsoleLog.clear();
        }
    }

    @Test
    public void aProjectThatUsesThemIsToldOnceAndStillPassesStrictValidation() throws Exception {
        File project = project("elcore-deprecated", ENTITIES, SERVICES);
        String[] log = new String[1];
        assertEquals(0, run(project, false, false, log), log[0]);
        String generated = log[0];

        // elcoreValidate -Pstrict: up to date, no DSL warning - the notices are listed and do not fail it.
        String[] validated = new String[1];
        assertEquals(0, run(project, true, true, validated), validated[0]);
        String report = report(project);
        assertTrue(report.contains("**Passed.**"), report);
        assertTrue(report.contains("DSL warnings: 0 (fail with -Pstrict)."), report);
        assertTrue(report.contains("## Deprecated DSL - still generated, removed in 3.1"), report);
        assertTrue(report.contains("- [shop] domainService Pricing is deprecated"), report);
        assertTrue(report.contains("- [shop] processManager Loyalty is deprecated"), report);
        assertTrue(report.contains("- [shop] Order @specification(highValue) is deprecated"), report);
        assertEquals(1, count(validated[0], DeprecatedDsl.HEADER), validated[0]);
        assertTrue(validated[0].contains("[elcore3] validate (nothing was written): PASSED"), validated[0]);

        // Once per declaration, under the notices' own heading, after the run.
        assertEquals(1, count(generated, DeprecatedDsl.HEADER), generated);
        for (String notice : new String[] {
            DeprecatedDsl.domainService("shop", "Pricing", Arrays.asList("Order", "Product")),
            DeprecatedDsl.processManager("shop", "Loyalty", Arrays.asList("OrderPlaced")),
            DeprecatedDsl.specification("shop", "Order", "highValue, totalAmount > 100"),
            DeprecatedDsl.specification("shop", "Order", "large, totalAmount > 1000")
        }) {
            assertEquals(1, count(generated, notice), notice + "\n" + generated);
        }
        // None of them is a DSL warning: the model has none.
        assertFalse(generated.contains("[elcore3] DSL validation warnings:"), generated);

        // Still generated as before.
        assertTrue(read(find(project, "PricingDomainServiceBase.java")).contains("protected ProductRepositoryPort"));
        assertTrue(read(find(project, "LoyaltyProcessManagerBase.java")).contains("@TransactionalEventListener"));
        assertTrue(
                read(find(project, "OrderSpecifications.java")).contains("public static Predicate<Order> highValue()"));
    }

    @Test
    public void aProjectThatDoesNotUseThemIsToldNothing() throws Exception {
        File project = project(
                "elcore-not-deprecated",
                ENTITIES.replace(
                        " @specification(highValue, totalAmount > 100) @specification(large, totalAmount > 1000)", ""),
                null);
        String[] log = new String[1];
        assertEquals(0, run(project, false, false, log), log[0]);
        assertFalse(log[0].contains(DeprecatedDsl.HEADER), log[0]);
        assertFalse(log[0].contains("is deprecated and is removed in 3.1"), log[0]);
        assertEquals(0, run(project, true, true, log), log[0]);
        assertFalse(report(project).contains("Deprecated DSL"), report(project));
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) n++;
        return n;
    }

    private static File project(String name, String entities, String services) throws IOException {
        File project = Files.createTempDirectory(name).toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/shop");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(new File(dir, "env.yml").toPath(), ENV.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/entity.yml").toPath(), entities.getBytes(StandardCharsets.UTF_8));
        if (services != null) {
            Files.write(new File(dir, "model/domainservice.yml").toPath(), services.getBytes(StandardCharsets.UTF_8));
        }
        return project;
    }

    private static int run(File project, boolean validate, boolean strict, String[] log) throws Exception {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v3",
            "elasticore.rules.v2",
            "elasticore.rules.ts",
            "elasticore.validate",
            "elasticore.validate.strict"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        PrintStream out = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int exit;
        try {
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "hexagonal");
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.v3", "true");
            if (validate) System.setProperty("elasticore.validate", "true");
            if (strict) System.setProperty("elasticore.validate.strict", "true");
            exit = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
            io.elasticore.base.model.pub.rules.ModelCheck.reset();
        }
        log[0] = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log[0]);
        return exit;
    }

    private static String report(File project) throws IOException {
        File f = new File(project, "build/elasticore/validate.md");
        assertTrue(f.isFile(), "no report");
        return read(f);
    }

    private static String read(File f) throws IOException {
        assertTrue(f != null && f.isFile(), "not generated: " + f);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static File find(File dir, String name) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isDirectory()) {
                File hit = find(f, name);
                if (hit != null) return hit;
            } else if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
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
