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

    // ------------------------------------------------------------------
    // List<X> @search(eq) on a single column: the DTO carries one value,
    // statusItem, and the mapper compares the column with it - it joined the
    // column, as the in case did.

    private static final String EQ_MODEL =
            MODEL.replace("status: List<OrderStatus> @search(in)", "status: List<OrderStatus> @search(eq)");

    private static final String STATUS_EQ = "c.equal(r.get(\"status\"),statusItem)";

    @Test
    public void anEqualityOnAListComparesTheColumnInTheV3Mappers() throws Exception {
        for (String[] output : new String[][] {{"layered", "elasticore.rules.v3"}, {"hexagonal", null}}) {
            File project = project(EQ_MODEL);
            assertEquals(0, run(project, output[0], "java", output[1]), output[0]);
            String mapper = found(project, "ShopMapper.java");
            assertTrue(mapper.contains("sp = sp.and((r,q,c) -> " + STATUS_EQ + ");"), mapper);
            assertTrue(mapper.contains("OrderStatus statusItem = searchDTO.getStatusItem();"), mapper);
            assertFalse(mapper.contains("Join<PurchaseOrder, OrderStatus>"), mapper);
        }
    }

    @Test
    public void anEqualityOnAListComparesTheColumnInKotlin() throws Exception {
        File project = project(EQ_MODEL);
        assertEquals(0, run(project, "layered", "kotlin", null));
        String mapper = found(project, "ShopMapper.kt");
        assertTrue(mapper.contains("c.equal(r.get<Any>(\"status\"),statusItem!!)"), mapper);
        assertFalse(mapper.contains("root.join"), mapper);
    }

    @Test
    public void anEqualityOnAListComparesTheColumnIn2x() throws Exception {
        File legacy = project(EQ_MODEL);
        extract(legacy);
        String mapper = found(legacy, "ShopMapper.java");
        assertTrue(mapper.contains("sp = sp.and((r,q,c) -> " + STATUS_EQ + ");"), mapper);
        assertFalse(mapper.contains("root.join(\"status\")"), mapper);
        File compatible = project(EQ_MODEL);
        assertEquals(0, run(compatible, "layered", "java", "elasticore.rules.v2"));
        assertEquals(toSpec(mapper), toSpec(found(compatible, "ShopMapper.java")));
    }

    // ------------------------------------------------------------------
    // A @searchable DTO of an entity declared in another domain: no generator
    // writes a toSpec for it (2.x neither), so it searches by nothing - the
    // build now says so and where to declare it.

    @Test
    public void aSearchDtoOfAnotherDomainsEntityIsReported() throws Exception {
        for (String[] output : new String[][] {{"layered", "elasticore.rules.v3"}, {"hexagonal", null}}) {
            File project = crossDomainProject(MODEL);
            assertEquals(0, run(project, output[0], "java", output[1]), output[0]);
            assertTrue(
                    lastLog.contains("[search] OrderSearchDTO @searchable(entity=PurchaseOrder): PurchaseOrder is"
                            + " declared in domain 'shop', and a search DTO is searched only in its entity's domain"
                            + " - no toSpec is written for it here. Declare OrderSearchDTO in domain 'shop'."),
                    lastLog);
            assertFalse(writesToSpec(project, "OrderSearchDTO"), output[0]);
        }
    }

    @Test
    public void aSearchDtoOfAnEntityNoDomainDeclaresIsReported() throws Exception {
        File project = project(MODEL.replace("@searchable(entity=PurchaseOrder)", "@searchable(entity=Purchase)"));
        assertEquals(0, run(project, "layered", "java", "elasticore.rules.v3"));
        assertTrue(
                lastLog.contains("[shop] PurchaseOrderSrchDTO @searchable(entity=Purchase): no domain declares an"
                        + " entity Purchase, so nothing is searched by PurchaseOrderSrchDTO (no toSpec is written)."),
                lastLog);
    }
    /** The entity and its enum in domain shop, the search DTO in domain search. */
    private File crossDomainProject(String model) throws IOException {
        File project = project(model.substring(0, model.indexOf("dto:")));
        File dir = new File(project, "src/main/resources/blueprint/search");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                env("search", "io.acme.search").getBytes(StandardCharsets.UTF_8));
        String dto = model.substring(model.indexOf("dto:"))
                .replace("  PurchaseOrderDTO:\n    meta: dto @template(PurchaseOrder)\n\n", "")
                .replace("PurchaseOrderSrchDTO:", "OrderSearchDTO:");
        Files.write(new File(dir, "model/model.yml").toPath(), dto.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    // ------------------------------------------------------------------
    // A list against a to-one relation (customer: Customer @ManyToOne) is not a column of
    // values: no column equality for it (both generators leave a list of entities out of a search DTO).

    private static final String RELATION_MODEL = MODEL.replace(
                    "      status: OrderStatus\n", "      status: OrderStatus\n      customer: Customer @ManyToOne\n")
            .replace(
                    "dto:\n",
                    "  Customer:\n    meta: entity\n    fields:\n      customerId: long @id @sequence\n      name: string(20)\n\ndto:\n")
            .replace(
                    "      status: List<OrderStatus> @search(in)\n",
                    "      status: List<OrderStatus> @search(in)\n      customer: List<Customer> @search(eq)\n");

    @Test
    public void aListAgainstARelationIsNotAColumnOfValues() throws Exception {
        File legacy = project(RELATION_MODEL);
        extract(legacy);
        String mapper = toSpec(found(legacy, "ShopMapper.java"));
        assertFalse(mapper.contains("c.equal(r.get(\"customer\")"), mapper);
        File compatible = project(RELATION_MODEL);
        assertEquals(0, run(compatible, "layered", "java", "elasticore.rules.v2"));
        assertEquals(mapper, toSpec(found(compatible, "ShopMapper.java")));
        File v3 = project(RELATION_MODEL);
        assertEquals(0, run(v3, "layered", "java", "elasticore.rules.v3"));
        String v3Mapper = toSpec(found(v3, "ShopMapper.java"));
        assertFalse(v3Mapper.contains("c.equal(r.get(\"customer\")"), v3Mapper);
    }

    private static void assertSearchesTheColumn(String mapper) {
        assertTrue(mapper.contains(STATUS_IN), mapper);
        assertFalse(mapper.contains("root.join(\"status\")"), mapper);
        assertFalse(mapper.contains("Join<PurchaseOrder, OrderStatus>"), mapper);
    }

    /** The toSpec(searchDTO, sp) method of a mapper. */
    private static String toSpec(String mapper) {
        return toSpec(mapper, "PurchaseOrderSrchDTO");
    }

    private static String toSpec(String mapper, String dto) {
        String head = "public static Specification<PurchaseOrder> toSpec(" + dto
                + " searchDTO, Specification<PurchaseOrder> sp)";
        int from = mapper.indexOf(head);
        assertTrue(from >= 0, mapper);
        int to = mapper.indexOf("\n    }\n", from);
        return mapper.substring(from, to < 0 ? mapper.length() : to);
    }
    // ------------------------------------------------------------------

    private File project() throws IOException {
        return project(MODEL);
    }

    private File project(String model) throws IOException {
        File project = Files.createTempDirectory("elcore-searchin").toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/shop");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(), env("shop", "io.acme.shop").getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    private static String env(String domain, String base) {
        return String.join(
                "\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: " + domain,
                "    framework: springboot",
                "    mode: jpa",
                "    j2ee: jakarta",
                "  namespace:",
                "    entity: " + base + ".entity",
                "    dto: " + base + ".dto",
                "    repository: " + base + ".repository",
                "    service: " + base + ".service",
                "    control: " + base + ".controller",
                "");
    }

    /** What the last run printed. */
    private static String lastLog = "";

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
            lastLog = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            out.print(lastLog);
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

    /** Whether any generated source writes toSpec(dto searchDTO, ...). */
    private static boolean writesToSpec(File project, String dto) throws IOException {
        List<File> all = new ArrayList<File>();
        listAll(new File(project, "src/main"), all);
        for (File f : all) {
            if (!f.getName().endsWith(".java") && !f.getName().endsWith(".kt")) continue;
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (text.contains("toSpec(" + dto + " searchDTO") || text.contains("toSpec(searchDTO: " + dto)) return true;
        }
        return false;
    }

    private static void listAll(File dir, List<File> all) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) listAll(f, all);
            else all.add(f);
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
