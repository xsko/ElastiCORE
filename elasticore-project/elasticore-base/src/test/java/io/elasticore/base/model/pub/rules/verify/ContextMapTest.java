package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bounded contexts as the domains declare them: what one may take from
 * another, and what stays inside.
 */
public class ContextMapTest {

    private File root;

    @AfterEach
    void clean() throws Exception {
        // The loaded domains stay process-wide; later tests expect none.
        java.lang.reflect.Field tmp = io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        if (root == null || !root.exists()) return;
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

    private void domain(String name, String context, String model) throws IOException {
        File dir = new File(root, name + "/model");
        assertTrue(dir.mkdirs());
        String env = String.join("\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: " + name,
                "    framework: springboot",
                "    mode: jpa",
                "    j2ee: jakarta",
                context,
                "  namespace:",
                "    base: io.acme." + name,
                "");
        Files.write(new File(root, name + "/env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
    }

    private static final String CATALOG = String.join("\n",
            "enumeration:",
            "  Unit:",
            "    meta: enum",
            "    enum:",
            "      PIECE: PC",
            "  Margin:",
            "    meta: enum",
            "    enum:",
            "      LOW: L",
            "entity:",
            "  Product:",
            "    meta: entity @root",
            "    fields:",
            "      productId: long @id",
            "      name:      string(30)",
            "dto:",
            "  ProductDTO:",
            "    meta: dto",
            "    fields:",
            "      productId: long",
            "      name:      string(30)",
            "");

    private static final String SHOP = String.join("\n",
            "entity:",
            "  Order:",
            "    meta: entity @root",
            "    fields:",
            "      orderId:   long @id",
            "      productId: long",
            "      product:   Product @ManyToOne",
            "      unit:      Unit",
            "      margin:    Margin",
            "dto:",
            "  OrderView:",
            "    meta: dto",
            "    fields:",
            "      product: ProductDTO",
            "");

    @Test
    public void aDeclaredContextTakesOnlyWhatItUsesAndTheOtherPublishes() throws Exception {
        root = Files.createTempDirectory("elcore-contexts").toFile();
        domain("catalog", String.join("\n",
                "    context:",
                "      publishes: [ProductDTO, Unit, Product, Nope]"), CATALOG);
        domain("shop", String.join("\n",
                "    context:",
                "      uses: catalog"), SHOP);
        // Declared, and uses catalog's DTO without saying so.
        domain("report", String.join("\n",
                "    context:",
                "      publishes: []"), String.join("\n",
                "dto:",
                "  SalesLine:",
                "    meta: dto",
                "    fields:",
                "      product: ProductDTO",
                ""));

        ContextMap map = ContextMap.of(BlueprintLoader.load(root.getAbsolutePath()));
        List<String> problems = map.problems();

        assertTrue(problems.contains("[shop] Order.product is catalog's entity Product: another context's entity is no field type"
                + " - hold its id (productId: long), or use a DTO catalog publishes."), problems.toString());
        assertTrue(problems.contains("[shop] Order.margin uses Margin from catalog, which catalog does not publish (config.context.publishes)."),
                problems.toString());
        assertTrue(problems.contains("[report] SalesLine.product uses ProductDTO from catalog, but report does not declare it (config.context.uses: catalog)."),
                problems.toString());
        // What catalog publishes, shop may use.
        for (String p : problems) {
            assertFalse(p.contains("Order.unit") || p.contains("OrderView.product"), p);
        }
        assertEquals(3, problems.size(), problems.toString());
        // Publishing an entity does not let it be a field elsewhere; a name catalog lacks is said.
        assertTrue(map.warnings().contains("[catalog] context publishes the entity Product: other contexts may not hold it as a field;"
                + " publish a DTO of it instead."), map.warnings().toString());
        assertTrue(map.warnings().contains("[catalog] context publishes Nope, which is no model or event of catalog."), map.warnings().toString());

        String md = map.markdown();
        assertTrue(md.contains("ctx_catalog -->|Product, Unit, Margin, ProductDTO| ctx_shop"), md);
        assertTrue(md.contains("ctx_catalog -->|ProductDTO| ctx_report"), md);
        assertTrue(md.contains("| catalog |  | ProductDTO, Unit, Product, Nope |"), md);
    }

    /**
     * A context that says only what it uses lets its own types be used (it
     * declared no publishes); a parent class taken from another context needs
     * that context in uses like any other type.
     */
    @Test
    public void publishesIsCheckedOnlyWhereDeclaredAndExtendCounts() throws Exception {
        root = Files.createTempDirectory("elcore-contexts").toFile();
        domain("catalog", String.join("\n",
                "    context:",
                "      uses: []"), CATALOG.replace("entity:\n", String.join("\n",
                "entity:",
                "  Audited:",
                "    meta: entity @abstract",
                "    fields:",
                "      createdBy: string(20)",
                "")));
        domain("shop", String.join("\n",
                "    context:",
                "      uses: [catalog]"), String.join("\n",
                "entity:",
                "  Basket:",
                "    meta: entity @root @extend(Audited)",
                "    fields:",
                "      basketId: long @id",
                "      unit:     Unit",
                ""));
        domain("billing", String.join("\n",
                "    context:",
                "      uses: []"), String.join("\n",
                "entity:",
                "  Invoice:",
                "    meta: entity @root @extend(Audited)",
                "    fields:",
                "      invoiceId: long @id",
                ""));

        List<String> problems = ContextMap.of(BlueprintLoader.load(root.getAbsolutePath())).problems();
        assertEquals(java.util.Collections.singletonList(
                "[billing] Invoice @extend uses Audited from catalog, but billing does not declare it (config.context.uses: catalog)."),
                problems);
    }

    private static final String ORDERS = String.join("\n",
            "entity:",
            "  Order:",
            "    meta: entity @root @event(Placed(total), Cancelled)",
            "    fields:",
            "      orderId: long @id",
            "      total:   long",
            "");

    /**
     * A context may publish the events its roots raise, by the event's name
     * or its class name; a member's or a keyless entity's @event raises
     * nothing, so publishing it is said.
     */
    @Test
    public void aContextPublishesTheEventsItsRootsRaise() throws Exception {
        root = Files.createTempDirectory("elcore-contexts").toFile();
        domain(
                "shop",
                String.join(
                        "\n", "    context:", "      publishes: [OrderPlaced, OrderCancelledEvent, OrderLineAdded]"),
                String.join(
                        "\n",
                        "entity:",
                        "  Order:",
                        "    meta: entity @root @event(Placed, Cancelled)",
                        "    fields:",
                        "      orderId: long @id",
                        "      lines:   List<OrderLine> @OneToMany(mappedBy=\"order\")",
                        "  OrderLine:",
                        "    meta: entity @event(Added)",
                        "    fields:",
                        "      lineId: long @id",
                        "      order:  Order @ManyToOne",
                        ""));

        ContextMap map = ContextMap.of(BlueprintLoader.load(root.getAbsolutePath()));
        assertEquals(
                java.util.Arrays.asList("[shop] context publishes OrderLineAdded, which is no model or event of shop."),
                map.warnings());
        assertTrue(map.problems().isEmpty(), map.problems().toString());
        assertEquals("shop", map.contextNamed("shop"));
        assertEquals(null, map.contextNamed("nowhere"));
    }

    /**
     * A processManager: section is no longer read (3.1, issue 370): an event
     * it names from another context crosses nothing - no reference, no
     * boundary problem, nothing drawn - so a project that still has one
     * builds as before.
     */
    @Test
    public void aLeftoverProcessManagerCrossesNothing() throws Exception {
        root = Files.createTempDirectory("elcore-contexts").toFile();
        domain("shop", String.join("\n", "    context:", "      publishes: [OrderPlaced]"), ORDERS);
        domain(
                "billing",
                String.join("\n", "    context:", "      uses: []"),
                String.join(
                        "\n",
                        "processManager:",
                        "  Receipting:",
                        "    on: [shop:OrderPlaced, shop:OrderCancelled]",
                        ""));

        ContextMap map = ContextMap.of(BlueprintLoader.load(root.getAbsolutePath()));
        assertTrue(map.problems().isEmpty(), map.problems().toString());
        assertTrue(map.warnings().isEmpty(), map.warnings().toString());
        assertTrue(map.references().isEmpty(), map.references().toString());
        assertFalse(map.markdown().contains("ctx_shop -->"), map.markdown());
    }
    /** Nobody declares a context: names resolve anywhere, as they always have. */
    @Test
    public void undeclaredDomainsAreLeftAsTheyWere() throws Exception {
        root = Files.createTempDirectory("elcore-contexts").toFile();
        domain("catalog", "", CATALOG);
        domain("shop", "", SHOP);

        ContextMap map = ContextMap.of(BlueprintLoader.load(root.getAbsolutePath()));
        assertFalse(map.declared());
        assertTrue(map.problems().isEmpty(), map.problems().toString());
        assertFalse(map.references().isEmpty(), "the crossing is still seen");
    }
}
