package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * -Pmode=valid on the rule engine: everything generated in memory, compared
 * with the tree, and nothing written.
 *
 * The mode used to reach this engine and be read by nothing in it, so a
 * check asked to write nothing regenerated the project. That is why every
 * run here is first held to the tree byte for byte, directories included: a
 * check that writes is worse than none, because it is trusted - a CI gate
 * built on it passes by repairing what it was meant to find.
 */
public class ValidationModeTest {

    private File project;
    /** What the last run printed. */
    private String log = "";

    /** The declaration the runs are made with; null for none (the compatible mode). */
    private String architecture = "hexagonal";
    private String language = "java";

    /** Where a file taken away waits, inside the project and outside every snapshot. */
    private static final String ASIDE = ".aside";

    private static final String PRODUCT = String.join("\n",
            "  Product:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      productId: long @id @sequence",
            "      name:      string(100)!",
            "      price:     long",
            "");

    private static final String MODEL = "entity:\n" + PRODUCT;

    @BeforeEach
    void newProject() throws IOException {
        project = Files.createTempDirectory("elcore-valid").toFile();
    }

    @Test
    @DisplayName("a tree in step with the model passes, and is left byte for byte")
    public void aTreeInStepPasses() throws Exception {
        model(MODEL);
        assertEquals(0, run(null), log);

        // A guide is copied by generation and must not be by a check.
        File guide = new File(project, "src/main/resources/blueprint/elasticore-dsl-guide.md");
        assertTrue(guide.isFile(), "generation deployed no guide to remove: " + guide);
        assertTrue(guide.delete());

        Map<String, String> before = snapshot();
        assertEquals(0, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertFalse(guide.exists(), "validation copied the guides");
        assertTrue(log.contains("[VALID] Validation PASSED"), log);
    }

    @Test
    @DisplayName("a model changed since generation fails, and nothing is written")
    public void aChangedModelFails() throws Exception {
        model(MODEL);
        assertEquals(0, run(null), log);

        model(MODEL.replace("      price:     long\n", "      price:     long\n      stock:     int\n"));
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        // schema.yml too: it would change with the model, and is a side-car.
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertTrue(log.contains("  WOULD WRITE"), log);
        assertTrue(log.contains("[VALID] Validation FAILED"), log);

        // Generation is what brings the tree back in step.
        assertEquals(0, run(null), log);
        assertEquals(0, run("valid"), log);
    }

    @Test
    @DisplayName("generated files that are missing fail, and neither they nor their directory are written")
    public void missingFilesFail() throws Exception {
        model(MODEL);
        assertEquals(0, run(null), log);

        File dtoDir = find("ProductDTO.java").getParentFile();
        deleteTree(dtoDir.toPath());
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertFalse(dtoDir.exists(), "validation made a directory");
        assertTrue(log.contains("WOULD WRITE(new)"), log);
    }

    @Test
    @DisplayName("the messages file the API errors read is checked, not written")
    public void aMissingMessagesFileFails() throws Exception {
        model(MODEL);
        assertEquals(0, run(null), log);

        File messages = new File(project, "src/main/resources/i18n/shop-messages.properties");
        assertTrue(messages.isFile(), "generation wrote no messages file to check:\n" + log);
        moveAside(messages);
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertFalse(messages.exists(), "validation wrote the messages file");
        assertTrue(log.contains("shop-messages.properties  WOULD WRITE(new)"), log);
    }

    @Test
    @DisplayName("with mapping: xml, orm.xml is checked, not written")
    public void aMissingOrmXmlFails() throws Exception {
        model(MODEL, "    mapping: xml");
        assertEquals(0, run(null), log);

        File orm = new File(project, "src/main/resources/META-INF/orm.xml");
        assertTrue(orm.isFile(), "generation wrote no orm.xml to check:\n" + log);
        moveAside(orm);
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertFalse(orm.exists(), "validation wrote orm.xml");
        assertTrue(log.contains("orm.xml  WOULD WRITE(new)"), log);
    }

    @Test
    @DisplayName("a managed file edited by hand fails - generation would leave it as it is")
    public void aHandEditedManagedFileFails() throws Exception {
        model(MODEL);
        assertEquals(0, run(null), log);

        File dto = find("ProductDTO.java");
        Files.write(dto.toPath(), (read(dto) + "// mine\n").getBytes(StandardCharsets.UTF_8));
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertTrue(log.contains("USER-MODIFIED(differs from the DSL"), log);
    }

    @Test
    @DisplayName("a managed file only re-indented fails: line endings are not compared, indentation is")
    public void aReindentedManagedFileFails() throws Exception {
        model(MODEL);
        assertEquals(0, run(null), log);

        File dto = find("ProductDTO.java");
        String text = read(dto);
        assertTrue(text.contains("private "), text);
        Files.write(dto.toPath(), text.replace("private ", "  private ").getBytes(StandardCharsets.UTF_8));
        // The file's hash ignores whitespace, so it does not read as edited:
        // generation would write it again, which is what the check says.
        assertEquals(1, run("valid"), log);
        assertTrue(log.contains("ProductDTO.java  WOULD WRITE"), log);
    }

    @Test
    @DisplayName("a preservation block in a managed file passes - generation keeps it")
    public void aPreservationBlockPasses() throws Exception {
        model(MODEL);
        assertEquals(0, run(null), log);

        File dto = find("ProductDTO.java");
        String text = read(dto);
        int last = text.lastIndexOf('}');
        Files.write(dto.toPath(), (text.substring(0, last) + PRESERVED + text.substring(last))
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(0, run("valid"), log);
    }

    /** A block the generator keeps through every rewrite. */
    static final String PRESERVED = "// !---------- mine\n    public String label() { return \"x\"; }\n// ------------!\n";

    @Test
    @DisplayName("a file that is the developer's, edited, passes")
    public void theDevelopersOwnFilePasses() throws Exception {
        model(MODEL);
        assertEquals(0, run(null), log);

        File service = find("ProductService.java");
        Files.write(service.toPath(), (read(service) + "// mine\n").getBytes(StandardCharsets.UTF_8));
        assertEquals(0, run("valid"), log);
        assertTrue(log.contains("SKIP(user-owned)"), log);
    }

    @Test
    @DisplayName("an owned entity whose table the model renamed fails, and keeps its annotations")
    public void anOwnedEntitysTableIsCheckedNotRewritten() throws Exception {
        model(MODEL);
        assertEquals(0, run(null), log);

        // The developer's half of the entity, edited: generation keeps it and
        // brings only its table annotations in line with the DSL.
        File entity = find("Product.java");
        Files.write(entity.toPath(), (read(entity) + "// mine\n").getBytes(StandardCharsets.UTF_8));
        model(MODEL.replace("@root @expose(20)", "@root @expose(20) @table(t_product)"));
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertTrue(log.contains("the table annotations differ from the DSL - generation would bring them in line"), log);
    }

    @Test
    @DisplayName("a file of the developer's that generation says needs a hand fix fails")
    public void aFileThatNeedsAHandFixFails() throws Exception {
        model(String.join("\n",
                "entity:",
                "  Customer:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      customerId: long @id @sequence",
                "      orders:     int",
                "  Order:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      orderId: long @id @sequence",
                "domainService:",
                "  Shipping:",
                "    uses: [Customer, Order]",
                ""));
        // A domain service written before services were split: its own
        // constructor, extending no base - generation says so and leaves it.
        File old = new File(project, "src/main/java/io/acme/shop/domain/service/ShippingDomainService.java");
        assertTrue(old.getParentFile().mkdirs());
        Files.write(old.toPath(), String.join("\n",
                "//ecd:-1H20260101000000_V3.0",
                "// Initially generated by ElastiCORE; no longer managed - do not remove this line.",
                "package io.acme.shop.domain.service;",
                "",
                "public class ShippingDomainService {",
                "    public ShippingDomainService() {",
                "    }",
                "}",
                "").getBytes(StandardCharsets.UTF_8));
        assertEquals(0, run(null), log);
        assertTrue(log.contains("does not extend ShippingDomainServiceBase"), log);

        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertTrue(log.contains("need a hand fix"), log);
    }

    @Test
    @DisplayName("a file written once and never edited, which the model has changed, fails - and is not rewritten")
    public void anUneditedOwnedFileFails() throws Exception {
        model(MODEL.replace("@root @expose(20)", "@root @expose(20) @editable"));
        assertEquals(0, run(null), log);
        File controller = find("ProductController.java");

        // Without @editable the controller loses its write endpoints; nobody
        // edited it, so generation would write it again.
        model(MODEL);
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertTrue(log.contains("WOULD REGENERATE(owned, never edited)"), log);
        assertTrue(controller.isFile());
    }

    @Test
    @DisplayName("a generated file the model no longer produces fails, and is not removed")
    public void aFileTheModelDroppedFails() throws Exception {
        model(MODEL + String.join("\n",
                "  Tag:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      tagId: long @id @sequence",
                "      label: string(50)",
                ""));
        assertEquals(0, run(null), log);
        File tagDto = find("TagDTO.java");

        model(MODEL);
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertTrue(tagDto.isFile(), "validation removed a file");
        assertTrue(log.contains("WOULD REMOVE(the DSL no longer produces it)"), log);
    }

    @Test
    @DisplayName("an edited file the model no longer produces fails - generation can only say so")
    public void anEditedFileTheModelDroppedFails() throws Exception {
        model(MODEL + String.join("\n",
                "  Tag:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      tagId: long @id @sequence",
                "      label: string(50)",
                ""));
        assertEquals(0, run(null), log);
        File tagDto = find("TagDTO.java");
        Files.write(tagDto.toPath(), (read(tagDto) + "// mine\n").getBytes(StandardCharsets.UTF_8));

        model(MODEL);
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertTrue(log.contains("the DSL no longer produces it, and it has been edited"), log);
    }

    @Test
    @DisplayName("the TypeScript client is checked the same way")
    public void typeScriptIsChecked() throws Exception {
        model(MODEL);
        assertEquals(0, run(null, "elasticore.rules.ts=true"), log);
        assertEquals(0, run("valid", "elasticore.rules.ts=true"), log);

        File ts = find("ProductDTO.ts");
        moveAside(ts);
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid", "elasticore.rules.ts=true"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertFalse(ts.exists(), "validation wrote the missing file");
        assertTrue(log.contains("WOULD WRITE(new)"), log);
    }

    @Test
    @DisplayName("a project of modules is checked across them, each in its own tree")
    public void modulesAreChecked() throws Exception {
        File alpha = new File(project, "mod-alpha");
        File beta = new File(project, "mod-beta");
        moduleDomain(alpha, "alpha", "entity:\n  Ant:\n    meta: entity @root @expose(20)\n    fields:\n"
                + "      antId: long @id @sequence\n      legs: int\n");
        moduleDomain(beta, "beta", "entity:\n  Bee:\n    meta: entity @root @expose(20)\n    fields:\n"
                + "      beeId: long @id @sequence\n      wings: int\n");
        String modules = "elasticore.modules=" + alpha.getAbsolutePath() + "," + beta.getAbsolutePath();
        assertEquals(0, run(null, modules), log);
        assertEquals(0, run("valid", modules), log);

        File beeDto = find(beta, "BeeDTO.java");
        moveAside(beeDto);
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid", modules), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertFalse(beeDto.exists(), "validation wrote the missing file");
    }

    @Test
    @DisplayName("a DSL warning fails the check: a part of the DSL is not applied, although generation goes on")
    public void aDslWarningFails() throws Exception {
        model(MODEL.replace("@root @expose(20)", "@root @expose(20) @sort(nope+)"));
        assertEquals(0, run(null), log);
        assertTrue(log.contains("@sort(nope+): nope is not a field of Product"), "the model was meant to draw a warning: " + log);

        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertTrue(log.contains(" DSL warning(s) - see the list above."), log);
    }

    @Test
    @DisplayName("a note of what the generator decided is said and not counted - a model keeps it on purpose")
    public void aDecisionNoteIsNotCounted() throws Exception {
        // The ticket of the Kotlin sample: the agent behind a ticket is read by
        // that agent alone, so the ticket's DTO leaves the agent's row out.
        model(String.join("\n",
                "entity:",
                "  Agent:",
                "    meta: entity @root @editable @expose(20) @owner(agentId) @access(read=owner, create=public, update=owner)",
                "    fields:",
                "      agentId: long @id @sequence",
                "      login:   string(20)! @login",
                "  Ticket:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      ticketId: long @id @sequence",
                "      agent:    Agent @ManyToOne",
                "      subject:  string(100)!",
                ""));
        assertEquals(0, run(null), log);
        assertTrue(log.contains("DSL notes - what the generator decided"), log);
        assertTrue(log.contains("Ticket.agent is left out"), log);

        assertEquals(0, run("valid"), log);
        assertTrue(log.contains("DSL note(s), listed above - what the generator decided; not counted."), log);
        assertTrue(log.contains("[VALID] Validation PASSED"), log);
    }

    @Test
    @DisplayName("a model file that does not parse fails the check - it was compared with nothing")
    public void aModelFileThatDoesNotParseFails() throws Exception {
        model(MODEL);
        assertEquals(0, run(null), log);

        // Generation goes on without it, with a LOAD ERROR; a check must not pass on that.
        Files.write(new File(project, "src/main/resources/blueprint/shop/model/broken.yml").toPath(),
                "entity: [Product\n".getBytes(StandardCharsets.UTF_8));
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertTrue(log.contains("LOAD ERROR"), log);
        assertTrue(log.contains("model file(s) could not be read"), log);
    }

    @Test
    @DisplayName("-Pdomain names whole domains: orders is not order")
    public void aDomainFilterMatchesWholeNames() throws Exception {
        domain("order", "entity:\n  Ticket:\n    meta: entity @root @expose(20)\n    fields:\n"
                + "      ticketId: long @id @sequence\n      seat: string(10)\n");
        domain("orders", "entity:\n  Voucher:\n    meta: entity @root @expose(20)\n    fields:\n"
                + "      voucherId: long @id @sequence\n      amount: long\n");
        assertEquals(0, run(null), log);

        domain("order", "entity:\n  Ticket:\n    meta: entity @root @expose(20)\n    fields:\n"
                + "      ticketId: long @id @sequence\n      seat: string(10)\n      row: int\n");
        // A substring match read orders as order too, and checked the stale one.
        assertEquals(0, run("valid", "elasticore.domain=orders"), log);
        assertEquals(1, run("valid", "elasticore.domain=order"), log);

        // What the DSL says on reading is said whichever domains are compared:
        // every domain is read, so a warning of order fails a check of orders.
        domain("order", "entity:\n  Ticket:\n    meta: entity @root @expose(20)\n    fields:\n"
                + "      ticketId: long @id @sequence\n      seat: string(10) @unique!\n");
        assertEquals(1, run("valid", "elasticore.domain=orders"), log);
        assertTrue(log.contains("invalid '!' after annotation"), log);
    }

    @Test
    @DisplayName("asked for with a check, a listing of the rules does not answer for it")
    public void aListingDoesNotAnswerForACheck() throws Exception {
        model(MODEL);
        assertEquals(0, run(null), log);

        model(MODEL.replace("      price:     long\n", "      price:     long\n      stock:     int\n"));
        assertEquals(1, run("valid", "elasticore.rules.list=true"), log);
        assertTrue(log.contains("  WOULD WRITE"), log);
    }

    @Test
    @DisplayName("Kotlin sources are checked the same way")
    public void kotlinIsChecked() throws Exception {
        language = "kotlin";
        model(MODEL);
        assertEquals(0, run(null), log);
        assertEquals(0, run("valid"), log);

        File dto = find("ProductDTO.kt");
        moveAside(dto);
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertFalse(dto.exists(), "validation wrote the missing file");
        assertTrue(log.contains("WOULD WRITE(new)"), log);
    }

    @Test
    @DisplayName("the compatible mode of a build that declares nothing is checked the same way")
    public void theCompatibleModeIsChecked() throws Exception {
        architecture = null;
        language = null;
        model(MODEL);
        assertEquals(0, run(null), log);
        assertTrue(log.contains("[elcore3] mode      : compatible"), log);
        assertEquals(0, run("valid"), log);

        model(MODEL.replace("      price:     long\n", "      price:     long\n      stock:     int\n"));
        Map<String, String> before = snapshot();
        assertEquals(1, run("valid"), log);
        assertEquals(before, snapshot(), "validation wrote to the tree");
        assertTrue(log.contains("  WOULD WRITE"), log);
    }

    @Test
    @DisplayName("-Pmode=clean is refused, and nothing is written or removed")
    public void cleanIsRefused() throws Exception {
        model(MODEL);
        assertEquals(0, run(null), log);

        Map<String, String> before = snapshot();
        assertEquals(1, run("clean"), log);
        assertEquals(before, snapshot(), "-Pmode=clean touched the tree");
        assertTrue(log.contains("-Pmode=clean is a mode of the 2.x generator"), log);
    }

    // ---- the harness --------------------------------------------------

    private void model(String model) throws IOException {
        model(model, null);
    }

    /** The shop domain, with extra lines under config: when given. */
    private void model(String model, String config) throws IOException {
        write("shop", model, config);
    }

    /** Another domain of the project, named for its directory. */
    private void domain(String name, String model) throws IOException {
        write(name, model, null);
    }

    /** A domain in a module of its own, under the project. */
    private void moduleDomain(File module, String name, String model) throws IOException {
        write(module, name, model, null);
    }

    private void write(String name, String model, String config) throws IOException {
        write(project, name, model, config);
    }

    private void write(File root, String name, String model, String config) throws IOException {
        File dir = new File(root, "src/main/resources/blueprint/" + name);
        assertTrue(new File(dir, "model").isDirectory() || new File(dir, "model").mkdirs());
        StringBuilder env = new StringBuilder()
                .append("elasticore:\n")
                .append("  enable: true\n")
                .append("  config:\n")
                .append("    domainName: ").append(name).append('\n')
                .append("    framework: springboot\n")
                .append("    mode: jpa\n")
                .append("    j2ee: jakarta\n");
        if (config != null) env.append(config).append('\n');
        env.append("  namespace:\n")
                .append("    base: io.acme.").append(name).append('\n');
        Files.write(new File(dir, "env.yml").toPath(), env.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/entity.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * One run of the engine, as the plugin starts it, with -Pmode as given
     * (null for none) and further system properties as key=value.
     */
    private int run(String mode, String... properties) {
        List<String> keys = new ArrayList<String>();
        keys.add("elasticore.base.path");
        keys.add("elasticore.architecture");
        keys.add("elasticore.language");
        keys.add("mode");
        for (String p : properties) keys.add(p.substring(0, p.indexOf('=')));
        String[] saved = new String[keys.size()];
        for (int i = 0; i < keys.size(); i++) saved[i] = System.getProperty(keys.get(i));
        java.io.PrintStream out = System.out;
        java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            set("elasticore.architecture", architecture);
            set("elasticore.language", language);
            set("mode", mode);
            for (String p : properties) {
                int eq = p.indexOf('=');
                System.setProperty(p.substring(0, eq), p.substring(eq + 1));
            }
            System.setOut(new java.io.PrintStream(captured, true, "UTF-8"));
            return RulesGenRunner.run(new String[0]);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.size(); i++) set(keys.get(i), saved[i]);
            log = new String(captured.toByteArray(), StandardCharsets.UTF_8);
            out.print(log);
        }
    }

    private static void set(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }

    /** Every file by its content hash, and every directory, under the project. */
    private Map<String, String> snapshot() throws IOException {
        final Map<String, String> tree = new TreeMap<String, String>();
        final Path root = project.toPath();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.getFileName() != null && ASIDE.equals(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                tree.put(root.relativize(dir).toString().replace('\\', '/') + "/", "dir");
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                tree.put(root.relativize(file).toString().replace('\\', '/'), sha256(Files.readAllBytes(file)));
                return FileVisitResult.CONTINUE;
            }
        });
        return tree;
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The one generated source of this name, in any source root. */
    private File find(final String fileName) throws IOException {
        return find(project, fileName);
    }

    /** The one generated source of this name, in any source root of a module (or of the project). */
    private File find(File root, final String fileName) throws IOException {
        final List<File> found = new ArrayList<File>();
        Files.walkFileTree(new File(root, "src").toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().equals(fileName)) found.add(file.toFile());
                return FileVisitResult.CONTINUE;
            }
        });
        assertEquals(1, found.size(), "expected one " + fileName + ", found " + found + "\n" + log);
        return found.get(0);
    }

    /** Takes a file out of the tree without deleting it. */
    private void moveAside(File file) throws IOException {
        Path aside = Files.createDirectories(new File(project, ASIDE).toPath());
        Files.move(file.toPath(), aside.resolve(file.getName() + "-" + System.nanoTime()),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static void deleteTree(Path top) throws IOException {
        Files.walkFileTree(top, new SimpleFileVisitor<Path>() {
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

    @AfterEach
    void removeProject() throws IOException {
        if (project != null && project.isDirectory()) deleteTree(project.toPath());
    }

    @AfterAll
    static void reset() throws Exception {
        java.lang.reflect.Field tmp = io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
    }
}
