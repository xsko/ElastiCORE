package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue 331 and the rule behind it: a model type of another domain - an @abstract parent, an enum, a
 * value object, an entity, a DTO - is named by its simple name, stays in the domain that declares it,
 * and is imported from there wherever the generator writes it, in JPA, MyBatis and MongoDB, Java and
 * Kotlin, layered, ddd and hexagonal. Type names are unique across domains. The domain-qualified form
 * 2.x accepted (common:Grade) is read as the simple name.
 *
 * Each combination below was also generated and compiled against the sample modules' classpaths while
 * the fixes were made; these assertions hold the lines whose absence did not compile.
 */
public class OtherDomainTypesTest {

    private static final String COMMON = String.join(
            "\n",
            "enumeration:",
            "  Grade:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(1) @code",
            "      label: string(20)",
            "    enum:",
            "      GOLD: G,Gold",
            "      SILVER: S,Silver",
            "",
            "  AgentStatus:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(3) @code",
            "      label: string(20)",
            "    enum:",
            "      ACTIVE: ACT,Active",
            "      RETIRED: RET,Retired",
            "",
            "entity:",
            "  AuditEntity:",
            "    meta: entity @abstract",
            "    fields:",
            "      createdAt: datetime @updatable(false)",
            "      createdBy: string(30) @updatable(false)",
            "",
            "  Location:",
            "    meta: entity @embeddable",
            "    fields:",
            "      city: string(50)",
            "      zip: string(10)",
            "",
            "  Office:",
            "    meta: entity @root @editable @expose(20) @extend(AuditEntity)",
            "    fields:",
            "      officeId: long @id @sequence",
            "      name: string(50)! @search(like)",
            "      grade: Grade @search(eq)",
            "",
            "dto:",
            "  ContactDTO:",
            "    meta: dto",
            "    fields:",
            "      email: string",
            "      grade: Grade",
            "");

    /** The using domain; {@code @@} is where a model writes the other domain's name, or nothing. */
    private static final String AGENCY = String.join(
            "\n",
            "entity:",
            "  Agent:",
            "    meta: entity @root @expose(20) @extend(@@AuditEntity) @event(Promoted(grade), Retired)",
            "    fields:",
            "      agentId: long @id @sequence",
            "      name: string(50)! @search(like) @editable",
            "      grade: @@Grade @search(eq) @editable",
            "      status: @@AgentStatus @search(eq)",
            "      home: @@Location @Embedded @editable",
            "      office: @@Office @ManyToOne",
            "    commands:",
            "      promote:",
            "        grade: @@Grade!",
            "    states:",
            "      status:",
            "        initial: ACTIVE",
            "        retire: ACTIVE -> RETIRED",
            "",
            "  Desk:",
            "    meta: entity @root @editable @expose(20) @extend(@@AuditEntity)",
            "    fields:",
            "      deskId: long @id @sequence",
            "      label: string(30)! @search(like)",
            "      spot: @@Location @Embedded",
            "      office: @@Office @ManyToOne",
            "",
            "dto:",
            "  AgentCardDTO:",
            "    meta: dto",
            "    fields:",
            "      name: string",
            "      contact: @@ContactDTO",
            "      contacts: List<@@ContactDTO>",
            "      grade: @@Grade",
            "",
            "  AgentContactDTO:",
            "    meta: dto @extend(@@ContactDTO)",
            "    fields:",
            "      agentName: string",
            "",
            "  OfficeViewDTO:",
            "    meta: dto @template(@@Office)",
            "",
            "  AgentSrchDTO:",
            "    meta: dto @searchable(entity=Agent, pageSize=20)",
            "");

    private static final String AGENCY_PORT = String.join(
            "\n",
            "port:",
            "  AgencyLookup:",
            "    meta: dbms",
            "    methods:",
            "      contactsLike:",
            "        meta: method",
            "        params:",
            "          input: @@ContactDTO",
            "        return: List<@@ContactDTO>",
            "        nativeQuery: true",
            "        query: |",
            "          SELECT email as email FROM agency_agent WHERE email = :email",
            "      firstContact:",
            "        meta: method",
            "        params:",
            "          grade: @@Grade",
            "        return: @@ContactDTO",
            "        nativeQuery: true",
            "        query: |",
            "          SELECT email as email FROM agency_agent WHERE grade = :grade",
            "");

    private static final String[][] COMBINATIONS = {
        {"jpa", "java", "layered"}, {"jpa", "java", "ddd"}, {"jpa", "java", "hexagonal"},
        {"jpa", "kotlin", "layered"}, {"jpa", "kotlin", "ddd"}, {"jpa", "kotlin", "hexagonal"},
        {"mybatis", "java", "layered"}, {"mybatis", "java", "ddd"}, {"mybatis", "java", "hexagonal"},
        {"mongo", "java", "layered"}, {"mongo", "kotlin", "layered"},
    };

    @Test
    public void everyCombinationImportsTheOtherDomainsTypes() throws Exception {
        for (String[] combo : COMBINATIONS) {
            String mode = combo[0];
            boolean kotlin = "kotlin".equals(combo[1]);
            boolean domainModel = !"layered".equals(combo[2]);
            String at = String.join("/", combo);
            String ext = kotlin ? ".kt" : ".java";
            String end = kotlin ? "" : ";";
            File project = project(mode, "");
            generate(project, combo[2], combo[1]);

            // The types stay in common: one class each, there - no copy in agency.
            for (String type :
                    new String[] {"AuditEntity", "Grade", "Location", "Office", "ContactDTO", "OfficeKeyDTO"}) {
                String path = all(project, type + ext).get(0).getPath().replace('\\', '/');
                assertTrue(path.contains("/com/acme/common/"), at + ": " + path);
            }

            // The entity extends the parent of common, imported (the regenerated half of a split root).
            String agent = found(project, (domainModel ? "AgentBase" : "Agent") + ext);
            assertTrue(
                    agent.contains("import com.acme.common.domain.model.AuditEntity" + end + "\n"), at + "\n" + agent);
            String desk = found(project, "Desk" + ext);
            assertTrue(desk.contains("import com.acme.common.domain.model.AuditEntity" + end + "\n"), at + "\n" + desk);
            String contact = found(project, "AgentContactDTO" + ext);
            assertTrue(contact.contains("import com.acme.common.dto.ContactDTO" + end + "\n"), at + "\n" + contact);

            // The port names common's DTO and enum, and imports them.
            String port = found(project, "AgencyLookup" + ext);
            assertTrue(port.contains("import com.acme.common.dto.ContactDTO" + end + "\n"), at + "\n" + port);
            assertTrue(port.contains("import com.acme.common.domain.model.Grade" + end + "\n"), at + "\n" + port);
            if ("mybatis".equals(mode)) {
                for (String file : new String[] {"AgencyLookupAdapter.java", "AgencyLookupMapper.java"}) {
                    String text = found(project, file);
                    assertTrue(
                            text.contains("import com.acme.common.dto.ContactDTO;\n"), at + " " + file + "\n" + text);
                    assertTrue(
                            text.contains("import com.acme.common.domain.model.Grade;\n"),
                            at + " " + file + "\n" + text);
                }
            }

            // The command's parameter of common's enum, in the behaviour class the developer keeps.
            if (domainModel) {
                String behaviour = found(project, "Agent" + ext);
                assertTrue(
                        behaviour.contains(
                                kotlin
                                        ? "override fun promote(grade: com.acme.common.domain.model.Grade) {"
                                        : "public void promote(com.acme.common.domain.model.Grade grade) {"),
                        at + "\n" + behaviour);
            }
        }
    }

    @Test
    public void aDomainWrittenBeforeTheNameIsReadAsTheSimpleName() throws Exception {
        String[][] combos = {
            {"jpa", "java", "layered"},
            {"mybatis", "java", "hexagonal"},
            {"jpa", "kotlin", "ddd"},
            {"mongo", "kotlin", "layered"},
            {"jpa", "typescript", "layered"},
        };
        for (String[] combo : combos) {
            File simple = project(combo[0], "");
            generate(simple, combo[2], combo[1]);
            File qualified = project(combo[0], "common:");
            generate(qualified, combo[2], combo[1]);
            Map<String, String> a = sources(simple);
            Map<String, String> b = sources(qualified);
            assertFalse(a.isEmpty(), String.join("/", combo));
            assertEquals(a.keySet(), b.keySet(), String.join("/", combo));
            for (Map.Entry<String, String> e : a.entrySet()) {
                assertEquals(e.getValue(), b.get(e.getKey()), String.join("/", combo) + " " + e.getKey());
                assertFalse(e.getValue().contains("common:"), e.getKey() + "\n" + e.getValue());
            }
        }
    }

    /** A Spring HTTP service client (httpPort: spring, Boot 4) of another domain's DTO imports it too. */
    @Test
    public void aSpringHttpPortImportsTheOtherDomainsDto() throws Exception {
        for (String language : new String[] {"java", "kotlin"}) {
            File project = project("jpa", "");
            File env = new File(project, "src/main/resources/blueprint/agency/env.yml");
            String text = new String(Files.readAllBytes(env.toPath()), StandardCharsets.UTF_8);
            Files.write(
                    env.toPath(),
                    text.replace("    j2ee: jakarta\n", "    j2ee: jakarta\n    httpPort: spring\n")
                            .getBytes(StandardCharsets.UTF_8));
            Files.write(
                    new File(project, "src/main/resources/blueprint/agency/port/client.yml").toPath(),
                    String.join(
                                    "\n",
                                    "port:",
                                    "  ContactClient:",
                                    "    meta: http @url(\"http://localhost:8080/api\")",
                                    "    methods:",
                                    "      find:",
                                    "        meta: method @get(\"/contacts/{email}\")",
                                    "        params:",
                                    "          email: string",
                                    "        return: ContactDTO",
                                    "      save:",
                                    "        meta: method @post(\"/contacts\")",
                                    "        params:",
                                    "          body: ContactDTO @body",
                                    "        return: ContactDTO",
                                    "")
                            .getBytes(StandardCharsets.UTF_8));
            generate(project, "layered", language, "4");
            boolean kotlin = "kotlin".equals(language);
            String client = found(project, "ContactClient" + (kotlin ? ".kt" : ".java"));
            assertTrue(client.contains("@HttpExchange"), client);
            assertTrue(client.contains("import com.acme.common.dto.ContactDTO" + (kotlin ? "" : ";") + "\n"), client);
        }
    }

    /**
     * The key DTO of another domain's entity is that domain's; the 2.x-compatible output keeps the
     * copy 2.x writes. A name two domains declare is each domain's own: each keeps its key DTO.
     */
    @Test
    public void theKeyDtoOfAnEntityIsItsOwnDomains() throws Exception {
        File v2 = project("jpa", "");
        generate(v2, "layered", "java", null, true);
        List<File> copies = new ArrayList<File>();
        collect(v2, "OfficeKeyDTO.java", copies);
        assertEquals(2, copies.size(), String.valueOf(copies));

        File project = Files.createTempDirectory("elcore-other-domain").toFile();
        projects.add(project);
        domain(project, "common", "jpa", COMMON, null);
        domain(
                project,
                "agency",
                "jpa",
                String.join(
                        "\n",
                        "entity:",
                        "  Office:",
                        "    meta: entity @root @editable @expose(20)",
                        "    fields:",
                        "      officeId: long @id @sequence",
                        "      title: string(50)",
                        ""),
                null);
        generate(project, "layered", "java");
        List<File> keys = new ArrayList<File>();
        collect(project, "OfficeKeyDTO.java", keys);
        assertEquals(2, keys.size(), String.valueOf(keys));
    }

    @Test
    public void aTypeTwoDomainsDeclareIsWarnedWithWhatToDo() throws Exception {
        File project = project("jpa", "");
        // A copy of common's enum in agency: what the rule forbids.
        Path agency = new File(project, "src/main/resources/blueprint/agency/model/copy.yml").toPath();
        Files.write(
                agency,
                String.join(
                                "\n",
                                "enumeration:",
                                "  Grade:",
                                "    meta: enum @db(code) @json(code)",
                                "    fields:",
                                "      code: string(1) @code",
                                "    enum:",
                                "      GOLD: G",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        String log = generate(project, "layered", "java");
        assertTrue(log.contains("duplicate type 'Grade' is defined in multiple domains"), log);
        assertTrue(log.contains("a type name is unique across domains: rename one"), log);
    }

    @Test
    public void aDomainBeforeATypeIsDroppedOnlyForTheRuleEngine() {
        boolean was = io.elasticore.base.model.entity.BaseFieldType.ruleEngineTypes();
        try {
            io.elasticore.base.model.entity.BaseFieldType.ruleEngineTypes(true);
            assertEquals("Grade", io.elasticore.base.model.entity.TypeInfo.simpleNamed("common:Grade"));
            assertEquals(
                    "List<ContactDTO>",
                    io.elasticore.base.model.entity.TypeInfo.simpleNamed("List<common:ContactDTO>"));
            assertEquals(
                    "Map<String, Grade>",
                    io.elasticore.base.model.entity.TypeInfo.simpleNamed("Map<String, common:Grade>"));
            assertEquals(
                    "java.util.List<Grade>",
                    io.elasticore.base.model.entity.TypeInfo.simpleNamed("java.util.List<Grade>"));
            assertEquals("Grade", new io.elasticore.base.model.entity.TypeInfo("common:Grade").getDefaultTypeName());
            io.elasticore.base.model.entity.BaseFieldType.ruleEngineTypes(false);
            // The 2.x generator writes such a type by its full class name: it reads it as written.
            assertEquals("common:Grade", io.elasticore.base.model.entity.TypeInfo.simpleNamed("common:Grade"));
        } finally {
            io.elasticore.base.model.entity.BaseFieldType.ruleEngineTypes(was);
        }
    }

    @Test
    public void aDomainQualifiedParentIsItsSimpleName() {
        assertEquals("AuditEntity", SpecFactory.simpleParent("common:AuditEntity"));
        assertEquals("AuditEntity", SpecFactory.simpleParent(" AuditEntity "));
        assertEquals("com.acme.support.Base", SpecFactory.simpleParent("com.acme.support.Base"));
        assertEquals("a.b:C", SpecFactory.simpleParent("a.b:C"));
        assertEquals("common:", SpecFactory.simpleParent("common:"));
        assertEquals("Base<Key>", SpecFactory.simpleParent("Base<common:Key>"));
        assertEquals(null, SpecFactory.simpleParent(null));
    }

    // ---- the harness ----------------------------------------------------------------

    private final List<File> projects = new ArrayList<File>();

    private File project(String mode, String qualifier) throws IOException {
        File project = Files.createTempDirectory("elcore-other-domain").toFile();
        projects.add(project);
        domain(project, "common", mode, COMMON, null);
        String agency = AGENCY.replace("@@", qualifier);
        String port = AGENCY_PORT.replace("@@", qualifier);
        domain(project, "agency", mode, agency, port);
        return project;
    }

    private static void domain(File project, String name, String mode, String model, String port) throws IOException {
        File dir = new File(project, "src/main/resources/blueprint/" + name);
        assertTrue(new File(dir, "model").mkdirs());
        String p = "com.acme." + name;
        String env = String.join(
                "\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: " + name,
                "    framework: springboot",
                "    mode: " + mode,
                "    j2ee: jakarta",
                "    table:",
                "      prefix: " + name + "_",
                "  namespace:",
                "    entity: " + p + ".domain.model",
                "    enumeration: " + p + ".domain.model",
                "    repository: " + p + ".infra",
                "    dto: " + p + ".dto",
                "    service: " + p + ".service",
                "    control: " + p + ".web",
                "    port: " + p + ".port",
                "");
        Files.write(new File(dir, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
        if (port != null) {
            assertTrue(new File(dir, "port").mkdirs());
            Files.write(new File(dir, "port/port.yml").toPath(), port.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String generate(File project, String architecture, String language) throws Exception {
        return generate(project, architecture, language, null, false);
    }

    private static String generate(File project, String architecture, String language, String springBoot)
            throws Exception {
        return generate(project, architecture, language, springBoot, false);
    }

    private static String generate(File project, String architecture, String language, String springBoot, boolean v2)
            throws Exception {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.springBoot",
            "elasticore.rules.v2"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        int code;
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            if (springBoot != null) System.setProperty("elasticore.springBoot", springBoot);
            if (v2) System.setProperty("elasticore.rules.v2", "true");
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
        assertEquals(0, code, architecture + "/" + language + "\n" + log);
        return log;
    }

    /** Every generated source, by its path under the project, without the blueprint. */
    private static Map<String, String> sources(File project) throws IOException {
        Map<String, String> out = new TreeMap<String, String>();
        List<File> files = new ArrayList<File>();
        collect(new File(project, "src"), null, files);
        for (File f : files) {
            String path = f.getAbsolutePath()
                    .substring(project.getAbsolutePath().length())
                    .replace('\\', '/');
            if (path.contains("/blueprint/") || path.contains("/.elasticore/")) continue;
            out.put(path, new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        }
        return out;
    }

    private static List<File> all(File project, String fileName) {
        List<File> hits = new ArrayList<File>();
        collect(project, fileName, hits);
        assertEquals(1, hits.size(), fileName + " in " + project + ": " + hits);
        return hits;
    }

    private static String found(File project, String fileName) throws IOException {
        File hit = all(project, fileName).get(0);
        return new String(Files.readAllBytes(hit.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static void collect(File dir, String name, List<File> hits) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, name, hits);
            else if (name == null || f.getName().equals(name)) hits.add(f);
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
        projects.clear();
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
