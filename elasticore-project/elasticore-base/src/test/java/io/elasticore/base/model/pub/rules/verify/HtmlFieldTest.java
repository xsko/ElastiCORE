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
 * An {@code html} field (issue 183): the CoreService cleans it with a jsoup safelist on every write, and
 * {@code @plain(field)} keeps its text without tags in a field no request writes. What the cleaning does to markup
 * is HtmlTest's (the runtime); what a request sees, the samples'.
 */
public class HtmlFieldTest {

    private static final String MODEL = String.join(
            "\n",
            "enumeration:",
            "  HtStatus:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(3) @code",
            "    enum:",
            "      DRAFT:     DRF",
            "      PUBLISHED: PUB",
            "",
            "entity:",
            "  HtPost:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      postId:      long @id @sequence",
            "      title:       string(100)!",
            "      content:     html! @plain(contentText)",
            "      contentText: text @search(like)",
            "      summary:     html @safe(relaxed)",
            "      status:      HtStatus",
            "    states:",
            "      status:",
            "        initial: DRAFT",
            "        publish: DRAFT -> PUBLISHED",
            "    commands:",
            "      publish:",
            "        content: html!",
            "");

    private static final String CALL = "entity.cleanHtml();";

    /** The entity's method, as the Java rule sets write it: through the accessors, its own under DDD. */
    private static final String METHOD = String.join(
            "\n",
            "\tpublic void cleanHtml() {",
            "\t    this.setContent(io.elasticore.springboot3.html.Html.clean(this.getContent(),"
                    + " org.jsoup.safety.Safelist.basic()));",
            "\t    this.setContentText(io.elasticore.springboot3.html.Html.plain(this.getContent()));",
            "\t    this.setSummary(io.elasticore.springboot3.html.Html.clean(this.getSummary(),"
                    + " org.jsoup.safety.Safelist.relaxed()));",
            "\t}");

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
    public void aLayeredServiceCleansOnEveryWriteBeforeTheDevelopersBeforeSave() throws Exception {
        String[] log = new String[1];
        File project = run("elcore-html-layered", "layered", "java", "jpa", MODEL, false, 0, log);
        String core = found(project, "HtPostCoreService.java");
        // save, update, update by condition: each cleans, then hands over to the developer's beforeSave.
        assertEquals(3, occurrences(core, CALL), core);
        String save = section(core, "public HtPostDTO save(HtPostDTO dto) {");
        assertTrue(save.indexOf(CALL) < save.indexOf("beforeSave(entity, dto);"), save);
        assertTrue(save.indexOf("beforeSave(entity, dto);") < save.indexOf(".save(entity)"), save);
        assertTrue(section(core, "public HtPostDTO update(HtPostDTO dto) {").contains(CALL), core);
        assertTrue(
                section(core, "public int updateBySpecification(HtPostDTO dto, Specification<HtPost> spec) {")
                        .contains(CALL),
                core);

        // The entity cleans itself: each field to its safelist, the text copied where @plain says - only there.
        String entity = found(project, "HtPost.java");
        assertTrue(entity.contains(METHOD), entity);
        assertFalse(entity.contains("Html.plain(this.getSummary())"), entity);
        assertTrue(
                entity.contains("@Column(name = \"content\", columnDefinition = \"TEXT\", nullable = false)\n"
                        + "\tprivate String content;"),
                entity);

        // The copy is the server's: no request writes it.
        String dto = found(project, "HtPostDTO.java");
        assertTrue(
                dto.contains("@Schema(description = \"contentText\" , accessMode = Schema.AccessMode.READ_ONLY )"),
                dto);
        assertFalse(log[0].contains("unresolved type"), log[0]);
    }

    @Test
    public void kotlinCleansTheSameWay() throws Exception {
        File project = run("elcore-html-kt", "layered", "kotlin", "jpa", MODEL, false, 0, new String[1]);
        String core = found(project, "HtPostCoreService.kt");
        assertEquals(3, occurrences(core, "entity.cleanHtml()\n"), core);
        String entity = found(project, "HtPost.kt");
        assertTrue(entity.contains("fun cleanHtml() {"), entity);
        assertTrue(
                entity.contains("this.content = io.elasticore.springboot3.html.Html.clean(this.content,"
                        + " org.jsoup.safety.Safelist.basic())\n"),
                entity);
        assertTrue(
                entity.contains("this.contentText = io.elasticore.springboot3.html.Html.plain(this.content)\n"),
                entity);
        assertTrue(
                entity.contains("this.summary = io.elasticore.springboot3.html.Html.clean(this.summary,"
                        + " org.jsoup.safety.Safelist.relaxed())\n"),
                entity);
    }

    @Test
    public void myBatisCleansInItsSavePath() throws Exception {
        String model = MODEL.substring(0, MODEL.indexOf("    states:"));
        // The layered service (mybatis_service) and the hexagonal one (mybatis_port_service).
        for (String architecture : new String[] {"layered", "hexagonal"}) {
            File project = run(
                    "elcore-html-mybatis-" + architecture,
                    architecture,
                    "java",
                    "mybatis",
                    model,
                    false,
                    0,
                    new String[1]);
            String core = found(project, "HtPostCoreService.java");
            assertEquals(3, occurrences(core, CALL), architecture + "\n" + core);
            String save = section(core, "public HtPostDTO save(HtPostDTO dto) {");
            assertTrue(save.indexOf(CALL) < save.indexOf("beforeSave(entity, dto);"), save);
            String entity =
                    "layered".equals(architecture) ? found(project, "HtPost.java") : found(project, "HtPostBase.java");
            assertTrue(entity.contains(METHOD), architecture + "\n" + entity);
        }
    }

    /** A MongoDB domain (mongo-springboot) cleans on the same three writes, before beforeSave. */
    @Test
    public void mongoCleansInItsSavePath() throws Exception {
        String model = MODEL.substring(0, MODEL.indexOf("    states:"));
        File project = run("elcore-html-mongo", "layered", "java", "mongo", model, false, 0, new String[1]);
        String core = found(project, "HtPostCoreService.java");
        assertEquals(3, occurrences(core, CALL), core);
        String save = section(core, "public HtPostDTO save(HtPostDTO dto) {");
        assertTrue(save.indexOf(CALL) < save.indexOf("beforeSave(entity, dto);"), save);
        String update = section(core, "public HtPostDTO update(HtPostDTO dto) {");
        assertTrue(update.indexOf(CALL) < update.indexOf("beforeSave(entity, dto);"), update);
        assertTrue(found(project, "HtPost.java").contains(METHOD), found(project, "HtPost.java"));
    }

    /**
     * A command's parameter is kept by the move inside the entity, past the save path: the service cleans after the
     * move. Under the DDD rule sets the setters are the entity's own, which is why the cleaning is its method.
     */
    @Test
    public void aCommandThatSetsTheFieldIsCleanedAfterItsMove() throws Exception {
        File project = run("elcore-html-command", "hexagonal", "java", "jpa", MODEL, false, 0, new String[1]);
        String base = found(project, "HtPostBase.java");
        assertTrue(section(base, "public void publish(").contains("this.content = content;"), base);
        assertTrue(base.contains(METHOD), base);
        String core = found(project, "HtPostCoreService.java");
        String publish = section(core, "public HtPostDTO publish(");
        int move = publish.indexOf("entity.publish(");
        assertTrue(move >= 0, publish);
        assertTrue(move < publish.indexOf(CALL), publish);
        assertTrue(publish.indexOf(CALL) < publish.indexOf(".save(entity)"), publish);
        assertFalse(core.contains("entity.setContent("), core);

        // Kotlin writes the command its own way: the same call after the move.
        File kt = run("elcore-html-command-kt", "hexagonal", "kotlin", "jpa", MODEL, false, 0, new String[1]);
        String ktCore = found(kt, "HtPostCoreService.kt");
        String ktPublish = section(ktCore, "fun publish(");
        int ktMove = ktPublish.indexOf("entity.publish(");
        assertTrue(ktMove >= 0, ktPublish);
        assertTrue(ktMove < ktPublish.indexOf("entity.cleanHtml()\n"), ktPublish);
        assertTrue(ktPublish.indexOf("entity.cleanHtml()\n") < ktPublish.indexOf(".save(entity)"), ktPublish);
    }

    @Test
    public void theTypeScriptClientSaysWhichFieldIsHtml() throws Exception {
        File project = run("elcore-html-ts", "layered", "typescript", "jpa", MODEL, false, 0, new String[1]);
        String ts = found(project, "HtPostDTO.ts");
        assertTrue(ts.contains("content: { label: \"content\", html: true, required: true },"), ts);
        assertTrue(ts.contains("summary: { label: \"summary\", html: true },"), ts);
        assertTrue(ts.contains("contentText: { label: \"contentText\", readOnly: true },"), ts);
        assertTrue(ts.contains("title: { label: \"title\", required: true, maxLength: 100 },"), ts);
    }

    @Test
    public void aModelWithoutHtmlIsWrittenAsBefore() throws Exception {
        String model = String.join(
                "\n",
                "entity:",
                "  HtNote:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      noteId: long @id @sequence",
                "      body:   text",
                "");
        for (String mode : new String[] {"jpa", "mybatis", "mongo"}) {
            File project = run("elcore-html-none-" + mode, "layered", "java", mode, model, false, 0, new String[1]);
            String core = found(project, "HtNoteCoreService.java");
            assertFalse(core.contains("Html."), core);
            assertFalse(core.contains("jsoup"), core);
            // The empty slot leaves no line behind: save, update and update by condition read as before.
            assertEquals(3, occurrences(core, "beforeSave(entity, dto);"), core);
            assertEquals(0, occurrences(core, "\n\n        beforeSave(entity, dto);"), core);
            assertEquals(0, occurrences(core, "\n\n            beforeSave(entity, dto);"), core);
            assertFalse(core.contains("${"), core);
        }
    }

    @Test
    public void whatTheDslGetsWrongStopsTheBuild() throws Exception {
        assertRefused(
                "elcore-html-safe",
                field("content: html @safe(strict)"),
                "[htforum] HtPost.content: @safe(strict) - the safelist is basic or relaxed");
        assertRefused(
                "elcore-html-plain-none",
                field("content: html @plain(missing)"),
                "HtPost.content: @plain(missing) names no field of HtPost - name the text field that keeps the text"
                        + " without tags");
        assertRefused(
                "elcore-html-plain-short",
                field("content: html @plain(title)"),
                "HtPost.content: @plain(title) - title is not a text field; the text without tags goes in one"
                        + " (title: text)");
        assertRefused(
                "elcore-html-plain-self",
                field("content: html @plain(content)"),
                "HtPost.content: @plain(content) - content is not a text field");
        assertRefused(
                "elcore-html-not-html",
                field("note: text @safe(basic)"),
                "HtPost.note: @safe is for an html field (content: html @safe(basic) @plain(contentText))");
    }

    @Test
    public void anHtmlFieldOnAMemberOrAValueObjectIsRefused() throws Exception {
        String member = String.join(
                "\n",
                "entity:",
                "  HtPost:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      postId: long @id @sequence",
                "  HtComment:",
                "    meta: entity @aggregate(HtPost)",
                "    fields:",
                "      commentId: long @id @sequence",
                "      body:      html",
                "  HtBlock:",
                "    meta: entity @embeddable",
                "    fields:",
                "      body: html",
                "");
        String[] log = new String[1];
        run("elcore-html-member", "layered", "java", "jpa", member, false, 1, log);
        assertTrue(
                log[0].contains("HtComment.body: an html field is cleaned by the service that saves its entity,"
                        + " and HtComment is saved through HtPost - put the field on a root"),
                log[0]);
        assertTrue(
                log[0].contains("HtBlock.body: an html field is cleaned by the service that saves its entity,"
                        + " and a value object has none - put the field on the entity"),
                log[0]);
    }

    @Test
    public void theTwoXCompatibleOutputStopsTheBuild() throws Exception {
        String[] log = new String[1];
        String model = MODEL.substring(0, MODEL.indexOf("    states:"));
        run("elcore-html-v2", "layered", "java", "jpa", model, true, 1, log);
        assertTrue(
                log[0].contains("HtPost.content: an html field is cleaned before it is saved by the 3.0 code;"
                        + " the 2.x-compatible output would store what a request sends - generate the 3.0 code (output v3),"
                        + " or declare it text"),
                log[0]);
    }

    // ------------------------------------------------------------------

    private static String field(String line) {
        return String.join(
                "\n",
                "entity:",
                "  HtPost:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      postId: long @id @sequence",
                "      title:  string(100)",
                "      " + line,
                "");
    }

    private static void assertRefused(String name, String model, String said) throws Exception {
        String[] log = new String[1];
        run(name, "layered", "java", "jpa", model, false, 1, log);
        assertTrue(log[0].contains(said), log[0]);
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
        File dir = new File(project, "src/main/resources/blueprint/htforum");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: htforum",
                                "    framework: springboot",
                                "    mode: " + mode,
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    base: io.acme.htforum",
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
