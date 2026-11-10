package io.elasticore.base.model.pub.ossie;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.elasticore.base.model.pub.rules.RuleCatalog;
import io.elasticore.base.model.pub.rules.verify.RulesGenRunner;
import io.elasticore.base.model.pub.rules.verify.SpecFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        // Core spec 0.3.0.dev0: one model, flat at the root - the semantic_model
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
        // The top-level sql is the portable form, in OSSIE_SQL_2026, the one
        // dialect the spec defines - so it is never named. ANSI_SQL names SQL
        // a consumer may ignore, and no field of this sample needs it.
        int expressions = count(yaml, "\n        expression:\n          sql: ");
        assertTrue(fields > 0 && fields == expressions, fields + " fields, " + expressions + " expressions");
        assertTrue(!yaml.contains("dialects:"), "an expression was exported as a dialect entry");
        assertTrue(!yaml.contains("OSSIE_SQL_2026"), "the portable form named as a dialect");
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

    // ---- a row filter: the rows of a table the application reads ------------------

    /**
     * A model with every way the DSL hands a row filter to an entity class, or,
     * with filters false, the same model with none of them.
     */
    private static String desk(boolean filters) {
        String where = filters ? " @jpa:Where(clause = \"delete_yn = 'N'\")" : "";
        return String.join(
                "\n",
                "entity:",
                "  Note:",
                "    meta: entity @root @expose(20)" + where + " -- Note",
                "    fields:",
                "      noteId: long @id @sequence",
                "      deleteYn: string(1)!",
                "      body: string(200)",
                "",
                "  Ticket:",
                "    meta: entity @root @expose(20)"
                        + (filters
                                ? " @jpa:org.hibernate.annotations.SQLRestriction(\"\\\"state\\\" not in ('X', 'Y')\")"
                                : ""),
                "    fields:",
                "      ticketId: long @id @sequence",
                "      state: string(1)!",
                "",
                "  Both:",
                "    meta: entity @root @expose(20)"
                        + (filters ? " @jpa:Where(clause = \"old = 1\") @jpa:SQLRestriction(value = \"new = 1\")" : ""),
                "    fields:",
                "      bothId: long @id @sequence",
                "",
                "  Mapped:",
                "    meta: entity @root @expose(20)" + (filters ? " @entity:Where(clause = \"kept = 1\")" : ""),
                "    fields:",
                "      mappedId: long @id @sequence",
                "",
                "  Odd:",
                "    meta: entity @root @expose(20)" + (filters ? " @jpa:Where(clause = Filters.LIVE)" : ""),
                "    fields:",
                "      oddId: long @id @sequence",
                "",
                "  Kept:",
                "    meta: entity @abstract" + where,
                "    fields:",
                "      deleteYn: string(1)",
                "",
                "  Child:",
                "    meta: entity @root @expose(20) @extend(Kept)",
                "    fields:",
                "      childId: long @id @sequence",
                "",
                "  Tag:",
                "    meta: entity @root @expose(20) -- Tag",
                "    fields:",
                "      tagId: long @id @sequence",
                "      name: string(50)",
                "",
                "metrics:",
                "  noteCount: count(Note) -- Notes",
                "  tagCount: count(Tag) -- Tags",
                "  notesPerTag: noteCount / tagCount",
                "");
    }

    @Test
    @DisplayName(
            "a class @jpa:Where is the dataset's row filter, in its extension and its description; source stays the table")
    public void aWhereIsTheDatasetsRowFilter() throws Exception {
        String[] filtered = export(desk(true));
        Map<String, Object> note = dataset(filtered[0], "Note");
        assertEquals("note", note.get("source"));
        assertEquals("Note (rows only where: delete_yn = 'N')", note.get("description"));
        assertEquals("delete_yn = 'N'", extension(note).get("row_filter"));
    }

    @Test
    @DisplayName("@SQLRestriction too, fully qualified, read with its quotes, and over @Where, as Hibernate reads them")
    public void anSqlRestrictionIsReadAsWrittenAndWins() throws Exception {
        String[] filtered = export(desk(true));
        Map<String, Object> ticket = dataset(filtered[0], "Ticket");
        assertEquals("(rows only where: \"state\" not in ('X', 'Y'))", ticket.get("description"));
        assertEquals("\"state\" not in ('X', 'Y')", extension(ticket).get("row_filter"));
        assertEquals("new = 1", extension(dataset(filtered[0], "Both")).get("row_filter"));
        // @entity: is the other prefix the generators write onto the class as given.
        assertEquals("kept = 1", extension(dataset(filtered[0], "Mapped")).get("row_filter"));
    }

    @Test
    @DisplayName(
            "a filter on an @abstract parent is said, not exported: whether Hibernate applies it is the generated code's")
    public void aParentsFilterIsSaidNotExported() throws Exception {
        String[] filtered = export(desk(true));
        String[] unfiltered = export(desk(false));
        assertFalse(extension(dataset(filtered[0], "Child")).containsKey("row_filter"), filtered[0]);
        assertTrue(filtered[1].contains("[ossie] Child: its parent Kept has a row filter on its class"), filtered[1]);
        assertFalse(unfiltered[1].contains("has a row filter on its class"), unfiltered[1]);
    }

    @Test
    @DisplayName("a clause that is not one string literal is said and left out")
    public void anUnreadableClauseIsSaidAndLeftOut() throws Exception {
        String[] filtered = export(desk(true));
        assertFalse(extension(dataset(filtered[0], "Odd")).containsKey("row_filter"), filtered[0]);
        assertTrue(
                filtered[1].contains("[ossie] Odd: @Where(clause = Filters.LIVE) is not one string literal"),
                filtered[1]);
    }

    @Test
    @DisplayName("a metric over a filtered dataset says so, also through another metric")
    public void aMetricOverAFilteredDatasetSaysSo() throws Exception {
        String[] filtered = export(desk(true));
        assertEquals(true, metricExtension(filtered[0], "noteCount").get("dataset_row_filter"));
        assertEquals(true, metricExtension(filtered[0], "notesPerTag").get("dataset_row_filter"));
        assertFalse(metricExtension(filtered[0], "tagCount").containsKey("dataset_row_filter"));
    }

    @Test
    @DisplayName("what no filter touches is exported byte for byte as without any")
    public void whatNoFilterTouchesIsExportedAsBefore() throws Exception {
        String with = export(desk(true))[0];
        String without = export(desk(false))[0];
        assertFalse(without.contains("row_filter") || without.contains("rows only where"), without);
        for (String name : new String[] {"Tag", "Child", "tagCount"}) {
            assertEquals(block(without, name), block(with, name), name);
        }
    }

    @Test
    @DisplayName("a field stamped by the lifecycle carries the state it is stamped on")
    public void aStampIsExported() throws Exception {
        String model = String.join(
                "\n",
                "enumeration:",
                "  Phase:",
                "    meta: enum @db(code) @json(code)",
                "    fields:",
                "      code: string(3) @code",
                "    enum:",
                "      OPEN: OPN",
                "      DONE: DON",
                "",
                "entity:",
                "  Job:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      jobId: long @id @sequence",
                "      phase: Phase",
                "      doneAt: timestamp @stamp(DONE)",
                "      openedAt: timestamp",
                "    states:",
                "      phase:",
                "        initial: OPEN",
                "        finish: OPEN -> DONE",
                "");
        String out = export(model)[0];
        assertTrue(out.contains("data: \"{\\\"stamp\\\": \\\"DONE\\\"}\""), out);
        int opened = out.indexOf("name: opened_at");
        assertTrue(
                opened > 0
                        && !out.substring(
                                        opened,
                                        out.indexOf("- name:", opened + 1) < 0
                                                ? out.length()
                                                : out.indexOf("- name:", opened + 1))
                                .contains("stamp"),
                out);
    }

    // ---- a schema: the source is the table it names, as @Table(schema=...) does ----

    /** Customer with those marks on its meta line; Tag with none. */
    private static String schemaDesk(String customerMarks) {
        return String.join(
                "\n",
                "entity:",
                "  Customer:",
                "    meta: entity @root @expose(20)" + customerMarks,
                "    fields:",
                "      customerId: long @id @sequence",
                "",
                "  Tag:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      tagId: long @id @sequence",
                "");
    }

    private static Object source(String yaml, String name) throws IOException {
        return dataset(yaml, name).get("source");
    }

    @Test
    @DisplayName("an entity @schema is the source's schema, joined by a dot")
    public void anEntitySchemaIsInTheSource() throws Exception {
        String out = export(schemaDesk(" @schema(schema_a)"))[0];
        assertEquals("schema_a.customer", source(out, "Customer"));
        assertEquals("tag", source(out, "Tag"));
    }

    @Test
    @DisplayName("the env.yml tableSchema is the schema of every entity of the domain")
    public void theDomainTableSchemaIsInTheSource() throws Exception {
        String out = export(schemaDesk(""), "tableSchema: schema_a")[0];
        assertEquals("schema_a.customer", source(out, "Customer"));
        assertEquals("schema_a.tag", source(out, "Tag"));
    }

    @Test
    @DisplayName("an entity @schema wins over the domain's tableSchema")
    public void anEntitySchemaWinsOverTheDomains() throws Exception {
        String out = export(schemaDesk(" @schema(sales)"), "tableSchema: schema_a")[0];
        assertEquals("sales.customer", source(out, "Customer"));
        assertEquals("schema_a.tag", source(out, "Tag"));
    }

    @Test
    @DisplayName("no schema anywhere: the table alone, and a dataset no schema touches is as before")
    public void noSchemaIsTheTableAlone() throws Exception {
        String without = export(schemaDesk(""))[0];
        String with = export(schemaDesk(" @schema(schema_a)"))[0];
        assertEquals("customer", source(without, "Customer"));
        assertEquals("tag", source(without, "Tag"));
        assertTrue(without.contains("\n    source: customer\n"), without);
        assertEquals(block(without, "Tag"), block(with, "Tag"));
    }

    @Test
    @DisplayName("a schema or a table with a dot in its name keeps the table alone: a dotted source cannot say it")
    public void aDottedNameKeepsTheTableAlone() throws Exception {
        assertEquals("customer", source(export(schemaDesk(" @schema(a.b)"))[0], "Customer"));
        assertEquals("tag", source(export(schemaDesk(""), "tableSchema: a.b")[0], "Tag"));
        assertEquals(
                "legacy.customer",
                source(export(schemaDesk(" @table(legacy.customer) @schema(sales)"))[0], "Customer"));
    }

    @Test
    @DisplayName("an explicit @table takes the schema and no prefix; a derived name takes both")
    public void anExplicitTableTakesTheSchema() throws Exception {
        String own = export(schemaDesk(" @table(cust_master) @schema(sales)"), "table:", "  prefix: tbl_")[0];
        assertEquals("sales.cust_master", source(own, "Customer"));
        assertEquals("tbl_tag", source(own, "Tag"));

        String domain =
                export(schemaDesk(" @table(cust_master)"), "tableSchema: schema_a", "table:", "  prefix: tbl_")[0];
        assertEquals("schema_a.cust_master", source(domain, "Customer"));
        assertEquals("schema_a.tbl_tag", source(domain, "Tag"));
    }

    /** The entry of datasets: or metrics: with that name, from its line to the next entry or section. */
    private static String block(String yaml, String name) {
        int start = yaml.indexOf("\n  - name: " + name + "\n");
        assertTrue(start >= 0, name + " is not in\n" + yaml);
        Matcher end = Pattern.compile("\n(?:  - name: |\\S)").matcher(yaml);
        return end.find(start + 1) ? yaml.substring(start, end.start()) : yaml.substring(start);
    }

    private static Map<String, Object> dataset(String yaml, String name) throws IOException {
        return named(yaml, "datasets", name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> named(String yaml, String section, String name) throws IOException {
        Map<String, Object> doc = new ObjectMapper(new YAMLFactory()).readValue(yaml, Map.class);
        for (Map<String, Object> each : (List<Map<String, Object>>) doc.get(section)) {
            if (name.equals(each.get("name"))) return each;
        }
        throw new AssertionError(name + " is not in " + section + ":\n" + yaml);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extension(Map<String, Object> entry) throws IOException {
        List<Map<String, Object>> extensions = (List<Map<String, Object>>) entry.get("custom_extensions");
        assertEquals(OssieSemanticModelExporter.VENDOR, extensions.get(0).get("vendor_name"));
        return new ObjectMapper().readValue((String) extensions.get(0).get("data"), Map.class);
    }

    private static Map<String, Object> metricExtension(String yaml, String name) throws IOException {
        return extension(named(yaml, "metrics", name));
    }

    /**
     * One generation of a one-domain project with that model: the export, and
     * what the generation said. Each test generates its own and none is kept:
     * diffMutation runs the tests again on each mutation in one JVM, and a kept
     * export would be the one generated before it.
     */
    private static String[] export(String model, String... config) throws Exception {
        File project = Files.createTempDirectory("elcore-ossie-filter").toFile();
        File dir = new File(project, "src/main/resources/blueprint/desk");
        assertTrue(new File(dir, "model").mkdirs());
        // Lines of env.yml's config:, below its fixed ones.
        StringBuilder extra = new StringBuilder();
        for (String line : config) extra.append("\n    ").append(line);
        String env = String.join(
                "\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: desk",
                "    framework: springboot",
                "    mode: jpa",
                "    j2ee: jakarta" + extra,
                "    publisher:",
                "      rules: [hexagonal-springboot, ossie-export]",
                "  namespace:",
                "    base: com.acme.desk",
                "");
        Files.write(new File(dir, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));

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
