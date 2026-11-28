package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.extract.FileBasedSourceFileAccessFactory;
import io.elasticore.base.extract.ModelExtractor;
import io.elasticore.base.model.ECoreModel;
import io.elasticore.processor.ElastiCoreProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * blueprint/env.yml (issue 248) read by every reader the same way: a project
 * that moves its shared settings into the root file generates what it
 * generated with them written in each domain - through the rule engine
 * (elcore3, BlueprintLoader), the 2.x generator (ModelExtractor), the
 * annotation processor and a multi-module build, one root file per module.
 * The TypeScript client's digest reads the root file; nothing else of the root.
 */
public class RootEnvReadersTest {

    @TempDir
    Path tmp;

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Item:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      itemId: long @id @sequence",
            "      name: string(20) @env:maskname",
            "      code: string(20) @env:upper",
            "");

    /** The settings written in the domain, as a project has them today. */
    private static final String FAT = String.join(
            "\n",
            "elasticore:",
            "  enable: true",
            "  config:",
            "    domainName: shop",
            "    framework: springboot",
            "    mode: jpa",
            "    j2ee: jakarta",
            "    apiPath: /api/v9",
            "    table:",
            "      prefix: shop_",
            "    annotations:",
            "      maskname: \"@dto:com.acme.mask.DomainMask\"",
            "      upper: \"@dto:com.acme.mask.Upper\"",
            "    publisher:",
            "      rules: [jpa-springboot-v3]",
            "  namespace:",
            "    entity: com.acme.domain.shop.entity",
            "    enumeration: com.acme.domain.shop.enums",
            "    dto: com.acme.domain.shop.dto",
            "    repository: com.acme.domain.shop.repository",
            "    service: com.acme.domain.shop.service",
            "    control: com.acme.domain.shop.control",
            "");

    /** The same, the shared part moved to blueprint/env.yml. */
    private static final String ROOT = String.join(
            "\n",
            "elasticore:",
            "  config:",
            "    framework: springboot",
            "    mode: jpa",
            "    j2ee: jakarta",
            "    apiPath: /api/v9",
            "    annotations:",
            "      maskname: \"@dto:com.acme.mask.RootMask\"",
            "      upper: \"@dto:com.acme.mask.Upper\"",
            "    publisher:",
            "      rules: [jpa-springboot-v3, ossie-export]",
            "  namespace:",
            "    base: com.acme",
            "    entity: \"{base}.domain.{domain}.entity\"",
            "    enumeration: \"{base}.domain.{domain}.enums\"",
            "    dto: \"{base}.domain.{domain}.dto\"",
            "    repository: \"{base}.domain.{domain}.repository\"",
            "    service: \"{base}.domain.{domain}.service\"",
            "    control: \"{base}.domain.{domain}.control\"",
            "    port: \"{base}.domain.{domain}.port\"",
            "");

    private static final String THIN = String.join(
            "\n",
            "elasticore:",
            "  enable: true",
            "  config:",
            "    domainName: shop",
            "    table:",
            "      prefix: shop_",
            "    annotations:",
            "      maskname: \"@dto:com.acme.mask.DomainMask\"",
            "    publisher:",
            "      rules: [jpa-springboot-v3]",
            "  namespace:",
            "    entity:",
            "    enumeration:",
            "    dto:",
            "    repository:",
            "    service:",
            "    control:",
            "");

    private static final String[] KEYS = {
        "elasticore.base.path",
        "elasticore.architecture",
        "elasticore.language",
        "elasticore.rules.force",
        "elasticore.rules.list",
        "elasticore.modules",
        "elasticore.rules.v2",
        "elasticore.rules.v3"
    };
    private final String[] saved = new String[KEYS.length];
    private Object[] context;

    @BeforeEach
    void save() throws Exception {
        for (int i = 0; i < KEYS.length; i++) saved[i] = System.getProperty(KEYS[i]);
        context = saveContext();
    }

    @AfterEach
    void restore() throws Exception {
        for (int i = 0; i < KEYS.length; i++) {
            if (saved[i] == null) System.clearProperty(KEYS[i]);
            else System.setProperty(KEYS[i], saved[i]);
        }
        restoreContext(context);
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
    }

    // ---- the rule engine ---------------------------------------------------------------------------

    @Test
    public void theBlueprintLoaderTakesTheRootFile() throws Exception {
        File fat = project("bl-fat", false);
        File thin = project("bl-thin", true);
        ECoreModel a = only(BlueprintLoader.load(blueprint(fat).getPath()));
        String[] said = new String[1];
        ECoreModel b =
                only(capture(said, () -> BlueprintLoader.load(blueprint(thin).getPath())));
        assertEquals(a.getNamespaceMap(), b.getNamespaceMap());
        for (String key : new String[] {
            "framework",
            "mode",
            "j2ee",
            "apiPath",
            "annotations.maskname",
            "annotations.upper",
            "publisher.rules",
            "table.prefix",
            "domainName"
        }) {
            assertEquals(a.getConfig(key), b.getConfig(key), key);
        }
        assertTrue(said[0].contains("[ElastiCORE] blueprint/env.yml: 15 settings inherited by 1 domain"), said[0]);
    }

    @Test
    public void elcore3GeneratesWhatThePerDomainFilesGenerate() throws Exception {
        File fat = project("e3-fat", false);
        File thin = project("e3-thin", true);
        assertEquals(0, runRuleEngine(fat, false));
        assertEquals(0, runRuleEngine(thin, false));
        Map<String, String> a = tree(fat);
        Map<String, String> b = tree(thin);
        assertTrue(
                a.containsKey("src/main/java/com/acme/domain/shop/control/ItemController.java"),
                a.keySet().toString());
        assertTrue(a.get("src/main/java/com/acme/domain/shop/dto/ItemDTO.java").contains("DomainMask"));
        assertEquals(a, b);
        // The domain's list replaced the root's: no Ossie export.
        assertFalse(
                b.containsKey("src/main/resources/blueprint/semantic_model.yml"),
                b.keySet().toString());
    }

    @Test
    public void listRulesMarksTheInheritedSettings() throws Exception {
        File thin = project("lr-thin", true);
        String[] said = new String[1];
        capture(said, () -> {
            System.setProperty("elasticore.rules.list", "true");
            runRuleEngine(thin, false);
            return null;
        });
        assertTrue(said[0].contains("  settings (* from blueprint/env.yml):"), said[0]);
        assertTrue(said[0].contains("    * config.apiPath = /api/v9"), said[0]);
        assertTrue(said[0].contains("      config.annotations.maskname = @dto:com.acme.mask.DomainMask"), said[0]);
        assertTrue(said[0].contains("    * namespace.dto = com.acme.domain.shop.dto"), said[0]);

        File fat = project("lr-fat", false);
        capture(said, () -> {
            System.setProperty("elasticore.rules.list", "true");
            runRuleEngine(fat, false);
            return null;
        });
        assertFalse(said[0].contains("settings (* from"), "no root file, the listing as before");
    }

    /** The env guide's example (section 1-1), and what the guide says the build prints for it. */
    @Test
    public void theEnvGuidesExamplePrintsWhatTheGuideShows() throws Exception {
        File project = tmp.resolve("guide").toFile();
        File bp = blueprint(project);
        write(
                new File(bp, "env.yml"),
                String.join(
                        "\n",
                        "elasticore:",
                        "  config:",
                        "    framework: springboot",
                        "    mode: jpa",
                        "    j2ee: jakarta",
                        "    apiPath: \"/api/v1\"",
                        "    annotations:",
                        "      genid: \"genid(io.elasticore.springboot3.util.IdUtils.newId)\"",
                        "  namespace:",
                        "    base: com.acme",
                        "    entity:      \"{base}.domain.{domain}.entity\"",
                        "    dto:         \"{base}.domain.{domain}.dto\"",
                        "    enumeration: \"{base}.domain.{domain}.enums\"",
                        "    repository:  \"{base}.domain.{domain}.repository\"",
                        "    service:     \"{base}.domain.{domain}.service\"",
                        "    control:     \"{base}.domain.{domain}.control\"",
                        "    port:        \"{base}.domain.{domain}.port\"",
                        ""));
        write(
                new File(bp, "catalog/env.yml"),
                String.join(
                        "\n",
                        "elasticore:",
                        "  enable: true",
                        "  config:",
                        "    domainName: catalog",
                        "    table:",
                        "      prefix: cat_",
                        "  namespace:",
                        "    dto:",
                        "    enumeration:",
                        "    port:",
                        ""));
        write(
                new File(bp, "catalog/model/model.yml"),
                String.join("\n", "dto:", "  BookDTO:", "    meta: dto", "    fields:", "      title: string(20)", ""));
        write(
                new File(bp, "shop/env.yml"),
                String.join(
                        "\n",
                        "elasticore:",
                        "  enable: true",
                        "  config:",
                        "    domainName: shop",
                        "  namespace:",
                        "    dto:",
                        ""));
        write(
                new File(bp, "shop/model/model.yml"),
                String.join("\n", "dto:", "  OrderDTO:", "    meta: dto", "    fields:", "      note: string(20)", ""));
        String[] said = new String[1];
        capture(said, () -> runRuleEngine(project, true));
        String out = said[0].replace("\r\n", "\n");
        assertTrue(out.contains("[ElastiCORE] blueprint/env.yml: 13 settings inherited by 2 domains"), out);
        assertTrue(
                out.contains(String.join(
                        "\n",
                        "[rules] catalog: jpa-springboot-v3",
                        "  settings (* from blueprint/env.yml):",
                        "    * config.framework = springboot",
                        "    * config.mode = jpa",
                        "    * config.j2ee = jakarta",
                        "    * config.apiPath = /api/v1",
                        "    * config.annotations.genid = genid(io.elasticore.springboot3.util.IdUtils.newId)",
                        "      config.domainName = catalog",
                        "      config.table.prefix = cat_",
                        "    * namespace.dto = com.acme.domain.catalog.dto",
                        "    * namespace.enumeration = com.acme.domain.catalog.enums",
                        "    * namespace.port = com.acme.domain.catalog.port",
                        "")),
                out);
    }

    @Test
    public void aMultiModuleBuildReadsOneRootFilePerModule() throws Exception {
        File one = project("mm/app-one", true);
        File two = project("mm/app-two", true);
        write(new File(blueprint(two), "env.yml"), ROOT.replace("{base}.domain.{domain}.dto", "com.two.{domain}.dto"));
        write(new File(blueprint(two), "shop/env.yml"), THIN.replace("domainName: shop", "domainName: shop2"));
        String[] said = new String[1];
        BlueprintLoader loader =
                capture(said, () -> BlueprintLoader.loadModules(one.getAbsolutePath() + "," + two.getAbsolutePath()));
        ECoreModel first = loader.modelsOf("app-one/shop").get(0);
        ECoreModel second = loader.modelsOf("app-two/shop").get(0);
        assertEquals("com.acme.domain.shop.dto", first.getNamespace("dto"));
        assertEquals("com.two.shop2.dto", second.getNamespace("dto"));
        assertTrue(said[0].contains("app-one/blueprint/env.yml: 15 settings inherited by 1 domain"), said[0]);
        assertTrue(said[0].contains("app-two/blueprint/env.yml: 15 settings inherited by 1 domain"), said[0]);
    }

    @Test
    public void theClientDigestReadsTheRootFileAndNothingElseThere() throws Exception {
        File thin = project("dg", true);
        String digest = RulesGenRunner.modelDigest(thin.getPath());
        File root = new File(blueprint(thin), "env.yml");
        write(root, ROOT.replace("/api/v9", "/api/v10"));
        String changed = RulesGenRunner.modelDigest(thin.getPath());
        assertNotEquals(digest, changed, "a changed apiPath in blueprint/env.yml makes the client older");
        write(new File(blueprint(thin), "schema.yml"), "x: 1\n");
        assertEquals(changed, RulesGenRunner.modelDigest(thin.getPath()), "what the generator writes there is not");
        assertTrue(root.delete());
        assertNotEquals(changed, RulesGenRunner.modelDigest(thin.getPath()));
    }

    // ---- the 2.x generator, the annotation processor, multi-module 2.x -----------------------------

    @Test
    public void the2xGeneratorGeneratesWhatThePerDomainFilesGenerate() throws Exception {
        File fat = project("x2-fat", false);
        File thin = project("x2-thin", true);
        extract(fat);
        String[] said = new String[1];
        capture(said, () -> {
            extract(thin);
            return null;
        });
        Map<String, String> a = tree(fat);
        assertTrue(
                a.containsKey("src/main/java/com/acme/domain/shop/dto/ItemDTO.java"),
                a.keySet().toString());
        assertEquals(a, tree(thin));
        assertTrue(said[0].contains("[ElastiCORE] blueprint/env.yml: 15 settings inherited by 1 domain"), said[0]);
    }

    @Test
    public void theAnnotationProcessorGeneratesWhatThePerDomainFilesGenerate() throws Exception {
        File fat = project("ap-fat", false);
        File thin = project("ap-thin", true);
        process(fat);
        process(thin);
        Map<String, String> a = tree(fat);
        assertTrue(
                a.containsKey("src/main/java/com/acme/domain/shop/entity/Item.java"),
                a.keySet().toString());
        assertEquals(a, tree(thin));
    }

    @Test
    public void the2xMultiModuleBuildReadsEachModulesRootFile() throws Exception {
        File fat = project("x2mm-fat/app-one", false);
        File thin = project("x2mm-thin/app-one", true);
        extractModules(fat);
        String[] said = new String[1];
        capture(said, () -> {
            extractModules(thin);
            return null;
        });
        assertTrue(
                said[0].contains("[ElastiCORE] app-one/blueprint/env.yml: 15 settings inherited by 1 domain"), said[0]);
        Map<String, String> a = tree(fat);
        assertTrue(
                a.containsKey("src/main/java/com/acme/domain/shop/dto/ItemDTO.java"),
                a.keySet().toString());
        assertEquals(a, tree(thin));
    }

    // ---- runs ------------------------------------------------------------------------------------

    private int runRuleEngine(File project, boolean list) {
        System.setProperty("elasticore.base.path", project.getAbsolutePath());
        System.setProperty("elasticore.architecture", "layered");
        System.setProperty("elasticore.language", "java");
        System.setProperty("elasticore.rules.force", "true");
        if (list) System.setProperty("elasticore.rules.list", "true");
        return RulesGenRunner.run(new String[0]);
    }

    private void extract(File project) throws Exception {
        System.setProperty("elasticore.base.path", project.getAbsolutePath());
        new ModelExtractor(blueprint(project).getAbsolutePath())
                .extract(new FileBasedSourceFileAccessFactory(new File(project, "src/main/java").getAbsolutePath()));
    }

    private void extractModules(File module) throws Exception {
        System.setProperty("elasticore.base.path", module.getAbsolutePath());
        System.setProperty("elasticore.modules", module.getAbsolutePath());
        new ModelExtractor().extractMultiModule(module.getAbsolutePath());
    }

    private void process(File project) {
        System.setProperty("elasticore.base.path", project.getAbsolutePath());
        ProcessingEnvironment env = mock(ProcessingEnvironment.class);
        Map<String, String> options = new HashMap<String, String>();
        options.put("projectPath", project.getAbsolutePath());
        options.put("generatedPath", "src/main/java");
        when(env.getOptions()).thenReturn(options);
        Messager messager = mock(Messager.class);
        when(env.getMessager()).thenReturn(messager);
        ElastiCoreProcessor processor = new ElastiCoreProcessor();
        processor.init(env);
        TypeElement annotation = mock(TypeElement.class);
        Element marked = mock(Element.class);
        when(marked.getEnclosingElement()).thenReturn(mock(Element.class));
        RoundEnvironment round = mock(RoundEnvironment.class);
        Set<Element> elements = Collections.singleton(marked);
        org.mockito.Mockito.doReturn(elements).when(round).getElementsAnnotatedWith(annotation);
        assertTrue(processor.process(Collections.singleton(annotation), round));
        org.mockito.Mockito.verify(messager, org.mockito.Mockito.never())
                .printMessage(
                        org.mockito.ArgumentMatchers.eq(javax.tools.Diagnostic.Kind.ERROR),
                        org.mockito.ArgumentMatchers.<CharSequence>any(),
                        org.mockito.ArgumentMatchers.<Element>any());
    }

    // ---- projects ----------------------------------------------------------------------------------

    private File project(String name, boolean withRoot) throws Exception {
        File project = tmp.resolve(name).toFile();
        File domain = new File(blueprint(project), "shop");
        write(new File(domain, "model/model.yml"), MODEL);
        write(new File(domain, "env.yml"), withRoot ? THIN : FAT);
        if (withRoot) write(new File(blueprint(project), "env.yml"), ROOT);
        return project;
    }

    private static File blueprint(File project) {
        return new File(project, "src/main/resources/blueprint");
    }

    private static ECoreModel only(BlueprintLoader loader) {
        assertEquals(1, loader.allModels().size());
        return loader.allModels().get(0);
    }

    /**
     * Every file the generation left, by path, without the stamp line - except
     * the env.yml files, which differ on purpose, the guides and the caches.
     */
    private static Map<String, String> tree(File project) throws Exception {
        Map<String, String> out = new TreeMap<String, String>();
        collect(project, "", out);
        return out;
    }

    private static void collect(File dir, String rel, Map<String, String> out) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String path = rel.isEmpty() ? f.getName() : rel + "/" + f.getName();
            if (f.isDirectory()) {
                if (path.equals("build") || path.equals(".elasticore") || path.endsWith("/_resource")) continue;
                collect(f, path, out);
                continue;
            }
            if (f.getName().equals("env.yml")
                    || (path.contains("/blueprint/") && f.getName().endsWith(".md"))) continue;
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
            if (text.startsWith("//ecd:")) text = text.substring(text.indexOf('\n') + 1);
            out.put(path, text);
        }
    }

    private static void write(File f, String text) throws Exception {
        assertTrue(f.getParentFile().isDirectory() || f.getParentFile().mkdirs());
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private interface Call<T> {
        T call() throws Exception;
    }

    private static <T> T capture(String[] said, Call<T> call) throws Exception {
        PrintStream out = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        try {
            return call.call();
        } finally {
            System.setOut(out);
            said[0] = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static final String[] CONTEXT = {"STATIC_DOMAIN_MAP", "context", "tmpCtx"};

    /** The generator's model context is static; a run leaves its domains there for the tests after it. */
    private static Object[] saveContext() throws Exception {
        Object[] out = new Object[CONTEXT.length];
        for (int i = 0; i < CONTEXT.length; i++) {
            java.lang.reflect.Field f =
                    io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField(CONTEXT[i]);
            f.setAccessible(true);
            Object value = f.get(null);
            out[i] = value instanceof Map ? new HashMap<Object, Object>((Map<?, ?>) value) : value;
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void restoreContext(Object[] values) throws Exception {
        for (int i = 0; i < CONTEXT.length; i++) {
            java.lang.reflect.Field f =
                    io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField(CONTEXT[i]);
            f.setAccessible(true);
            if (values[i] instanceof Map) {
                Map map = (Map) f.get(null);
                map.clear();
                map.putAll((Map) values[i]);
            } else {
                f.set(null, values[i]);
            }
        }
    }
}
