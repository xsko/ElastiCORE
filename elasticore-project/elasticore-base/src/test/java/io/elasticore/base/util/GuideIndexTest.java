package io.elasticore.base.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guide index: a line per section, with a range a reader can open as is.
 */
public class GuideIndexTest {

    private static final Pattern ROW = Pattern.compile("^(elasticore-[a-z-]+\\.md):(\\d+)-(\\d+)  (.*)$");

    @Test
    @DisplayName("a section runs to the next heading of its level; a heading in a fence is an example")
    public void rangesAndFences() {
        List<String> guide = Arrays.asList(
                "# Title",                         // 1
                "",                                // 2
                "## 1. Entities",                  // 3
                "Use `@root` on a root.",          // 4
                "```yaml",                         // 5
                "## not a heading",                // 6
                "  meta: entity @access(read=public)", // 7
                "```",                             // 8
                "### Fields",                      // 9
                "A `money` field.",                // 10
                "",                                // 11
                "## 2. Enums",                     // 12
                "Text.");                          // 13

        List<String> rows = GuideIndex.index("g.md", guide);

        assertEquals(Arrays.asList(
                "g.md:1-13  # Title",
                "g.md:3-10  ## 1. Entities  [@root, @access]",
                "g.md:9-10  ### Fields  [money]",
                "g.md:12-13  ## 2. Enums"), rows);
    }

    @Test
    @DisplayName("a fence closes only on its own marker; h4 under h2, a last-line heading, trailing blanks")
    public void edgesOfTheMarkdown() {
        List<String> guide = Arrays.asList(
                "## A",                            // 1
                "~~~markdown",                     // 2
                "```yaml",                         // 3  inside the tilde fence: not a fence of its own
                "## not a heading",                // 4
                "```",                             // 5
                "~~~",                             // 6  closes the tilde fence
                "#### Deep",                       // 7  h4 straight under h2
                "Java in an example is not a DSL word:", // 8
                "```java",                         // 9
                "@Override @root",                 // 10
                "```",                             // 11
                "",                                // 12
                "",                                // 13
                "## B",                            // 14
                "",                                // 15
                "## C");                           // 16 heading on the last line

        List<String> rows = GuideIndex.index("g.md", guide);

        assertEquals(Arrays.asList(
                "g.md:1-16  # g.md",
                "g.md:1-11  ## A",
                "g.md:7-11  #### Deep  [@root]",
                "g.md:14-14  ## B",
                "g.md:16-16  ## C"), rows);
    }

    @Test
    @DisplayName("a word inside a longer word of the heading is still listed")
    public void headingWordsAreWholeWords() {
        assertTrue(GuideIndex.inHeading("## @access / @owner", "@access"));
        assertFalse(GuideIndex.inHeading("## @access(admin) roles", "@access"));
        assertFalse(GuideIndex.inHeading("## username rules", "user"));
    }

    @Test
    @DisplayName("names, annotations and keys are words; commands, paths and JSON are not")
    public void whatCountsAsAWord() {
        for (String yes : new String[]{"@search(like)", "publisher.rules", "ctx(e).excludeMethod(\"x\")",
                "fields:", "EntitySpec", "@access(read=public)"}) {
            assertTrue(GuideIndex.isTerm(yes), yes);
        }
        for (String no : new String[]{"./gradlew build", "Partner.java", "env.yml", "{\"name\":\"A\"}",
                "src/main/java", "-- text", "x"}) {
            assertFalse(GuideIndex.isTerm(no), no);
        }
    }

    @Test
    @DisplayName("every line of the real index opens on its heading")
    public void theRealIndexPointsAtHeadings(@TempDir Path blueprint) throws IOException {
        GuideDeployer.deployTo(blueprint.toString(), System.getProperty("user.dir"));

        Path index = blueprint.resolve(GuideIndex.FILE_NAME);
        assertTrue(Files.isRegularFile(index), "no index beside the guides");
        List<String> rows = Files.readAllLines(index, StandardCharsets.UTF_8);

        int sections = 0;
        for (String row : rows) {
            Matcher m = ROW.matcher(row);
            if (!m.matches()) continue;
            List<String> guide = Files.readAllLines(blueprint.resolve(m.group(1)), StandardCharsets.UTF_8);
            int first = Integer.parseInt(m.group(2));
            int last = Integer.parseInt(m.group(3));
            assertTrue(first <= last && last <= guide.size(), row);
            if (first == 1) continue;
            sections++;
            String heading = guide.get(first - 1);
            assertTrue(heading.matches("#{2,4} .*"), row + " opens on: " + heading);
            String name = m.group(4).split("  \\[")[0];
            assertEquals(heading.trim(), name, row);

            // The range ends where the next heading of the same or a higher
            // level starts: only blank lines between, and that heading next.
            int level = heading.indexOf(' ');
            int n = last + 1;
            while (n <= guide.size() && guide.get(n - 1).trim().isEmpty()) n++;
            if (n <= guide.size()) {
                String next = guide.get(n - 1);
                int nextLevel = next.indexOf(' ');
                assertTrue(next.matches("#{1,4} .*") && nextLevel <= level, row + " ends before: " + next);
            }
        }
        assertTrue(sections > 300, "only " + sections + " sections indexed");

        // The index is searched, not read, but it has to stay a fraction of
        // the guides it spares: no line longer than a terminal can show in
        // two, and the whole file a small part of the guides.
        long guides = 0;
        for (String g : GuideDeployer.GUIDE_FILES) guides += Files.size(blueprint.resolve(g));
        long size = Files.size(index);
        assertTrue(size * 6 < guides, "index " + size + " bytes for " + guides + " bytes of guides");
        for (String row : rows) assertTrue(row.length() < 400, row);
    }

    @Test
    @DisplayName("a word a developer searches for leads to the section that explains it")
    public void wordsLeadToSections(@TempDir Path blueprint) throws IOException {
        GuideDeployer.deployTo(blueprint.toString(), System.getProperty("user.dir"));
        String index = new String(Files.readAllBytes(blueprint.resolve(GuideIndex.FILE_NAME)), StandardCharsets.UTF_8);

        for (String word : new String[]{"@access", "@owner", "publisher.rules", "excludeMethod", "ModelCheck"}) {
            assertTrue(index.contains(word), "the index never mentions " + word);
        }
        assertFalse(index.contains("## not a heading"));
    }
}
