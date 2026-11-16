package io.elasticore.sample.guide.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The code the annotation guide shows in "Spring Security instead of @signin"
 * is the code of this package, which SpringSecurityGuideTest runs, and the
 * Korean guide shows the same blocks.
 */
class SecurityGuideCodeTest {

    private static final String EN = "/elasticore-guide/elasticore-annotation-guide.md";
    private static final String KO = "/elasticore-guide/ko/elasticore-annotation-guide.md";
    private static final Path SOURCES = Paths.get("src/test/java/io/elasticore/sample/guide/security");

    @Test
    void everyBlockOfTheSectionIsCodeThatRuns() throws IOException {
        List<List<String>> blocks = blocks(section(EN, "### Spring Security instead of @signin"));
        assertEquals(3, blocks.size(), "the login form, the JWT resource server, the OIDC login");

        List<List<String>> sources = new ArrayList<List<String>>();
        try (Stream<Path> files = Files.list(SOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).collect(Collectors.toList())) {
                sources.add(Files.readAllLines(file, StandardCharsets.UTF_8));
            }
        }
        for (List<String> block : blocks) {
            boolean found = false;
            for (List<String> source : sources) found |= inOrder(block, source);
            assertTrue(found, "not in the code SpringSecurityGuideTest runs:\n" + String.join("\n", block));
        }
    }

    @Test
    void theKoreanGuideShowsTheSameCode() throws IOException {
        assertEquals(blocks(section(EN, "### Spring Security instead of @signin")),
                blocks(section(KO, "### @signin 대신 Spring Security")));
    }

    @Test
    void aBlockIsFoundOnlyInItsOrder() {
        List<String> source = Arrays.asList("a", "  b", "c");
        assertTrue(inOrder(Arrays.asList("a", "", "b"), source));
        assertTrue(!inOrder(Arrays.asList("b", "a"), source));
    }

    /** Whether the block's lines, blank ones aside and trimmed, stand in this order in the source. */
    static boolean inOrder(List<String> block, List<String> source) {
        int at = 0;
        for (String line : block) {
            String wanted = line.trim();
            if (wanted.isEmpty()) continue;
            while (at < source.size() && !source.get(at).trim().equals(wanted)) at++;
            if (at == source.size()) return false;
            at++;
        }
        return true;
    }

    /** The lines from the heading to the next heading of its level or above. */
    private static List<String> section(String resource, String heading) throws IOException {
        List<String> lines;
        try (InputStream in = SecurityGuideCodeTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is not on the classpath");
            lines = Arrays.asList(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\r?\n", -1));
        }
        int start = -1;
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).startsWith(heading)) start = i;
        assertTrue(start >= 0, heading + " is not in " + resource);
        int end = start + 1;
        while (end < lines.size() && !lines.get(end).startsWith("## ") && !lines.get(end).startsWith("### ")) end++;
        return lines.subList(start, end);
    }

    private static List<List<String>> blocks(List<String> section) {
        List<List<String>> blocks = new ArrayList<List<String>>();
        List<String> open = null;
        for (String line : section) {
            if (open == null && line.equals("```java")) {
                open = new ArrayList<String>();
            } else if (open != null && line.equals("```")) {
                blocks.add(open);
                open = null;
            } else if (open != null) {
                open.add(line);
            }
        }
        return blocks;
    }
}
