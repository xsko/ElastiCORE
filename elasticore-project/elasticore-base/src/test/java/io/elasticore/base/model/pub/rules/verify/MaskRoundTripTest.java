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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * Issue 270: a field the response writes masked ({@code @Mask(MaskType.X)}, named in env.yml) keeps its stored value
 * on an update when the text sent is the stored value masked - in every v3 update: JPA, MyBatis and MongoDB, Java and
 * Kotlin, layered and hexagonal. A create writes what it is sent; a model without a masked field gets no line. That
 * the generated line compiles and keeps the value is the samples' (BoardEncryptTest, CommentEncryptTest, ShopApi).
 */
public class MaskRoundTripTest {

    private static final String MASK = "io.elasticore.springboot3.mask.Mask";

    private static final String ANNOTATIONS = String.join(
            "\n",
            "    annotations:",
            "      maskphone: \"@dto:" + MASK + "(io.elasticore.springboot3.mask.MaskType.PHONE)\"",
            "      maskemail: \"@dto:" + MASK + "(value = io.elasticore.springboot3.mask.MaskType.EMAIL)\"",
            "      maskown: \"@dto:com.acme.rt.mask.Mask(com.acme.rt.mask.MaskType.NAME)\"",
            "      maskodd: \"@dto:" + MASK + "(io.elasticore.springboot3.mask.MaskType.PHONE.name())\"",
            "      other: \"@dto:com.fasterxml.jackson.annotation.JsonInclude\"");

    private static String model(boolean encrypt) {
        return String.join(
                "\n",
                "entity:",
                "  MkMember:",
                "    meta: entity @expose(20)",
                "    fields:",
                "      memberId: long @id @sequence",
                "      name: string(50)! @search(like)",
                "      phone: string(20) @env:maskphone",
                "      email: string(200) @env:maskemail" + (encrypt ? " @encrypt" : ""),
                "      memo: string(100) @env:other",
                "      nick: string(30) @env:maskown",
                "      code: string(30) @env:maskodd",
                "      stamp: string(30) @env:maskphone @readonly",
                "");
    }

    private static final String PLAIN = String.join(
            "\n",
            "entity:",
            "  MkMember:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      memberId: long @id @sequence",
            "      name: string(50)! @search(like)",
            "      phone: string(20) @env:other",
            "");

    private static final String JAVA_PHONE = "if (entity.getPhone() != null"
            + " && io.elasticore.springboot3.mask.MaskType.PHONE.apply(entity.getPhone()).equals(dto.getPhone()))"
            + " dto.setPhone(entity.getPhone());";

    private static final String JAVA_EMAIL = "if (entity.getEmail() != null"
            + " && io.elasticore.springboot3.mask.MaskType.EMAIL.apply(entity.getEmail()).equals(dto.getEmail()))"
            + " dto.setEmail(entity.getEmail());";

    private static final String KOTLIN_PHONE = "entity.phone?.let"
            + " { if (io.elasticore.springboot3.mask.MaskType.PHONE.apply(it) == dto.phone) dto.phone = it }";

    private static final String KOTLIN_EMAIL = "entity.email?.let"
            + " { if (io.elasticore.springboot3.mask.MaskType.EMAIL.apply(it) == dto.email) dto.email = it }";

    private static final List<File> projects = new ArrayList<File>();

    @AfterAll
    static void cleanUp() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        for (File project : projects) delete(project.toPath());
    }

    @Test
    public void aLayeredJpaUpdateKeepsAMaskedFieldSentBackAsShown() throws Exception {
        String[] log = new String[1];
        File project = run("layered", "java", "jpa", model(true), null, log);
        String core = found(project, "MkMemberCoreService.java");
        String update = method(core, "public MkMemberDTO update(");
        assertTrue(update.contains(JAVA_PHONE), update);
        // @encrypt: the entity holds the value the converter decrypted, so the comparison is with the plain value.
        assertTrue(update.contains(JAVA_EMAIL), update);
        // After the row is found and its version checked, before the mapper writes the DTO.
        assertTrue(update.indexOf("EntityNotFoundException") < update.indexOf(JAVA_PHONE), update);
        assertTrue(update.indexOf(JAVA_EMAIL) < update.indexOf("Mapper.mapping(dto, entity"), update);
        // Only the mask: another @dto: annotation, another class named Mask, a @readonly field are not touched.
        assertFalse(update.contains("getMemo()"), update);
        assertFalse(update.contains("getNick()"), update);
        assertFalse(update.contains("getStamp()"), update);
        assertFalse(update.contains("getCode()"), update);
        // A mask whose argument is not a MaskType constant is said, and written as sent.
        assertTrue(log[0].contains("MkMember.code") && log[0].contains("names no MaskType constant"), log[0]);
        // Only the update: a create writes what it is sent, the batch update goes through update(dto).
        assertEquals(2, occurrences(core, "MaskType."), core);
        assertTrue(method(core, "public void batchUpdate(").contains("update(dto);"), core);
    }

    @Test
    public void aKotlinJpaUpdateKeepsAMaskedFieldSentBackAsShown() throws Exception {
        String[] log = new String[1];
        File project = run("layered", "kotlin", "jpa", model(true), null, log);
        String core = found(project, "MkMemberCoreService.kt");
        String update = method(core, "open fun update(");
        assertTrue(update.contains(KOTLIN_PHONE), update);
        assertTrue(update.contains(KOTLIN_EMAIL), update);
        assertTrue(update.indexOf(KOTLIN_EMAIL) < update.indexOf("Mapper.mapping(dto, entity"), update);
        assertEquals(2, occurrences(core, "MaskType."), core);
    }

    @Test
    public void aHexagonalJpaUpdateKeepsAMaskedFieldSentBackAsShown() throws Exception {
        String[] log = new String[1];
        File project = run("hexagonal", "java", "jpa", model(true), null, log);
        String core = found(project, "MkMemberCoreService.java");
        String update = method(core, "public MkMemberDTO update(");
        assertTrue(update.contains(JAVA_PHONE), update);
        assertTrue(update.contains(JAVA_EMAIL), update);
        assertEquals(2, occurrences(core, "MaskType."), core);
    }

    @Test
    public void aMyBatisUpdateKeepsAMaskedFieldSentBackAsShown() throws Exception {
        for (String architecture : new String[] {"layered", "hexagonal"}) {
            String[] log = new String[1];
            File project = run(architecture, "java", "mybatis", model(true), null, log);
            String core = found(project, "MkMemberCoreService.java");
            String update = method(core, "public MkMemberDTO update(");
            assertTrue(update.contains(JAVA_PHONE), architecture + "\n" + update);
            // The MyBatis type handler decrypts what it reads: the plain value again.
            assertTrue(update.contains(JAVA_EMAIL), architecture + "\n" + update);
            assertTrue(update.indexOf(JAVA_EMAIL) < update.indexOf("Mapper.mapping(dto, entity"), update);
            assertEquals(2, occurrences(core, "MaskType."), architecture + "\n" + core);
        }
    }

    @Test
    public void aMongoUpdateKeepsAMaskedFieldSentBackAsShown() throws Exception {
        String[] log = new String[1];
        File project = run("layered", "java", "mongo", model(false), null, log);
        String core = found(project, "MkMemberCoreService.java");
        String update = method(core, "public MkMemberDTO update(");
        assertTrue(update.contains(JAVA_PHONE), update);
        assertTrue(update.contains(JAVA_EMAIL), update);
        assertEquals(2, occurrences(core, "MaskType."), core);

        project = run("layered", "kotlin", "mongo", model(false), null, log);
        core = found(project, "MkMemberCoreService.kt");
        update = method(core, "fun update(");
        assertTrue(update.contains(KOTLIN_PHONE), update);
        assertTrue(update.contains(KOTLIN_EMAIL), update);
        assertEquals(2, occurrences(core, "MaskType."), core);
    }

    /**
     * runtimePackages: the mask written with the runtime's package is renamed with the rest of the code, the
     * comparison too; written with the project's package, it is recognised under that name, and the comparison names
     * the MaskType beside it. Without the mapping, a Mask of another package is someone else's annotation.
     */
    @Test
    public void theMaskIsFoundUnderTheRuntimePackagesTheProjectRenamesItTo() throws Exception {
        String[] log = new String[1];
        File project = run("layered", "java", "jpa", model(false), "io.elasticore.springboot3=com.acme.rt", log);
        String update = method(found(project, "MkMemberCoreService.java"), "public MkMemberDTO update(");
        assertTrue(update.contains(JAVA_PHONE.replace("io.elasticore.springboot3", "com.acme.rt")), update);
        assertTrue(
                update.contains("if (entity.getNick() != null"
                        + " && com.acme.rt.mask.MaskType.NAME.apply(entity.getNick()).equals(dto.getNick()))"
                        + " dto.setNick(entity.getNick());"),
                update);
        assertFalse(update.contains("io.elasticore.springboot3"), update);
        assertTrue(
                found(project, "MkMemberDTO.java").contains("@com.acme.rt.mask.Mask(com.acme.rt.mask.MaskType.PHONE)"));
    }

    /** Without a masked field the update is what it was: no line, no comment. */
    @Test
    public void aModelWithoutAMaskedFieldGetsNoLine() throws Exception {
        String[] log = new String[1];
        for (String[] run : new String[][] {
            {"layered", "java", "jpa", "MkMemberCoreService.java"},
            {"layered", "kotlin", "jpa", "MkMemberCoreService.kt"},
            {"layered", "java", "mybatis", "MkMemberCoreService.java"},
            {"layered", "java", "mongo", "MkMemberCoreService.java"}
        }) {
            File project = run(run[0], run[1], run[2], PLAIN, null, log);
            String core = found(project, run[3]);
            assertFalse(core.contains("MaskType"), String.join(" ", run) + "\n" + core);
            assertFalse(core.contains("A masked field"), String.join(" ", run) + "\n" + core);
        }
    }

    // ---- helpers -------------------------------------------------------------------

    private static File run(
            String architecture, String language, String mode, String model, String runtimePackages, String[] log)
            throws Exception {
        File project = Files.createTempDirectory("elcore-mask").toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/mkforum");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: mkforum",
                                "    framework: springboot",
                                "    mode: " + mode,
                                "    j2ee: jakarta",
                                ANNOTATIONS,
                                "  namespace:",
                                "    base: io.acme.mkforum",
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
            RuntimePackages.PROPERTY,
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
            if ("layered".equals(architecture)) System.setProperty("elasticore.rules.v3", "true");
            if (runtimePackages != null) System.setProperty(RuntimePackages.PROPERTY, runtimePackages);
            System.setProperty(DdlConfig.DATABASES, "postgresql");
            int code = RulesGenRunner.run(new String[0]);
            assertEquals(0, code, "the run's exit: " + project);
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

    /** From the signature to the next method's javadoc. */
    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, signature + " not in\n" + source);
        int end = source.indexOf("/**", start);
        return source.substring(start, end < 0 ? source.length() : end);
    }

    private static String found(File project, String name) throws IOException {
        try (Stream<Path> all = Files.walk(project.toPath())) {
            List<Path> hits = new ArrayList<Path>();
            all.filter(p -> p.getFileName().toString().equals(name)).forEach(hits::add);
            assertEquals(1, hits.size(), name + " in " + project + ": " + hits);
            return new String(Files.readAllBytes(hits.get(0)), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    private static int occurrences(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) n++;
        return n;
    }

    private static void delete(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> all = Files.walk(root)) {
            List<Path> paths = new ArrayList<Path>();
            all.forEach(paths::add);
            for (int i = paths.size() - 1; i >= 0; i--) Files.deleteIfExists(paths.get(i));
        }
    }
}
