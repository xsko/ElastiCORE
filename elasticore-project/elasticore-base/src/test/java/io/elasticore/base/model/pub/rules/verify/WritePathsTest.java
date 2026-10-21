package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The write paths the pet store change request found hand code for, in Java
 * and Kotlin on the hexagonal bundle and in Java on the layered v3 one:
 *
 * - C2: PUT /batch goes through update(dto), so an override of update() in
 *   the extension service governs it; @restExclude(update) takes batchUpdate too;
 * - C8: @restExclude naming a service method takes it off the use case as well;
 * - C1: a @readonly @owner field keeps the owner - claimed on create, kept on update;
 * - C4: @updatable(false) on a reference reaches the identity field that is written.
 */
public class WritePathsTest {

    private static final String MODEL = String.join("\n",
            "entity:",
            "  WpItem:",
            "    meta: entity @root @editable @expose(20)",
            "    fields:",
            "      itemId: long @id @sequence",
            "      name:   string(30)",
            "",
            "  WpReview:",
            "    meta: entity @root @editable @expose(20) @owner(writerId) @access(read=public, create=user, update=owner, delete=owner)",
            "    fields:",
            "      reviewId: long @id @sequence",
            "      writerId: long @readonly",
            "      item:     WpItem @ManyToOne @updatable(false)",
            "      rating:   int",
            "",
            "  WpNote:",
            "    meta: entity @root @editable @expose(20) @restExclude(update)",
            "    fields:",
            "      noteId: long @id @sequence",
            "      text:   string(20)",
            "",
            "  WpTag:",
            "    meta: entity @root @editable @expose(20) @restExclude(batchUpdate, deleteById)",
            "    fields:",
            "      tagId: long @id @sequence",
            "      label: string(20)",
            "");

    private static File java;
    private static File kotlin;
    private static File layered;

    @BeforeAll
    static void generate() throws Exception {
        java = project("elcore-writes-java");
        run(java, "java", "hexagonal");
        kotlin = project("elcore-writes-kt");
        run(kotlin, "kotlin", "hexagonal");
        layered = project("elcore-writes-layered");
        run(layered, "java", "layered");
        // The TypeScript client, of the hexagonal server and of a 2.x one.
        runTs(java, "hexagonal");
        // -Pv3 given to the server's run only: the client learns it from what that run recorded.
        runTs(layered, null);
        compat = project("elcore-writes-compat");
        runTs(compat, null);
    }

    private static File compat;

    private static void runTs(File project, String architecture) {
        String[] keys = {"elasticore.base.path", "elasticore.architecture", "elasticore.language",
                "elasticore.rules.force", "elasticore.rules.ts", "elasticore.rules.v3"};
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            if (architecture != null) System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty("elasticore.rules.ts", "true");
            assertTrue(RulesGenRunner.run(new String[0]) == 0, "the TypeScript run failed: " + project);
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
    }

    /** The client follows its server: a clean one drops batchUpdate with update, a 2.x one keeps PUT /batch. */
    @Test
    public void theClientOffersWhatItsServerKeeps() throws Exception {
        String clean = found(java, "WpNoteApi.ts");
        assertFalse(clean.contains("batchUpdate"), clean);
        assertFalse(clean.contains("'PUT', BASE, input"), clean);
        String told = found(layered, "WpNoteApi.ts");
        assertFalse(told.contains("batchUpdate"), told);
        String old = found(compat, "WpNoteApi.ts");
        assertTrue(old.contains("batchUpdate"), old);
        assertFalse(old.contains("'PUT', BASE, input"), old);
    }

    private static File project(String name) throws Exception {
        File dir = Files.createTempDirectory(name).toFile();
        File blueprint = new File(dir, "src/main/resources/blueprint/wpshop");
        assertTrue(new File(blueprint, "model").mkdirs());
        Files.write(new File(blueprint, "env.yml").toPath(), String.join("\n",
                "elasticore:", "  enable: true", "  config:", "    domainName: wpshop", "    framework: springboot",
                "    mode: jpa", "    j2ee: jakarta", "  namespace:", "    base: io.acme.wpshop", "")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private static void run(File project, String language, String architecture) {
        String[] keys = {"elasticore.base.path", "elasticore.architecture", "elasticore.language",
                "elasticore.rules.force", "elasticore.rules.v3"};
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            if ("layered".equals(architecture)) System.setProperty("elasticore.rules.v3", "true");
            RulesGenRunner.main(new String[0]);
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
    }

    private static String found(File project, String name) throws Exception {
        try (java.util.stream.Stream<Path> all = Files.walk(project.toPath())) {
            for (Path p : all.toArray(Path[]::new)) {
                if (p.getFileName().toString().equals(name)) {
                    return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                }
            }
        }
        assertTrue(false, "not generated: " + name + " in " + project);
        return null;
    }

    private static String squashed(String text) {
        return text.replaceAll("\\s+", " ");
    }

    /** The method's text, from its signature to its closing brace (a member of a top-level class). */
    private static String method(String text, String signature) {
        String t = text.replace("\r\n", "\n");
        int at = t.indexOf(signature);
        assertTrue(at >= 0, signature + " not in\n" + text);
        int end = t.indexOf("\n    }\n", at);
        return t.substring(at, end < 0 ? t.length() : end + 6);
    }

    private static String foundOrEmpty(File project, String name) throws Exception {
        try (java.util.stream.Stream<Path> all = Files.walk(project.toPath())) {
            for (Path p : all.toArray(Path[]::new)) {
                if (p.getFileName().toString().equals(name)) {
                    return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                }
            }
        }
        return "";
    }

    // ---- C2 ----------------------------------------------------------------------

    @Test
    public void aBatchUpdateGoesThroughUpdate() throws Exception {
        for (File project : new File[]{java, layered}) {
            String batch = squashed(method(found(project, "WpItemCoreService.java"), "public void batchUpdate("));
            assertTrue(batch.contains("for (WpItemDTO dto : dtoList) { update(dto); }"), project + ": " + batch);
            assertFalse(batch.contains("mapping("), batch);
        }
        String kt = squashed(method(found(kotlin, "WpItemCoreService.kt"), "open fun batchUpdate("));
        assertTrue(kt.contains("for (dto in dtoList) { update(dto) }"), kt);
    }

    @Test
    public void excludingUpdateTakesTheBatchWithIt() throws Exception {
        for (File project : new File[]{java, layered}) {
            String service = found(project, "WpNoteCoreService.java");
            assertFalse(service.contains(" update(WpNoteDTO dto)"), service);
            assertFalse(service.contains("batchUpdate("), service);
            String control = found(project, "WpNoteController.java");
            assertFalse(control.contains("@PutMapping"), control);
        }
        String useCase = found(java, "WpNoteUseCase.java");
        assertFalse(useCase.contains("update("), useCase);
        // The Kotlin service keeps its methods (it never drops one for @restExclude); the port and the
        // endpoints follow the DSL, which compiles: the service offers more than the port promises.
        assertFalse(found(kotlin, "WpNoteUseCase.kt").contains("update("));
        assertFalse(found(kotlin, "WpNoteController.kt").contains("@PutMapping"));
        assertFalse(found(java, "WpNoteController.java").contains("/batch"));
    }

    // ---- C8 ----------------------------------------------------------------------

    @Test
    public void theUseCaseDropsWhatTheServiceDropped() throws Exception {
        String service = found(java, "WpTagCoreService.java");
        assertFalse(service.contains("batchUpdate(") || service.contains("deleteById("), service);
        // The list delete calls deleteById, so it goes too - and POST /delete with it.
        assertFalse(service.contains("public List<Boolean> delete("), service);
        String useCase = found(java, "WpTagUseCase.java");
        assertFalse(useCase.contains("batchUpdate(") || useCase.contains("deleteById(") || useCase.contains(" delete("), useCase);
        assertTrue(useCase.contains("update(") && useCase.contains("save("), useCase);
        String control = found(java, "WpTagController.java");
        assertFalse(control.contains("@DeleteMapping") || control.contains("\"/delete\"") || control.contains("\"/batch\""), control);
        String kt = found(kotlin, "WpTagUseCase.kt");
        assertFalse(kt.contains("batchUpdate(") || kt.contains("deleteById(") || kt.contains(" delete("), kt);
        String ktControl = found(kotlin, "WpTagController.kt");
        assertFalse(ktControl.contains("@DeleteMapping") || ktControl.contains("\"/delete\"") || ktControl.contains("\"/batch\""), ktControl);
    }

    // ---- C1 ----------------------------------------------------------------------

    @Test
    public void aReadonlyOwnerIsClaimedOnCreateAndKept() throws Exception {
        for (File project : new File[]{java, layered}) {
            String core = found(project, "WpReviewCoreService.java");
            String save = squashed(method(core, "public WpReviewDTO save("));
            assertTrue(save.contains("claimOwner(entity);"), project + ": " + save);
            assertTrue(save.indexOf("toEntity(") < save.indexOf("claimOwner(entity);")
                    && save.indexOf("claimOwner(entity);") < save.indexOf("beforeSave("), save);
            String claim = squashed(method(core, "private void claimOwner("));
            assertTrue(claim.contains("if (currentUser.system()) return; Object id = currentUser.id(); if (id == null) return;"), claim);
            assertTrue(claim.contains("setWriterId(io.elasticore.springboot3.security.AccessPolicy.as(id, Long.class));"), claim);
            // Not written from a request, on create or update: the stored owner stays.
            String reverse = method(found(project, "WpshopMapper.java"),
                    "public static void mapping(WpReviewDTO from, WpReview to, boolean isSkipNull, MappingContext c){");
            assertFalse(reverse.contains("setWriterId"), project + ": " + reverse);
        }
        String kt = squashed(method(found(kotlin, "WpReviewCoreService.kt"), "private fun claimOwner("));
        assertTrue(kt.contains("if (currentUser.system()) return val id = currentUser.id() ?: return"), kt);
        assertTrue(kt.contains("writerId = io.elasticore.springboot3.security.AccessPolicy.`as`(id, Long::class.javaObjectType)"), kt);
        // Nothing to claim where the owner is not @readonly.
        assertFalse(found(java, "WpItemCoreService.java").contains("claimOwner"));
    }

    // ---- C4 ----------------------------------------------------------------------

    @Test
    public void aReferenceThatMayNotChangeKeepsItsRow() throws Exception {
        String entity = foundOrEmpty(java, "WpReviewBase.java") + found(java, "WpReview.java");
        assertTrue(entity.contains("@Column(name = \"item_item_id\", updatable = false)"), entity);
        String mapper = found(java, "WpshopMapper.java");
        assertTrue(mapper.contains("if((c==null || c.fd(\"itemId\").checkEnable()) && to.getItemId() == null)"), mapper);
        String kt = foundOrEmpty(kotlin, "WpReviewBase.kt") + found(kotlin, "WpReview.kt");
        assertTrue(kt.contains("@Column(name = \"item_item_id\", updatable = false)"), kt);
        assertTrue(found(kotlin, "WpshopMapper.kt").contains("&& to.itemId == null)"));
    }

    @AfterAll
    static void clean() throws Exception {
        java.lang.reflect.Field tmp = io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
        for (File dir : new File[]{java, kotlin, layered, compat}) {
            if (dir == null || !dir.exists()) continue;
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, java.io.IOException e) throws java.io.IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
