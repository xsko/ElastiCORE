package io.elasticore.base.model.loader.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** An '=' in a condition annotation compares when it is part of ==, >=, <= or !=. */
public class HasAssignmentTest {

    @Test
    public void aComparisonIsNoAssignment() {
        assertFalse(AbstractModelLoader.hasAssignment("Comment.post, status == VISIBLE"));
        assertFalse(AbstractModelLoader.hasAssignment("total >= 0, 'never below'"));
        assertFalse(AbstractModelLoader.hasAssignment("total <= 9"));
        assertFalse(AbstractModelLoader.hasAssignment("total != 9"));
        assertFalse(AbstractModelLoader.hasAssignment("a==b"));
        assertFalse(AbstractModelLoader.hasAssignment("total > 0, 'a = b in a message'"));
        assertFalse(AbstractModelLoader.hasAssignment("total > 0, \"a = b\""));
    }

    @Test
    public void onlyAConditionAnnotationReadsTheComparison() {
        AbstractModelLoader loader = new AbstractModelLoader();
        assertEquals("A.b, s == V", loader.parseParameters("count", "A.b, s == V").getValue());
        assertEquals("t >= 0, 'm'", loader.parseParameters("invariant", "t >= 0, 'm'").getValue());
        assertEquals("q, n <= 3", loader.parseParameters("specification", "q, n <= 3").getValue());
        // Every other annotation keeps the 2.x reading.
        assertEquals("x", loader.parseParameters("search", ">=, x").getValue());
        // A single '=' in a condition annotation is still a key=value pair.
        assertEquals("V", loader.parseParameters("count", "A.b, s = V").getProperties().getProperty("s"));
    }

    @Test
    public void aSingleEqualsAssigns() {
        assertTrue(AbstractModelLoader.hasAssignment("status = VISIBLE"));
        assertTrue(AbstractModelLoader.hasAssignment("=x"));
        assertTrue(AbstractModelLoader.hasAssignment("x="));
        assertTrue(AbstractModelLoader.hasAssignment("a == b, c = d"));
        assertTrue(AbstractModelLoader.hasAssignment("'q' x = 1"));
    }
}
