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

    /**
     * 2.x imported a.dto.* and a.service.*; the 3.0 code names FolderDTO and
     * FolderService. A controller the developer wrote more into kept the
     * wildcards, and its own code uses NoteService through them: the merge
     * keeps them, beside the generated imports and the developer's own. A
     * wildcard of a package the generated code imports nothing from goes, and
     * so does every one where the developer wrote no code.
     */
    @Test
    public void aBaseWildcardTheEditedCodeKeptStaysWhileItsPackageIsImported() {
        String base = t(
                "package a.control;",
                "",
                "import a.dto.*;",
                "import a.service.*;",
                "import a.old.*;",
                "",
                "public class FolderController {",
                "    private final FolderService folderService;",
                "}");
        String yours = t(
                "package a.control;",
                "",
                "import a.dto.*;",
                "import a.service.*;",
                "import a.old.*;",
                "import java.util.ArrayList;",
                "",
                "public class FolderController {",
                "    private final FolderService folderService;",
                "    private final NoteService noteService;",
                "}");
        String generated = t(
                "package a.control;",
                "",
                "import a.dto.FolderDTO;",
                "import a.service.FolderService;",
                "",
                "public class FolderController {",
                "    private final FolderService folderService;",
                "}");
        ThreeWayMerge.Result r = ThreeWayMerge.mergeSource(base, yours, generated);
        assertEquals(0, r.conflicts(), r.text());
        String text = r.text();
        assertTrue(text.contains("import a.dto.FolderDTO;\nimport a.service.FolderService;\n"), text);
        assertTrue(text.contains("import a.dto.*;\n"), text);
        assertTrue(text.contains("import a.service.*;\n"), text);
        assertTrue(text.contains("import java.util.ArrayList;\n"), text);
        assertTrue(text.contains("private final NoteService noteService;"), text);
        assertFalse(text.contains("import a.old.*;"), "no generated import from a.old: " + text);

        // Nothing the developer wrote in the code: the generated imports alone.
        String untouched = ThreeWayMerge.mergeSource(base, base.replace("import a.old.*;\n", ""), generated)
                .text();
        assertFalse(untouched.contains(".*;"), untouched);
    }

    @Test
    public void aWildcardIsKeptOnlyForAPackageAClassIsStillImportedFrom() {
        java.util.Set<String> generated = new java.util.HashSet<String>(java.util.Arrays.asList(
                "import a.dto.FolderDTO", "import a.dto.sub.Deep", "import static a.util.Strings.trim"));
        assertTrue(ThreeWayMerge.stillImportedFrom("import a.dto.*", generated));
        assertFalse(ThreeWayMerge.stillImportedFrom("import a.dt.*", generated));
        assertFalse(ThreeWayMerge.stillImportedFrom("import a.*", generated), "a.dto.FolderDTO is not of package a");
        assertFalse(ThreeWayMerge.stillImportedFrom("import a.dto.FolderDTO", generated));
        assertFalse(ThreeWayMerge.stillImportedFrom("import static a.util.Strings.*", generated));
    }

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

    private static final String SOURCE = t(
            "package a;",
            "",
            "import a.b.Item;",
            "import lombok.Getter;",
            "",
            "@Getter",
            "public class X {",
            "    private String name;",
            "}");

    @Test
    public void importsAddedOnBothSidesAtTheSamePlaceAreBothKept() {
        // The case a line merge gets wrong: the DSL's new type and the developer's, side by side.
        String yours = SOURCE.replace("import lombok.Getter;\n", "import lombok.Getter;\nimport java.util.Objects;\n")
                .replace("}\n", "    public boolean same(X o) { return Objects.equals(name, o.name); }\n}\n");
        String generated = SOURCE.replace("import lombok.Getter;\n", "import lombok.Getter;\nimport java.time.LocalDate;\n")
                .replace("    private String name;\n", "    private String name;\n    private LocalDate madeOn;\n");
        // A line merge conflicts twice: at the imports, and where both add before the closing brace.
        assertEquals(2, ThreeWayMerge.merge(SOURCE, yours, generated).conflicts(), "a line merge conflicts here");

        ThreeWayMerge.Result r = ThreeWayMerge.mergeSource(SOURCE, yours, generated);
        assertEquals(0, r.conflicts(), r.text());
        assertEquals(t(
                "package a;",
                "",
                "import a.b.Item;",
                "import lombok.Getter;",
                "import java.time.LocalDate;",
                "import java.util.Objects;",
                "",
                "@Getter",
                "public class X {",
                "    private String name;",
                "    private LocalDate madeOn;",
                "    public boolean same(X o) { return Objects.equals(name, o.name); }",
                "}"), r.text());
    }

    @Test
    public void anImportTheDeveloperRemovedStaysRemovedAndOneTheDslRemovedGoes() {
        String yours = SOURCE.replace("import a.b.Item;\n", "");
        String generated = SOURCE.replace("import lombok.Getter;\n", "import lombok.Getter;\nimport lombok.Setter;\n")
                .replace("import a.b.Item;\n", "import a.b.Thing;\n");
        ThreeWayMerge.Result r = ThreeWayMerge.mergeSource(SOURCE, yours, generated);
        assertEquals(0, r.conflicts(), r.text());
        assertTrue(r.text().contains("import a.b.Thing;\nimport lombok.Getter;\nimport lombok.Setter;\n"), r.text());
        assertFalse(r.text().contains("Item"), r.text());

        // The DSL drops an import the developer kept: it goes.
        ThreeWayMerge.Result dropped = ThreeWayMerge.mergeSource(SOURCE, SOURCE + "// mine\n",
                SOURCE.replace("import a.b.Item;\n", ""));
        assertFalse(dropped.text().contains("a.b.Item"), dropped.text());
        assertTrue(dropped.text().contains("// mine"), dropped.text());
    }

    @Test
    public void aSourceWithoutImportsGetsTheOnesEitherSideAdds() {
        String bare = t("package a;", "", "public class X {", "}");
        String yours = t("package a;", "import java.util.List;", "", "public class X {", "    List<String> l;", "}");
        String generated = t("package a;", "", "import lombok.Getter;", "", "@Getter", "public class X {", "}");
        ThreeWayMerge.Result r = ThreeWayMerge.mergeSource(bare, yours, generated);
        assertEquals(0, r.conflicts(), r.text());
        assertTrue(r.text().contains("import lombok.Getter;") && r.text().contains("import java.util.List;"), r.text());
        assertTrue(r.text().contains("@Getter") && r.text().contains("    List<String> l;"), r.text());
    }

    @Test
    public void inASourceLinesAddedNextToAChangedOneStayBesideIt() {
        // The upgrade's case: the DSL rewrites the last field and drops the blank lines
        // after it; the developer added a method right there.
        String base = t("class X {", "    int a;", "", "}");
        String yours = t("class X {", "    int a;", "", "    void m() {}", "}");
        String generated = t("class X {", "    long a;", "}");
        assertEquals(1, ThreeWayMerge.merge(base, yours, generated).conflicts(), "a line merge conflicts here");
        ThreeWayMerge.Result r = ThreeWayMerge.mergeSource(base, yours, generated);
        assertEquals(0, r.conflicts(), r.text());
        assertEquals(t("class X {", "    long a;", "    void m() {}", "}"), r.text());

        // A line both changed, each its own way, is still a conflict.
        assertEquals(1, ThreeWayMerge.mergeSource(base, t("class X {", "    short a;", "", "}"), generated).conflicts());
    }

    @Test
    public void aCommentAmongTheImportsIsKeptAndACommentedImportStaysComment() {
        String yours = SOURCE.replace("import lombok.Getter;\n",
                "// the team's: kept for the batch\n// import a.b.Old;\nimport lombok.Getter;\n");
        String generated = SOURCE.replace("import lombok.Getter;\n", "import lombok.Getter;\nimport java.time.LocalDate;\n");
        ThreeWayMerge.Result r = ThreeWayMerge.mergeSource(SOURCE, yours, generated);
        assertEquals(0, r.conflicts(), r.text());
        assertTrue(r.text().contains("// the team's: kept for the batch\n// import a.b.Old;\n"), r.text());
        assertFalse(r.text().contains("\nimport a.b.Old;"), r.text());
        assertTrue(r.text().contains("import java.time.LocalDate;"), r.text());
        assertEquals(1, r.text().split("import lombok.Getter;", -1).length - 1, r.text());

        // Imports in a block comment above are not imports.
        String commented = "/*\nimport a.b.Gone;\n*/\n" + SOURCE;
        ThreeWayMerge.Result c = ThreeWayMerge.mergeSource(commented, commented + "// mine\n", commented);
        assertEquals(commented + "// mine\n", c.text());
    }

    @Test
    public void anImportInATextBlockIsCode() {
        String base = t("package a;", "", "public class X {", "    String s = \"\"\"", "import a.b.C;", "\"\"\";", "}");
        String yours = base.replace("public class X {", "public class X { // mine");
        String generated = base.replace("    String s", "    int n;\n    String s");
        ThreeWayMerge.Result r = ThreeWayMerge.mergeSource(base, yours, generated);
        assertEquals(0, r.conflicts(), r.text());
        assertEquals(t("package a;", "", "public class X { // mine", "    int n;", "    String s = \"\"\"", "import a.b.C;",
                "\"\"\";", "}"), r.text());
    }

    @Test
    public void kotlinImportsHaveNoSemicolon() {
        String base = t("package a", "", "import a.b.Item", "", "class X(val name: String)");
        String yours = t("package a", "", "import a.b.Item", "import java.util.UUID", "", "class X(val name: String) // mine");
        String generated = t("package a", "", "import a.b.Item", "import java.time.LocalDate", "", "class X(val name: String)");
        ThreeWayMerge.Result r = ThreeWayMerge.mergeSource(base, yours, generated);
        assertEquals(0, r.conflicts(), r.text());
        assertEquals(t("package a", "", "import a.b.Item", "import java.time.LocalDate", "import java.util.UUID", "",
                "class X(val name: String) // mine"), r.text());
    }

    private static void assertMerged(String expected, String base, String yours, String generated) {
        ThreeWayMerge.Result r = ThreeWayMerge.merge(base, yours, generated);
        assertEquals(0, r.conflicts(), r.text());
        assertEquals(expected, r.text());
    }
}
