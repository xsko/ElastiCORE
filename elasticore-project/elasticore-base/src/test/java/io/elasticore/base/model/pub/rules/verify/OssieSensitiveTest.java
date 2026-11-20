package io.elasticore.base.model.pub.rules.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.elasticore.base.extract.ModelExtractor;
import io.elasticore.base.model.pub.ossie.OssieSemanticModelExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code @sensitive} (issue 184): a column the application reads, left out of
 * the Ossie export as a {@code @password} or {@code @writeonly} one is, so a
 * tool that writes its queries from the model is not offered it. Nothing else
 * reads the word: every other generated file is the same with it and without.
 */
public class OssieSensitiveTest {

    /** The model, with the @sensitive marks, or (marked false) the same model without them. */
    private static String model(boolean marked) {
        String s = marked ? " @sensitive" : "";
        return String.join(
                "\n",
                "entity:",
                "  Address:",
                "    meta: entity @embeddable",
                "    fields:",
                "      city: string(50)",
                "      street: string(100)" + s,
                "",
                "  Provider:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      providerId: long @id @sequence",
                "      name: string(50)",
                "      apiKey: string(100)" + s,
                "",
                "  Team:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      teamId: long @id @sequence",
                "      name: string(50)",
                "      office: Address @Embedded",
                "",
                "  Member:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      memberId: long @id @sequence" + s,
                "      email: string(100)!" + s + " @search(eq)",
                "      nickname: string(50)",
                "      score: int" + s,
                "      secret: string(100) @password",
                "      home: Address @Embedded" + s,
                "      provider: Provider @ManyToOne" + s,
                "      team: Team @ManyToOne",
                "      posts: List<Post> @OneToMany(mappedBy=\"member\")" + s,
                "",
                "  Post:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      postId: long @id @sequence",
                "      title: string(100)",
                "      member: Member @ManyToOne",
                "",
                "metrics:",
                "  members: count(Member)",
                "  topScore: max(Member.score)",
                "");
    }

    private final List<File> projects = new ArrayList<File>();

    @Test
    @DisplayName("a field marked @sensitive is left out of its dataset and listed as omitted, as a password is")
    public void aSensitiveFieldIsLeftOut() throws Exception {
        String[] out = export(model(true));
        Map<String, Object> member = dataset(out[0], "Member");
        List<String> fields = fieldNames(member);
        assertFalse(fields.contains("email"), out[0]);
        assertFalse(fields.contains("score"), out[0]);
        assertTrue(fields.contains("nickname"), out[0]);
        List<?> omitted = (List<?>) extension(member).get("omitted_columns");
        assertTrue(
                omitted.contains("email") && omitted.contains("score") && omitted.contains("secret"),
                omitted.toString());

        // In another entity too, and a value field of an embeddable wherever the value is embedded.
        assertFalse(fieldNames(dataset(out[0], "Provider")).contains("api_key"), out[0]);
        List<String> team = fieldNames(dataset(out[0], "Team"));
        assertTrue(team.contains("city"), team.toString());
        assertFalse(team.contains("street"), team.toString());
        assertEquals(
                java.util.Arrays.asList("street"),
                extension(dataset(out[0], "Team")).get("omitted_columns"));

        // The model's instructions say it, only where something was marked.
        assertTrue(out[0].contains("So are the columns the model marks sensitive."), out[0]);
    }

    @Test
    @DisplayName(
            "a reference that joins on a @sensitive field's column offers it neither as a field nor a relationship")
    public void aJoinOnASensitiveColumnIsNotExported() throws Exception {
        String[] out = export(String.join(
                "\n",
                "entity:",
                "  Customer:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      customerId: long @id @sequence",
                "      name: string(50)",
                "",
                "  Purchase:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      purchaseId: long @id @sequence",
                "      customerId: long @sensitive @db(cust_ref)",
                "      customer: Customer @ManyToOne @joinBy(customerId)",
                "",
                "  Ticket:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      ticketId: long @id @sequence",
                "      customerId: long @sensitive",
                "      customer: Customer @ManyToOne",
                "",
                "  Visit:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      visitId: long @id @sequence",
                "      customer: Customer @ManyToOne",
                ""));
        String yaml = out[0];
        // @joinBy onto the marked field: its column is not written back as the join's field.
        Map<String, Object> purchase = dataset(yaml, "Purchase");
        assertFalse(fieldNames(purchase).contains("cust_ref"), yaml);
        assertTrue(((List<?>) extension(purchase).get("omitted_columns")).contains("cust_ref"), yaml);
        assertFalse(yaml.contains("name: Purchase_customer\n"), yaml);
        // The reference held by a marked identity field: no relationship on the hidden column.
        assertFalse(fieldNames(dataset(yaml, "Ticket")).contains("customer_id"), yaml);
        assertFalse(yaml.contains("name: Ticket_customer\n"), yaml);
        assertTrue(out[1].contains("[ossie] Ticket.customer joins on customer_id, which is @sensitive"), out[1]);
        // An unmarked reference beside them is exported as before.
        assertTrue(yaml.contains("name: Visit_customer\n"), yaml);
    }

    @Test
    @DisplayName("a field marked in an embeddable alone is enough for the instructions to say so")
    public void aMarkInAnEmbeddableIsSaid() throws Exception {
        String yaml = export(String.join(
                "\n",
                "entity:",
                "  Address:",
                "    meta: entity @embeddable",
                "    fields:",
                "      city: string(50)",
                "      street: string(100) @sensitive",
                "",
                "  Team:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      teamId: long @id @sequence",
                "      office: Address @Embedded",
                ""))[0];
        assertFalse(fieldNames(dataset(yaml, "Team")).contains("street"), yaml);
        assertTrue(yaml.contains("So are the columns the model marks sensitive."), yaml);
    }

    @Test
    @DisplayName("an embedded value marked @sensitive leaves out every column it is stored in")
    public void aSensitiveValueLeavesOutItsColumns() throws Exception {
        String yaml = export(model(true))[0];
        List<String> fields = fieldNames(dataset(yaml, "Member"));
        assertFalse(fields.contains("city"), fields.toString());
        assertFalse(fields.contains("street"), fields.toString());
        List<?> omitted = (List<?>) extension(dataset(yaml, "Member")).get("omitted_columns");
        assertTrue(omitted.contains("city") && omitted.contains("street"), omitted.toString());
    }

    @Test
    @DisplayName("a reference marked @sensitive leaves out its join column and its relationship; another stays")
    public void aSensitiveReferenceLeavesOutItsJoin() throws Exception {
        String yaml = export(model(true))[0];
        List<String> fields = fieldNames(dataset(yaml, "Member"));
        assertFalse(fields.contains("provider_provider_id"), fields.toString());
        assertTrue(fields.contains("team_team_id"), fields.toString());
        List<?> omitted = (List<?>) extension(dataset(yaml, "Member")).get("omitted_columns");
        assertTrue(omitted.contains("provider_provider_id"), omitted.toString());
        assertFalse(yaml.contains("name: Member_provider\n"), yaml);
        assertTrue(yaml.contains("name: Member_team\n"), yaml);
        // The reference to Member from Post is not touched by what Member marks.
        assertTrue(yaml.contains("name: Post_member\n"), yaml);
    }

    @Test
    @DisplayName("a key marked @sensitive is exported all the same, and said; so is a collection")
    public void aSensitiveKeyIsSaidAndExported() throws Exception {
        String[] out = export(model(true));
        Map<String, Object> member = dataset(out[0], "Member");
        assertEquals(java.util.Arrays.asList("member_id"), member.get("primary_key"));
        assertTrue(fieldNames(member).contains("member_id"), out[0]);
        assertTrue(out[1].contains("[ossie] Member.memberId: @sensitive on a key is ignored"), out[1]);
        assertTrue(out[1].contains("[ossie] Member.posts: @sensitive on a collection is ignored"), out[1]);
        // The relationship to Member's key still stands.
        assertTrue(out[0].contains("to_columns: [member_id]"), out[0]);
    }

    @Test
    @DisplayName("a metric over a @sensitive field is refused, as one over a column the dataset lacks")
    public void aMetricOverASensitiveFieldIsRefused() throws Exception {
        String[] out = export(model(true));
        assertTrue(out[1].contains("topScore") && out[1].contains("Member has no column for score"), out[1]);
        assertFalse(out[0].contains("name: topScore"), out[0]);
        assertTrue(out[0].contains("name: members"), out[0]);

        String[] plain = export(model(false));
        assertTrue(plain[0].contains("name: topScore"), plain[0]);
    }

    /**
     * Unique keys over columns the export leaves out (issue 245), or (marked false) the same
     * model without the marks: @sensitive on a field, on an embeddable's field, @password and
     * @writeonly, each in a key of its own field or of an @Index, beside keys that hold none.
     */
    private static String keysModel(boolean marked) {
        String s = marked ? " @sensitive" : "";
        return String.join(
                "\n",
                "entity:",
                "  Address:",
                "    meta: entity @embeddable",
                "    fields:",
                "      city: string(50)",
                "      street: string(100)" + s,
                "",
                "  Customer:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      customerId: long @id @sequence",
                "      email: string(100)! @unique" + s,
                "      code: string(20)! @unique",
                "      name: string(50)!",
                "",
                "  SocialAccount:",
                "    meta: entity @root @expose(20)"
                        + " @Index(name=\"uk_social_account\", columnList=\"provider,provider_user_id\", unique=\"true\")",
                "          @Index(name=\"uk_social_handle\", columnList=\"provider,handle\", unique=\"true\")",
                "    fields:",
                "      socialAccountId: long @id @sequence",
                "      provider: string(20)!",
                "      providerUserId: string(100)!" + s,
                "      handle: string(50)!",
                "",
                "  Credential:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      credentialId: long @id @sequence",
                "      loginName: string(50)! @unique",
                "      secret: string(100)! @unique" + (marked ? " @password" : ""),
                "      token: string(100) @unique" + (marked ? " @writeonly" : ""),
                "",
                "  Team:",
                "    meta: entity @root @expose(20)"
                        + " @Index(name=\"uk_team_office\", columnList=\"street,city\", unique=\"true\")",
                "    fields:",
                "      teamId: long @id @sequence",
                "      office: Address @Embedded",
                "",
                // The column in capitals by @column, the @Index name read as Hibernate reads it (badge_serial).
                "  Badge:",
                "    meta: entity @embeddable",
                "    fields:",
                "      serial: string(40) @column(BADGE_SERIAL)" + s,
                "",
                "  Guard:",
                "    meta: entity @root @expose(20)"
                        + " @Index(name=\"uk_guard_badge\", columnList=\"BADGE_SERIAL\", unique=\"true\")",
                "    fields:",
                "      guardId: long @id @sequence",
                "      badge: Badge @Embedded",
                "");
    }

    @Test
    @DisplayName("a unique key that holds a column the export leaves out is left out as a whole; the others stay")
    public void aUniqueKeyOverALeftOutColumnIsNotExported() throws Exception {
        String[] out = export(keysModel(true));
        String yaml = out[0];
        // @unique on a @sensitive field: its key goes, the other @unique key of the dataset stays.
        assertEquals(keys(new String[] {"code"}), dataset(yaml, "Customer").get("unique_keys"), yaml);
        assertTrue(((List<?>) extension(dataset(yaml, "Customer")).get("omitted_columns")).contains("email"), yaml);
        // An @Index key with a @sensitive column goes as a whole - [provider] alone is not unique.
        assertEquals(
                keys(new String[] {"provider", "handle"}),
                dataset(yaml, "SocialAccount").get("unique_keys"),
                yaml);
        // @password and @writeonly columns are left out of the fields; so are their keys.
        assertEquals(
                keys(new String[] {"login_name"}), dataset(yaml, "Credential").get("unique_keys"), yaml);
        // A key over an embedded value's @sensitive column.
        assertNull(dataset(yaml, "Team").get("unique_keys"), yaml);
        // The key's name and the left-out column differ in case only: the same column.
        assertTrue(((List<?>) extension(dataset(yaml, "Guard")).get("omitted_columns")).contains("BADGE_SERIAL"), yaml);
        assertNull(dataset(yaml, "Guard").get("unique_keys"), yaml);
        // Every name left in unique_keys is a field of its dataset.
        for (String name : new String[] {"Customer", "SocialAccount", "Credential", "Team", "Guard"}) {
            Map<String, Object> d = dataset(yaml, name);
            @SuppressWarnings("unchecked")
            List<List<String>> unique = (List<List<String>>) d.get("unique_keys");
            if (unique == null) continue;
            for (List<String> key : unique) assertTrue(fieldNames(d).containsAll(key), name + " " + key + "\n" + yaml);
        }
        assertTrue(
                out[1].contains("[ossie] Customer: the unique key [email] is not exported - it holds email,"), out[1]);
    }

    @Test
    @DisplayName("without the marks, every unique key is exported as before")
    public void withoutTheMarksEveryUniqueKeyIsExported() throws Exception {
        String[] out = export(keysModel(false));
        String yaml = out[0];
        assertEquals(
                keys(new String[] {"email"}, new String[] {"code"}),
                dataset(yaml, "Customer").get("unique_keys"));
        assertEquals(
                keys(new String[] {"provider", "provider_user_id"}, new String[] {"provider", "handle"}),
                dataset(yaml, "SocialAccount").get("unique_keys"));
        assertEquals(
                keys(new String[] {"login_name"}, new String[] {"secret"}, new String[] {"token"}),
                dataset(yaml, "Credential").get("unique_keys"));
        assertEquals(
                keys(new String[] {"street", "city"}), dataset(yaml, "Team").get("unique_keys"));
        assertEquals(keys(new String[] {"badge_serial"}), dataset(yaml, "Guard").get("unique_keys"));
        assertFalse(out[1].contains("unique key"), out[1]);
    }

    private static List<List<String>> keys(String[]... keys) {
        List<List<String>> out = new ArrayList<List<String>>();
        for (String[] k : keys) out.add(java.util.Arrays.asList(k));
        return out;
    }

    @Test
    @DisplayName("without the word, nothing is left out and the instructions are as before")
    public void withoutTheWordTheExportIsAsBefore() throws Exception {
        String[] out = export(model(false));
        List<String> fields = fieldNames(dataset(out[0], "Member"));
        for (String column : new String[] {"email", "score", "city", "street", "provider_provider_id"}) {
            assertTrue(fields.contains(column), column + " in " + fields);
        }
        assertEquals(
                java.util.Arrays.asList("secret"),
                extension(dataset(out[0], "Member")).get("omitted_columns"));
        assertNull(extension(dataset(out[0], "Team")).get("omitted_columns"));
        assertFalse(out[0].contains("sensitive"), out[0]);
        assertFalse(out[1].contains("@sensitive"), out[1]);
    }

    @Test
    @DisplayName("every other generated file is the same with @sensitive and without: v3 Java, Kotlin, TypeScript")
    public void nothingElseChangesInV3() throws Exception {
        String[][] runs = {
            {"hexagonal", "java", null}, {"layered", "kotlin", null}, {"layered", "java", "elasticore.rules.ts"}
        };
        for (String[] r : runs) {
            File with = project(model(true));
            File without = project(model(false));
            assertEquals(0, run(with, r[0], r[1], r[2]));
            assertEquals(0, run(without, r[0], r[1], r[2]));
            assertSameOutput(without, with, r[0] + "/" + r[1] + (r[2] != null ? "/ts" : ""));
        }
    }

    @Test
    @DisplayName("the 2.x generator writes the same code with @sensitive and without; its export leaves the field out")
    public void nothingElseChangesIn2x() throws Exception {
        File with = project(model(true));
        File without = project(model(false));
        extract(with);
        extract(without);
        assertSameOutput(without, with, "2.x");
        String yaml = read(new File(with, "src/main/resources/blueprint/" + OssieSemanticModelExporter.FILE_NAME));
        assertFalse(fieldNames(dataset(yaml, "Member")).contains("email"), yaml);
    }

    // ---- the harness ----------------------------------------------------------------

    private static void assertSameOutput(File expected, File actual, String what) throws IOException {
        Map<String, String> a = files(expected);
        Map<String, String> b = files(actual);
        assertTrue(a.size() > 10, what + ": too little was generated to compare: " + a.keySet());
        assertEquals(a.keySet(), b.keySet(), what);
        List<String> differ = new ArrayList<String>();
        for (Map.Entry<String, String> e : a.entrySet()) {
            if (!e.getValue().equals(b.get(e.getKey())))
                differ.add(e.getKey() + firstDifference(e.getValue(), b.get(e.getKey())));
        }
        assertTrue(differ.isEmpty(), what + ": @sensitive changed " + differ);
    }

    /** The first line two texts differ in, both ways: what an assertion message needs to be read. */
    private static String firstDifference(String a, String b) {
        String[] x = a.split("\n", -1);
        String[] y = b.split("\n", -1);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            String l = i < x.length ? x[i] : "";
            String r = i < y.length ? y[i] : "";
            if (!l.equals(r)) return " (line " + (i + 1) + ": [" + l + "] / [" + r + "])";
        }
        return "";
    }

    /** Every generated file but the blueprint (the model itself and the export), by its path in the project. */
    private static Map<String, String> files(File project) throws IOException {
        final Map<String, String> out = new TreeMap<String, String>();
        final Path root = project.toPath();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = root.relativize(file).toString().replace('\\', '/');
                // build/ holds the generator's own records (a hash of the model files), not generated code.
                if (!rel.contains("/blueprint/") && !rel.startsWith(".elasticore/") && !rel.startsWith("build/")) {
                    // The 2.x stamp keeps its content hash and drops the time it was written at.
                    String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                    out.put(rel, text.replaceAll("(//ecd:-?\\d+H)\\d+", "$1"));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return out;
    }

    private static String read(File f) throws IOException {
        assertTrue(f.isFile(), f + " was not written");
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataset(String yaml, String name) throws IOException {
        Map<String, Object> doc = new ObjectMapper(new YAMLFactory()).readValue(yaml, Map.class);
        for (Map<String, Object> each : (List<Map<String, Object>>) doc.get("datasets")) {
            if (name.equals(each.get("name"))) return each;
        }
        throw new AssertionError(name + " is not a dataset of\n" + yaml);
    }

    @SuppressWarnings("unchecked")
    private static List<String> fieldNames(Map<String, Object> dataset) {
        List<String> names = new ArrayList<String>();
        for (Map<String, Object> f : (List<Map<String, Object>>) dataset.get("fields"))
            names.add((String) f.get("name"));
        return names;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extension(Map<String, Object> entry) {
        List<Map<String, Object>> extensions = (List<Map<String, Object>>) entry.get("nonportable_extensions");
        return (Map<String, Object>) extensions.get(0).get("data");
    }

    private File project(String model) throws IOException {
        File project = Files.createTempDirectory("elcore-sensitive").toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/club");
        assertTrue(new File(dir, "model").mkdirs());
        String env = String.join(
                "\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: club",
                "    framework: springboot",
                "    mode: jpa",
                "    j2ee: jakarta",
                "    publisher:",
                "      rules: [ossie-export]",
                "  namespace:",
                "    entity: com.acme.club.entity",
                "    dto: com.acme.club.dto",
                "    repository: com.acme.club.repository",
                "    service: com.acme.club.service",
                "    control: com.acme.club.controller",
                "");
        Files.write(new File(dir, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    /** One v3 generation of a fresh project with that model, with the export: the export and the log. */
    private String[] export(String model) throws Exception {
        File project = project(model);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        int code;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        try {
            code = run(project, "hexagonal", "java", null);
        } finally {
            System.setOut(out);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        assertEquals(0, code, log);
        String yaml = read(new File(project, "src/main/resources/blueprint/" + OssieSemanticModelExporter.FILE_NAME));
        return new String[] {yaml, log};
    }

    private static int run(File project, String architecture, String language, String output) {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v3",
            "elasticore.rules.v2",
            "elasticore.rules.ts",
            "elasticore.publisher.rules"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            if (output != null) System.setProperty(output, "true");
            return RulesGenRunner.run(new String[0]);
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
    }

    /** The 2.x generator, in this JVM. */
    private static void extract(File project) throws Exception {
        String basePath = System.getProperty("elasticore.base.path");
        System.setProperty("elasticore.base.path", project.getAbsolutePath());
        Object[] context = saveContext();
        try {
            new ModelExtractor(new File(project, "src/main/resources/blueprint").getAbsolutePath())
                    .extract(new io.elasticore.base.extract.FileBasedSourceFileAccessFactory(
                            new File(project, "src/main/java").getAbsolutePath()));
        } finally {
            restoreContext(context);
            if (basePath == null) System.clearProperty("elasticore.base.path");
            else System.setProperty("elasticore.base.path", basePath);
        }
    }

    private static final String[] CONTEXT = {"STATIC_DOMAIN_MAP", "context", "tmpCtx"};

    private static Object[] saveContext() throws Exception {
        Object[] saved = new Object[CONTEXT.length];
        for (int i = 0; i < CONTEXT.length; i++) {
            java.lang.reflect.Field f =
                    io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField(CONTEXT[i]);
            f.setAccessible(true);
            Object value = f.get(null);
            saved[i] = value instanceof java.util.Map
                    ? new java.util.HashMap<Object, Object>((java.util.Map<?, ?>) value)
                    : value;
        }
        return saved;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void restoreContext(Object[] saved) throws Exception {
        for (int i = 0; i < CONTEXT.length; i++) {
            java.lang.reflect.Field f =
                    io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField(CONTEXT[i]);
            f.setAccessible(true);
            if (saved[i] instanceof java.util.Map) {
                java.util.Map map = (java.util.Map) f.get(null);
                map.clear();
                map.putAll((java.util.Map) saved[i]);
            } else {
                f.set(null, saved[i]);
            }
        }
    }

    @AfterEach
    void clean() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        io.elasticore.base.model.pub.rules.ModelCheck.reset();
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
        for (File project : projects) delete(project);
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
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
