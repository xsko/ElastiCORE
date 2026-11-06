package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.pub.rules.ModelCheck;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @deleted in an @abstract(entity) hierarchy, generated the way a project generates it (layered, Java and Kotlin):
 * JOINED with the field on the root, TABLE_PER_CLASS with it on an @abstract frame above the root, a collection of
 * the root's type, and the field on a subclass only, which the build refuses.
 */
public class SoftDeleteHierarchyTest {

    private static String env() {
        return String.join(
                "\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: hdshop",
                "    framework: springboot",
                "    mode: jpa",
                "    j2ee: jakarta",
                "  namespace:",
                "    base: io.acme.hdshop",
                "");
    }

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  HdParty:",
            "    meta: entity @abstract(entity) @expose(20) @jpa:Inheritance(strategy = InheritanceType.JOINED)",
            "    fields:",
            "      partyId: long @id @sequence",
            "      name:    string(30)! @search(like)",
            "      version: long @jpa:Version",
            "      leftAt:  timestamp @deleted",
            "",
            "  HdPerson:",
            "    meta: entity @extend(HdParty)",
            "    fields:",
            "      birthYear: int",
            "",
            "  HdKept:",
            "    meta: entity @abstract",
            "    fields:",
            "      retiredAt: timestamp @deleted",
            "",
            "  HdTag:",
            "    meta: entity @abstract(entity) @extend(HdKept) @expose(20)"
                    + " @jpa:Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)",
            "    fields:",
            "      tagId: long @id @sequence(SEQUENCE)",
            "      label: string(20)",
            "      board: HdBoard @ManyToOne",
            "",
            "  HdColorTag:",
            "    meta: entity @extend(HdTag)",
            "    fields:",
            "      rgb: string(6)",
            "",
            "  HdBoard:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      boardId: long @id @sequence",
            "      tags:    List<HdTag> @OneToMany(mappedBy=\"board\")",
            "");

    private static final String REFUSED = String.join(
            "\n",
            "entity:",
            "  HdCode:",
            "    meta: entity @abstract(entity) @expose(20) @jpa:Inheritance(strategy = InheritanceType.JOINED)",
            "    fields:",
            "      codeId: long @id @sequence",
            "",
            "  HdStatus:",
            "    meta: entity @extend(HdCode)",
            "    fields:",
            "      closedAt: timestamp @deleted",
            "",
            "  HdRegion:",
            "    meta: entity @extend(HdCode)",
            "    fields:",
            "      country: string(2)",
            "",
            // A collection of a JOINED subclass: its own table has no column to leave the deleted out by.
            "  HdOwner:",
            "    meta: entity @abstract(entity) @expose(20) @jpa:Inheritance(strategy = InheritanceType.JOINED)",
            "    fields:",
            "      ownerId: long @id @sequence",
            "      goneAt:  timestamp @deleted",
            "      club:    HdClub @ManyToOne",
            "",
            "  HdMember:",
            "    meta: entity @extend(HdOwner)",
            "    fields:",
            "      nick: string(10)",
            "",
            "  HdClub:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      clubId:  long @id @sequence",
            "      members: List<HdMember> @OneToMany(mappedBy=\"club\")",
            "      owners:  List<HdOwner> @OneToMany(mappedBy=\"club\")",
            "");

    private static File java;
    private static File kotlin;
    private static File refused;
    private static int refusedStatus;
    private static String refusedLog = "";

    @BeforeAll
    static void generate() throws Exception {
        java = write("elcore-hd", MODEL);
        kotlin = write("elcore-hd-kt", MODEL);
        refused = write("elcore-hd-refused", REFUSED);
        ModelCheck.reset();
        assertEquals(0, run(java, "java"), "the Java run");
        assertEquals(0, run(kotlin, "kotlin"), "the Kotlin run");
        ModelCheck.reset();
        java.io.PrintStream out = System.out;
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(bytes, true, "UTF-8"));
        try {
            refusedStatus = run(refused, "java");
        } finally {
            System.setOut(out);
        }
        refusedLog = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(refusedLog);
        ModelCheck.reset();
    }

    private static File write(String prefix, String model) throws Exception {
        File dir = Files.createTempDirectory(prefix).toFile();
        File blueprint = new File(dir, "src/main/resources/blueprint/hdshop");
        assertTrue(new File(blueprint, "model").mkdirs());
        Files.write(new File(blueprint, "env.yml").toPath(), env().getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private static int run(File dir, String language) {
        return run(dir, language, "layered");
    }

    private static int run(File dir, String language, String architecture) {
        String[] keys = {
            "elasticore.base.path", "elasticore.architecture", "elasticore.language", "elasticore.rules.force"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        try {
            System.setProperty("elasticore.base.path", dir.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            return RulesGenRunner.run(new String[0]);
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
    }

    @AfterAll
    static void clean() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        ModelCheck.reset();
        for (File dir : new File[] {java, kotlin, refused}) {
            if (dir == null || !dir.exists()) continue;
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, java.io.IOException e) throws java.io.IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /** The one generated file of that name, its lines unindented. */
    private static String file(File dir, String name) throws Exception {
        Path found;
        try (Stream<Path> walk = Files.walk(dir.toPath())) {
            found = walk.filter(p -> p.getFileName().toString().equals(name))
                    .findFirst()
                    .orElse(null);
        }
        assertTrue(found != null, "not generated: " + name);
        return new String(Files.readAllBytes(found), StandardCharsets.UTF_8)
                .replace("\r", "")
                .replaceAll("(?m)^[ \t]+", "");
    }

    @Test
    public void joinedWritesTheTimeInTheRootsTableAndKeepsTheSubclassRow() throws Exception {
        for (String[] lang : new String[][] {{"java", ".java"}, {"kotlin", ".kt"}}) {
            File dir = "java".equals(lang[0]) ? java : kotlin;
            String root = file(dir, "HdParty" + lang[1]);
            assertTrue(
                    root.contains("@SQLDelete(sql = \"UPDATE hd_party SET left_at = CURRENT_TIMESTAMP WHERE party_id"
                            + " = ? AND version = ? AND left_at IS NULL\")"),
                    root);
            String person = file(dir, "HdPerson" + lang[1]);
            assertTrue(
                    person.contains("@SQLDelete(sql = \"UPDATE hd_party SET left_at = left_at WHERE party_id = ?\")"),
                    person);
        }
    }

    @Test
    public void tablePerClassWritesTheTimeInEachSubclasssTable() throws Exception {
        for (String[] lang : new String[][] {{"java", ".java"}, {"kotlin", ".kt"}}) {
            File dir = "java".equals(lang[0]) ? java : kotlin;
            assertFalse(file(dir, "HdTag" + lang[1]).contains("SQLDelete"));
            String color = file(dir, "HdColorTag" + lang[1]);
            assertTrue(
                    color.contains("@SQLDelete(sql = \"UPDATE hd_color_tag SET retired_at = CURRENT_TIMESTAMP WHERE"
                            + " tag_id = ? AND retired_at IS NULL\")"),
                    color);
        }
    }

    @Test
    public void theRootsReadsLeaveTheDeletedOut() throws Exception {
        String parties = file(java, "HdPartyRepository.java");
        assertTrue(
                parties.contains("@Query(\"SELECT e FROM HdParty e WHERE e.partyId = :id AND e.leftAt IS NULL\")"),
                parties);
        assertTrue(parties.contains("@Query(\"SELECT COUNT(e) FROM HdParty e WHERE e.leftAt IS NULL\")"), parties);
        String tags = file(java, "HdTagRepository.java");
        assertTrue(
                tags.contains("@Query(\"SELECT e FROM HdTag e WHERE e.tagId = :id AND e.retiredAt IS NULL\")"), tags);
        String kt = file(kotlin, "HdPartyRepository.kt");
        assertTrue(kt.contains("override fun findById(@Param(\"id\") id: Long): java.util.Optional<HdParty>"), kt);
        // The search: the mapper's toSpec ends with the condition.
        String mapper = file(java, "HdshopMapper.java");
        assertTrue(mapper.contains("c.isNull(r.get(\"leftAt\"))"), mapper);
        assertTrue(mapper.contains("c.isNull(r.get(\"retiredAt\"))"), mapper);
        // A subclass, whose key is inherited, has no repository of its own.
        try (Stream<Path> walk = Files.walk(java.toPath())) {
            assertFalse(walk.anyMatch(p -> p.getFileName().toString().equals("HdPersonRepository.java")));
        }
    }

    @Test
    public void aCollectionOfTheRootsTypeHoldsTheLiveOnes() throws Exception {
        String board = file(java, "HdBoard.java");
        assertTrue(
                board.matches(
                        "(?s).*@SQLRestriction\\(\"retired_at IS NULL\"\\)\\s+(@\\S+\\s+)*private List<HdTag> tags.*"),
                board);
    }

    @Test
    public void aFieldOnASubclassOnlyStopsTheBuild() {
        assertNotEquals(0, refusedStatus, refusedLog);
        assertTrue(
                refusedLog.contains(
                        "[elcore3] HdStatus @deleted on a subclass of the @abstract(entity) HdCode is not"
                                + " supported - a read through HdCode would find kept and removed rows alike; declare it on HdCode."),
                refusedLog);
    }

    /**
     * Hexagonal splits the subclass into a generated base and a class that is the developer's. @deleted
     * added later cannot be written into that class, and without its statement a delete removes the row:
     * the build stops rather than leave it to a note.
     */
    @Test
    public void anOwnedSubclassWithoutTheStatementStopsTheBuild() throws Exception {
        String before = String.join(
                "\n",
                "entity:",
                "  HdAccount:",
                "    meta: entity @abstract(entity) @expose(20) @jpa:Inheritance(strategy = InheritanceType.JOINED)",
                "    fields:",
                "      accountId: long @id @sequence",
                "      holder:    string(30)",
                "",
                "  HdSaving:",
                "    meta: entity @extend(HdAccount)",
                "    fields:",
                "      rate: int",
                "");
        File dir = write("elcore-hd-owned", before);
        try {
            ModelCheck.reset();
            assertEquals(0, run(dir, "java", "hexagonal"), "the first run");
            assertFalse(file(dir, "HdSaving.java").contains("SQLDelete"));
            // The developer writes the subclass's behaviour: the file is theirs from now on.
            Path owned;
            try (Stream<Path> walk = Files.walk(dir.toPath())) {
                owned = walk.filter(p -> p.toString().replace('\\', '/').endsWith("domain/model/HdSaving.java"))
                        .findFirst()
                        .orElseThrow(IllegalStateException::new);
            }
            String code = new String(Files.readAllBytes(owned), StandardCharsets.UTF_8);
            int close = code.lastIndexOf('}');
            Files.write(
                    owned,
                    (code.substring(0, close) + "    public boolean high() { return true; }\n}\n")
                            .getBytes(StandardCharsets.UTF_8));
            Files.write(
                    new File(dir, "src/main/resources/blueprint/hdshop/model/model.yml").toPath(),
                    before.replace(
                                    "      holder:    string(30)",
                                    "      holder:    string(30)\n      closedAt:  timestamp @deleted")
                            .getBytes(StandardCharsets.UTF_8));
            String statement = "@SQLDelete(sql = \"UPDATE hd_account SET closed_at = closed_at WHERE account_id = ?\")";
            // Edited, with the generator's base in the cache: the line is merged in beside the developer's code.
            Path kept = owned.resolveSibling("HdSaving.java.kept");
            Files.copy(owned, kept);
            ModelCheck.reset();
            assertEquals(0, run(dir, "java", "hexagonal"), "the merging run");
            String merged = new String(Files.readAllBytes(owned), StandardCharsets.UTF_8);
            assertTrue(merged.contains(statement) && merged.contains("high()"), merged);
            // The same file with no base to merge with (written before merging, or the cache gone): left as it is,
            // and a delete would remove the row - so the build stops.
            Files.move(kept, owned, StandardCopyOption.REPLACE_EXISTING);
            File cache = new File(dir, ".elasticore/cache");
            assertTrue(cache.isFile(), "the merge base");
            Files.delete(cache.toPath());
            ModelCheck.reset();
            java.io.PrintStream out = System.out;
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            System.setOut(new java.io.PrintStream(bytes, true, "UTF-8"));
            int status;
            try {
                status = run(dir, "java", "hexagonal");
            } finally {
                System.setOut(out);
            }
            String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            assertNotEquals(0, status, log);
            assertTrue(
                    log.contains("HdSaving.java: the class has no " + statement
                            + ", which @deleted needs - a delete would remove the row."),
                    log);
        } finally {
            ModelCheck.reset();
            try (Stream<Path> walk = Files.walk(dir.toPath())) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    public void aCollectionOfAJoinedSubclassStopsTheBuildAndOneOfTheRootDoesNot() {
        assertTrue(
                refusedLog.contains("[elcore3] HdClub.members: a collection of HdMember, a subclass of the JOINED"
                        + " @abstract(entity) HdOwner, cannot leave its deleted rows out - the @deleted column is in HdOwner's"
                        + " table. Hold List<HdOwner> instead."),
                refusedLog);
        assertFalse(refusedLog.contains("HdClub.owners:"), refusedLog);
    }
}
