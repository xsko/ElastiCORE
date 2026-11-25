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

    /** The service hands the entity its pair for content. */
    private static final String CALL =
            "entity.cleanContent(html -> cleanContent(entity, html), html -> plainContent(entity, html));";

    /** ... and its one method for summary, which has no @plain. */
    private static final String SUMMARY_CALL = "entity.cleanSummary(html -> cleanSummary(entity, html));";

    /** An update keeps the value it read, before the mapper writes the row. */
    private static final String KEPT = "String contentBefore = entity.getContent();";

    /** ... and cleans only where the request changed it. */
    private static final String CHANGED = "if (!java.util.Objects.equals(entity.getContent(), contentBefore)"
            + " || (entity.getContent() != null && entity.getContentText() == null))";

    /** summary has no @plain: only a change cleans it. */
    private static final String SUMMARY_CHANGED = "if (!java.util.Objects.equals(entity.getSummary(), summaryBefore))";

    /** The entity keeps cleanHtml() for the developer's own code: the defaults, field by field. */
    private static final String KEPT_METHOD = String.join(
            "\n",
            "\tpublic void cleanHtml() {",
            "\t    this.cleanContent(html -> io.elasticore.springboot3.html.Html.clean(html, org.jsoup.safety.Safelist.basic()),"
                    + " html -> io.elasticore.springboot3.html.Html.plain(html));",
            "\t    this.cleanSummary(html -> io.elasticore.springboot3.html.Html.clean(html,"
                    + " org.jsoup.safety.Safelist.relaxed()));",
            "\t}");

    /** A create cleans unless the field is null. */
    private static final String CREATED = "if (entity.getContent() != null)";

    /** The entity's methods, as the Java rule sets write them: through the accessors, its own under DDD. */
    private static final String METHOD = String.join(
            "\n",
            "\tpublic void cleanContent(java.util.function.UnaryOperator<String> clean,"
                    + " java.util.function.UnaryOperator<String> plain) {",
            "\t    String html = this.getContent() == null ? null : clean.apply(this.getContent());",
            "\t    this.setContent(html);",
            "\t    this.setContentText(html == null ? null : plain.apply(html));",
            "\t}");

    private static final String SUMMARY_METHOD = String.join(
            "\n",
            "\tpublic void cleanSummary(java.util.function.UnaryOperator<String> clean) {",
            "\t    String html = this.getSummary() == null ? null : clean.apply(this.getSummary());",
            "\t    this.setSummary(html);",
            "\t}");

    /** The CoreService's pair for content, which the developer's service overrides. */
    private static final String PAIR = String.join(
            "\n",
            "    protected String cleanContent(HtPost row, String html) {",
            "        return io.elasticore.springboot3.html.Html.clean(html, org.jsoup.safety.Safelist.basic());",
            "    }",
            "",
            "    /**",
            "     * The text of HtPost.content without tags, kept in contentText (@plain): gets what cleanContent"
                    + " returned.",
            "     * io.elasticore.springboot3.html.Html.plainLines(html) keeps a line per paragraph.",
            "     */",
            "    protected String plainContent(HtPost row, String html) {",
            "        return io.elasticore.springboot3.html.Html.plain(html);",
            "    }");

    private static final String SUMMARY_PAIR = String.join(
            "\n",
            "    protected String cleanSummary(HtPost row, String html) {",
            "        return io.elasticore.springboot3.html.Html.clean(html, org.jsoup.safety.Safelist.relaxed());",
            "    }");

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
        assertEquals(3, occurrences(core, SUMMARY_CALL), core);
        String save = section(core, "public HtPostDTO save(HtPostDTO dto) {");
        assertTrue(save.contains("        " + CREATED + "\n            " + CALL), save);
        assertTrue(save.indexOf(CALL) < save.indexOf("beforeSave(entity, dto);"), save);
        assertTrue(save.indexOf("beforeSave(entity, dto);") < save.indexOf(".save(entity)"), save);
        assertFalse(save.contains("contentBefore"), save);
        // An update reads the value before the mapper writes the row, and cleans only where it changed.
        String update = section(core, "public HtPostDTO update(HtPostDTO dto) {");
        assertTrue(update.indexOf(KEPT) < update.indexOf(".mapping(dto, entity"), update);
        // The value is read before the try that discards a refused row (RefusedWrite); the cleaning is inside it.
        assertTrue(update.indexOf(KEPT) < update.indexOf("try {"), update);
        assertTrue(update.contains("            " + CHANGED + "\n                " + CALL), update);
        assertTrue(update.indexOf(".mapping(dto, entity") < update.indexOf(CALL), update);
        assertTrue(update.indexOf(CALL) < update.indexOf("beforeSave(entity, dto);"), update);
        String all = section(core, "public int updateBySpecification(HtPostDTO dto, Specification<HtPost> spec) {");
        assertTrue(all.indexOf("            " + KEPT) < all.indexOf(".mapping(dto, entity"), all);
        assertTrue(all.contains("                " + CHANGED + "\n                    " + CALL), all);
        // The pair the developer's service overrides: the safelist @safe names, the text @plain asks for.
        assertTrue(core.contains(PAIR), core);
        assertTrue(core.contains(SUMMARY_PAIR), core);
        assertFalse(core.contains("plainSummary"), core);

        // The entity sets the fields from what it is handed: each field, the text copied where @plain says.
        String entity = found(project, "HtPost.java");
        assertTrue(entity.contains(METHOD), entity);
        assertTrue(entity.contains(SUMMARY_METHOD), entity);
        assertTrue(entity.contains(KEPT_METHOD), entity);
        assertTrue(all.contains("                " + SUMMARY_CHANGED + "\n                    " + SUMMARY_CALL), all);
        // The generated service no longer calls it.
        assertFalse(core.contains("cleanHtml"), core);
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
        String call = "entity.cleanContent({ cleanContent(entity, it) }, { plainContent(entity, it) })\n";
        assertEquals(3, occurrences(core, call), core);
        assertEquals(3, occurrences(core, "entity.cleanSummary { cleanSummary(entity, it) }\n"), core);
        assertEquals(1, occurrences(core, "\n        if (entity.content != null) " + call), core);
        String ktChanged =
                "if (entity.content != contentBefore || entity.content != null && entity.contentText == null) ";
        // Update and update by condition clean inside the try that discards a refused row (RefusedWrite).
        assertEquals(1, occurrences(core, "\n            " + ktChanged + call), core);
        assertEquals(1, occurrences(core, "\n                " + ktChanged + call), core);
        assertEquals(2, occurrences(core, "if (entity.summary != summaryBefore) entity.cleanSummary"), core);
        assertEquals(2, occurrences(core, "val contentBefore = entity.content\n"), core);
        String update = section(core, "fun update(dto: HtPostDTO): HtPostDTO? {");
        assertTrue(update.indexOf("val contentBefore") < update.indexOf(".mapping(dto, entity"), update);
        assertTrue(
                core.contains(String.join(
                        "\n",
                        "    protected open fun cleanContent(row: HtPost, html: String?): String? =",
                        "        io.elasticore.springboot3.html.Html.clean(html, org.jsoup.safety.Safelist.basic())")),
                core);
        assertTrue(
                core.contains(String.join(
                        "\n",
                        "    protected open fun plainContent(row: HtPost, html: String?): String? =",
                        "        io.elasticore.springboot3.html.Html.plain(html)")),
                core);
        assertTrue(
                core.contains(
                        "    protected open fun cleanSummary(row: HtPost, html: String?): String? =\n"
                                + "        io.elasticore.springboot3.html.Html.clean(html, org.jsoup.safety.Safelist.relaxed())"),
                core);
        String entity = found(project, "HtPost.kt");
        assertTrue(
                entity.contains(String.join(
                        "\n",
                        "    fun cleanContent(clean: (String) -> String?, plain: (String) -> String?) {",
                        "        val html = this.content?.let(clean)",
                        "        this.content = html",
                        "        this.contentText = html?.let(plain)",
                        "    }")),
                entity);
        assertTrue(entity.contains("fun cleanSummary(clean: (String) -> String?) {"), entity);
        assertTrue(
                entity.contains("    fun cleanHtml() {\n"
                        + "        this.cleanContent({ io.elasticore.springboot3.html.Html.clean(it, org.jsoup.safety.Safelist.basic()) },"
                        + " { io.elasticore.springboot3.html.Html.plain(it) })\n"
                        + "        this.cleanSummary { io.elasticore.springboot3.html.Html.clean(it, org.jsoup.safety.Safelist.relaxed()) }\n"
                        + "    }"),
                entity);
        assertFalse(core.contains("cleanHtml"), core);
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
            assertEquals(2, occurrences(core, KEPT), architecture + "\n" + core);
            assertEquals(2, occurrences(core, CHANGED), architecture + "\n" + core);
            assertTrue(core.contains(PAIR), architecture + "\n" + core);
            String save = section(core, "public HtPostDTO save(HtPostDTO dto) {");
            assertTrue(save.indexOf(CREATED) < save.indexOf(CALL), save);
            assertTrue(save.indexOf(CALL) < save.indexOf("beforeSave(entity, dto);"), save);
            String update = section(core, "public HtPostDTO update(HtPostDTO dto) {");
            assertTrue(update.indexOf(KEPT) < update.indexOf(".mapping(dto, entity"), update);
            assertTrue(update.indexOf(CALL) < update.indexOf("beforeSave(entity, dto);"), update);
            String entity =
                    "layered".equals(architecture) ? found(project, "HtPost.java") : found(project, "HtPostBase.java");
            assertTrue(entity.contains(METHOD), architecture + "\n" + entity);
            assertTrue(entity.contains(SUMMARY_METHOD), architecture + "\n" + entity);
            assertTrue(entity.contains(KEPT_METHOD), architecture + "\n" + entity);
        }
    }

    /** A MongoDB domain (mongo-springboot) cleans on the same three writes, before beforeSave. */
    @Test
    public void mongoCleansInItsSavePath() throws Exception {
        String model = MODEL.substring(0, MODEL.indexOf("    states:"));
        File project = run("elcore-html-mongo", "layered", "java", "mongo", model, false, 0, new String[1]);
        String core = found(project, "HtPostCoreService.java");
        assertEquals(3, occurrences(core, CALL), core);
        assertEquals(2, occurrences(core, KEPT), core);
        assertTrue(core.contains(PAIR), core);
        String save = section(core, "public HtPostDTO save(HtPostDTO dto) {");
        assertTrue(save.indexOf(CREATED) < save.indexOf(CALL), save);
        assertTrue(save.indexOf(CALL) < save.indexOf("beforeSave(entity, dto);"), save);
        String update = section(core, "public HtPostDTO update(HtPostDTO dto) {");
        assertTrue(update.indexOf(KEPT) < update.indexOf(".mapping(dto, entity"), update);
        assertTrue(update.indexOf(CHANGED) < update.indexOf(CALL), update);
        assertTrue(update.indexOf(CALL) < update.indexOf("beforeSave(entity, dto);"), update);
        assertTrue(found(project, "HtPost.java").contains(METHOD), found(project, "HtPost.java"));
    }

    /**
     * MongoDB keeps an entity without a collection of its own inside the document that holds it, and the root's
     * mapper writes it from the request: its html is cleaned on each of the root's writes, before beforeSave - a
     * collection's members, a single one, and the members they hold in turn. A member without html is left alone.
     */
    private static final String MONGO_MEMBERS = String.join(
            "\n",
            "entity:",
            "  HtOrder:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      orderId: long @id @sequence",
            "      orderNo: string(20)",
            "      lines:   List<HtLine> @OneToMany(mappedBy=\"order\") @dtype(List<HtLineDTO>)",
            "      tags:    List<HtTag> @OneToMany(mappedBy=\"order\") @dtype(List<HtTagDTO>)",
            "  HtLine:",
            "    meta: entity",
            "    fields:",
            "      lineId: long @id @sequence",
            "      order:  HtOrder @ManyToOne",
            "      note:   html @plain(noteText)",
            "      noteText: text",
            "      parts:  List<HtPart> @OneToMany(mappedBy=\"line\") @dtype(List<HtPartDTO>)",
            // A single member, inside a member: a root's own would be searched by its key, which no document stores.
            "      cover:  HtCover",
            "  HtPart:",
            "    meta: entity",
            "    fields:",
            "      partId: long @id @sequence",
            "      line:   HtLine @ManyToOne",
            "      body:   html @safe(relaxed)",
            "  HtCover:",
            "    meta: entity",
            "    fields:",
            "      coverId: long @id @sequence",
            "      caption: html",
            "  HtTag:",
            "    meta: entity",
            "    fields:",
            "      tagId: long @id @sequence",
            "      order: HtOrder @ManyToOne",
            "      label: string(20)",
            "dto:",
            "  HtLineDTO:",
            "    meta: dto @template(HtLine)",
            "  HtPartDTO:",
            "    meta: dto @template(HtPart)",
            "  HtTagDTO:",
            "    meta: dto @template(HtTag)",
            "");

    @Test
    public void mongoCleansTheMembersADocumentEmbedsOnEveryWrite() throws Exception {
        String[] log = new String[1];
        File project = run("elcore-html-mongo-members", "layered", "java", "mongo", MONGO_MEMBERS, false, 0, log);
        String core = found(project, "HtOrderCoreService.java");
        String line = found(project, "HtLine.java");
        int at = line.indexOf("package ") + "package ".length();
        String model = line.substring(at, line.indexOf(';', at)) + ".";
        String members = String.join(
                "\n",
                "        if (entity.getLines() != null) for (" + model + "HtLine m : entity.getLines()) {",
                "            m.cleanNote(html -> cleanNote(m, html), html -> plainNote(m, html));",
                "            if (m.getParts() != null) for (" + model + "HtPart m2 : m.getParts()) {",
                "                m2.cleanBody(html -> cleanBody(m2, html));",
                "            }",
                "            if (m.getCover() != null) {",
                "                " + model + "HtCover m2 = m.getCover();",
                "                m2.cleanCaption(html -> cleanCaption(m2, html));",
                "            }",
                "        }",
                "        beforeSave(entity, dto);");
        // save, update and update by condition (a batch update is update's): the root has no html of its own.
        assertEquals(2, occurrences(core, members), core);
        assertEquals(1, occurrences(core, members.replace("\n        ", "\n            ")), core);
        assertFalse(core.contains("entity.clean"), core);
        assertFalse(core.contains("Before = entity."), core);
        // Three members cleaned on each of the three writes; the tags, which have no html, are not.
        assertEquals(9, occurrences(core, "html -> clean"), core);
        String save = section(core, "public HtOrderDTO save(HtOrderDTO dto) {");
        assertTrue(save.indexOf("m.cleanNote(") < save.indexOf("beforeSave(entity, dto);"), save);
        String update = section(core, "public HtOrderDTO update(HtOrderDTO dto) {");
        assertTrue(update.indexOf("mapping(dto, entity") < update.indexOf("m.cleanNote("), update);
        assertTrue(update.indexOf("m.cleanNote(") < update.indexOf("beforeSave(entity, dto);"), update);
        // The root's service has each member's pair, once, for the member's type.
        assertEquals(1, occurrences(core, "protected String cleanNote(" + model + "HtLine row, String html) {"), core);
        assertEquals(1, occurrences(core, "protected String plainNote(" + model + "HtLine row, String html) {"), core);
        assertEquals(1, occurrences(core, "protected String cleanBody(" + model + "HtPart row, String html) {"), core);
        assertTrue(core.contains("Html.clean(html, org.jsoup.safety.Safelist.relaxed());"), core);
        assertEquals(
                1, occurrences(core, "protected String cleanCaption(" + model + "HtCover row, String html) {"), core);
        assertTrue(
                found(project, "HtLine.java").contains("public void cleanNote(java.util.function.UnaryOperator"), core);
        assertFalse(found(project, "HtTag.java").contains("clean"), core);

        File kt = run("elcore-html-mongo-members-kt", "layered", "kotlin", "mongo", MONGO_MEMBERS, false, 0, log);
        String ktCore = found(kt, "HtOrderCoreService.kt");
        String ktMembers = String.join(
                "\n",
                "        for (m in entity.lines ?: emptyList<" + model + "HtLine>()) {",
                "            m.cleanNote({ cleanNote(m, it) }, { plainNote(m, it) })",
                "            for (m2 in m.parts ?: emptyList<" + model + "HtPart>()) {",
                "                m2.cleanBody { cleanBody(m2, it) }",
                "            }",
                "            m.cover?.let { m2 ->",
                "                m2.cleanCaption { cleanCaption(m2, it) }",
                "            }",
                "        }",
                "        beforeSave(entity, dto)");
        assertEquals(2, occurrences(ktCore, ktMembers), ktCore);
        assertEquals(1, occurrences(ktCore, ktMembers.replace("\n        ", "\n            ")), ktCore);
        assertTrue(
                ktCore.contains("protected open fun cleanNote(row: " + model + "HtLine, html: String?): String? ="),
                ktCore);
        assertTrue(found(kt, "HtLine.kt").contains("fun cleanNote(clean: (String) -> String?, plain:"), ktCore);
    }

    /** A root's own html is cleaned first, then its members'; a MongoDB model without html on members is as before. */
    @Test
    public void mongoCleansTheRootBeforeItsMembersAndLeavesOtherModelsAlone() throws Exception {
        String model =
                MONGO_MEMBERS.replace("      orderNo: string(20)", "      orderNo: string(20)\n      intro:   html");
        File project = run("elcore-html-mongo-root", "layered", "java", "mongo", model, false, 0, new String[1]);
        String save = section(found(project, "HtOrderCoreService.java"), "public HtOrderDTO save(HtOrderDTO dto) {");
        assertTrue(
                save.contains("        if (entity.getIntro() != null)\n"
                        + "            entity.cleanIntro(html -> cleanIntro(entity, html));\n"
                        + "        if (entity.getLines() != null) for ("),
                save);

        String plain = MONGO_MEMBERS
                .replace("html @plain(noteText)", "text")
                .replace("html @safe(relaxed)", "text")
                .replace("caption: html", "caption: text");
        File none = run("elcore-html-mongo-none", "layered", "java", "mongo", plain, false, 0, new String[1]);
        String core = found(none, "HtOrderCoreService.java");
        assertFalse(core.contains(".clean"), core);
        assertFalse(core.contains("Html."), core);
        assertEquals(0, occurrences(core, "\n\n        beforeSave(entity, dto);"), core);
        assertEquals(0, occurrences(core, "\n\n            beforeSave(entity, dto);"), core);
        assertFalse(core.contains("${"), core);

        // JPA keeps the same members in tables of their own, which the root's mapper does not write: no member call.
        File jpa = run("elcore-html-mongo-model-jpa", "layered", "java", "jpa", MONGO_MEMBERS, false, 0, new String[1]);
        String jpaCore = found(jpa, "HtOrderCoreService.java");
        assertFalse(jpaCore.contains(".clean"), jpaCore);
        assertFalse(jpaCore.contains("Html."), jpaCore);
        assertTrue(found(jpa, "HtLine.java").contains("public void cleanNote("), jpaCore);
    }

    /** Members that hold each other are kept to any depth, which the service's lines cannot reach: refused. */
    @Test
    public void mongoRefusesHtmlOnMembersThatHoldEachOther() throws Exception {
        String model = MONGO_MEMBERS.replace(
                "      body:   html @safe(relaxed)",
                "      body:   html @safe(relaxed)\n      lines:  List<HtLine> @dtype(List<HtLineDTO>)");
        String[] log = new String[1];
        run("elcore-html-mongo-cycle", "layered", "java", "mongo", model, false, 1, log);
        assertTrue(
                log[0].contains("HtLine.note: MongoDB keeps HtLine inside the HtOrder document to any depth"
                        + " (HtLine -> HtPart -> HtLine), and the service cleans an html field only to a fixed depth"
                        + " - break the cycle, or declare the field text"),
                log[0]);
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
        // The value read before the move; the cleaning after it, only where the move changed the field.
        assertTrue(publish.indexOf("        " + KEPT) < move, publish);
        assertTrue(publish.indexOf("String summaryBefore = entity.getSummary();") < move, publish);
        // Inside the try that discards a refused row (RefusedWrite): one step further in.
        assertTrue(move < publish.indexOf("            " + CHANGED + "\n                " + CALL), publish);
        assertTrue(publish.indexOf(KEPT) < publish.indexOf("try {"), publish);
        assertTrue(publish.indexOf("try {") < move, publish);
        assertTrue(move < publish.indexOf(SUMMARY_CALL), publish);
        assertTrue(publish.indexOf(CALL) < publish.indexOf(".save(entity)"), publish);
        assertFalse(core.contains("entity.setContent("), core);
        assertTrue(core.contains(PAIR), core);
        assertTrue(base.contains(METHOD), base);

        // Kotlin writes the command its own way: the same lines around the move.
        File kt = run("elcore-html-command-kt", "hexagonal", "kotlin", "jpa", MODEL, false, 0, new String[1]);
        String ktCore = found(kt, "HtPostCoreService.kt");
        String ktPublish = section(ktCore, "fun publish(");
        int ktMove = ktPublish.indexOf("entity.publish(");
        String ktCall = "if (entity.content != contentBefore || entity.content != null && entity.contentText == null)"
                + " entity.cleanContent({ cleanContent(entity, it) }, { plainContent(entity, it) })\n";
        assertTrue(ktMove >= 0, ktPublish);
        assertTrue(ktPublish.indexOf("        val contentBefore = entity.content\n") < ktMove, ktPublish);
        assertTrue(ktMove < ktPublish.indexOf("        " + ktCall), ktPublish);
        assertTrue(ktPublish.indexOf(ktCall) < ktPublish.indexOf(".save(entity)"), ktPublish);
    }

    /**
     * An html field on an @abstract parent: its methods are the parent's class's, which the child extends - written
     * once, not again in the child (Kotlin would need override) - and the child's cleanHtml() calls the parent's.
     */
    private static final String EXTENDED = String.join(
            "\n",
            "entity:",
            "  HtBase:",
            "    meta: entity @abstract",
            "    fields:",
            "      content:     html @plain(contentText)",
            "      contentText: text",
            "  HtPost:",
            "    meta: entity @root @expose(20) @extend(HtBase)",
            "    fields:",
            "      postId:  long @id @sequence",
            "      summary: html @safe(relaxed)",
            "  HtNote:",
            "    meta: entity @root @expose(20) @extend(HtBase)",
            "    fields:",
            "      noteId: long @id @sequence",
            "");

    @Test
    public void aParentsHtmlFieldHasItsMethodsInTheParentOnly() throws Exception {
        File project = run("elcore-html-extend", "layered", "java", "jpa", EXTENDED, false, 0, new String[1]);
        String base = found(project, "HtBase.java");
        assertTrue(base.contains("public void cleanContent(java.util.function.UnaryOperator<String> clean,"), base);
        assertTrue(base.contains("\tpublic void cleanHtml() {\n\t    this.cleanContent("), base);
        String post = found(project, "HtPost.java");
        assertFalse(post.contains("public void cleanContent("), post);
        assertTrue(post.contains("public void cleanSummary("), post);
        assertTrue(
                post.contains(
                        "\t@Override\n\tpublic void cleanHtml() {\n\t    super.cleanHtml();\n\t    this.cleanSummary("),
                post);
        // A child without html of its own inherits both.
        assertFalse(found(project, "HtNote.java").contains("cleanHtml"), found(project, "HtNote.java"));
        // Each child's service has the pair of every field it writes, the parent's included.
        String core = found(project, "HtPostCoreService.java");
        assertTrue(core.contains("protected String cleanContent(HtPost row, String html) {"), core);
        assertTrue(core.contains("protected String cleanSummary(HtPost row, String html) {"), core);
        assertTrue(found(project, "HtNoteCoreService.java").contains("protected String cleanContent(HtNote row"), core);

        File kt = run("elcore-html-extend-kt", "layered", "kotlin", "jpa", EXTENDED, false, 0, new String[1]);
        String ktBase = found(kt, "HtBase.kt");
        assertTrue(ktBase.contains("    open fun cleanHtml() {\n"), ktBase);
        String ktPost = found(kt, "HtPost.kt");
        assertFalse(ktPost.contains("fun cleanContent("), ktPost);
        assertTrue(
                ktPost.contains(
                        "    override fun cleanHtml() {\n        super.cleanHtml()\n        this.cleanSummary {"),
                ktPost);
        assertFalse(found(kt, "HtNote.kt").contains("cleanHtml"), found(kt, "HtNote.kt"));
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
            assertFalse(core.contains("Before = entity."), core);
            assertFalse(core.contains("java.util.Objects"), core);
            // The empty slot leaves no line behind: save, update and update by condition read as before.
            assertEquals(3, occurrences(core, "beforeSave(entity, dto);"), core);
            assertEquals(0, occurrences(core, "\n\n        beforeSave(entity, dto);"), core);
            assertEquals(0, occurrences(core, "\n\n            beforeSave(entity, dto);"), core);
            assertFalse(core.contains("${"), core);
        }
        // Kotlin: JPA and MongoDB (Kotlin has no MyBatis rule set).
        for (String mode : new String[] {"jpa", "mongo"}) {
            File project =
                    run("elcore-html-none-kt-" + mode, "layered", "kotlin", mode, model, false, 0, new String[1]);
            String core = found(project, "HtNoteCoreService.kt");
            assertFalse(core.contains("Html."), core);
            assertFalse(core.contains("Before = entity."), core);
            assertEquals(3, occurrences(core, "beforeSave(entity, dto)\n"), core);
            assertEquals(0, occurrences(core, "\n\n        beforeSave(entity, dto)"), core);
            assertEquals(0, occurrences(core, "\n\n            beforeSave(entity, dto)"), core);
            assertEquals(0, occurrences(core, "\n\n        HtforumMapper.mapping("), core);
            assertFalse(core.contains("${"), core);
            assertFalse(found(project, "HtNote.kt").contains("cleanHtml"), core);
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
        int code;
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
            code = RulesGenRunner.run(new String[0]);
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
        if (exit != null) assertEquals(exit.intValue(), code, "the run's exit: " + project);
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
