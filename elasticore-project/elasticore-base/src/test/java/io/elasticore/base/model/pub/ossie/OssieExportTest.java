package io.elasticore.base.model.pub.ossie;

import io.elasticore.base.model.pub.rules.RuleCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Ossie export, held to the spec it claims to speak.
 *
 * The checks run against the file the sample module actually generates,
 * because the two defects the first real export had - a table prefix applied
 * on top of an explicit @table, and a relationship pointing at an embeddable
 * that is not exported as a dataset - are both invisible to a unit test with
 * a hand-built model: they only exist where a real blueprint combines the
 * features. Same reason the golden comparison runs against real output.
 */
public class OssieExportTest {

    /** Ossie's portable datatype vocabulary, from the core spec. */
    private static final Set<String> OSSIE_TYPES = new LinkedHashSet<String>(java.util.Arrays.asList(
            "String", "Integer", "Decimal", "Float", "Boolean",
            "Date", "Time", "DateTime", "DateTimeTz", "Opaque"));

    @Test
    @DisplayName("the opt-in set is one the catalog has")
    public void theRuleSetResolves() {
        assertTrue(RuleCatalog.standard().setNames().contains(OssieSemanticModelExporter.RULE_SET),
                "ossie-export is read from publisher.rules but is not a set a project can name");
    }

    @Test
    @DisplayName("every relationship points at a dataset the file contains")
    public void relationshipsAreClosed() throws IOException {
        String yaml = exportedModel();

        Set<String> datasets = matches(yaml, "- name: (\\w+)\\s*\\r?\\n\\s+source:");
        assertTrue(!datasets.isEmpty(), "no datasets found");
        Set<String> targets = matches(yaml, "\\bto: (\\w+)");
        Set<String> sources = matches(yaml, "\\bfrom: (\\w+)");

        // The first real export joined Customer to Address while Address,
        // being embeddable, was not a dataset - a file that validates nowhere.
        List<String> dangling = new ArrayList<String>();
        for (String name : targets) if (!datasets.contains(name)) dangling.add("to: " + name);
        for (String name : sources) if (!datasets.contains(name)) dangling.add("from: " + name);
        assertTrue(dangling.isEmpty(), "relationships reference entities not exported: " + dangling);
    }

    @Test
    @DisplayName("every datatype is a word the Ossie spec has")
    public void datatypesAreSpecWords() throws IOException {
        String yaml = exportedModel();

        List<String> unknown = new ArrayList<String>();
        for (String type : matches(yaml, "datatype: (\\w+)")) {
            if (!OSSIE_TYPES.contains(type)) unknown.add(type);
        }
        assertTrue(unknown.isEmpty(), "datatypes outside the spec vocabulary: " + unknown);
    }

    @Test
    @DisplayName("an explicit @table is not prefixed a second time")
    public void theTablePrefixIsNotDoubled() throws IOException {
        String yaml = exportedModel();

        // shop_products carries its prefix in the DSL's own @table; the first
        // export prepended table.prefix on top and shipped shop_shop_products.
        assertTrue(yaml.contains("source: shop_products"), yaml);
        assertTrue(!yaml.contains("shop_shop_"), "the table prefix was applied twice");

        // And an entity with no @table gets the prefix, or the fix overshot.
        assertTrue(yaml.contains("source: shop_category"), yaml);
    }

    @Test
    @DisplayName("what is exported is the dataset side, honestly")
    public void theShapeIsTheSpecShape() throws IOException {
        String yaml = exportedModel();

        assertTrue(yaml.startsWith("# Apache Ossie"), "no provenance header");
        // Core spec 0.2.0.dev0: one model, flat at the root - the semantic_model
        // array of 0.1.x is no longer accepted.
        assertTrue(yaml.contains("\nversion: " + OssieSemanticModelExporter.SPEC_VERSION + "\n"), "no spec version");
        // No ossieName in the ddd sample: the model is named after its domains, as before the setting existed.
        assertTrue(yaml.contains("\nname: billing_catalog_shop\n"), "the unnamed model's name moved");
        assertTrue(yaml.contains("\ndatasets:\n"), "datasets are not at the root");
        assertTrue(!yaml.contains("semantic_model:"), "the 0.1 array shape");
        assertTrue(yaml.contains("primary_key: ["), "no primary keys exported");

        // No metrics section: this sample declares no metrics:, and a metric
        // invented from entity fields would be an exported guess.
        assertTrue(!yaml.contains("\nmetrics:"),
                "a metrics section appeared that no metrics: declared");
    }

    @Test
    @DisplayName("every field is an expression in the Ossie language: the spec requires one")
    public void everyFieldHasAnExpression() throws IOException {
        String yaml = exportedModel();
        int fields = count(yaml, "\n      - name: ");
        // OSSIE_SQL_2026 is the one dialect the spec defines; ANSI_SQL names
        // SQL a consumer may ignore, and no field of this sample needs it.
        int expressions = count(yaml, "\n        expression:\n          dialects:\n            - dialect: OSSIE_SQL_2026\n");
        assertTrue(fields > 0 && fields == expressions, fields + " fields, " + expressions + " expressions");
        assertTrue(!yaml.contains("dialect: ANSI_SQL"), "a column name was exported as opaque SQL");
    }

    @Test
    @DisplayName("each DSL value type is the Ossie data type the guide says")
    public void eachValueTypeIsItsOssieType() throws IOException {
        String yaml = exportedModel();
        // catalog/model/reading.yml declares one field of every value type.
        String[][] expected = {
                {"label", "String"}, {"note", "String"}, {"raw_text", "String"}, {"payload", "Opaque"},
                {"read_count", "Integer"},
                {"total_count", "Integer"}, {"big_count", "Integer"}, {"units", "Integer"}, {"ratio", "Decimal"},
                {"exact_value", "Decimal"}, {"amount", "Decimal"}, {"score", "Float"}, {"weight", "Float"},
                {"active", "Boolean"}, {"read_date", "Date"}, {"read_time", "Time"}, {"local_at", "DateTime"},
                {"measured_at", "DateTimeTz"},
                {"image", "Opaque"}};
        String reading = yaml.substring(yaml.indexOf("  - name: Reading\n"));
        reading = reading.substring(0, reading.indexOf("\n  - name: ", 5));
        for (String[] e : expected) {
            Matcher m = Pattern.compile("- name: " + e[0] + "\\n(?:.*\\n)*?        datatype: (\\w+)").matcher(reading);
            assertTrue(m.find(), "no field " + e[0]);
            assertEquals(e[1], m.group(1), e[0]);
        }
    }

    @Test
    @DisplayName("an inherited column is in the child's table, and so in its dataset")
    public void inheritedColumnsAreExported() throws IOException {
        String yaml = exportedModel();
        String category = yaml.substring(yaml.indexOf("  - name: Category\n"));
        category = category.substring(0, category.indexOf("\n  - name: ", 5));
        assertTrue(category.contains("- name: created_by") && category.contains("- name: last_modified_date"),
                "AuditEntity's columns are missing from Category");
    }

    private int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) n++;
        return n;
    }

    /** The file the sample module generates, which is where the defects live. */
    private String exportedModel() throws IOException {
        File file = new File(repositoryRoot(),
                "elasticore-project/elasticore-template-ddd/src/main/resources/blueprint/"
                        + OssieSemanticModelExporter.FILE_NAME);
        assertTrue(file.isFile(),
                "the ddd sample has no exported semantic model - run elcore3 on it first");
        // A Windows checkout with core.autocrlf holds the committed file with CRLF.
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private Set<String> matches(String text, String pattern) {
        Set<String> out = new LinkedHashSet<String>();
        Matcher m = Pattern.compile(pattern).matcher(text);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private File repositoryRoot() {
        File dir = new File(OssieExportTest.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath().replace("%20", " "));
        while (dir != null && !new File(dir, "settings.gradle").isFile()) {
            dir = dir.getParentFile();
        }
        assertTrue(dir != null, "could not find the checkout root");
        return dir;
    }

    @Test
    @DisplayName("nothing is exported unless somebody asked")
    public void theExportIsOptIn() {
        // The empty list and the undeclared project both answer no. Answering
        // yes by default would put a new file in every 2.x project's blueprint
        // directory on upgrade - a changed output nobody opted into.
        assertEquals(false, OssieSemanticModelExporter.requested(null));
        assertEquals(false, OssieSemanticModelExporter.requested(
                new ArrayList<io.elasticore.base.model.ECoreModel>()));
    }
}
