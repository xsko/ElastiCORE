package io.elasticore.base.model.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * blueprint/env.yml (issue 248): what a domain takes from the root file, row
 * by row of the spec, and that a domain loaded through FileBasedModelLoader
 * with the root file has the settings it has when its own env.yml says them.
 */
public class RootEnvTest {

    @TempDir
    Path tmp;

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(String... lines) throws Exception {
        Map<String, Object> whole = YAML.readValue(String.join("\n", lines) + "\n", LinkedHashMap.class);
        return (Map<String, Object>) whole.get("elasticore");
    }

    private static final String[] ROOT = {
        "elasticore:",
        "  config:",
        "    mode: jpa",
        "    j2ee: jakarta",
        "    apiPath: /api/v9",
        "    interface:",
        "      pageable: com.acme.Paging",
        "      sortable: com.acme.Sorting",
        "    annotations:",
        "      genid: \"genid(com.acme.Ids.next)\"",
        "      maskname: \"@dto:com.acme.RootMask\"",
        "    publisher:",
        "      suppress: [spring-api-errors, jpa-comment]",
        "  namespace:",
        "    base: com.acme",
        "    entity: \"{base}.domain.{domain}.entity\"",
        "    dto: \"{base}.domain.{domain}.dto\"",
        "    enumeration: \"{base}.domain.{domain}.enums\"",
        "    port: \"{base}.domain.{domain}.port\"",
        "    shared: com.acme.shared",
    };

    private Map<String, Object> root() throws Exception {
        File f = write("blueprint/env.yml", ROOT);
        return RootEnv.read(f).settings();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    // ---- namespaces, row by row of the spec ------------------------------------------------------

    @Test
    public void aNamespaceKeyNotWrittenIsNotGenerated() throws Exception {
        RootEnv.Merged m = RootEnv.apply(root(), section("elasticore:", "  namespace:", "    dto:"), "shop");
        Map<String, Object> ns = map(m.env().get("namespace"));
        assertEquals(1, ns.size(), ns.toString());
        assertFalse(ns.containsKey("entity"), "a pattern alone switches nothing on");
        assertFalse(ns.containsKey("port"));
    }

    @Test
    public void aKeyWithNoValueTakesTheRootsPattern() throws Exception {
        RootEnv.Merged m = RootEnv.apply(
                root(),
                section("elasticore:", "  namespace:", "    dto:", "    enumeration:", "    shared:"),
                "catalog");
        Map<String, Object> ns = map(m.env().get("namespace"));
        assertEquals("com.acme.domain.catalog.dto", ns.get("dto"));
        assertEquals("com.acme.domain.catalog.enums", ns.get("enumeration"));
        // A root value without {domain}: one package every domain shares.
        assertEquals("com.acme.shared", ns.get("shared"));
        assertTrue(m.inherited().contains("namespace.dto"), m.inherited().toString());
        assertTrue(m.warnings().isEmpty(), m.warnings().toString());
    }

    @Test
    public void aKeyWithAValueKeepsIt() throws Exception {
        // K-CHANHO's case: a domain whose enumerations sit in ...enumeration keeps them there by saying so.
        RootEnv.Merged m = RootEnv.apply(
                root(),
                section("elasticore:", "  namespace:", "    enumeration: com.acme.domain.shop.enumeration"),
                "shop");
        assertEquals(
                "com.acme.domain.shop.enumeration",
                map(m.env().get("namespace")).get("enumeration"));
        assertFalse(m.inherited().contains("namespace.enumeration"));
    }

    @Test
    public void aKeyWithNoValueAndNoPatternIsSaidAndNotGenerated() throws Exception {
        RootEnv.Merged m = RootEnv.apply(root(), section("elasticore:", "  namespace:", "    service:"), "shop");
        Map<String, Object> ns = map(m.env().get("namespace"));
        assertTrue(ns.containsKey("service"));
        assertNull(ns.get("service"), "as today: no value, nothing generated");
        assertEquals(1, m.warnings().size(), m.warnings().toString());
        assertTrue(
                m.warnings().get(0).contains("namespace.service has no value"),
                m.warnings().get(0));
    }

    @Test
    public void aDomainWithoutANamespaceSectionTakesNone() throws Exception {
        RootEnv.Merged m = RootEnv.apply(root(), section("elasticore:", "  config:", "    domainName: shop"), "shop");
        assertFalse(m.env().containsKey("namespace"), m.env().toString());
        // A section written with nothing in it is no section either.
        RootEnv.Merged empty = RootEnv.apply(root(), section("elasticore:", "  namespace:"), "shop");
        assertNull(empty.env().get("namespace"));
    }

    @Test
    public void theOneLineBaseOfADomainKeepsItsMeaning() throws Exception {
        RootEnv.Merged m =
                RootEnv.apply(root(), section("elasticore:", "  namespace:", "    base: com.other.shop"), "shop");
        Map<String, Object> ns = map(m.env().get("namespace"));
        assertEquals("com.other.shop", ns.get("base"));
        assertEquals(1, ns.size(), "the root's patterns are not added beside it");
        // And it still switches on its thirteen keys.
        Map<String, String> layout = FileBasedModelLoader.withBaseLayout(stringMap(ns));
        assertEquals("com.other.shop.domain.model", layout.get("entity"));
        assertEquals("com.other.shop.adapter.in.web", layout.get("control"));
    }

    @Test
    public void aBaseWrittenWithNoValueTakesTheRootsBase() throws Exception {
        RootEnv.Merged m = RootEnv.apply(root(), section("elasticore:", "  namespace:", "    base:"), "shop");
        assertEquals("com.acme", map(m.env().get("namespace")).get("base"));
        assertTrue(m.inherited().contains("namespace.base"));
    }

    @Test
    public void aDomainWithoutADomainNameIsNamedByItsFolder() throws Exception {
        write("named/blueprint/env.yml", ROOT);
        write(
                "named/blueprint/orders/env.yml",
                "elasticore:",
                "  config:",
                "    table:",
                "      prefix: o_",
                "  namespace:",
                "    dto:");
        assertEquals(
                "com.acme.domain.orders.dto",
                contextOf("named/blueprint/orders").getNamespace("dto"));
    }

    @Test
    public void theLoaderSaysAKeyWithoutAPatternAndGoesOn() throws Exception {
        write("warn/blueprint/env.yml", ROOT);
        write(
                "warn/blueprint/shop/env.yml",
                "elasticore:",
                "  config:",
                "    domainName: shop",
                "  namespace:",
                "    dto:",
                "    service:");
        ModelLoaderContext[] ctx = new ModelLoaderContext[1];
        String out = capture(() -> ctx[0] = contextOf("warn/blueprint/shop"));
        assertTrue(
                out.contains("[shop] namespace.service has no value, and blueprint/env.yml has no pattern for it"),
                out);
        assertNull(ctx[0].getNamespace("service"));
        assertEquals("com.acme.domain.shop.dto", ctx[0].getNamespace("dto"));
    }

    @Test
    public void aPatternUsingBaseWithoutARootBaseIsSaid() throws Exception {
        Map<String, Object> noBase = RootEnv.read(
                        write("blueprint/env.yml", "elasticore:", "  namespace:", "    dto: \"{base}.{domain}.dto\""))
                .settings();
        RootEnv.Merged m = RootEnv.apply(noBase, section("elasticore:", "  namespace:", "    dto:"), "shop");
        assertNull(map(m.env().get("namespace")).get("dto"));
        assertTrue(
                m.warnings().get(0).contains("no namespace.base"), m.warnings().toString());
    }

    @Test
    public void aModuleRedirectInAPatternStaysForTheLoader() throws Exception {
        Map<String, Object> redirect = RootEnv.read(write(
                        "blueprint/env.yml",
                        "elasticore:",
                        "  namespace:",
                        "    service: \"app-admin://com.acme.{domain}.service\""))
                .settings();
        RootEnv.Merged m = RootEnv.apply(redirect, section("elasticore:", "  namespace:", "    service:"), "shop");
        assertEquals(
                "app-admin://com.acme.shop.service",
                map(m.env().get("namespace")).get("service"));
    }

    // ---- every other key -------------------------------------------------------------------------

    @Test
    public void mapsMergeKeyByKeyAndTheDomainWins() throws Exception {
        RootEnv.Merged m = RootEnv.apply(
                root(),
                section(
                        "elasticore:",
                        "  config:",
                        "    domainName: shop",
                        "    apiPath: /api/shop",
                        "    interface:",
                        "      sortable: com.shop.Sorting",
                        "    annotations:",
                        "      maskname: \"@dto:com.shop.DomainMask\"",
                        "      upper: \"@dto:com.shop.Upper\""),
                "shop");
        Map<String, Object> config = map(m.env().get("config"));
        assertEquals("shop", config.get("domainName"));
        assertEquals("/api/shop", config.get("apiPath"), "a value the domain writes wins");
        assertEquals("jpa", config.get("mode"), "a value it does not write is inherited");
        Map<String, Object> iface = map(config.get("interface"));
        assertEquals("com.acme.Paging", iface.get("pageable"));
        assertEquals("com.shop.Sorting", iface.get("sortable"));
        Map<String, Object> annotations = map(config.get("annotations"));
        assertEquals("genid(com.acme.Ids.next)", annotations.get("genid"), "an entry it does not name is inherited");
        assertEquals("@dto:com.shop.DomainMask", annotations.get("maskname"), "an entry it names is replaced");
        assertEquals("@dto:com.shop.Upper", annotations.get("upper"), "an entry of its own is added");
        assertTrue(m.inherited().contains("config.mode"));
        assertTrue(m.inherited().contains("config.annotations.genid"));
        assertFalse(m.inherited().contains("config.annotations.maskname"));
        assertFalse(m.inherited().contains("config.apiPath"));
    }

    @Test
    public void aListIsReplacedWholeNotAppendedTo() throws Exception {
        RootEnv.Merged own = RootEnv.apply(
                root(), section("elasticore:", "  config:", "    publisher:", "      suppress: [jpa-comment]"), "shop");
        Object suppress = map(map(own.env().get("config")).get("publisher")).get("suppress");
        assertEquals(Arrays.asList("jpa-comment"), suppress);
        RootEnv.Merged inherited = RootEnv.apply(root(), section("elasticore:", "  config:", "    mode: jpa"), "shop");
        assertEquals(
                Arrays.asList("spring-api-errors", "jpa-comment"),
                map(map(inherited.env().get("config")).get("publisher")).get("suppress"));
        assertTrue(inherited.inherited().contains("config.publisher.suppress"));
    }

    @Test
    public void aKeyWrittenWithNoValueTakesTheRoots() throws Exception {
        RootEnv.Merged m = RootEnv.apply(root(), section("elasticore:", "  config:", "    apiPath:"), "shop");
        assertEquals("/api/v9", map(m.env().get("config")).get("apiPath"));
    }

    @Test
    public void theDomainsSettingsAreNotSharedWithTheNextDomain() throws Exception {
        Map<String, Object> root = root();
        RootEnv.Merged a = RootEnv.apply(root, section("elasticore:", "  config:", "    domainName: a"), "a");
        map(map(a.env().get("config")).get("annotations")).put("x", "y");
        RootEnv.Merged b = RootEnv.apply(root, section("elasticore:", "  config:", "    domainName: b"), "b");
        assertFalse(map(map(b.env().get("config")).get("annotations")).containsKey("x"));
    }

    // ---- per-domain keys in the root, and what the build says -------------------------------------

    @Test
    public void domainNameAndEnableInTheRootAreIgnoredAndSaid() throws Exception {
        File f = write(
                "blueprint/env.yml",
                "elasticore:",
                "  enable: false",
                "  config:",
                "    domainName: everything",
                "    mode: jpa");
        RootEnv.Root root = RootEnv.read(f);
        assertEquals(Arrays.asList("enable", "config.domainName"), root.ignored());
        assertFalse(root.settings().containsKey("enable"));
        assertFalse(map(root.settings().get("config")).containsKey("domainName"));
        assertEquals(1, root.count());

        String out = capture(() -> RootEnv.announce(f.getParentFile(), "blueprint/env.yml", 2));
        assertTrue(out.contains("blueprint/env.yml: enable is one per domain - ignored here"), out);
        assertTrue(out.contains("blueprint/env.yml: config.domainName is one per domain - ignored here"), out);
        assertTrue(out.contains("blueprint/env.yml: 1 setting inherited by 2 domains"), out);
    }

    @Test
    public void theLineCountsWhatTheFileGives() throws Exception {
        File f = write("blueprint/env.yml", ROOT);
        // config: mode, j2ee, apiPath, 2 interface, 2 annotations, the suppress list; namespace: 6.
        assertEquals(14, RootEnv.read(f).count());
        assertEquals("blueprint/env.yml: 6 settings inherited by 9 domains", RootEnv.line("blueprint/env.yml", 6, 9));
        assertEquals("", capture(() -> RootEnv.announce(tmp.resolve("nothing").toFile(), "blueprint/env.yml", 1)));
    }

    @Test
    public void aRootFileThatIsNoYamlStopsTheLoad() throws Exception {
        File f = write("blueprint/env.yml", "elasticore:", "  config: [unclosed");
        assertThrows(IllegalArgumentException.class, () -> RootEnv.read(f));
        // An empty one gives nothing.
        File empty = write("other/env.yml");
        assertEquals(0, RootEnv.read(empty).count());
    }

    @Test
    public void theListingMarksTheInheritedSettings() throws Exception {
        write("blueprint/env.yml", ROOT);
        File domain = write(
                        "blueprint/shop/env.yml",
                        "elasticore:",
                        "  config:",
                        "    domainName: shop",
                        "    apiPath: /api/shop",
                        "  namespace:",
                        "    dto:")
                .getParentFile();
        List<String> lines = RootEnv.listing(domain);
        String text = String.join("\n", lines);
        assertTrue(text.contains("    * config.mode = jpa"), text);
        assertTrue(text.contains("      config.apiPath = /api/shop"), text);
        assertTrue(text.contains("    * namespace.dto = com.acme.domain.shop.dto"), text);
        // Without the root file the listing is what it was.
        File alone = write("solo/blueprint/shop/env.yml", "elasticore:", "  config:", "    domainName: shop")
                .getParentFile();
        assertTrue(RootEnv.listing(alone).isEmpty());
    }

    // ---- the loader: the root's settings have the effect the domain's own have --------------------

    @Test
    public void theLoaderTakesTheRootAsTheDomainsOwnSettings() throws Exception {
        write("thin/blueprint/env.yml", ROOT);
        write(
                "thin/blueprint/shop/env.yml",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: shop",
                "    annotations:",
                "      maskname: \"@dto:com.shop.DomainMask\"",
                "  namespace:",
                "    entity:",
                "    dto:",
                "    port: com.shop.port");
        write(
                "fat/blueprint/shop/env.yml",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    mode: jpa",
                "    j2ee: jakarta",
                "    apiPath: /api/v9",
                "    interface:",
                "      pageable: com.acme.Paging",
                "      sortable: com.acme.Sorting",
                "    annotations:",
                "      genid: \"genid(com.acme.Ids.next)\"",
                "      maskname: \"@dto:com.shop.DomainMask\"",
                "    publisher:",
                "      suppress: [spring-api-errors, jpa-comment]",
                "    domainName: shop",
                "  namespace:",
                "    entity: com.acme.domain.shop.entity",
                "    dto: com.acme.domain.shop.dto",
                "    port: com.shop.port");
        ModelLoaderContext thin = contextOf("thin/blueprint/shop");
        ModelLoaderContext fat = contextOf("fat/blueprint/shop");
        assertEquals(fat.getDomainId(), thin.getDomainId());
        assertEquals(fat.getNsMap(), thin.getNsMap());
        for (String key : new String[] {
            "mode",
            "j2ee",
            "apiPath",
            "interface.pageable",
            "annotations.genid",
            "annotations.maskname",
            "publisher.suppress",
            "domainName"
        }) {
            assertEquals(fat.getConfig(key), thin.getConfig(key), key);
        }
        assertEquals(fat.getConfigMap().keySet(), thin.getConfigMap().keySet());
    }

    @Test
    public void withoutTheRootFileTheDomainIsReadAsWritten() throws Exception {
        write(
                "plain/blueprint/shop/env.yml",
                "elasticore:",
                "  config:",
                "    domainName: shop",
                "  namespace:",
                "    entity:",
                "    dto: com.x.dto");
        ModelLoaderContext ctx = contextOf("plain/blueprint/shop");
        assertNull(ctx.getNamespace("entity"));
        assertEquals("com.x.dto", ctx.getNamespace("dto"));
        assertNull(ctx.getConfig("mode"));
        assertSame(null, RootEnv.fileFor(tmp.resolve("plain/blueprint/shop").toFile()));
    }

    private ModelLoaderContext contextOf(String dir) {
        FileBasedModelLoader loader = FileBasedModelLoader.newInstance();
        loader.setTemplateFileDirPath(tmp.resolve(dir).toFile().getAbsolutePath());
        return loader.getModelLoaderContext(new File(dir).getName());
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static Map<String, String> stringMap(Map<String, Object> m) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> e : m.entrySet())
            out.put(e.getKey(), e.getValue() == null ? null : String.valueOf(e.getValue()));
        return out;
    }

    private File write(String path, String... lines) throws Exception {
        File f = tmp.resolve(path).toFile();
        assertTrue(f.getParentFile().isDirectory() || f.getParentFile().mkdirs());
        String text = lines.length == 0 ? "" : String.join("\n", lines) + "\n";
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    private static String capture(Runnable r) throws Exception {
        java.io.PrintStream out = System.out;
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(bytes, true, "UTF-8"));
        try {
            r.run();
        } finally {
            System.setOut(out);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }
}
