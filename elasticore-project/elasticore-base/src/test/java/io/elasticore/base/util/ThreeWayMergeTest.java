package io.elasticore.base.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThreeWayMergeTest {

    private static String t(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    private static final String BASE = t("a", "b", "c", "d", "e");

    @Test
    public void whatOnlyOneSideChangedIsTaken() {
        assertMerged(t("a", "B", "c", "d", "e"), BASE, t("a", "B", "c", "d", "e"), BASE);
        assertMerged(t("a", "b", "c", "D", "e"), BASE, BASE, t("a", "b", "c", "D", "e"));
    }

    @Test
    public void bothSidesInDifferentPlacesAreBothKept() {
        // A comment added by the developer, a line the DSL added, a line the developer changed.
        String yours = t("<!-- mine -->", "a", "b", "C2", "d", "e", "extra");
        String generated = t("a", "b", "c", "d", "d2", "e");
        assertMerged(t("<!-- mine -->", "a", "b", "C2", "d", "d2", "e", "extra"), BASE, yours, generated);
    }

    @Test
    public void theSameChangeOnBothSidesIsTakenOnce() {
        String both = t("a", "b", "x", "d", "e");
        assertMerged(both, BASE, both, both);
    }

    @Test
    public void aDeletionMergesWithAnEditElsewhere() {
        assertMerged(t("a", "c", "D", "e"), BASE, t("a", "c", "d", "e"), t("a", "b", "c", "D", "e"));
    }

    @Test
    public void theSameLinesChangedDifferentlyAreAConflict() {
        ThreeWayMerge.Result r = ThreeWayMerge.merge(BASE, t("a", "b", "mine", "d", "e"), t("a", "b", "theirs", "d", "e"));
        assertEquals(1, r.conflicts());
        assertEquals(t("a", "b", "<<<<<<< yours", "mine", "=======", "theirs", ">>>>>>> elasticore", "d", "e"), r.text());
        assertTrue(ThreeWayMerge.hasConflictMarks(r.text()));
    }

    @Test
    public void aLineTheDeveloperDeletedAndTheDslChangedIsAConflict() {
        ThreeWayMerge.Result r = ThreeWayMerge.merge(BASE, t("a", "b", "d", "e"), t("a", "b", "C", "d", "e"));
        assertEquals(1, r.conflicts());
    }

    @Test
    public void insertionsAtTheSamePlaceConflictUnlessEqual() {
        assertEquals(1, ThreeWayMerge.merge(BASE, t("a", "x", "b", "c", "d", "e"), t("a", "y", "b", "c", "d", "e")).conflicts());
        assertMerged(t("a", "x", "b", "c", "d", "e"), BASE, t("a", "x", "b", "c", "d", "e"), t("a", "x", "b", "c", "d", "e"));
    }

    @Test
    public void lineEndsDoNotCount() {
        String yours = BASE.replace("\n", "\r\n").replace("c\r\n", "C\r\n");
        assertMerged(t("a", "b", "C", "d", "E"), BASE, yours, t("a", "b", "c", "d", "E"));
        assertFalse(ThreeWayMerge.hasConflictMarks(BASE));
    }

    private static void assertMerged(String expected, String base, String yours, String generated) {
        ThreeWayMerge.Result r = ThreeWayMerge.merge(base, yours, generated);
        assertEquals(0, r.conflicts(), r.text());
        assertEquals(expected, r.text());
    }
}
