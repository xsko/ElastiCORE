package io.elasticore.base.model.pub.rules.ddd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A state field with @default and states.initial: one note when the two name
 * the same state - the generator only says which line it kept - and a warning
 * when they name different ones, since the @default is then not applied.
 */
public class StateDefaultTest {

    @Test
    public void theSameStateIsOneWhicheverWayItIsWritten() {
        assertTrue(DddStateRules.sameState("PLACED", "PLACED"));
        assertTrue(DddStateRules.sameState("OrderStatus.PLACED", "PLACED"));
        assertTrue(DddStateRules.sameState("\"PLACED\"", "PLACED"));
        assertTrue(DddStateRules.sameState(" 'PLACED' ", "PLACED"));
    }

    @Test
    public void anotherStateOrACodeIsAnother() {
        assertFalse(DddStateRules.sameState("SHIPPED", "PLACED"));
        // A code value names the state only through the enum: not compared, read as another.
        assertFalse(DddStateRules.sameState("PLC", "PLACED"));
        assertFalse(DddStateRules.sameState(null, "PLACED"));
    }
}
