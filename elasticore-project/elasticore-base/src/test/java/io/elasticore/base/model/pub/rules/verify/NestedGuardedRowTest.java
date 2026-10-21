package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A whole row of a sign-in entity carried in another entity's DTO, with a
 * cascade: the reverse mapping attached the member the request sent, and the
 * save merged it into the member's row - login and password included - for
 * anyone who may update the profile. Generated in the four v3 readings (layered
 * and hexagonal, Java and Kotlin): the relation is not written from the DTO,
 * its key still points it, and the build says so naming the field. A list of
 * another domain's guarded rows, which the mapper writes back, is not written
 * either; a cascade into a row that guards nothing still is.
 */
public class NestedGuardedRowTest {

    /** The review's model, plus a row that guards nothing and a list from another domain. */
    private static final String SHOP = String.join("\n",
            "entity:",
            "  QmMember:",
            "    meta: entity @root @editable @expose(20) @signin @owner(memberId) @access(read=public, create=public, update=owner)",
            "    fields:",
            "      memberId: long @id @sequence",
            "      login:    string(20)! @login",
            "      password: string(255)! @password",
            "",
            "  QmNote:",
            "    meta: entity @root @editable @expose(20)",
            "    fields:",
            "      noteId: long @id @sequence",
            "      text:   string(50)",
            "",
            "  QmProfile:",
            "    meta: entity @root @editable @expose(20) @access(read=user, create=user, update=user)",
            "    fields:",
            "      profileId: long @id @sequence",
            "      bio:       string(50)",
            "      member:    QmMember @OneToOne @cascade(merge) @dtype(QmMemberDTO)",
            "      buddy:     QmMember @OneToOne @cascade",
            "      note:      QmNote @OneToOne @cascade(merge) @dtype(QmNoteDTO)",
            "");

    private static final String CLUB = String.join("\n",
            "entity:",
            "  QmClub:",
            "    meta: entity @root @editable @expose(20)",
            "    fields:",
            "      clubId:  long @id @sequence",
            "      name:    string(30)",
            "      members: List<QmMember> @OneToMany @cascade @dtype(List<QmMemberDTO>)",
            "      notes:   List<QmNote> @OneToMany @cascade @dtype(List<QmNoteDTO>)",
            "");

    /** A member of the root's own aggregate that holds what only a service may write. */
    private static final String ACCOUNTS = String.join("\n",
            "entity:",
            "  QdCustomer:",
            "    meta: entity @root @editable @expose(20) @owner(customerId) @access(read=owner, create=public, update=owner)",
            "    fields:",
            "      customerId: long @id @sequence",
            "      name:       string(30)",
            "      account:    QdAccount @OneToOne @cascade @dtype(QdAccountDTO)",
            "",
            "  QdAccount:",
            "    meta: entity @aggregate(QdCustomer) @service",
            "    fields:",
            "      accountId: long @id @sequence",
            "      tier:      string(10) @access(admin)",
            "      secret:    string(255) @password",
            "      label:     string(20)",
            "",
            "  QdItem:",
            "    meta: entity @root @editable @expose(20)",
            "    fields:",
            "      itemId: long @id @sequence",
            "      dtype:  string(20) @formula(\"dtype\")",
            "",
            "  QdBook:",
            "    meta: entity @extend(QdItem) @rollup(QdItem)",
            "    fields:",
            "      author: string(20)",
            "");

    private static File layeredJava;
    private static File layeredKotlin;
    private static File hexJava;
    private static File hexKotlin;
    private static final StringBuilder LOG = new StringBuilder();

    @BeforeAll
    static void generate() throws Exception {
        layeredJava = run("elcore-nested-lj", "java", "layered");
        layeredKotlin = run("elcore-nested-lk", "kotlin", "layered");
        hexJava = run("elcore-nested-hj", "java", "hexagonal");
        hexKotlin = run("elcore-nested-hk", "kotlin", "hexagonal");
    }

    private static void domain(File project, String name, String model) throws Exception {
        File dir = new File(project, "src/main/resources/blueprint/" + name);
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(new File(dir, "env.yml").toPath(), String.join("\n",
                "elasticore:", "  enable: true", "  config:", "    domainName: " + name, "    framework: springboot",
                "    mode: jpa", "    j2ee: jakarta", "  namespace:", "    base: io.acme." + name, "")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
    }

    private static File run(String name, String language, String architecture) throws Exception {
        File project = Files.createTempDirectory(name).toFile();
        domain(project, "qmshop", SHOP);
        domain(project, "qmclub", CLUB);
        domain(project, "qdshop", ACCOUNTS);
        String[] keys = {"elasticore.base.path", "elasticore.architecture", "elasticore.language",
                "elasticore.rules.force", "elasticore.rules.v3"};
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        java.io.PrintStream out = System.out;
        java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(captured, true, "UTF-8"));
        int status;
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            if ("layered".equals(architecture) && "java".equals(language)) {
                System.setProperty("elasticore.rules.v3", "true");
            }
            status = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
        String log = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        LOG.append("==== ").append(name).append('\n').append(log);
        assertTrue(status == 0, log);
        return project;
    }

    private static String found(File project, String name) throws Exception {
        try (java.util.stream.Stream<Path> all = Files.walk(project.toPath())) {
            for (Path p : all.toArray(Path[]::new)) {
                if (p.getFileName().toString().equals(name)) {
                    return new String(Files.readAllBytes(p), StandardCharsets.UTF_8).replace("\r\n", "\n");
                }
            }
        }
        assertTrue(false, "not generated: " + name + " in " + project);
        return null;
    }

    /** The bodies of the reverse mappings (DTO to entity) of one entity, all overloads. */
    private static String reverse(String mapper, String dto, String entity, boolean kt) {
        StringBuilder out = new StringBuilder();
        String head = kt ? "fun mapping(from: " + dto + "?, to: " + entity + "?" : "public static void mapping(" + dto + " from, " + entity + " to";
        int at = 0;
        while ((at = mapper.indexOf(head, at)) >= 0) {
            int end = mapper.indexOf("\n    }\n", at);
            out.append(mapper, at, end < 0 ? mapper.length() : end).append('\n');
            at = end < 0 ? mapper.length() : end;
        }
        assertTrue(out.length() > 0, head + " not in\n" + mapper);
        return out.toString();
    }

    private static boolean writes(String body, String field, boolean kt) {
        String cap = Character.toUpperCase(field.charAt(0)) + field.substring(1);
        return kt ? body.contains("from." + field + ",") || body.contains("to." + field + " = from." + field)
                : body.contains("to::set" + cap + ",") || body.contains("to.set" + cap + "(from.get" + cap + "())");
    }

    @Test
    public void theSignInRowIsNotWrittenThroughTheProfile() throws Exception {
        File[] projects = {layeredJava, layeredKotlin, hexJava, hexKotlin};
        for (File project : projects) {
            boolean kt = project == layeredKotlin || project == hexKotlin;
            String mapper = found(project, kt ? "QmshopMapper.kt" : "QmshopMapper.java");
            String body = reverse(mapper, "QmProfileDTO", "QmProfile", kt);
            assertFalse(writes(body, "member", kt), project + ": the member row is written\n" + body);
            // The key the DTO sends still points the relation.
            assertTrue(body.contains("MemberMemberId") || body.contains("memberMemberId"), project + ": the key no longer points it\n" + body);
            // A cascade into a row that guards nothing is written as before.
            assertTrue(writes(body, "note", kt), project + ": the note is no longer written\n" + body);
        }
    }

    @Test
    public void aListOfAnotherDomainsGuardedRowsIsNotWritten() throws Exception {
        File[] projects = {layeredJava, layeredKotlin, hexJava, hexKotlin};
        for (File project : projects) {
            boolean kt = project == layeredKotlin || project == hexKotlin;
            String mapper = found(project, kt ? "QmclubMapper.kt" : "QmclubMapper.java");
            String body = reverse(mapper, "QmClubDTO", "QmClub", kt);
            assertFalse(writes(body, "members", kt), project + ": the members are written\n" + body);
            // Rows that guard nothing are written as before - the check above reads the generated form.
            assertTrue(writes(body, "notes", kt), project + ": the notes are no longer written\n" + body);
            // The forward direction still hands them out: anyone may read a member.
            assertTrue(mapper.contains("toQmMemberDTOList("), project + ": " + mapper);
        }
    }

    /**
     * A member arrives with its root in the DDD readings - that is the design -
     * but what only a service hashes or checks (@password, @access(admin)) is
     * not written through it: a member has no service of its own. Layered, the
     * member is another guarded row and is not written at all.
     */
    @Test
    public void aMembersPasswordAndAdminFieldsAreNotWrittenThroughItsRoot() throws Exception {
        for (File project : new File[]{hexJava, hexKotlin}) {
            boolean kt = project == hexKotlin;
            String mapper = found(project, kt ? "QdshopMapper.kt" : "QdshopMapper.java");
            String root = reverse(mapper, "QdCustomerDTO", "QdCustomer", kt);
            assertTrue(root.contains("mapping(d, m, isSkipNull, c)"), project + ": the root no longer writes its member\n" + root);
            String member = reverse(mapper, "QdAccountDTO", "QdAccount", kt);
            assertFalse(writes(member, "secret", kt), project + ": the password is written\n" + member);
            assertFalse(writes(member, "tier", kt), project + ": the admin field is written\n" + member);
            assertTrue(writes(member, "label", kt), project + ": the label is no longer written\n" + member);
        }
        for (File project : new File[]{layeredJava, layeredKotlin}) {
            boolean kt = project == layeredKotlin;
            String mapper = found(project, kt ? "QdshopMapper.kt" : "QdshopMapper.java");
            String root = reverse(mapper, "QdCustomerDTO", "QdCustomer", kt);
            assertFalse(writes(root, "account", kt), project + ": the account row is written\n" + root);
        }
        String log = LOG.toString();
        assertTrue(log.contains("QdAccount.secret is not written from a DTO: QdAccount is a member of QdCustomer's aggregate"), log);
        assertTrue(log.contains("QdAccount.tier is not written from a DTO"), log);
        assertFalse(log.contains("QdAccount.label is not written"), log);
    }

    /** @rollup(QdItem)'s own value is no discriminator in the v3 readings either: the entity's name, as 2.x. */
    @Test
    public void aRolledUpEntityIsToldApartByItsName() throws Exception {
        for (File project : new File[]{layeredJava, layeredKotlin, hexJava, hexKotlin}) {
            boolean kt = project == layeredKotlin || project == hexKotlin;
            String book = found(project, kt ? "QdBook.kt" : "QdBook.java");
            assertTrue(book.contains("@DiscriminatorValue(\"QdBook\")"), project + ": " + book);
        }
    }

    /** A cascade written as a native JPA annotation is one; a mention of the word is not. */
    @Test
    public void aNativeCascadeIsACascade() {
        io.elasticore.base.model.pub.rules.EntitySpec e = new io.elasticore.base.model.pub.rules.EntitySpec("QxProfile")
                .addField(new Loader().parse("a", "QmMember @jpa:OneToOne(cascade = CascadeType.MERGE)"))
                .addField(new Loader().parse("b", "QmMember @OneToOne @jpa:Comment(\"no cascade here\")"))
                .addField(new Loader().parse("c", "QmMember @OneToOne @cascade"));
        java.util.Map<String, Boolean> seen = new java.util.HashMap<String, Boolean>();
        for (io.elasticore.base.model.pub.rules.FieldSpec f : e.fields()) {
            seen.put(f.name(), io.elasticore.base.model.pub.rules.base.ServiceAccessRules.cascades(f));
        }
        assertTrue(seen.get("a"), seen.toString());
        assertFalse(seen.get("b"), seen.toString());
        assertTrue(seen.get("c"), seen.toString());
    }

    private static final class Loader extends io.elasticore.base.model.loader.module.AbstractModelLoader {
        io.elasticore.base.model.entity.Field parse(String name, String line) {
            return parseFieldLine(name, line, null);
        }
    }

    @Test
    public void theBuildSaysWhichFieldIsNotWritten() {
        String log = LOG.toString();
        assertTrue(log.contains("QmProfile.member is not written from the DTO: QmMember says who may write it"), log);
        assertTrue(log.contains("QmClub.members is not written from the DTO: QmMember says who may write it"), log);
        assertFalse(log.contains("QmProfile.note is not written"), log);
        assertFalse(log.contains("QmProfile.buddy is not written"), log);
    }

    @AfterAll
    static void clean() throws Exception {
        java.lang.reflect.Field tmp = io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
        SpecFactory.referenceRequiredByKey(false);
        for (File dir : new File[]{layeredJava, layeredKotlin, hexJava, hexKotlin}) {
            if (dir == null || !dir.exists()) continue;
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, java.io.IOException e) throws java.io.IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
