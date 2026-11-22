package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated messages name a field, an entity and a state by its label
 * (issue 274): the default bundle's sentences take them as values, the
 * refusals pass them as the runtime's Label, and config: labelLanguage gives
 * the "--" labels a file of their own language.
 */
public class MessageLabelsGenerationTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Account:",
            "    meta: entity @root @expose(20) @invariant(name notNull) @label(en=Customer account) -- 거래처",
            "    fields:",
            "      accountId:     long @id @sequence",
            "      name:          string(30)! @label(en=Name) -- 이름",
            "      annualRevenue: int @min(0) @max(1000, 'at most 1000') -- 연 매출",
            "      employees:     int @min(0, 'no {negative} count')",
            "      stage:         Stage",
            "    states:",
            "      stage:",
            "        initial: OPEN",
            "        win:     OPEN -> WON",
            "        lose:    OPEN -> LOST",
            "        delete:  LOST",
            "enumeration:",
            "  Stage:",
            "    meta: enum @db(code) @json(code) -- 단계",
            "    fields:",
            "      code:     string(1) @code",
            "      label:    string(20)",
            "      label_en: string(20)",
            "    enum:",
            "      OPEN: O, 진행 중, Open",
            "      WON:  W, 수주, Won",
            "      LOST: L, 실주",
            "");

    private static final String PLAIN = String.join(
            "\n",
            "entity:",
            "  Item:",
            "    meta: entity @root @expose(20) -- 판매 단위",
            "    fields:",
            "      itemId:   long @id @sequence",
            "      unitCost: int! -- 단가",
            "");

    private static final String LABEL = "io.elasticore.springboot3.validation.Label";

    @Test
    public void theDefaultBundleSaysTheLabelsAsValues() throws Exception {
        File project = generate(MODEL, null, "hexagonal", "java");
        try {
            Properties bundle = bundle(project, "desk-messages.properties");
            assertEquals("{0} must be at least 0", bundle.getProperty("invariant.Account.annualRevenue.min"));
            // The DSL's own sentence is kept as written: no placeholder, no pattern.
            assertEquals("at most 1000", bundle.getProperty("invariant.Account.annualRevenue.max"));
            // A DSL sentence with a brace is kept as written too; the runtime says it so (RefusalLabelsTest).
            assertEquals("no {negative} count", bundle.getProperty("invariant.Account.employees.min"));
            assertEquals("{1} violates name notNull", bundle.getProperty("invariant.Account.name-notNull"));
            assertEquals(
                    "Account violates name notNull",
                    MessageFormat.format(bundle.getProperty("invariant.Account.name-notNull"), "name", "Account"));
            assertEquals(
                    "{3} cannot win when {2} is {4}; it must be {5}", bundle.getProperty("state.Account.stage.win"));
            assertEquals(
                    "{3} cannot be deleted when {2} is {4}; it must be {5}",
                    bundle.getProperty("state.Account.stage.delete"));
            String text = read(find(project, "desk-messages.properties"));
            assertTrue(text.contains("# invariant.*: {0} the field, {1} the entity"), text);
        } finally {
            delete(project);
        }
    }

    @Test
    public void theJavaRefusalsPassTheLabels() throws Exception {
        File project = generate(MODEL, null, "hexagonal", "java");
        try {
            String invariants = read(find(project, "AccountInvariants.java"));
            // The exception's own message - a log, a catch - is as before.
            assertTrue(
                    invariants.contains("InvariantViolationException(\"invariant.Account.annualRevenue.min\","
                            + " \"annualRevenue must be at least 0\", " + LABEL + ".of(\"Account.annualRevenue\"), "
                            + LABEL + ".of(\"Account\"));"),
                    invariants);
            assertTrue(
                    invariants.contains("InvariantViolationException(\"invariant.Account.employees.min\","
                            + " \"no {negative} count\", " + LABEL + ".of(\"Account.employees\"), "
                            + LABEL + ".of(\"Account\"));"),
                    invariants);
            assertTrue(
                    invariants.contains("InvariantViolationException(\"invariant.Account.name-notNull\","
                            + " \"Account violates name notNull\", " + LABEL + ".of(\"Account.name\"), "
                            + LABEL + ".of(\"Account\"));"),
                    invariants);

            String base = read(find(project, "AccountBase.java"));
            assertTrue(base.contains("String.valueOf(this.stage), java.util.Arrays.toString(from),"), base);
            assertTrue(
                    base.contains(LABEL + ".of(\"Account.stage\"), " + LABEL + ".of(\"Account\"), " + LABEL
                            + ".of(\"Stage\", this.stage), " + LABEL + ".listOf(\"Stage\", from));"),
                    base);
            assertTrue(base.contains("String.valueOf(this.stage), \"[LOST]\","), base);
            assertTrue(base.contains(LABEL + ".listOf(\"Stage\", Stage.LOST));"), base);
        } finally {
            delete(project);
        }
    }

    @Test
    public void theKotlinRefusalsPassTheLabels() throws Exception {
        File project = generate(MODEL, null, "ddd", "kotlin");
        try {
            String invariants = read(find(project, "AccountInvariants.kt"));
            assertTrue(
                    invariants.contains("InvariantViolationException(\"invariant.Account.annualRevenue.min\","
                            + " \"annualRevenue must be at least 0\", " + LABEL + ".of(\"Account.annualRevenue\"), "
                            + LABEL + ".of(\"Account\"))"),
                    invariants);
            String base = read(find(project, "AccountBase.kt"));
            assertTrue(base.contains("this.stage.toString(), from.joinToString(\" or \"),"), base);
            assertTrue(
                    base.contains(LABEL + ".of(\"Stage\", this.stage), " + LABEL + ".anyOf(\"Stage\", *from))"), base);
            assertTrue(base.contains(LABEL + ".listOf(\"Stage\", Stage.LOST))"), base);
        } finally {
            delete(project);
        }
    }

    /**
     * A model without any label: each default sentence, with the values the
     * runtime gives it where no label is found (the names, the states as the
     * codes beside them), reads exactly as the sentence before labels did -
     * "Account cannot win when stage is {0}; it must be {1}" with {0} {1} the
     * codes, the invariant sentences as written.
     */
    @Test
    public void withoutLabelsTheDefaultSentencesReadAsBefore() throws Exception {
        String unlabelled = MODEL.replace(" @label(en=Customer account) -- 거래처", "")
                .replace(" @label(en=Name) -- 이름", "")
                .replace(" -- 연 매출", "")
                .replace(" -- 단계", "")
                .replace("      label_en: string(20)\n", "")
                .replace(", Open\n", "\n")
                .replace(", Won\n", "\n");
        assertFalse(
                unlabelled.contains("@label") || unlabelled.contains("--") || unlabelled.contains("label_en"),
                unlabelled);
        File project = generate(unlabelled, null, "hexagonal", "java");
        try {
            Properties bundle = bundle(project, "desk-messages.properties");
            assertEquals(
                    MessageFormat.format("Account cannot win when stage is {0}; it must be {1}", "LOST", "[OPEN]"),
                    MessageFormat.format(
                            bundle.getProperty("state.Account.stage.win"),
                            "LOST",
                            "[OPEN]",
                            "stage",
                            "Account",
                            "LOST",
                            "[OPEN]"));
            assertEquals(
                    MessageFormat.format(
                            "Account cannot be deleted when stage is {0}; it must be {1}", "OPEN", "[LOST]"),
                    MessageFormat.format(
                            bundle.getProperty("state.Account.stage.delete"),
                            "OPEN",
                            "[LOST]",
                            "stage",
                            "Account",
                            "OPEN",
                            "[LOST]"));
            // Kotlin's code list is "OPEN or WON"; the label list beside it is framed the same.
            assertEquals(
                    MessageFormat.format("Account cannot lose when stage is {0}; it must be {1}", "WON", "OPEN"),
                    MessageFormat.format(
                            bundle.getProperty("state.Account.stage.lose"),
                            "WON",
                            "OPEN",
                            "stage",
                            "Account",
                            "WON",
                            "OPEN"));
            assertEquals(
                    "annualRevenue must be at least 0",
                    MessageFormat.format(
                            bundle.getProperty("invariant.Account.annualRevenue.min"), "annualRevenue", "Account"));
            assertEquals(
                    "Account violates name notNull",
                    MessageFormat.format(bundle.getProperty("invariant.Account.name-notNull"), "name", "Account"));
            assertNull(
                    find(project, "desk-labels.properties"), "no label, no label bundle: the runtime says the names");
        } finally {
            delete(project);
        }
    }

    /** A model without invariants and lifecycles: no refusal, no Label, the bundle as before. */
    @Test
    public void aModelWithoutRefusalsPassesNoLabel() throws Exception {
        File project = generate(PLAIN, null, "hexagonal", "java");
        try {
            assertFalse(anyFileContains(project, "validation.Label"), "a Label in a model without refusals");
            String text = read(find(project, "desk-messages.properties"));
            assertFalse(text.contains("invariant.*") || text.contains("state.*"), text);
            assertNull(find(project, "desk-labels.properties"));
        } finally {
            delete(project);
        }
    }

    /**
     * config: labelLanguage: ko - the "--" labels get a file of their own
     * language: every label, @label(ko=...) else the "--" text, and the
     * enumeration's label column where it has no _ko column. English keeps
     * only what is written in English.
     */
    @Test
    public void theLabelLanguageGetsAFileOfItsOwn() throws Exception {
        File project = generate(MODEL, "ko", "hexagonal", "java");
        try {
            Properties ko = bundle(project, "desk-labels_ko.properties");
            assertEquals("거래처", ko.getProperty("Account"));
            assertEquals("이름", ko.getProperty("Account.name"));
            assertEquals("연 매출", ko.getProperty("Account.annualRevenue"));
            assertEquals("진행 중", ko.getProperty("Stage.OPEN"));
            assertEquals("실주", ko.getProperty("Stage.LOST"));
            assertEquals("단계", ko.getProperty("Stage"));
            assertTrue(read(find(project, "desk-labels_ko.properties")).contains("(config: labelLanguage)"));

            Properties en = bundle(project, "desk-labels_en.properties");
            assertEquals("Customer account", en.getProperty("Account"));
            assertEquals("Name", en.getProperty("Account.name"));
            assertEquals("Open", en.getProperty("Stage.OPEN"));
            assertNull(en.getProperty("Account.annualRevenue"), "no English label: the runtime says the name");
            assertNull(en.getProperty("Stage.LOST"));

            Properties base = bundle(project, "desk-labels.properties");
            assertEquals("거래처", base.getProperty("Account"));
            assertEquals("진행 중", base.getProperty("Stage.OPEN"));
        } finally {
            delete(project);
        }
    }

    /** Without labelLanguage there is no file for the "--" labels' language: the runtime says the names there. */
    @Test
    public void withoutTheLabelLanguageTheDashLabelsHaveNoFileOfTheirOwn() throws Exception {
        File project = generate(MODEL, null, "hexagonal", "java");
        try {
            assertNull(find(project, "desk-labels_ko.properties"));
            assertTrue(find(project, "desk-labels_en.properties") != null);
        } finally {
            delete(project);
        }
    }

    /** A label language with no translation at all is reason enough to write the bundle; a tag is normalised. */
    @Test
    public void theLabelLanguageAloneWritesTheBundleInItsNormalForm() throws Exception {
        File project = generate(PLAIN, "ko_kr", "hexagonal", "java");
        try {
            assertEquals("단가", bundle(project, "desk-labels_ko_KR.properties").getProperty("Item.unitCost"));
            assertEquals("단가", bundle(project, "desk-labels.properties").getProperty("Item.unitCost"));
        } finally {
            delete(project);
        }
    }

    @Test
    public void aLabelLanguageThatIsNoLanguageIsSaidAndIgnored() throws Exception {
        File project = Files.createTempDirectory("elcore-msglabels").toFile();
        try {
            String log = generateInto(project, PLAIN, "korean", "hexagonal", "java");
            assertTrue(log.contains("config.labelLanguage 'korean' is not a language tag"), log);
            assertNull(find(project, "desk-labels.properties"), "nothing written for it");
        } finally {
            delete(project);
        }
    }

    private static boolean anyFileContains(File dir, String text) throws Exception {
        File[] children = dir.listFiles();
        if (children == null) return false;
        for (File c : children) {
            if (c.isDirectory()) {
                if (anyFileContains(c, text)) return true;
            } else if ((c.getName().endsWith(".java") || c.getName().endsWith(".kt")) && read(c).contains(text)) {
                return true;
            }
        }
        return false;
    }

    private static String read(File f) throws Exception {
        assertTrue(f != null, "file not written");
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static Properties bundle(File project, String name) throws Exception {
        File f = find(project, name);
        assertTrue(f != null, name + " was not written");
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            p.load(in);
        }
        return p;
    }

    private static File find(File dir, String name) {
        List<File> todo = new ArrayList<File>();
        todo.add(dir);
        while (!todo.isEmpty()) {
            File d = todo.remove(0);
            File[] children = d.listFiles();
            if (children == null) continue;
            for (File c : children) {
                if (c.isDirectory()) todo.add(c);
                else if (c.getName().equals(name)) return c;
            }
        }
        return null;
    }

    private static File generate(String model, String labelLanguage, String architecture, String language)
            throws Exception {
        File project = Files.createTempDirectory("elcore-msglabels").toFile();
        generateInto(project, model, labelLanguage, architecture, language);
        return project;
    }

    private static String generateInto(
            File project, String model, String labelLanguage, String architecture, String language) throws Exception {
        File dir = new File(project, "src/main/resources/blueprint/desk");
        assertTrue(new File(dir, "model").isDirectory() || new File(dir, "model").mkdirs());
        String env = String.join(
                "\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: desk",
                "    framework: springboot",
                "    mode: jpa",
                "    j2ee: jakarta",
                labelLanguage == null ? "" : "    labelLanguage: " + labelLanguage,
                "  namespace:",
                "    base: com.acme.desk",
                "");
        Files.write(new File(dir, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));

        String[] keys = {
            "elasticore.base.path", "elasticore.architecture", "elasticore.language", "elasticore.rules.force"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        int status;
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            status = RulesGenRunner.run(new String[0]);
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
        assertEquals(0, status, log);
        return log;
    }

    private static void delete(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) delete(c);
        assertTrue(f.delete() || !f.exists(), "could not delete " + f);
    }
}
