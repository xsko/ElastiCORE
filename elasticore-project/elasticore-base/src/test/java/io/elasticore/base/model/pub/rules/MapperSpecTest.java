package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.model.pub.rules.base.MapperSpecRules;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JPA Specification generation, the largest single gap in the mapper layer:
 * makeSpecificationFieldCode is 240 lines of 2.x that had no counterpart.
 *
 * The primary case compares against the toSpec pair committed in
 * ContentMapper.java. The search DTO fed to the rules mirrors what the
 * search-DTO publisher derives from the entity - string fields get the
 * automatic form, date-times get between, everything else eq - because
 * that derivation happens upstream of emission.
 */
public class MapperSpecTest {

    private static final String GOLDEN =
            "elasticore-template/src/main/java/io/elasticore/blueprint/domain/content/dto/ContentMapper.java";

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    private String render(EntitySpec e) {
        return new RuleEngine().register(new MapperSpecRules())
                .register(new io.elasticore.base.model.pub.rules.base.MapperSpecShellRules())
                .run(e, "mapper").renderBlock("spec");
    }

    /** CommentSrchDTO as the search-DTO publisher derives it. */
    private EntitySpec commentSearchDto() {
        return new EntitySpec("Comment")
                .addField(loader.parse("articleArticleId", "string(36) @search(eq) @ref(article.articleId)"))
                .addField(loader.parse("commentId", "long @search(eq)"))
                .addField(loader.parse("content", "text @search"))
                .addField(loader.parse("authorName", "string(100) @search(like)"))
                .addField(loader.parse("authorEmail", "string(100) @search(eq)"))
                .addField(loader.parse("approved", "boolean @search(eq)"))
                .addField(loader.parse("createdBy", "string(50) @search"))
                .addField(loader.parse("createDate", "datetime @search(between)"))
                .addField(loader.parse("lastModifiedBy", "string(50) @search"))
                .addField(loader.parse("lastModifiedDate", "datetime @search(between)"));
    }

    // ------------------------------------------------------------------

    @Test
    public void toSpec_matchesCommittedContentMapper() throws IOException {
        String actual = render(commentSearchDto());
        String expected = extractToSpec();

        assertEquals(expected, actual);
    }

    /** From the toSpec(searchDTO) declaration to the close of the second overload. */
    private String extractToSpec() throws IOException {
        List<String> lines = readLines(golden());
        String start = "    public static Specification<Comment> toSpec(CommentSrchDTO searchDTO){";
        int s = lines.indexOf(start);
        assertTrue(s >= 0, "toSpec not found in ContentMapper.java");

        int closers = 0;
        int end = -1;
        for (int i = s; i < lines.size(); i++) {
            if (lines.get(i).equals("    }")) {
                closers++;
                if (closers == 2) { end = i; break; }
            }
        }
        assertTrue(end > s, "toSpec end not found");

        StringBuilder sb = new StringBuilder();
        for (int i = s; i <= end; i++) {
            sb.append(lines.get(i)).append('\n');
        }
        return sb.toString();
    }

    // ---- condition coverage beyond what the golden exercises ----------

    private String oneField(String dsl) {
        EntitySpec e = new EntitySpec("Order").addField(loader.parse("f", dsl));
        return render(e);
    }

    @Test
    public void comparisonConditions() {
        assertTrue(oneField("long @search(>=)").contains(
                "sp = sp.and((r,q,c) -> c.greaterThanOrEqualTo(r.get(\"f\"),f));"), oneField("long @search(>=)"));
        assertTrue(oneField("long @search(<=)").contains(
                "sp = sp.and((r,q,c) -> c.lessThanOrEqualTo(r.get(\"f\"),f));"), oneField("long @search(<=)"));
        assertTrue(oneField("long @search(>)").contains(
                "sp = sp.and((r,q,c) -> c.greaterThan(r.get(\"f\"),f));"), oneField("long @search(>)"));
        assertTrue(oneField("long @search(<)").contains(
                "sp = sp.and((r,q,c) -> c.lessThan(r.get(\"f\"),f));"), oneField("long @search(<)"));
    }

    @Test
    public void likeVariants() {
        assertTrue(oneField("string(30) @search(like)").contains(
                "c.like(r.get(\"f\"),\"%\" +trim(f)+ \"%\")"), oneField("string(30) @search(like)"));

        String ignoreCase = oneField("string(30) @search(%__%)");
        assertTrue(ignoreCase.contains(
                "c.like(c.lower(r.get(\"f\")),\"%\" +f.toLowerCase()+ \"%\")"), ignoreCase);

        String eqIgnoreCase = oneField("string(30) @search(__=)");
        assertTrue(eqIgnoreCase.contains(
                "c.equal(c.lower(r.get(\"f\")),f.toLowerCase())"), eqIgnoreCase);

        String suffix = oneField("string(30) @search(%_)");
        assertTrue(suffix.contains("c.like(r.get(\"f\"),\"%\" +trim(f))"), suffix);
    }

    private String renderIn(SourceCode.TemplateSet set, String fieldLine) {
        EntitySpec e = new EntitySpec("Order").addField(loader.parse("f", fieldLine));
        RuleEngine engine = new RuleEngine().templateSet(set).register(new MapperSpecRules());
        engine.register(set == SourceCode.TemplateSet.KOTLIN
                ? new io.elasticore.base.model.pub.rules.lang.kotlin.KotlinMapperSpecShellRules()
                : new io.elasticore.base.model.pub.rules.base.MapperSpecShellRules());
        return engine.run(e, "mapper").renderBlock("spec");
    }

    /**
     * The clean output reads a string's like as contains-ignoring-case, trimmed;
     * %_% keeps case, and the ignore-case symbols trim and lower in the root locale.
     */
    @Test
    public void likeIgnoresCase_v3() {
        SourceCode.TemplateSet v3 = SourceCode.TemplateSet.V3;
        String like = renderIn(v3, "string(30) @search(like)");
        assertTrue(like.contains("c.like(c.lower(r.get(\"f\")),\"%\" +trim(f).toLowerCase(java.util.Locale.ROOT)+ \"%\")"), like);
        assertEquals(like, renderIn(v3, "string(30) @search(%__%)"));
        String keeps = renderIn(v3, "string(30) @search(%_%)");
        assertTrue(keeps.contains("c.like(r.get(\"f\"),\"%\" +trim(f)+ \"%\")"), keeps);
        String number = renderIn(v3, "long @search(like)");
        assertTrue(number.contains("c.like(r.get(\"f\"),\"%\" +trim(f)+ \"%\")"), number);
        String eq = renderIn(v3, "string(30) @search(__=)");
        assertTrue(eq.contains("c.equal(c.lower(r.get(\"f\")),f.toLowerCase(java.util.Locale.ROOT))"), eq);
        String prefix = renderIn(v3, "string(30) @search(__%)");
        assertTrue(prefix.contains("c.like(c.lower(r.get(\"f\")),trim(f).toLowerCase(java.util.Locale.ROOT)+ \"%\")"), prefix);
        String through = renderIn(v3, "string(30) @search(like) @ref(a.b)");
        assertTrue(through.contains("c.like(c.lower(r.get(\"a\").get(\"b\")),"), through);
    }

    /** Kotlin lowers with lowercase(): toLowerCase() is an error from Kotlin 2.1 on. */
    @Test
    public void ignoreCase_kotlin() {
        for (String op : new String[]{"like", "%__%", "__=", "%__", "__%"}) {
            String out = renderIn(SourceCode.TemplateSet.KOTLIN, "string(30) @search(" + op + ")");
            assertTrue(out.contains(".lowercase()"), op + ": " + out);
            assertTrue(!out.contains("toLowerCase"), op + ": " + out);
        }
        String like = renderIn(SourceCode.TemplateSet.KOTLIN, "string(30) @search(like)");
        assertTrue(like.contains("\"%\" +trim(f)!!.lowercase()+ \"%\""), like);
    }

    /** An operator nobody reads is an equality - said in the clean output, as the 2.x output always did it. */
    @Test
    public void anUnknownConditionIsSaid() {
        io.elasticore.base.util.ConsoleLog.clearKey("DSL_WARN");
        SourceCode.resetWarnings();
        renderIn(SourceCode.TemplateSet.V3, "string(30) @search(ilike)");
        assertEquals(1, io.elasticore.base.util.ConsoleLog.countStoredLogkey("DSL_WARN"));
        io.elasticore.base.util.ConsoleLog.clearKey("DSL_WARN");
        SourceCode.resetWarnings();
        renderIn(SourceCode.TemplateSet.V3, "string(30) @search(like)");
        renderIn(SourceCode.TemplateSet.V3, "string(30) @search(%_)");
        renderIn(SourceCode.TemplateSet.V3, "string(30) @search(__%)");
        renderIn(SourceCode.TemplateSet.V3, "date @search(isnull)");
        renderIn(SourceCode.TemplateSet.V3, "date @search(~)");
        renderIn(SourceCode.TemplateSet.V3, "string(30) @search");
        // The 2.x output says nothing new.
        renderIn(SourceCode.TemplateSet.COMPATIBLE, "string(30) @search(ilike)");
        assertEquals(0, io.elasticore.base.util.ConsoleLog.countStoredLogkey("DSL_WARN"));
        SourceCode.resetWarnings();
    }

    /** A lone % searches by the end of the value, and the warning says so - not "by equality". */
    @Test
    public void aLonePercentIsSaidAsTheEndsWithItIs() throws Exception {
        io.elasticore.base.util.ConsoleLog.clearKey("DSL_WARN");
        SourceCode.resetWarnings();
        String out = renderIn(SourceCode.TemplateSet.V3, "string(30) @search(%)");
        assertTrue(out.contains("c.like(r.get(\"f\"),\"%\" +trim(f))"), out);
        assertEquals(1, io.elasticore.base.util.ConsoleLog.countStoredLogkey("DSL_WARN"));
        java.io.PrintStream saved = System.out;
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(bytes, true, "UTF-8"));
        try {
            io.elasticore.base.util.ConsoleLog.printStoredLog("DSL_WARN", "");
        } finally {
            System.setOut(saved);
        }
        String said = new String(bytes.toByteArray(), "UTF-8");
        assertTrue(said.contains("@search(%) searches by the end of the value"), said);
        assertFalse(said.contains("by equality"), said);
        io.elasticore.base.util.ConsoleLog.clearKey("DSL_WARN");
        SourceCode.resetWarnings();
    }

    @Test
    public void inCondition_onPlainField() {
        String out = oneField("string(30) @search(in)");
        assertTrue(out.contains("sp = sp.and((r, q, c) -> r.get(\"f\").in(f));"), out);
    }

    /** A collection field with in() joins the association. */
    @Test
    public void inCondition_onCollectionJoinsAssociation() {
        EntitySpec e = new EntitySpec("Article")
                .addField(loader.parse("tags", "List<Tag> @search(in)"));

        String out = render(e);
        assertTrue(out.contains("Join<Article, Tag> join = root.join(\"tags\");"), out);
        assertTrue(out.contains("return join.in(tags);"), out);
    }

    /** A collection field with eq joins on a single item instead. */
    @Test
    public void eqCondition_onCollectionJoinsSingleItem() {
        EntitySpec e = new EntitySpec("Article")
                .addField(loader.parse("tags", "List<Tag> @search(eq)"));

        String out = render(e);
        assertTrue(out.contains("Tag tagsItem = searchDTO.getTagsItem();"), out);
        assertTrue(out.contains("Join<Article, Tag> join = root.join(\"tags\");"), out);
        assertTrue(out.contains("return criteriaBuilder.equal(join, tagsItem);"), out);
    }

    /** LocalDate between shifts the upper bound by a day and uses lessThan. */
    @Test
    public void betweenOnLocalDate_shiftsUpperBound() {
        EntitySpec e = new EntitySpec("Order")
                .addField(loader.parse("orderDate", "date @search(between)"));

        String out = render(e);
        assertTrue(out.contains("java.time.LocalDate orderDateTo_2 = orderDateTo.plusDays(1);"), out);
        assertTrue(out.contains("c.between(r.get(\"orderDate\"),orderDateFrom,orderDateTo_2)"), out);
        assertTrue(out.contains("c.lessThan(r.get(\"orderDate\"),orderDateTo_2)"), out);
    }

    /** A custom entity field name may follow the condition, comma separated. */
    @Test
    public void conditionWithCustomFieldName() {
        String out = oneField("string(30) @search(eq,otherField)");
        assertTrue(out.contains("String otherField = searchDTO.getOtherField();"), out);
        assertTrue(out.contains("c.equal(r.get(\"otherField\"),otherField)"), out);
    }

    // ---- emptiness: @search(isnull) ---------------------------------------

    /** The clean (3.0) output, the only one that knows the isnull keyword. */
    private String renderClean(EntitySpec e) {
        return new RuleEngine().templateSet(SourceCode.TemplateSet.V3)
                .register(new MapperSpecRules())
                .register(new io.elasticore.base.model.pub.rules.base.MapperSpecShellRules())
                .run(e, "mapper").renderBlock("spec");
    }

    /**
     * The three-state property the search DTO declares: absent adds nothing,
     * true asks for an empty column, false for a set one.
     */
    @Test
    public void isnullCondition() {
        EntitySpec e = new EntitySpec("Order").addField(loader.parse("f", "date @search(isnull)"));

        String out = renderClean(e);
        assertTrue(out.contains(
                "        Boolean fEmpty = searchDTO.getFEmpty();\n"
                + "        if(hasValue(fEmpty)){\n"
                + "            if(fEmpty){\n"
                + "                sp = sp.and((r,q,c) -> c.isNull(r.get(\"f\")));\n"
                + "            }else{\n"
                + "                sp = sp.and((r,q,c) -> c.isNotNull(r.get(\"f\")));\n"
                + "            }\n"
                + "        }\n"), out);
        assertTrue(!out.contains("c.equal"), out);
        assertTrue(!out.contains("searchDTO.getF()"), out);
    }

    /** Through a relation the check reads the related column. */
    @Test
    public void isnullCondition_throughRef() {
        EntitySpec e = new EntitySpec("Shipment").addField(loader.parse("orderShippedDate",
                "date @search(isnull) @ref(order.shippedDate)"));

        String out = renderClean(e);
        assertTrue(out.contains("Boolean orderShippedDateEmpty = searchDTO.getOrderShippedDateEmpty();"), out);
        assertTrue(out.contains(
                "sp = sp.and((r,q,c) -> c.isNull(r.get(\"order\").get(\"shippedDate\")));"), out);
        assertTrue(out.contains(
                "sp = sp.and((r,q,c) -> c.isNotNull(r.get(\"order\").get(\"shippedDate\")));"), out);
    }

    /** The alias names the property read, as the search DTO names it. */
    @Test
    public void isnullCondition_alias() {
        EntitySpec e = new EntitySpec("Loan").addField(loader.parse("returnedDate",
                "date @search(isnull,open)"));

        String out = renderClean(e);
        assertTrue(out.contains("Boolean openEmpty = searchDTO.getOpenEmpty();"), out);
        assertTrue(out.contains("c.isNull(r.get(\"open\"))"), out);
    }

    /**
     * Kotlin through the same seam: the guard does not smart-cast, so the
     * value is asserted, and the lambda names the interface it becomes.
     */
    @Test
    public void isnullCondition_kotlin() {
        EntitySpec e = new EntitySpec("Order").addField(loader.parse("f", "date @search(isnull)"));

        String out = new RuleEngine().templateSet(SourceCode.TemplateSet.KOTLIN)
                .register(new MapperSpecRules())
                .register(new io.elasticore.base.model.pub.rules.lang.kotlin.KotlinMapperSpecShellRules())
                .run(e, "mapper").renderBlock("spec");
        assertTrue(out.contains(
                "        val fEmpty = searchDTO.fEmpty\n"
                + "        if(hasValue(fEmpty)){\n"
                + "            if(fEmpty!!){\n"
                + "                sp = sp.and(Specification<Order> { r,q,c -> c.isNull(r.get<java.time.LocalDate>(\"f\")) })\n"
                + "            }else{\n"
                + "                sp = sp.and(Specification<Order> { r,q,c -> c.isNotNull(r.get<java.time.LocalDate>(\"f\")) })\n"
                + "            }\n"
                + "        }\n"), out);
    }

    /**
     * The 2.x-compatible output does not know the keyword: it stays the
     * equality on the field's own type that 2.x writes for it.
     */
    @Test
    public void isnullCondition_compatibleOutputUnchanged() {
        String out = oneField("date @search(isnull)");
        assertTrue(out.contains("java.time.LocalDate f = searchDTO.getF();"), out);
        assertTrue(out.contains("sp = sp.and((r,q,c) -> c.equal(r.get(\"f\"),f));"), out);
        assertTrue(!out.contains("Empty"), out);
    }

    /** Fields without a search annotation take no part. */
    @Test
    public void fieldWithoutSearchAnnotation_isSkipped() {
        EntitySpec e = new EntitySpec("Order")
                .addField(loader.parse("hidden", "string(30)"))
                .addField(loader.parse("shown", "string(30) @search(eq)"));

        String out = render(e);
        assertTrue(out.contains("shown"), out);
        assertTrue(!out.contains("hidden"), out);
    }

    // ---- helpers -------------------------------------------------------

    private File golden() {
        String[] candidates = {"../" + GOLDEN, "elasticore-project/" + GOLDEN};
        for (String c : candidates) {
            File f = new File(c);
            if (f.isFile()) return f;
        }
        throw new IllegalStateException("ContentMapper.java not found from "
                + new File(".").getAbsolutePath());
    }

    private List<String> readLines(File f) throws IOException {
        List<String> lines = new ArrayList<String>();
        BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = r.readLine()) != null) lines.add(line);
        } finally {
            r.close();
        }
        return lines;
    }
}
