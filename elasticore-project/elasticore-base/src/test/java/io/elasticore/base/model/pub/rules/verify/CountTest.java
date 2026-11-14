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

/** {@code @count(Comment.post[, status == VISIBLE])}: the counted row's CoreService keeps it (issue 179). */
public class CountTest {

    private static final String MODEL = String.join(
            "\n",
            "enumeration:",
            "  CtStatus:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(3) @code",
            "    enum:",
            "      VISIBLE: VIS",
            "      HIDDEN: HID",
            "",
            "entity:",
            "  CtPost:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      postId: long @id @sequence",
            "      commentCount: long @count(CtComment.post, status == VISIBLE and pinned == false)",
            "      likeCount: int @count(CtLike.post)",
            "  CtComment:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      commentId: long @id @sequence",
            "      post: CtPost!",
            "      status: CtStatus",
            "      pinned: boolean",
            "      deletedAt: timestamp @deleted",
            "    states:",
            "      status:",
            "        initial: VISIBLE",
            "        hide: VISIBLE -> HIDDEN",
            "  CtLike:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      likeId: long @id @sequence",
            "      post: CtPost!",
            "");

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
    public void theCountedRowsServiceTouchesTheCounts() throws Exception {
        File project = run("elcore-count-hex", "hexagonal", "java", "jpa", MODEL, false, 0, new String[1]);
        String comment = found(project, "CtCommentCoreService.java");
        String jpql = "update CtPost x set x.commentCount = (select count(c) from CtComment c where c.postId = x.postId"
                + " and c.status = io.acme.forum.domain.model.CtStatus.VISIBLE and c.pinned = false and c.deletedAt is null)"
                + " where x.postId = :id";
        assertTrue(
                comment.contains(
                        "io.elasticore.springboot3.domain.Counts.touch(em, \"select x from CtPost x where x.postId = :id\", \""
                                + jpql + "\", row.getPostId());"),
                comment);
        // Each way a row is written or goes: save/update (as written), update (as it was), by condition, by key,
        // by search, and the command that moves its state.
        assertTrue(
                comment.contains("private static void countsOfCtComment(EntityManager em, CtComment row) {"), comment);
        assertTrue(count(comment, "countsOfCtCommentWritten(entityManager, entity);") >= 3, comment);
        assertTrue(count(comment, "countsOfCtComment(entityManager, entity);") >= 2, comment);
        assertTrue(
                comment.contains("for (CtComment row : query.findAll(spec)) countsOfCtComment(entityManager, row);"),
                comment);
        assertTrue(
                comment.contains(
                        "repository.findById(commentId).ifPresent(row -> countsOfCtComment(entityManager, row));"),
                comment);
        assertTrue(
                comment.contains("for (CtComment row : query.findAll(ForumMapper.toSpec(searchDTO)))"
                        + " countsOfCtComment(entityManager, row);"),
                comment);
        int hide = comment.indexOf("public CtCommentDTO hide(");
        assertTrue(hide > 0 && comment.indexOf("countsOfCtComment(entityManager, entity);", hide) > hide, comment);
        assertTrue(found(project, "CtLikeCoreService.java")
                .contains(
                        "\"update CtPost x set x.likeCount = (select count(c) from CtLike c where c.postId = x.postId)"
                                + " where x.postId = :id\""));
        // The count is the model's: not written by a request, nor by a save of its row; a new row starts at 0.
        String post = found(project, "CtPostBase.java");
        assertTrue(post.contains("@Column(name = \"comment_count\", updatable = false)"), post);
        assertTrue(post.contains("private Long commentCount = 0L;"), post);
        assertTrue(found(project, "CtPostDTO.java").contains("@JsonProperty(access=JsonProperty.Access.READ_ONLY)"));
        assertFalse(found(project, "CtPostCoreService.java").contains("Counts.touch"));
    }

    @Test
    public void kotlinTheSame() throws Exception {
        File project = run("elcore-count-kt", "hexagonal", "kotlin", "jpa", MODEL, false, 0, new String[1]);
        String comment = found(project, "CtCommentCoreService.kt");
        assertTrue(
                comment.contains(
                        "private fun countsOfCtComment(em: jakarta.persistence.EntityManager, row: CtComment?) {"),
                comment);
        assertTrue(
                comment.contains(
                        "Counts.touch(em, \"select x from CtPost x where x.postId = :id\", \"update CtPost x set x.commentCount"),
                comment);
        assertTrue(comment.contains(", row.postId)"), comment);
        assertTrue(comment.contains(", row) { r -> r.postId }"), comment);
        assertTrue(
                comment.contains("repository.findById(commentId!!).ifPresent { countsOfCtComment(entityManager, it) }"),
                comment);
        String test = found(project, "CtCommentServiceTest.kt");
        assertTrue(test.contains(".thenReturn(onDeleteQuery)"), test);
    }

    @Test
    public void layeredReadsTheKeyThroughTheRelation() throws Exception {
        String model = MODEL.replace(" @root", "");
        File project = run("elcore-count-layered", "layered", "java", "jpa", model, false, 0, new String[1]);
        String like = found(project, "CtLikeCoreService.java");
        assertTrue(like.contains("from CtLike c where c.post.postId = x.postId"), like);
        assertTrue(like.contains("(row.getPost() == null ? null : row.getPost().getPostId())"), like);
    }

    @Test
    public void whatItCannotDoStopsTheBuild() throws Exception {
        String[] log = new String[1];
        run("elcore-count-mybatis", "layered", "java", "mybatis", MODEL.replace(" @root", ""), false, 1, log);
        assertTrue(log[0].contains("is not kept under mode: mybatis yet"), log[0]);
        run("elcore-count-v2", "layered", "java", "jpa", MODEL.replace(" @root", ""), true, 1, log);
        assertTrue(log[0].contains("is not kept in the 2.x-compatible output yet"), log[0]);
    }

    @Test
    public void aMisuseStopsTheBuild() throws Exception {
        String[] log = new String[1];
        String model = MODEL.replace(
                        "@count(CtComment.post, status == VISIBLE and pinned == false)",
                        "@count(CtComment.post, status = VISIBLE)")
                .replace(
                        "likeCount: int @count(CtLike.post)",
                        "likeCount: int @count(CtLike.liker)\n"
                                + "      hiddenCount: long @count(CtComment.post, status == GONE)\n"
                                + "      title: string(20) @count(CtLike.post)");
        run("elcore-count-misuse", "hexagonal", "java", "jpa", model, false, 1, log);
        assertTrue(log[0].contains("CtPost.commentCount @count(): say what it counts"), log[0]);
        assertTrue(log[0].contains("CtLike.liker is no reference of an entity of the domain to CtPost"), log[0]);
        assertTrue(
                log[0].contains("CtPost.hiddenCount @count(CtComment.post, status == GONE): CtStatus has no GONE"),
                log[0]);
        assertTrue(log[0].contains("CtPost.title @count(CtLike.post): a count is a long or an int"), log[0]);
    }

    /** A literal of the wrong kind, and rows written by a cascade or a root, are refused at the build. */
    @Test
    public void whatCannotBeCountedIsRefused() throws Exception {
        String[] log = new String[1];
        String model = MODEL.replace("status == VISIBLE and pinned == false", "pinned == 1")
                .replace(
                        "      likeId: long @id @sequence",
                        "      likeId: long @id @sequence\n      tags: List<CtTag> @OneToMany @cascade(ALL)")
                .replace(
                        "  CtLike:",
                        "  CtTag:\n    meta: entity @expose(20)\n    fields:\n      tagId: long @id @sequence\n"
                                + "      post: CtPost\n  CtLike:")
                .replace(
                        "likeCount: int @count(CtLike.post)",
                        "likeCount: int @count(CtLike.post)\n" + "      tagCount: long @count(CtTag.post)");
        run("elcore-count-refused", "hexagonal", "java", "jpa", model, false, 1, log);
        assertTrue(log[0].contains("1 is not a value of pinned (Boolean): true or false"), log[0]);
        assertTrue(
                log[0].contains("CtPost.tagCount @count(CtTag.post): CtTag is written by a cascade of CtLike"), log[0]);
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) n++;
        return n;
    }

    private static File run(
            String name,
            String architecture,
            String language,
            String mode,
            String model,
            boolean v2,
            int exit,
            String[] log)
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
                                "    mode: " + mode,
                                "    j2ee: jakarta",
                                "    testCode: \"true\"",
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
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        PrintStream out = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty(v2 ? "elasticore.rules.v2" : "elasticore.rules.v3", "true");
            System.setProperty(DdlConfig.DATABASES, "postgresql");
            assertEquals(exit, RulesGenRunner.run(new String[0]), "the run's exit: " + project);
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
