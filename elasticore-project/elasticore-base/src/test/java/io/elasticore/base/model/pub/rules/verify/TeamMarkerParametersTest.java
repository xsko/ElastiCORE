package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.core.Annotation;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A team's own marker mixes key=value pairs and flags: {@code @ui(menu=sales, order=30, staff)}
 * (issue 273). The flag was dropped - the 2.x reading keeps a value only when there is no
 * pair - and a field marker's pairs were reachable only through the internal classes. The
 * rule API now reads both, and the 2.x reading is unchanged.
 */
public class TeamMarkerParametersTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String line) {
            return super.parseFieldLine(name, line, null);
        }
    }

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    public void aFieldMarkersPairsInTheirOrderAndItsFlags() {
        FieldSpec f = new EntitySpec("Account")
                .addField(new TestLoader().parse("name", "string(100) @ui(width=20, title=true, staff, wide) -- 이름"))
                .fields()
                .get(0);
        assertEquals(map("width", "20", "title", "true"), f.dslProperties("ui"));
        assertEquals(Arrays.asList("staff", "wide"), f.dslFlags("ui"));
        assertEquals(Collections.emptyMap(), f.dslProperties("search"));
        assertEquals(Collections.emptyList(), f.dslFlags("ui2"));
    }

    @Test
    public void theTwoXReadingIsUnchanged() {
        Annotation ui = new TestLoader()
                .parse("name", "string(100) @ui(width=20, staff)")
                .getAnnotation("ui");
        assertNull(ui.getValue(), "a key=value annotation has no value, as before");
        assertEquals("20", ui.getProperties().getProperty("width"));
        assertEquals(1, ui.getProperties().size(), "the flag is not a property");
        // Without pairs the value is the value, and there are no flags.
        Annotation plain =
                new TestLoader().parse("name", "string(100) @ui(staff)").getAnnotation("ui");
        assertEquals("staff", plain.getValue());
        assertEquals(Collections.emptyList(), plain.getFlags());
    }

    @Test
    public void aTeamRuleReadsAMetaMarkersFlagsAndAFieldMarker() throws Exception {
        File project = Files.createTempDirectory("elcore-ui-marker").toFile();
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
                                    "      rules: [jpa-springboot-v3, com.acme.probe.UiMarkerRules]",
                                    "  namespace:",
                                    "    base: com.acme.crm",
                                    "")
                            .getBytes(StandardCharsets.UTF_8));
            Files.write(
                    new File(dir, "model/entity.yml").toPath(),
                    String.join(
                                    "\n",
                                    "entity:",
                                    "  Account:",
                                    "    meta: entity @root @expose(20) @ui(menu=sales, order=30, staff)",
                                    "    fields:",
                                    "      accountId: long @id @sequence",
                                    "      name: string(100)! @ui(title=true, wide)",
                                    "")
                            .getBytes(StandardCharsets.UTF_8));
            String log = run(project);
            File out = find(project, "AccountUiMarker.java");
            assertTrue(out != null, "the team's file was not written:\n" + log);
            String text = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
            assertTrue(text.contains("// meta pairs: {menu=sales, order=30}"), text);
            assertTrue(text.contains("// meta flags: [staff]"), text);
            assertTrue(text.contains("// fields: name {title=true} [wide];"), text);
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

    private static String run(File project) throws Exception {
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
            System.setProperty("elasticore.language", "java");
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
