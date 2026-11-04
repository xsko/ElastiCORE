package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.pub.rules.schema.DdlConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
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
            "  Post:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      postId: long @id @sequence",
            "      title: string(100)",
            "  Comment:",
            "    meta: entity @root @expose(50)",
            "    fields:",
            "      commentId: long @id @sequence",
            "      post: Post!",
            "      parent: Comment",
            "      content: string(500)",
            // An aggregate with a member that points back at its root.
            "  Basket:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      basketId: long @id @sequence",
            "      lines: List<BasketLine> @OneToMany(mappedBy=\"basket\")",
            "  BasketLine:",
            "    meta: entity",
            "    fields:",
            "      lineId: long @id @sequence",
            "      basket: Basket @ManyToOne",
            "      quantity: int",
            "dto:",
            "  CommentDTO:",
            "    meta: dto @template(Comment)",
            "");

    private static final List<File> projects = new ArrayList<File>();

    private static File hexagonal;
    private static File ddd;
    private static File kotlin;
    private static File layered;

    @BeforeAll
    static void generate() throws Exception {
        hexagonal = run("elcore-selfref-hex", "hexagonal", "java");
        ddd = run("elcore-selfref-ddd", "ddd", "java");
        kotlin = run("elcore-selfref-kt", "hexagonal", "kotlin");
        layered = run("elcore-selfref-layered", "layered", "java");
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
            String entity = entityOf(project, "Comment", ".java");
            assertTrue(entity.contains("private Long parentCommentId;"), entity);
            // The relation stays, read-only on the same column, as the post's does.
            assertTrue(entity.contains("@Column(name = \"parent_comment_id\")"), entity);
            assertTrue(entity.contains("@JoinColumn(name = \"parent_comment_id\""), entity);
            assertTrue(
                    entity.contains(
                            "insertable = false, updatable = false)\n\t@Setter(AccessLevel.NONE)\n\tprivate Comment parent;"),
                    entity);
            assertTrue(entity.contains("private Long postId;"), entity);
        }
    }

    @Test
    public void theDeclaredDtoKeepsTheParentsId() throws Exception {
        for (File project : new File[] {hexagonal, ddd}) {
            String dto = found(project, "CommentDTO.java");
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
            assertTrue(ddl.contains("FOREIGN KEY (parent_comment_id) REFERENCES comment (comment_id)"), ddl);
        }
    }

    @Test
    public void aMembersReferenceBackToItsRootStaysAnObject() throws Exception {
        for (File project : new File[] {hexagonal, ddd}) {
            String line = entityOf(project, "BasketLine", ".java");
            assertTrue(line.contains("private Basket basket;"), line);
            assertFalse(line.contains("basketId;"), line);
        }
    }

    @Test
    public void kotlinReferencesTheParentByItsId() throws Exception {
        String entity = entityOf(kotlin, "Comment", ".kt");
        assertTrue(entity.contains("var parentCommentId: Long?"), entity);
    }

    @Test
    public void theLayeredOutputKeepsTheObject() throws Exception {
        String entity = found(layered, "Comment.java");
        assertTrue(entity.contains("private Comment parent;"), entity);
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
                                "  namespace:",
                                "    base: io.acme.forum",
                                "    entity: io.acme.forum.entity",
                                "    dto: io.acme.forum.dto",
                                "    repository: io.acme.forum.repository",
                                "    service: io.acme.forum.service",
                                "    control: io.acme.forum.controller",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));

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
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty("elasticore.rules.v3", "true");
            System.setProperty(DdlConfig.DATABASES, "postgresql");
            assertEquals(0, RulesGenRunner.run(new String[0]), "the run failed: " + project);
        } finally {
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
