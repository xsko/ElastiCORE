package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A TypeScript run asks a rule set listed for TypeScript for its domain-level files
 * (DomainArtifactContributor), as it asks for its per-entity ones (issue 266). Before,
 * the TypeScript branch returned after the enums and apiSupport, and the file was not
 * written, with nothing said.
 */
public class TsDomainContributorTest {

    @Test
    public void aTeamsDomainFileIsWrittenInTheTypeScriptRun() throws Exception {
        File project = Files.createTempDirectory("elcore-ts-domain").toFile();
        try {
            File dir = new File(project, "src/main/resources/blueprint/crm");
            assertTrue(new File(dir, "model").mkdirs());
            Files.write(
                    new File(dir, "env.yml").toPath(),
                    String.join(
                                    "\n",
                                    "elasticore:",
                                    "  enable: true",
                                    "  config:",
                                    "    domainName: crm",
                                    "    framework: springboot",
                                    "    mode: jpa",
                                    "    j2ee: jakarta",
                                    "    apiPath: \"/api\"",
                                    "    publisher:",
                                    "      rules:",
                                    "        ts: [ts-model, com.acme.probe.TsUiRules]",
                                    "  namespace:",
                                    "    base: com.acme.crm",
                                    "")
                            .getBytes(StandardCharsets.UTF_8));
            Files.write(
                    new File(dir, "model/entity.yml").toPath(),
                    String.join(
                                    "\n",
                                    "entity:",
                                    "  Product:",
                                    "    meta: entity @root @editable @expose(20)",
                                    "    fields:",
                                    "      productId: long @id @sequence",
                                    "      name: string(100)!",
                                    "")
                            .getBytes(StandardCharsets.UTF_8));

            String log = run(project);
            File ui = new File(project, "src/main/ts/crmUiModel.ts");
            assertTrue(ui.isFile(), "the domain file was not written:\n" + log);
            assertEquals(
                    "export const domain = \"crm\";",
                    new String(Files.readAllBytes(ui.toPath()), StandardCharsets.UTF_8).trim());
            assertTrue(new File(project, "src/main/ts/apiSupport.ts").isFile(), log);
            assertTrue(
                    new File(project, "src/main/ts/ProductDTO.ts").isFile(), "the per-entity files as before:\n" + log);
        } finally {
            delete(project);
        }
    }

    private static String run(File project) throws Exception {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.ts"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        int status;
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "hexagonal");
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty("elasticore.rules.ts", "true");
            status = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
            java.lang.reflect.Field tmp =
                    io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
            tmp.setAccessible(true);
            tmp.set(null, null);
            SpecFactory.valueObjectsInDto(false);
            SpecFactory.rootsSplitByDefault(false);
            SpecFactory.commandOnlyRoots(false);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        assertEquals(0, status, log);
        return log;
    }

    private static void delete(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) delete(c);
        assertTrue(f.delete() || !f.exists(), "could not delete " + f);
    }
}
