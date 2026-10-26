package io.elasticore.base.model.pub.ossie;

import io.elasticore.base.model.pub.rules.verify.RulesGenRunner;
import io.elasticore.base.model.pub.rules.verify.SpecFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The model named by the project rather than by its domains. A project of
 * two domains, generated with elasticore.ossie.name set the way the plugin's
 * ossieName sets it, and read from the file. The name left unset is held by
 * OssieExportTest, on the ddd sample's committed export.
 */
public class OssieModelNameTest {

    private static File project;
    private static String yaml;

    private static String env(String domain) {
        return String.join("\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: " + domain,
                "    framework: springboot",
                "    mode: jpa",
                "    j2ee: jakarta",
                "    publisher:",
                "      rules: [hexagonal-springboot, ossie-export]",
                "  namespace:",
                "    base: com.acme." + domain,
                "");
    }

    private static final String CATALOG = String.join("\n",
            "entity:",
            "  Category:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      categoryId: long @id @sequence",
            "      name: string(100)!",
            "");

    private static final String SHOP = String.join("\n",
            "entity:",
            "  Basket:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      basketId: long @id @sequence",
            "      owner: string(100)!",
            "");

    @BeforeAll
    static void generate() throws Exception {
        project = Files.createTempDirectory("elcore-ossie-name").toFile();
        write("catalog", CATALOG);
        write("shop", SHOP);

        String[] keys = {"elasticore.base.path", "elasticore.architecture", "elasticore.language",
                "elasticore.rules.force", "elasticore.publisher.rules", OssieSemanticModelExporter.NAME_PROPERTY};
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "hexagonal");
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty("elasticore.publisher.rules", "ossie-export");
            // Not an identifier as written: the export says so and writes one.
            System.setProperty(OssieSemanticModelExporter.NAME_PROPERTY, " acme-shop ");
            RulesGenRunner.main(new String[0]);
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
            resetStatics();
        }
        File exported = new File(project, "src/main/resources/blueprint/" + OssieSemanticModelExporter.FILE_NAME);
        assertTrue(exported.isFile(), "no semantic model exported under " + project);
        yaml = new String(Files.readAllBytes(exported.toPath()), StandardCharsets.UTF_8);
    }

    private static void write(String domain, String model) throws IOException {
        File dir = new File(project, "src/main/resources/blueprint/" + domain);
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(new File(dir, "env.yml").toPath(), env(domain).getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
    }

    private static void resetStatics() throws Exception {
        java.lang.reflect.Field tmp = io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
    }

    @AfterAll
    static void clean() throws IOException {
        if (project == null || !project.exists()) return;
        Files.walkFileTree(project.toPath(), new SimpleFileVisitor<Path>() {
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

    @Test
    public void theGivenNameNamesTheModel() {
        assertTrue(yaml.contains("\nname: acme_shop\n"), yaml);
        assertTrue(!yaml.contains("\nname: catalog_shop\n"), "the domains still named the model:\n" + yaml);
    }

    @Test
    public void theDomainsAreStillSaid() {
        // Only the name moves: the description and the extension keep listing the domains.
        int at = yaml.indexOf("\ndescription: \"Domain model of ");
        assertTrue(at >= 0, yaml);
        String description = yaml.substring(at, yaml.indexOf('\n', at + 1));
        assertTrue(description.contains("catalog") && description.contains("shop"), description);
        assertTrue(yaml.contains("  - name: Category\n") && yaml.contains("  - name: Basket\n"), yaml);
    }
}
