package io.elasticore.base.model.pub.rules.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.elasticore.base.model.pub.rules.base.EncryptRules;
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
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * An {@code @encrypt} field: the column kept encrypted by the runtime (a JPA converter, a MyBatis type handler), as
 * long as the ciphertext can be, and - searched by equality - a blind index beside it that the service writes and the
 * search compares. What the runtime does with the keys is FieldCryptoTest's; what a request sees, the samples'.
 */
public class EncryptFieldTest {

    private static final String MODEL = String.join(
            "\n",
            "enumeration:",
            "  EnStatus:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(3) @code",
            "    enum:",
            "      ACTIVE: ACT",
            "      CLOSED: CLS",
            "",
            "entity:",
            "  EnMember:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      memberId: long @id @sequence",
            "      name:     string(50)! @search(like)",
            "      phone:    string(20) @encrypt @s(eq)",
            "      email:    string(100) @encrypt @db(mail_addr) @search",
            "      memo:     text @encrypt",
            "      status:   EnStatus",
            "    states:",
            "      status:",
            "        initial: ACTIVE",
            "        close: ACTIVE -> CLOSED",
            "    commands:",
            "      close:",
            "        phone: string(20)",
            "");

    private static final String CALL = "entity.indexEncrypted();";

    private static final String METHOD = String.join(
            "\n",
            "\tpublic void indexEncrypted() {",
            "\t    this.setPhoneBidx(io.elasticore.springboot3.crypto.Bidx.of(this.getPhone()));",
            "\t    this.setEmailBidx(io.elasticore.springboot3.crypto.Bidx.of(this.getEmail()));",
            "\t}");

    private static final String CONVERT =
            "@jakarta.persistence.Convert(converter = io.elasticore.springboot3.crypto.EncryptConverter.class)";

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
    public void theColumnHoldsTheCiphertextOfTheDeclaredLength() {
        // v1: + base64(12 + 4n + 16): a phone of 20 characters needs 147, an unsized string 1403.
        assertEquals(147, EncryptRules.columnLength(20));
        assertEquals(1403, EncryptRules.columnLength(255));
        assertEquals(47, EncryptRules.columnLength(1));
        for (int n = 1; n <= 300; n++) {
            int base64 = (12 + 4 * n + 16 + 2) / 3 * 4;
            assertEquals(base64 + "v1:".length(), EncryptRules.columnLength(n));
        }
    }

    @Test
    public void aLayeredJpaEntityKeepsTheColumnEncryptedAndIndexesTheSearchedOnes() throws Exception {
        String[] log = new String[1];
        File project = run("elcore-enc-layered", "layered", "java", "jpa", MODEL, false, 0, log);
        String entity = found(project, "EnMember.java");
        assertTrue(
                entity.contains("@Column(name = \"phone\", length = 147)\n\t" + CONVERT + "\n\tprivate String phone;"),
                entity);
        assertTrue(entity.contains("@Column(name = \"memo\", columnDefinition = \"TEXT\")\n\t" + CONVERT), entity);
        assertTrue(entity.contains("private String phoneBidx;"), entity);
        assertTrue(entity.contains("@Column(name = \"phone_bidx\", length = 22)"), entity);
        assertTrue(entity.contains("@Column(name = \"mail_addr_bidx\", length = 22)"), entity);
        assertFalse(entity.contains("memoBidx"), entity);
        assertTrue(entity.contains(METHOD), entity);
        assertEquals(3, occurrences(entity, CONVERT), entity);
        assertTrue(entity.contains("columnList = \"phone_bidx\""), entity);
        assertTrue(entity.contains("columnList = \"mail_addr_bidx\""), entity);

        String core = found(project, "EnMemberCoreService.java");
        assertEquals(3, occurrences(core, CALL), core);
        String save = section(core, "public EnMemberDTO save(EnMemberDTO dto) {");
        assertTrue(save.indexOf(CALL) < save.indexOf("beforeSave(entity, dto);"), save);

        // The index is the model's: no DTO carries it, and the DTO checks the declared length.
        String dto = found(project, "EnMemberDTO.java");
        assertFalse(dto.contains("Bidx"), dto);
        assertTrue(dto.contains("@Size(max=20)"), dto);
        String srch = found(project, "EnMemberSrchDTO.java");
        assertFalse(srch.contains("Bidx"), srch);
        assertTrue(srch.contains("private String phone;"), srch);

        // The search compares the indexes, never the encrypted column.
        String mapper = found(project, "EnforumMapper.java");
        assertTrue(
                mapper.contains("c.equal(r.get(\"phoneBidx\"),io.elasticore.springboot3.crypto.Bidx.of(phone))"),
                mapper);
        assertTrue(
                mapper.contains("c.equal(r.get(\"emailBidx\"),io.elasticore.springboot3.crypto.Bidx.of(email))"),
                mapper);
        assertFalse(mapper.contains("r.get(\"phone\")"), mapper);
        assertFalse(log[0].contains("unresolved type"), log[0]);

        // The DDL: the long column, the index column and its index.
        String ddl = read(new File(project, "src/main/resources/db/schema-postgresql.sql"));
        assertTrue(ddl.contains("phone VARCHAR(147)"), ddl);
        assertTrue(ddl.contains("phone_bidx VARCHAR(22)"), ddl);
        assertTrue(ddl.contains("mail_addr VARCHAR(" + EncryptRules.columnLength(100) + ")"), ddl);
        assertTrue(ddl.contains("CREATE INDEX ix_en_member_phone_bidx ON en_member (phone_bidx);"), ddl);

        // The schema description says the column the database has, not the declared length.
        String schema = read(new File(project, "src/main/resources/blueprint/schema.yml"));
        assertTrue(schema.contains("phone: string(20) @Column(name = \"phone\", length = 147)"), schema);
        assertTrue(schema.contains("memo: string @Column(name = \"memo\")"), schema);
        assertTrue(schema.contains("phoneBidx: string(22) @Column(name = \"phone_bidx\", length = 22)"), schema);
    }

    @Test
    public void kotlinWritesTheSameWay() throws Exception {
        File project = run("elcore-enc-kt", "layered", "kotlin", "jpa", MODEL, false, 0, new String[1]);
        String entity = found(project, "EnMember.kt");
        assertTrue(
                entity.contains(
                        "@jakarta.persistence.Convert(converter = io.elasticore.springboot3.crypto.EncryptConverter::class)"),
                entity);
        assertTrue(entity.contains("fun indexEncrypted() {"), entity);
        assertTrue(entity.contains("this.phoneBidx = io.elasticore.springboot3.crypto.Bidx.of(this.phone)\n"), entity);
        String core = found(project, "EnMemberCoreService.kt");
        assertEquals(3, occurrences(core, "entity.indexEncrypted()\n"), core);
        String mapper = found(project, "EnforumMapper.kt");
        assertTrue(mapper.contains("io.elasticore.springboot3.crypto.Bidx.of(phone"), mapper);
        assertFalse(found(project, "EnMemberDTO.kt").contains("Bidx"), found(project, "EnMemberDTO.kt"));
    }

    @Test
    public void myBatisBindsTheColumnThroughTheHandlerAndSearchesTheIndex() throws Exception {
        String model = MODEL.substring(0, MODEL.indexOf("    states:"));
        for (String architecture : new String[] {"layered", "hexagonal"}) {
            File project = run(
                    "elcore-enc-mybatis-" + architecture,
                    architecture,
                    "java",
                    "mybatis",
                    model,
                    false,
                    0,
                    new String[1]);
            String core = found(project, "EnMemberCoreService.java");
            assertEquals(3, occurrences(core, CALL), architecture + "\n" + core);
            String xml = found(project, "EnMemberRepository.xml");
            assertTrue(xml.contains("typeHandler=io.elasticore.springboot3.crypto.EncryptTypeHandler"), xml);
            assertTrue(xml.contains("typeHandler=\"io.elasticore.springboot3.crypto.EncryptTypeHandler\""), xml);
            assertTrue(xml.contains("@io.elasticore.springboot3.crypto.Bidx@of(s.phone)"), xml);
            assertTrue(xml.contains("t.phone_bidx = #{"), xml);
            assertFalse(xml.contains("t.phone = #{"), xml);
            String entity = "layered".equals(architecture)
                    ? found(project, "EnMember.java")
                    : found(project, "EnMemberBase.java");
            assertTrue(entity.contains(METHOD), architecture + "\n" + entity);
            assertFalse(entity.contains("jakarta.persistence.Convert"), entity);
            String ddl = read(new File(project, "src/main/resources/db/schema-postgresql.sql"));
            assertTrue(ddl.contains("phone VARCHAR(147)"), ddl);
            assertTrue(ddl.contains("phone_bidx VARCHAR(22)"), ddl);
        }
    }

    @Test
    public void aCommandThatSetsTheFieldIndexesItAfterItsMove() throws Exception {
        File project = run("elcore-enc-command", "hexagonal", "java", "jpa", MODEL, false, 0, new String[1]);
        String base = found(project, "EnMemberBase.java");
        assertTrue(base.contains(METHOD), base);
        String core = found(project, "EnMemberCoreService.java");
        String close = section(core, "public EnMemberDTO close(");
        int move = close.indexOf("entity.close(");
        assertTrue(move >= 0, close);
        assertTrue(move < close.indexOf(CALL), close);
        assertTrue(close.indexOf(CALL) < close.indexOf(".save(entity)"), close);
    }

    @Test
    public void theTypeScriptClientDoesNotSeeTheIndex() throws Exception {
        File project = run("elcore-enc-ts", "layered", "typescript", "jpa", MODEL, false, 0, new String[1]);
        String ts = found(project, "EnMemberDTO.ts");
        assertTrue(ts.contains("phone"), ts);
        assertFalse(ts.contains("Bidx"), ts);
    }

    @Test
    public void aModelWithoutEncryptIsWrittenAsBefore() throws Exception {
        String model = String.join(
                "\n",
                "entity:",
                "  EnNote:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      noteId: long @id @sequence",
                "      phone:  string(20) @search(eq)",
                "");
        for (String mode : new String[] {"jpa", "mybatis", "mongo"}) {
            File project = run("elcore-enc-none-" + mode, "layered", "java", mode, model, false, 0, new String[1]);
            String core = found(project, "EnNoteCoreService.java");
            assertFalse(core.contains("indexEncrypted"), core);
            assertEquals(0, occurrences(core, "\n\n        beforeSave(entity, dto);"), core);
            String entity = found(project, "EnNote.java");
            assertFalse(entity.contains("Bidx"), entity);
            assertFalse(entity.contains("crypto"), entity);
        }
    }

    @Test
    public void whatAnEncryptedColumnCannotDoStopsTheBuild() throws Exception {
        String like = "an encrypted column is searched by equality only (@search(eq))";
        for (String condition : new String[] {"like", "%_%", "_%", "%_", "%__%", "__=", ">=", "between", "in", "!="}) {
            assertRefused(
                    "elcore-enc-" + Math.abs(condition.hashCode()),
                    field("phone: string(20) @encrypt @search(" + condition + ")"),
                    "EnMember.phone: @search(" + condition + ") - " + like);
        }
        // The condition is the part before a property name.
        assertRefused(
                "elcore-enc-named",
                field("phone: string(20) @encrypt @search(like, tel)"),
                "EnMember.phone: @search(like) - " + like);
        assertRefused("elcore-enc-words", field("phone: \"string(20) @encrypt @search(words: phone)\""), like);
        assertRefused(
                "elcore-enc-long",
                field("age: long @encrypt"),
                "EnMember.age: @encrypt is for a string field (phone: string(20) @encrypt)");
        assertRefused(
                "elcore-enc-list",
                field("tags: List<String> @encrypt"),
                "EnMember.tags: @encrypt is for a string field");
        assertRefused(
                "elcore-enc-unique",
                field("phone: string(20) @encrypt @unique"),
                "EnMember.phone: @unique on an encrypted column holds nothing back");
        assertRefused(
                "elcore-enc-key",
                field("code: string(20) @pk @encrypt"),
                "EnMember.code: a key is not encrypted: it is compared and joined by its value");
        assertRefused(
                "elcore-enc-formula",
                field("label: string(20) @encrypt @formula(upper(title))"),
                "EnMember.label: a @formula is computed by the database, not stored - nothing to encrypt");
        assertRefused(
                "elcore-enc-taken",
                field("phone: string(20) @encrypt @search(eq)\n      phoneBidx: string(22)"),
                "EnMember.phone: its blind index is the field phoneBidx, and EnMember already has one of that name");
    }

    @Test
    public void aMemberOrAValueObjectIsRefusedWhereTheServiceCannotIndex() throws Exception {
        String model = String.join(
                "\n",
                "entity:",
                "  EnMember:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      memberId: long @id @sequence",
                "  EnCard:",
                "    meta: entity @aggregate(EnMember)",
                "    fields:",
                "      cardId: long @id @sequence",
                "      number: string(20) @encrypt @search(eq)",
                "      holder: string(20) @encrypt",
                "  EnAddress:",
                "    meta: entity @embeddable",
                "    fields:",
                "      street: string(50) @encrypt",
                "");
        String[] log = new String[1];
        run("elcore-enc-member", "layered", "java", "jpa", model, false, 1, log);
        assertTrue(
                log[0].contains("EnCard.number: an encrypted field searched by equality keeps a blind index the"
                        + " service that saves its entity writes, and EnCard is saved through EnMember"),
                log[0]);
        assertFalse(log[0].contains("EnCard.holder:"), log[0]);
        assertTrue(log[0].contains("EnAddress.street: @encrypt is for an entity's field"), log[0]);
    }

    @Test
    public void aDeclaredSearchOfAnEncryptedFieldIsByItsIndexOrRefused() throws Exception {
        String model = String.join(
                "\n",
                "entity:",
                "  EnMember:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      memberId: long @id @sequence",
                "      phone:    string(20) @encrypt",
                "dto:",
                "  EnMemberFind:",
                "    meta: dto @searchable(entity=EnMember)",
                "    fields:",
                "      phone: string @search(like)",
                "");
        String[] log = new String[1];
        run("elcore-enc-declared", "layered", "java", "jpa", model, false, 1, log);
        assertTrue(
                log[0].contains("EnMemberFind.phone: @search(like) on phone, which is encrypted - an encrypted"
                        + " column is searched by equality only (@search(eq))"),
                log[0]);

        // By equality it is the blind index - there is none without the entity's own @search(eq).
        String eq = model.replace("phone: string @search(like)", "phone: string @search(eq)");
        run("elcore-enc-declared-eq", "layered", "java", "jpa", eq, false, 1, log);
        assertTrue(
                log[0].contains("EnMemberFind.phone: phone is encrypted and has no blind index to search by - mark"
                        + " it @search(eq) on the entity"),
                log[0]);
        String indexed = eq.replace("phone:    string(20) @encrypt", "phone:    string(20) @encrypt @search(eq)");
        File project = run("elcore-enc-declared-ok", "layered", "java", "jpa", indexed, false, 0, log);
        String mapper = found(project, "EnforumMapper.java");
        assertEquals(
                2,
                occurrences(mapper, "c.equal(r.get(\"phoneBidx\"),io.elasticore.springboot3.crypto.Bidx.of("),
                mapper);
        assertFalse(mapper.contains("r.get(\"phone\")"), mapper);

        // MyBatis says the same.
        run("elcore-enc-declared-mybatis", "layered", "java", "mybatis", model, false, 1, log);
        assertTrue(
                log[0].contains("EnMemberFind.phone: phone is encrypted, and an encrypted column is searched by"
                        + " equality only"),
                log[0]);
    }

    @Test
    public void mongoAndTheTwoXCompatibleOutputStopTheBuild() throws Exception {
        String model = MODEL.substring(0, MODEL.indexOf("    states:"));
        String[] log = new String[1];
        run("elcore-enc-mongo", "layered", "java", "mongo", model, false, 1, log);
        assertTrue(log[0].contains("EnMember.phone: @encrypt is not written for mode: mongodb yet"), log[0]);
        run("elcore-enc-v2", "layered", "java", "jpa", model, true, 1, log);
        assertTrue(
                log[0].contains("EnMember.phone: an @encrypt field is kept encrypted by the 3.0 code; the"
                        + " 2.x-compatible output would store it as sent"),
                log[0]);
    }

    // ------------------------------------------------------------------

    private static String field(String line) {
        return String.join(
                "\n",
                "entity:",
                "  EnMember:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      memberId: long @id @sequence",
                "      title:    string(100)",
                "      " + line,
                "");
    }

    private static void assertRefused(String name, String model, String said) throws Exception {
        String[] log = new String[1];
        run(name, "layered", "java", "jpa", model, false, 1, log);
        assertTrue(log[0].contains(said), said + "\n---\n" + log[0]);
    }

    private static int occurrences(String text, String part) {
        int n = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) n++;
        return n;
    }

    private static String section(String source, String head) {
        int from = source.indexOf(head);
        assertTrue(from >= 0, head + "\n" + source);
        int to = source.indexOf("\n    }", from);
        return source.substring(from, to < 0 ? source.length() : to);
    }

    private static File run(
            String name,
            String architecture,
            String language,
            String mode,
            String model,
            boolean v2,
            Integer exit,
            String[] log)
            throws Exception {
        File project = Files.createTempDirectory(name).toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/enforum");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: enforum",
                                "    framework: springboot",
                                "    mode: " + mode,
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    base: io.acme.enforum",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));

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
            System.setProperty("elasticore.language", "typescript".equals(language) ? "java" : language);
            System.setProperty("elasticore.rules.force", "true");
            if (v2) System.setProperty("elasticore.rules.v2", "true");
            else if ("layered".equals(architecture)) System.setProperty("elasticore.rules.v3", "true");
            if ("typescript".equals(language)) System.setProperty("elasticore.rules.ts", "true");
            System.setProperty(DdlConfig.DATABASES, "postgresql");
            int code = RulesGenRunner.run(new String[0]);
            if (exit != null) assertEquals(exit.intValue(), code, "the run's exit: " + project);
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

    private static String read(File file) throws IOException {
        assertTrue(file.isFile(), file + " was not written");
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String found(File project, String fileName) throws IOException {
        List<File> hits = new ArrayList<File>();
        collect(project, fileName, hits);
        assertEquals(1, hits.size(), fileName + " in " + project + ": " + hits);
        return read(hits.get(0));
    }

    private static void collect(File dir, String fileName, List<File> hits) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, fileName, hits);
            else if (f.getName().equals(fileName)) hits.add(f);
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
