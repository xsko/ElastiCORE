package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A search sorts only by what its response shows: each 3.0 search DTO lists the properties its sort may name, and
 * the generated SortableObject refuses any other (400, through the runtime's SortCheck) - in every mode (JPA,
 * MyBatis, MongoDB), both languages and every architecture. The list holds the response's values, a part of an
 * embedded value it shows, a @ref path, the values of a joined row whose DTO the response carries, the key and the
 * default order - and never a @password, a @writeonly value, a masked one, a joined row's value the response leaves
 * out, or a row nested only for readers who may not see it. The 2.x-compatible output lists nothing, as before.
 */
public class SortTermGenerationTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  StAddress:",
            "    meta: entity @embeddable",
            "    fields:",
            "      city: string(40)",
            "      gateCode: string(10) @writeonly",
            "",
            "  StMember:",
            "    meta: entity @root @editable @expose(20) @owner(memberId) @access(read=owner, create=public, update=owner)",
            "    fields:",
            "      memberId: long @id @sequence",
            "      login: string(20)! @login",
            "      secret: string(255)! @password",
            "      email: string(100)",
            "",
            "  StCategory:",
            "    meta: entity @root @editable @expose(20)",
            "    fields:",
            "      categoryId: long @id @sequence",
            "      name: string(30)",
            "      buyerNote: string(30) @writeonly",
            "",
            "  StOrder:",
            "    meta: entity @root @editable @expose(20) @sort(placedAt-)",
            "    fields:",
            "      orderId: long @id @sequence",
            "      placedAt: datetime",
            "      cardNo: string(30) @writeonly",
            "      phone: string(20) @env:maskphone",
            "      auditRef: string(20) @env:hideref",
            "      member: StMember @ManyToOne",
            "      category: StCategory @ManyToOne",
            "      address: StAddress @Embedded",
            "",
            "  StReview:",
            "    meta: entity @root @editable @expose(20)",
            "    fields:",
            "      reviewId: long @id @sequence",
            "      stars: int",
            "      category: StCategory @ManyToOne",
            "      member: StMember @ManyToOne",
            "",
            "dto:",
            "  StOrderDTO:",
            "    meta: dto @template(StOrder)",
            "    fields:",
            "      memberLogin: string @ref(member.login)",
            "      memberSecret: string @ref(member.secret)",
            "");

    /** mode, language, architecture -> the generated project. */
    private static final Map<String, File> RUNS = new LinkedHashMap<String, File>();

    private static File compatible;

    @BeforeAll
    static void generate() throws Exception {
        String[][] runs = {
            {"jpa", "java", "hexagonal"},
            {"jpa", "java", "layered"},
            {"jpa", "java", "ddd"},
            {"jpa", "kotlin", "hexagonal"},
            {"jpa", "kotlin", "layered"},
            {"mybatis", "java", "layered"},
            {"mybatis", "java", "ddd"},
            {"mongo", "java", "layered"},
            {"mongo", "kotlin", "layered"}
        };
        for (String[] r : runs) {
            File project = project("elcore-sort-" + r[0] + "-" + r[1] + "-" + r[2], r[0], MODEL);
            run(project, r[1], r[2], true);
            RUNS.put(String.join("/", r), project);
        }
        compatible = project("elcore-sort-v2", "jpa", MODEL);
        run(compatible, "java", "layered", false);
    }

    @Test
    public void everySearchDtoListsWhatItsResponseShows() throws Exception {
        for (Map.Entry<String, File> r : RUNS.entrySet()) {
            boolean kotlin = r.getKey().contains("/kotlin/");
            String srch = found(r.getValue(), "StOrderSrchDTO." + (kotlin ? "kt" : "java"));
            String where = r.getKey() + ":\n" + srch;
            assertTrue(
                    srch.contains(
                            kotlin
                                    ? "override fun sortableProperties(): Set<String> = setOf("
                                    : "public java.util.Set<String> sortableProperties() {"),
                    where);
            // Shown: the key, a value, a @ref and the default order.
            for (String shown : new String[] {"orderId", "placedAt", "member.login"}) {
                assertTrue(srch.contains("\"" + shown + "\""), shown + " - " + where);
            }
            // A part of the embedded value, where the response carries the value (value objects in the DTO).
            String dto = found(r.getValue(), "StOrderDTO." + (kotlin ? "kt" : "java"));
            boolean carried = dto.contains(kotlin ? "var address: StAddress" : "StAddress address;");
            assertTrue(
                    srch.contains("\"address.city\"") == carried, "address.city (carried: " + carried + ") - " + where);
            // Not shown: write-only, masked, a @ref to a password, a guarded row's values, a part left out.
            for (String hidden : new String[] {
                "cardNo", "phone", "auditRef", "member.secret", "member.email", "address.gateCode", "category.buyerNote"
            }) {
                assertFalse(srch.contains("\"" + hidden + "\""), hidden + " - " + where);
            }

            String sortable = found(r.getValue(), "SortableObject." + (kotlin ? "kt" : "java"));
            assertTrue(sortable.contains("SortCheck.sortable("), r.getKey() + ":\n" + sortable);
            assertTrue(
                    sortable.contains(
                            kotlin
                                    ? "fun sortableProperties(): Set<String>? = null"
                                    : "default Set<String> sortableProperties()"),
                    r.getKey() + ":\n" + sortable);

            // A member's own search: its password is never shown, so never sorted by.
            String member = found(r.getValue(), "StMemberSrchDTO." + (kotlin ? "kt" : "java"));
            assertTrue(member.contains("\"login\"") && member.contains("\"email\""), r.getKey() + ":\n" + member);
            assertFalse(member.contains("\"secret\""), r.getKey() + ":\n" + member);
        }
    }

    /**
     * An entity's own DTO nests a related row anyone may read whole (StCategoryDTO): the values that DTO shows sort.
     * A row only some may read is not nested (access-dto-nested), so none of its values sorts but its key.
     */
    @Test
    public void aNestedPublicRowSortsByWhatItsDtoShows() throws Exception {
        int nested = 0;
        for (Map.Entry<String, File> r : RUNS.entrySet()) {
            boolean kotlin = r.getKey().contains("/kotlin/");
            String dto = found(r.getValue(), "StReviewDTO." + (kotlin ? "kt" : "java"));
            String srch = found(r.getValue(), "StReviewSrchDTO." + (kotlin ? "kt" : "java"));
            String where = r.getKey() + ":\n" + dto + "\n" + srch;
            assertTrue(srch.contains("\"stars\""), where);
            assertFalse(srch.contains("\"category.buyerNote\""), where);
            assertFalse(
                    srch.contains("\"member.login\"")
                            || srch.contains("\"member.email\"")
                            || srch.contains("\"member.secret\""),
                    where);
            assertFalse(dto.contains("StMemberDTO"), where);
            boolean carried = dto.contains("StCategoryDTO");
            if (carried) nested++;
            assertTrue(srch.contains("\"category.name\"") == carried, where);
        }
        assertTrue(nested > 0, "no run nests StCategoryDTO in StReviewDTO");
    }

    /** The 2.x-compatible output sorts as 2.x did: its search DTO lists nothing, its SortableObject checks nothing. */
    @Test
    public void theCompatibleOutputIsAsBefore() throws Exception {
        String srch = found(compatible, "StOrderSrchDTO.java");
        assertFalse(srch.contains("sortableProperties"), srch);
        String sortable = found(compatible, "SortableObject.java");
        assertFalse(sortable.contains("SortCheck"), sortable);
    }

    // ---- helpers -------------------------------------------------------------------------

    private static File project(String name, String mode, String model) throws Exception {
        File dir = Files.createTempDirectory(name).toFile();
        File blueprint = new File(dir, "src/main/resources/blueprint/stshop");
        assertTrue(new File(blueprint, "model").mkdirs());
        Files.write(
                new File(blueprint, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: stshop",
                                "    framework: springboot",
                                "    mode: " + mode,
                                "    j2ee: jakarta",
                                "    annotations:",
                                "      maskphone: \"@dto:io.elasticore.springboot3.mask.Mask(io.elasticore.springboot3.mask.MaskType.PHONE)\"",
                                "      hideref: \"@dto:com.fasterxml.jackson.annotation.JsonIgnore\"",
                                "  namespace:",
                                "    base: io.acme.stshop",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private static void run(File project, String language, String architecture, boolean v3) throws Exception {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v3",
            "elasticore.rules.v2"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        PrintStream err = System.err;
        PrintStream capture = new PrintStream(bytes, true, "UTF-8");
        System.setOut(capture);
        System.setErr(capture);
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            if (v3 && "layered".equals(architecture)) System.setProperty("elasticore.rules.v3", "true");
            if (!v3) System.setProperty("elasticore.rules.v2", "true");
            RulesGenRunner.main(new String[0]);
        } finally {
            System.setOut(out);
            System.setErr(err);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
        out.print(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
    }

    private static Path find(File project, String name) throws Exception {
        try (java.util.stream.Stream<Path> all = Files.walk(project.toPath())) {
            return all.filter(p -> p.getFileName().toString().equals(name))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static String found(File project, String name) throws Exception {
        Path p = find(project, name);
        assertTrue(p != null, "not generated: " + name + " in " + project);
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }
}
