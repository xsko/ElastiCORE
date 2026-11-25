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
 * A search DTO's relation, in the browser's model. The Java search DTO holds
 * the entity and a search sends its key alone - {"insurer": {"id": "C1"}} -
 * but the TypeScript one declared the entity type and imported a module no
 * run writes (elcore3ts writes DTOs, not entities), so the client did not
 * compile. The property is now the key's shape. An enum stays an import.
 */
public class TypeScriptSearchDtoRelationTest {

    private static final String ORGANIZATION = String.join("\n",
            "entity:",
            "  Company:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      id: string(20) @id",
            "      name: string(100)",
            "");

    private static final String SALES = String.join("\n",
            "enumeration:",
            "  SaleStatus:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(1) @code",
            "      name: string(20)",
            "    enum:",
            "      OPEN: O,Open",
            "      DONE: D,Done",
            "entity:",
            "  Branch:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      branchId: long @id @sequence",
            "  Sale:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      saleId: long @id @sequence",
            "      insurer: Company @search(eq)",
            "      branch: Branch @search(eq)",
            "      status: SaleStatus @search(eq)",
            "dto:",
            "  SaleSrchDTO:",
            "    meta: dto @searchable(entity=Sale, pageable=true)",
            "    fields:",
            "      saleNo: string",
            "");

    private static File project;

    @BeforeAll
    static void generate() throws Exception {
        project = Files.createTempDirectory("elcore-ts-srch-relation").toFile();
        domain("organization", ORGANIZATION);
        domain("sales", SALES);

        String[] keys = {"elasticore.base.path", "elasticore.architecture", "elasticore.language",
                "elasticore.rules.force", "elasticore.rules.v3", "elasticore.rules.ts"};
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "layered");
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty("elasticore.rules.ts", "true");
            assertEquals(0, RulesGenRunner.run(new String[0]), "the run failed: " + project);
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
    }

    @AfterAll
    static void cleanUp() throws Exception {
        java.lang.reflect.Field tmp = io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        delete(project);
    }

    @Test
    public void aRelationOfAnotherDomainIsItsKey() throws Exception {
        String dto = found("SaleSrchDTO.ts");
        assertTrue(dto.contains("insurer?: { id: string };"), dto);
        assertFalse(dto.contains("from './Company'"), dto);
    }

    @Test
    public void aRelationOfTheSameDomainIsItsKey() throws Exception {
        String dto = found("SaleSrchDTO.ts");
        assertTrue(dto.contains("branch?: { branchId: number };"), dto);
        assertFalse(dto.contains("from './Branch'"), dto);
    }

    @Test
    public void anEnumIsStillImported() throws Exception {
        String dto = found("SaleSrchDTO.ts");
        assertTrue(dto.contains("status?: SaleStatus;"), dto);
        // Only a field's type in a DTO file: a type-only import (verbatimModuleSyntax, issue 346).
        assertTrue(dto.contains("import type { SaleStatus } from './SaleStatus';"), dto);
    }

    @Test
    public void everyModuleTheSearchDtoImportsIsWritten() throws Exception {
        String dto = found("SaleSrchDTO.ts");
        for (String line : dto.split("\\r?\\n")) {
            int from = line.indexOf("from './");
            if (from < 0) continue;
            String module = line.substring(from + 8, line.lastIndexOf('\''));
            List<File> hits = new ArrayList<File>();
            collect(project, module + ".ts", hits);
            assertEquals(1, hits.size(), module + ".ts is imported but not written: " + dto);
        }
    }

    private static void domain(String name, String model) throws IOException {
        File dir = new File(project, "src/main/resources/blueprint/" + name);
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(new File(dir, "env.yml").toPath(), String.join("\n",
                "elasticore:", "  enable: true", "  config:", "    domainName: " + name, "    framework: springboot",
                "    mode: jpa", "    j2ee: jakarta", "  namespace:",
                "    entity: io.acme." + name + ".entity", "    dto: io.acme." + name + ".dto",
                "    enumeration: io.acme." + name + ".enums",
                "    repository: io.acme." + name + ".repository", "    service: io.acme." + name + ".service",
                "    control: io.acme." + name + ".controller", "")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
    }

    private static String found(String fileName) throws IOException {
        List<File> hits = new ArrayList<File>();
        collect(project, fileName, hits);
        assertEquals(1, hits.size(), fileName + " in " + project + ": " + hits);
        return new String(Files.readAllBytes(hits.get(0).toPath()), StandardCharsets.UTF_8);
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
