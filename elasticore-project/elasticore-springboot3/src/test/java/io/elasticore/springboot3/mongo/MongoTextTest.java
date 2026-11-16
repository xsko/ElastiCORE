package io.elasticore.springboot3.mongo;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoTextTest {

    @Test
    void containsStartsEndsAndEqualsAreAnchoredAsSaid() {
        assertTrue(find(MongoText.match("app", true, true, false), "pineapple pie"));
        assertTrue(find(MongoText.match("app", false, true, false), "apple"));
        assertFalse(find(MongoText.match("app", false, true, false), "pineapple"));
        assertTrue(find(MongoText.match("ple", true, false, false), "apple"));
        assertFalse(find(MongoText.match("ple", true, false, false), "apples"));
        assertTrue(find(MongoText.match("apple", false, false, false), "apple"));
        assertFalse(find(MongoText.match("apple", false, false, false), "apples"));
        assertEquals("^apple$", MongoText.match("apple", false, false, false).pattern());
        assertEquals("apple", MongoText.match("apple", true, true, false).pattern());
    }

    @Test
    void caseIsIgnoredOnlyWhenAsked() {
        assertTrue(find(MongoText.match("APPLE", true, true, true), "an apple"));
        assertFalse(find(MongoText.match("APPLE", true, true, false), "an apple"));
        assertEquals(
                Pattern.CASE_INSENSITIVE, MongoText.match("a", true, true, true).flags());
        assertEquals(0, MongoText.match("a", true, true, false).flags());
    }

    @Test
    void theValuesOwnRegexCharactersAreTakenAsWritten() {
        assertTrue(find(MongoText.match("(1)", true, true, false), "gift (1) wrap"));
        assertFalse(find(MongoText.match("a.c", true, true, false), "abc"));
        assertTrue(find(MongoText.match("a.c", true, true, false), "a.c"));
        assertEquals("\\\\\\^\\$\\.\\|\\?\\*\\+\\(\\)\\[\\]\\{\\}x", MongoText.quote("\\^$.|?*+()[]{}x"));
        assertEquals("^$", MongoText.match(null, false, false, false).pattern());
    }

    @Test
    void aLikeReadsPercentAndUnderscore() {
        assertTrue(find(MongoText.like("ap%"), "apple"));
        assertFalse(find(MongoText.like("ap%"), "pineapple"));
        assertTrue(find(MongoText.like("%ple"), "pineapple"));
        assertTrue(find(MongoText.like("a_ple"), "apple"));
        assertFalse(find(MongoText.like("a_ple"), "aple"));
        assertTrue(find(MongoText.like("apple"), "apple"));
        assertFalse(find(MongoText.like("apple"), "apples"));
        assertTrue(find(MongoText.like("a.%"), "a.b"));
        assertFalse(find(MongoText.like("a.%"), "ab"));
        assertTrue(find(MongoText.like("%"), "line\nbreak"));
        assertEquals("^$", MongoText.like(null).pattern());
    }

    private static boolean find(Pattern p, String text) {
        return p.matcher(text).find();
    }
}
