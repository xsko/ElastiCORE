package io.elasticore.base.model.pub.ossie;

import io.elasticore.base.model.pub.rules.verify.RulesGenRunner;
import io.elasticore.base.model.pub.rules.verify.SpecFactory;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A unique key names the dataset's columns. An @Index column list may name a
 * field by its property (seatNo) or by its column (hall_id), as Hibernate
 * reads either; the key is written in columns, the names its fields have.
 */
public class OssieUniqueKeysTest {

    private static File project;
    private static String yaml;

    private static final String ENV = String.join(
            "\n",
            "elasticore:",
            "  enable: true",
            "  config:",
            "    domainName: venue",
            "    framework: springboot",
            "    mode: jpa",
            "    j2ee: jakarta",
            "    publisher:",
            "      rules: [hexagonal-springboot, ossie-export]",
            "  namespace:",
            "    base: com.acme.venue",
            "");

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Seat:",
            "    meta: entity @root @expose(20) @Index(name=\"idx_seat_u\", columnList=\"hall_id,seatNo\", unique=true)",
            "    fields:",
            "      seatId: long @id @sequence",
            "      hallId: long!",
            "      seatNo: string(10)!",
            "",
            "  Box:",
            "    meta: entity @root @expose(20) @Index(name=\"idx_box_u\", columnList=\"BOX_ROW,boxNo\", unique=true)",
            "          @Index(name=\"idx_box_no\", columnList=\"box_no\", unique=true)",
            "    fields:",
            "      boxId: long @id @sequence",
            "      boxRow: long! @column(BOX_ROW)",
            "      boxNo: string(10)! @column(BOX_NO) @unique",
            "");

    @BeforeAll
    static void generate() throws Exception {
        project = Files.createTempDirectory("elcore-ossie-unique").toFile();
        File dir = new File(project, "src/main/resources/blueprint/venue");
        assertTrue(new File(dir, "model").mkdirs());
        Files.write(new File(dir, "env.yml").toPath(), ENV.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));

        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.publisher.rules"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "hexagonal");
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.force", "true");
            System.setProperty("elasticore.publisher.rules", "ossie-export");
            RulesGenRunner.main(new String[0]);
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
            resetStatics();
        }
        File exported = new File(project, "src/main/resources/blueprint/" + OssieSemanticModelExporter.FILE_NAME);
        assertTrue(exported.isFile(), "no semantic model exported under " + project);
        yaml = new String(Files.readAllBytes(exported.toPath()), StandardCharsets.UTF_8);
    }

    private static void resetStatics() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
    }

    @AfterAll
    static void clean() throws IOException {
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

    @Test
    public void aPropertyInTheColumnListIsWrittenAsItsColumn() {
        assertTrue(yaml.contains("    unique_keys:\n      - [hall_id, seat_no]\n"), yaml);
    }

    @Test
    public void everyColumnOfTheKeyIsAFieldOfTheDataset() {
        assertTrue(yaml.contains("      - name: hall_id\n") && yaml.contains("      - name: seat_no\n"), yaml);
        assertTrue(!yaml.contains("seatNo"), "the property name was written as a column:\n" + yaml);
    }

    @Test
    public void aColumnWrittenByColumnKeepsItsCaseAndAKeyIsWrittenOnce() {
        // The Box dataset: from its name to its entity extension.
        int end = yaml.indexOf("\"entity\": \"Box\"");
        assertTrue(end > 0, yaml);
        String box = yaml.substring(yaml.lastIndexOf("\n  - name: ", end), end);
        assertTrue(box.contains("      - name: BOX_ROW\n") && box.contains("      - name: BOX_NO\n"), box);
        assertTrue(box.contains("      - [BOX_ROW, BOX_NO]\n"), box);
        // @unique on boxNo and the index on box_no are one key.
        assertTrue(
                box.indexOf("      - [BOX_NO]\n") >= 0
                        && box.indexOf("      - [BOX_NO]\n") == box.lastIndexOf("      - [BOX_NO]\n"),
                box);
        assertTrue(!box.toLowerCase().contains("[box_row") || box.contains("[BOX_ROW"), box);
    }
}
