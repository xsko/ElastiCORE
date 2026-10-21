package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A server run leaves src/main/ts as it was - only elcore3ts writes it - so a
 * generated client there is said to be stale, naming the task and the setting.
 */
public class StaleClientTest {

    @TempDir
    Path project;

    @Test
    public void aGeneratedClientThisRunDidNotWriteIsSaid() throws Exception {
        assertNull(RulesGenRunner.staleClient(Collections.singletonList(project.toString())), "no client at all");

        File ts = new File(project.toFile(), "src/main/ts");
        assertTrue(ts.mkdirs());
        // The developer's own TypeScript is not the generator's business.
        Files.write(new File(ts, "app.ts").toPath(), "export const x = 1;\n".getBytes(StandardCharsets.UTF_8));
        assertNull(RulesGenRunner.staleClient(Collections.singletonList(project.toString())));

        Files.write(new File(ts, "ItemDTO.ts").toPath(),
                "//ecd:-1H20261022_V3.0\nexport interface ItemDTO {}\n".getBytes(StandardCharsets.UTF_8));
        String said = RulesGenRunner.staleClient(Collections.singletonList(project.toString()));
        assertTrue(said != null && said.contains("elcore3ts") && said.contains("typescriptClient = true"), said);
    }

    /** A run of the whole generator says it once, after its summary, unless the build writes the client too. */
    @Test
    public void aServerRunSaysItAndABuildThatWritesTheClientDoesNot() throws Exception {
        File blueprint = new File(project.toFile(), "src/main/resources/blueprint/stshop");
        assertTrue(new File(blueprint, "model").mkdirs());
        Files.write(new File(blueprint, "env.yml").toPath(), String.join("\n",
                "elasticore:", "  enable: true", "  config:", "    domainName: stshop", "    framework: springboot",
                "    mode: jpa", "    j2ee: jakarta", "  namespace:", "    base: io.acme.stshop", "").getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), String.join("\n",
                "entity:", "  StItem:", "    meta: entity @root @editable @expose(20)", "    fields:",
                "      itemId: long @id @sequence", "      name: string(20)", "").getBytes(StandardCharsets.UTF_8));

        run(true, null);
        // Made from the model as it is: nothing to say, whichever task the build ran elcore3ts from.
        String fresh = run(false, null);
        assertTrue(!fresh.contains("older model"), fresh);

        // The model changed and the client was not made again.
        Files.write(new File(blueprint, "model/model.yml").toPath(), String.join("\n",
                "entity:", "  StItem:", "    meta: entity @root @editable @expose(20)", "    fields:",
                "      itemId: long @id @sequence", "      name: string(20)", "      note: string(40)", "")
                .getBytes(StandardCharsets.UTF_8));
        String server = run(false, null);
        assertTrue(server.contains("holds a generated TypeScript client made from an older model"), server);
        String withBuild = run(false, "true");
        assertTrue(!withBuild.contains("older model"), withBuild);
        String client = run(true, null);
        assertTrue(!client.contains("older model"), client);
        String after = run(false, null);
        assertTrue(!after.contains("older model"), after);
    }

    /** Line endings alone do not make a model older; a changed file does. */
    @Test
    public void theDigestReadsTheDomainModelsOnly() throws Exception {
        File domain = new File(project.toFile(), "src/main/resources/blueprint/stshop");
        assertTrue(domain.mkdirs());
        File model = new File(domain, "model.yml");
        Files.write(model.toPath(), "entity:\n  A:\n".getBytes(StandardCharsets.UTF_8));
        String digest = RulesGenRunner.modelDigest(project.toString());
        Files.write(model.toPath(), "entity:\r\n  A:\r\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(digest.equals(RulesGenRunner.modelDigest(project.toString())));
        // What the generator writes beside the domains is not the model.
        Files.write(new File(domain.getParentFile(), "schema.yml").toPath(), "x: 1\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(digest.equals(RulesGenRunner.modelDigest(project.toString())));
        Files.write(model.toPath(), "entity:\n  B:\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(!digest.equals(RulesGenRunner.modelDigest(project.toString())));
    }

    /** What a generation leaves in statics, as SignInAndReferencesTest clears it. */
    @org.junit.jupiter.api.AfterEach
    void clean() throws Exception {
        java.lang.reflect.Field tmp = io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
    }

    private String run(boolean typescript, String withBuild) throws Exception {
        String[] keys = {"elasticore.base.path", "elasticore.architecture", "elasticore.language",
                "elasticore.rules.force", "elasticore.rules.ts", "elasticore.rules.tsWithBuild"};
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        java.io.PrintStream out = System.out;
        java.io.PrintStream err = System.err;
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        java.io.PrintStream capture = new java.io.PrintStream(bytes, true, "UTF-8");
        System.setOut(capture);
        System.setErr(capture);
        try {
            System.setProperty("elasticore.base.path", project.toString());
            System.setProperty("elasticore.architecture", "hexagonal");
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.force", "true");
            if (typescript) System.setProperty("elasticore.rules.ts", "true");
            if (withBuild != null) System.setProperty("elasticore.rules.tsWithBuild", withBuild);
            RulesGenRunner.main(new String[0]);
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
            System.setOut(out);
            System.setErr(err);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }
}
