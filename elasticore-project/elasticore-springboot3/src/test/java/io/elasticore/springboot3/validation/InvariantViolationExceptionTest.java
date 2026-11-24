package io.elasticore.springboot3.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue 325: a refusal with values filled its default message like the other refusals do, except this one, whose
 * {0} reached the response when no translation had the code. The generated checks' own sentences, which carry no
 * placeholder and may quote a value, are kept as written.
 */
public class InvariantViolationExceptionTest {

    @Test
    public void theDefaultMessageIsFilledAsTheOtherRefusalsFillTheirs() {
        String message = "{0} is your only way to sign in; unlinking it would lock you out";
        InvariantViolationException refused = new InvariantViolationException("sns.unlink.only", message, "Kakao");
        assertEquals("Kakao is your only way to sign in; unlinking it would lock you out", refused.getMessage());
        assertEquals(new ConflictException("sns.unlink.only", message, "Kakao").getMessage(), refused.getMessage());
        // The code and the values stay for the translation.
        assertEquals("sns.unlink.only", refused.getCode());
        assertArrayEquals(new Object[] {"Kakao"}, refused.getArgs());
    }

    @Test
    public void aSentenceWithoutAPlaceholderKeepsItsQuotes() {
        String sentence = "Order violates status != 'CANCELLED'";
        InvariantViolationException refused =
                new InvariantViolationException("invariant.Order.status", sentence, "Order.status", "Order");
        assertEquals(sentence, refused.getMessage());
        assertEquals(
                "Order can't ship",
                new InvariantViolationException("state.Order.ship", "Order can't ship", "PLACED").getMessage());
    }

    @Test
    public void withoutValuesTheMessageIsAsWritten() {
        assertEquals("{0} stays", new InvariantViolationException("c", "{0} stays").getMessage());
        assertEquals("plain", new InvariantViolationException("plain").getMessage());
    }

    @Test
    public void aPlaceholderIsABraceBeforeADigit() {
        assertTrue(InvariantViolationException.placeholders("at {1}"));
        assertTrue(InvariantViolationException.placeholders("{0}"));
        assertFalse(InvariantViolationException.placeholders("a {b} c"));
        assertFalse(InvariantViolationException.placeholders("ends with {"));
        assertFalse(InvariantViolationException.placeholders("{ {x} }"));
        assertFalse(InvariantViolationException.placeholders(null));
    }
}
