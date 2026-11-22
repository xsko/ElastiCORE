package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a screen needs around labels (issue 268), generated: an enumeration's
 * label in other languages as label_en, label_ja columns - in the TypeScript
 * client and in the label bundle - and the type's label as XxxMeta. A model
 * with neither writes what it did.
 */
public class ScreenLabelsGenerationTest {

    /** Translations only in the enumeration's columns: no @label(en=...) anywhere. */
    private static final String COLUMNS = String.join(
            "\n",
            "enumeration:",
            "  PetStatus:",
            "    meta: enum @db(code) @json(code) -- 상태",
            "    fields:",
            "      code: string(1) @code",
            "      label: string(20)",
            "      label_en: string(20)",
            "      label_ja: string(20)",
            "      label_zh_TW: string(20)",
            "      label_long: string(40)",
            "    enum:",
            "      AVAILABLE: A, 판매중, Available, 販売中, 販售中, for sale now",
            "      SOLD: S, 판매완료, Sold",
            "entity:",
            "  Pet:",
            "    meta: entity @root @expose(20) -- 반려동물",
            "    fields:",
            "      petId:  long @id @sequence",
            "      name:   string(30)! -- 이름",
            "      status: PetStatus",
            "");

    /** The type's label: the entity's in two languages, a DTO with its own and one without. */
    private static final String TYPE_LABELS = String.join(
            "\n",
            "entity:",
            "  Pet:",
            "    meta: entity @root @expose(20) @label(en=Pet, ja=ペット) -- 반려동물",
            "    fields:",
            "      petId:  long @id @sequence",
            "      name:   string(30)! -- 이름",
            "dto:",
            "  PetCard:",
            "    meta: dto @template(Pet) -- 반려동물 카드",
            "    fields:",
            "      note: string(100)",
            "  PetView:",
            "    meta: dto @template(Pet)",
            "    fields:",
            "      note: string(100)",
            "");

    private static final String PLAIN = String.join(
            "\n",
            "enumeration:",
            "  PetKind:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(1) @code",
            "      name: string(20)",
            "    enum:",
            "      DOG: D, Dog",
            "      CAT: C, Cat",
            "entity:",
            "  Pet:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      petId: long @id @sequence",
            "      name:  string(30)!",
            "      kind:  PetKind",
            "");

    @Test
    public void theColumnsAreTheBundleOfAModelWithNoOtherTranslation() throws Exception {
        File project = Files.createTempDirectory("elcore-enum-labels").toFile();
        try {
            // A column of its own: a warning is said once per run of the JVM, and the other tests warn of label_long.
            String log = generateInto(project, COLUMNS.replace("label_long", "label_remark"), "java", false);
            Properties base = bundle(project, "desk-labels.properties");
            assertEquals("판매중", base.getProperty("PetStatus.AVAILABLE"));
            assertEquals("판매완료", base.getProperty("PetStatus.SOLD"));
            assertEquals("상태", base.getProperty("PetStatus"));
            assertEquals("이름", base.getProperty("Pet.name"));

            Properties en = bundle(project, "desk-labels_en.properties");
            assertEquals("Available", en.getProperty("PetStatus.AVAILABLE"));
            assertEquals("Sold", en.getProperty("PetStatus.SOLD"));
            assertNull(en.getProperty("Pet.name"), "no translation: the default file answers");

            Properties ja = bundle(project, "desk-labels_ja.properties");
            assertEquals("販売中", ja.getProperty("PetStatus.AVAILABLE"));
            assertNull(ja.getProperty("PetStatus.SOLD"), "a line that leaves the column out has no translation");
            assertEquals("販售中", bundle(project, "desk-labels_zh_TW.properties").getProperty("PetStatus.AVAILABLE"));
            assertNull(find(project, "desk-labels_remark.properties"), "label_remark is not a language");

            String header = new String(
                    Files.readAllBytes(find(project, "desk-labels.properties").toPath()), StandardCharsets.UTF_8);
            assertTrue(header.contains("Enum.CONSTANT for a constant's"), header);

            String said = "[desk] enum PetStatus: column 'label_remark' is not a translation of 'label'"
                    + " - 'remark' is not an ISO 639-1 language";
            assertTrue(log.contains(said), log);
            assertEquals(log.indexOf(said), log.lastIndexOf(said), "said once:\n" + log);

            // The columns are the enum's properties, as any column of an enumeration.
            String javaEnum = read(find(project, "PetStatus.java"));
            assertTrue(javaEnum.contains("label_en"), javaEnum);
        } finally {
            delete(project);
        }
    }

    @Test
    public void theColumnsAreTheTypeScriptLabelsOfTheEnumeration() throws Exception {
        File project = Files.createTempDirectory("elcore-enum-labels-ts").toFile();
        try {
            generateInto(project, COLUMNS, "java", true);
            String ts = read(find(project, "PetStatus.ts"));
            assertTrue(ts.contains("[PetStatus.AVAILABLE]: \"판매중\","), "XxxLabel is unchanged:\n" + ts);
            assertTrue(ts.contains("export const PetStatusLabels: Record<PetStatus, Record<string, string>> = {"), ts);
            assertTrue(
                    ts.contains("[PetStatus.AVAILABLE]: { en: \"Available\", ja: \"販売中\", \"zh-TW\": \"販售中\" },"), ts);
            assertTrue(ts.contains("[PetStatus.SOLD]: { en: \"Sold\" },"), ts);
            assertFalse(ts.contains("long"), ts);

            String dto = read(find(project, "PetDTO.ts"));
            assertTrue(
                    dto.contains("export const PetMeta: { label: string; labels: Record<string, string> } = "
                            + "{ label: \"반려동물\", labels: {} };"),
                    dto);
            assertTrue(dto.contains("export function validatePetCodes("), dto);
            assertTrue(dto.contains("errors.push({ field: \"name\", code: \"NotNull\", values: {} });"), dto);
            assertTrue(dto.contains("errors.push({ field: \"name\", code: \"Size\", values: { max: 30 } });"), dto);
        } finally {
            delete(project);
        }
    }

    @Test
    public void aDtoWithoutALabelIsShownByItsEntitys() throws Exception {
        File project = Files.createTempDirectory("elcore-type-labels-ts").toFile();
        try {
            generateInto(project, TYPE_LABELS, "java", true);
            String meta = "export const %sMeta: { label: string; labels: Record<string, string> } = %s;";
            String pet = read(find(project, "PetDTO.ts"));
            assertTrue(
                    pet.contains(
                            String.format(meta, "Pet", "{ label: \"반려동물\", labels: { en: \"Pet\", ja: \"ペット\" } }")),
                    pet);
            String card = read(find(project, "PetCard.ts"));
            assertTrue(card.contains(String.format(meta, "PetCard", "{ label: \"반려동물 카드\", labels: {} }")), card);
            String view = read(find(project, "PetView.ts"));
            assertTrue(
                    view.contains(String.format(
                            meta, "PetView", "{ label: \"반려동물\", labels: { en: \"Pet\", ja: \"ペット\" } }")),
                    view);
        } finally {
            delete(project);
        }
    }

    @Test
    public void aModelWithNeitherWritesNoBundleNoMetaAndNoLabels() throws Exception {
        File project = Files.createTempDirectory("elcore-enum-labels-plain").toFile();
        try {
            generateInto(project, PLAIN, "java", false);
            assertNull(find(project, "desk-labels.properties"));
            generateInto(project, PLAIN, "java", true);
            String ts = read(find(project, "PetKind.ts"));
            assertTrue(ts.contains("export const PetKindLabel"), ts);
            assertFalse(ts.contains("PetKindLabels"), ts);
            String dto = read(find(project, "PetDTO.ts"));
            assertFalse(dto.contains("PetMeta"), dto);
            assertTrue(dto.contains("export function validatePetCodes("), "the codes beside validatePet:\n" + dto);
        } finally {
            delete(project);
        }
    }

    /** Columns that only look like translations, in a model that never asked for one. */
    private static final String LOOK_ALIKE = String.join(
            "\n",
            "enumeration:",
            "  PetSize:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code:       string(1) @code",
            "      name:       string(20)",
            "      name_no:    int",
            "      name_kor:   string(20)",
            "      name_short: string(10)",
            "      name_kana:  string(20)",
            "    enum:",
            "      SMALL: S, 소형, 1, 소, S, ショウ",
            "      LARGE: L, 대형, 2, 대, L, ダイ",
            "entity:",
            "  Pet:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      petId: long @id @sequence",
            "      size:  PetSize",
            "");

    @Test
    public void columnsThatOnlyLookLikeTranslationsAreOrdinaryAndSaidNothingOf() throws Exception {
        File project = Files.createTempDirectory("elcore-enum-look-alike").toFile();
        try {
            String log = generateInto(project, LOOK_ALIKE, "java", false);
            assertNull(find(project, "desk-labels.properties"), "no translation: no bundle");
            assertFalse(log.contains("enum PetSize:"), "nothing to warn of without a translation:\n" + log);
            generateInto(project, LOOK_ALIKE, "java", true);
            String ts = read(find(project, "PetSize.ts"));
            assertFalse(ts.contains("PetSizeLabels"), ts);
        } finally {
            delete(project);
        }
    }

    /** A field of a type named PetMeta: the file imports the name, so it declares no const of it. */
    private static final String META_TAKEN = String.join(
            "\n",
            "enumeration:",
            "  PetMeta:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(1) @code",
            "      name: string(20)",
            "    enum:",
            "      NEW: N, New",
            "entity:",
            "  Pet:",
            "    meta: entity @root @expose(20) -- 반려동물",
            "    fields:",
            "      petId: long @id @sequence",
            "      meta:  PetMeta",
            "");

    @Test
    public void aMetaNameTheFileImportsIsNotDeclaredAgain() throws Exception {
        File project = Files.createTempDirectory("elcore-meta-taken").toFile();
        try {
            generateInto(project, META_TAKEN, "java", true);
            String dto = read(find(project, "PetDTO.ts"));
            assertTrue(dto.contains("from './PetMeta'"), dto);
            assertFalse(dto.contains("export const PetMeta"), dto);
            assertTrue(dto.contains("export const PetFields"), dto);
        } finally {
            delete(project);
        }
    }

    @Test
    public void kotlinTakesTheColumnsAsProperties() throws Exception {
        File project = Files.createTempDirectory("elcore-enum-labels-kt").toFile();
        try {
            generateInto(project, COLUMNS, "kotlin", false);
            String kt = read(find(project, "PetStatus.kt"));
            assertTrue(kt.contains("label_en"), kt);
            assertEquals(
                    "Available", bundle(project, "desk-labels_en.properties").getProperty("PetStatus.AVAILABLE"));
        } finally {
            delete(project);
        }
    }

    private static String read(File f) throws Exception {
        assertTrue(f != null, "not written");
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static Properties bundle(File project, String name) throws Exception {
        File f = find(project, name);
        assertTrue(f != null, name + " was not written");
        Properties p = new Properties();
        InputStream in = new FileInputStream(f);
        try {
            p.load(in);
        } finally {
            in.close();
        }
        return p;
    }

    private static File find(File dir, String name) {
        List<File> todo = new ArrayList<File>();
        todo.add(dir);
        while (!todo.isEmpty()) {
            File d = todo.remove(0);
            File[] children = d.listFiles();
            if (children == null) continue;
            for (File c : children) {
                if (c.isDirectory()) todo.add(c);
                else if (c.getName().equals(name)) return c;
            }
        }
        return null;
    }

    private static String generateInto(File project, String model, String language, boolean typescript)
            throws Exception {
        File dir = new File(project, "src/main/resources/blueprint/desk");
        assertTrue(new File(dir, "model").isDirectory() || new File(dir, "model").mkdirs());
        String env = String.join(
                "\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: desk",
                "    framework: springboot",
                "    mode: jpa",
                "    j2ee: jakarta",
                "  namespace:",
                "    base: com.acme.desk",
                "");
        Files.write(new File(dir, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));

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
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "layered");
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            if (typescript) System.setProperty("elasticore.rules.ts", "true");
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
