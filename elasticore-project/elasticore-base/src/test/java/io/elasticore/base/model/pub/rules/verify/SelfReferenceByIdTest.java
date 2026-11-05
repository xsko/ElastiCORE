package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.pub.rules.schema.DdlConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Under the DDD rule sets another root is referenced by its id. A root that
 * refers to its own kind - a comment's parent - refers to another instance,
 * another aggregate, and was left an object: the check that keeps a member's
 * reference back to its root also caught it, as a root's own root is itself.
 * A DTO declared with @template, which leaves relations out, then had no
 * parent at all, while the reference to the post stayed as postId.
 */
public class SelfReferenceByIdTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  SfPost:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      postId: long @id @sequence",
            "      title: string(100)",
            "  SfComment:",
            "    meta: entity @root @expose(50)",
            "    fields:",
            "      commentId: long @id @sequence",
            "      post: SfPost!",
            "      parent: SfComment",
            "      content: string(500)",
            // An aggregate with a member that points back at its root.
            "  SfBasket:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      basketId: long @id @sequence",
            "      lines: List<SfBasketLine> @OneToMany(mappedBy=\"basket\")",
            "  SfBasketLine:",
            "    meta: entity",
            "    fields:",
            "      lineId: long @id @sequence",
            "      basket: SfBasket @ManyToOne",
            "      replaces: SfBasketLine @ManyToOne",
            "      quantity: int",
            // A root whose only reference by id is to its own kind.
            "  SfNode:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      nodeId: long @id @sequence",
            "      parent: SfNode",
            "  SfStep:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      stepId: long @id @sequence",
            "      previous: SfStep!",
            "  SfPerson:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      personId: long @id @sequence",
            "      manager: SfPerson",
            "      mentor: SfPerson",
            "  SfTopic:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      topicId: long @id @sequence",
            "      parent: SfTopic @objectref",
            "dto:",
            "  SfCommentDTO:",
            "    meta: dto @template(SfComment)",
            "  SfCommentKeyedDTO:",
            "    meta: dto @template(SfComment)",
            "    fields:",
            "      parentKey: long @ref(parent.commentId)",
            "  SfCommentSameNameDTO:",
            "    meta: dto @template(SfComment)",
            "    fields:",
            "      parentCommentId: long @ref(parent.commentId)",
            "  SfTopicDTO:",
            "    meta: dto @template(SfTopic)",
            "    fields:",
            "      parentKey: long @ref(parent.topicId)",
            "");

    /** referenceColumn: short renames an id reference's column; the root's reference to its own kind keeps its own. */
    private static final String SHORT_COLUMNS = String.join(
            "\n",
            "entity:",
            "  SfDepartment:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      departmentId: long @id @sequence",
            "  SfEmployee:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      empId: long @id @sequence @db(emp_no)",
            "      department: SfDepartment",
            "      manager: SfEmployee",
            "");

    private static final List<File> projects = new ArrayList<File>();

    private static File hexagonal;
    private static File ddd;
    private static File kotlin;
    private static File layered;
    private static File shortColumns;
    private static String hexagonalLog;

    @BeforeAll
    static void generate() throws Exception {
        hexagonal = run("elcore-selfref-hex", "hexagonal", "java");
        ddd = run("elcore-selfref-ddd", "ddd", "java");
        kotlin = run("elcore-selfref-kt", "hexagonal", "kotlin");
        layered = run("elcore-selfref-layered", "layered", "java");
        shortColumns = run("elcore-selfref-short", "hexagonal", "java", SHORT_COLUMNS, "    referenceColumn: short");
        hexagonalLog = read(new File(hexagonal, "elcore.log"));
    }

    @AfterAll
    static void cleanUp() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        if (System.getenv("KEEP_SELFREF") != null) return;
        for (File project : projects) delete(project);
    }

    @Test
    public void aRootsReferenceToItsOwnKindIsItsId() throws Exception {
        for (File project : new File[] {hexagonal, ddd}) {
            String entity = entityOf(project, "SfComment", ".java");
            assertTrue(entity.contains("private Long parentCommentId;"), entity);
            // The relation stays, read-only on the same column, as the post's does.
            assertTrue(entity.contains("@Column(name = \"parent_comment_id\")"), entity);
            assertTrue(entity.contains("@JoinColumn(name = \"parent_comment_id\""), entity);
            assertTrue(
                    entity.contains(
                            "insertable = false, updatable = false)\n\t@Setter(AccessLevel.NONE)\n\tprivate SfComment parent;"),
                    entity);
            assertTrue(entity.contains("private Long postId;"), entity);
        }
    }

    @Test
    public void theDeclaredDtoKeepsTheParentsId() throws Exception {
        for (File project : new File[] {hexagonal, ddd}) {
            String dto = found(project, "SfCommentDTO.java");
            assertTrue(dto.contains("private Long parentCommentId;"), dto);
            assertTrue(dto.contains("private Long postId;"), dto);
            String mapper = found(project, "ForumMapper.java");
            // Written on create as postId is, and searched by it.
            assertTrue(
                    mapper.contains(
                            "setVal(from.getParentCommentId(), to.creation()::setParentCommentId, isSkipNull);"),
                    mapper);
            assertTrue(mapper.contains("c.equal(r.get(\"parentCommentId\"),parentCommentId)"), mapper);
        }
    }

    @Test
    public void theTableDoesNotChange() throws Exception {
        for (File project : new File[] {hexagonal, ddd, kotlin}) {
            String ddl = read(new File(project, "src/main/resources/db/schema-postgresql.sql"));
            assertEquals(1, count(ddl, "parent_comment_id BIGINT,"), ddl);
            assertFalse(ddl.contains("parent_comment_comment_id"), ddl);
            assertTrue(ddl.contains("FOREIGN KEY (parent_comment_id) REFERENCES sf_comment (comment_id)"), ddl);
        }
    }

    @Test
    public void aMembersReferenceBackToItsRootStaysAnObject() throws Exception {
        for (File project : new File[] {hexagonal, ddd}) {
            String line = entityOf(project, "SfBasketLine", ".java");
            assertTrue(line.contains("private SfBasket basket;"), line);
            assertFalse(line.contains("basketId;"), line);
        }
    }

    @Test
    public void kotlinReferencesTheParentByItsId() throws Exception {
        String entity = entityOf(kotlin, "SfComment", ".kt");
        assertTrue(entity.contains("var parentCommentId: Long?"), entity);
        // Read-only on the same column, and written through the id.
        assertTrue(
                entity.contains("@JoinColumn(name = \"parent_comment_id\" , insertable = false, updatable = false)\n"
                        + "    final var parent: SfComment? = null"),
                entity);
        String mapper = found(kotlin, "ForumMapper.kt");
        assertTrue(
                mapper.contains("setVal(from.parentCommentId, { v -> to.creation().parentCommentId = v }, isSkipNull)"),
                mapper);
    }

    /**
     * A DTO that declares the reference under a name of its own loses it to the
     * id field, as it does for any other root: that renames a property of the
     * API, and a client still sending it is ignored, so it is said.
     */
    @Test
    public void aRefDeclaredUnderAnotherNameIsSaidAndDropped() throws Exception {
        String dto = found(hexagonal, "SfCommentKeyedDTO.java");
        assertFalse(dto.contains("parentKey"), dto);
        assertTrue(dto.contains("private Long parentCommentId;"), dto);
        assertTrue(
                hexagonalLog.contains(
                        "SfCommentKeyedDTO.parentKey @ref(parent.commentId) is dropped: the DTO carries this"
                                + " reference as parentCommentId, its id field, and a request that sends parentKey is ignored."),
                hexagonalLog);
    }

    /** The way around the #93 report: it has the id's own name, so the API stays and the id is what is written. */
    @Test
    public void aRefDeclaredUnderTheIdsNameStays() throws Exception {
        String dto = found(hexagonal, "SfCommentSameNameDTO.java");
        assertEquals(1, count(dto, "private Long parentCommentId;"), dto);
        assertFalse(hexagonalLog.contains("SfCommentSameNameDTO.parentCommentId @ref"), hexagonalLog);
        String mapper = found(hexagonal, "ForumMapper.java");
        int start = mapper.indexOf(
                "mapping(SfCommentSameNameDTO from, SfComment to, boolean isSkipNull, MappingContext c)");
        assertTrue(start >= 0, mapper);
        String written = mapper.substring(start, mapper.indexOf("public static", start + 1));
        assertTrue(written.contains("to.creation()::setParentCommentId"), written);
        assertFalse(written.contains("setParent("), written);
    }

    /** What keeps a declared name: the relation stays an object, and the @ref writes it as ever. */
    @Test
    public void objectrefKeepsTheDeclaredName() throws Exception {
        String dto = found(hexagonal, "SfTopicDTO.java");
        assertTrue(dto.contains("private Long parentKey;"), dto);
        String entity = entityOf(hexagonal, "SfTopic", ".java");
        assertTrue(entity.contains("private SfTopic parent;"), entity);
        assertFalse(entity.contains("parentTopicId"), entity);
        assertFalse(hexagonalLog.contains("SfTopicDTO.parentKey @ref"), hexagonalLog);
    }

    /** Before it, the relation's column was JPA's own, field and key column, which the setting does not touch. */
    @Test
    public void referenceColumnShortKeepsTheColumnTheRelationHad() throws Exception {
        String ddl = read(new File(shortColumns, "src/main/resources/db/schema-postgresql.sql"));
        assertTrue(ddl.contains("manager_emp_no BIGINT,"), ddl);
        assertTrue(ddl.contains("FOREIGN KEY (manager_emp_no) REFERENCES sf_employee (emp_no)"), ddl);
        assertFalse(ddl.contains("manager_emp_id"), ddl);
        // The setting still names the column of a reference to another root.
        assertTrue(ddl.contains("FOREIGN KEY (department_id) REFERENCES sf_department (department_id)"), ddl);
        String entity = entityOf(shortColumns, "SfEmployee", ".java");
        assertTrue(entity.contains("@Column(name = \"manager_emp_no\")"), entity);
        assertTrue(entity.contains("private Long managerEmpId;"), entity);
    }

    @Test
    public void aMembersReferenceToItsOwnKindStaysAnObject() throws Exception {
        for (File project : new File[] {hexagonal, ddd}) {
            String line = entityOf(project, "SfBasketLine", ".java");
            assertTrue(line.contains("private SfBasketLine replaces;"), line);
            assertFalse(line.contains("replacesLineId"), line);
        }
    }

    @Test
    public void aRequiredReferenceIsRequiredOnItsId() throws Exception {
        String entity = entityOf(hexagonal, "SfStep", ".java");
        assertTrue(entity.contains("@Column(name = \"previous_step_id\", nullable = false)"), entity);
        String dto = found(hexagonal, "SfStepDTO.java");
        assertTrue(dto.contains("@NotNull\n\tprivate Long previousStepId;"), dto);
        String ddl = read(new File(hexagonal, "src/main/resources/db/schema-postgresql.sql"));
        assertTrue(ddl.contains("previous_step_id BIGINT NOT NULL,"), ddl);
    }

    @Test
    public void twoReferencesHaveAColumnEach() throws Exception {
        String entity = entityOf(hexagonal, "SfPerson", ".java");
        assertTrue(entity.contains("private Long managerPersonId;"), entity);
        assertTrue(entity.contains("private Long mentorPersonId;"), entity);
        String ddl = read(new File(hexagonal, "src/main/resources/db/schema-postgresql.sql"));
        assertTrue(ddl.contains("FOREIGN KEY (manager_person_id) REFERENCES sf_person (person_id)"), ddl);
        assertTrue(ddl.contains("FOREIGN KEY (mentor_person_id) REFERENCES sf_person (person_id)"), ddl);
    }

    /**
     * A root whose only reference by id is to its own kind now behaves as one
     * that references another root: the row it names is checked before the save,
     * 404 rather than the foreign key's 409, and the save is read back.
     */
    @Test
    public void aRootWhoseOnlyReferenceIsToItsOwnKindChecksItAndReadsItBack() throws Exception {
        String entity = entityOf(hexagonal, "SfNode", ".java");
        assertTrue(entity.contains("public void requireReferences("), entity);
        assertTrue(entity.contains("\"reference.SfNode.parent\""), entity);
        String service = found(hexagonal, "SfNodeCoreService.java");
        assertTrue(service.contains("entity.requireReferences("), service);
        assertTrue(service.contains("private SfNode readBack(SfNode saved)"), service);
    }

    @Test
    public void theLayeredOutputKeepsTheObject() throws Exception {
        String entity = found(layered, "SfComment.java");
        assertTrue(entity.contains("private SfComment parent;"), entity);
        assertFalse(entity.contains("parentCommentId"), entity);
    }

    /** The entity's fields: its regenerated base when the root is split for behaviour, else the class. */
    private static String entityOf(File project, String name, String extension) throws IOException {
        List<File> hits = new ArrayList<File>();
        collect(project, name + "Base" + extension, hits);
        if (hits.isEmpty()) collect(project, name + extension, hits);
        assertTrue(!hits.isEmpty(), name + " in " + project);
        return read(hits.get(0));
    }

    private static File run(String name, String architecture, String language) throws Exception {
        return run(name, architecture, language, MODEL, "    j2ee: jakarta");
    }

    private static File run(String name, String architecture, String language, String model, String config)
            throws Exception {
        File project = Files.createTempDirectory(name).toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/forum");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: forum",
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                config,
                                "  namespace:",
                                "    base: io.acme.forum",
                                "    entity: io.acme.forum.entity",
                                "    dto: io.acme.forum.dto",
                                "    repository: io.acme.forum.repository",
                                "    service: io.acme.forum.service",
                                "    control: io.acme.forum.controller",
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
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        PrintStream out = System.out;
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty("elasticore.rules.v3", "true");
            System.setProperty(DdlConfig.DATABASES, "postgresql");
            System.setOut(new PrintStream(log, true, "UTF-8"));
            assertEquals(0, RulesGenRunner.run(new String[0]), "the run failed: " + project);
        } finally {
            System.setOut(out);
            Files.write(new File(project, "elcore.log").toPath(), log.toByteArray());
            out.print(new String(log.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
            io.elasticore.base.model.pub.rules.ModelCheck.reset();
        }
        return project;
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) n++;
        return n;
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
