package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.extract.ModelExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
 * A repository method written without {@code query:} is a Spring Data derived
 * query, read from its name - the form the DSL guide's repository example
 * starts with. The 2.x generator stopped on it with a NullPointerException
 * (the method has no query to ask whether it selects), so no source was
 * written; it now declares the method as it is named, without {@code @Query}.
 */
public class DerivedQueryMethodTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Stock:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      stockId: long @id @sequence",
            "      warehouse: string(20)",
            "      onHand: int",
            "",
            "repository:",
            "  Stock:",
            "    methods:",
            "      - id: findByWarehouseAndOnHandGreaterThan",
            "        params:",
            "          warehouse: string",
            "          onHand: int",
            "        return: List<Stock>",
            "",
            "      - id: findByWarehouse",
            "        params:",
            "          warehouse: string",
            "        return: Page<Stock>",
            "        pageable: true",
            "",
            "      - id: findLowStock",
            "        params:",
            "          threshold: int",
            "        return: List<Stock>",
            "        query: \"SELECT s FROM Stock s WHERE s.onHand < :threshold ORDER BY s.onHand\"",
            "");

    private final List<File> projects = new ArrayList<File>();

    /** The 2.x generator, as ./gradlew elcore and output = 'v2' run it. */
    @Test
    public void the2xGeneratorDeclaresTheDerivedQuery() throws Exception {
        File project = project("jpa");
        extract(project);

        String repo = found(project, "StockRepository.java");
        assertTrue(
                repo.contains("List<Stock> findByWarehouseAndOnHandGreaterThan(String warehouse ,Integer onHand);"),
                repo);
        assertTrue(repo.contains("Page<Stock> findByWarehouse(String warehouse, Pageable pageable);"), repo);
        assertEquals(1, count(repo, "@Query("), "only the JPQL method has a query:\n" + repo);
        assertTrue(repo.contains("List<Stock> findLowStock(@Param(\"threshold\") Integer threshold);"), repo);
        assertFalse(repo.contains("Modifying"), repo);
    }

    /** The rule engine's 2.x-compatible output writes what the 2.x generator writes. */
    @Test
    public void theCompatibleOutputMatchesThe2xGenerator() throws Exception {
        File legacy = project("jpa");
        extract(legacy);

        File compatible = project("jpa");
        assertEquals(0, run(compatible, "layered", "java", "elasticore.rules.v2"));
        assertEquals(
                methods(found(legacy, "StockRepository.java")), methods(found(compatible, "StockRepository.java")));
    }

    /** The 2.x MongoDB repository had the same stop. */
    @Test
    public void the2xMongoGeneratorDeclaresTheDerivedQuery() throws Exception {
        File project = project("mongo");
        extract(project);

        String repo = found(project, "StockRepository.java");
        assertTrue(
                repo.contains("List<Stock> findByWarehouseAndOnHandGreaterThan(String warehouse ,Integer onHand);"),
                repo);
        assertTrue(repo.contains("Page<Stock> findByWarehouse(String warehouse, Pageable pageable);"), repo);
        assertEquals(1, count(repo, "@Query("), repo);
        assertFalse(repo.contains("Modifying"), repo);
    }

    @Test
    public void theV3LayeredRepositoryDeclaresTheDerivedQuery() throws Exception {
        File project = project("jpa");
        assertEquals(0, run(project, "layered", "java", "elasticore.rules.v3"));
        String repo = found(project, "StockRepository.java");
        assertTrue(repo.contains("findByWarehouseAndOnHandGreaterThan(String warehouse"), repo);
        assertEquals(1, count(repo, "@Query("), repo);
    }

    @Test
    public void theV3KotlinRepositoryDeclaresTheDerivedQuery() throws Exception {
        File project = project("jpa");
        assertEquals(0, run(project, "layered", "kotlin", null));
        String repo = found(project, "StockRepository.kt");
        assertTrue(repo.contains("fun findByWarehouseAndOnHandGreaterThan(warehouse: String"), repo);
        assertEquals(1, count(repo, "@Query("), repo);
    }

    @Test
    public void theHexagonalRepositoryDeclaresTheDerivedQuery() throws Exception {
        File project = project("jpa");
        assertEquals(0, run(project, "hexagonal", "java", null));
        String repo = found(project, "StockRepository.java");
        assertTrue(repo.contains("findByWarehouseAndOnHandGreaterThan(String warehouse"), repo);
        assertEquals(1, count(repo, "@Query("), repo);
    }

    // ------------------------------------------------------------------
    // A derived query without return: answers by its name - it was void for every name.

    private static final String NO_RETURN = String.join(
            "\n",
            "entity:",
            "  Stock:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      stockId: long @id @sequence",
            "      warehouse: string(20)",
            "      onHand: int",
            "",
            "repository:",
            "  Stock:",
            "    methods:",
            "      - id: findByOnHandLessThan",
            "        params:",
            "          onHand: int",
            "      - id: findByWarehouseOrderByOnHand",
            "        params:",
            "          warehouse: string",
            "        pageable: true",
            "      - id: countByWarehouse",
            "        params:",
            "          warehouse: string",
            "      - id: existsByWarehouse",
            "        params:",
            "          warehouse: string",
            "      - id: getterByWarehouse",
            "        params:",
            "          warehouse: string",
            "      - id: deleteByWarehouse",
            "        params:",
            "          warehouse: string",
            "");

    @Test
    public void withoutReturnThe2xGeneratorAnswersByTheName() throws Exception {
        for (String mode : new String[] {"jpa", "mongo"}) {
            File project = project(mode, NO_RETURN);
            extract(project);
            String repo = found(project, "StockRepository.java");
            assertTrue(repo.contains("List<Stock> findByOnHandLessThan(Integer onHand);"), mode + "\n" + repo);
            assertTrue(
                    repo.contains("Page<Stock> findByWarehouseOrderByOnHand(String warehouse, Pageable pageable);"),
                    mode + "\n" + repo);
            assertTrue(repo.contains("long countByWarehouse(String warehouse);"), mode + "\n" + repo);
            assertTrue(repo.contains("boolean existsByWarehouse(String warehouse);"), mode + "\n" + repo);
            assertTrue(repo.contains("void deleteByWarehouse(String warehouse);"), mode + "\n" + repo);
            // Not a query name as Spring Data reads one (a subject word starts upper case): left void.
            assertTrue(repo.contains("void getterByWarehouse(String warehouse);"), mode + "\n" + repo);
            assertFalse(repo.contains("void findBy"), mode + "\n" + repo);
        }
    }

    @Test
    public void withoutReturnTheCompatibleOutputMatchesThe2xGenerator() throws Exception {
        File legacy = project("jpa", NO_RETURN);
        extract(legacy);
        File compatible = project("jpa", NO_RETURN);
        assertEquals(0, run(compatible, "layered", "java", "elasticore.rules.v2"));
        assertEquals(
                methods(found(legacy, "StockRepository.java")), methods(found(compatible, "StockRepository.java")));
    }

    @Test
    public void withoutReturnTheV3RepositoriesAnswerByTheName() throws Exception {
        File layered = project("jpa", NO_RETURN);
        assertEquals(0, run(layered, "layered", "java", "elasticore.rules.v3"));
        String repo = found(layered, "StockRepository.java");
        assertTrue(repo.contains("List<Stock> findByOnHandLessThan(Integer onHand"), repo);
        assertTrue(repo.contains("long countByWarehouse(String warehouse"), repo);
        assertFalse(repo.contains("void findBy"), repo);

        File hexagonal = project("jpa", NO_RETURN);
        assertEquals(0, run(hexagonal, "hexagonal", "java", null));
        String hex = found(hexagonal, "StockRepository.java");
        assertTrue(hex.contains("List<Stock> findByOnHandLessThan(Integer onHand"), hex);
        assertTrue(hex.contains("boolean existsByWarehouse(String warehouse"), hex);

        File kotlin = project("jpa", NO_RETURN);
        assertEquals(0, run(kotlin, "layered", "kotlin", null));
        String kt = found(kotlin, "StockRepository.kt");
        assertTrue(kt.contains("fun findByOnHandLessThan(onHand: Int"), kt);
        assertTrue(kt.contains("): List<Stock>"), kt);
        assertTrue(kt.contains("fun existsByWarehouse(warehouse: String"), kt);
    }
    // ------------------------------------------------------------------

    private File project(String mode) throws IOException {
        return project(mode, MODEL);
    }

    private File project(String mode, String model) throws IOException {
        File project = Files.createTempDirectory("elcore-derived").toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/inv");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: inv",
                                "    framework: springboot",
                                "    mode: " + mode,
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    entity: io.acme.inv.entity",
                                "    dto: io.acme.inv.dto",
                                "    repository: io.acme.inv.repository",
                                "    service: io.acme.inv.service",
                                "    control: io.acme.inv.controller",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    /** The method declarations of a repository, one per line, in order. */
    private static String methods(String source) {
        StringBuilder sb = new StringBuilder();
        for (String line : source.split("\n")) {
            String t = line.trim();
            if (t.startsWith("@Query(") || (t.endsWith(");") && t.contains("(")))
                sb.append(t).append('\n');
        }
        return sb.toString();
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) n++;
        return n;
    }

    private static int run(File project, String architecture, String language, String output) {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v3",
            "elasticore.rules.v2",
            "elasticore.rules.ts"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        PrintStream out = System.out;
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try {
            for (String k : keys) System.clearProperty(k);
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            if (output != null) System.setProperty(output, "true");
            return RulesGenRunner.run(new String[0]);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        } finally {
            System.setOut(out);
            out.print(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
    }

    /** The 2.x generator, in this JVM: an exception it throws fails the test. */
    private static void extract(File project) throws Exception {
        // The generator writes under the working directory unless told the project: told both ways here.
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

    /** The generator's model context is static; a run leaves its domains there for the tests after it. */
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

    private static String found(File project, String fileName) throws IOException {
        List<File> hits = new ArrayList<File>();
        collect(project, fileName, hits);
        assertEquals(1, hits.size(), fileName + " in " + project + ": " + hits);
        return new String(Files.readAllBytes(hits.get(0).toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static void collect(File dir, String fileName, List<File> hits) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, fileName, hits);
            else if (f.getName().equals(fileName)) hits.add(f);
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
