package io.elasticore.base.model.pub.jpa;

import io.elasticore.base.extract.ModelExtractor;
import io.elasticore.base.model.pub.rules.verify.RulesGenRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A search DTO must not depend on the order of the entities in the DSL (issue 376, the same fix as
 * 2.2.261026).
 *
 * The @expose search DTO used to write its automatic @search into the field objects, and the
 * fields of an @extend parent are one object for every entity that extends it. A @service entity
 * placed after an @expose sibling then got the parent's fields in its search DTO; placed before,
 * it did not. Each generation runs in a JVM of its own: the model context is static.
 */
class SearchDtoFieldOrderTest {

    private static final String ENV = String.join(
            "\n",
            "elasticore:",
            "  enable: true",
            "  config:",
            "    domainName: svc",
            "    framework: springboot",
            "    mode: jpa",
            "    j2ee: jakarta",
            "  namespace:",
            "    entity: com.x.svc.entity",
            "    dto: com.x.svc.dto",
            "    enumeration: com.x.svc.enums",
            "    repository: com.x.svc.repository",
            "    service: com.x.svc.service",
            "    control: com.x.svc.controller",
            "");

    private static final String AUDIT = String.join(
            "\n",
            "  AuditEntity:",
            "    meta: entity @abstract",
            "    fields:",
            "      createdBy: string(50) @updatable(false)",
            "      createDate: datetime @updatable(false)",
            "      lastModifiedBy: string(50)",
            "      lastModifiedDate: datetime",
            "");

    private static final String SERVICE = String.join(
            "\n",
            "  MasterServiceCategory:",
            "    meta: entity @service @extend(AuditEntity)",
            "    fields:",
            "      svcCategoryId: string(15) @id",
            "      svcId: string(15)",
            "      memo: string(500)",
            "");

    private static final String EXPOSE = String.join(
            "\n",
            "  MasterService:",
            "    meta: entity @expose @extend(AuditEntity)",
            "    fields:",
            "      svcId: string(15) @id",
            "      svcNm: string(100) @s(%%)",
            "");

    /** The issue's own model: env.yml says domainName, mode and namespace.base, nothing else. */
    private static final String ISSUE_ENV = String.join(
            "\n",
            "elasticore:",
            "  enable: true",
            "  config:",
            "    domainName: shop",
            "    mode: jpa",
            "  namespace:",
            "    base: com.example.shop",
            "");

    private static final String ISSUE_AUDIT = String.join(
            "\n",
            "  AuditEntity:",
            "    meta: entity @abstract",
            "    fields:",
            "      createdBy: string(50) @updatable(false)",
            "      createDate: datetime @updatable(false)",
            "");

    private static final String ISSUE_NOTICE = String.join(
            "\n",
            "  Notice:",
            "    meta: entity @service @extend(AuditEntity)",
            "    fields:",
            "      noticeId: long @id @sequence",
            "      title: string(100) @search(like)",
            "");

    private static final String ISSUE_POST = String.join(
            "\n",
            "  Post:",
            "    meta: entity @expose @extend(AuditEntity)",
            "    fields:",
            "      postId: long @id @sequence",
            "      title: string(100)",
            "");

    private static final String ORDER_A = "entity:\n" + ISSUE_AUDIT + ISSUE_NOTICE + ISSUE_POST;
    private static final String ORDER_B = "entity:\n" + ISSUE_AUDIT + ISSUE_POST + ISSUE_NOTICE;

    /** The rule engine writing the code 2.x writes, which the 2.x publisher's output is compared with. */
    private static final String[] RULES_V2 = {"elasticore.rules.v2", "elasticore.rules.force"};

    private static final String[] RULES_V3 = {"elasticore.rules.v3", "elasticore.rules.force"};

    private static final String[] RULES_TS = {"elasticore.rules.ts", "elasticore.rules.force"};

    @Test
    void serviceSearchDtoIsTheSameWhicheverEntityComesFirst(@TempDir Path tmp) throws Exception {
        Path serviceFirst = extract(tmp.resolve("service-first"), "entity:\n" + AUDIT + SERVICE + EXPOSE, null);
        Path exposeFirst = extract(tmp.resolve("expose-first"), "entity:\n" + AUDIT + EXPOSE + SERVICE, null);

        String a = found(serviceFirst, "MasterServiceCategorySrchDTO.java");
        String b = found(exposeFirst, "MasterServiceCategorySrchDTO.java");
        assertEquals(withoutStamp(b), withoutStamp(a));

        // the parent's fields are searched, as they were when the @expose entity came first
        for (String field : Arrays.asList(
                "String createdBy;",
                "createDateFrom;",
                "createDateTo;",
                "String lastModifiedBy;",
                "lastModifiedDateFrom;",
                "lastModifiedDateTo;")) {
            assertTrue(a.contains(field), field + " missing:\n" + a);
        }
        // own fields without @search stay out of a @service search DTO
        assertFalse(a.contains(" memo;"), a);

        String mapper = found(serviceFirst, "SvcMapper.java");
        assertTrue(mapper.contains("searchDTO.getCreatedBy()"), "the Specification searches the parent's fields");
        assertEquals(
                toSpecOf(found(exposeFirst, "SvcMapper.java"), "MasterServiceCategory"),
                toSpecOf(mapper, "MasterServiceCategory"));
    }

    @Test
    void ruleEngineAgreesWhicheverEntityComesFirst(@TempDir Path tmp) throws Exception {
        Path serviceFirst = rules(tmp.resolve("service-first"), "entity:\n" + AUDIT + SERVICE + EXPOSE, null, RULES_V2);
        Path exposeFirst = rules(tmp.resolve("expose-first"), "entity:\n" + AUDIT + EXPOSE + SERVICE, null, RULES_V2);
        Path publisher = extract(tmp.resolve("publisher"), "entity:\n" + AUDIT + SERVICE + EXPOSE, null);

        String a = found(serviceFirst, "MasterServiceCategorySrchDTO.java");
        assertEquals(withoutStamp(found(exposeFirst, "MasterServiceCategorySrchDTO.java")), withoutStamp(a));
        assertEquals(
                withoutStamp(found(publisher, "MasterServiceCategorySrchDTO.java")),
                withoutStamp(a),
                "the rule engine and the 2.x publisher differ");
        assertTrue(a.contains("String createdBy;"), a);

        String spec = toSpecOf(found(serviceFirst, "SvcMapper.java"), "MasterServiceCategory");
        assertTrue(spec.contains("getCreatedBy()"), spec);
        assertEquals(toSpecOf(found(exposeFirst, "SvcMapper.java"), "MasterServiceCategory"), spec);
        assertEquals(toSpecOf(found(publisher, "SvcMapper.java"), "MasterServiceCategory"), spec);
    }

    @Test
    void ruleEngineGivesADslSearchDtoInheritedFieldsOnly(@TempDir Path tmp) throws Exception {
        String expose = EXPOSE + "      memo: string(500)\n";
        String dto = String.join(
                "\n",
                "dto:",
                "  MasterServiceNameSrchDTO:",
                "    meta: dto @searchable(entity=MasterService)",
                "    fields:",
                "      keyword: string @search(like) @ref(svcNm)",
                "");
        Path rules = rules(tmp.resolve("rules"), "entity:\n" + AUDIT + expose + SERVICE, dto, RULES_V2);
        Path publisher = extract(tmp.resolve("publisher"), "entity:\n" + AUDIT + expose + SERVICE, dto);

        String srch = found(rules, "MasterServiceNameSrchDTO.java");
        assertTrue(srch.contains("String createdBy;"), srch);
        assertFalse(srch.contains(" memo;"), srch);
        assertEquals(withoutStamp(found(publisher, "MasterServiceNameSrchDTO.java")), withoutStamp(srch));

        String spec = toSpecOf(found(rules, "SvcMapper.java"), "MasterServiceName");
        assertTrue(spec.contains("getCreatedBy()"), spec);
        assertFalse(spec.contains("getMemo()"), spec);
        assertEquals(toSpecOf(found(publisher, "SvcMapper.java"), "MasterServiceName"), spec);
    }

    @Test
    void regeneratedMapperGetsSpecLinesThatRepeatAnotherMethod(@TempDir Path tmp) throws Exception {
        // MasterService searches its memo automatically (@expose); MasterServiceCategory does not yet
        String expose = EXPOSE + "      memo: string(500)\n";
        Path out = extract(tmp, "entity:\n" + AUDIT + expose + SERVICE, null);
        assertFalse(
                toSpecOf(found(out, "SvcMapper.java"), "MasterServiceCategory").contains("getMemo()"));

        // now it does: the one new toSpec line is the same as a line in MasterService's toSpec,
        // which a set-based comparison took for "unchanged", so the mapper was never rewritten
        String searchedMemo = SERVICE.replace("memo: string(500)", "memo: string(500) @search");
        extract(tmp, "entity:\n" + AUDIT + expose + searchedMemo, null);
        assertTrue(found(out, "MasterServiceCategorySrchDTO.java").contains("String memo;"));
        assertTrue(
                toSpecOf(found(out, "SvcMapper.java"), "MasterServiceCategory").contains("getMemo()"),
                "the existing mapper was not rewritten");
    }

    @Test
    void searchDtoWrittenInTheDslGetsInheritedFieldsNotTheEntityOwnFields(@TempDir Path tmp) throws Exception {
        String expose = EXPOSE + "      memo: string(500)\n";
        String dto = String.join(
                "\n",
                "dto:",
                "  MasterServiceNameSrchDTO:",
                "    meta: dto @searchable(entity=MasterService)",
                "    fields:",
                "      keyword: string @search(like) @ref(svcNm)",
                "");
        Path out = extract(tmp, "entity:\n" + AUDIT + expose + SERVICE, dto);

        String srch = found(out, "MasterServiceNameSrchDTO.java");
        assertTrue(srch.contains("String createdBy;"), srch);
        assertTrue(srch.contains("createDateFrom;"), srch);
        // MasterService's own memo is searched by MasterServiceSrchDTO (@expose), not by this one
        assertFalse(srch.contains(" memo;"), srch);

        String spec = toSpecOf(found(out, "SvcMapper.java"), "MasterServiceName");
        assertTrue(spec.contains("getCreatedBy()"), spec);
        assertFalse(spec.contains("getMemo()"), spec);
    }

    @Test
    void repeatedLinesCountButRepeatedImportsDoNot() throws Exception {
        java.lang.reflect.Method m =
                SrcFilePublisher.class.getDeclaredMethod("isSemanticallyIdentical", String.class, String.class);
        m.setAccessible(true);
        SrcFilePublisher publisher = new SrcFilePublisher(null);

        String once = "import a.*;\nclass A {\n  x();\n}\n";
        String importTwice = "import a.*;\nimport a.*;\nclass A {\n  x();\n}\n";
        String callTwice = "import a.*;\nclass A {\n  x();\n  x();\n}\n";
        String reordered = "class A {\n    x();\n}\nimport a.*;\n";

        assertEquals(true, m.invoke(publisher, once, importTwice), "an IDE removing a repeated import is no change");
        assertEquals(true, m.invoke(publisher, once, reordered), "order and indentation are no change");
        assertEquals(false, m.invoke(publisher, once, callTwice), "a repeated statement is a change");
    }

    @Test
    void sameSearchNameOnTwoFieldsKeepsTheFieldsAfterIt(@TempDir Path tmp) throws Exception {
        Path out = extract(
                tmp,
                String.join(
                        "\n",
                        "entity:",
                        "  Note:",
                        "    meta: entity @expose",
                        "    fields:",
                        "      noteId: string(15) @id",
                        "      title: string(100) @s(like$keyword)",
                        "      body: string(1000) @s(like$keyword)",
                        "      memo: string(500)",
                        "      hitCount: int",
                        ""),
                null);

        String dto = found(out, "NoteSrchDTO.java");
        assertTrue(dto.contains("String keyword;"), dto);
        assertTrue(dto.contains("String memo;"), dto);
        assertTrue(dto.contains("Integer hitCount;"), dto);
    }

    private static final String PRODUCT = String.join(
            "\n",
            "entity:",
            "  Product:",
            "    meta: entity @expose",
            "    fields:",
            "      productId: long @id @sequence",
            "      name: string(100)",
            "      price: int",
            "");

    private static final String PRODUCT_NAME_SEARCH = String.join(
            "\n",
            "dto:",
            "  ProductNameSrchDTO:",
            "    meta: dto @searchable(entity=Product)",
            "    fields:",
            "      keyword: string @search(like) @ref(name)",
            "");

    /**
     * A search DTO written in the DSL searches its entity's own fields only by their own @search, wherever its
     * dto: section is. The model files are read by name and a file's dto: section before its entity: one, so
     * a dto: section in search.yml came after the @expose ProductSrchDTO and got every field it had been given
     * a condition in (name, priceFrom/To); one in a-search.yml came before and did not.
     */
    @Test
    void declaredSearchDtoIsTheSameWhicheverFileItIsInWithThe2xGenerator(@TempDir Path tmp) throws Exception {
        assertDeclaredSearchDtoIgnoresItsFile(tmp, ModelExtractor.class);
    }

    @Test
    void declaredSearchDtoIsTheSameWhicheverFileItIsInWithTheCompatibleRuleEngine(@TempDir Path tmp) throws Exception {
        assertDeclaredSearchDtoIgnoresItsFile(tmp, RulesGenRunner.class, RULES_V2);
    }

    @Test
    void declaredSearchDtoIsTheSameWhicheverFileItIsInWithTheV3Code(@TempDir Path tmp) throws Exception {
        assertDeclaredSearchDtoIgnoresItsFile(tmp, RulesGenRunner.class, RULES_V3);
    }

    private static void assertDeclaredSearchDtoIgnoresItsFile(Path tmp, Class<?> generator, String... switches)
            throws Exception {
        Path before =
                generate(tmp.resolve("before"), ENV, PRODUCT, "a-search.yml", PRODUCT_NAME_SEARCH, generator, switches);
        Path after =
                generate(tmp.resolve("after"), ENV, PRODUCT, "search.yml", PRODUCT_NAME_SEARCH, generator, switches);

        String srch = found(after, "ProductNameSrchDTO.java");
        assertEquals(withoutStamp(found(before, "ProductNameSrchDTO.java")), withoutStamp(srch));
        assertTrue(srch.contains("String keyword;"), srch);
        for (String own : Arrays.asList(" name;", " price;", "priceFrom;", "priceTo;")) {
            assertFalse(srch.contains(own), own + "\n" + srch);
        }
        String spec = toSpecOf(found(after, "SvcMapper.java"), "ProductName");
        assertEquals(toSpecOf(found(before, "SvcMapper.java"), "ProductName"), spec);
        assertFalse(spec.contains("getPrice"), spec);

        // the @expose search DTO of the entity still searches every field
        String exposed = found(after, "ProductSrchDTO.java");
        assertTrue(exposed.contains(" name;") && exposed.contains(" price;"), exposed);
    }

    /** The way back for an own field such a search DTO used to get: a @search of its own on the entity. */
    @Test
    void ownSearchOnTheEntityBringsTheFieldBackToADeclaredSearchDto(@TempDir Path tmp) throws Exception {
        String searched = PRODUCT.replace("price: int", "price: int @search(eq)");
        Path publisher = generate(
                tmp.resolve("publisher"), ENV, searched, "search.yml", PRODUCT_NAME_SEARCH, ModelExtractor.class);
        Path v3 = generate(
                tmp.resolve("v3"), ENV, searched, "search.yml", PRODUCT_NAME_SEARCH, RulesGenRunner.class, RULES_V3);
        for (Path out : Arrays.asList(publisher, v3)) {
            String srch = found(out, "ProductNameSrchDTO.java");
            assertTrue(srch.contains("Integer price;"), out + "\n" + srch);
            assertFalse(srch.contains(" name;"), out + "\n" + srch);
            assertTrue(toSpecOf(found(out, "SvcMapper.java"), "ProductName").contains("getPrice()"), out.toString());
        }
    }

    /** The way out for a parent field a project does not want searched: @searchBypass, in every search DTO. */
    @Test
    void searchBypassKeepsAParentFieldOutOfEverySearchDto(@TempDir Path tmp) throws Exception {
        String audit = AUDIT.replace(
                "createdBy: string(50) @updatable(false)", "createdBy: string(50) @updatable(false) @searchBypass");
        String model = "entity:\n" + audit + SERVICE + EXPOSE;
        Path publisher = extract(tmp.resolve("publisher"), model, null);
        Path v3 = rules(tmp.resolve("v3"), model, null, RULES_V3);

        for (Path out : Arrays.asList(publisher, v3)) {
            for (String srch : Arrays.asList("MasterServiceCategorySrchDTO.java", "MasterServiceSrchDTO.java")) {
                String dto = found(out, srch);
                // not a search property (the 3.0 code still lists it among the sortable ones)
                assertFalse(dto.contains(" createdBy;"), srch + " in " + out + "\n" + dto);
                assertTrue(dto.contains("createDateFrom;"), srch + " in " + out + "\n" + dto);
            }
            String mapper = found(out, "SvcMapper.java");
            for (String entity : Arrays.asList("MasterServiceCategory", "MasterService")) {
                String spec = toSpecOf(mapper, entity);
                assertFalse(spec.contains("getCreatedBy()"), spec);
                assertTrue(spec.contains("getCreateDateFrom()"), spec);
            }
        }
    }

    // ---- the issue's model, in both orders, through each generator ----

    /** The default 3.0 Java code: NoticeSrchDTO and its toSpec are the same in both orders. */
    @Test
    void issueModelGivesTheSameV3CodeInBothOrders(@TempDir Path tmp) throws Exception {
        Path a = generate(tmp.resolve("a"), ISSUE_ENV, ORDER_A, null, RulesGenRunner.class, RULES_V3);
        Path b = generate(tmp.resolve("b"), ISSUE_ENV, ORDER_B, null, RulesGenRunner.class, RULES_V3);

        String notice = found(a, "NoticeSrchDTO.java");
        assertEquals(withoutStamp(found(b, "NoticeSrchDTO.java")), withoutStamp(notice));
        assertIssueFields(notice);

        String mapperA = found(a, "ShopMapper.java");
        String mapperB = found(b, "ShopMapper.java");
        String spec = toSpecOf(mapperA, "Notice");
        assertEquals(toSpecOf(mapperB, "Notice"), spec);
        assertTrue(spec.contains("searchDTO.getCreatedBy()"), spec);
        assertTrue(spec.contains("\"createDate\""), spec);
        assertTrue(spec.contains("searchDTO.getTitle()"), spec);
        System.out.println("[issue 376] NoticeSrchDTO.java, the same in both orders:\n" + notice);
        System.out.println("[issue 376] ShopMapper toSpec(NoticeSrchDTO), the same in both orders:\n" + spec);

        // Post (@expose) keeps every field, also the same in both orders
        String post = found(a, "PostSrchDTO.java");
        assertEquals(withoutStamp(found(b, "PostSrchDTO.java")), withoutStamp(post));
        assertIssueFields(post);
        assertEquals(toSpecOf(mapperB, "Post"), toSpecOf(mapperA, "Post"));
    }

    /** The TypeScript client: NoticeSrchDTO.ts is the same in both orders. */
    @Test
    void issueModelGivesTheSameTypeScriptInBothOrders(@TempDir Path tmp) throws Exception {
        Path a = generate(tmp.resolve("a"), ISSUE_ENV, ORDER_A, null, RulesGenRunner.class, RULES_TS);
        Path b = generate(tmp.resolve("b"), ISSUE_ENV, ORDER_B, null, RulesGenRunner.class, RULES_TS);

        String notice = found(a, "NoticeSrchDTO.ts");
        assertEquals(withoutStamp(found(b, "NoticeSrchDTO.ts")), withoutStamp(notice));
        assertTrue(notice.contains("createdBy?"), notice);
        assertTrue(notice.contains("createDateFrom?"), notice);
        assertTrue(notice.contains("createDateTo?"), notice);
        assertTrue(notice.contains("title?"), notice);
        assertFalse(notice.contains("noticeId?"), notice);
        System.out.println("[issue 376] NoticeSrchDTO.ts, the same in both orders:\n" + notice);
    }

    /** The 2.x publisher and the 2.x-compatible rule engine agree, in both orders. */
    @Test
    void issueModelGivesTheSame2xCodeInBothOrders(@TempDir Path tmp) throws Exception {
        // j2ee said, so both generators import jakarta (without it the 2.x one imports javax)
        String env = ISSUE_ENV.replace("    mode: jpa\n", "    mode: jpa\n    j2ee: jakarta\n");
        Path a = extract(tmp.resolve("a"), env, ORDER_A, null);
        Path b = extract(tmp.resolve("b"), env, ORDER_B, null);
        Path rulesB = generate(tmp.resolve("rules-b"), env, ORDER_B, null, RulesGenRunner.class, RULES_V2);

        String notice = withoutStamp(found(a, "NoticeSrchDTO.java"));
        assertEquals(withoutStamp(found(b, "NoticeSrchDTO.java")), notice);
        assertEquals(withoutStamp(found(rulesB, "NoticeSrchDTO.java")), notice);
        assertIssueFields(notice);

        String spec = toSpecOf(found(a, "ShopMapper.java"), "Notice");
        assertEquals(toSpecOf(found(b, "ShopMapper.java"), "Notice"), spec);
        assertEquals(toSpecOf(found(rulesB, "ShopMapper.java"), "Notice"), spec);
        assertTrue(spec.contains("searchDTO.getCreatedBy()"), spec);
    }

    /** Over MyBatis the search conditions come from the same decision: the same XML in both orders. */
    @Test
    void issueModelGivesTheSameMyBatisSearchInBothOrders(@TempDir Path tmp) throws Exception {
        String env = ISSUE_ENV.replace("mode: jpa", "mode: mybatis");
        Path a = generate(tmp.resolve("a"), env, ORDER_A, null, RulesGenRunner.class, RULES_V3);
        Path b = generate(tmp.resolve("b"), env, ORDER_B, null, RulesGenRunner.class, RULES_V3);

        assertIssueFields(found(a, "NoticeSrchDTO.java"));
        for (String xml : new String[] {"NoticeRepository.xml", "PostRepository.xml"}) {
            String inA = found(a, xml);
            assertEquals(found(b, xml), inA, xml);
            // the inherited fields are searched, by the condition the search DTO derived
            assertTrue(inA.contains("createdBy"), xml + "\n" + inA);
            assertTrue(inA.contains("createDateFrom"), xml + "\n" + inA);
        }
    }

    private static void assertIssueFields(String srchDto) {
        for (String field : Arrays.asList("String createdBy;", "createDateFrom;", "createDateTo;", "String title;")) {
            assertTrue(srchDto.contains(field), field + " missing:\n" + srchDto);
        }
    }

    // ------------------------------------------------------------------

    private static Path extract(Path project, String entityYml, String dtoYml)
            throws IOException, InterruptedException {
        return generate(project, ENV, entityYml, dtoYml, ModelExtractor.class);
    }

    private static Path extract(Path project, String env, String entityYml, String dtoYml)
            throws IOException, InterruptedException {
        return generate(project, env, entityYml, dtoYml, ModelExtractor.class);
    }

    private static Path rules(Path project, String entityYml, String dtoYml, String... switches)
            throws IOException, InterruptedException {
        return generate(project, ENV, entityYml, dtoYml, RulesGenRunner.class, switches);
    }

    /** The project's blueprint written, then one generator in a JVM of its own; the project. */
    private static Path generate(
            Path project, String env, String entityYml, String dtoYml, Class<?> generator, String... switches)
            throws IOException, InterruptedException {
        return generate(project, env, entityYml, "dto.yml", dtoYml, generator, switches);
    }

    /** As above, with the dto: section in a model file of the given name (files are read by name). */
    private static Path generate(
            Path project,
            String env,
            String entityYml,
            String dtoFile,
            String dtoYml,
            Class<?> generator,
            String... switches)
            throws IOException, InterruptedException {
        String domain = env.contains("domainName: shop") ? "shop" : "svc";
        Path blueprint = project.resolve("src/main/resources/blueprint/" + domain);
        Files.createDirectories(blueprint.resolve("model"));
        Files.write(blueprint.resolve("env.yml"), env.getBytes(StandardCharsets.UTF_8));
        Files.write(blueprint.resolve("model/entity.yml"), entityYml.getBytes(StandardCharsets.UTF_8));
        if (dtoYml != null) Files.write(blueprint.resolve("model/" + dtoFile), dtoYml.getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(project.resolve("src/main/java"));

        List<String> cmd = new ArrayList<>();
        cmd.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-Xmx256m");
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Delasticore.base.path=" + project.toAbsolutePath());
        for (String s : switches) cmd.add("-D" + s + "=true");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(generator.getName());

        Path log = project.resolve("gen-" + generator.getSimpleName() + ".log");
        Process process = new ProcessBuilder(cmd)
                .directory(project.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        process.getOutputStream().close();
        boolean finished = process.waitFor(180, TimeUnit.SECONDS);
        String output = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
        assertTrue(finished, "generator timed out\n" + output);
        assertEquals(0, process.exitValue(), output);
        assertFalse(output.contains("Exception"), output);
        return project;
    }

    private static String found(Path project, String fileName) throws IOException {
        List<File> hits = new ArrayList<>();
        collect(project.toFile(), fileName, hits);
        assertEquals(1, hits.size(), fileName + " in " + project + ": " + hits);
        return new String(Files.readAllBytes(hits.get(0).toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static void collect(File dir, String fileName, List<File> hits) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                // the merge cache keeps a copy of every generated file
                if (!f.getName().equals(".elasticore")) collect(f, fileName, hits);
            } else if (f.getName().equals(fileName)) {
                hits.add(f);
            }
        }
    }

    // the body of toSpec(<entity>SrchDTO searchDTO, Specification sp)
    private static String toSpecOf(String mapper, String entity) {
        int start = mapper.indexOf("toSpec(" + entity + "SrchDTO searchDTO, ");
        assertTrue(start >= 0, "no toSpec for " + entity + "\n" + mapper);
        return mapper.substring(start, mapper.indexOf("return sp;", start));
    }

    // a 2.x file's first line holds the generation timestamp
    private static String withoutStamp(String s) {
        return s.startsWith("//ecd:") ? s.substring(s.indexOf('\n') + 1) : s;
    }
}
