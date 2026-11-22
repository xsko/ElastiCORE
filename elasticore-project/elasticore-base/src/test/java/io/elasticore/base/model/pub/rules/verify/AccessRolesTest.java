package io.elasticore.base.model.pub.rules.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.elasticore.base.model.pub.rules.schema.DdlConfig;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * {@code @access} names roles (issue 269): read=owner|MANAGER, update=REP&owner - the role
 * enum's constants checked where the application has one, in every mode and both languages.
 */
public class AccessRolesTest {

    private static final String ROLES = String.join(
            "\n",
            "enumeration:",
            "  ArRole:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(1) @code",
            "    enum:",
            "      USER:    U",
            "      MANAGER: M",
            "      REP:     R",
            "      ADMIN:   A",
            "");

    private static final String MEMBER = String.join(
            "\n",
            "  ArMember:",
            "    meta: entity @root @editable @expose(20) @signin @owner(memberId) @access(read=owner|MANAGER, create=public, update=owner)",
            "    fields:",
            "      memberId: long @id @sequence",
            "      login:    string(20)! @login @unique",
            "      password: string(255)! @password",
            "      role:     ArRole @default(ArRole.USER) @access(admin)",
            "");

    private static final String TICKET = String.join(
            "\n",
            "  ArTicket:",
            "    meta: entity @root @editable @expose(20) @owner(memberId) @access(read=owner | MANAGER, create=REP|MANAGER, update=REP&owner, delete=user&MANAGER)",
            "    fields:",
            "      ticketId: long @id @sequence",
            "      memberId: long",
            "      title:    string(50)",
            "");

    private static final String MODEL = ROLES + "\nentity:\n" + MEMBER + "\n" + TICKET;

    private static final List<File> projects = new ArrayList<File>();

    @AfterAll
    static void cleanUp() throws IOException {
        for (File p : projects) delete(p);
    }

    private static Map<String, String> one(String model) {
        Map<String, String> domains = new LinkedHashMap<String, String>();
        domains.put("forum", model);
        return domains;
    }

    @Test
    public void theServiceCarriesTheEntryAndTheBuildSaysWhatItCannotMean() throws Exception {
        String[] log = new String[1];
        File project = run("elcore-roles-java", one(MODEL), "hexagonal", "java", "jpa", 0, log);
        String service = holding(project, "AccessPolicy.parse(\"ArTicket\"");
        assertTrue(service.contains("read=owner | MANAGER"), service);
        assertTrue(service.contains("update=REP&owner"), service);
        assertTrue(service.contains("ACCESS.ownerFilter(currentUser, \"read\")"), service);
        assertTrue(log[0].contains("ArRole.USER cannot be named in @access"), log[0]);
        assertTrue(log[0].contains("ArTicket @access(delete=user&MANAGER): user with & adds nothing"), log[0]);
        assertFalse(log[0].contains("is not operation=level"), log[0]);
        assertFalse(log[0].contains("is neither a level"), log[0]);
        // read=owner|MANAGER keeps the sign-in: update=owner on the key is what it asks.
        assertFalse(log[0].contains("@signin generates no sign-in"), log[0]);
    }

    /** The role names change the declaration the service parses, and nothing else of it. */
    @Test
    public void aRoleNameChangesOnlyTheDeclaration() throws Exception {
        String[] log = new String[1];
        File named = run("elcore-roles-named", one(MODEL), "hexagonal", "java", "jpa", 0, log);
        String plain = MODEL.replace(
                "read=owner | MANAGER, create=REP|MANAGER, update=REP&owner, delete=user&MANAGER",
                "read=owner, create=user, update=owner, delete=user");
        File levels = run("elcore-roles-plain", one(plain), "hexagonal", "java", "jpa", 0, log);
        String withRoles = holding(named, "AccessPolicy.parse(\"ArTicket\"");
        String withLevels = holding(levels, "AccessPolicy.parse(\"ArTicket\"");
        assertEquals(
                withLevels.replaceAll("AccessPolicy.parse\\(\"ArTicket\", \"[^\"]*\"", "PARSE"),
                withRoles.replaceAll("AccessPolicy.parse\\(\"ArTicket\", \"[^\"]*\"", "PARSE"));
    }

    @Test
    public void inKotlinTheCompanionParsesTheSameEntry() throws Exception {
        String[] log = new String[1];
        File project = run("elcore-roles-kt", one(MODEL), "hexagonal", "kotlin", "jpa", 0, log);
        String service = holding(project, "AccessPolicy.parse(\"ArTicket\"");
        assertTrue(service.contains("read=owner | MANAGER"), service);
        assertTrue(service.contains("ACCESS.ownerFilter(currentUser, \"read\")"), service);
    }

    /** The search filter of MyBatis and MongoDB asks the same policy: a MANAGER's search is not narrowed. */
    @Test
    public void myBatisAndMongoNarrowTheSearchByTheSamePolicy() throws Exception {
        String[] log = new String[1];
        String[][] cases = {{"mybatis", "java"}, {"mongo", "java"}, {"mongo", "kotlin"}};
        for (String[] c : cases) {
            File project = run("elcore-roles-" + c[0] + "-" + c[1], one(MODEL), "layered", c[1], c[0], 0, log);
            String service = holding(project, "AccessPolicy.parse(\"ArTicket\"");
            assertTrue(service.contains("read=owner | MANAGER"), c[0] + " " + c[1] + ": " + service);
            assertTrue(service.contains("ACCESS.ownerFilter(currentUser, \"read\")"), service);
            assertTrue(service.contains("ownedOnly()"), service);
            assertFalse(log[0].contains("is neither a level"), log[0]);
        }
    }

    @Test
    public void aNameTheRoleEnumDoesNotHaveStopsTheBuild() throws Exception {
        String[] log = new String[1];
        String model = MODEL.replace("create=REP|MANAGER", "create=REP|MANGER")
                .replace("update=REP&owner", "update=rep&owner");
        run("elcore-roles-typo", one(model), "hexagonal", "java", "jpa", 1, log);
        assertTrue(
                log[0].contains("ArTicket @access(create=REP|MANGER): MANGER is neither a level (public, user, owner,"
                        + " admin, none) nor a constant of the role enum ArRole (USER, MANAGER, REP, ADMIN)"),
                log[0]);
        assertTrue(log[0].contains("ArTicket @access(update=rep&owner): rep is neither a level"), log[0]);
    }

    /** The @signin entity of another domain of the application gives the names; its update must stay its own. */
    @Test
    public void theRoleEnumOfAnotherDomainChecksTheNames() throws Exception {
        String[] log = new String[1];
        Map<String, String> domains = new LinkedHashMap<String, String>();
        domains.put("account", ROLES + "\nentity:\n" + MEMBER.replace("update=owner", "update=owner|MANAGER"));
        domains.put("forum", "entity:\n" + TICKET.replace("create=REP|MANAGER", "create=CLERK"));
        run("elcore-roles-across", domains, "hexagonal", "java", "jpa", 1, log);
        assertTrue(log[0].contains("ArTicket @access(create=CLERK): CLERK is neither a level"), log[0]);
        // A MANAGER who may change any member's password could sign in as the administrator.
        assertTrue(log[0].contains("ArMember: @signin generates no sign-in: update is owner|MANAGER"), log[0]);
    }

    /**
     * update=REP&owner on the key still lets in only the row's own user: the sign-in stays. The & warnings
     * for none and public; on another key the reason names the owner that is not the key.
     */
    @Test
    public void aBothEntryOnTheKeyKeepsTheSignIn() throws Exception {
        String[] log = new String[1];
        String model = MODEL.replace("update=owner)", "update=REP&owner)")
                .replace("delete=user&MANAGER", "delete=none&REP, archive=public&MANAGER");
        File project = run("elcore-roles-both", one(model), "hexagonal", "java", "jpa", 0, log);
        assertFalse(log[0].contains("@signin generates no sign-in"), log[0]);
        assertTrue(holding(project, "@PostMapping(\"/signin\")").contains("ArMember"));
        assertTrue(log[0].contains("ArTicket @access(delete=none&REP): none with & lets nobody in"), log[0]);
        assertTrue(log[0].contains("ArTicket @access(archive=public&MANAGER): public with & adds nothing"), log[0]);

        String other = MODEL.replace(
                        "@owner(memberId) @access(read=owner|MANAGER, create=public, update=owner)",
                        "@owner(sponsorId) @access(read=owner|MANAGER, create=public, update=REP&owner)")
                .replace(
                        "      memberId: long @id @sequence\n",
                        "      memberId: long @id @sequence\n      sponsorId: long\n");
        run("elcore-roles-both-other", one(other), "hexagonal", "java", "jpa", 0, log);
        assertTrue(
                log[0].contains(
                        "@signin generates no sign-in: update is REP&owner, but @owner(sponsorId) is not the key"
                                + " memberId"),
                log[0]);
    }

    /** A model that names no role is not told about its enum's USER: only an entry that names a role, or writes USER. */
    @Test
    public void theHiddenConstantIsSaidOnlyWhereItMayMislead() throws Exception {
        String[] log = new String[1];
        String plain = MODEL.replace("read=owner|MANAGER, create=public", "read=owner, create=public")
                .replace(
                        "read=owner | MANAGER, create=REP|MANAGER, update=REP&owner, delete=user&MANAGER",
                        "read=owner, create=user, update=owner");
        run("elcore-roles-none-named", one(plain), "hexagonal", "java", "jpa", 0, log);
        assertFalse(log[0].contains("cannot be named in @access"), log[0]);
        run(
                "elcore-roles-caps",
                one(plain.replace("create=user, update=owner", "create=USER, update=owner")),
                "hexagonal",
                "java",
                "jpa",
                0,
                log);
        assertTrue(log[0].contains("ArRole.USER cannot be named in @access"), log[0]);
    }

    /** No @signin entity - the application signs in its own way: a role name is the application's to give. */
    @Test
    public void withoutARoleEnumANameIsNotChecked() throws Exception {
        String[] log = new String[1];
        String model = "entity:\n" + TICKET.replace("create=REP|MANAGER", "create=CLERK");
        File project = run("elcore-roles-unchecked", one(model), "hexagonal", "java", "jpa", 0, log);
        assertTrue(holding(project, "AccessPolicy.parse(\"ArTicket\"").contains("create=CLERK"));
        assertFalse(log[0].contains("is neither a level"), log[0]);
        assertFalse(log[0].contains("cannot be named in @access"), log[0]);
    }

    /** The Ossie export writes a joined entry as the runtime reads it, spaces left out. */
    @Test
    public void theOssieExportWritesTheEntryAsTheRuntimeReadsIt() throws Exception {
        String[] log = new String[1];
        File project = run(
                "elcore-roles-ossie",
                one(MODEL),
                "hexagonal",
                "java",
                "jpa",
                0,
                log,
                "    publisher:\n      rules: [ossie-export]");
        File yaml = new File(project, "src/main/resources/blueprint/semantic_model.yml");
        assertTrue(yaml.isFile(), log[0]);
        String text = new String(Files.readAllBytes(yaml.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("\"read\": \"owner|MANAGER\""), text);
        assertTrue(text.contains("\"update\": \"REP&owner\""), text);
    }

    private static File run(
            String name,
            Map<String, String> domains,
            String architecture,
            String language,
            String mode,
            int exit,
            String[] log)
            throws Exception {
        return run(name, domains, architecture, language, mode, exit, log, "");
    }

    private static File run(
            String name,
            Map<String, String> domains,
            String architecture,
            String language,
            String mode,
            int exit,
            String[] log,
            String extraConfig)
            throws Exception {
        File project = Files.createTempDirectory(name).toFile();
        projects.add(project);
        for (Map.Entry<String, String> d : domains.entrySet()) {
            File dir = new File(project, "src/main/resources/blueprint/" + d.getKey());
            assertTrue(new File(dir, "model").mkdirs());
            Files.write(
                    new File(dir, "env.yml").toPath(),
                    String.join(
                                    "\n",
                                    "elasticore:",
                                    "  enable: true",
                                    "  config:",
                                    "    domainName: " + d.getKey(),
                                    "    framework: springboot",
                                    "    mode: " + mode,
                                    "    j2ee: jakarta"
                                            + (extraConfig.isEmpty() ? "" : "\n" + extraConfig.replaceAll("\n$", "")),
                                    "  namespace:",
                                    "    base: io.acme." + d.getKey(),
                                    "")
                            .getBytes(StandardCharsets.UTF_8));
            Files.write(new File(dir, "model/model.yml").toPath(), d.getValue().getBytes(StandardCharsets.UTF_8));
        }
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v3",
            "elasticore.rules.v2",
            "elasticore.rules.ts",
            DdlConfig.DATABASES
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        PrintStream out = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty("elasticore.rules.v3", "true");
            System.setProperty(DdlConfig.DATABASES, "postgresql");
            assertEquals(exit, RulesGenRunner.run(new String[0]), "the run's exit: " + project);
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
        return project;
    }

    /** The one generated source that holds the text. */
    private static String holding(File project, String text) throws IOException {
        List<File> hits = new ArrayList<File>();
        collect(project, hits);
        List<String> found = new ArrayList<String>();
        String body = null;
        for (File f : hits) {
            String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
            if (s.contains(text)) {
                found.add(f.getName());
                body = s;
            }
        }
        assertEquals(1, found.size(), text + " in " + project + ": " + found);
        return body;
    }

    private static void collect(File dir, List<File> hits) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, hits);
            else if (f.getName().endsWith(".java") || f.getName().endsWith(".kt")) hits.add(f);
        }
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
