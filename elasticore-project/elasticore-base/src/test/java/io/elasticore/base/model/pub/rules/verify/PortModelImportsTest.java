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
 * A port imports the domain's model packages with wildcards, so it may name
 * only packages that hold something. A domain of ports and DTOs alone - a
 * dashboard reading through SQL - has an entity namespace in its env.yml but
 * no entity package, and an import of it does not compile. 2.x imports a
 * model package only when the domain has a model of that kind; the rule
 * engine imported the entity and DTO packages always.
 */
public class PortModelImportsTest {

    private static final String REPORT_PORT = String.join("\n",
            "port:",
            "  ReportQueryService:",
            "    meta: dbms",
            "    methods:",
            "      summaryOf:",
            "        meta: method",
            "        params:",
            "          fromDate: LocalDate",
            "        return: List<ReportRowDTO>",
            "        nativeQuery: true",
            "        query: |",
            "          SELECT COUNT(*) AS total FROM report_log WHERE created_at >= :fromDate",
            "");

    private static final String REPORT_DTO = String.join("\n",
            "dto:",
            "  ReportRowDTO:",
            "    meta: dto",
            "    fields:",
            "      total: long",
            "");

    /** Ports alone: no entity, no DTO. */
    private static final String PING_PORT = String.join("\n",
            "port:",
            "  PingQueryService:",
            "    meta: dbms",
            "    methods:",
            "      countAll:",
            "        meta: method",
            "        params:",
            "        return: int",
            "        nativeQuery: true",
            "        query: |",
            "          SELECT COUNT(*) FROM ping_log",
            "");

    /** A domain with entities, whose port keeps every import it had. */
    private static final String SALES = String.join("\n",
            "entity:",
            "  Sale:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      saleId: long @id @sequence",
            "      amount: long",
            "port:",
            "  SaleQueryService:",
            "    meta: dbms",
            "    methods:",
            "      countSales:",
            "        meta: method",
            "        params:",
            "        return: int",
            "        nativeQuery: true",
            "        query: |",
            "          SELECT COUNT(*) FROM sale",
            "");

    private static final List<File> projects = new ArrayList<File>();

    private static File compat;
    private static File v3;

    @BeforeAll
    static void generate() throws Exception {
        compat = run("elcore-portimports-compat", false);
        v3 = run("elcore-portimports-v3", true);
    }

    @AfterAll
    static void clean() throws Exception {
        java.lang.reflect.Field tmp = io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        for (File project : projects) delete(project);
    }

    @Test
    public void aDomainWithoutEntitiesImportsNoEntityPackage() throws Exception {
        for (File project : new File[]{compat, v3}) {
            String port = found(project, "ReportQueryService.java");
            assertFalse(port.contains("import io.acme.report.entity."), port);
            assertTrue(port.contains("import io.acme.report.dto.*;"), port);
        }
    }

    @Test
    public void aDomainOfPortsAloneImportsNoModelPackage() throws Exception {
        for (File project : new File[]{compat, v3}) {
            String port = found(project, "PingQueryService.java");
            assertFalse(port.contains("import io.acme.ping.entity."), port);
            assertFalse(port.contains("import io.acme.ping.dto."), port);
        }
    }

    @Test
    public void aDomainWithEntitiesKeepsItsImports() throws Exception {
        for (File project : new File[]{compat, v3}) {
            String port = found(project, "SaleQueryService.java");
            assertTrue(port.contains("import io.acme.sales.entity.*;"), port);
            assertTrue(port.contains("import io.acme.sales.dto.*;"), port);
        }
    }

    private static File run(String name, boolean clean) throws Exception {
        File project = Files.createTempDirectory(name).toFile();
        projects.add(project);
        domain(project, "report", new String[]{"port.yml", REPORT_PORT}, new String[]{"dto.yml", REPORT_DTO});
        domain(project, "ping", new String[]{"port.yml", PING_PORT});
        domain(project, "sales", new String[]{"model.yml", SALES});

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
            if (clean) System.setProperty("elasticore.rules.v3", "true");
            assertEquals(0, RulesGenRunner.run(new String[0]), "the run failed: " + project);
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
        return project;
    }

    /** A domain whose env.yml declares the usual namespaces, entity included. */
    private static void domain(File project, String name, String[]... files) throws IOException {
        File dir = new File(project, "src/main/resources/blueprint/" + name);
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(new File(dir, "env.yml").toPath(), String.join("\n",
                "elasticore:", "  enable: true", "  config:", "    domainName: " + name, "    framework: springboot",
                "    mode: jpa", "    j2ee: jakarta", "  namespace:", "    base: io.acme." + name,
                "    entity: io.acme." + name + ".entity", "    dto: io.acme." + name + ".dto",
                "    port: io.acme." + name + ".port", "")
                .getBytes(StandardCharsets.UTF_8));
        for (String[] file : files) {
            Files.write(new File(dir, "model/" + file[0]).toPath(), file[1].getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String found(File project, String fileName) throws IOException {
        List<File> hits = new ArrayList<File>();
        collect(project, fileName, hits);
        assertEquals(1, hits.size(), fileName + " in " + project + ": " + hits);
        return new String(Files.readAllBytes(hits.get(0).toPath()), StandardCharsets.UTF_8);
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
