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
 * {@code status: List<OrderStatus> @search(in)} in a search DTO - the DSL
 * guide's own example - searches a single column by a list of values:
 * {@code status in (...)}. The mapper read the list as a collection of the
 * entity and joined it - {@code root.join("status")} on an enum column, which
 * no query can do - and the 3.0 Java code wrapped the lambda's parameters
 * twice, {@code ((root, query, criteriaBuilder)) ->}, which does not compile.
 * A collection of the entity still joins (MapperSpecTest), with one pair of parentheses.
 */
public class SearchInValueListTest {

    private static final String MODEL = String.join(
            "\n",
            "enumeration:",
            "  OrderStatus:",
            "    meta: enum @db(code)",
            "    fields:",
            "      code: string(1)",
            "      label: string(20)",
            "    enum:",
            "      PLACED: P,Placed",
            "      SHIPPED: S,Shipped",
            "      CANCELLED: C,Cancelled",
            "",
            "entity:",
            "  PurchaseOrder:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      orderId: long @id @sequence",
            "      orderNo: string(20)",
            "      status: OrderStatus",
            "",
            "dto:",
            "  PurchaseOrderDTO:",
            "    meta: dto @template(PurchaseOrder)",
            "",
            "  PurchaseOrderSrchDTO:",
            "    meta: dto @searchable(entity=PurchaseOrder)",
            "    fields:",
            "      orderNo: string @search(like)",
            "      status: List<OrderStatus> @search(in)",
            "");

    private static final String STATUS_IN = "r.get(\"status\").in(status)";

    private final List<File> projects = new ArrayList<File>();

    @Test
    public void theV3LayeredMapperSearchesTheColumn() throws Exception {
        File project = project();
        assertEquals(0, run(project, "layered", "java", "elasticore.rules.v3"));
        assertSearchesTheColumn(found(project, "ShopMapper.java"));
    }

    @Test
    public void theHexagonalMapperSearchesTheColumn() throws Exception {
        File project = project();
        assertEquals(0, run(project, "hexagonal", "java", null));
        assertSearchesTheColumn(found(project, "ShopMapper.java"));
    }

    @Test
    public void theKotlinMapperSearchesTheColumn() throws Exception {
        File project = project();
        assertEquals(0, run(project, "layered", "kotlin", null));
        String mapper = found(project, "ShopMapper.kt");
        assertTrue(mapper.contains("r.get<Any>(\"status\").`in`(status)"), mapper);
        assertFalse(mapper.contains("root.join<OrderStatus>(\"status\")"), mapper);
        assertFalse(mapper.contains("join(\"status\")"), mapper);
    }

    /** The 2.x generator joined the enum column too: the query failed when it ran. */
    @Test
    public void the2xMapperSearchesTheColumn() throws Exception {
        File project = project();
        extract(project);
        String mapper = found(project, "ShopMapper.java");
        assertTrue(mapper.contains("sp = sp.and((r, q, c) -> " + STATUS_IN + ");"), mapper);
        assertFalse(mapper.contains("root.join(\"status\")"), mapper);
    }

    /** The rule engine's 2.x-compatible output writes the 2.x generator's toSpec. */
    @Test
    public void theCompatibleOutputMatchesThe2xGenerator() throws Exception {
        File legacy = project();
        extract(legacy);
        File compatible = project();
        assertEquals(0, run(compatible, "layered", "java", "elasticore.rules.v2"));
        assertEquals(toSpec(found(legacy, "ShopMapper.java")), toSpec(found(compatible, "ShopMapper.java")));
    }

    private static void assertSearchesTheColumn(String mapper) {
        assertTrue(mapper.contains(STATUS_IN), mapper);
        assertFalse(mapper.contains("root.join(\"status\")"), mapper);
        assertFalse(mapper.contains("Join<PurchaseOrder, OrderStatus>"), mapper);
    }

    /** The toSpec(searchDTO, sp) method of a mapper. */
    private static String toSpec(String mapper) {
        String head =
                "public static Specification<PurchaseOrder> toSpec(PurchaseOrderSrchDTO searchDTO, Specification<PurchaseOrder> sp)";
        int from = mapper.indexOf(head);
        assertTrue(from >= 0, mapper);
        int to = mapper.indexOf("\n    }\n", from);
        return mapper.substring(from, to < 0 ? mapper.length() : to);
    }
    // ------------------------------------------------------------------

    private File project() throws IOException {
        File project = Files.createTempDirectory("elcore-searchin").toFile();
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
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    entity: io.acme.shop.entity",
                                "    dto: io.acme.shop.dto",
                                "    repository: io.acme.shop.repository",
                                "    service: io.acme.shop.service",
                                "    control: io.acme.shop.controller",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
        return project;
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
