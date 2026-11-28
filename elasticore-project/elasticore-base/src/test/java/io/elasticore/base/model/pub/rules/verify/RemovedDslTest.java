package io.elasticore.base.model.pub.rules.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.elasticore.base.util.HashUtils;
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
 * domainService:, processManager: and @specification are no longer read since 3.1 (issue 370).
 *
 * A project that still declares them is told so once per declaration, with what to write instead, and
 * builds: nothing is generated for them, the load does not fail, and elcoreValidate -Pstrict passes,
 * because a notice is not a DSL warning. What 3.0 generated for them stays while the project's code still
 * uses it, and goes once nothing does; the browser copy of a specification is handed over to the project.
 */
public class RemovedDslTest {

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

    private static final String PLAIN_ENTITIES = ENTITIES.replace(
            " @specification(highValue, totalAmount > 100) @specification(large, totalAmount > 1000)", "");

    private static final String SERVICES = String.join(
            "\n",
            "domainService:",
            "  Pricing:",
            "    uses: [Order, Product]",
            "processManager:",
            "  Loyalty:",
            "    on: [OrderPlaced]",
            "    uses: [Customer]",
            "  Receipting:",
            "    on: [billing:Nowhere]",
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

    // ---- the notice ---------------------------------------------------------------------------------

    @Test
    public void eachNoticeNamesTheDeclarationAndWhatToWriteInstead() {
        String service = RemovedDsl.domainService("shop", "Pricing", Arrays.asList("Order", "Product"));
        assertEquals(
                "[shop] domainService Pricing is no longer read since 3.1 - remove it; write a @Service class in the"
                        + " domain.service package that takes the repository ports of Order, Product in its constructor.",
                service);

        String pm = RemovedDsl.processManager("shop", "Loyalty", Arrays.asList("OrderPlaced"));
        assertEquals(
                "[shop] processManager Loyalty is no longer read since 3.1 - remove it; write a @Component with a"
                        + " method for each event of 'on' (OrderPlaced) annotated"
                        + " @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true) and"
                        + " @Transactional(propagation = REQUIRES_NEW), or a plain @EventListener when it must run in"
                        + " the caller's transaction.",
                pm);

        String spec = RemovedDsl.specification("shop", "Order", "highValue, totalAmount > 100");
        assertEquals(
                "[shop] Order @specification(highValue) is no longer read since 3.1 - remove it; write a static"
                        + " Predicate<Order> highValue() method in a class of your own, plus the TypeScript function if"
                        + " screens need it.",
                spec);

        assertTrue(RemovedDsl.ruleSet("shop", "ddd-specification")
                .startsWith("[shop] the rule set ddd-specification is no longer read since 3.1 - remove it;"));
    }

    @Test
    public void aNoticeIsNotADslWarning() {
        io.elasticore.base.util.ConsoleLog.clear();
        try {
            RemovedDsl.record(
                    Arrays.asList(RemovedDsl.domainService("shop", "Pricing", Arrays.asList("Order"))),
                    new java.util.HashSet<String>());
            assertEquals(0, io.elasticore.base.util.ConsoleLog.countStoredLogkey("DSL_WARN"));
            assertEquals(1, io.elasticore.base.util.ConsoleLog.countStoredLogkey(RemovedDsl.NOTICE));
        } finally {
            io.elasticore.base.util.ConsoleLog.clear();
        }
    }

    @Test
    public void aProjectThatStillDeclaresThemBuildsIsToldOnceAndPassesStrictValidation() throws Exception {
        File project = project("elcore-removed", ENTITIES, SERVICES);
        String[] log = new String[1];
        assertEquals(0, run(project, Run.JAVA, log), log[0]);
        String generated = log[0];

        // Once per declaration, under the notices' own heading, after the run - one message each, and no
        // DSL warning: neither the old checks of the sections (an event nobody declares) nor anything else.
        assertEquals(1, count(generated, RemovedDsl.HEADER), generated);
        for (String notice : new String[] {
            RemovedDsl.domainService("shop", "Pricing", Arrays.asList("Order", "Product")),
            RemovedDsl.processManager("shop", "Loyalty", Arrays.asList("OrderPlaced")),
            RemovedDsl.processManager("shop", "Receipting", Arrays.asList("billing:Nowhere")),
            RemovedDsl.specification("shop", "Order", "highValue, totalAmount > 100"),
            RemovedDsl.specification("shop", "Order", "large, totalAmount > 1000")
        }) {
            assertEquals(1, count(generated, notice), notice + "\n" + generated);
        }
        assertEquals(5, count(generated, RemovedDsl.NO_LONGER_READ), generated);
        assertFalse(generated.contains("[elcore3] DSL validation warnings:"), generated);

        // Nothing is generated for them, and the rest is.
        for (String name : new String[] {
            "PricingDomainServiceBase.java",
            "PricingDomainService.java",
            "LoyaltyProcessManagerBase.java",
            "LoyaltyProcessManager.java",
            "OrderSpecifications.java"
        }) {
            assertNull(find(project, name), name);
        }
        assertNotNull(find(project, "Order.java"), generated);

        // elcoreValidate -Pstrict: up to date, no DSL warning - the notices are listed and do not fail it.
        String[] validated = new String[1];
        assertEquals(0, run(project, Run.STRICT_VALIDATE, validated), validated[0]);
        String report = report(project);
        assertTrue(report.contains("**Passed.**"), report);
        assertTrue(report.contains("DSL warnings: 0 (fail with -Pstrict)."), report);
        assertTrue(report.contains("## DSL no longer read since 3.1 - remove it"), report);
        assertTrue(report.contains("- [shop] domainService Pricing is no longer read since 3.1"), report);
        assertTrue(report.contains("- [shop] processManager Loyalty is no longer read since 3.1"), report);
        assertTrue(report.contains("- [shop] Order @specification(highValue) is no longer read since 3.1"), report);
        assertEquals(1, count(validated[0], RemovedDsl.HEADER), validated[0]);
        assertTrue(validated[0].contains("[elcore3] validate (nothing was written): PASSED"), validated[0]);
    }

    @Test
    public void aSelectionThatStillNamesARemovedRuleSetRunsWithoutIt() throws Exception {
        File project = project("elcore-removed-rules", PLAIN_ENTITIES, null);
        File env = new File(project, "src/main/resources/blueprint/shop/env.yml");
        String withRules = ENV.replace(
                "    j2ee: jakarta\n",
                "    j2ee: jakarta\n    publisher:\n      rules: [hexagonal-springboot, ddd-specification, ddd-domain-service]\n");
        Files.write(env.toPath(), withRules.getBytes(StandardCharsets.UTF_8));
        String[] log = new String[1];
        assertEquals(0, run(project, Run.JAVA, log), log[0]);
        assertEquals(1, count(log[0], RemovedDsl.ruleSet("shop", "ddd-specification")), log[0]);
        assertEquals(1, count(log[0], RemovedDsl.ruleSet("shop", "ddd-domain-service")), log[0]);
        assertNotNull(find(project, "Order.java"), "the rest of the selection ran");
    }

    @Test
    public void aProjectThatDoesNotUseThemIsToldNothing() throws Exception {
        File project = project("elcore-not-removed", PLAIN_ENTITIES, null);
        String[] log = new String[1];
        assertEquals(0, run(project, Run.JAVA, log), log[0]);
        assertFalse(log[0].contains(RemovedDsl.HEADER), log[0]);
        assertFalse(log[0].contains(RemovedDsl.NO_LONGER_READ), log[0]);
        assertEquals(0, run(project, Run.STRICT_VALIDATE, log), log[0]);
        assertFalse(report(project).contains("no longer read"), report(project));
    }

    /**
     * namespace.processManager placed what processManager: generated, and nothing reads it now: it is a
     * notice, which -Pstrict does not count. namespace.domainService stays - the architecture test reads it as
     * a domain package - and is read without a word.
     */
    @Test
    public void namespaceProcessManagerIsSaidAndNamespaceDomainServiceIsNot() throws Exception {
        File project = project("elcore-removed-namespace", PLAIN_ENTITIES, null);
        write(
                new File(project, "src/main/resources/blueprint/shop/env.yml"),
                ENV + "    domainService: io.acme.shop.domain.service\n"
                        + "    processManager: io.acme.shop.application.process\n");
        String[] log = new String[1];
        assertEquals(0, run(project, Run.JAVA, log), log[0]);
        String notice = RemovedDsl.processManagerNamespace("shop", "io.acme.shop.application.process");
        assertEquals(
                "[shop] namespace.processManager (io.acme.shop.application.process) is no longer read since 3.1 -"
                        + " remove it; nothing is generated into it since processManager: was removed; put the"
                        + " listeners you write in the package of your choice.",
                notice);
        assertEquals(1, count(log[0], RemovedDsl.HEADER), log[0]);
        assertEquals(1, count(log[0], notice), log[0]);
        assertEquals(1, count(log[0], RemovedDsl.NO_LONGER_READ), log[0]);
        assertFalse(log[0].contains("namespace.domainService"), log[0]);
        assertFalse(log[0].contains("[elcore3] DSL validation warnings:"), log[0]);

        String[] validated = new String[1];
        assertEquals(0, run(project, Run.STRICT_VALIDATE, validated), validated[0]);
        String report = report(project);
        assertTrue(report.contains("**Passed.**"), report);
        assertTrue(report.contains("- " + notice), report);
        assertFalse(report.contains("namespace.domainService"), report);
    }

    // ---- what 3.0 generated for them, in a project that upgrades -----------------------------------

    private static final String SERVICE_PKG = "io.acme.shop.domain.service";
    private static final String APP_PKG = "io.acme.shop.application";
    private static final String RULES_PKG = "io.acme.shop.domain.model";

    private static String base(String pkg, String cls, String port) {
        return String.join(
                "\n",
                "package " + pkg + ";",
                "",
                "import io.acme.shop.domain.repository." + port + ";",
                "import org.springframework.beans.factory.annotation.Autowired;",
                "",
                "/**",
                " * The wiring, regenerated from the DSL on every build.",
                " */",
                "public abstract class " + cls + "Base {",
                "",
                "    @Autowired",
                "    protected " + port + " " + Character.toLowerCase(port.charAt(0)) + port.substring(1) + ";",
                "}",
                "");
    }

    private static String owned(String pkg, String cls, String annotation, String body) {
        return String.join(
                "\n",
                "// Initially generated by ElastiCORE; no longer managed - do not remove this line.",
                "package " + pkg + ";",
                "",
                "@" + annotation,
                "public class " + cls + " extends " + cls + "Base {",
                body,
                "}",
                "");
    }

    private static String specifications(String pkg, String entity) {
        return String.join(
                "\n",
                "package " + pkg + ";",
                "",
                "import java.util.function.Predicate;",
                "",
                "/**",
                " * " + entity + "Specifications",
                " *",
                " * Generated by ElastiCORE. Do not edit outside the ElastiCORE guidelines.",
                " */",
                "public final class " + entity + "Specifications {",
                "",
                "    private " + entity + "Specifications() {",
                "    }",
                "",
                "    /**",
                "     * large: x != null",
                "     *",
                "     * " + RulesGenRunner.SPECIFICATION_RULE,
                "     * is a predicate and not a boolean.",
                "     */",
                "    public static Predicate<" + entity + "> large() {",
                "        return x -> x != null;",
                "    }",
                "}",
                "");
    }

    /** The developer's code that still uses what 3.0 generated. */
    private static final String CHECKOUT = String.join(
            "\n",
            "package " + APP_PKG + ";",
            "",
            "import " + SERVICE_PKG + ".PricingDomainService;",
            "import " + RULES_PKG + ".OrderSpecifications;",
            "",
            "public class CheckoutService {",
            "    private final PricingDomainService pricing;",
            "",
            "    public CheckoutService(PricingDomainService pricing) {",
            "        this.pricing = pricing;",
            "    }",
            "",
            "    public boolean large(Object order) {",
            "        return OrderSpecifications.large().test(null);",
            "    }",
            "}",
            "");

    /**
     * The default (merging) project: the files 3.0 wrote are in .elasticore/cache, as they were written.
     * The ones the project's code still uses stay, and say so; an edited one is the project's; the rest go.
     */
    @Test
    public void whatTheCodeStillUsesStaysAndTheRestGoesInAMergingProject() throws Exception {
        File project = project("elcore-removed-merge", PLAIN_ENTITIES, null);
        String[] log = new String[1];
        assertEquals(0, run(project, Run.JAVA, log), log[0]);

        File pricingBase = java(project, SERVICE_PKG, "PricingDomainServiceBase");
        File pricing = java(project, SERVICE_PKG, "PricingDomainService");
        File shippingBase = java(project, SERVICE_PKG, "ShippingDomainServiceBase");
        File shipping = java(project, SERVICE_PKG, "ShippingDomainService");
        File loyaltyBase = java(project, APP_PKG, "LoyaltyProcessManagerBase");
        File loyalty = java(project, APP_PKG, "LoyaltyProcessManager");
        File receiptingBase = java(project, APP_PKG, "ReceiptingProcessManagerBase");
        File receipting = java(project, APP_PKG, "ReceiptingProcessManager");
        File orderRules = java(project, RULES_PKG, "OrderSpecifications");
        File productRules = java(project, RULES_PKG, "ProductSpecifications");

        GeneratedCache cache = GeneratedCache.load(project);
        generated(project, cache, pricingBase, base(SERVICE_PKG, "PricingDomainService", "OrderRepositoryPort"));
        generated(project, cache, pricing, owned(SERVICE_PKG, "PricingDomainService", "Service", ""));
        generated(project, cache, shippingBase, base(SERVICE_PKG, "ShippingDomainService", "OrderRepositoryPort"));
        generated(project, cache, shipping, owned(SERVICE_PKG, "ShippingDomainService", "Service", ""));
        generated(project, cache, loyaltyBase, base(APP_PKG, "LoyaltyProcessManager", "CustomerRepositoryPort"));
        generated(project, cache, loyalty, owned(APP_PKG, "LoyaltyProcessManager", "Component", ""));
        generated(project, cache, receiptingBase, base(APP_PKG, "ReceiptingProcessManager", "OrderRepositoryPort"));
        generated(project, cache, receipting, owned(APP_PKG, "ReceiptingProcessManager", "Component", ""));
        generated(project, cache, orderRules, specifications(RULES_PKG, "Order"));
        generated(project, cache, productRules, specifications(RULES_PKG, "Product"));
        cache.save();
        // The developer wrote the step of one process manager, and code that uses the domain service and a rule.
        write(loyalty, owned(APP_PKG, "LoyaltyProcessManager", "Component", "    // the step - mine"));
        write(java(project, APP_PKG, "CheckoutService"), CHECKOUT);

        assertEquals(0, run(project, Run.JAVA, log), log[0]);

        // Used: kept, and said.
        assertTrue(pricing.isFile() && pricingBase.isFile() && orderRules.isFile(), log[0]);
        assertTrue(
                log[0].contains(rel(project, pricing) + " is no longer generated, but "
                        + rel(project, java(project, APP_PKG, "CheckoutService")) + " still uses it - kept"),
                log[0]);
        assertTrue(
                log[0].contains(rel(project, pricingBase) + " is no longer generated, but " + rel(project, pricing)
                        + " still uses it - kept"),
                log[0]);
        assertTrue(log[0].contains(rel(project, orderRules) + " is no longer generated, but"), log[0]);
        // Edited: the developer's now; its base stays while it extends it, so the listener still runs.
        assertTrue(loyalty.isFile() && loyaltyBase.isFile(), log[0]);
        assertTrue(
                log[0].contains(rel(project, loyalty) + " was generated, the DSL no longer produces it, and it has"
                        + " been edited - it is yours now"),
                log[0]);
        assertTrue(
                log[0].contains(rel(project, loyaltyBase) + " is no longer generated, but " + rel(project, loyalty)
                        + " still uses it - kept"),
                log[0]);
        // Unused: removed, as any file the DSL no longer produces.
        for (File gone : new File[] {shipping, shippingBase, receipting, receiptingBase, productRules}) {
            assertFalse(gone.exists(), gone + "\n" + log[0]);
            assertTrue(log[0].contains(rel(project, gone) + "  REMOVED(the DSL no longer produces it)"), log[0]);
        }

        // Once nothing uses them, they go too.
        Files.delete(java(project, APP_PKG, "CheckoutService").toPath());
        assertEquals(0, run(project, Run.JAVA, log), log[0]);
        assertFalse(pricing.exists() || pricingBase.exists() || orderRules.exists(), log[0]);
        assertTrue(loyalty.isFile() && loyaltyBase.isFile(), "the edited one is the developer's: " + log[0]);
    }

    /** A project that turned the merge off (merge = false): stamped files, the stamp's rules. */
    @Test
    public void whatTheCodeStillUsesStaysInAStampedProject() throws Exception {
        File project = project("elcore-removed-stamped", PLAIN_ENTITIES, null);
        String[] log = new String[1];
        assertEquals(0, run(project, Run.JAVA_STAMPED, log), log[0]);
        // The stamped stale files are looked for where the run writes: the entity package.
        File entity = find(project, "Order.java");
        assertNotNull(entity, log[0]);
        String entityPkg = packageOf(read(entity));
        File dir = entity.getParentFile();

        File orderRules = new File(dir, "OrderSpecifications.java");
        File productRules = new File(dir, "ProductSpecifications.java");
        write(orderRules, stamped(specifications(entityPkg, "Order")));
        write(productRules, stamped(specifications(entityPkg, "Product")));
        File pricingBase = java(project, SERVICE_PKG, "PricingDomainServiceBase");
        File pricing = java(project, SERVICE_PKG, "PricingDomainService");
        write(pricingBase, stamped(base(SERVICE_PKG, "PricingDomainService", "OrderRepositoryPort")));
        write(pricing, owned(SERVICE_PKG, "PricingDomainService", "Service", ""));
        write(java(project, APP_PKG, "CheckoutService"), CHECKOUT.replace(RULES_PKG, entityPkg));

        assertEquals(0, run(project, Run.JAVA_STAMPED, log), log[0]);
        assertTrue(orderRules.isFile(), "used by CheckoutService: " + log[0]);
        assertTrue(
                log[0].contains(rel(project, orderRules) + " is no longer generated, but "
                        + rel(project, java(project, APP_PKG, "CheckoutService")) + " still uses it - kept"),
                log[0]);
        assertFalse(productRules.exists(), "unused: " + log[0]);
        assertTrue(log[0].contains(rel(project, productRules) + "  REMOVED(the DSL no longer produces it)"), log[0]);
        // The developer's class (handed over when generated) keeps the base it extends.
        assertTrue(pricing.isFile() && pricingBase.isFile(), log[0]);
    }

    // ---- the browser copy of a specification ------------------------------------------------------

    private static String specificationsTs() {
        return String.join(
                "\n",
                "import type { OrderDTO } from './OrderDTO';",
                "",
                "/**",
                " * Domain rules about OrderDTO.",
                " *",
                " * " + RulesGenRunner.SPECIFICATIONS_TS,
                " * question rather than a similar one.",
                " *",
                " * Generated by ElastiCORE. Do not edit outside the ElastiCORE guidelines.",
                " */",
                "export function highValue(order: OrderDTO | null | undefined): boolean {",
                "    return order != null && (order.totalAmount != null && Number(order.totalAmount) > 100);",
                "}",
                "");
    }

    /**
     * Screens import OrderSpecifications.ts, and the cleanup reads no TypeScript for users: the file is
     * handed over - kept as it is, no longer tracked - rather than deleted. Any other stale file goes.
     */
    @Test
    public void theBrowserCopyIsHandedOverInAMergingProject() throws Exception {
        File project = project("elcore-removed-ts", PLAIN_ENTITIES, null);
        String[] log = new String[1];
        assertEquals(0, run(project, Run.TS, log), log[0]);
        File dto = find(project, "OrderDTO.ts");
        assertNotNull(dto, log[0]);

        File rules = new File(dto.getParentFile(), "OrderSpecifications.ts");
        File gone = new File(dto.getParentFile(), "GoneDTO.ts");
        GeneratedCache cache = GeneratedCache.load(project);
        generated(project, cache, rules, specificationsTs());
        generated(project, cache, gone, "export interface GoneDTO {\n    goneId?: number;\n}\n");
        cache.save();

        assertEquals(0, run(project, Run.TS, log), log[0]);
        assertEquals(specificationsTs(), read(rules).replace("\r\n", "\n"), "kept as it is: " + log[0]);
        assertTrue(
                log[0].contains(rel(project, rules) + " is no longer generated (@specification was removed in"
                        + " 3.1) - it is yours now"),
                log[0]);
        assertNull(GeneratedCache.load(project).get(rel(project, rules)), "no longer tracked");
        assertFalse(gone.exists(), "another stale file goes as before: " + log[0]);

        // From then on it is the project's: nothing is said, nothing is done.
        assertEquals(0, run(project, Run.TS, log), log[0]);
        assertTrue(rules.isFile(), log[0]);
        assertFalse(log[0].contains("OrderSpecifications.ts"), log[0]);
    }

    /**
     * A generated DTO with nothing to import starts with an empty line (KeyDTO, SrchDTO, a DTO of plain
     * fields). When one stops being generated, the stale-file pass read its empty first line as a stamp and
     * the run stopped on a StringIndexOutOfBoundsException - the browser copy of a specification was then
     * never handed over, and the client never updated. The file goes, and the run goes on.
     */
    @Test
    public void aStaleTypeScriptFileThatStartsWithAnEmptyLineDoesNotStopTheRun() throws Exception {
        File project = project("elcore-removed-ts-empty-line", PLAIN_ENTITIES, null);
        String[] log = new String[1];
        assertEquals(0, run(project, Run.TS, log), log[0]);
        File dto = find(project, "OrderDTO.ts");
        assertNotNull(dto, log[0]);

        File rules = new File(dto.getParentFile(), "OrderSpecifications.ts");
        File gone = new File(dto.getParentFile(), "GoneKeyDTO.ts");
        GeneratedCache cache = GeneratedCache.load(project);
        generated(project, cache, rules, specificationsTs());
        generated(project, cache, gone, "\nexport interface GoneKeyDTO {\n    goneId?: number;\n}\n");
        cache.save();

        assertEquals(0, run(project, Run.TS, log), log[0]);
        assertFalse(gone.exists(), "the stale file goes: " + log[0]);
        assertTrue(log[0].contains(rel(project, gone) + "  REMOVED(the DSL no longer produces it)"), log[0]);
        assertEquals(specificationsTs(), read(rules).replace("\r\n", "\n"), "handed over as it is: " + log[0]);
    }

    @Test
    public void theBrowserCopyIsHandedOverInAStampedProject() throws Exception {
        File project = project("elcore-removed-ts-stamped", PLAIN_ENTITIES, null);
        String[] log = new String[1];
        assertEquals(0, run(project, Run.TS_STAMPED, log), log[0]);
        File dto = find(project, "OrderDTO.ts");
        assertNotNull(dto, log[0]);

        File rules = new File(dto.getParentFile(), "OrderSpecifications.ts");
        File gone = new File(dto.getParentFile(), "GoneDTO.ts");
        write(rules, stamped(specificationsTs()));
        write(gone, stamped("export interface GoneDTO {\n    goneId?: number;\n}\n"));

        assertEquals(0, run(project, Run.TS_STAMPED, log), log[0]);
        String kept = read(rules).replace("\r\n", "\n");
        assertTrue(kept.contains(specificationsTs()), "kept as it was written: " + kept);
        assertTrue(kept.contains("// Initially generated by ElastiCORE; no longer managed"), "handed over: " + kept);
        assertTrue(
                log[0].contains(" is no longer generated (@specification was removed in 3.1) - it is yours now"),
                log[0]);
        assertFalse(gone.exists(), "another stale file goes as before: " + log[0]);

        assertEquals(0, run(project, Run.TS_STAMPED, log), log[0]);
        assertTrue(rules.isFile(), log[0]);
        assertFalse(log[0].contains("OrderSpecifications.ts"), log[0]);
    }

    /** Edited by the developer, the browser copy is handed over too: kept with the edit, said once, then silence. */
    @Test
    public void anEditedBrowserCopyIsHandedOverInAStampedProject() throws Exception {
        File project = project("elcore-removed-ts-edited-stamped", PLAIN_ENTITIES, null);
        String[] log = new String[1];
        assertEquals(0, run(project, Run.TS_STAMPED, log), log[0]);
        File dto = find(project, "OrderDTO.ts");
        assertNotNull(dto, log[0]);
        File rules = new File(dto.getParentFile(), "OrderSpecifications.ts");
        write(rules, stamped(specificationsTs()) + "// mine\n");

        assertEquals(0, run(project, Run.TS_STAMPED, log), log[0]);
        String kept = read(rules).replace("\r\n", "\n");
        assertTrue(
                kept.contains("// mine") && kept.contains("// Initially generated by ElastiCORE; no longer managed"),
                kept + "\n" + log[0]);
        assertTrue(
                log[0].contains(" is no longer generated (@specification was removed in 3.1) - it is yours now"),
                log[0]);
        assertFalse(log[0].contains("OrderSpecifications.ts was generated, the DSL no longer produces it"), log[0]);

        assertEquals(0, run(project, Run.TS_STAMPED, log), log[0]);
        assertTrue(rules.isFile(), log[0]);
        assertFalse(log[0].contains("OrderSpecifications.ts"), log[0]);
    }

    @Test
    public void anEditedBrowserCopyIsHandedOverInAMergingProject() throws Exception {
        File project = project("elcore-removed-ts-edited", PLAIN_ENTITIES, null);
        String[] log = new String[1];
        assertEquals(0, run(project, Run.TS, log), log[0]);
        File dto = find(project, "OrderDTO.ts");
        assertNotNull(dto, log[0]);
        File rules = new File(dto.getParentFile(), "OrderSpecifications.ts");
        GeneratedCache cache = GeneratedCache.load(project);
        cache.put(rel(project, rules), specificationsTs());
        cache.save();
        write(rules, specificationsTs() + "// mine\n");

        assertEquals(0, run(project, Run.TS, log), log[0]);
        assertTrue(read(rules).contains("// mine"), log[0]);
        assertTrue(
                log[0].contains(rel(project, rules) + " is no longer generated (@specification was removed in 3.1)"),
                log[0]);
        assertFalse(log[0].contains("OrderSpecifications.ts was generated, the DSL no longer produces it"), log[0]);
        assertNull(GeneratedCache.load(project).get(rel(project, rules)), "no longer tracked");

        assertEquals(0, run(project, Run.TS, log), log[0]);
        assertTrue(rules.isFile(), log[0]);
        assertFalse(log[0].contains("OrderSpecifications.ts"), log[0]);
    }

    /**
     * Review M1: in a stamped project only the classes @specification wrote may stay because something uses
     * them. An entity that happens to be named ProductSpecifications, removed from the DSL with its enum, goes
     * in one run with every file generated for it - its repository and service are going too, so they are no
     * users - as before 3.1.
     */
    @Test
    public void anEntityNamedLikeSpecificationsGoesWithItsFilesInAStampedProject() throws Exception {
        File project = project("elcore-removed-entity-specs", PLAIN_ENTITIES, null);
        File spec = new File(project, "src/main/resources/blueprint/shop/model/spec.yml");
        write(
                spec,
                String.join(
                        "\n",
                        "enumeration:",
                        "  SpecGrade:",
                        "    meta: enum @db(code) @json(code)",
                        "    fields:",
                        "      code: string(1) @code",
                        "      name: string(10)",
                        "    enum:",
                        "      HIGH: H,High",
                        "      LOW: L,Low",
                        "entity:",
                        "  ProductSpecifications:",
                        "    meta: entity @root @expose(20)",
                        "    fields:",
                        "      specId: long @id @sequence",
                        "      grade: SpecGrade",
                        ""));
        String[] log = new String[1];
        assertEquals(0, run(project, Run.JAVA_STAMPED_LAYERED, log), log[0]);
        List<File> generated = new ArrayList<File>();
        collect(new File(project, "src/main"), "ProductSpecifications", generated);
        collect(new File(project, "src/main"), "SpecGrade", generated);
        List<File> stampedOnes = new ArrayList<File>();
        for (File f : generated) {
            // The developer's entity class (handed over when generated) stays, and the base it extends with it,
            // as before 3.1: neither is the question here.
            if (read(f).contains("Initially generated by ElastiCORE")
                    || f.getName().endsWith("Base.java")) continue;
            stampedOnes.add(f);
        }
        assertTrue(stampedOnes.size() >= 3, "the entity, its repository and the enum at least: " + stampedOnes);
        assertTrue(
                stampedOnes.contains(find(project, "ProductSpecifications.java")),
                "the entity class is stamped: " + stampedOnes);

        Files.delete(spec.toPath());
        assertEquals(0, run(project, Run.JAVA_STAMPED_LAYERED, log), log[0]);
        for (File f : stampedOnes) assertFalse(f.exists(), f + "\n" + log[0]);
        assertFalse(log[0].contains("ProductSpecifications.java is no longer generated, but"), log[0]);
        assertFalse(log[0].contains("still uses it - kept"), log[0]);
    }

    private static String specificationsKt(String pkg, String entity) {
        return String.join(
                "\n",
                "package " + pkg,
                "",
                "import java.util.function.Predicate",
                "",
                "/**",
                " * " + entity + "Specifications",
                " *",
                " * Generated by ElastiCORE. Do not edit outside the ElastiCORE guidelines.",
                " */",
                "object " + entity + "Specifications {",
                "",
                "    /**",
                "     * large: x != null",
                "     *",
                "     * " + RulesGenRunner.SPECIFICATION_RULE,
                "     * is a predicate and not a boolean.",
                "     */",
                "    fun large(): Predicate<" + entity + "> = Predicate { x -> x != null }",
                "}",
                "");
    }

    @Test
    public void whatTheKotlinCodeStillUsesStaysInAStampedProject() throws Exception {
        File project = project("elcore-removed-kt-stamped", PLAIN_ENTITIES, null);
        String[] log = new String[1];
        assertEquals(0, run(project, Run.KOTLIN_STAMPED, log), log[0]);
        File entity = find(project, "Order.kt");
        assertNotNull(entity, log[0]);
        String entityPkg = packageOf(read(entity));
        File orderRules = new File(entity.getParentFile(), "OrderSpecifications.kt");
        File productRules = new File(entity.getParentFile(), "ProductSpecifications.kt");
        write(orderRules, stamped(specificationsKt(entityPkg, "Order")));
        write(productRules, stamped(specificationsKt(entityPkg, "Product")));
        File checkout = new File(project, "src/main/kotlin/io/acme/shop/application/Checkout.kt");
        write(
                checkout,
                String.join(
                        "\n",
                        "package io.acme.shop.application",
                        "",
                        "import " + entityPkg + ".OrderSpecifications",
                        "",
                        "class Checkout {",
                        "    fun large() = OrderSpecifications.large()",
                        "}",
                        ""));

        assertEquals(0, run(project, Run.KOTLIN_STAMPED, log), log[0]);
        assertTrue(orderRules.isFile(), "used by Checkout.kt: " + log[0]);
        assertTrue(
                log[0].contains(rel(project, orderRules) + " is no longer generated, but " + rel(project, checkout)
                        + " still uses it - kept"),
                log[0]);
        assertFalse(productRules.exists(), "unused: " + log[0]);
    }

    @Test
    public void aKotlinProjectThatStillDeclaresThemBuildsAndIsTold() throws Exception {
        File project = project("elcore-removed-kt", ENTITIES, SERVICES);
        String[] log = new String[1];
        assertEquals(0, run(project, Run.KOTLIN, log), log[0]);
        assertEquals(5, count(log[0], RemovedDsl.NO_LONGER_READ), log[0]);
        assertFalse(log[0].contains("[elcore3] DSL validation warnings:"), log[0]);
        for (String name :
                new String[] {"PricingDomainServiceBase.kt", "LoyaltyProcessManagerBase.kt", "OrderSpecifications.kt"
                }) {
            assertNull(find(project, name), name);
        }
        assertNotNull(find(project, "Order.kt"), log[0]);
    }

    @Test
    public void aMyBatisProjectThatStillDeclaresThemBuildsAndIsTold() throws Exception {
        File project = project("elcore-removed-mybatis", ENTITIES, SERVICES);
        File env = new File(project, "src/main/resources/blueprint/shop/env.yml");
        write(env, ENV.replace("    mode: jpa\n", "    mode: mybatis\n"));
        String[] log = new String[1];
        assertEquals(0, run(project, Run.JAVA, log), log[0]);
        assertEquals(5, count(log[0], RemovedDsl.NO_LONGER_READ), log[0]);
        assertFalse(log[0].contains("[elcore3] DSL validation warnings:"), log[0]);
        for (String name : new String[] {
            "PricingDomainServiceBase.java", "LoyaltyProcessManagerBase.java", "OrderSpecifications.java"
        }) {
            assertNull(find(project, name), name);
        }
        assertNotNull(find(project, "Order.java"), log[0]);
    }

    /** Review L5: whatever shape a leftover section has, the load does not fail; each is said. */
    @Test
    public void aMalformedLeftoverSectionDoesNotFailTheLoad() throws Exception {
        File project = project("elcore-removed-malformed", PLAIN_ENTITIES, null);
        File model = new File(project, "src/main/resources/blueprint/shop/model");
        write(new File(model, "a.yml"), "domainService: [Pricing, Shipping]\n");
        write(new File(model, "b.yml"), "processManager: just a sentence\n");
        write(new File(model, "c.yml"), "domainService:\n");
        write(new File(model, "d.yml"), "processManager: {}\n");
        write(new File(model, "e.yml"), "domainService:\n  Pricing: a scalar, not a map\n");
        String[] log = new String[1];
        assertEquals(0, run(project, Run.JAVA, log), log[0]);
        assertTrue(log[0].contains(RemovedDsl.domainService("shop", null, null)), log[0]);
        assertTrue(log[0].contains(RemovedDsl.processManager("shop", null, null)), log[0]);
        assertTrue(log[0].contains(RemovedDsl.domainService("shop", "Pricing", null)), log[0]);
        assertFalse(log[0].contains("[elcore3] DSL validation warnings:"), log[0]);
        assertNotNull(find(project, "Order.java"), log[0]);
    }

    /** Review L3: an empty section is a leftover too. */
    @Test
    public void anEmptySectionIsSaid() throws Exception {
        File project = project("elcore-removed-empty", PLAIN_ENTITIES, "domainService: {}\n");
        String[] log = new String[1];
        assertEquals(0, run(project, Run.JAVA, log), log[0]);
        assertEquals(1, count(log[0], RemovedDsl.domainService("shop", null, null)), log[0]);
    }

    /**
     * Review L4: a client-only run (elcore3ts alone) says it too; one the build runs right after the
     * server's (tsWithBuild), which said it already, does not.
     */
    @Test
    public void aTypeScriptRunSaysItUnlessTheServersRunDid() throws Exception {
        File project = project("elcore-removed-ts-notice", ENTITIES, SERVICES);
        String[] log = new String[1];
        assertEquals(0, run(project, Run.TS, log), log[0]);
        assertEquals(1, count(log[0], RemovedDsl.HEADER), log[0]);
        assertEquals(
                1, count(log[0], RemovedDsl.specification("shop", "Order", "highValue, totalAmount > 100")), log[0]);
        assertEquals(
                1,
                count(log[0], RemovedDsl.domainService("shop", "Pricing", Arrays.asList("Order", "Product"))),
                log[0]);

        assertEquals(0, run(project, Run.TS_WITH_BUILD, log), log[0]);
        assertFalse(log[0].contains(RemovedDsl.HEADER), log[0]);
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private enum Run {
        JAVA,
        JAVA_STAMPED,
        JAVA_STAMPED_LAYERED,
        KOTLIN,
        KOTLIN_STAMPED,
        STRICT_VALIDATE,
        TS,
        TS_STAMPED,
        TS_WITH_BUILD
    }

    private static boolean ts(Run how) {
        return how == Run.TS || how == Run.TS_STAMPED || how == Run.TS_WITH_BUILD;
    }

    private static File java(File project, String pkg, String cls) {
        return new File(project, "src/main/java/" + pkg.replace('.', '/') + "/" + cls + ".java");
    }

    /** A file as 3.0 wrote it: on disk and, as the merge's base, in the cache. */
    private static void generated(File project, GeneratedCache cache, File f, String text) throws IOException {
        write(f, text);
        cache.put(rel(project, f), text);
    }

    private static String stamped(String text) {
        return HashUtils.makeEcdCode(text) + text;
    }

    private static void write(File f, String text) throws IOException {
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static String rel(File project, File f) {
        String p = f.getAbsolutePath().replace('\\', '/');
        String root = project.getAbsolutePath().replace('\\', '/');
        return p.startsWith(root + "/") ? p.substring(root.length() + 1) : p;
    }

    private static String packageOf(String text) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)").matcher(text);
        assertTrue(m.find(), text);
        return m.group(1);
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

    private static int run(File project, Run how, String[] log) throws Exception {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v3",
            "elasticore.rules.v2",
            "elasticore.rules.ts",
            "elasticore.merge",
            "elasticore.rules.tsWithBuild",
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
            System.setProperty("elasticore.architecture", how == Run.JAVA_STAMPED_LAYERED ? "layered" : "hexagonal");
            if (ts(how)) {
                System.setProperty("elasticore.rules.ts", "true");
            } else {
                boolean kotlin = how == Run.KOTLIN || how == Run.KOTLIN_STAMPED;
                System.setProperty("elasticore.language", kotlin ? "kotlin" : "java");
                System.setProperty("elasticore.rules.v3", "true");
            }
            if (how == Run.JAVA_STAMPED
                    || how == Run.JAVA_STAMPED_LAYERED
                    || how == Run.KOTLIN_STAMPED
                    || how == Run.TS_STAMPED) {
                System.setProperty("elasticore.merge", "false");
            }
            if (how == Run.TS_WITH_BUILD) System.setProperty("elasticore.rules.tsWithBuild", "true");
            if (how == Run.STRICT_VALIDATE) {
                System.setProperty("elasticore.validate", "true");
                System.setProperty("elasticore.validate.strict", "true");
            }
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

    /** The .java and .kt files under dir whose name starts with prefix. */
    private static void collect(File dir, String prefix, List<File> hits) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, prefix, hits);
            else if (f.getName().startsWith(prefix)
                    && (f.getName().endsWith(".java") || f.getName().endsWith(".kt"))) {
                hits.add(f);
            }
        }
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
