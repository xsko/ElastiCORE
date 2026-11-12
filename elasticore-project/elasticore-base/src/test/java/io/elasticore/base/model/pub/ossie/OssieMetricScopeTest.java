package io.elasticore.base.model.pub.ossie;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.elasticore.base.model.pub.rules.verify.RulesGenRunner;
import io.elasticore.base.model.pub.rules.verify.SpecFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each metric in its scope (core spec, "Metric Scoping"). One over one entity
 * is that dataset's measure: in its metrics, on its fields by their own names.
 * Only arithmetic over metrics of two entities spans datasets, and stays at
 * the model root as before.
 */
public class OssieMetricScopeTest {

    /** Customer(customer_id, name) and PurchaseOrder(order_id, total, status stored by its code). */
    private static final String SHOP = String.join(
            "\n",
            "enumeration:",
            "  OrderStatus:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(3) @code",
            "    enum:",
            "      PLACED: PLC",
            "      CANCELLED: CAN",
            "",
            "entity:",
            "  Customer:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      customerId: long @id @sequence",
            "      name: string(50)",
            "",
            "  PurchaseOrder:",
            "    meta: entity @root @expose(20) @terms(order, purchase)",
            "    fields:",
            "      orderId: long @id @sequence",
            "      total: money(12.2)",
            "      status: OrderStatus",
            "");

    private static String shop(String... metrics) {
        return SHOP + "\nmetrics:\n" + String.join("\n", metrics) + "\n";
    }

    @Test
    @DisplayName("a metric over one entity, filtered or not, is its dataset's, on the fields by their own names")
    public void aMetricOverOneEntityIsItsDatasets() throws Exception {
        String[] out = export(
                "shop",
                shop(
                        "  revenue: sum(PurchaseOrder.total) where status != CANCELLED @terms(sales) -- Revenue",
                        "  orderCount: count(PurchaseOrder)",
                        "  customerCount: count(Customer)"));
        Map<String, Object> doc = parse(out[0]);

        Map<String, Object> revenue = named(metricsOf(doc, "PurchaseOrder"), "revenue");
        assertEquals("SUM(CASE WHEN status <> 'CAN' THEN total END)", sql(revenue));
        assertEquals("Revenue", revenue.get("description"));
        assertEquals("Decimal", revenue.get("datatype"));
        assertEquals(Arrays.asList("sales"), ((Map<?, ?>) revenue.get("ai_context")).get("synonyms"));
        assertEquals("shop", extension(revenue).get("domain"));
        assertEquals("COUNT(order_id)", sql(named(metricsOf(doc, "PurchaseOrder"), "orderCount")));
        assertEquals(Arrays.asList("customerCount"), names(metricsOf(doc, "Customer")));
        assertEquals("COUNT(customer_id)", sql(named(metricsOf(doc, "Customer"), "customerCount")));

        // What the dataset says ahead of its metrics is as before.
        Map<String, Object> dataset = named(list(doc, "datasets"), "PurchaseOrder");
        assertEquals(Arrays.asList("order_id"), dataset.get("primary_key"));
        assertEquals(Arrays.asList("order", "purchase"), ((Map<?, ?>) dataset.get("ai_context")).get("synonyms"));
        assertEquals("PurchaseOrder", extension(dataset).get("entity"));

        // None of them is at the root any more, and no root metrics: section is left empty.
        assertNull(doc.get("metrics"), out[0]);
        assertFalse(out[0].contains("\nmetrics:"), out[0]);
        // After its fields, before its own extension.
        assertTrue(out[0].contains("\n    metrics:\n      - name: revenue\n"), out[0]);
        String order = out[0].substring(out[0].indexOf("  - name: PurchaseOrder\n"));
        assertTrue(
                order.indexOf("    fields:") < order.indexOf("    metrics:")
                        && order.indexOf("    metrics:") < order.indexOf("\n    nonportable_extensions:"),
                order);
    }

    @Test
    @DisplayName("arithmetic over one entity's metrics is that dataset's too")
    public void aCompositeOverOneEntityIsItsDatasets() throws Exception {
        Map<String, Object> doc = parse(export(
                "shop",
                shop(
                        "  revenue: sum(PurchaseOrder.total) where status != CANCELLED",
                        "  orderCount: count(PurchaseOrder)",
                        "  averageOrder: revenue / orderCount -- Average order"))[0]);
        Map<String, Object> average = named(metricsOf(doc, "PurchaseOrder"), "averageOrder");
        assertEquals("(SUM(CASE WHEN status <> 'CAN' THEN total END)) / NULLIF((COUNT(order_id)), 0)", sql(average));
        assertEquals("Decimal", average.get("datatype"));
        assertEquals(Arrays.asList("revenue", "orderCount", "averageOrder"), names(metricsOf(doc, "PurchaseOrder")));
    }

    @Test
    @DisplayName("arithmetic over the metrics of two entities stays at the root, on fields named with their dataset")
    public void aCompositeOverTwoEntitiesStaysAtTheRoot() throws Exception {
        String[] out = export(
                "shop",
                shop(
                        "  orderCount: count(PurchaseOrder)",
                        "  customerCount: count(Customer)",
                        "  ordersPerCustomer: orderCount / customerCount -- Orders per customer"));
        Map<String, Object> doc = parse(out[0]);
        List<Map<String, Object>> root = list(doc, "metrics");
        assertEquals(Arrays.asList("ordersPerCustomer"), names(root));
        Map<String, Object> ratio = named(root, "ordersPerCustomer");
        assertEquals(
                "CAST((COUNT(PurchaseOrder.order_id)) AS DECIMAL(38, 10)) / NULLIF((COUNT(Customer.customer_id)), 0)",
                sql(ratio));
        assertEquals("Orders per customer", ratio.get("description"));
        assertEquals("shop", extension(ratio).get("domain"));
        assertEquals(Arrays.asList("orderCount"), names(metricsOf(doc, "PurchaseOrder")));
        assertEquals(Arrays.asList("customerCount"), names(metricsOf(doc, "Customer")));
        // At the root as before: its entry is where and as it always was.
        assertTrue(out[0].contains("\nmetrics:\n  - name: ordersPerCustomer\n    expression:\n      sql: "), out[0]);
    }

    @Test
    @DisplayName("a metric named like a field of its dataset, in any case, is refused and said, naming both")
    public void aMetricNamedLikeAFieldIsRefused() throws Exception {
        String[] out = export(
                "shop",
                shop(
                        "  Total: sum(PurchaseOrder.total)",
                        "  status: count(PurchaseOrder) where status = PLACED",
                        "  name: count(Customer)",
                        "  orderCount: count(PurchaseOrder)"));
        Map<String, Object> doc = parse(out[0]);
        assertEquals(Arrays.asList("orderCount"), names(metricsOf(doc, "PurchaseOrder")));
        assertEquals(new ArrayList<String>(), names(metricsOf(doc, "Customer")));
        assertNull(doc.get("metrics"), out[0]);
        String[] said = {
            "[ossie] [shop] metric Total: PurchaseOrder has a field total,"
                    + " and a dataset's fields and metrics share one set of names - rename the metric; it is left out.",
            "[ossie] [shop] metric status: PurchaseOrder has a field status,",
            "[ossie] [shop] metric name: Customer has a field name,"
        };
        for (String s : said) assertTrue(out[1].contains(s), s + "\n" + out[1]);
    }

    /** report has no PurchaseOrder or Customer of its own: its metrics over them are shop's datasets'. */
    private static final String REPORT = String.join(
            "\n",
            "entity:",
            "  Note:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      noteId: long @id @sequence",
            "",
            "metrics:",
            "  orders: count(PurchaseOrder)",
            "  customers: count(Customer)",
            "  perCustomer: orders / customers",
            "  notes: count(Note)",
            "");

    @Test
    @DisplayName("two domains: no domain before a dataset's metric, a name it already has is refused;"
            + " a root name both use is qualified as before")
    public void twoDomainsShareADatasetsNames() throws Exception {
        String[] out = export(
                "shop",
                shop(
                        "  orders: count(PurchaseOrder)",
                        "  customers: count(Customer)",
                        "  perCustomer: orders / customers"),
                "report",
                REPORT);
        Map<String, Object> doc = parse(out[0]);
        assertEquals(Arrays.asList("orders"), names(metricsOf(doc, "PurchaseOrder")));
        assertEquals(Arrays.asList("customers"), names(metricsOf(doc, "Customer")));
        assertEquals(Arrays.asList("notes"), names(metricsOf(doc, "Note")));
        List<String> root = names(list(doc, "metrics"));
        assertEquals(2, root.size(), root.toString());
        assertTrue(root.contains("shop_perCustomer") && root.contains("report_perCustomer"), root.toString());

        // The domain read second is told which domain has the name.
        Matcher said = Pattern.compile("\\[ossie\\] \\[(\\w+)\\] metric orders: PurchaseOrder already has a metric"
                        + " orders, from domain (\\w+) - rename the metric; it is left out\\.")
                .matcher(out[1]);
        assertTrue(said.find(), out[1]);
        assertNotEquals(said.group(1), said.group(2));
        assertEquals(
                said.group(2),
                extension(named(metricsOf(doc, "PurchaseOrder"), "orders")).get("domain"));
        assertTrue(out[1].contains("metric customers: Customer already has a metric customers, from domain "), out[1]);
    }

    // ---- the document --------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String yaml) throws Exception {
        return new ObjectMapper(new YAMLFactory()).readValue(yaml, Map.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> owner, String key) {
        Object v = owner.get(key);
        return v == null ? new ArrayList<Map<String, Object>>() : (List<Map<String, Object>>) v;
    }

    private static List<Map<String, Object>> metricsOf(Map<String, Object> doc, String dataset) {
        return list(named(list(doc, "datasets"), dataset), "metrics");
    }

    private static Map<String, Object> named(List<Map<String, Object>> entries, String name) {
        for (Map<String, Object> e : entries) if (name.equals(e.get("name"))) return e;
        throw new AssertionError(name + " is not among " + names(entries));
    }

    private static List<String> names(List<Map<String, Object>> entries) {
        List<String> out = new ArrayList<String>();
        for (Map<String, Object> e : entries) out.add((String) e.get("name"));
        return out;
    }

    private static String sql(Map<String, Object> metric) {
        return (String) ((Map<?, ?>) metric.get("expression")).get("sql");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extension(Map<String, Object> entry) throws Exception {
        List<Map<String, Object>> extensions = (List<Map<String, Object>>) entry.get("nonportable_extensions");
        assertEquals(OssieSemanticModelExporter.VENDOR, extensions.get(0).get("vendor_name"));
        return (Map<String, Object>) extensions.get(0).get("data");
    }

    /**
     * One generation of a project of those domains (name, model, name, model, ...): the
     * export, and what the generation said. Each test generates its own: diffMutation runs the
     * tests again on each mutation in one JVM, and a kept export would be the one before it.
     */
    private static String[] export(String... domains) throws Exception {
        File project = Files.createTempDirectory("elcore-ossie-scope").toFile();
        for (int i = 0; i < domains.length; i += 2) {
            File dir = new File(project, "src/main/resources/blueprint/" + domains[i]);
            assertTrue(new File(dir, "model").mkdirs());
            String env = String.join(
                    "\n",
                    "elasticore:",
                    "  enable: true",
                    "  config:",
                    "    domainName: " + domains[i],
                    "    framework: springboot",
                    "    mode: jpa",
                    "    j2ee: jakarta",
                    "    publisher:",
                    "      rules: [hexagonal-springboot, ossie-export]",
                    "  namespace:",
                    "    base: com.acme." + domains[i],
                    "");
            Files.write(new File(dir, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
            Files.write(new File(dir, "model/model.yml").toPath(), domains[i + 1].getBytes(StandardCharsets.UTF_8));
        }

        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.publisher.rules"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "hexagonal");
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty("elasticore.publisher.rules", "ossie-export");
            RulesGenRunner.main(new String[0]);
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
            SpecFactory.valueObjectsInDto(false);
            SpecFactory.rootsSplitByDefault(false);
            SpecFactory.commandOnlyRoots(false);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        File exported = new File(project, "src/main/resources/blueprint/" + OssieSemanticModelExporter.FILE_NAME);
        assertTrue(exported.isFile(), "no semantic model exported:\n" + log);
        String yaml = new String(Files.readAllBytes(exported.toPath()), StandardCharsets.UTF_8);
        delete(project);
        return new String[] {yaml, log};
    }

    private static void delete(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) delete(c);
        assertTrue(f.delete() || !f.exists(), "could not delete " + f);
    }
}
