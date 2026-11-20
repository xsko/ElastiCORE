package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * archTest: true over the layered code writes the layers' rules - the web
 * uses the services, the services use persistence, the entities and the
 * DTOs, nothing points back up - instead of the hexagon's, which the layered
 * services break by calling the repositories and the persistence helper
 * directly. That the rules hold on generated code, and fail on a violation
 * added by hand, is shown by building generated projects (the change's
 * history entry); this test holds what is written.
 */
public class LayeredArchitectureTestTest {

    private static final String[] KEYS = {
        "elasticore.base.path",
        "elasticore.architecture",
        "elasticore.language",
        "elasticore.rules.force",
        "elasticore.rules.v2",
        "elasticore.rules.v3",
        "elasticore.rules.ts",
        "elasticore.rules.kt",
        "elasticore.publisher.rules.add",
        "elasticore.rules.list"
    };

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Product:",
            "    meta: entity @root @editable @expose(20) @access(read=public) @sort(name+) -- product",
            "    fields:",
            "      productId: long @id @sequence",
            "      name:      string(100)! @search(like)",
            "      price:     money(10.2)! @min(0.01, 'price must be positive')",
            "");

    private final List<File> projects = new ArrayList<File>();
    private final String[] saved = save();

    @AfterEach
    void clean() throws IOException {
        for (int i = 0; i < KEYS.length; i++) {
            if (saved[i] == null) System.clearProperty(KEYS[i]);
            else System.setProperty(KEYS[i], saved[i]);
        }
        for (File p : projects) delete(p.toPath());
    }

    @Test
    public void theLayeredCodeGetsTheLayersRules() throws Exception {
        File project = project();
        System.setProperty("elasticore.architecture", "layered");
        System.setProperty("elasticore.language", "java");
        String text = archTest(project, "src/test/java/com/acme/shop/ShopArchitectureTest.java");
        assertTrue(text.contains(" * The layers, checked on every build"), text);
        assertTrue(
                text.contains(
                        "static final ArchRule servicesDependOnNoWeb = noClasses()\n"
                                + "            .that().resideInAnyPackage(\"com.acme.shop.application..\")\n"
                                + "            .should().dependOnClassesThat().resideInAnyPackage(\"com.acme.shop.adapter.in.web..\");"),
                text);
        // The DTOs and the ports under the services' package are shared: a MyBatis mapper takes the search DTO.
        assertTrue(
                text.contains("static final ArchRule persistenceDependsOnNothingAbove = noClasses()\n"
                        + "            .that().resideInAnyPackage(\"com.acme.shop.infrastructure.persistence..\","
                        + " \"com.acme.shop.domain.model..\", \"com.acme.shop.domain.event..\","
                        + " \"com.acme.shop.domain.service..\", \"com.acme.shop.domain.repository..\")\n"
                        + "            .should().dependOnClassesThat(JavaClass.Predicates.resideInAnyPackage("
                        + "\"com.acme.shop.application..\", \"com.acme.shop.adapter.in.web..\")"
                        + ".and(DescribedPredicate.not(JavaClass.Predicates.resideInAnyPackage("
                        + "\"com.acme.shop.application.dto..\", \"com.acme.shop.application.port.out..\"))));"),
                text);
        assertFalse(text.contains("applicationDependsOnPortsNotAdapters"), text);
        assertFalse(text.contains("hexagon"), text);
    }

    @Test
    public void aProjectThatDeclaresNoArchitectureIsLayeredToo() throws Exception {
        File project = project();
        String text = archTest(project, "src/test/java/com/acme/shop/ShopArchitectureTest.java");
        assertTrue(text.contains("servicesDependOnNoWeb"), text);
    }

    @Test
    public void theLayeredKotlinCodeGetsTheLayersRules() throws Exception {
        File project = project();
        System.setProperty("elasticore.architecture", "layered");
        System.setProperty("elasticore.language", "kotlin");
        String text = archTest(project, "src/test/kotlin/com/acme/shop/ShopArchitectureTest.kt");
        assertTrue(text.contains("    val servicesDependOnNoWeb: ArchRule = noClasses()\n"), text);
        assertTrue(text.contains("    val persistenceDependsOnNothingAbove: ArchRule = noClasses()\n"), text);
        assertFalse(text.contains("applicationDependsOnPortsNotAdapters"), text);
    }

    @Test
    public void theHexagonKeepsItsRules() throws Exception {
        for (String architecture : new String[] {"hexagonal", "ddd"}) {
            File project = project();
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", "java");
            String text = archTest(project, "src/test/java/com/acme/shop/ShopArchitectureTest.java");
            assertTrue(
                    text.contains(" * The hexagon's boundaries, checked on every build. The packages are the\n"
                            + " * namespaces the domain's env.yml declares. Yours from here: add rules.\n"),
                    text);
            assertTrue(text.contains("applicationDependsOnPortsNotAdapters"), text);
            assertTrue(text.contains("domainDependsOnNothingOutside"), text);
            assertFalse(text.contains("servicesDependOnNoWeb"), text);
        }
    }

    // ---- helpers ----------------------------------------------------------------------

    private String archTest(File project, String path) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        int code;
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.rules.force", "true");
            code = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        assertEquals(0, code, log);
        File test = new File(project, path);
        assertNotNull(test.isFile() ? test : null, path + " was not written: " + log);
        return new String(Files.readAllBytes(test.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private File project() throws IOException {
        File dir = Files.createTempDirectory("elcore-archtest").toFile();
        projects.add(dir);
        File blueprint = new File(dir, "src/main/resources/blueprint/shop");
        assertTrue(new File(blueprint, "model").mkdirs());
        String env = String.join(
                "\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: shop",
                "    framework: springboot",
                "    mode: jpa",
                "    j2ee: jakarta",
                "    archTest: true",
                "  namespace:",
                "    base: com.acme.shop",
                "");
        Files.write(new File(blueprint, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/entity.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private static String[] save() {
        String[] s = new String[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) s[i] = System.getProperty(KEYS[i]);
        return s;
    }

    private static void delete(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> all = Files.walk(root)) {
            List<Path> paths = new ArrayList<Path>();
            all.forEach(paths::add);
            for (int i = paths.size() - 1; i >= 0; i--) Files.deleteIfExists(paths.get(i));
        }
    }
}
