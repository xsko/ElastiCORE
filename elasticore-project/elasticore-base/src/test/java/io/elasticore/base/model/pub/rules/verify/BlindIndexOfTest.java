package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blind index the generator adds beside an {@code @encrypt @search(eq)} field is a field of the entity like any
 * other in {@code fields()}; {@code FieldSpec.blindIndexOf()} tells a team rule which one it is, and of what (issue
 * 290).
 */
public class BlindIndexOfTest {

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Account:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      accountId: long @id @sequence",
            "      name:  string(100)! @search(like)",
            "      email: string(100) @encrypt @s(eq) -- 이메일",
            "      phone: string(20) @encrypt(digits) @search(eq)",
            "      memo:  text @encrypt",
            "");

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String line) {
            return super.parseFieldLine(name, line, null);
        }
    }

    @Test
    public void aDeclaredFieldIndexesNothing() {
        FieldSpec f = new EntitySpec("Account")
                .addField(new TestLoader().parse("email", "string(100) @encrypt @s(eq) -- 이메일"))
                .fields()
                .get(0);
        assertNull(f.blindIndexOf());
    }

    @Test
    public void javaRuleSeesTheIndexesAndLeavesThemOut() throws Exception {
        checkScreen("java", "jpa-springboot-v3", "AccountScreen.java");
    }

    @Test
    public void kotlinRuleSeesTheIndexesAndLeavesThemOut() throws Exception {
        checkScreen("kotlin", "jpa-springboot-kt", "AccountScreen.kt");
    }

    private static void checkScreen(String language, String bundle, String file) throws Exception {
        File project = Files.createTempDirectory("elcore-bidx-" + language).toFile();
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
                                    "    publisher:",
                                    "      rules: [" + bundle + ", com.acme.probe.ScreenFieldRules]",
                                    "  namespace:",
                                    "    base: com.acme.crm",
                                    "")
                            .getBytes(StandardCharsets.UTF_8));
            Files.write(new File(dir, "model/entity.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
            String log = run(project, language);
            File out = find(project, file);
            assertTrue(out != null, "the team's file was not written:\n" + log);
            String text = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
            // fields() still holds the indexes - each names the field it indexes, @encrypt(digits) one too.
            assertTrue(text.contains("// indexes: emailBidx->email phoneBidx->phone\n"), text);
            // The declared fields answer null, so the screen lists exactly them.
            assertTrue(text.contains("// shown: accountId name email phone memo\n"), text);
            assertTrue(text.contains("// dto shown: accountId name email phone memo\n"), text);
            assertFalse(text.contains("memoBidx"), text);
        } finally {
            delete(project);
        }
    }

    private static File find(File dir, String name) {
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File c : children) {
            if (c.isDirectory()) {
                File f = find(c, name);
                if (f != null) return f;
            } else if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    private static String run(File project, String language) throws Exception {
        String[] keys = {
            "elasticore.base.path", "elasticore.architecture", "elasticore.language", "elasticore.rules.force"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        int status;
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "layered");
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
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
