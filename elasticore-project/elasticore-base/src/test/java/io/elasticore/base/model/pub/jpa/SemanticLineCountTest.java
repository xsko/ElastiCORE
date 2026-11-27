package io.elasticore.base.model.pub.jpa;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a generated file must be rewritten counts its lines (issue 376, from 2.2.261026): a line that occurs
 * a different number of times is a change, whatever the difference, and -Pmode=valid shows its counts.
 */
class SemanticLineCountTest {

    private static final SrcFilePublisher PUBLISHER = new SrcFilePublisher(null);

    @Test
    void aLineRepeatedADifferentNumberOfTimesIsAChange() throws Exception {
        String once = "class A {\n  x();\n}\n";
        String twice = "class A {\n  x();\n  x();\n}\n";
        String thrice = "class A {\n  x();\n  x();\n  x();\n}\n";

        assertFalse(identical(thrice, once), "3 against 1");
        assertFalse(identical(once, thrice), "1 against 3");
        assertFalse(identical(thrice, twice), "3 against 2");
        assertTrue(identical(thrice, "x();\nclass A {\n    x();\n  x();\n}\n"), "3 and 3, in another order");
        assertTrue(identical(twice, twice), "the same text");
    }

    @Test
    void theDiffShowsTheCountsOfALineBothHave() throws Exception {
        String existing = "class A {\n  x();\n  x();\n  x();\n}\n";
        String generated = "class A {\n  x();\n}\n";

        String removed = diff(existing, generated);
        assertTrue(removed.contains("  [current] only in current file:\n    > x();  (3 vs 1)\n"), removed);
        assertFalse(removed.contains("[expected]"), removed);

        String added = diff(generated, existing);
        assertTrue(added.contains("  [expected] only in ElastiCORE generated:\n    > x();  (3 vs 1)\n"), added);
        assertFalse(added.contains("[current]"), added);

        String same = diff(existing, "class A {\n x();\n x();\n x();\n}\n");
        assertFalse(same.contains("[current]") || same.contains("[expected]"), same);
    }

    private static boolean identical(String existing, String generated) throws Exception {
        Method m = SrcFilePublisher.class.getDeclaredMethod("isSemanticallyIdentical", String.class, String.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(PUBLISHER, existing, generated);
    }

    private static String diff(String existing, String generated) throws Exception {
        Method m = SrcFilePublisher.class.getDeclaredMethod(
                "generateUnifiedDiff", String.class, String.class, String.class);
        m.setAccessible(true);
        String text = (String) m.invoke(PUBLISHER, existing, generated, "A.java");
        assertEquals("A.java\n", text.substring(0, "A.java\n".length()), text);
        return text;
    }
}
