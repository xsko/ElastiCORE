package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The values of a @pattern with leading lookaheads - a password's rule - that
 * the contract tests send: one that matches, one that matches the rest and
 * misses a lookahead, and what stays no sample.
 */
public class PatternSampleTest {

    private static final String LETTER_AND_DIGIT = "^(?=.*[A-Za-z])(?=.*[0-9]).+$";
    private static final String FOUR_KINDS = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$";

    @Test
    public void aLetterAndADigitAtLeastEightLong() {
        // string(255)! @length(min=8): the length the field asks for, a letter and a digit.
        String valid = PatternSample.matching(LETTER_AND_DIGIT, 8, 255);
        assertNotNull(valid);
        assertTrue(Pattern.matches(LETTER_AND_DIGIT, valid), valid);
        assertTrue(valid.length() >= 8 && valid.length() <= 255, valid);
        // Refused: the rest (.+) matches, the length holds, a lookahead is missed.
        String refused = PatternSample.notMatching(LETTER_AND_DIGIT, valid);
        assertNotNull(refused);
        assertTrue(Pattern.matches("^.+$", refused), refused);
        assertFalse(Pattern.matches(LETTER_AND_DIGIT, refused), refused);
        assertEquals(valid.length(), refused.length());
    }

    @Test
    public void anUpperALowerADigitAndASymbol() {
        String valid = PatternSample.matching(FOUR_KINDS, 0, 255);
        assertNotNull(valid);
        assertTrue(Pattern.matches(FOUR_KINDS, valid), valid);
        String refused = PatternSample.notMatching(FOUR_KINDS, valid);
        assertNotNull(refused);
        assertTrue(Pattern.matches("^.{8,}$", refused), refused);
        assertFalse(Pattern.matches(FOUR_KINDS, refused), refused);
        assertTrue(PatternSample.lookaheads(FOUR_KINDS));
    }

    @Test
    public void theLazyFormAndALiteralAreRead() {
        String re = "^(?=.*?#)(?=.*x)[a-z#]{4}$";
        String valid = PatternSample.matching(re, 0, 10);
        assertNotNull(valid);
        assertTrue(Pattern.matches(re, valid), valid);
        String refused = PatternSample.notMatching(re, valid);
        assertNotNull(refused);
        assertTrue(Pattern.matches("^[a-z#]{4}$", refused), refused);
        assertFalse(Pattern.matches(re, refused), refused);
    }

    /** A value varied per row keeps every lookahead: the position that holds one varies within it. */
    @Test
    public void aVariedValueKeepsEachLookahead() {
        String re = "^(?=.*[A-Z])(?=.*\\d)[A-Za-z0-9]{6}$";
        PatternSample.Sample s = PatternSample.sample(re, 0, 6);
        assertNotNull(s);
        assertTrue(s.varies());
        Random random = new Random(295);
        for (int round = 0; round < 2000; round++) {
            char[] t = s.text.toCharArray();
            for (int i = 0; i < s.alphabets.size(); i++) {
                String a = s.alphabets.get(i);
                if (!a.isEmpty()) t[i] = a.charAt(random.nextInt(a.length()));
            }
            assertTrue(Pattern.matches(re, new String(t)), new String(t));
        }
    }

    /** ^(?=.*\d)\d+$: the rest holds the digit itself, so no value matches it and misses the lookahead. */
    @Test
    public void aLookaheadTheRestAlwaysHoldsCannotBeMissed() {
        String re = "^(?=.*\\d)\\d+$";
        String valid = PatternSample.matching(re, 0, 6);
        assertNotNull(valid);
        assertNull(PatternSample.notMatching(re, valid));
    }

    @Test
    public void anyOtherLookaroundIsNoSample() {
        // A negative lookahead, a lookbehind, a lookahead further in, one without ^ before it, and one no character
        // fills.
        for (String re : new String[] {
            "^(?!.*\\s).+$",
            "^(?<=a)b$",
            "^a(?=.*b).+$",
            "(?=.*\\d).+",
            "^(?=.*[a-z])a(?=.*b).+$",
            "^(?=.*\\b).+$",
            "^(?=.*[a-z]+).+$"
        }) {
            assertNull(PatternSample.matching(re, 0, 20), re);
        }
        assertFalse(PatternSample.lookaheads("^(?!.*\\s).+$"));
    }

    @Test
    public void aPatternWithoutLookaheadsIsReadAsBefore() {
        assertEquals("AA000", PatternSample.matching("[A-Z]{2}[0-9]{3}", 0, 8));
        assertEquals("!", PatternSample.notMatching("[A-Z]{2}[0-9]{3}", "AA000"));
        assertFalse(PatternSample.lookaheads("[A-Z]{2}[0-9]{3}"));
    }

    /** The DSL writes a string literal's content (\\d); the DTO's @Pattern hands the engine what it reads (\d). */
    @Test
    public void thePatternIsReadAsTheStringLiteralTheDtoWrites() {
        assertEquals("^\\d{3}$", PatternSample.unescape("^\\\\d{3}$"));
        assertEquals("a\"b", PatternSample.unescape("a\\\"b"));
        assertEquals("A", PatternSample.unescape("\\u0041"));
        assertEquals("\t", PatternSample.unescape("\\t"));
        // A lone backslash (the loader warns of it) stays.
        assertEquals("\\d", PatternSample.unescape("\\d"));
        String phone = PatternSample.matching(PatternSample.unescape("^\\\\d{3}-\\\\d{4}$"), 0, 20);
        assertNotNull(phone);
        assertTrue(phone.matches("\\d{3}-\\d{4}"), phone);
    }
}
