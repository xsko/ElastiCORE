package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Spring property placeholder in generated Kotlin is escaped, since Kotlin
 * reads "${...}" as a string template of its own: the gRPC client's
 * @Value("${grpc.x.ip:localhost}") did not parse. The template modules leave
 * the gRPC output out of their compilation, so this test reads every
 * generated Kotlin file of a domain with a proto port for a placeholder
 * Kotlin would take as its own.
 */
public class KotlinSpringPlaceholderTest {

    private static final String[] KEYS = {
        "elasticore.base.path",
        "elasticore.architecture",
        "elasticore.language",
        "elasticore.rules.force",
        "elasticore.rules.v2",
        "elasticore.rules.v3",
        "elasticore.rules.ts",
        "elasticore.rules.kt",
        "elasticore.publisher.rules.add",
        "elasticore.rules.list"
    };

    private static final String MODEL = String.join(
            "\n",
            "entity:",
            "  Note:",
            "    meta: entity @expose(20)",
            "    fields:",
            "      noteId: long @id @sequence",
            "      title: string(100)! @search(like)",
            "");

    private static final String PORT = String.join(
            "\n",
            "port:",
            "  SyncGrpcService:",
            "    meta: proto @server @client",
            "    methods:",
            "      getSyncStatus:",
            "        meta: method",
            "        params:",
            "          request: SyncStatusRequest",
            "        return: SyncStatusReply",
            "",
            "dto:",
            "  SyncStatusRequest:",
            "    meta: dto",
            "    fields:",
            "      recordId: string",
            "",
            "  SyncStatusReply:",
            "    meta: dto",
            "    fields:",
            "      recordId: string",
            "      status: string",
            "");

    /** "${name:default}" not preceded by a backslash: a Spring placeholder Kotlin would read as a template. */
    private static final Pattern UNESCAPED = Pattern.compile("(?<!\\\\)\\$\\{[\\w.-]+:[^}]*}");

    private final List<File> projects = new ArrayList<File>();
    private final String[] saved = save();

    @AfterEach
    void clean() throws IOException {
        for (int i = 0; i < KEYS.length; i++) {
            if (saved[i] == null) System.clearProperty(KEYS[i]);
            else System.setProperty(KEYS[i], saved[i]);
        }
        for (File p : projects) delete(p.toPath());
    }

    @Test
    public void theKotlinGrpcClientEscapesItsPlaceholders() throws Exception {
        File project = project();
        System.setProperty("elasticore.language", "kotlin");
        String log = run(project);
        Path client = find(project, "SyncGrpcServiceClient.kt");
        assertNotNull(client, log);
        String text = read(client);
        assertTrue(text.contains("@Value(\"\\${grpc.syncGrpcService.ip:localhost}\") grpcSvcIp: String"), text);
        assertTrue(text.contains("@Value(\"\\${grpc.syncGrpcService.port:9090}\") grpcPort: Int"), text);
        assertNotNull(find(project, "SyncGrpcServiceProvider.kt"), log);
    }

    @Test
    public void noGeneratedKotlinFileHoldsAPlaceholderKotlinWouldRead() throws Exception {
        File project = project();
        System.setProperty("elasticore.language", "kotlin");
        System.setProperty("elasticore.architecture", "hexagonal");
        String log = run(project);
        List<Path> kotlin = all(project, ".kt");
        assertFalse(kotlin.isEmpty(), log);
        List<String> found = new ArrayList<String>();
        for (Path p : kotlin) {
            Matcher m = UNESCAPED.matcher(read(p));
            while (m.find()) found.add(p.getFileName() + ": " + m.group());
        }
        assertTrue(found.isEmpty(), String.join("\n", found));
    }

    @Test
    public void theJavaGrpcClientIsAsBefore() throws Exception {
        File project = project();
        System.setProperty("elasticore.language", "java");
        String log = run(project);
        Path client = find(project, "SyncGrpcServiceClient.java");
        assertNotNull(client, log);
        String text = read(client);
        assertTrue(text.contains("(@Value(\"${grpc.syncGrpcService.ip:localhost}\") String grpcSvcIp"), text);
        assertTrue(text.contains(",@Value(\"${grpc.syncGrpcService.port:9090}\") int grpcPort)"), text);
        assertFalse(text.contains("\\$"), text);
    }

    // ---- helpers ----------------------------------------------------------------------

    private String run(File project) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        int code;
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.rules.force", "true");
            code = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        assertEquals(0, code, log);
        return log;
    }

    private File project() throws IOException {
        File dir = Files.createTempDirectory("elcore-kt-grpc").toFile();
        projects.add(dir);
        File blueprint = new File(dir, "src/main/resources/blueprint/dgshop");
        assertTrue(new File(blueprint, "model").mkdirs());
        assertTrue(new File(blueprint, "port").mkdirs());
        String env = String.join(
                "\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: dgshop",
                "    framework: springboot",
                "    mode: jpa",
                "    j2ee: jakarta",
                "  namespace:",
                "    base: io.acme.dgshop",
                "    proto: io.acme.dgshop.grpc",
                "");
        Files.write(new File(blueprint, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "port/grpc_port.yml").toPath(), PORT.getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    private static Path find(File project, String name) throws IOException {
        try (Stream<Path> all = Files.walk(new File(project, "src/main").toPath())) {
            return all.filter(p -> p.getFileName().toString().equals(name))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static List<Path> all(File project, String extension) throws IOException {
        try (Stream<Path> all = Files.walk(new File(project, "src/main").toPath())) {
            return all.filter(p -> p.toString().endsWith(extension)).collect(Collectors.toList());
        }
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
