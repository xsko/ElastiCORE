package io.elasticore.base.model.pub.rules.verify;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue 247: the warning for a rule the DSL states and the selected rules do
 * not carry gives the advice that fits where the selection came from. A list
 * in env.yml replaces the architecture's bundle, so "declare an architecture"
 * does nothing for it; the 2.x code takes neither an architecture nor an added
 * rule set; a layered bundle is extended by the domain-model ones.
 */
public class UnenforcedWarningTest {

    private static final String ACCESS = "@access, @owner and @password (jpa-service-access)";

    /** The domain-model bundles of a Java domain over JPA or MyBatis. */
    private static final String JAVA = "hexagonal-springboot or ddd-springboot";

    @Test
    @DisplayName("a list in env.yml: it replaces the bundle; the adding form, or the set named in the list")
    public void aListInEnvYmlIsSaidToReplaceTheBundle() {
        String w = RulesGenRunner.unenforcedWarning(
                "club", "Member", ACCESS, "jpa-service-access", "publisher.rules", null, JAVA);
        assertTrue(
                w.startsWith("[club] Member declares @access, @owner and @password (jpa-service-access), which the"
                        + " selected rules do not enforce - nothing is checked, derived or hashed. "),
                w);
        assertTrue(
                w.endsWith("publisher.rules in env.yml replaces the architecture's bundle rather than adding to it,"
                        + " and its list does not name jpa-service-access. To add rule sets to the bundle, leave"
                        + " publisher.rules out and name them in rules = [...] in the elasticore { } block"
                        + " (-Delasticore.publisher.rules.add=...); to keep the list, name jpa-service-access in it."),
                w);
        assertFalse(w.contains("Declare architecture"), w);

        // Kotlin's own key, and a lifecycle, which needs the domain-model rule sets beside it.
        String kt = RulesGenRunner.unenforcedWarning(
                "club",
                "Order",
                "states: (ddd-state)",
                "ddd-state",
                "publisher.rules.kt",
                null,
                "hexagonal-springboot-kt or ddd-springboot-kt");
        assertTrue(kt.contains("publisher.rules.kt in env.yml replaces the architecture's bundle"), kt);
        assertTrue(kt.contains("leave publisher.rules.kt out"), kt);
        assertTrue(
                kt.endsWith("to keep the list, name a bundle that carries ddd-state with the domain-model rule sets it"
                        + " works on (hexagonal-springboot-kt or ddd-springboot-kt) in it."),
                kt);
    }

    @Test
    @DisplayName("the build's -Delasticore.publisher.rules replaces the bundle as a list in env.yml does")
    public void theBuildsPropertyIsSaidToReplaceTheBundle() {
        String w = RulesGenRunner.unenforcedWarning(
                "club", "Member", ACCESS, "jpa-service-access", "elasticore.publisher.rules", null, JAVA);
        assertTrue(
                w.endsWith("The build's -Delasticore.publisher.rules replaces the architecture's bundle rather than"
                        + " adding to it, and its list does not name jpa-service-access. To add rule sets to the"
                        + " bundle, leave -Delasticore.publisher.rules out and name them in rules = [...] in the"
                        + " elasticore { } block (-Delasticore.publisher.rules.add=...); to keep the list, name"
                        + " jpa-service-access in it."),
                w);
        assertFalse(w.contains("env.yml"), w);
    }

    @Test
    @DisplayName("a list naming jpa-springboot: take it out of the list for the 3.0 code")
    public void aListNamingTheTwoXBundleIsToldToTakeItOut() {
        String w = RulesGenRunner.unenforcedWarning(
                "club", "Member", ACCESS, "jpa-service-access", "publisher.rules", "jpa-springboot", JAVA);
        assertTrue(
                w.endsWith("publisher.rules in env.yml names jpa-springboot, the code 2.x generates, which has no"
                        + " jpa-service-access; the 3.0 code carries it: take jpa-springboot out of the list (leave"
                        + " the list out for the architecture's bundle), and output = 'v2' out of the elasticore { }"
                        + " block if it is there - see elasticore-migration-guide.md."),
                w);
        String states = RulesGenRunner.unenforcedWarning(
                "club", "Order", "states: (ddd-state)", "ddd-state", "publisher.rules", "jpa-springboot", JAVA);
        assertTrue(states.contains("if it is there, and declare architecture = 'ddd' or 'hexagonal' - see"), states);
        assertTrue(RulesGenRunner.namesTwoXBundle(java.util.Arrays.asList("ossie-export", " jpa-springboot ")));
        assertFalse(RulesGenRunner.namesTwoXBundle(java.util.Arrays.asList("jpa-springboot-v3", "ossie-export")));
    }

    @Test
    @DisplayName("MongoDB: no architecture to declare - the lifecycle is written for JPA and MyBatis")
    public void mongoIsNotSentToAnArchitecture() {
        for (String listKey : new String[] {null, "publisher.rules"}) {
            String w = RulesGenRunner.unenforcedWarning(
                    "club",
                    "Order",
                    "states: (ddd-state)",
                    "ddd-state",
                    listKey,
                    listKey == null ? "mongo-springboot" : null,
                    null);
            assertTrue(
                    w.endsWith("This domain's mode has no domain-model bundle: ddd-state and the domain-model rule sets"
                            + " it works on are generated for JPA and MyBatis (architecture = 'ddd' or 'hexagonal')."),
                    w);
        }
        // What MongoDB does carry is still named as for any other domain.
        String access = RulesGenRunner.unenforcedWarning(
                "club", "Member", ACCESS, "jpa-service-access", "publisher.rules", null, null);
        assertTrue(access.endsWith("to keep the list, name jpa-service-access in it."), access);
    }

    @Test
    @DisplayName("a layered bundle: it is named with the set it lacks; declare the architecture or add the set")
    public void aLayeredBundleIsNamedWithTheSetItLacks() {
        String w = RulesGenRunner.unenforcedWarning(
                "club", "Line", "@snapshot and @compute (ddd-derive)", "ddd-derive", null, "jpa-springboot-v3", JAVA);
        assertTrue(
                w.endsWith("nothing is checked, derived or hashed. The jpa-springboot-v3 bundle does not carry"
                        + " ddd-derive. Declare architecture = 'ddd' or 'hexagonal', or add the rule set."),
                w);
        assertFalse(w.contains("env.yml"), w);
    }

    /** Issue 320: added to a layered bundle, ddd-state sets the initial state alone - it is not offered as a way. */
    @Test
    @DisplayName("a layered bundle and a lifecycle: adding ddd-state is said to set the initial state only")
    public void aLayeredBundleIsNotSentToAddTheLifecycle() {
        String w = RulesGenRunner.unenforcedWarning(
                "stshop", "StDoc", "@stamp (ddd-state)", "ddd-state", null, "jpa-springboot-v3", JAVA);
        assertTrue(
                w.endsWith("nothing is checked, derived or hashed. The jpa-springboot-v3 bundle does not carry"
                        + " ddd-state, and adding it to this bundle sets the initial state only: the commands, the moves"
                        + " they check, @stamp and the delete: guard are written for a root with a behaviour class - a"
                        + " root that is not @editable, with architecture = 'ddd' or 'hexagonal'."),
                w);
        assertFalse(w.contains("or add the rule set"), w);
    }

    /** What the warning still offers for a layered bundle - adding the set - does the job. */
    @Test
    @DisplayName("layered with ddd-derive added: the value is worked out and nothing is said")
    public void addingDddDeriveToALayeredBundleWorks() throws Exception {
        File added = project(null, String.join("\n", "entity:", MORE));
        String log = generate(added, null, "ddd-derive");
        assertFalse(log.contains("ddd-derive"), log);
        assertTrue(
                anySourceContains(added, "quantity * price")
                        || anySourceContains(added, "getQuantity() * getPrice()")
                        || anySourceContains(added, "this.quantity * this.price"),
                "no derived value was written");
    }

    @Test
    @DisplayName("the 2.x code: neither an architecture nor an added set - the 3.0 code carries it")
    public void theTwoXCodeIsSentToTheThreeOCode() {
        String w = RulesGenRunner.unenforcedWarning(
                "club", "Member", ACCESS, "jpa-service-access", null, "jpa-springboot", JAVA);
        assertTrue(
                w.endsWith(
                        "The code 2.x generates (output = 'v2', bundle jpa-springboot) has no jpa-service-access;"
                                + " the 3.0 code carries it: elasticore { output = 'v3' } - see elasticore-migration-guide.md."),
                w);
        assertFalse(w.contains("Declare architecture"), w);
        String states = RulesGenRunner.unenforcedWarning(
                "club", "Order", "states: (ddd-state)", "ddd-state", null, "jpa-springboot", JAVA);
        assertTrue(
                states.contains("elasticore { output = 'v3' } with architecture = 'ddd' or 'hexagonal' - see"), states);
    }

    // ---- what a run says ----------------------------------------------------------------

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Member:",
            "    meta: entity @root @expose(20) @owner(memberId) @access(read=owner, create=public, update=owner)",
            "    fields:",
            "      memberId: long @id @sequence",
            "      nickname: string(50)",
            "      secret: string(100) @password",
            "");

    /** Two entities more: a delete that keeps the row, and a value worked out. */
    private static final String MORE = String.join(
            "\n",
            "  Note:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      noteId: long @id @sequence",
            "      deletedAt: timestamp @deleted",
            "",
            "  Line:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      lineId: long @id @sequence",
            "      quantity: int",
            "      price: int",
            "      total: int @compute(quantity * price)",
            "");

    /** The reported run: a hexagonal domain whose env.yml names ossie-export alone. */
    @Test
    @DisplayName("hexagonal with publisher.rules: [ossie-export] is told the list replaces the bundle")
    public void theReportedRunIsToldTheListReplacesTheBundle() throws Exception {
        String log = generate(project("[ossie-export]", MODEL + MORE), "hexagonal", null);
        assertTrue(
                log.contains("Member declares @access, @owner and @password (jpa-service-access), which the selected"
                        + " rules do not enforce"),
                log);
        assertTrue(log.contains("publisher.rules in env.yml replaces the architecture's bundle"), log);
        assertTrue(log.contains("to keep the list, name jpa-service-access in it."), log);
        assertFalse(log.contains("Declare architecture"), log);
        // Each with the set it lacks: the JPA delete that keeps the row, and the derived values.
        assertTrue(log.contains("Note declares @deleted (jpa-entity-annotations), which"), log);
        assertTrue(log.contains("does not name jpa-entity-annotations."), log);
        assertTrue(
                log.contains("name a bundle that carries ddd-derive with the domain-model rule sets it works on"
                        + " (hexagonal-springboot or ddd-springboot) in it."),
                log);
    }

    /** The same list as the build's property: the engine runs it alone, and the warning says so. */
    @Test
    @DisplayName("hexagonal with -Delasticore.publisher.rules=ossie-export is warned and told the property replaces it")
    public void theBuildsPropertyIsWarnedAsTheListIs() throws Exception {
        String log = generate(project(null), "hexagonal", null, "elasticore.publisher.rules=ossie-export");
        assertTrue(log.contains("Member declares @access, @owner and @password (jpa-service-access)"), log);
        assertTrue(log.contains("The build's -Delasticore.publisher.rules replaces the architecture's bundle"), log);
        assertFalse(log.contains("Declare architecture"), log);
    }

    /** A layered Java domain whose env.yml the wizard wrote before 3.0.261028-rc3: [jpa-springboot]. */
    @Test
    @DisplayName("publisher.rules: [jpa-springboot] is told to take it out of the list, not to name a set in it")
    public void aListNamingTheTwoXBundleIsToldToTakeItOutOfTheList() throws Exception {
        String log = generate(project("[jpa-springboot]"), null, null);
        assertTrue(
                log.contains("publisher.rules in env.yml names jpa-springboot, the code 2.x generates, which has no"
                        + " jpa-service-access; the 3.0 code carries it: take jpa-springboot out of the list"),
                log);
        assertFalse(log.contains("to keep the list"), log);
    }

    @Test
    @DisplayName("@deleted names the set of the domain's mode")
    public void theDeleteThatKeepsTheRowNamesTheSetOfTheMode() {
        assertEquals(
                "jpa-entity-annotations",
                RulesGenRunner.keepingRuleSet(io.elasticore.base.model.pub.rules.RulesConfig.Persistence.JPA));
        assertEquals(
                "mybatis-mapper",
                RulesGenRunner.keepingRuleSet(io.elasticore.base.model.pub.rules.RulesConfig.Persistence.MYBATIS));
        assertEquals(
                "mongo-service-file",
                RulesGenRunner.keepingRuleSet(io.elasticore.base.model.pub.rules.RulesConfig.Persistence.MONGO));
    }

    /** Both ways the message offers end the warning, and the checks are written. */
    @Test
    @DisplayName("the adding form, and the set named in the list, each end the warning and write the checks")
    public void bothWaysTheMessageOffersWork() throws Exception {
        File added = project(null);
        String addedLog = generate(added, "hexagonal", "ossie-export");
        assertFalse(addedLog.contains("do not enforce"), addedLog);
        assertTrue(anySourceContains(added, "AccessPolicy"), "no access check was written");

        File named = project("[ossie-export, jpa-service-access]");
        String namedLog = generate(named, "hexagonal", null);
        assertFalse(namedLog.contains("do not enforce"), namedLog);
        assertTrue(anySourceContains(named, "AccessPolicy"), "no access check was written");
    }

    @Test
    @DisplayName("output = 'v2' is sent to the 3.0 code, not to an architecture")
    public void theTwoXRunIsSentToTheThreeOCode() throws Exception {
        String log = generate(project(null, MODEL + MORE), null, null, "elasticore.rules.v2");
        assertTrue(log.contains("Member declares @access, @owner and @password (jpa-service-access)"), log);
        assertTrue(
                log.contains(
                        "The code 2.x generates (output = 'v2', bundle jpa-springboot) has no jpa-service-access;"),
                log);
        assertTrue(
                log.contains("has no jpa-entity-annotations; the 3.0 code carries it: elasticore { output = 'v3' } -"),
                log);
        assertTrue(log.contains("has no ddd-derive; the 3.0 code carries it: elasticore { output = 'v3' } with"), log);
        assertFalse(log.contains("Declare architecture"), log);
    }

    // ---- the harness ----------------------------------------------------------------

    private final List<File> projects = new ArrayList<File>();

    private File project(String rules) throws IOException {
        return project(rules, MODEL);
    }

    private File project(String rules, String model) throws IOException {
        File project = Files.createTempDirectory("elcore-unenforced").toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/club");
        assertTrue(new File(dir, "model").mkdirs());
        List<String> env = new ArrayList<String>(java.util.Arrays.asList(
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: club",
                "    framework: springboot",
                "    mode: jpa",
                "    j2ee: jakarta"));
        if (rules != null) {
            env.add("    publisher:");
            env.add("      rules: " + rules);
        }
        env.addAll(java.util.Arrays.asList(
                "  namespace:",
                "    entity: com.acme.club.entity",
                "    dto: com.acme.club.dto",
                "    repository: com.acme.club.repository",
                "    service: com.acme.club.service",
                "    control: com.acme.club.controller",
                ""));
        Files.write(new File(dir, "env.yml").toPath(), String.join("\n", env).getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    /** One generation; its log. The build's added rule sets, if any, and one switch more. */
    private static String generate(File project, String architecture, String added, String... switches)
            throws Exception {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v3",
            "elasticore.rules.v2",
            "elasticore.rules.ts",
            "elasticore.publisher.rules",
            "elasticore.publisher.rules.add"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        int code;
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            if (architecture != null) System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.force", "true");
            if (added != null) System.setProperty("elasticore.publisher.rules.add", added);
            for (String s : switches) {
                int eq = s.indexOf('=');
                if (eq < 0) System.setProperty(s, "true");
                else System.setProperty(s.substring(0, eq), s.substring(eq + 1));
            }
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            code = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        assertEquals(0, code, log);
        return log;
    }

    private static boolean anySourceContains(File project, final String text) throws IOException {
        final boolean[] found = new boolean[1];
        File sources = new File(project, "src/main/java");
        if (!sources.isDirectory()) return false;
        Files.walkFileTree(sources.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains(text)) {
                    found[0] = true;
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return found[0];
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
