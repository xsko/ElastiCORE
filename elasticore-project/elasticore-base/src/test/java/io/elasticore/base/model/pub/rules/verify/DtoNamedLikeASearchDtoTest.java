package io.elasticore.base.model.pub.rules.verify;

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
 * A plain DTO whose name ends in SrchDTO - a report's filter, say - is a
 * plain DTO: its class takes the declared name, as 2.x writes it. The rule
 * engine used to read the name as the mark of a search DTO, and the plain DTO
 * file then wrote class LedgerStatSrchDTODTO into LedgerStatSrchDTO.java,
 * which does not compile. Every output that writes a DTO class is checked:
 * the 2.x-compatible one, -Pv3, Kotlin and TypeScript.
 */
public class DtoNamedLikeASearchDtoTest {

    private static final String MODEL = String.join("\n",
            "entity:",
            "  Ledger:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      ledgerId: long @id @sequence",
            "      amount:   long",
            "",
            "dto:",
            "  LedgerStatSrchDTO:",
            "    meta: dto",
            "    fields:",
            "      dateFrom: LocalDate",
            "      dateTo:   LocalDate",
            "");

    private static final List<File> projects = new ArrayList<File>();

    private static File compat;
    private static File v3;
    private static File kotlin;
    private static File typescript;

    @BeforeAll
    static void generate() throws Exception {
        compat = run("elcore-srchname-compat", "java", false, false);
        v3 = run("elcore-srchname-v3", "java", true, false);
        kotlin = run("elcore-srchname-kt", "kotlin", false, false);
        typescript = run("elcore-srchname-ts", "java", false, true);
    }

    @AfterAll
    static void clean() throws Exception {
        java.lang.reflect.Field tmp = io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        for (File project : projects) delete(project);
    }

    @Test
    public void theCompatibleOutputKeepsTheDeclaredName() throws Exception {
        String dto = found(compat, "LedgerStatSrchDTO.java");
        assertTrue(dto.contains("class LedgerStatSrchDTO "), dto);
        assertFalse(dto.contains("LedgerStatSrchDTODTO"), dto);
    }

    @Test
    public void theCleanOutputKeepsTheDeclaredName() throws Exception {
        String dto = found(v3, "LedgerStatSrchDTO.java");
        assertTrue(dto.contains("class LedgerStatSrchDTO "), dto);
        assertFalse(dto.contains("LedgerStatSrchDTODTO"), dto);
    }

    @Test
    public void kotlinKeepsTheDeclaredName() throws Exception {
        String dto = found(kotlin, "LedgerStatSrchDTO.kt");
        assertTrue(dto.contains("class LedgerStatSrchDTO"), dto);
        assertFalse(dto.contains("LedgerStatSrchDTODTO"), dto);
    }

    @Test
    public void typeScriptKeepsTheDeclaredName() throws Exception {
        String dto = found(typescript, "LedgerStatSrchDTO.ts");
        assertTrue(dto.contains("interface LedgerStatSrchDTO "), dto);
        assertFalse(dto.contains("LedgerStatSrchDTODTO"), dto);
    }

    /** The entity's own search DTO is still one: its name and its search shape are unchanged. */
    @Test
    public void theEntitysSearchDtoIsStillASearchDto() throws Exception {
        String srch = found(compat, "LedgerSrchDTO.java");
        assertTrue(srch.contains("class LedgerSrchDTO "), srch);
        assertEquals(1, matches(compat, "LedgerSrchDTO.java"), "one search DTO file");
    }

    private static File run(String name, String language, boolean clean, boolean ts) throws Exception {
        File project = Files.createTempDirectory(name).toFile();
        projects.add(project);
        File blueprint = new File(project, "src/main/resources/blueprint/ledger");
        assertTrue(new File(blueprint, "model").mkdirs());
        Files.write(new File(blueprint, "env.yml").toPath(), String.join("\n",
                "elasticore:", "  enable: true", "  config:", "    domainName: ledger", "    framework: springboot",
                "    mode: jpa", "    j2ee: jakarta", "  namespace:", "    base: io.acme.ledger", "")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));

        String[] keys = {"elasticore.base.path", "elasticore.architecture", "elasticore.language",
                "elasticore.rules.force", "elasticore.rules.v3", "elasticore.rules.ts"};
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "layered");
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            if (clean) System.setProperty("elasticore.rules.v3", "true");
            if (ts) System.setProperty("elasticore.rules.ts", "true");
            assertEquals(0, RulesGenRunner.run(new String[0]), "the run failed: " + project);
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
        return project;
    }

    private static String found(File project, String fileName) throws IOException {
        List<File> hits = new ArrayList<File>();
        collect(project, fileName, hits);
        assertEquals(1, hits.size(), fileName + " in " + project + ": " + hits);
        return new String(Files.readAllBytes(hits.get(0).toPath()), StandardCharsets.UTF_8);
    }

    private static int matches(File project, String fileName) {
        List<File> hits = new ArrayList<File>();
        collect(project, fileName, hits);
        return hits.size();
    }

    private static void collect(File dir, String fileName, List<File> hits) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collect(child, fileName, hits);
            else if (child.getName().equals(fileName)) hits.add(child);
        }
    }

    private static void delete(File project) throws IOException {
        if (project == null || !project.exists()) return;
        Files.walkFileTree(project.toPath(), new SimpleFileVisitor<Path>() {
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
