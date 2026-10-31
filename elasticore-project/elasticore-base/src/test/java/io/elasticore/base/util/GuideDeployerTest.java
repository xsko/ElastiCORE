package io.elasticore.base.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which guides a project receives: the language a build asks for, read from
 * {@link GuideDeployer#LANGUAGE_PROPERTY}.
 */
public class GuideDeployerTest {

    private static final Pattern HANGUL = Pattern.compile("[\\uac00-\\ud7a3]");

    private String before;

    @BeforeEach
    public void remember() {
        before = System.getProperty(GuideDeployer.LANGUAGE_PROPERTY);
    }

    @AfterEach
    public void restore() {
        if (before == null) System.clearProperty(GuideDeployer.LANGUAGE_PROPERTY);
        else System.setProperty(GuideDeployer.LANGUAGE_PROPERTY, before);
    }

    @Test
    @DisplayName("unset or blank is English; a known language however written is that language")
    public void theLanguageAskedFor() {
        System.clearProperty(GuideDeployer.LANGUAGE_PROPERTY);
        assertEquals("en", GuideDeployer.language());

        System.setProperty(GuideDeployer.LANGUAGE_PROPERTY, "  ");
        assertEquals("en", GuideDeployer.language());

        System.setProperty(GuideDeployer.LANGUAGE_PROPERTY, " KO ");
        assertEquals("ko", GuideDeployer.language());

        System.setProperty(GuideDeployer.LANGUAGE_PROPERTY, "en");
        assertEquals("en", GuideDeployer.language());
    }

    @Test
    @DisplayName("a language the guides are not written in is English, with a warning that names it")
    public void anUnknownLanguageIsEnglishAndSaysSo() {
        System.setProperty(GuideDeployer.LANGUAGE_PROPERTY, "fr");
        String printed = printedBy(new Runnable() {
            @Override
            public void run() {
                assertEquals("en", GuideDeployer.language());
            }
        });
        assertTrue(printed.contains("The guides are not written in 'fr' (en, ko); copying the English ones."), printed);
    }

    @Test
    @DisplayName("English guides at the top of elasticore-guide/, every other language in its own directory")
    public void theDirectories() {
        assertEquals("", GuideDeployer.directoryOf("en"));
        assertEquals("ko/", GuideDeployer.directoryOf("ko"));
    }

    @Test
    @DisplayName("a user's project - no checkout - gets the Korean guides from the jar when it asks")
    public void theKoreanGuidesComeFromTheClasspath(@TempDir Path root) throws IOException {
        System.setProperty(GuideDeployer.LANGUAGE_PROPERTY, "ko");
        Path ko = Files.createDirectories(root.resolve("ko"));
        GuideDeployer.deployTo(ko.toString(), null);
        for (String guide : GuideDeployer.GUIDE_FILES) {
            String text = new String(Files.readAllBytes(ko.resolve(guide)), StandardCharsets.UTF_8);
            assertTrue(HANGUL.matcher(text).find(), guide + " is not the Korean guide");
        }
        assertTrue(Files.isRegularFile(ko.resolve(GuideIndex.FILE_NAME)));

        System.clearProperty(GuideDeployer.LANGUAGE_PROPERTY);
        Path en = Files.createDirectories(root.resolve("en"));
        GuideDeployer.deployTo(en.toString(), null);
        String first = Files.readAllLines(en.resolve(GuideDeployer.GUIDE_FILES[0]), StandardCharsets.UTF_8)
                .get(2);
        assertFalse(HANGUL.matcher(first).find(), first);
    }

    private static String printedBy(Runnable action) {
        PrintStream out = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, "UTF-8"));
            action.run();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        } finally {
            System.setOut(out);
        }
        return new String(captured.toByteArray(), StandardCharsets.UTF_8);
    }
}
