package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * graphql.enable in a domain's env.yml: one GraphQL controller per entity the
 * REST API serves, calling the same service; nothing at all without it.
 */
public class GraphQlGenerationTest {

    private static final String MODEL = "entity:\n"
            + "  Member:\n"
            + "    meta: entity @root @expose(20)\n"
            + "    fields:\n"
            + "      memberId: long @id @sequence\n"
            + "      nickname: string(30)!\n"
            + "  Ticket:\n"
            + "    meta: entity @root @expose(20)\n"
            + "    fields:\n"
            + "      ticketId: long @id @sequence\n"
            + "      title: string(100)\n"
            + "      note: text @editable\n"
            + "    commands:\n"
            + "      close:\n"
            + "        reason: string(50)!\n"
            + "      reopen:\n"
            + "  Label:\n"
            + "    meta: entity @root @expose(20) @readonly\n"
            + "    fields:\n"
            + "      labelId: long @id @sequence\n"
            + "      name: string(20)\n"
            + "  Reply:\n"
            + "    meta: entity\n"
            + "    fields:\n"
            + "      replyId: long @id @sequence\n"
            + "      body: string(100)\n";

    @TempDir
    Path dir;

    private static final String[] KEYS = {"elasticore.base.path", "elasticore.rules.force", "elasticore.rules.kt"};
    private final String[] saved = {
        System.getProperty(KEYS[0]), System.getProperty(KEYS[1]), System.getProperty(KEYS[2])
    };

    @AfterEach
    void restore() {
        for (int i = 0; i < KEYS.length; i++) {
            if (saved[i] == null) System.clearProperty(KEYS[i]);
            else System.setProperty(KEYS[i], saved[i]);
        }
    }

    @Test
    public void layeredJavaControllerCallsTheService() throws Exception {
        String log = generate("forum", null, "    graphql:\n      enable: true\n", 0);

        String member = read(find("MemberGraphQlController.java"));
        assertTrue(member.contains("@Controller"), member);
        assertTrue(member.contains("@GraphQlDomain(\"forum\")"), member);
        assertTrue(member.contains("@Import(ElastiCoreGraphQlConfiguration.class)"), member);
        assertTrue(member.contains("private final MemberService service;"), member);
        assertTrue(
                member.contains(
                        "@QueryMapping(\"member\")\n    public MemberDTO member(@Argument(\"memberId\") @NotNull Long memberId) {\n"
                                + "        return service.findById(memberId).orElse(null);"),
                member);
        assertTrue(member.contains("@QueryMapping(\"members\")"), member);
        assertTrue(member.contains("return service.findBySearch(search);"), member);
        assertTrue(
                member.contains(
                        "public MemberDTO createMember(@Argument(\"input\") @Valid @NotNull MemberDTO input) {\n"
                                + "        return service.save(input);"),
                member);
        assertTrue(member.contains("return service.update(input);"), member);
        assertTrue(
                member.contains("public boolean deleteMember(@Argument(\"memberId\") @NotNull Long memberId) {"),
                member);
        assertTrue(member.contains("import io.elasticore.springboot3.graphql.GraphQlDomain;"), member);

        // @readonly: the queries only.
        String label = read(find("LabelGraphQlController.java"));
        assertTrue(label.contains("@QueryMapping(\"labels\")"), label);
        assertFalse(label.contains("@MutationMapping"), label);
        assertFalse(label.contains("MutationMapping;"), label);

        // Not served by the REST API, so not by GraphQL either.
        assertFalse(exists("ReplyGraphQlController.java"), log);

        assertFalse(Files.exists(dir.resolve("src/main/resources/graphql/elasticore-graphql.properties")));
    }

    @Test
    public void hexagonalInjectsTheUseCaseAndMakesEachCommandAMutation() throws Exception {
        String log = generate(
                "forum", "hexagonal-springboot", "    graphql:\n      enable: true\n      path: /api/graphql\n", 0);

        String ticket = read(find("TicketGraphQlController.java"));
        assertTrue(ticket.contains("private final TicketUseCase service;"), ticket);
        assertTrue(ticket.contains("public Page<TicketDTO> tickets("), ticket);
        assertTrue(ticket.contains("@MutationMapping(\"closeTicket\")"), ticket);
        assertTrue(ticket.contains("return service.close(ticketId, command);"), ticket);
        assertTrue(ticket.contains("@MutationMapping(\"reopenTicket\")"), ticket);
        assertTrue(ticket.contains("return service.reopen(ticketId);"), ticket);
        // A member of no aggregate root still gets nothing.
        assertFalse(exists("ReplyGraphQlController.java"), log);

        assertTrue(read(dir.resolve("src/main/resources/graphql/elasticore-graphql.properties"))
                .contains("spring.graphql.path=/api/graphql"));
    }

    @Test
    public void queriesOnlyWhenMutationsAreOff() throws Exception {
        generate("forum", null, "    graphql:\n      enable: true\n      mutations: false\n", 0);

        String member = read(find("MemberGraphQlController.java"));
        assertTrue(member.contains("@QueryMapping(\"member\")"), member);
        assertFalse(member.contains("@MutationMapping"), member);
    }

    @Test
    public void kotlinController() throws Exception {
        System.setProperty("elasticore.rules.kt", "true");
        generate("forum", null, "    graphql:\n      enable: true\n", 0);

        String member = read(find("MemberGraphQlController.kt"));
        assertTrue(member.contains("@Import(ElastiCoreGraphQlConfiguration::class)"), member);
        assertTrue(
                member.contains("class MemberGraphQlController(\n    private val service: MemberService\n)"), member);
        // A runtime-visible @NotNull: Kotlin's own nullability is not readable off the class.
        assertTrue(
                member.contains(
                        "fun member(@Argument(\"memberId\") @NotNull memberId: Long): MemberDTO? = service.findById(memberId).orElse(null)"),
                member);
        assertTrue(
                member.contains(
                        "fun createMember(@Argument(\"input\") @Valid @NotNull input: MemberDTO): MemberDTO? = service.save(input)"),
                member);
        assertTrue(member.contains("import jakarta.validation.constraints.NotNull"), member);
    }

    @Test
    public void kotlinHexagonalCommandsAreMutations() throws Exception {
        System.setProperty("elasticore.rules.kt", "true");
        generate("forum", "hexagonal-springboot-kt", "    graphql:\n      enable: true\n", 0);

        String ticket = read(find("TicketGraphQlController.kt"));
        assertTrue(ticket.contains("private val service: TicketUseCase"), ticket);
        assertTrue(ticket.contains("@MutationMapping(\"closeTicket\")"), ticket);
        assertTrue(
                ticket.contains("fun closeTicket(@Argument(\"ticketId\") @NotNull ticketId: Long,"
                        + " @Argument(\"command\") @Valid @NotNull command: "),
                ticket);
        assertTrue(ticket.contains("= service.close(ticketId, command)"), ticket);
        assertTrue(
                ticket.contains(
                        "fun reopenTicket(@Argument(\"ticketId\") @NotNull ticketId: Long): TicketDTO? = service.reopen(ticketId)"),
                ticket);
    }

    @Test
    public void exposedMethodsAndCompositeKeys() throws Exception {
        String model = "entity:\n"
                + "  Note:\n"
                + "    meta: entity @expose(20, methods=\"read,search\")\n"
                + "    fields:\n"
                + "      noteId: long @id @sequence\n"
                + "      body: string(100)\n"
                + "  Box:\n"
                + "    meta: entity @expose(20) @restExclude(deleteById)\n"
                + "    fields:\n"
                + "      boxId: long @id @sequence\n"
                + "      label: string(20)\n"
                + "  Category:\n"
                + "    meta: entity @expose(20)\n"
                + "    fields:\n"
                + "      shelf: string(5) @id\n"
                + "      slot: string(5) @id\n"
                + "      name: string(20)\n";
        writeDomain("forum", null, "    graphql:\n      enable: true\n", model);
        run(0);

        String note = read(find("NoteGraphQlController.java"));
        assertTrue(note.contains("@QueryMapping(\"notes\")"), note);
        assertFalse(note.contains("@MutationMapping"), note);

        String box = read(find("BoxGraphQlController.java"));
        assertTrue(box.contains("@QueryMapping(\"boxes\")"), box);
        assertTrue(box.contains("@MutationMapping(\"createBox\")"), box);
        assertFalse(box.contains("deleteBox"), box);

        String category = read(find("CategoryGraphQlController.java"));
        assertTrue(category.contains("@QueryMapping(\"categories\")"), category);
        assertTrue(
                category.contains(
                        "category(@Argument(\"shelf\") @NotNull String shelf, @Argument(\"slot\") @NotNull String slot)"),
                category);
        assertTrue(category.contains("service.findById(shelf, slot)"), category);
    }

    @Test
    public void thePathFileGoesWhenNoDomainNamesAPath() throws Exception {
        generate("forum", null, "    graphql:\n      enable: yes\n      path: /gql\n", 0);
        Path props = dir.resolve("src/main/resources/graphql/elasticore-graphql.properties");
        assertTrue(Files.exists(props));
        // Unchanged: left as it is.
        generate("forum", null, "    graphql:\n      enable: on\n      path: /gql\n", 0);
        assertTrue(read(props).contains("spring.graphql.path=/gql"));

        generate("forum", null, "    graphql:\n      enable: true\n", 0);
        assertFalse(Files.exists(props));
    }

    @Test
    public void namingAndSwitches() {
        assertEquals("members", io.elasticore.base.model.pub.rules.graphql.GraphQlRules.plural("member"));
        assertEquals("categories", io.elasticore.base.model.pub.rules.graphql.GraphQlRules.plural("category"));
        assertEquals("days", io.elasticore.base.model.pub.rules.graphql.GraphQlRules.plural("day"));
        assertEquals("boxes", io.elasticore.base.model.pub.rules.graphql.GraphQlRules.plural("box"));
        assertEquals("buses", io.elasticore.base.model.pub.rules.graphql.GraphQlRules.plural("bus"));
        assertEquals("batches", io.elasticore.base.model.pub.rules.graphql.GraphQlRules.plural("batch"));
        assertEquals("wishes", io.elasticore.base.model.pub.rules.graphql.GraphQlRules.plural("wish"));
        assertEquals("quizes", io.elasticore.base.model.pub.rules.graphql.GraphQlRules.plural("quiz"));
        assertEquals("ys", io.elasticore.base.model.pub.rules.graphql.GraphQlRules.plural("y"));
        assertTrue(io.elasticore.base.model.pub.rules.graphql.GraphQlRules.enabled(" On "));
        assertFalse(io.elasticore.base.model.pub.rules.graphql.GraphQlRules.enabled(null));
        assertFalse(io.elasticore.base.model.pub.rules.graphql.GraphQlRules.enabled("false"));
    }

    @Test
    public void nothingWithoutTheSwitch() throws Exception {
        String log = generate("forum", null, "", 0);

        assertFalse(exists("MemberGraphQlController.java"), log);
        assertFalse(Files.exists(dir.resolve("src/main/resources/graphql")), log);
    }

    @Test
    public void twoDomainsMustNameOnePathAndServeDifferentEntities() throws Exception {
        writeDomain("billing", null, "    graphql:\n      enable: true\n      path: /billing/graphql\n");
        String log = generate("forum", null, "    graphql:\n      enable: true\n      path: /graphql\n", 1);

        assertTrue(log.contains("names path"), log);
        assertTrue(log.contains("both serve an entity Member"), log);
    }

    private String generate(String domainName, String rules, String extraConfig, int expected) throws Exception {
        writeDomain(domainName, rules, extraConfig);
        return run(expected);
    }

    private String run(int expected) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        PrintStream err = System.err;
        PrintStream both = new PrintStream(bytes, true, "UTF-8");
        System.setOut(both);
        System.setErr(both);
        int code;
        try {
            System.setProperty("elasticore.base.path", dir.toString());
            System.setProperty("elasticore.rules.force", "true");
            code = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
        String genLog = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        assertEquals(expected, code, genLog);
        return genLog;
    }

    private void writeDomain(String domainName, String rules, String extraConfig) throws Exception {
        writeDomain(domainName, rules, extraConfig, MODEL);
    }

    private void writeDomain(String domainName, String rules, String extraConfig, String model) throws Exception {
        Path domain = dir.resolve("src/main/resources/blueprint/" + domainName);
        Files.createDirectories(domain.resolve("model"));
        String env = "elasticore:\n"
                + "  enable: true\n"
                + "  config:\n"
                + "    domainName: " + domainName + "\n"
                + "    framework: springboot\n"
                + "    mode: jpa\n"
                + "    j2ee: jakarta\n"
                + extraConfig
                + (rules == null ? "" : "    publisher:\n      rules: [" + rules + "]\n")
                + "  namespace:\n"
                + "    base: com.acme." + domainName + "\n";
        Files.write(domain.resolve("env.yml"), env.getBytes(StandardCharsets.UTF_8));
        Files.write(domain.resolve("model/entity.yml"), model.getBytes(StandardCharsets.UTF_8));
    }

    private List<Path> all() throws Exception {
        List<Path> files = new ArrayList<Path>();
        for (String root : new String[] {"src/main/java", "src/main/kotlin"}) {
            Path p = dir.resolve(root);
            if (!Files.exists(p)) continue;
            try (Stream<Path> s = Files.walk(p)) {
                s.forEach(files::add);
            }
        }
        return files;
    }

    private boolean exists(String name) throws Exception {
        for (Path f : all()) if (f.getFileName().toString().equals(name)) return true;
        return false;
    }

    private Path find(String name) throws Exception {
        for (Path f : all()) if (f.getFileName().toString().equals(name)) return f;
        throw new AssertionError(name + " not generated");
    }

    private static String read(Path p) throws Exception {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
