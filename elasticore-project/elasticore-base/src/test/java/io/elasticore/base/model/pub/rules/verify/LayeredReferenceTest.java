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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A layered build of a model written for ddd/hexagonal: the order references its customer as an object
 * (customer: Customer @ManyToOne), the owner is the customer's identity (@owner(customerId)) and the env
 * sets referenceColumn: short. Only ddd and hexagonal hold that identity as a field and name its column, so
 * layered keeps JPA's join column (customer_customer_id - the entity, its DDL and the Ossie export agree,
 * run on H2) and resolves no owner. The build now says what to write instead of only that nothing resolves.
 */
public class LayeredReferenceTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Customer:",
            "    meta: entity @root @editable @expose(20) @signin @owner(customerId) @access(read=owner, create=public, update=owner)",
            "    fields:",
            "      customerId: long @id @sequence",
            "      login: string(20)! @login",
            "      password: string(255)! @password",
            "",
            "  PurchaseOrder:",
            "    meta: entity @root @expose(20) @owner(customerId) @access(read=owner, create=user)",
            "    fields:",
            "      orderId: long @id @sequence",
            "      customer: Customer @ManyToOne",
            "      total: money(12.2)!",
            "");

    private static final String OWNER_HINT = "[Shop] PurchaseOrder @access has owner-level operations but no @owner"
            + " field resolves (@owner(customerId)) - they are the administrator's alone until @owner names the"
            + " identity that holds the owner. customerId is the identity of customer: Customer, which only"
            + " architecture = 'ddd' and 'hexagonal' hold as a field. Here, declare it on the column the relation"
            + " joins on - customerId: long @db(customer_customer_id) - and join the relation by it: customer:"
            + " Customer @ManyToOne @joinBy(customerId); then @owner(customerId).";

    private static final String COLUMN_UNUSED = "[shop] referenceColumn: short is read by architecture = 'ddd' and"
            + " 'hexagonal' only - the selected rules keep each @ManyToOne on JPA's join column (customer_customer_id"
            + " for customer: Customer), in the entity, the DDL and the Ossie export alike.";

    private final List<File> projects = new ArrayList<File>();

    @Test
    public void layeredSaysWhatToWrite() throws Exception {
        String log = run(project(MODEL), "layered");
        assertTrue(log.contains(OWNER_HINT), log);
        assertTrue(log.contains(COLUMN_UNUSED), log);
    }

    /** What the hint says to write resolves the owner in layered. */
    @Test
    public void theHintResolvesTheOwner() throws Exception {
        String model = MODEL.replace(
                "      customer: Customer @ManyToOne",
                "      customerId: long @db(customer_customer_id)\n      customer: Customer @ManyToOne @joinBy(customerId)");
        File project = project(model);
        String log = run(project, "layered");
        assertFalse(log.contains("no @owner field resolves"), log);
        String service = found(project, "PurchaseOrderCoreService.java");
        assertTrue(service.contains("customerId"), service);
    }

    @Test
    public void hexagonalNeedsNeither() throws Exception {
        String log = run(project(MODEL), "hexagonal");
        assertFalse(log.contains("no @owner field resolves"), log);
        assertFalse(log.contains("referenceColumn:"), log);
    }

    // ------------------------------------------------------------------

    private File project(String model) throws IOException {
        File project = Files.createTempDirectory("elcore-layref").toFile();
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
                                "    referenceColumn: short",
                                "  namespace:",
                                "    base: io.acme.shop",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    private static String run(File project, String architecture) {
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
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            for (String k : keys) System.clearProperty(k);
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.force", "true");
            if ("layered".equals(architecture)) System.setProperty("elasticore.rules.v3", "true");
            assertEquals(0, RulesGenRunner.run(new String[0]));
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
        // Long lines are wrapped by nothing; colour codes are around each line only.
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String found(File project, String fileName) throws IOException {
        List<File> hits = new ArrayList<File>();
        collect(project, fileName, hits);
        assertEquals(1, hits.size(), fileName + " in " + project + ": " + hits);
        return new String(Files.readAllBytes(hits.get(0).toPath()), StandardCharsets.UTF_8);
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
