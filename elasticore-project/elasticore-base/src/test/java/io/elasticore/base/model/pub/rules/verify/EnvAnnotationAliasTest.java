package io.elasticore.base.model.pub.rules.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * An annotation named in env.yml (config.annotations) and used as @env:name reaches the v3 code: "@dto:" ones the
 * DTO alone, "@entity:" ones the entity alone. The env guide's masking example is written this way.
 */
public class EnvAnnotationAliasTest {

    private static final String[] KEYS = {
        "elasticore.base.path",
        "elasticore.rules.force",
        "elasticore.merge",
        "elasticore.rules.v2",
        "elasticore.rules.v3",
        "elasticore.domain"
    };

    private final String[] saved = save();
    private final List<File> projects = new ArrayList<File>();

    @AfterEach
    void clean() throws IOException {
        for (int i = 0; i < KEYS.length; i++) {
            if (saved[i] == null) System.clearProperty(KEYS[i]);
            else System.setProperty(KEYS[i], saved[i]);
        }
        for (File p : projects) delete(p.toPath());
    }

    @Test
    public void aMaskNamedInEnvYmlIsOnTheDtoFieldAndNotOnTheEntity() throws Exception {
        File project = project(
                String.join(
                        "\n",
                        "    annotations:",
                        "      maskphone: \"@dto:io.elasticore.springboot3.mask.Mask(io.elasticore.springboot3.mask"
                                + ".MaskType.PHONE)\""),
                String.join(
                        "\n",
                        "entity:",
                        "  Customer:",
                        "    meta: entity @expose(20)",
                        "    fields:",
                        "      customerId: long @id @sequence",
                        "      name: string(50)! @search(like)",
                        "      phone: string(20) @env:maskphone",
                        ""));
        String log = run(project);
        String dto = read(java(project, "CustomerDTO.java"));
        String entity = read(java(project, "Customer.java"));
        String phoneInDto = dto.substring(0, dto.indexOf("String phone;"));
        assertTrue(
                phoneInDto
                        .substring(phoneInDto.lastIndexOf(';'))
                        .contains(
                                "@io.elasticore.springboot3.mask.Mask(io.elasticore.springboot3.mask.MaskType.PHONE)"),
                dto + "\n---\n" + log);
        assertFalse(entity.contains("springboot3.mask"), entity);
    }

    /** The runtime's converter named in env.yml, without @encrypt: the entity field is kept encrypted, the DTO not. */
    @Test
    public void anEncryptConverterNamedInEnvYmlIsOnTheEntityFieldAndNotOnTheDto() throws Exception {
        File project = project(
                String.join(
                        "\n",
                        "    annotations:",
                        "      encrypt: \"@entity:jakarta.persistence.Convert(converter=io.elasticore.springboot3.crypto"
                                + ".EncryptConverter.class)\""),
                String.join(
                        "\n",
                        "entity:",
                        "  Customer:",
                        "    meta: entity @expose(20)",
                        "    fields:",
                        "      customerId: long @id @sequence",
                        "      phone: string(200) @env:encrypt",
                        ""));
        String log = run(project);
        String entity = read(java(project, "Customer.java"));
        String phone = entity.substring(0, entity.indexOf("String phone;"));
        assertTrue(
                phone.substring(phone.lastIndexOf(';'))
                        .contains("Convert(converter=io.elasticore.springboot3.crypto.EncryptConverter.class)"),
                entity + "\n---\n" + log);
        assertFalse(read(java(project, "CustomerDTO.java")).contains("crypto"), log);
    }

    private File project(String config, String model) throws IOException {
        File dir = Files.createTempDirectory("elcore-alias").toFile();
        projects.add(dir);
        File blueprint = new File(dir, "src/main/resources/blueprint/dgshop");
        assertTrue(new File(blueprint, "model").mkdirs());
        String env = String.join(
                "\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: dgshop",
                "    framework: springboot",
                "    mode: jpa",
                "    j2ee: jakarta",
                config,
                "  namespace:",
                "    base: io.acme.dgshop",
                "");
        Files.write(new File(blueprint, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private static String run(File project) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        int code;
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.rules.v3", "true");
            code = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        assertEquals(0, code, log);
        return log;
    }

    private static Path java(File project, String name) throws IOException {
        try (Stream<Path> all = Files.walk(new File(project, "src/main/java").toPath())) {
            Path p = all.filter(x -> x.getFileName().toString().equals(name))
                    .findFirst()
                    .orElse(null);
            assertNotNull(p, name + " was not written");
            return p;
        }
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    private static String[] save() {
        String[] s = new String[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) s[i] = System.getProperty(KEYS[i]);
        return s;
    }

    private static void delete(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> all = Files.walk(root)) {
            List<Path> paths = new ArrayList<Path>();
            all.forEach(paths::add);
            for (int i = paths.size() - 1; i >= 0; i--) Files.deleteIfExists(paths.get(i));
        }
    }
}
