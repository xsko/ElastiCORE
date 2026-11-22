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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * export in @expose(methods="...") (issue 271): POST /export on the controller of an entity that lists it - in
 * every mode (JPA, MyBatis, MongoDB), both languages and every architecture the mode has - and exportXxx in its
 * TypeScript client; nothing on an entity that does not list it, whose files are what they were.
 */
public class SearchExportGenerationTest {

    /** An entity with the five it has: generated alike with or without an export beside it. */
    private static final String FULL = String.join(
            "\n",
            "  ExFull:",
            "    meta: entity @root @editable @expose(20)",
            "    fields:",
            "      fullId: long @id @sequence",
            "      label:  string(20)",
            "");

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  ExItem:",
            "    meta: entity @root @editable @expose(20, methods=\"read,search,export\")",
            "    fields:",
            "      itemId: long @id @sequence",
            "      name:   string(30) @search(like)",
            "      price:  long",
            "",
            "  ExTypo:",
            "    meta: entity @root @editable @expose(20, methods=\"search,exprot\")",
            "    fields:",
            "      typoId: long @id @sequence",
            "      label:  string(20)",
            "",
            FULL);

    /** mode, language, architecture -> the generated project. */
    private static final Map<String, File> RUNS = new LinkedHashMap<String, File>();

    private static String javaLog;

    private static File ts;

    private static File tsWithout;

    @BeforeAll
    static void generate() throws Exception {
        String[][] runs = {
            {"jpa", "java", "hexagonal"},
            {"jpa", "java", "layered"},
            {"jpa", "java", "ddd"},
            {"jpa", "kotlin", "hexagonal"},
            {"jpa", "kotlin", "layered"},
            {"mybatis", "java", "layered"},
            {"mybatis", "java", "hexagonal"},
            {"mongo", "java", "layered"},
            {"mongo", "kotlin", "layered"}
        };
        for (String[] r : runs) {
            File project = project("elcore-export-" + r[0] + "-" + r[1] + "-" + r[2], r[0], MODEL);
            String log = run(project, r[1], r[2]);
            if (javaLog == null) javaLog = log;
            RUNS.put(String.join("/", r), project);
        }
        ts = project("elcore-export-ts", "jpa", MODEL);
        runTs(ts, "hexagonal");
        tsWithout = project("elcore-export-ts-none", "jpa", "entity:\n" + FULL);
        runTs(tsWithout, "hexagonal");
        // Domains beside the exporting one, before and after it: they write the same apiSupport.ts.
        tsDomains = project("elcore-export-ts-domains", "jpa", MODEL);
        domain(tsDomains, "aaplain", plain("AaNote"));
        domain(tsDomains, "zzplain", plain("ZzNote"));
        runTs(tsDomains, "hexagonal");
    }

    private static String plain(String entity) {
        return String.join(
                "\n",
                "entity:",
                "  " + entity + ":",
                "    meta: entity @root @editable @expose(20)",
                "    fields:",
                "      noteId: long @id @sequence",
                "      text:   string(20)",
                "");
    }

    private static File tsDomains;

    /** Another domain of the project. */
    private static void domain(File project, String name, String model) throws Exception {
        File blueprint = new File(project, "src/main/resources/blueprint/" + name);
        assertTrue(new File(blueprint, "model").mkdirs());
        Files.write(
                new File(blueprint, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: " + name,
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    base: io.acme." + name,
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Every domain of a run writes the one apiSupport.ts (src/main/ts): it keeps the export's types whichever domain
     * is written last, so the exporting domain's client compiles beside domains that do not export.
     */
    @Test
    public void theSharedApiSupportKeepsTheExportWhicheverDomainIsWrittenLast() throws Exception {
        String support = found(tsDomains, "apiSupport.ts");
        assertTrue(
                support.contains("export async function requestFile(") && support.contains("ExportRequest<S>"),
                support);
        assertTrue(found(tsDomains, "ExItemApi.ts").contains("requestFile, ExportRequest"));
        assertFalse(found(tsDomains, "ZzNoteApi.ts").contains("requestFile"));
    }

    // ---- the controller -------------------------------------------------------------------

    @Test
    public void theControllerOfAnEntityThatListsExportServesIt() throws Exception {
        for (Map.Entry<String, File> run : RUNS.entrySet()) {
            boolean kotlin = run.getKey().contains("/kotlin/");
            String control = found(run.getValue(), "ExItemController" + (kotlin ? ".kt" : ".java"));
            String where = run.getKey() + "\n" + control;
            assertTrue(control.contains("@PostMapping(\"/export\")"), where);
            assertTrue(
                    control.contains("@PostMapping(\"/search\")") && control.contains("@GetMapping(\"/{itemId}\")"),
                    where);
            // The names it lists and no other.
            assertFalse(control.contains("@PutMapping") || control.contains("@DeleteMapping"), where);
            // Every page of the service's own search, in the order asked for with the key after it.
            assertTrue(
                    control.contains(
                            "io.elasticore.springboot3.export.SearchExport.of(\"exshop\", \"ExItem\", ExItemDTO"),
                    where);
            assertTrue(control.contains("exItemService.findBySearch(searchDTO)"), where);
            if (kotlin) {
                assertTrue(
                        control.contains("fun export(@Valid @RequestBody request: io.elasticore.springboot3.export"
                                + ".SearchExport.Request<ExItemSrchDTO>,"),
                        where);
                assertTrue(control.contains("val searchDTO = request.search ?: ExItemSrchDTO()"), where);
                assertTrue(
                        control.contains("searchDTO.sortCode = io.elasticore.springboot3.export.SearchExport.sortCode("
                                + "request.sortCode, searchDTO.sortCode, searchDTO.sortColumn, searchDTO.sortAscending,"
                                + " \"itemId\")"),
                        where);
                assertTrue(control.contains("searchDTO.sortColumn = null"), where);
                assertTrue(control.contains("searchDTO.pageNumber = page"), where);
            } else {
                assertTrue(
                        control.contains("public void export(@Valid @RequestBody io.elasticore.springboot3.export"
                                + ".SearchExport.Request<ExItemSrchDTO> request,"),
                        where);
                assertTrue(control.contains("searchDTO.setPageNumber(page);"), where);
            }
        }
    }

    @Test
    public void anEntityThatDoesNotListItIsGeneratedAsBefore() throws Exception {
        for (Map.Entry<String, File> run : RUNS.entrySet()) {
            boolean kotlin = run.getKey().contains("/kotlin/");
            String full = found(run.getValue(), "ExFullController" + (kotlin ? ".kt" : ".java"));
            assertFalse(full.contains("export") || full.contains("Export"), run.getKey() + "\n" + full);
            assertTrue(full.contains("@DeleteMapping"), run.getKey() + "\n" + full);
        }
        // The same file as in a project where no entity lists export.
        assertEquals(found(tsWithout, "ExFullController.java"), found(ts, "ExFullController.java"));
        assertEquals(found(tsWithout, "ExFullApi.ts"), found(ts, "ExFullApi.ts"));
    }

    @Test
    public void theUseCaseAndTheServiceAreAsTheSearchHasThem() throws Exception {
        // The export calls findBySearch, which the port and the service have already.
        String port = found(RUNS.get("jpa/java/hexagonal"), "ExItemUseCase.java");
        assertTrue(port.contains("findBySearch(ExItemSrchDTO searchDTO)"), port);
        assertFalse(port.contains("export"), port);
    }

    // ---- the client ---------------------------------------------------------------------------

    @Test
    public void theClientCallsTheExportAndReceivesAFile() throws Exception {
        String client = found(ts, "ExItemApi.ts");
        assertTrue(
                client.contains("export async function exportExItem(body: ExportRequest<ExItemSrchDTO>,"
                        + " init?: RequestInit): Promise<Blob> {"),
                client);
        assertTrue(client.contains("return requestFile('POST', BASE + '/export', body, init);"), client);
        assertTrue(client.contains("requestFile, ExportRequest"), client);

        String support = found(ts, "apiSupport.ts");
        assertTrue(support.contains("export interface ExportRequest<S> {"), support);
        // The enumeration columns by their label (issue 289): a list of them, or '*'.
        assertTrue(support.replace("\r\n", "\n").contains("    labels?: string[] | '*';\n}"), support);
        assertTrue(support.contains("export async function requestFile("), support);
        // Without an export in the domain, the file is what it was: the same text without the two.
        String before = found(tsWithout, "apiSupport.ts").replace("\r\n", "\n");
        assertFalse(before.contains("requestFile") || before.contains("ExportRequest"), before);
        String lf = support.replace("\r\n", "\n");
        int cut = lf.indexOf("\n/** The body of an export");
        assertTrue(cut > 0, support);
        assertEquals(before, lf.substring(0, cut));
        assertFalse(found(ts, "ExFullApi.ts").contains("export async function export"));
    }

    // ---- what the build says ------------------------------------------------------------------

    @Test
    public void aNameItDoesNotKnowIsStillSaid() throws Exception {
        assertTrue(
                javaLog.contains("[ExTypo] @expose(methods=\"search,exprot\"): exprot is not one of create, read,"
                        + " search, update, delete, export, updateOne, updateBatch, deleteOne, deleteBatch; it serves"
                        + " nothing."),
                javaLog);
        String typo = found(RUNS.get("jpa/java/hexagonal"), "ExTypoController.java");
        assertFalse(typo.contains("/export"), typo);
    }

    // ---- harness --------------------------------------------------------------------------------

    private static File project(String name, String mode, String model) throws Exception {
        File dir = Files.createTempDirectory(name).toFile();
        File blueprint = new File(dir, "src/main/resources/blueprint/exshop");
        assertTrue(new File(blueprint, "model").mkdirs());
        Files.write(
                new File(blueprint, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: exshop",
                                "    framework: springboot",
                                "    mode: " + mode,
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    base: io.acme.exshop",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
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
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty("elasticore.rules.ts", "true");
            assertTrue(RulesGenRunner.run(new String[0]) == 0, "the TypeScript run failed: " + project);
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
        // The Java sources too, for the file compared with the project without an export.
        try {
            run(project, "java", architecture);
        } catch (Exception e) {
            throw new IllegalStateException(e);
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
