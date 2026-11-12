package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.extract.ModelExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A DTO declared {@code @searchResult(Entity)} is what the entity's search
 * answers with: the list carries the DTO's fields, not every field the entity
 * has. 2.x did this; the rule engine wrote the DTO and left the search on the
 * entity's own DTO. An entity without one searches as before.
 */
public class SearchResultDtoTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  SrDiagram:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      diagramId: long @id @sequence",
            "      name: string(50) @search(like)",
            "      body: text -- a big diagram body the list must not carry",
            "",
            "  SrNote:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      noteId: long @id @sequence",
            "      title: string(50) @search(like)",
            "",
            "dto:",
            "  SrDiagramDTO:",
            "    meta: dto @template(SrDiagram)",
            "",
            "  SrDiagramResultDTO:",
            "    meta: dto @searchResult(SrDiagram)",
            "    fields:",
            "      body: --",
            "",
            "  SrNoteDTO:",
            "    meta: dto @template(SrNote)",
            "");

    /** A result DTO that also copies its entity through @template. */
    private static final String TEMPLATED = MODEL
            + String.join(
                    "\n",
                    "  SrNoteCardDTO:",
                    "    meta: dto @template(SrNote) @searchResult(SrNote)",
                    "    fields:",
                    "      title: --",
                    "");

    private final List<File> projects = new ArrayList<File>();

    @Test
    public void theV3SearchAnswersWithTheResultDto() throws Exception {
        File project = project(MODEL);
        assertEquals(0, run(project, "layered", "java", "elasticore.rules.v3", null));

        String service = found(project, "SrDiagramCoreService.java");
        assertTrue(service.contains("Page<SrDiagramResultDTO> findBySearch("), service);
        assertTrue(service.contains("Mapper::toSrDiagramResultDTO"), service);
        assertFalse(service.contains("Page<SrDiagramDTO> findBySearch("), service);

        String mapper = found(project, "SrdeskMapper.java");
        assertTrue(mapper.contains("public static SrDiagramResultDTO toSrDiagramResultDTO(SrDiagram from)"), mapper);
        String section = section(
                mapper,
                "public static void mapping(SrDiagram from, SrDiagramResultDTO to, boolean isSkipNull, MappingContext c)");
        assertTrue(section.contains("\"name\""), section);
        assertFalse(section.contains("\"body\""), "the result DTO has no body:\n" + section);

        String controller = found(project, "SrDiagramController.java");
        assertTrue(controller.contains("import io.acme.srdesk.dto.SrDiagramResultDTO;"), controller);
        assertTrue(controller.contains("Page<SrDiagramResultDTO> search("), controller);

        // An entity without a result DTO searches with its own, as before.
        String note = found(project, "SrNoteCoreService.java");
        assertTrue(note.contains("Page<SrNoteDTO> findBySearch("), note);
        assertTrue(note.contains("Mapper::toDTO"), note);
        String noteController = found(project, "SrNoteController.java");
        assertFalse(noteController.contains("ResultDTO"), noteController);
        assertTrue(
                noteController.contains(
                        "import io.acme.srdesk.dto.SrNoteSrchDTO;\nimport io.acme.srdesk.service.SrNoteService;"),
                "no line is added to a controller without a result DTO:\n" + noteController);
    }

    /** With @template too, the DTO's own mapping is the one the search calls. */
    @Test
    public void aTemplatedResultDtoIsMappedOnce() throws Exception {
        File project = project(TEMPLATED);
        assertEquals(0, run(project, "layered", "java", "elasticore.rules.v3", null));
        String service = found(project, "SrNoteCoreService.java");
        assertTrue(service.contains("Page<SrNoteCardDTO> findBySearch("), service);
        assertTrue(service.contains("Mapper::toSrNoteCardDTO"), service);
        String mapper = found(project, "SrdeskMapper.java");
        assertEquals(1, count(mapper, "public static SrNoteCardDTO toSrNoteCardDTO(SrNote from)"), mapper);
    }

    /** The 2.x-compatible output answers as the 2.x generator does. */
    @Test
    public void theCompatibleOutputSearchesAs2xDoes() throws Exception {
        File project = project(MODEL);
        assertEquals(0, run(project, "layered", "java", "elasticore.rules.v2", null));
        String compatible = found(project, "SrDiagramCoreService.java");
        String compatibleMapper = found(project, "SrdeskMapper.java");

        File legacy = project(MODEL);
        String[] run = extract(legacy);
        assertEquals("0", run[0], run[1]);
        String generated = found(legacy, "SrDiagramCoreService.java");
        String generatedMapper = found(legacy, "SrdeskMapper.java");

        for (String line : new String[] {"Page<SrDiagramResultDTO> findBySearch(", "Mapper::toSrDiagramResultDTO"}) {
            assertTrue(generated.contains(line), "2.x: " + line + "\n" + generated);
            assertTrue(compatible.contains(line), "compatible: " + line + "\n" + compatible);
        }
        String method = "public static SrDiagramResultDTO toSrDiagramResultDTO(SrDiagram from)";
        assertTrue(generatedMapper.contains(method), generatedMapper);
        assertTrue(compatibleMapper.contains(method), compatibleMapper);
        assertEquals(
                section(
                        generatedMapper,
                        "public static void mapping(SrDiagram from, SrDiagramResultDTO to, boolean isSkipNull, MappingContext c)"),
                section(
                        compatibleMapper,
                        "public static void mapping(SrDiagram from, SrDiagramResultDTO to, boolean isSkipNull, MappingContext c)"));
    }

    @Test
    public void kotlinSearchesWithTheResultDto() throws Exception {
        File project = project(MODEL);
        assertEquals(0, run(project, "layered", "kotlin", null, null));
        String service = found(project, "SrDiagramCoreService.kt");
        assertTrue(service.contains("Page<SrDiagramResultDTO>"), service);
        assertTrue(service.contains(".toSrDiagramResultDTO(it)"), service);
        String mapper = found(project, "SrdeskMapper.kt");
        assertTrue(mapper.contains("fun toSrDiagramResultDTO(from: SrDiagram"), mapper);
        String controller = found(project, "SrDiagramController.kt");
        assertTrue(controller.contains("import io.acme.srdesk.dto.SrDiagramResultDTO\n"), controller);
    }

    @Test
    public void theHexagonalUseCaseAnswersWithTheResultDto() throws Exception {
        File project = project(MODEL);
        assertEquals(0, run(project, "hexagonal", "java", null, null));
        String useCase = found(project, "SrDiagramUseCase.java");
        assertTrue(useCase.contains("import io.acme.srdesk.dto.SrDiagramResultDTO;"), useCase);
        assertTrue(useCase.contains("Page<SrDiagramResultDTO> findBySearch("), useCase);
        assertTrue(found(project, "SrdeskMapper.java").contains("toSrDiagramResultDTO(SrDiagram from)"));
    }

    @Test
    public void theDddServiceAnswersWithTheResultDto() throws Exception {
        File project = project(MODEL);
        assertEquals(0, run(project, "ddd", "java", null, null));
        String service = found(project, "SrDiagramCoreService.java");
        assertTrue(service.contains("Page<SrDiagramResultDTO> findBySearch("), service);
        assertTrue(found(project, "SrdeskMapper.java").contains("toSrDiagramResultDTO(SrDiagram from)"));
    }

    @Test
    public void myBatisSearchesWithTheResultDto() throws Exception {
        File project = project(MODEL);
        File env = new File(project, "src/main/resources/blueprint/srdesk/env.yml");
        String yml = new String(Files.readAllBytes(env.toPath()), StandardCharsets.UTF_8);
        Files.write(env.toPath(), yml.replace("mode: jpa", "mode: mybatis").getBytes(StandardCharsets.UTF_8));
        assertEquals(0, run(project, "layered", "java", "elasticore.rules.v3", null));
        String service = found(project, "SrDiagramCoreService.java");
        assertTrue(service.contains("Page<SrDiagramResultDTO> findBySearch("), service);
        assertTrue(service.contains("Mapper::toSrDiagramResultDTO"), service);
        assertTrue(found(project, "SrdeskMapper.java").contains("toSrDiagramResultDTO(SrDiagram from)"));
    }

    @Test
    public void theTypescriptClientReadsTheResultDto() throws Exception {
        File project = project(MODEL);
        assertEquals(0, run(project, "layered", "java", "elasticore.rules.ts", null));
        String api = found(project, "SrDiagramApi.ts");
        assertTrue(api.contains("SrDiagramResultDTO"), api);
        assertFalse(found(project, "SrNoteApi.ts").contains("ResultDTO"));
    }

    // ------------------------------------------------------------------

    private File project(String model) throws IOException {
        File project = Files.createTempDirectory("elcore-result").toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/srdesk");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: srdesk",
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    entity: io.acme.srdesk.entity",
                                "    dto: io.acme.srdesk.dto",
                                "    repository: io.acme.srdesk.repository",
                                "    service: io.acme.srdesk.service",
                                "    control: io.acme.srdesk.controller",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    /** From the line that starts with the head to the closing brace of that method. */
    private static String section(String source, String head) {
        int from = source.indexOf(head);
        assertTrue(from >= 0, head + "\n" + source);
        int to = source.indexOf("\n    }\n", from);
        return source.substring(from, to < 0 ? source.length() : to);
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) n++;
        return n;
    }

    private static int run(
            File project, String architecture, String language, String output, ByteArrayOutputStream captured) {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v3",
            "elasticore.rules.v2",
            "elasticore.rules.ts"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        PrintStream out = System.out;
        ByteArrayOutputStream bytes = captured != null ? captured : new ByteArrayOutputStream();
        try {
            for (String k : keys) System.clearProperty(k);
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            if (output != null) System.setProperty(output, "true");
            return RulesGenRunner.run(new String[0]);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        } finally {
            System.setOut(out);
            out.print(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
    }

    /** The 2.x generator in a JVM of its own, as ./gradlew elcore runs it. Its exit code and log. */
    private static String[] extract(File project) throws Exception {
        File log = new File(project, "gen.log");
        Process p = new ProcessBuilder(
                        Paths.get(System.getProperty("java.home"), "bin", "java")
                                .toString(),
                        "-Xmx256m",
                        "-Dfile.encoding=UTF-8",
                        "-Delasticore.base.path=" + project.getAbsolutePath(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        ModelExtractor.class.getName())
                .directory(project)
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start();
        try {
            assertTrue(p.waitFor(120, TimeUnit.SECONDS), "generation did not finish");
        } finally {
            p.destroyForcibly();
        }
        return new String[] {
            String.valueOf(p.exitValue()), new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8)
        };
    }

    private static String found(File project, String fileName) throws IOException {
        List<File> hits = new ArrayList<File>();
        collect(project, fileName, hits);
        assertEquals(1, hits.size(), fileName + " in " + project + ": " + hits);
        return new String(Files.readAllBytes(hits.get(0).toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static void collect(File dir, String fileName, List<File> hits) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, fileName, hits);
            else if (f.getName().equals(fileName)) hits.add(f);
        }
    }

    @AfterEach
    void clean() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        io.elasticore.base.model.pub.rules.ModelCheck.reset();
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
        for (File project : projects) delete(project);
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
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
