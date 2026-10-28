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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A @formula in the exported model, declared as what it can honestly be: an
 * expression of the Ossie language as the portable top-level sql
 * (OSSIE_SQL_2026, never named), a subquery as an entry of the opaque ANSI_SQL
 * dialect. Generated the way a project generates it - a hexagonal build that
 * opted into ossie-export - and read from the file.
 */
public class OssieFormulaDialectTest {

    private static final String SUBQUERY =
            "(select count(*) from cat_item i where i.category_id = category_id)";
    private static final String ARITHMETIC = "unit_price * quantity";

    private static final String ENV = String.join("\n",
            "elasticore:",
            "  enable: true",
            "  config:",
            "    domainName: catalog",
            "    framework: springboot",
            "    mode: jpa",
            "    j2ee: jakarta",
            "    publisher:",
            "      rules: [hexagonal-springboot, ossie-export]",
            "  namespace:",
            "    base: com.acme.catalog",
            "");

    private static final String MODEL = String.join("\n",
            "entity:",
            "  Category:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      categoryId: long @id @sequence",
            "      name: string(100)!",
            "      itemCount: long @readonly @formula(\"" + SUBQUERY + "\") -- items in it",
            "  Item:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      itemId: long @id @sequence",
            "      unitPrice: money!",
            "      quantity: int!",
            "      lineTotal: money @readonly @formula(\"" + ARITHMETIC + "\") -- price times quantity",
            "");

    private static File project;
    private static String yaml;

    @BeforeAll
    static void generate() throws Exception {
        project = Files.createTempDirectory("elcore-ossie-formula").toFile();
        File dir = new File(project, "src/main/resources/blueprint/catalog");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(new File(dir, "env.yml").toPath(), ENV.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));

        String[] keys = {"elasticore.base.path", "elasticore.architecture", "elasticore.language",
                "elasticore.rules.force", "elasticore.publisher.rules"};
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "hexagonal");
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty("elasticore.publisher.rules", "ossie-export");
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
    public void aSubqueryFormulaIsOpaqueSqlAndNothingElse() {
        String field = field("item_count");
        // Kept as a dialect entry, with no top-level sql: the spec makes sql
        // authoritative when both are there, and this SQL is not portable.
        assertTrue(field.contains("        expression:\n          dialects:\n            - dialect: ANSI_SQL\n"
                + "              expression: \"" + SUBQUERY + "\"\n"), field);
        assertEquals("ANSI_SQL", dialectOf(field), field);
        assertTrue(!field.contains(" sql: "), "a subquery claimed to be in the Ossie language:\n" + field);
        assertTrue(!field.contains("OSSIE_SQL_2026"), "a subquery claimed to be in the Ossie language:\n" + field);
        assertTrue(field.contains("\\\"formula\\\": \\\"database\\\""), "the extension does not say the database computes it:\n" + field);
    }

    @Test
    public void anArithmeticFormulaIsInTheOssieLanguage() {
        String field = field("line_total");
        assertTrue(field.contains("        expression:\n          sql: \"" + ARITHMETIC + "\"\n"), field);
        assertTrue(!field.contains("dialect"), "a portable formula named a dialect:\n" + field);
        assertTrue(field.contains("\\\"formula\\\": \\\"database\\\""), field);
    }

    @Test
    public void aPlainColumnIsInTheOssieLanguage() {
        String field = field("unit_price");
        assertTrue(field.contains("        expression:\n          sql: \"unit_price\"\n"), field);
        assertTrue(!field.contains("dialect"), "a column named a dialect:\n" + field);
        assertTrue(!field.contains("formula"), "a stored column is not a formula:\n" + field);
    }

    /** One field's block: from its name to the next field or the dataset's extension. */
    private static String field(String column) {
        int at = yaml.indexOf("      - name: " + column + "\n");
        assertTrue(at >= 0, "no field " + column + " in:\n" + yaml);
        int end = yaml.indexOf("\n      - name: ", at + 1);
        int ext = yaml.indexOf("\n    custom_extensions:", at + 1);
        if (end < 0 || (ext >= 0 && ext < end)) end = ext;
        return yaml.substring(at, end < 0 ? yaml.length() : end);
    }

    private static String dialectOf(String field) {
        Matcher m = Pattern.compile("- dialect: (\\w+)").matcher(field);
        assertTrue(m.find(), "no dialect in:\n" + field);
        String first = m.group(1);
        assertTrue(!m.find(), "more than one dialect entry:\n" + field);
        return first;
    }
}
