package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @expose(20, methods="read,search"): the entity's controller serves what is
 * listed and nothing else, in Java and Kotlin on the hexagonal bundle and in
 * Java on the layered v3 one, and its TypeScript client calls the same. The
 * service keeps every method; the use case promises only the writes served.
 * A 2.x-compatible server ignores it, so its client calls everything.
 */
public class ExposeMethodsTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  EmCode:",
            "    meta: entity @root @editable @expose(20, methods=\"read,search\")",
            "    fields:",
            "      codeId: long @id @sequence",
            "      name:   string(30)",
            "",
            "  EmNote:",
            "    meta: entity @root @editable @expose(methods=\"read, search, create\")",
            "    fields:",
            "      noteId: long @id @sequence",
            "      text:   string(20)",
            "",
            "  EmTag:",
            "    meta: entity @root @editable @expose(20, methods=\"update,delete\")",
            "    fields:",
            "      tagId: long @id @sequence",
            "      label: string(20)",
            "",
            "  EmTypo:",
            "    meta: entity @root @editable @expose(20, methods=\"raed,search\")",
            "    fields:",
            "      typoId: long @id @sequence",
            "      label:  string(20)",
            "",
            "  EmNone:",
            "    meta: entity @root @editable @expose(20, methods=\"\")",
            "    fields:",
            "      noneId: long @id @sequence",
            "      label:  string(20)",
            "",
            "  EmOld:",
            "    meta: entity @root @editable @expose(20) @restExclude(deleteById)",
            "    fields:",
            "      oldId: long @id @sequence",
            "      label: string(20)",
            "",
            "  EmSplit:",
            "    meta: entity @root @editable @expose(20) @restExclude(batchUpdate, deleteById)",
            "    fields:",
            "      splitId: long @id @sequence",
            "      label:   string(20)",
            "",
            "  EmRead:",
            "    meta: entity @root @editable @expose(20) @readonly",
            "    fields:",
            "      readId: long @id @sequence",
            "      label:  string(20)",
            "",
            "  EmBare:",
            "    meta: entity @root @editable @expose(20, methods=read,search)",
            "    fields:",
            "      bareId: long @id @sequence",
            "      label:  string(20)",
            "",
            "  EmCase:",
            "    meta: entity @root @editable @expose(20, Methods=\"Read,Search\")",
            "    fields:",
            "      caseId: long @id @sequence",
            "      label:  string(20)",
            "",
            "  EmFull:",
            "    meta: entity @root @editable @expose(20)",
            "    fields:",
            "      fullId: long @id @sequence",
            "      label:  string(20)",
            "");

    private static File java;
    private static File kotlin;
    private static File layered;
    private static File compat;
    private static String javaLog;

    @BeforeAll
    static void generate() throws Exception {
        java = project("elcore-expose-java");
        javaLog = run(java, "java", "hexagonal");
        kotlin = project("elcore-expose-kt");
        run(kotlin, "kotlin", "hexagonal");
        layered = project("elcore-expose-layered");
        run(layered, "java", "layered");
        runTs(java, "hexagonal");
        compat = project("elcore-expose-compat");
        System.setProperty("elasticore.rules.v2", "true");
        try {
            runTs(compat, null);
        } finally {
            System.clearProperty("elasticore.rules.v2");
        }
    }

    // ---- the controller ------------------------------------------------------------

    @Test
    public void theControllerServesWhatIsListed() throws Exception {
        for (File project : new File[] {java, layered}) {
            String control = found(project, "EmCodeController.java");
            assertTrue(control.contains("@GetMapping(\"/{codeId}\")"), control);
            assertTrue(control.contains("@PostMapping(\"/search\")"), control);
            assertFalse(control.contains("@PostMapping\n") || control.contains("@PostMapping\r\n"), control);
            assertFalse(control.contains("@PutMapping"), control);
            assertFalse(control.contains("@DeleteMapping") || control.contains("\"/delete\""), control);
        }
        String kt = found(kotlin, "EmCodeController.kt");
        assertTrue(kt.contains("/search"), kt);
        assertFalse(kt.contains("@PutMapping") || kt.contains("@DeleteMapping") || kt.contains("\"/delete\""), kt);
    }

    @Test
    public void spacesInTheListAreRead() throws Exception {
        String control = found(java, "EmNoteController.java");
        assertTrue(
                control.contains("@PostMapping(\"/search\")") && control.contains("@GetMapping(\"/{noteId}\")"),
                control);
        assertTrue(control.contains(" create(@Valid @RequestBody EmNoteDTO inputDto)"), control);
        assertFalse(control.contains("@PutMapping") || control.contains("@DeleteMapping"), control);
    }

    @Test
    public void updateAndDeleteEachTakeBothOfTheirEndpoints() throws Exception {
        String control = found(java, "EmTagController.java");
        assertTrue(control.contains("@PutMapping(\"/batch\")") && control.contains("@DeleteMapping"), control);
        assertTrue(control.contains("\"/delete\""), control);
        assertFalse(
                control.contains("@PostMapping(\"/search\")") || control.contains("@GetMapping(\"/{tagId}\")"),
                control);
    }

    @Test
    public void anEntityWithoutTheListIsServedInFull() throws Exception {
        String control = found(java, "EmFullController.java");
        for (String mapping : new String[] {
            "@GetMapping(\"/{fullId}\")",
            "@PostMapping(\"/search\")",
            "@PutMapping(\"/batch\")",
            "@DeleteMapping",
            "\"/delete\"",
            "@GetMapping(\"/schema/info\")"
        }) {
            assertTrue(control.contains(mapping), mapping + "\n" + control);
        }
    }

    // ---- the service and the port ---------------------------------------------------

    @Test
    public void theServiceKeepsEveryMethod() throws Exception {
        for (File project : new File[] {java, layered}) {
            String service = found(project, "EmCodeCoreService.java");
            assertTrue(service.contains(" save(EmCodeDTO dto)") && service.contains(" update(EmCodeDTO dto)"), service);
            assertTrue(service.contains("batchUpdate(") && service.contains("deleteById("), service);
        }
    }

    @Test
    public void thePortPromisesTheWritesServedAndEveryRead() throws Exception {
        String useCase = found(java, "EmCodeUseCase.java");
        assertTrue(useCase.contains("findById(") && useCase.contains("findBySearch("), useCase);
        assertFalse(useCase.contains("save(") || useCase.contains("update(") || useCase.contains("delete"), useCase);
        String tag = found(java, "EmTagUseCase.java");
        assertTrue(tag.contains("update(") && tag.contains("batchUpdate(") && tag.contains("deleteById("), tag);
        assertFalse(tag.contains("save("), tag);
        String kt = found(kotlin, "EmCodeUseCase.kt");
        assertFalse(kt.contains("save(") || kt.contains("update(") || kt.contains("delete"), kt);
    }

    // ---- the client ------------------------------------------------------------------

    @Test
    public void theClientCallsWhatTheServerServesAndImportsOnlyThat() throws Exception {
        String code = found(java, "EmCodeApi.ts");
        assertTrue(code.contains("function findById(") && code.contains("function search("), code);
        assertFalse(
                code.contains("function create(")
                        || code.contains("function update(")
                        || code.contains("function deleteMany(")
                        || code.contains("function deleteById("),
                code);
        // noUnusedLocals: no import nothing reads.
        assertFalse(code.contains("EmCodeKeyDTO"), code);

        String tag = found(java, "EmTagApi.ts");
        assertFalse(tag.contains("requestOrNull") || tag.contains("EmTagSrchDTO"), tag);
        assertTrue(tag.contains("EmTagKeyDTO") && tag.contains("seg"), tag);

        String full = found(java, "EmFullApi.ts");
        assertTrue(full.contains("import { request, requestOrNull, seg, Page } from './apiSupport';"), full);
        // A client that only reads still names the page it answers with, and no request it never makes.
        assertTrue(code.contains("import { request, requestOrNull, seg, Page } from './apiSupport';"), code);
        String typo = found(java, "EmTypoApi.ts");
        assertTrue(typo.contains("import { request, Page } from './apiSupport';"), typo);
        assertTrue(full.contains("EmFullKeyDTO") && full.contains("EmFullSrchDTO"), full);
    }

    @Test
    public void aClientOfNothingIsNotWritten() throws Exception {
        assertNull(find(java, "EmNoneApi.ts"));
        String control = found(java, "EmNoneController.java");
        assertFalse(control.contains("@PostMapping(\"/search\")") || control.contains("@PutMapping"), control);
    }

    @Test
    public void aTwoXServerServesEverythingSoItsClientCallsEverything() throws Exception {
        String old = found(compat, "EmCodeApi.ts");
        assertTrue(old.contains("function create(") && old.contains("function deleteById("), old);
    }

    // ---- what the build says ------------------------------------------------------

    @Test
    public void aNameItDoesNotKnowIsSaidAndServesNothing() throws Exception {
        assertTrue(
                javaLog.contains("[EmTypo] @expose(methods=\"raed,search\"): raed is not one of create, read, search,"
                        + " update, delete; it serves nothing."),
                javaLog);
        String control = found(java, "EmTypoController.java");
        assertTrue(control.contains("@PostMapping(\"/search\")"), control);
        assertFalse(control.contains("@GetMapping(\"/{typoId}\")"), control);
        assertTrue(javaLog.contains("[EmNone] @expose(methods=\"\") names none of"), javaLog);
    }

    @Test
    public void restExcludeStillAppliesAndIsDeprecated() throws Exception {
        String control = found(java, "EmOldController.java");
        assertFalse(control.contains("@DeleteMapping") || control.contains("\"/delete\""), control);
        assertTrue(control.contains("@PutMapping"), control);
        assertTrue(
                javaLog.contains("[EmOld] @restExclude is deprecated: list what the controller serves instead,"
                        + " @expose(methods=\"create,read,search,update\")."),
                javaLog);
        assertFalse(javaLog.contains("[EmFull]"), javaLog);
    }

    @Test
    public void aRestExcludeNoListMatchesSaysWhatTheNearestChanges() throws Exception {
        assertTrue(
                javaLog.contains(
                        "[EmSplit] @restExclude is deprecated, and no @expose(methods) list serves the same"
                                + " endpoints: the nearest is @expose(methods=\"create,read,search\"), but it also drops PUT /."),
                javaLog);
        String control = found(java, "EmSplitController.java");
        assertTrue(control.contains("@PutMapping\n") || control.contains("@PutMapping\r\n"), control);
    }

    /**
     * Unquoted, the loader reads only the first name (an annotation with key=value
     * parameters keeps no bare value), so the list is cut short - to fewer endpoints.
     * The guides say to quote it. Capitals are read as the names they spell.
     */
    @Test
    public void anUnquotedListServesLessAndCapitalsAreRead() throws Exception {
        String bare = found(java, "EmBareController.java");
        assertTrue(bare.contains("@GetMapping(\"/{bareId}\")"), bare);
        assertFalse(
                bare.contains("@PostMapping(\"/search\")")
                        || bare.contains("@PutMapping")
                        || bare.contains("@DeleteMapping"),
                bare);
        String control = found(java, "EmCaseController.java");
        assertTrue(
                control.contains("@PostMapping(\"/search\")") && control.contains("@GetMapping(\"/{caseId}\")"),
                control);
        assertFalse(control.contains("@PutMapping") || control.contains("@DeleteMapping"), control);
        assertFalse(javaLog.contains("[EmCase]"), javaLog);
    }

    /**
     * Not only methods: any client whose server leaves endpoints out imports only what it
     * calls - a @readonly one had the key DTO of a delete it does not have, which a client
     * compiled with noUnusedLocals refused. Of a 3.0 server and of a 2.x one alike.
     */
    @Test
    public void aReadonlyClientImportsOnlyWhatItCalls() throws Exception {
        for (File project : new File[] {java, compat}) {
            String client = found(project, "EmReadApi.ts");
            assertTrue(client.contains("function findById(") && client.contains("function search("), client);
            assertFalse(client.contains("function create(") || client.contains("EmReadKeyDTO"), client);
        }
    }

    // ---- harness -------------------------------------------------------------------

    private static File project(String name) throws Exception {
        File dir = Files.createTempDirectory(name).toFile();
        File blueprint = new File(dir, "src/main/resources/blueprint/emshop");
        assertTrue(new File(blueprint, "model").mkdirs());
        Files.write(
                new File(blueprint, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: emshop",
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    base: io.acme.emshop",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private static String run(File project, String language, String architecture) throws Exception {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v3"
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
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            if ("layered".equals(architecture)) System.setProperty("elasticore.rules.v3", "true");
            RulesGenRunner.main(new String[0]);
        } finally {
            System.setOut(out);
            System.setErr(err);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        return log;
    }

    private static void runTs(File project, String architecture) {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.ts",
            "elasticore.rules.v3"
        };
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
