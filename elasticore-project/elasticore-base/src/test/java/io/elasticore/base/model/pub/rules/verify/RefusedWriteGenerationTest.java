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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue 344: a generated write refused after the request was put onto the row it read - by beforeSave, a
 * reference check, an invariant, a command's rule - discards that row from what the transaction reads.
 * Update, update by condition and the commands, over JPA (Java, Kotlin; layered and hexagonal) and MyBatis
 * (layered and hexagonal); a create, whose row nothing else holds yet, is left as it was.
 */
public class RefusedWriteGenerationTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  RwOrder:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      orderId: long @id @sequence",
            "      memo:    string(30) @editable",
            "    commands:",
            "      close:",
            "");

    private static final String JPA_DISCARD = "io.elasticore.springboot3.entity.RefusedWrite.discard(";
    private static final String MYBATIS_DISCARD =
            "throw io.elasticore.springboot3.mybatis.RefusedWrite.discard(refused);";

    private static File layered;
    private static File hexagonal;
    private static File kotlin;
    private static File mybatis;
    private static File mybatisHex;
    private static final java.util.List<File> mongo = new java.util.ArrayList<File>();

    @BeforeAll
    static void generate() throws Exception {
        layered = project("elcore-refused-layered", "jpa");
        run(layered, "java", "layered");
        hexagonal = project("elcore-refused-hex", "jpa");
        run(hexagonal, "java", "hexagonal");
        kotlin = project("elcore-refused-kt", "jpa");
        run(kotlin, "kotlin", "hexagonal");
        mybatis = project("elcore-refused-mybatis", "mybatis");
        run(mybatis, "java", "layered");
        mybatisHex = project("elcore-refused-mybatis-hex", "mybatis");
        run(mybatisHex, "java", "hexagonal");
    }

    @Test
    public void aJpaUpdateReadsTheRowAgainWhenRefused() throws Exception {
        String core = found(layered, "RwOrderCoreService.java");
        String update = squashed(method(core, "public RwOrderDTO update("));
        assertTrue(update.contains("try { RwoMapper.mapping(dto, entity, true, helper.getEntityManager());"), update);
        assertTrue(update.contains("} catch (RuntimeException refused) {"), update);
        assertTrue(update.contains("throw " + JPA_DISCARD + "helper.getEntityManager(), entity, refused); }"), update);
        // The checks before the mapping stay outside: they change nothing.
        assertTrue(update.indexOf("throw new EntityNotFoundException") < update.indexOf("try {"), update);
        String byCondition = squashed(method(core, "public int updateBySpecification(RwOrderDTO dto, Specification<"));
        assertTrue(byCondition.contains("for (RwOrder entity : targets) { try {"), byCondition);
        assertTrue(byCondition.contains("throw " + JPA_DISCARD + "em, entity, refused); } }"), byCondition);
        // A create's row is new: nothing else holds it.
        assertFalse(squashed(method(core, "public RwOrderDTO save(")).contains("RefusedWrite"), core);
    }

    @Test
    public void theHexagonalServiceDiscardsThroughItsEntityManager() throws Exception {
        String core = found(hexagonal, "RwOrderCoreService.java");
        String update = squashed(method(core, "public RwOrderDTO update("));
        assertTrue(update.contains("throw " + JPA_DISCARD + "entityManager, entity, refused);"), update);
        String close = squashed(method(core, "public RwOrderDTO close("));
        assertTrue(close.contains("try { entity.close();"), close);
        assertTrue(
                close.contains("} catch (RuntimeException refused) { throw " + JPA_DISCARD
                        + "entityManager, entity, refused); } }"),
                close);
        assertTrue(close.indexOf("orElseThrow") < close.indexOf("try {"), close);
    }

    @Test
    public void kotlinDiscardsAlike() throws Exception {
        String core = found(kotlin, "RwOrderCoreService.kt");
        String update = squashed(method(core, "open fun update("));
        assertTrue(update.contains("} catch (refused: RuntimeException) { // Refused"), update);
        assertTrue(update.contains("throw " + JPA_DISCARD + "entityManager, entity, refused) }"), update);
        String byCondition = squashed(method(core, "open fun updateBySpecification(dto: RwOrderDTO, spec:"));
        assertTrue(byCondition.contains("throw " + JPA_DISCARD + "em, entity, refused) } }"), byCondition);
        String close = squashed(method(core, "open fun close("));
        assertTrue(close.contains("try { entity.close()"), close);
        assertTrue(
                close.contains("} catch (refused: RuntimeException) { throw " + JPA_DISCARD
                        + "entityManager, entity, refused) } }"),
                close);
    }

    @Test
    public void myBatisClearsTheSessionCacheWhenRefused() throws Exception {
        String core = found(mybatis, "RwOrderCoreService.java");
        String update = squashed(method(core, "public RwOrderDTO update("));
        assertTrue(update.contains("try {"), update);
        assertTrue(update.contains(MYBATIS_DISCARD), update);
        assertTrue(update.indexOf("try {") < update.indexOf("write(entity);"), update);
        String byCondition = squashed(method(core, "public int updateBySpecification("));
        assertTrue(byCondition.contains(MYBATIS_DISCARD), byCondition);
        assertEquals(2, occurrences(core, MYBATIS_DISCARD), core);
        assertFalse(core.contains(JPA_DISCARD), core);

        String hex = found(mybatisHex, "RwOrderCoreService.java");
        assertTrue(squashed(method(hex, "public RwOrderDTO update(")).contains(MYBATIS_DISCARD), hex);
        assertTrue(squashed(method(hex, "public int updateBySpecification(")).contains(MYBATIS_DISCARD), hex);
        String close = squashed(method(hex, "public RwOrderDTO close("));
        assertTrue(close.contains("try { entity.close();"), close);
        assertTrue(close.contains(MYBATIS_DISCARD), close);
        assertFalse(hex.contains(JPA_DISCARD), hex);
    }

    /**
     * MongoDB reads a new object each time, so nothing is discarded: no entity manager, no session cache. On 3.1
     * MongoDB is layered (Java, Kotlin); ddd and hexagonal are refused (RulesConfig.mongoRefusal), and
     * RefusedWrites asks the model (MongoDocs), so a command service over MongoDB would get none either.
     */
    @Test
    public void mongoDiscardsNothing() throws Exception {
        for (String language : new String[] {"java", "kotlin"}) {
            File project = project("elcore-refused-mongo-" + language, "mongo");
            mongo.add(project);
            run(project, language, "layered");
            String core = found(project, "java".equals(language) ? "RwOrderCoreService.java" : "RwOrderCoreService.kt");
            assertFalse(core.contains("RefusedWrite"), core);
            assertFalse(core.contains("entityManager"), core);
            assertFalse(core.contains("catch (refused"), core);
        }
    }

    // ---- the harness ------------------------------------------------------------

    private static File project(String name, String mode) throws Exception {
        File dir = Files.createTempDirectory(name).toFile();
        File blueprint = new File(dir, "src/main/resources/blueprint/rwo");
        assertTrue(new File(blueprint, "model").mkdirs());
        Files.write(
                new File(blueprint, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: rwo",
                                "    framework: springboot",
                                "    mode: " + mode,
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    base: io.acme.rwo",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private static void run(File project, String language, String architecture) {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v3"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            if ("layered".equals(architecture)) System.setProperty("elasticore.rules.v3", "true");
            assertEquals(0, RulesGenRunner.run(new String[0]), "the run failed: " + project);
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
    }

    private static String found(File project, String name) throws Exception {
        try (java.util.stream.Stream<Path> all = Files.walk(project.toPath())) {
            for (Path p : all.toArray(Path[]::new)) {
                if (p.getFileName().toString().equals(name)) {
                    return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                }
            }
        }
        assertTrue(false, "not generated: " + name + " in " + project);
        return null;
    }

    private static String squashed(String text) {
        return text.replaceAll("\\s+", " ");
    }

    private static int occurrences(String text, String part) {
        int n = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) n++;
        return n;
    }

    /** The method's text, from its signature to its closing brace (a member of a top-level class). */
    private static String method(String text, String signature) {
        String t = text.replace("\r\n", "\n");
        int at = t.indexOf(signature);
        assertTrue(at >= 0, signature + " not in\n" + text);
        int end = t.indexOf("\n    }\n", at);
        return t.substring(at, end < 0 ? t.length() : end + 6);
    }

    @AfterAll
    static void clean() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
        java.util.List<File> all =
                new java.util.ArrayList<File>(java.util.Arrays.asList(layered, hexagonal, kotlin, mybatis, mybatisHex));
        all.addAll(mongo);
        for (File dir : all) {
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
