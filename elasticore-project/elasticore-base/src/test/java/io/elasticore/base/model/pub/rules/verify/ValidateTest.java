package io.elasticore.base.model.pub.rules.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/** elcoreValidate: the DSL and the generated files checked for a CI step, nothing written. */
public class ValidateTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  VdItem:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      itemId: long @id @sequence",
            "      name: string(40) @search(like)",
            "");

    private static final List<File> projects = new ArrayList<File>();

    @AfterAll
    static void cleanUp() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        for (File project : projects) delete(project);
    }

    @Test
    public void anUpToDateProjectPassesAndAChangedDslFails() throws Exception {
        File project = project("elcore-validate", MODEL);
        String[] log = new String[1];
        assertEquals(0, run(project, false, false, log), log[0]);
        Map<String, String> before = tree(project);

        assertEquals(0, run(project, true, false, log), log[0]);
        assertTrue(report(project).contains("**Passed.**"), report(project));
        assertEquals(before, tree(project), "the validation wrote nothing");

        // A field added to the DSL and not generated: the entity is stale.
        model(
                project,
                MODEL.replace(
                        "      name: string(40) @search(like)",
                        "      name: string(40) @search(like)\n      note: string(80)"));
        assertEquals(1, run(project, true, false, log), log[0]);
        String report = report(project);
        assertTrue(report.contains("**Failed:**") && report.contains("generated file(s) not up to date"), report);
        assertTrue(report.contains("VdItem.java"), report);
        assertTrue(log[0].contains("[elcore3] validate (nothing was written): FAILED"), log[0]);
        assertTrue(log[0].contains("  stale: src/main/java/io/acme/shop/entity/VdItem.java"), log[0]);
        assertTrue(log[0].contains("=== elcore3:"), "the run's summary: " + log[0]);
        assertTrue(log[0].contains("[elcore3] the report: "), log[0]);
        assertTrue(report.contains("## Not up to date - run ./gradlew elcore3 and commit"), report);
        assertTrue(report.contains("(they fail only with -Pstrict)"), report);
        // The stale files in order, on the console as in the report.
        List<String> stale = new ArrayList<String>();
        for (String line : log[0].replace("\r", "").split("\n", -1)) {
            if (line.startsWith("  stale: ")) stale.add(line);
        }
        List<String> sorted = new ArrayList<String>(stale);
        java.util.Collections.sort(sorted);
        assertTrue(stale.size() > 1 && stale.equals(sorted), stale.toString());
        Map<String, String> after = tree(project);
        after.remove("src/main/resources/blueprint/shop/model/model.yml");
        before.remove("src/main/resources/blueprint/shop/model/model.yml");
        assertEquals(before, after, "a failed validation wrote nothing either");

        // Generated again, it passes; an entity taken out of the DSL leaves files no longer produced.
        assertEquals(0, run(project, false, false, log), log[0]);
        assertEquals(0, run(project, true, false, log), log[0]);
        model(project, MODEL.replace("  VdItem:", "  VdOther:").replace("itemId", "otherId"));
        assertEquals(1, run(project, true, false, log), log[0]);
        assertTrue(report(project).contains("no longer produces"), report(project));
        assertTrue(report(project).contains("## No longer produced by the DSL"), report(project));
        assertTrue(log[0].contains("  no longer produced: "), log[0]);
        assertTrue(log[0].contains("  - "), log[0]);
    }

    @Test
    public void aFileTheBuildTakesForYoursIsListedAsNotChecked() throws Exception {
        File project = project("elcore-validate-cache", MODEL);
        String[] log = new String[1];
        assertEquals(0, run(project, false, false, log), log[0]);
        // The cache not committed: a generated source changed since is the developer's to the build.
        delete(new File(project, ".elasticore"));
        model(
                project,
                MODEL.replace(
                        "      name: string(40) @search(like)",
                        "      name: string(40) @search(like)\n      note: string(80)"));
        assertEquals(0, run(project, true, false, log), log[0]);
        assertTrue(report(project).contains("## Not checked - no ElastiCORE header"), report(project));
        assertTrue(report(project).contains("VdItem.java"), report(project));
    }

    @Test
    public void theDdlIsReadOffTheSourcesTheRunWouldLeave() throws Exception {
        File project = project("elcore-validate-ddl", MODEL);
        String[] log = new String[1];
        ddl = true;
        try {
            // No source on disk yet: the tables are read off what the run would write - laid out and taken away.
            List<String> overlays = overlays();
            assertEquals(0, run(project, true, false, log), log[0]);
            assertEquals(overlays, overlays(), "the sources laid out for the DDL are gone");
            assertTrue(report(project).contains("**Passed.**"), report(project));
            assertTrue(find(project, "schema-postgresql.sql") == null, "nothing written");

            assertEquals(0, run(project, false, false, log), log[0]);
            assertTrue(find(project, "schema-postgresql.sql") != null, log[0]);
            assertEquals(0, run(project, true, false, log), log[0]);
            assertTrue(report(project).contains("**Passed.**"), report(project));

            model(
                    project,
                    MODEL.replace(
                            "      name: string(40) @search(like)",
                            "      name: string(40) @search(like)\n      note: string(80)"));
            assertEquals(1, run(project, true, false, log), log[0]);
            assertTrue(report(project).contains("schema-postgresql.sql"), report(project));
        } finally {
            ddl = false;
        }
    }

    private static boolean ddl;

    /** The temporary directories a validation lays its sources out in, as there now. */
    private static List<String> overlays() {
        List<String> out = new ArrayList<String>();
        String[] names = new File(System.getProperty("java.io.tmpdir")).list();
        if (names != null) {
            for (String n : names) if (n.matches("elcore-validate\\d+")) out.add(n);
        }
        java.util.Collections.sort(out);
        return out;
    }

    @Test
    public void anEditOfTheDevelopersPassesAndIsListed() throws Exception {
        File project = project("elcore-validate-edit", MODEL);
        String[] log = new String[1];
        assertEquals(0, run(project, false, false, log), log[0]);
        File service = find(project, "VdItemService.java");
        String text = new String(Files.readAllBytes(service.toPath()), StandardCharsets.UTF_8);
        int end = text.lastIndexOf('}');
        Files.write(
                service.toPath(),
                (text.substring(0, end) + "    public int answer() { return 42; }\n}\n")
                        .getBytes(StandardCharsets.UTF_8));
        assertEquals(0, run(project, true, false, log), log[0]);
        assertTrue(report(project).contains("Edited by you"), report(project));
        assertTrue(report(project).contains("VdItemService.java"), report(project));
    }

    @Test
    public void aModelThatDoesNotPassFails() throws Exception {
        File project = project("elcore-validate-bad", MODEL.replace("@search(like)", "@count(VdItem.itemId)"));
        String[] log = new String[1];
        assertEquals(1, run(project, true, false, log), log[0]);
        assertTrue(report(project).contains("## Model check failures"), report(project));
        assertFalse(find(project, "VdItem.java") != null, "nothing generated");
    }

    @Test
    public void aWarningFailsOnlyWhenStrict() throws Exception {
        File project = project("elcore-validate-warn", MODEL.replace("@search(like)", "@search(ilike)"));
        String[] log = new String[1];
        assertEquals(0, run(project, false, false, log), log[0]);
        assertEquals(0, run(project, true, false, log), log[0]);
        assertTrue(report(project).contains("DSL warnings: 1"), report(project));
        assertEquals(1, run(project, true, true, log), log[0]);
        assertTrue(report(project).contains("DSL warning(s) (-Pstrict)"), report(project));
        assertTrue(report(project).contains("(fail with -Pstrict)"), report(project));
    }

    @Test
    public void aProjectThatKeepsNoGeneratedFileIsCheckedForItsDsl() throws Exception {
        File project = project("elcore-validate-none", MODEL);
        String[] log = new String[1];
        assertEquals(0, run(project, true, false, log), log[0]);
        assertTrue(report(project).contains("keeps no generated source in its tree"), report(project));
        assertTrue(find(project, "VdItem.java") == null, "nothing written");
    }

    /** Properties of the next runs, beside the ones every run sets. */
    private static final Map<String, String> extra = new TreeMap<String, String>();

    /** A line more under env.yml's config, for the next project made. */
    private static String config = "";

    private static final String OWNED = "// Initially generated by ElastiCORE;";

    @Test
    public void aMigrationTheBuildWouldWriteFailsAndIsNotWritten() throws Exception {
        File project = project("elcore-validate-flyway", MODEL);
        String[] log = new String[1];
        ddl = true;
        extra.put("elasticore.ddl.migration", "flyway");
        try {
            assertEquals(0, run(project, false, false, log), log[0]);
            assertTrue(find(project, "schema-state.json") != null, log[0]);
            assertEquals(0, run(project, true, false, log), log[0]);
            assertTrue(report(project).contains("**Passed.**"), report(project));

            model(
                    project,
                    MODEL.replace(
                            "      name: string(40) @search(like)",
                            "      name: string(40) @search(like)\n      note: string(80)"));
            Map<String, String> before = tree(project);
            assertEquals(1, run(project, true, false, log), log[0]);
            String report = report(project);
            assertTrue(report.contains("1 migration(s) not written yet"), report);
            assertTrue(report.contains("## Migrations the build would write"), report);
            assertTrue(report.contains("schema-state.json"), report);
            assertTrue(
                    log[0].contains("    migration    src/main/resources/db/migration/schema-state.json  WROTE"),
                    log[0]);
            assertTrue(log[0].contains("    migration    src/main/resources/db/migration/postgresql/V"), log[0]);
            assertEquals(before, tree(project), "no migration and no state written");
        } finally {
            ddl = false;
            extra.clear();
        }
    }

    @Test
    public void withoutMergingTheOrmXmlAndTheMessagesAreCheckedAndNotWritten() throws Exception {
        config = "    mapping: xml";
        File project = project("elcore-validate-nomerge", MODEL);
        config = "";
        String[] log = new String[1];
        extra.put("elasticore.merge", "false");
        extra.put("elasticore.springBoot", "4");
        try {
            assertEquals(0, run(project, false, false, log), log[0]);
            File orm = new File(project, "src/main/resources/META-INF/orm.xml");
            assertTrue(orm.isFile(), log[0]);
            File messages = find(new File(project, "src/main/resources"), "shop-messages.properties");
            assertTrue(messages != null, log[0]);
            assertEquals(0, run(project, true, false, log), log[0]);

            model(
                    project,
                    MODEL.replace(
                            "      name: string(40) @search(like)",
                            "      name: string(40) @search(like)\n      note: string(80)"));
            assertTrue(messages.delete());
            Map<String, String> before = tree(project);
            assertEquals(1, run(project, true, false, log), log[0]);
            String report = report(project);
            assertTrue(report.contains("META-INF/orm.xml"), report);
            assertTrue(report.contains("shop-messages.properties"), report);
            assertEquals(before, tree(project), "nothing written");
        } finally {
            extra.clear();
        }
    }

    @Test
    public void committedSkeletonsAloneDoNotMakeTheGeneratedSourcesStale() throws Exception {
        File project = project("elcore-validate-skeletons", MODEL);
        String[] log = new String[1];
        assertEquals(0, run(project, false, false, log), log[0]);
        // A clone of a project that commits its skeletons and ignores the rest: no generated source, no cache.
        delete(new File(project, ".elasticore"));
        int kept = 0;
        for (File f : javaFiles(new File(project, "src/main/java"))) {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (text.contains(OWNED)) kept++;
            else Files.delete(f.toPath());
        }
        assertTrue(kept > 0, "a skeleton is kept");
        assertEquals(0, run(project, true, false, log), log[0]);
        assertTrue(report(project).contains("keeps no generated source in its tree"), report(project));
    }

    private static List<File> javaFiles(File dir) {
        List<File> out = new ArrayList<File>();
        File[] files = dir.listFiles();
        if (files == null) return out;
        for (File f : files) {
            if (f.isDirectory()) out.addAll(javaFiles(f));
            else if (f.getName().endsWith(".java")) out.add(f);
        }
        return out;
    }

    private static File project(String name, String model) throws IOException {
        File project = Files.createTempDirectory(name).toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/shop");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: shop",
                                "    framework: springboot",
                                "    j2ee: jakarta",
                                config,
                                "  namespace:",
                                "    base: io.acme.shop",
                                "    entity: io.acme.shop.entity",
                                "    dto: io.acme.shop.dto",
                                "    repository: io.acme.shop.repository",
                                "    service: io.acme.shop.service",
                                "    control: io.acme.shop.controller",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        model(project, model);
        return project;
    }

    private static void model(File project, String model) throws IOException {
        Files.write(
                new File(project, "src/main/resources/blueprint/shop/model/model.yml").toPath(),
                model.getBytes(StandardCharsets.UTF_8));
    }

    private static int run(File project, boolean validate, boolean strict, String[] log) throws Exception {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v3",
            "elasticore.rules.v2",
            "elasticore.rules.ts",
            "elasticore.validate",
            "elasticore.validate.strict",
            io.elasticore.base.model.pub.rules.schema.DdlConfig.DATABASES,
            "elasticore.ddl.migration",
            "elasticore.merge",
            "elasticore.springBoot"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        PrintStream out = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int exit;
        try {
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "layered");
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.v3", "true");
            if (validate) System.setProperty("elasticore.validate", "true");
            if (strict) System.setProperty("elasticore.validate.strict", "true");
            if (ddl) System.setProperty(io.elasticore.base.model.pub.rules.schema.DdlConfig.DATABASES, "postgresql");
            for (Map.Entry<String, String> en : extra.entrySet()) System.setProperty(en.getKey(), en.getValue());
            exit = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
            io.elasticore.base.model.pub.rules.ModelCheck.reset();
        }
        log[0] = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log[0]);
        return exit;
    }

    private static String report(File project) throws IOException {
        File f = new File(project, "build/elasticore/validate.md");
        assertTrue(f.isFile(), "no report");
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /** Every file of the project with its text. */
    private static Map<String, String> tree(File project) throws IOException {
        final Map<String, String> out = new TreeMap<String, String>();
        final Path root = project.toPath();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String path = root.relativize(file).toString().replace('\\', '/');
                // The report is the one file a validation writes.
                if (!path.equals("build/elasticore/validate.md")) {
                    out.put(path, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return out;
    }

    private static File find(File dir, String name) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isDirectory()) {
                File hit = find(f, name);
                if (hit != null) return hit;
            } else if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
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
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
