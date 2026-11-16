package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * schema.yml names the columns the generated entity maps. A reference's
 * {@code @foreignKey} is the column its {@code @JoinColumn} names - with
 * {@code referenceColumn}, with {@code @column}, or JPA's default - and a field
 * whose type is a value class (BigDecimal, Instant, Money) is a column, not an
 * entity relationship.
 */
public class SchemaColumnsTest {

    private static final String MODEL = "entity:\n"
            + "  Member:\n"
            + "    meta: entity @root @expose(20)\n"
            + "    fields:\n"
            + "      memberId: long @id @sequence\n"
            + "      nickname: string(30)!\n"
            + "  Post:\n"
            + "    meta: entity @root @expose(20)\n"
            + "    fields:\n"
            + "      postId: long @id @sequence\n"
            + "      title: string(200)!\n"
            + "      author: Member!\n"
            + "      editor: Member @column(last_editor_id)\n"
            + "      parent: Post\n"
            + "      price: money(10.2)\n"
            + "      rate: decimal(5.2) @default(0)\n"
            + "      deletedAt: timestamp @deleted\n"
            + "      topic: Topic\n"
            + "      tags: List<Tag>\n"
            + "      place: Place @embedded\n"
            + "      comments: List<Reply> @OneToMany(mappedBy=\"post\")\n"
            + "  Reply:\n"
            + "    meta: entity\n"
            + "    fields:\n"
            + "      replyId: long @id @sequence\n"
            + "      post: Post!\n"
            + "      body: string(500)\n"
            + "      removedAt: timestamp @deleted\n"
            + "  Topic:\n"
            + "    meta: entity @root\n"
            + "    fields:\n"
            + "      id: long @id @sequence @column(topic_no)\n"
            + "      name: string(50)\n"
            + "  Place:\n"
            + "    meta: entity @embeddable\n"
            + "    fields:\n"
            + "      city: string(50)\n"
            + "\n"
            + "enumeration:\n"
            + "  Tag:\n"
            + "    meta: enum @db(code)\n"
            + "    fields:\n"
            + "      code: string(2)\n"
            + "    enum:\n"
            + "      NEWS: N\n"
            + "      HELP: H\n";

    @TempDir
    Path dir;

    private static final String[] KEYS = {"elasticore.base.path", "elasticore.rules.force"};
    private final String[] saved = {System.getProperty(KEYS[0]), System.getProperty(KEYS[1])};

    @AfterEach
    void restore() {
        for (int i = 0; i < KEYS.length; i++) {
            if (saved[i] == null) System.clearProperty(KEYS[i]);
            else System.setProperty(KEYS[i], saved[i]);
        }
    }

    @Test
    public void hexagonalWithReferenceColumnField() throws Exception {
        String schema = generate("hexagonal-springboot", "    referenceColumn: field\n");

        assertTrue(schema.contains("author: Member @foreignKey(author_id) -- Member entity relationship"), schema);
        assertTrue(schema.contains("editor: Member @foreignKey(last_editor_id) -- Member entity relationship"), schema);
        assertTrue(schema.contains("parent: Post @foreignKey(parent_id) -- Post entity relationship"), schema);
        assertValueColumns(schema);
        // The columns the entity itself maps.
        String entity = read(find("PostBase.java", "Post.java"));
        assertTrue(
                entity.contains("\"author_id\"")
                        && entity.contains("\"last_editor_id\"")
                        && entity.contains("\"parent_id\""),
                entity);
    }

    @Test
    public void layeredWithJpaDefaults() throws Exception {
        String schema = generate(null, "");

        // No @JoinColumn name: JPA's default, the field and the target key's column.
        assertTrue(
                schema.contains("author: Member @foreignKey(author_member_id) -- Member entity relationship"), schema);
        assertTrue(schema.contains("editor: Member @foreignKey(last_editor_id) -- Member entity relationship"), schema);
        assertTrue(schema.contains("parent: Post @foreignKey(parent_post_id) -- Post entity relationship"), schema);
        // The target key's own column, as JPA joins on it.
        assertTrue(schema.contains("topic: Topic @foreignKey(topic_topic_no) -- Topic entity relationship"), schema);
        assertValueColumns(schema);
        String entity = read(find("Post.java"));
        assertTrue(entity.contains("@JoinColumn(name = \"last_editor_id\""), entity);
    }

    private static void assertValueColumns(String schema) {
        assertTrue(schema.contains("price: io.elasticore.springboot3.domain.Money @Column(name = \"price\")"), schema);
        assertTrue(schema.contains("rate: BigDecimal @Column(name = \"rate\")"), schema);
        assertTrue(schema.contains("deletedAt: java.time.Instant @Column(name = \"deleted_at\")"), schema);
        assertFalse(schema.contains("mappedBy=\"post_id\""), schema);
        assertFalse(
                schema.contains("price_id") || schema.contains("rate_id") || schema.contains("deleted_at_id"), schema);
        // A list of enum codes is a table of its own; an embedded value's columns are the value's.
        assertTrue(schema.contains("tags: List<Tag> @ElementCollection"), schema);
        assertTrue(
                schema.contains("place: Place @Embedded\n") || schema.contains("place: Place @Embedded\r\n"), schema);
        // A collection names the other side's field, and holds no column here.
        assertTrue(
                schema.contains(
                        "comments: List<Reply> @relation(mappedBy=\"post\") -- List<Reply> entity relationship"),
                schema);
        assertFalse(schema.contains("comments_id"), schema);
    }

    private String generate(String rules, String extraConfig) throws Exception {
        Path domain = dir.resolve("src/main/resources/blueprint/forum");
        Files.createDirectories(domain.resolve("model"));
        String env = "elasticore:\n"
                + "  enable: true\n"
                + "  config:\n"
                + "    domainName: forum\n"
                + "    framework: springboot\n"
                + "    mode: jpa\n"
                + "    j2ee: jakarta\n"
                + extraConfig
                + (rules == null ? "" : "    publisher:\n      rules: [" + rules + "]\n")
                + "  namespace:\n"
                + "    base: com.acme.forum\n";
        Files.write(domain.resolve("env.yml"), env.getBytes(StandardCharsets.UTF_8));
        Files.write(domain.resolve("model/entity.yml"), MODEL.getBytes(StandardCharsets.UTF_8));

        // In this JVM, as DefaultGenerationTest runs the rule engine, so a mutation
        // test sees what this test covers.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = System.out;
        System.setOut(new PrintStream(bytes, true, "UTF-8"));
        int code;
        try {
            System.setProperty("elasticore.base.path", dir.toString());
            System.setProperty("elasticore.rules.force", "true");
            code = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
        }
        String genLog = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        assertEquals(0, code, genLog);
        Path schema = domain.getParent().resolve("schema.yml");
        assertTrue(Files.exists(schema), "no schema.yml\n" + genLog);
        return read(schema);
    }

    private Path find(String... names) throws Exception {
        try (java.util.stream.Stream<Path> all = Files.walk(dir.resolve("src/main/java"))) {
            java.util.List<Path> files = new java.util.ArrayList<Path>();
            all.forEach(files::add);
            for (String name : names) {
                for (Path f : files) {
                    if (f.getFileName().toString().equals(name)) return f;
                }
            }
        }
        throw new AssertionError("none of " + String.join(", ", names) + " generated");
    }

    private static String read(Path p) throws Exception {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }
}
