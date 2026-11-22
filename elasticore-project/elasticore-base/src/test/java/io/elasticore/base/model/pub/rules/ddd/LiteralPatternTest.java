package io.elasticore.base.model.pub.rules.ddd;

import org.junit.jupiter.api.Test;

import java.text.MessageFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The text a default sentence of the bundle takes literally - a condition, a
 * bound - beside its placeholders: written so MessageFormat says it back as
 * it was, an apostrophe and a brace included.
 */
public class LiteralPatternTest {

    private static String rendered(String literal) {
        return MessageFormat.format("{1} violates " + DddInvariantRules.literalPattern(literal), "f", "Order");
    }

    @Test
    public void anApostropheAndABraceAreSaidAsWritten() {
        assertEquals("Order violates note != 'x'", rendered("note != 'x'"));
        assertEquals("Order violates a {b} c", rendered("a {b} c"));
        assertEquals("Order violates it's {0} '' }{", rendered("it's {0} '' }{"));
        assertEquals("Order violates total >= 0", rendered("total >= 0"));
    }
}
