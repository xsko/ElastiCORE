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

/** {@code @onDelete(cascade | restrict | setNull)}: the deleted row's CoreService carries it out (issue 180). */
public class OnDeleteTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  OdPost:",
            "    meta: entity @root @expose(20) @access(read=public)",
            "    fields:",
            "      postId: long @id @sequence",
            "      title: string(200)!",
            "  OdComment:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      commentId: long @id @sequence",
            "      text: string(200)",
            "      post: OdPost! @onDelete(cascade)",
            "      parent: OdComment @onDelete(cascade)",
            "  OdReport:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      reportId: long @id @sequence",
            "      post: OdPost @onDelete(restrict)",
            "      comment: OdComment @onDelete(restrict)",
            "      deletedAt: timestamp @deleted",
            "  OdBookmark:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      bookmarkId: long @id @sequence",
            "      post: OdPost @onDelete(setNull)",
            "      version: long @jpa:Version",
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
    public void theDeletedRootsServiceCarriesItOut() throws Exception {
        File project = run("elcore-ondelete-hex", "hexagonal", "java", "jpa", MODEL, false, 0, new String[1]);
        String post = found(project, "OdPostCoreService.java");
        // Before each delete: by its key, and each row a search deletes, as it deletes it.
        assertTrue(
                post.contains("repository.findById(postId).ifPresent(row -> onDeleteOdPost(entityManager, row));"),
                post);
        assertTrue(post.contains("repository.delete(onDeleteOdPost(entityManager, each));"), post);
        assertTrue(post.contains("public static OdPost onDeleteOdPost(EntityManager em, Object row) {"), post);
        // restrict first, then setNull, then cascade - on the id the reference holds; a soft-deleted report is out.
        int restrict = post.indexOf("\"select 1 from OdReport x where x.postId = :id and x.deletedAt is null\"");
        int setNull = post.indexOf("\"update versioned OdBookmark x set x.postId = null where x.postId = :id\"");
        int cascade = post.indexOf("\"select x from OdComment x where x.postId = :id\"");
        assertTrue(restrict > 0 && setNull > restrict && cascade > setNull, post);
        assertTrue(
                post.contains("throw new io.elasticore.springboot3.validation.ConflictException(\"restricted\","
                        + " \"OdPost {0} is still pointed to by OdReport rows (OdReport.post @onDelete(restrict))\","
                        + " String.valueOf(id));"),
                post);
        // A cascaded comment takes its replies, each row once, and is refused while a report points to it.
        assertTrue(
                post.contains("            if (!em.contains(each)) continue;\n            if (!seen.add(each))\n"
                        + "                throw new io.elasticore.springboot3.validation.ConflictException(\"cycle\","
                        + " \"OdComment rows point to each other through OdComment.parent - @onDelete(cascade) does not"
                        + " delete a cycle\");\n"
                        + "            onDeleteOdComment(em, each, seen);\n            em.remove(each);"),
                post);
        assertTrue(
                post.contains(
                        "private static void onDeleteOdComment(EntityManager em, Object row, java.util.Set<Object> seen) {"),
                post);
        assertTrue(post.contains("Object id = ((OdPost) row).getPostId();"), post);
        assertTrue(post.contains("Object id = ((OdComment) row).getCommentId();"), post);
        assertTrue(post.contains("\"select x from OdComment x where x.parentCommentId = :id\""), post);
        assertTrue(post.contains("\"select 1 from OdReport x where x.commentId = :id and x.deletedAt is null\""), post);
        // Nothing points to a report or a bookmark with @onDelete: their services are as before.
        assertFalse(found(project, "OdReportCoreService.java").contains("onDelete"));
        assertFalse(found(project, "OdBookmarkCoreService.java").contains("onDelete"));
        assertTrue(found(project, "OdReportCoreService.java").contains("repository.delete(each);"));
    }

    @Test
    public void kotlinTheSame() throws Exception {
        File project = run("elcore-ondelete-kt", "hexagonal", "kotlin", "jpa", MODEL, false, 0, new String[1]);
        String post = found(project, "OdPostCoreService.kt");
        assertTrue(
                post.contains("repository.findById(postId!!).ifPresent { onDeleteOdPost(entityManager, it) }"), post);
        assertTrue(post.contains("for (each in found) repository.delete(onDeleteOdPost(entityManager, each))"), post);
        // Members beside the @access companion object - a class has only one.
        assertTrue(post.split("companion object", -1).length <= 2, post);
        assertTrue(
                post.contains(
                        "protected fun onDeleteOdPost(em: jakarta.persistence.EntityManager, row: Any): OdPost {"),
                post);
        assertTrue(post.contains("val id: Any? = (row as OdPost).postId"), post);
        assertTrue(post.contains("throw io.elasticore.springboot3.validation.ConflictException(\"restricted\","), post);
        assertTrue(
                post.contains("if (each == null || !em.contains(each)) continue\n            if (!seen.add(each))"),
                post);
        assertTrue(post.contains("onDeleteOdComment(em, each, seen)"), post);
        // Its generated test hands the mocked entity manager a query that finds nothing.
        String test = found(project, "OdPostServiceTest.kt");
        assertTrue(
                test.contains("`when`(entityManager.createQuery(org.mockito.ArgumentMatchers.anyString()))"
                        + ".thenReturn(onDeleteQuery)"),
                test);
        assertFalse(found(project, "OdReportServiceTest.kt").contains("onDeleteQuery"));
    }

    @Test
    public void layeredComparesThroughTheRelation() throws Exception {
        String model = MODEL.replace(" @root", "");
        File project = run("elcore-ondelete-layered", "layered", "java", "jpa", model, false, 0, new String[1]);
        String post = found(project, "OdPostCoreService.java");
        assertTrue(post.contains("\"select x from OdComment x where x.post.postId = :id\""), post);
        assertTrue(
                post.contains("\"update versioned OdBookmark x set x.post = null where x.post.postId = :id\""), post);
    }

    @Test
    public void whatItCannotDoStopsTheBuild() throws Exception {
        String[] log = new String[1];
        run("elcore-ondelete-mybatis", "layered", "java", "mybatis", MODEL.replace(" @root", ""), false, 1, log);
        assertTrue(
                log[0].contains("@onDelete on references to OdPost is not carried out under mode: mybatis yet"),
                log[0]);
        run("elcore-ondelete-v2", "layered", "java", "jpa", MODEL.replace(" @root", ""), true, 1, log);
        assertTrue(log[0].contains("is not carried out in the 2.x-compatible output yet"), log[0]);
    }

    @Test
    public void aMisuseStopsTheBuild() throws Exception {
        String[] log = new String[1];
        String model = MODEL.replace("post: OdPost @onDelete(restrict)", "post: OdPost @onDelete(remove)")
                .replace("text: string(200)", "text: string(200) @onDelete(cascade)")
                .replace("post: OdPost! @onDelete(cascade)", "post: OdPost! @onDelete(setNull)");
        run("elcore-ondelete-misuse", "hexagonal", "java", "jpa", model, false, 1, log);
        assertTrue(log[0].contains("OdReport.post @onDelete(remove): say cascade, restrict or setNull"), log[0]);
        assertTrue(log[0].contains("OdComment.text @onDelete(cascade): it goes on a reference to one row"), log[0]);
        assertTrue(log[0].contains("OdComment.post @onDelete(setNull): the reference is required"), log[0]);
    }

    /** An aggregate's member is deleted by its root, and does not cascade on its own. */
    @Test
    public void aMemberOfAnAggregateIsRefused() throws Exception {
        String[] log = new String[1];
        String model = String.join(
                "\n",
                "entity:",
                "  OdOrder:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      orderId: long @id @sequence",
                "      lines: List<OdLine> @OneToMany(mappedBy=\"order\") @cascade(ALL)",
                "  OdLine:",
                "    meta: entity @aggregate(OdOrder)",
                "    fields:",
                "      lineId: long @id @sequence",
                "      order: OdOrder @ManyToOne",
                "      item: OdItem @onDelete(cascade)",
                "  OdItem:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      itemId: long @id @sequence",
                "  OdReview:",
                "    meta: entity @root @expose(20)",
                "    fields:",
                "      reviewId: long @id @sequence",
                "      line: OdLine @onDelete(restrict)",
                "");
        run("elcore-ondelete-member", "hexagonal", "java", "jpa", model, false, 1, log);
        assertTrue(log[0].contains("OdLine.item @onDelete(cascade): OdLine is a member of an aggregate"), log[0]);
        assertTrue(log[0].contains("OdReview.line @onDelete(restrict): OdLine is a member of an aggregate"), log[0]);
    }

    /** A soft-deleting comment cascaded from a post deleted outright keeps its row and its foreign key: said. */
    @Test
    public void aSoftDeletedChildOfAHardDeletedTargetIsSaid() throws Exception {
        String[] log = new String[1];
        String model = MODEL.replace(
                "      parent: OdComment @onDelete(cascade)",
                "      parent: OdComment @onDelete(cascade)\n      deletedAt: timestamp @deleted");
        run("elcore-ondelete-soft", "hexagonal", "java", "jpa", model, false, 0, log);
        assertTrue(log[0].contains("OdComment.post @onDelete(cascade): OdComment is deleted softly"), log[0]);
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
