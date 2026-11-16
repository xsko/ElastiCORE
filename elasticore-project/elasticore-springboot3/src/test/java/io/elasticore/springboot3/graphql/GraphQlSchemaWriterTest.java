package io.elasticore.springboot3.graphql;

import io.elasticore.springboot3.graphql.fixture.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer;
import org.springframework.graphql.execution.DefaultExecutionGraphQlService;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphQlSchemaWriterTest {

    private static final List<Class<?>> TWO_DOMAINS =
            Arrays.asList(Fixtures.MemberGraphQlController.class, Fixtures.ItemGraphQlController.class);

    @Test
    void writesEachDomainsOperationsAndTheTypesOnlyItUses() {
        Map<String, String> files = GraphQlSchemaWriter.schemas(TWO_DOMAINS);

        assertThat(files).containsOnlyKeys("elasticore", "shop", "catalog");
        String shop = files.get("shop");
        assertThat(shop)
                .startsWith(GraphQlSchemaWriter.HEADER)
                .contains(
                        "extend type Query {\n  member(id: Long): Member\n  members(search: MemberSrchInput): MemberPage\n}")
                .contains(
                        "extend type Mutation {\n  createMember(input: MemberInput): Member\n  deleteMember(id: Long!): Boolean\n}")
                .contains("enum Grade {\n  GOLD\n  SILVER\n}")
                // Declared field order; the scalar each Java type is.
                .contains("type Member {\n  id: Long\n  name: String\n  grade: Grade\n  rate: BigDecimal\n"
                        + "  joined: Date\n  balance: Money\n  address: Address\n  tags: [String]\n}")
                // A write-only value is sent, never answered.
                .contains(
                        "input MemberInput {\n  id: Long\n  name: String\n  grade: Grade\n  rate: BigDecimal\n"
                                + "  joined: Date\n  balance: Money\n  password: String\n  address: AddressInput\n  tags: [String]\n}")
                .contains("type MemberPage {\n  content: [Member]\n  totalElements: Long\n  totalPages: Int\n"
                        + "  number: Int\n  size: Int\n}");
        assertThat(files.get("catalog"))
                .contains("extend type Query {\n  item(id: Long): Item\n}")
                .doesNotContain("type Address");

        // Address is answered by both domains, so it is defined once, in the shared file.
        String common = files.get("elasticore");
        assertThat(common)
                .contains("type Query\n")
                .contains("type Mutation\n")
                .contains("scalar Long")
                .contains("scalar Money")
                .contains("scalar Date")
                .contains("scalar BigDecimal")
                .contains("type Address {\n  city: String\n  zip: String\n}")
                .doesNotContain("scalar Instant");
        assertThat(shop).doesNotContain("type Address {");
    }

    @Test
    void refusesAnOperationTwoDomainsName() {
        assertThatThrownBy(() -> GraphQlSchemaWriter.schemas(
                        Arrays.asList(Fixtures.MemberGraphQlController.class, Fixtures.OtherMemberController.class)))
                .isInstanceOf(GraphQlSchemaWriter.Problem.class)
                .hasMessageContaining("Query member is named by")
                .hasMessageContaining("(shop)")
                .hasMessageContaining("(billing)");
    }

    @Test
    void saysEveryTypeItCannotWrite() {
        assertThatThrownBy(() -> GraphQlSchemaWriter.schemas(Arrays.asList(Fixtures.OddController.class)))
                .isInstanceOf(GraphQlSchemaWriter.Problem.class)
                .hasMessageContaining("OddController.shape: " + Fixtures.Shape.class.getName() + " cannot be written")
                .hasMessageContaining(Fixtures.Empty.class.getName() + " has no property a GraphQL type")
                .hasMessageContaining("OddController.grades(page): a page is an answer, not something sent")
                .hasMessageContaining("java.lang.Thread cannot be written");
    }

    @Test
    void runFindsTheControllersAmongTheCompiledClasses(@TempDir File resources) throws Exception {
        File classes = new File(io.elasticore.springboot3.graphql.fixture.run.ReportGraphQlController.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        ClassLoader loader = getClass().getClassLoader();

        assertThat(GraphQlSchemaWriter.run(new String[] {resources.getPath()}, loader))
                .isEqualTo(2);
        assertThat(GraphQlSchemaWriter.run(new String[] {resources.getPath(), classes.getPath()}, loader))
                .isZero();

        String report = new String(
                Files.readAllBytes(new File(resources, "graphql/report.graphqls").toPath()), StandardCharsets.UTF_8);
        // Optional is the value or null; an array is a list; a map is JSON.
        assertThat(report).contains("report(year: Int!): Line").contains("reportExtras(keys: [String]): JSON");
        assertThat(new String(
                        Files.readAllBytes(new File(resources, "graphql/elasticore.graphqls").toPath()),
                        StandardCharsets.UTF_8))
                .contains("scalar JSON")
                .doesNotContain("type Mutation");

        // A schema that does not build is the run's answer.
        Files.write(
                new File(resources, "graphql/mine.graphqls").toPath(),
                "type Query { mine: String }\n".getBytes(StandardCharsets.UTF_8));
        assertThat(GraphQlSchemaWriter.run(new String[] {resources.getPath(), classes.getPath()}, loader))
                .isEqualTo(1);
        // And one that does not parse.
        Files.write(new File(resources, "graphql/mine.graphqls").toPath(), "type {\n".getBytes(StandardCharsets.UTF_8));
        assertThat(GraphQlSchemaWriter.check(new File(resources, "graphql")))
                .anyMatch(p -> p.startsWith("mine.graphqls"));
        // A run that finds problems in the classes writes nothing.
        assertThat(GraphQlSchemaWriter.write(resources, Arrays.asList(Fixtures.OddController.class)))
                .isNotEmpty();
        // An empty folder has nothing to check.
        assertThat(GraphQlSchemaWriter.check(new File(resources, "none"))).isEmpty();
    }

    @Test
    void refusesTwoClassesTakingOneTypeName() {
        assertThatThrownBy(() -> GraphQlSchemaWriter.schemas(
                        Arrays.asList(Fixtures.MemberGraphQlController.class, Fixtures.Other.BillingController.class)))
                .isInstanceOf(GraphQlSchemaWriter.Problem.class)
                .hasMessageContaining("the GraphQL type Member would be both")
                .hasMessageContaining(Fixtures.MemberDTO.class.getName())
                .hasMessageContaining(Fixtures.Other.MemberDTO.class.getName());
    }

    @Test
    void writesTheFilesBuildsTheSchemaAndRemovesAFileNoDomainProducesAnyMore(@TempDir File resources) throws Exception {
        File dir = new File(resources, "graphql");
        dir.mkdirs();
        File stale = new File(dir, "billing.graphqls");
        Files.write(
                stale.toPath(),
                (GraphQlSchemaWriter.HEADER + "\nextend type Query { old: String }\n")
                        .getBytes(StandardCharsets.UTF_8));
        File own = new File(dir, "reports.graphqls");
        Files.write(own.toPath(), "extend type Query { report: String }\n".getBytes(StandardCharsets.UTF_8));

        assertThat(GraphQlSchemaWriter.write(resources, TWO_DOMAINS)).isEmpty();

        assertThat(new File(dir, "elasticore.graphqls")).isFile();
        assertThat(new File(dir, "shop.graphqls")).isFile();
        assertThat(new File(dir, "catalog.graphqls")).isFile();
        assertThat(stale).doesNotExist();
        assertThat(own).isFile();
    }

    @Test
    void aSchemaThatDoesNotBuildIsSaidWithTheFile(@TempDir File resources) throws Exception {
        File dir = new File(resources, "graphql");
        dir.mkdirs();
        // The developer's own file declares the root type a second time.
        Files.write(
                new File(dir, "reports.graphqls").toPath(),
                "type Query { report: String }\n".getBytes(StandardCharsets.UTF_8));

        List<String> problems = GraphQlSchemaWriter.write(resources, TWO_DOMAINS);

        assertThat(problems).isNotEmpty();
        assertThat(String.join("\n", problems)).contains("Query");
    }

    @Test
    void theWrittenSchemaServesTheControllers(@TempDir File resources) throws Exception {
        assertThat(GraphQlSchemaWriter.write(resources, TWO_DOMAINS)).isEmpty();
        ExecutionGraphQlServiceTester tester = tester(new File(resources, "graphql"));

        tester.document("mutation { createMember(input: {name: \"Kim\", grade: GOLD, rate: 0.125,"
                        + " joined: \"2026-11-16\", balance: 1500.50, password: \"secret\","
                        + " address: {city: \"Seoul\"}, tags: [\"vip\"]}) { id name balance } }")
                .execute()
                .path("createMember.id")
                .entity(Long.class)
                .isEqualTo(1L)
                .path("createMember.balance")
                .entity(Double.class)
                .isEqualTo(1500.50);

        tester.document("{ member(id: 1) { name grade rate joined balance address { city } tags } }")
                .execute()
                .path("member.name")
                .entity(String.class)
                .isEqualTo("Kim")
                .path("member.grade")
                .entity(String.class)
                .isEqualTo("GOLD")
                .path("member.rate")
                .entity(Double.class)
                .isEqualTo(0.125)
                .path("member.joined")
                .entity(String.class)
                .isEqualTo("2026-11-16")
                .path("member.address.city")
                .entity(String.class)
                .isEqualTo("Seoul")
                .path("member.tags[0]")
                .entity(String.class)
                .isEqualTo("vip");

        tester.document("{ members(search: {name: \"Kim\", pageSize: 5}) { totalElements size content { name } } }")
                .execute()
                .path("members.totalElements")
                .entity(Long.class)
                .isEqualTo(1L)
                .path("members.size")
                .entity(Integer.class)
                .isEqualTo(5)
                .path("members.content[0].name")
                .entity(String.class)
                .isEqualTo("Kim");

        tester.document("{ item(id: 7) { id title } }")
                .execute()
                .path("item.title")
                .entity(String.class)
                .isEqualTo("Lamp");
    }

    @Test
    void aRefusalIsAnErrorWithItsStatusAndCode(@TempDir File resources) throws Exception {
        assertThat(GraphQlSchemaWriter.write(resources, TWO_DOMAINS)).isEmpty();
        ExecutionGraphQlServiceTester tester = tester(new File(resources, "graphql"));

        tester.document("mutation { deleteMember(id: 99) }").execute().errors().satisfy(errors -> {
            assertThat(errors).hasSize(1);
            ResponseError e = errors.get(0);
            assertThat(e.getErrorType().toString()).isEqualTo("NOT_FOUND");
            assertThat(e.getMessage()).isEqualTo("Member not found");
            assertThat(e.getExtensions()).containsEntry("status", 404);
        });

        tester.document("mutation { createMember(input: {name: \"taken\"}) { id } }")
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).getErrorType().toString()).isEqualTo("CONFLICT");
                    assertThat(errors.get(0).getExtensions())
                            .containsEntry("status", 409)
                            .containsEntry("code", "member.name.taken");
                });
    }

    private static ExecutionGraphQlServiceTester tester(File dir) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                Fixtures.MemberGraphQlController.class, Fixtures.ItemGraphQlController.class);
        AnnotatedControllerConfigurer controllers = new AnnotatedControllerConfigurer();
        controllers.setApplicationContext(context);
        // What the configuration's post-processor does to the application's configurer.
        ElastiCoreGraphQlConfiguration.fallBackOnFields(controllers);
        controllers.afterPropertiesSet();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".graphqls"));
        Resource[] resources = new Resource[files.length];
        for (int i = 0; i < files.length; i++) resources[i] = new FileSystemResource(files[i]);
        ElastiCoreGraphQlConfiguration config = new ElastiCoreGraphQlConfiguration();
        GraphQlSource source = GraphQlSource.schemaResourceBuilder()
                .schemaResources(resources)
                .configureRuntimeWiring(config.elastiCoreGraphQlScalars())
                .configureRuntimeWiring(controllers)
                .exceptionResolvers(Arrays.asList(controllers.getExceptionResolver(), config.elastiCoreGraphQlErrors()))
                .build();
        return ExecutionGraphQlServiceTester.create(new DefaultExecutionGraphQlService(source));
    }
}
