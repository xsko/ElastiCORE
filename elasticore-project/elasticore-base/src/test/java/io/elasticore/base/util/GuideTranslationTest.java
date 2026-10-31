package io.elasticore.base.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The English guides and the Korean ones say the same thing in the same place.
 *
 * Each guide is written twice, and the second copy is the one a change forgets.
 * Whether two paragraphs mean the same cannot be checked; that they have the
 * same shape can: the same lines, the same headings with their numbers, and the
 * same code blocks of the same length. A change made to one language only moves
 * one of those, and a reader of the other then follows a guide that is out of
 * date. The English guides are for readers who do not read Korean, so their
 * prose holds none.
 */
public class GuideTranslationTest {

    /** A heading's level and its section number - "2.3", "1." - not a number that is part of a word. */
    private static final Pattern HEADING = Pattern.compile("^(#{1,6}) +(?:([0-9][0-9.]*)(?=\\s|$))?");
    private static final Pattern HANGUL = Pattern.compile("[\\uac00-\\ud7a3\\u3131-\\u318e]");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`]*`");
    private static final Pattern LINK_TARGET = Pattern.compile("\\]\\([^)]*\\)");

    @Test
    @DisplayName("every guide has the same lines, headings and code blocks in English and in Korean")
    public void theTwoLanguagesHaveTheSameShape() throws IOException {
        List<String> problems = new ArrayList<String>();
        for (String guide : GuideDeployer.GUIDE_FILES) {
            List<String> en = lines("/elasticore-guide/" + GuideDeployer.directoryOf("en") + guide);
            List<String> ko = lines("/elasticore-guide/" + GuideDeployer.directoryOf("ko") + guide);
            if (en.size() != ko.size()) {
                problems.add(guide + ": " + en.size() + " lines in English, " + ko.size() + " in Korean");
            }
            String difference = firstDifference(shape(en), shape(ko));
            if (difference != null) problems.add(guide + ": " + difference);
        }
        assertTrue(problems.isEmpty(), "the guides differ between the languages - change both:\n"
                + String.join("\n", problems));
    }

    @Test
    @DisplayName("the English guides' prose holds no Korean")
    public void theEnglishProseIsEnglish() throws IOException {
        List<String> problems = new ArrayList<String>();
        for (String guide : GuideDeployer.GUIDE_FILES) {
            List<String> en = lines("/elasticore-guide/" + GuideDeployer.directoryOf("en") + guide);
            boolean fence = false;
            for (int i = 0; i < en.size(); i++) {
                String line = en.get(i);
                if (line.trim().startsWith("```")) {
                    fence = !fence;
                    continue;
                }
                if (fence) continue;
                String prose = LINK_TARGET.matcher(INLINE_CODE.matcher(line).replaceAll("")).replaceAll("]");
                if (HANGUL.matcher(prose).find()) problems.add(guide + ":" + (i + 1) + "  " + line);
            }
        }
        assertTrue(problems.isEmpty(), "Korean in the English guides:\n" + String.join("\n", problems));
    }

    @Test
    @DisplayName("the shape compares headings by level and number, and code blocks by fence and length")
    public void theShapeIsWhatIsCompared() {
        List<String> a = Arrays.asList("# Guide", "## 2.3 Rules", "Text.", "```yaml", "a: 1 # one", "```");
        List<String> b = Arrays.asList("# 가이드", "## 2.3 규칙", "설명.", "```yaml", "a: 1 # 하나", "```");
        assertEquals(null, firstDifference(shape(a), shape(b)));

        List<String> c = Arrays.asList("# Guide", "## 2.4 Rules", "Text.", "```yaml", "a: 1", "```");
        assertTrue(firstDifference(shape(a), shape(c)).contains("2.4"));

        List<String> d = Arrays.asList("# Guide", "## 2.3 Rules", "Text.", "```yaml", "a: 1", "b: 2", "```");
        assertTrue(firstDifference(shape(a), shape(d)).contains("code block"));
    }

    /** The headings (level and number) and code blocks (fence and length) in order, with their line. */
    static List<String[]> shape(List<String> lines) {
        List<String[]> shape = new ArrayList<String[]>();
        boolean fence = false;
        int start = 0;
        String opener = "";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().startsWith("```")) {
                if (!fence) {
                    fence = true;
                    start = i;
                    opener = line.trim();
                } else {
                    fence = false;
                    shape.add(new String[]{"code block " + opener + " of " + (i - start - 1) + " lines",
                            String.valueOf(start + 1)});
                }
                continue;
            }
            if (fence) continue;
            Matcher m = HEADING.matcher(line);
            if (m.find()) {
                String number = m.group(2) == null ? "" : " " + m.group(2);
                shape.add(new String[]{"heading " + m.group(1) + number, String.valueOf(i + 1)});
            }
        }
        return shape;
    }

    static String firstDifference(List<String[]> en, List<String[]> ko) {
        for (int i = 0; i < Math.min(en.size(), ko.size()); i++) {
            if (!en.get(i)[0].equals(ko.get(i)[0])) {
                return "English line " + en.get(i)[1] + " has " + en.get(i)[0] + ", Korean line " + ko.get(i)[1]
                        + " has " + ko.get(i)[0];
            }
        }
        if (en.size() != ko.size()) {
            return en.size() + " headings and code blocks in English, " + ko.size() + " in Korean";
        }
        return null;
    }

    private static List<String> lines(String resource) throws IOException {
        InputStream in = GuideTranslationTest.class.getResourceAsStream(resource);
        assertNotNull(in, resource + " is not on the classpath");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<String>(Arrays.asList(text.split("\r?\n", -1)));
            if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
            return lines;
        } finally {
            in.close();
        }
    }
}
