package io.elasticore.base.model.pub.rules.verify;

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
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A key DTO is its DTO with only the key fields: it imports what those use,
 * not the enums and the List of the fields it left out (issue 231). The DTO
 * itself keeps them.
 */
public class KeyDtoImportsTest {

    private static final String MODEL = String.join(
            "\n",
            "enumeration:",
            "  MemberLevel:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(1) @code",
            "      label: string(20)",
            "    enum:",
            "      BASIC: B, Basic",
            "      GOLD: G, Gold",
            "  MemberStatus:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(1) @code",
            "      label: string(20)",
            "    enum:",
            "      ACTIVE: A, Active",
            "      WITHDRAWN: W, Withdrawn",
            "",
            "entity:",
            "  Member:",
            "    meta: entity @root @expose",
            "    fields:",
            "      memberId: long @id @sequence",
            "      email: string(100) @email",
            "      grade: MemberLevel",
            "      status: MemberStatus",
            "",
            "dto:",
            "  MemberDTO:",
            "    meta: dto @template(Member)",
            "    fields:",
            "      tagIds: List<Long>",
            "");

    private final List<File> projects = new ArrayList<File>();

    @Test
    public void aJavaKeyDtoImportsOnlyWhatItsKeyUses() throws Exception {
        for (String architecture : new String[] {"layered", "ddd", "hexagonal"}) {
            File project = project();
            assertEquals(
                    0,
                    run(project, architecture, "java", "layered".equals(architecture) ? "elasticore.rules.v3" : null));
            String key = found(project, "MemberKeyDTO.java");
            assertTrue(key.contains(" memberId;"), architecture + ":\n" + key);
            for (String unused : new String[] {"MemberLevel", "MemberStatus", "java.util.List", "Email"}) {
                assertFalse(key.contains(unused), architecture + ": " + unused + " in\n" + key);
            }
            // The DTO itself still has them all.
            String dto = found(project, "MemberDTO.java");
            for (String used : new String[] {"MemberLevel;", "MemberStatus;", "import java.util.List;"}) {
                assertTrue(dto.contains(used), architecture + ": " + used + " missing from\n" + dto);
            }
        }
    }

    @Test
    public void aKotlinKeyDtoImportsOnlyWhatItsKeyUses() throws Exception {
        for (String architecture : new String[] {"layered", "ddd", "hexagonal"}) {
            File project = project();
            assertEquals(0, run(project, architecture, "kotlin", null));
            String key = found(project, "MemberKeyDTO.kt");
            assertTrue(key.contains("memberId"), architecture + ":\n" + key);
            for (String unused : new String[] {"MemberLevel", "MemberStatus", "Email"}) {
                assertFalse(key.contains(unused), architecture + ": " + unused + " in\n" + key);
            }
            String dto = found(project, "MemberDTO.kt");
            for (String used : new String[] {"MemberLevel", "MemberStatus"}) {
                assertTrue(
                        dto.contains("import ") && dto.contains("." + used + "\n"),
                        architecture + ": " + used + " missing from\n" + dto);
            }
        }
    }

    private File project() throws IOException {
        File project = Files.createTempDirectory("elcore-keydto").toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/shop");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: shop",
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    base: io.acme.shop",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
        return project;
    }

    private static int run(File project, String architecture, String language, String output) {
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
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
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
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
