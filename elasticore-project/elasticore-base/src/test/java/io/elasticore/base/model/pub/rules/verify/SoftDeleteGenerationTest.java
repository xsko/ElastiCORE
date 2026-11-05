package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.pub.rules.ModelCheck;
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
 * @deleted, generated the way a project generates it: the hexagonal bundle in
 * Java and Kotlin with its TypeScript client, and the models the build refuses.
 */
public class SoftDeleteGenerationTest {

    private static String env(String extra) {
        return String.join(
                "\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: sdshop",
                "    framework: springboot",
                "    mode: jpa",
                "    j2ee: jakarta",
                extra,
                "  namespace:",
                "    base: io.acme.sdshop",
                "");
    }

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  SdKept:",
            "    meta: entity @abstract",
            "    fields:",
            "      deletedAt: timestamp @deleted",
            "",
            "  SdProduct:",
            "    meta: entity @root @editable @expose(20) @extend(SdKept)",
            "    fields:",
            "      productId: long @id @sequence",
            "      name:      string(30)! @search(like)",
            "",
            "  SdOrder:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      orderId:   string(36) @id @genid",
            "      memo:      string(30)",
            "      removedAt: datetime @deleted",
            "      lines:     List<SdLine> @OneToMany(mappedBy=\"order\") @dtype(List<SdLineDTO>)",
            "",
            "  SdLine:",
            "    meta: entity @behavior",
            "    fields:",
            "      lineId:    string(36) @id @genid",
            "      order:     SdOrder @ManyToOne",
            "      product:   SdProduct @ManyToOne",
            "      removedAt: datetime @deleted",
            "",
            "  SdMember:",
            "    meta: entity @root @editable @expose(20) @access(read=public, create=public)",
            "    fields:",
            "      memberId:    long @id @sequence",
            "      login:       string(20)! @login @unique",
            "      secret:      string(255)! @password",
            "      withdrawnAt: datetime @deleted",
            "",
            "dto:",
            "  SdLineDTO:",
            "    meta: dto @template(SdLine)",
            "");

    /** The same in the layered MyBatis bundle: a root and its members. */
    private static final String MYBATIS = String.join(
            "\n",
            "entity:",
            "  SdOrder:",
            "    meta: entity @expose(20) @access(*=public)",
            "    fields:",
            "      orderId:   string(36) @id @genid",
            "      removedAt: datetime @deleted",
            "      lines:     List<SdLine> @OneToMany(mappedBy=\"order\") @cascade",
            "",
            "  SdLine:",
            "    meta: entity",
            "    fields:",
            "      lineId:    string(36) @id @genid",
            "      order:     SdOrder @ManyToOne",
            "      removedAt: datetime @deleted",
            "");

    /** A root that would remove its members outright, and a key too many. */
    private static final String REFUSED = String.join(
            "\n",
            "entity:",
            "  SdOrder:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      orderId:   string(36) @id @genid",
            "      removedAt: datetime @deleted",
            "      lines:     List<SdLine> @OneToMany(mappedBy=\"order\") @dtype(List<SdLineDTO>)",
            "",
            "  SdLine:",
            "    meta: entity @behavior",
            "    fields:",
            "      lineId: string(36) @id @genid",
            "      order:  SdOrder @ManyToOne",
            "",
            "  SdNote:",
            "    meta: entity @root @editable @expose(20)",
            "    fields:",
            "      noteId: long @id @sequence",
            "      gone:   boolean @deleted",
            "",
            "dto:",
            "  SdLineDTO:",
            "    meta: dto @template(SdLine)",
            "");

    private static File java;
    private static File kotlin;
    private static File refused;
    private static File xml;
    private static File mybatis;
    private static File xml4;
    private static String springBoot;
    private static String architecture = "hexagonal";
    private static String refusedLog = "";
    private static String xmlLog = "";
    private static int refusedStatus;
    private static int xmlStatus;

    @BeforeAll
    static void generate() throws Exception {
        java = write("elcore-sd", env(""), MODEL);
        kotlin = write("elcore-sd-kt", env(""), MODEL);
        refused = write("elcore-sd-refused", env(""), REFUSED);
        xml = write("elcore-sd-xml", env("    mapping: xml"), MODEL);
        mybatis = write("elcore-sd-mb", env("").replace("mode: jpa", "mode: mybatis"), MYBATIS);
        architecture = "layered";
        try {
            assertEquals(0, run(mybatis, "java", false), "the MyBatis run");
        } finally {
            architecture = "hexagonal";
        }
        // Spring Boot 4 brings Hibernate 7, which applies the delete and the restriction from orm.xml.
        xml4 = write("elcore-sd-xml4", env("    mapping: xml"), MODEL);
        springBoot = "4";
        try {
            assertEquals(0, run(xml4, "java", false), "the Spring Boot 4 mapping: xml run");
        } finally {
            springBoot = null;
        }
        assertEquals(0, run(java, "java", false), "the Java run");
        assertEquals(0, run(java, "java", true), "the TypeScript run");
        assertEquals(0, run(kotlin, "kotlin", false), "the Kotlin run");
        ModelCheck.reset();
        String[] out = captured(refused);
        refusedStatus = Integer.parseInt(out[0]);
        refusedLog = out[1];
        ModelCheck.reset();
        out = captured(xml);
        xmlStatus = Integer.parseInt(out[0]);
        xmlLog = out[1];
        ModelCheck.reset();
    }

    private static String[] captured(File dir) throws Exception {
        java.io.PrintStream out = System.out;
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(bytes, true, "UTF-8"));
        int status;
        try {
            status = run(dir, "java", false);
        } finally {
            System.setOut(out);
        }
        String text = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(text);
        return new String[] {String.valueOf(status), text};
    }

    private static File write(String prefix, String env, String model) throws Exception {
        File dir = Files.createTempDirectory(prefix).toFile();
        File blueprint = new File(dir, "src/main/resources/blueprint/sdshop");
        assertTrue(new File(blueprint, "model").mkdirs());
        Files.write(new File(blueprint, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private static int run(File dir, String language, boolean typescript) {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.ts",
            "elasticore.springBoot"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        try {
            System.setProperty("elasticore.base.path", dir.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            if (typescript) System.setProperty("elasticore.rules.ts", "true");
            if (springBoot != null) System.setProperty("elasticore.springBoot", springBoot);
            return RulesGenRunner.run(new String[0]);
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
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
        ModelCheck.reset();
        for (File dir : new File[] {java, kotlin, refused, xml, mybatis, xml4}) {
            if (dir == null || !dir.exists()) continue;
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, java.io.IOException e) throws java.io.IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static String read(File dir, String path) throws Exception {
        File f = new File(dir, path);
        assertTrue(f.exists(), "not generated: " + path);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)
                .replace("\r", "")
                .replaceAll("(?m)^[ \t]+", "");
    }

    private static String javaFile(String path) throws Exception {
        return read(java, "src/main/java/io/acme/sdshop/" + path);
    }

    private static File find(File dir, String name) throws Exception {
        final File[] found = new File[1];
        Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().equals(name)) found[0] = file.toFile();
                return FileVisitResult.CONTINUE;
            }
        });
        assertTrue(found[0] != null, "not generated: " + name);
        return found[0];
    }

    private static String text(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)
                .replace("\r", "")
                .replaceAll("(?m)^[ \t]+", "");
    }

    /** The class with the table: the delete writes the time - an inherited @deleted too. */
    @Test
    public void theEntityKeepsTheRowOnDelete() throws Exception {
        String product = text(find(java, "SdProduct.java"));
        assertTrue(
                product.contains(
                        "@SQLDelete(sql = \"UPDATE sd_product SET deleted_at = CURRENT_TIMESTAMP WHERE product_id = ? AND deleted_at IS NULL\")"),
                product);
        String kept = text(find(java, "SdKept.java"));
        assertTrue(kept.contains("insertable = false, updatable = false"), kept);
        assertFalse(kept.contains("SQLDelete"), kept);
        // A split root: on the developer's class, which has the table.
        String order = text(find(java, "SdOrder.java"));
        assertTrue(
                order.contains(
                        "@SQLDelete(sql = \"UPDATE sd_order SET removed_at = CURRENT_TIMESTAMP WHERE order_id = ?"),
                order);
        // Its members are kept too, and its collection holds the live ones.
        String base = text(find(java, "SdOrderBase.java"));
        assertTrue(
                base.matches(
                        "(?s).*@SQLRestriction\\(\"removed_at IS NULL\"\\)\\s+(@\\S+[^\\n]*\\s+)*private List<SdLine> lines.*"),
                base);
    }

    /** Every read of the entity leaves the deleted rows out: the repository's and the search's. */
    @Test
    public void theReadsLeaveTheDeletedOut() throws Exception {
        String repository = text(find(java, "SdProductRepository.java"));
        assertTrue(
                repository.contains(
                        "@Query(\"SELECT e FROM SdProduct e WHERE e.productId = :id AND e.deletedAt IS NULL\")"),
                repository);
        String mapper = text(find(java, "SdshopMapper.java"));
        assertTrue(mapper.contains("sp = sp.and((r,q,c) -> c.isNull(r.get(\"deletedAt\")));"), mapper);
        assertTrue(mapper.contains("sp = sp.and((r,q,c) -> c.isNull(r.get(\"removedAt\")));"), mapper);
        String search = text(find(java, "SdProductSrchDTO.java"));
        assertFalse(search.contains("deletedAt"), search);
        String kt = text(find(kotlin, "SdshopMapper.kt"));
        assertTrue(kt.contains("c.isNull(r.get<Any>(\"deletedAt\"))"), kt);
    }

    /** A request never writes it. */
    @Test
    public void noRequestWritesIt() throws Exception {
        String dto = text(find(java, "SdProductDTO.java"));
        assertTrue(
                dto.matches("(?s).*READ_ONLY\\)\\s+(@\\S+[^\\n]*\\s+)*private (java.time.)?Instant deletedAt;.*"), dto);
        String ts = text(find(java, "SdProductDTO.ts"));
        assertTrue(ts.matches("(?s).*\\bdeletedAt: \\{[^}]*readOnly: true.*"), ts);
    }

    @Test
    public void whatWouldBeRemovedOutrightStopsTheBuild() {
        assertEquals(1, refusedStatus, refusedLog);
        assertTrue(
                refusedLog.contains(
                        "SdOrder @deleted keeps its rows, but a delete would remove lines (SdLine) outright with it"
                                + " - mark that entity @deleted too"),
                refusedLog);
        assertTrue(refusedLog.contains("SdNote.gone @deleted is"), refusedLog);
    }

    /** A deleted member signs in no more. */
    @Test
    public void aDeletedRowSignsInNoMore() throws Exception {
        String service = text(find(java, "SdMemberCoreService.java"));
        assertTrue(
                service.contains("cb.and(cb.equal(r.get(\"login\"), login), cb.isNull(r.get(\"withdrawnAt\")))"),
                service);
        String kt = text(find(kotlin, "SdMemberCoreService.kt"));
        assertTrue(
                kt.contains("cb.and(cb.equal(r.get<Any>(\"login\"), login), cb.isNull(r.get<Any>(\"withdrawnAt\")))"),
                kt);
    }

    /** Layered MyBatis: a delete keeps the root and its members; an update that replaces them removes the live rows outright. */
    @Test
    public void myBatisKeepsTheMembersOnDeleteAndReplacesThemOnUpdate() throws Exception {
        String xml = text(find(mybatis, "SdLineRepository.xml"));
        assertTrue(xml.contains("UPDATE sd_line SET removed_at = CURRENT_TIMESTAMP WHERE order_order_id = #{key"), xml);
        assertTrue(xml.contains("<delete id=\"purgeAllByOrder\">"), xml);
        assertTrue(
                xml.matches(
                        "(?s).*DELETE FROM sd_line WHERE order_order_id = #\\{key[^}]*\\} AND removed_at IS NULL.*"),
                xml);
        String service = text(find(mybatis, "SdOrderCoreService.java"));
        assertTrue(service.contains(".purgeAllByOrder("), service);
        assertTrue(service.contains(".deleteAllByOrder("), service);
    }

    /** Spring Boot 4: orm.xml in Hibernate's schema says the delete and the collection's restriction; the classes say nothing. */
    @Test
    public void mappingXmlOnSpringBoot4SaysItInOrmXml() throws Exception {
        String orm = text(find(xml4, "orm.xml"));
        assertTrue(
                orm.contains("<entity-mappings xmlns=\"http://www.hibernate.org/xsd/orm/mapping\"\nversion=\"7.0\">"),
                orm);
        assertTrue(
                orm.matches(
                        "(?s).*<table name=\"sd_product\"/>\n<sql-delete>UPDATE sd_product SET deleted_at = CURRENT_TIMESTAMP"
                                + " WHERE product_id = \\? AND deleted_at IS NULL</sql-delete>.*"),
                orm);
        assertTrue(
                orm.matches(
                        "(?s).*<one-to-many name=\"lines\"[^>]*>\n<sql-restriction>removed_at IS NULL</sql-restriction>.*"),
                orm);
        for (String name : new String[] {"SdProduct.java", "SdOrder.java", "SdOrderBase.java"}) {
            String source = text(find(xml4, name));
            assertFalse(source.contains("org.hibernate") || source.contains("@SQL"), name + "\n" + source);
        }
    }

    @Test
    public void mappingXmlStopsTheBuild() {
        assertEquals(1, xmlStatus, xmlLog);
        assertTrue(xmlLog.contains("@deleted with mapping: xml needs Spring Boot 4 (Hibernate 7)"), xmlLog);
    }
}
