package io.elasticore.base.model.pub.rules.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Issue 321: a whole row of another entity is carried in a DTO when everyone who passes the holder's read passes
 * the referenced entity's read too - and then only in what a read answers. A Coverage read by a MANAGER carries the
 * MANAGER-only Product: the mapper fills it, while create, update, a command and me() - which check create, update,
 * the command or nothing, never read - answer without it. The way in stays closed: the DTO -> entity mapping never
 * writes the product. Generated in every mode and reading that has the paths: JPA, MyBatis and MongoDB; Java and
 * Kotlin; layered, ddd and hexagonal.
 */
public class CarriedRowTest {

    static final String MODEL = String.join(
            "\n",
            "entity:",
            "  CrProduct:",
            "    meta: entity @root @editable @expose(20) @access(read=MANAGER, create=admin, update=admin)",
            "    fields:",
            "      productCode: string(20) @id",
            "      name:        string(50)",
            "",
            "  CrCoverage:",
            "    meta: entity @root @editable @expose(20) @access(read=MANAGER, create=user, update=user)",
            "    fields:",
            "      coverageId: long @id @sequence",
            "      title:      string(50)",
            "      product:    CrProduct @ManyToOne @dtype(CrProductDTO)",
            "",
            "  CrNote:",
            "    meta: entity @root @editable @expose(20) @access(read=user, create=user, update=user)",
            "    fields:",
            "      noteId:  long @id @sequence",
            "      product: CrProduct @ManyToOne @dtype(CrProductDTO)",
            "");

    /** A root that is changed by a command, which answers with the DTO. */
    static final String CLAIM = String.join(
            "\n",
            "  CrClaim:",
            "    meta: entity @root @expose(20) @access(read=MANAGER, create=user, update=user, approve=user)",
            "    fields:",
            "      claimId: long @id @sequence",
            "      amount:  long",
            "      product: CrProduct @ManyToOne @dtype(CrProductDTO)",
            "    commands:",
            "      approve:",
            "");

    /** The entity that signs in, carrying a row its me() would hand over. */
    static final String MEMBER = String.join(
            "\n",
            "  CrMember:",
            "    meta: entity @root @editable @expose(20) @signin @owner(memberId) @access(read=MANAGER, create=public, update=owner)",
            "    fields:",
            "      memberId: long @id @sequence",
            "      login:    string(20)! @login @unique",
            "      password: string(255)! @password",
            "      product:  CrProduct @ManyToOne @dtype(CrProductDTO)",
            "");

    private static final List<File> projects = new ArrayList<File>();

    @AfterAll
    static void cleanUp() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
        SpecFactory.referenceRequiredByKey(false);
        for (File project : projects) delete(project);
    }

    @Test
    public void jpaLayeredJava() throws Exception {
        File p = run("elcore-carried-jlj", "layered", "java", "jpa", MODEL + MEMBER);
        assertCarried(p, false, "CrCoverage", "CrshopMapper.java");
        assertMe(p, false);
        assertNotCarried(p, false, "CrNote");
    }

    @Test
    public void jpaLayeredKotlin() throws Exception {
        File p = run("elcore-carried-jlk", "layered", "kotlin", "jpa", MODEL + MEMBER);
        assertCarried(p, true, "CrCoverage", "CrshopMapper.kt");
        assertMe(p, true);
        assertNotCarried(p, true, "CrNote");
    }

    @Test
    public void jpaHexagonalJava() throws Exception {
        File p = run("elcore-carried-jhj", "hexagonal", "java", "jpa", MODEL + CLAIM);
        assertCarried(p, false, "CrCoverage", "CrshopMapper.java");
        assertCommand(p, false);
    }

    @Test
    public void jpaHexagonalKotlin() throws Exception {
        File p = run("elcore-carried-jhk", "hexagonal", "kotlin", "jpa", MODEL + CLAIM);
        assertCarried(p, true, "CrCoverage", "CrshopMapper.kt");
        assertCommand(p, true);
    }

    @Test
    public void jpaDddJava() throws Exception {
        File p = run("elcore-carried-jdj", "ddd", "java", "jpa", MODEL + CLAIM);
        assertCarried(p, false, "CrCoverage", "CrshopMapper.java");
        assertCommand(p, false);
    }

    @Test
    public void myBatisLayeredJava() throws Exception {
        File p = run("elcore-carried-mlj", "layered", "java", "mybatis", MODEL + MEMBER);
        assertCarried(p, false, "CrCoverage", "CrshopMapper.java");
        assertMe(p, false);
    }

    @Test
    public void myBatisHexagonalJava() throws Exception {
        File p = run("elcore-carried-mhj", "hexagonal", "java", "mybatis", MODEL + CLAIM);
        assertCarried(p, false, "CrCoverage", "CrshopMapper.java");
        assertCommand(p, false);
    }

    @Test
    public void myBatisDddJava() throws Exception {
        File p = run("elcore-carried-mdj", "ddd", "java", "mybatis", MODEL);
        assertCarried(p, false, "CrCoverage", "CrshopMapper.java");
    }

    @Test
    public void mongoLayeredJava() throws Exception {
        File p = run("elcore-carried-glj", "layered", "java", "mongo", MODEL + MEMBER);
        assertCarried(p, false, "CrCoverage", "CrshopMapper.java");
        assertMe(p, false);
    }

    @Test
    public void mongoLayeredKotlin() throws Exception {
        File p = run("elcore-carried-glk", "layered", "kotlin", "mongo", MODEL);
        assertCarried(p, true, "CrCoverage", "CrshopMapper.kt");
    }

    /** The TypeScript type carries the row too, since the response does. */
    @Test
    public void theTypeScriptTypeCarriesTheRow() throws Exception {
        File p = run("elcore-carried-ts", "layered", "typescript", "jpa", MODEL);
        String ts = foundEnding(p, ".ts", "CrCoverageDTO");
        int at = ts.indexOf("interface CrCoverageDTO");
        assertTrue(at >= 0, ts);
        String type = ts.substring(at, ts.indexOf('}', at));
        assertTrue(type.contains("product?: CrProductDTO"), type);
    }

    // ---- what each reading must show

    /** The DTO carries the row, the mapper fills it and never writes it back, and create and update leave it out. */
    private static void assertCarried(File p, boolean kt, String entity, String mapperFile) throws IOException {
        String ext = kt ? ".kt" : ".java";
        String dto = found(p, entity + "DTO" + ext);
        assertTrue(dto.contains("CrProductDTO"), p + ": the DTO does not carry the product\n" + dto);

        String mapper = found(p, mapperFile);
        String forward = bodies(mapper, entity, entity + "DTO", kt);
        assertTrue(fills(forward, "product", kt), p + ": the mapper does not fill the product\n" + forward);
        String reverse = bodies(mapper, entity + "DTO", entity, kt);
        // The key still points the relation (to.setProduct(t) with t keyed by productProductCode); the row is not read.
        assertFalse(
                java.util.regex.Pattern.compile(kt ? "from\\.product\\b" : "from\\.getProduct\\(\\)")
                        .matcher(reverse)
                        .find(),
                p + ": the product is written from the DTO\n" + reverse);
        // Layered: productProductCode; ddd and hexagonal hold the reference by its id, productCode.
        assertTrue(
                reverse.contains(kt ? "from.productProductCode" : "from.getProductProductCode()")
                        || reverse.contains(kt ? "from.productCode" : "from.getProductCode()"),
                reverse);

        String core = found(p, entity + "CoreService" + ext);
        String clear = kt ? "dto?.product = null" : "dto.setProduct(null);";
        assertTrue(core.contains(clear), p + ": nothing clears the product\n" + core);
        String save = section(
                core,
                kt ? "open fun save(dto: " + entity + "DTO)" : "public " + entity + "DTO save(" + entity + "DTO dto)");
        assertTrue(save.contains("return withoutCarriedRows("), p + ": create answers with the product\n" + save);
        String update = section(
                core,
                kt
                        ? "open fun update(dto: " + entity + "DTO)"
                        : "public " + entity + "DTO update(" + entity + "DTO dto)");
        assertTrue(update.contains("return withoutCarriedRows("), p + ": update answers with the product\n" + update);
        // A read passes the holder's read, and answers with it.
        String read = section(core, kt ? "open fun findById(" : "public Optional<" + entity + "DTO> findById(");
        assertFalse(read.contains("withoutCarriedRows"), p + ": a read leaves the product out\n" + read);
    }

    /** An entity read at user carries no MANAGER-only row, and its service has nothing to clear. */
    private static void assertNotCarried(File p, boolean kt, String entity) throws IOException {
        String ext = kt ? ".kt" : ".java";
        String dto = found(p, entity + "DTO" + ext);
        assertFalse(dto.contains("CrProductDTO"), p + ": the DTO carries the product\n" + dto);
        String core = found(p, entity + "CoreService" + ext);
        assertFalse(core.contains("withoutCarriedRows"), p + "\n" + core);
    }

    private static void assertCommand(File p, boolean kt) throws IOException {
        String core = found(p, "CrClaimCoreService" + (kt ? ".kt" : ".java"));
        String approve = section(core, kt ? "open fun approve(" : "public CrClaimDTO approve(");
        assertTrue(
                approve.contains("return withoutCarriedRows("),
                p + ": the command answers with the product\n" + approve);
    }

    private static void assertMe(File p, boolean kt) throws IOException {
        String core = found(p, "CrMemberCoreService" + (kt ? ".kt" : ".java"));
        String me = section(core, kt ? "open fun me()" : "> me() {");
        assertTrue(me.contains("withoutCarriedRows("), p + ": me() answers with the product\n" + me);
        String signIn = section(core, kt ? "open fun authenticate(" : "> authenticate(");
        assertTrue(signIn.contains("withoutCarriedRows("), p + ": sign-in answers with the product\n" + signIn);
    }

    /** The bodies of the mappings from one type to another, all overloads. */
    private static String bodies(String mapper, String from, String to, boolean kt) {
        StringBuilder out = new StringBuilder();
        String head = kt
                ? "fun mapping(from: " + from + "?, to: " + to + "?"
                : "public static void mapping(" + from + " from, " + to + " to";
        int at = 0;
        while ((at = mapper.indexOf(head, at)) >= 0) {
            int end = mapper.indexOf("\n    }\n", at);
            out.append(mapper, at, end < 0 ? mapper.length() : end).append('\n');
            at = end < 0 ? mapper.length() : end;
        }
        assertTrue(out.length() > 0, head + " not in\n" + mapper);
        return out.toString();
    }

    private static boolean fills(String body, String field, boolean kt) {
        String cap = Character.toUpperCase(field.charAt(0)) + field.substring(1);
        return kt
                ? body.contains("from." + field + ",") || body.contains("to." + field + " = ")
                : body.contains("to::set" + cap + ",") || body.contains("to.set" + cap + "(");
    }

    private static String section(String source, String head) {
        int from = source.indexOf(head);
        assertTrue(from >= 0, head + "\n" + source);
        int to = source.indexOf("\n    }", from);
        return source.substring(from, to < 0 ? source.length() : to);
    }

    // ---- generation

    /** Generated without config: nestedRows - readers, the default from 3.1 (issue 321). */
    private static File run(String name, String architecture, String language, String mode, String model)
            throws Exception {
        File project = Files.createTempDirectory(name).toFile();
        projects.add(project);
        blueprint(project, mode, model, null);
        generate(project, architecture, language, () -> RulesGenRunner.run(new String[0]));
        return project;
    }

    /** The crshop domain of the model, with config: nestedRows set to the value (null: not said). */
    static void blueprint(File project, String mode, String model, String nestedRows) throws IOException {
        File dir = new File(project, "src/main/resources/blueprint/crshop");
        assertTrue(new File(dir, "model").mkdirs());
        List<String> env = new ArrayList<String>(java.util.Arrays.asList(
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: crshop",
                "    framework: springboot",
                "    mode: " + mode,
                "    j2ee: jakarta"));
        if (nestedRows != null) env.add("    nestedRows: " + nestedRows);
        env.addAll(java.util.Arrays.asList("  namespace:", "    base: io.acme.crshop", ""));
        Files.write(new File(dir, "env.yml").toPath(), String.join("\n", env).getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
    }

    /** Runs a generation over the project, as the rule engine's runner is asked to; the log, and exit 0. */
    static String generate(
            File project, String architecture, String language, java.util.concurrent.Callable<Integer> runner)
            throws Exception {
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
        int code;
        try {
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", "typescript".equals(language) ? "java" : language);
            System.setProperty("elasticore.rules.force", "true");
            if ("layered".equals(architecture)) System.setProperty("elasticore.rules.v3", "true");
            if ("typescript".equals(language)) System.setProperty("elasticore.rules.ts", "true");
            code = runner.call();
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
            io.elasticore.base.model.pub.rules.ModelCheck.reset();
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        assertEquals(0, code, log);
        return log;
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String found(File project, String fileName) throws IOException {
        List<File> hits = new ArrayList<File>();
        collect(project, fileName, hits);
        assertEquals(1, hits.size(), fileName + " in " + project + ": " + hits);
        return read(hits.get(0));
    }

    /** The one file of that ending that mentions the text. */
    private static String foundEnding(File project, String ending, String mentions) throws IOException {
        List<File> all = new ArrayList<File>();
        collectEnding(project, ending, all);
        for (File f : all) {
            String text = read(f);
            if (text.contains("interface " + mentions)) return text;
        }
        assertTrue(false, mentions + " in no " + ending + " of " + project + ": " + all);
        return null;
    }

    private static void collect(File dir, String fileName, List<File> hits) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, fileName, hits);
            else if (f.getName().equals(fileName)) hits.add(f);
        }
    }

    private static void collectEnding(File dir, String ending, List<File> hits) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collectEnding(f, ending, hits);
            else if (f.getName().endsWith(ending)) hits.add(f);
        }
    }

    static void delete(File root) throws IOException {
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
