package io.elasticore.base.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    /** Issue 230: a method both sides took out, one leaving a blank line more. */
    @Test
    public void theSameChangeButABlankLineIsNoConflict() {
        String base = "class C {\n    void read() {}\n\n    void schema() {}\n\n    void search() {}\n}\n";
        String yours = "class C {\n    void read() {}\n\n    void search() {}\n}\n";
        String generated = "class C {\n    void read() {}\n\n\n    void search() {}\n}\n";
        for (ThreeWayMerge.Result r : new ThreeWayMerge.Result[] {
            ThreeWayMerge.merge(base, yours, generated), ThreeWayMerge.mergeSource(base, yours, generated)
        }) {
            assertEquals(0, r.conflicts(), r.text());
            assertEquals(yours, r.text(), "the developer's layout is kept");
        }
        // The other way round: the blank line is the developer's, and it stays.
        assertEquals(
                generated, ThreeWayMerge.mergeSource(base, generated, yours).text());
    }

    @Test
    public void spacesAtTheEndOfALineAreLayoutToo() {
        String yours = t("a", "b", "x  ", "d", "e");
        assertMerged(yours, BASE, yours, t("a", "b", "x", "d", "e"));
        assertMerged(
                t("a", "b", "x", "", "d", "e"), BASE, t("a", "b", "x", "", "d", "e"), t("a", "b", "x\t", "d", "e"));
    }

    /** Indentation and spaces inside a line mean something in YAML, a raw string, a literal: still a conflict. */
    @Test
    public void indentationAndSpacesInsideALineAreNotLayout() {
        assertEquals(
                1,
                ThreeWayMerge.merge(BASE, t("a", "b", "  x", "d", "e"), t("a", "b", "x", "d", "e"))
                        .conflicts());
        assertEquals(
                1,
                ThreeWayMerge.merge(BASE, t("a", "b", "x  y", "d", "e"), t("a", "b", "x y", "d", "e"))
                        .conflicts());
    }

    @Test
    public void aRealConflictWithABlankLineBesideIsStillOne() {
        ThreeWayMerge.Result r =
                ThreeWayMerge.merge(BASE, t("a", "b", "mine", "", "d", "e"), t("a", "b", "theirs", "d", "e"));
        assertEquals(1, r.conflicts());
        assertTrue(r.text().contains("mine") && r.text().contains("theirs"), r.text());
        // Lines both deleted, against lines one changed: not alike.
        assertEquals(
                1,
                ThreeWayMerge.merge(BASE, t("a", "b", "", "d", "e"), t("a", "b", "C", "d", "e"))
                        .conflicts());
    }

    /** In a class body, the same method both added once - not the developer's and the DSL's one after the other. */
    @Test
    public void theSameMethodBothAddedButABlankLineIsTakenOnce() {
        String base = t("class X {", "    int a;", "}");
        String yours = t("class X {", "    int a;", "", "    void m() {}", "}");
        String generated = t("class X {", "    int a;", "    void m() {}", "}");
        ThreeWayMerge.Result r = ThreeWayMerge.mergeSource(base, yours, generated);
        assertEquals(0, r.conflicts(), r.text());
        assertEquals(yours, r.text());
    }

    @Test
    public void aConflictSaysWhereItIsInYours() {
        ThreeWayMerge.Result r =
                ThreeWayMerge.merge(BASE, t("a", "b", "mine", "d", "e"), t("a", "b", "theirs", "d", "e"));
        assertEquals(java.util.Arrays.asList(3), r.lines());
        // In a source, past the imports of the developer's file.
        String yours = SOURCE.replace("import lombok.Getter;\n", "import lombok.Getter;\nimport java.util.List;\n")
                .replace("private String name;", "private String mine;");
        String generated = SOURCE.replace("private String name;", "private String theirs;");
        ThreeWayMerge.Result s = ThreeWayMerge.mergeSource(SOURCE, yours, generated);
        assertEquals(1, s.conflicts(), s.text());
        assertEquals("    private String mine;", yours.split("\n")[s.lines().get(0) - 1]);
    }

    /** The generated contract test of issue 295, as the generator wrote it last time. */
    private static final String CONTRACT = t(
            "package com.acme.crm;",
            "",
            "import org.junit.jupiter.api.DisplayName;",
            "import org.junit.jupiter.api.Test;",
            "",
            "class AccountContractTest extends CrmContractSupport {",
            "",
            "    @Test",
            "    @DisplayName(\"name is required (400 NotNull)\")",
            "    void nameIsRequired() throws Exception {",
            "        assertFieldError(post(ACCOUNT, withValue(newAccount(), \"name\", null)), \"name\", \"NotNull\");",
            "    }",
            "",
            "    @Test",
            "    @DisplayName(\"name: 100 pass, 101 are refused (400 Size)\")",
            "    void nameSize() throws Exception {",
            "        assertCreated(post(ACCOUNT, withValue(newAccount(), \"name\", text(100))));",
            "        assertFieldError(post(ACCOUNT, withValue(newAccount(), \"name\", text(101))), \"name\", \"Size\");",
            "    }",
            "}");

    /** The developer's test, written right after the generated nameIsRequired, with a Javadoc of its own. */
    private static final String HAND_WRITTEN = String.join(
            "\n",
            "    /** Written by hand: @notblank is not generated yet. */",
            "    @Test",
            "    @DisplayName(\"name: blank is refused (400 NotBlank)\")",
            "    void nameIsNotBlank() throws Exception {",
            "        assertFieldError(post(ACCOUNT, withValue(newAccount(), \"name\", \"   \")), \"name\", \"NotBlank\");",
            "    }",
            "",
            "");

    /** The test the generator now writes at the same place. */
    private static final String GENERATED_MEMBER = String.join(
            "\n",
            "    @Test",
            "    @DisplayName(\"name: blank is refused (400 NotBlank)\")",
            "    void nameNotBlank() throws Exception {",
            "        assertFieldError(post(ACCOUNT, withValue(newAccount(), \"name\", \"   \")), \"name\", \"NotBlank\");",
            "    }",
            "",
            "");

    private static final String AFTER_REQUIRED = "    }\n\n    @Test\n    @DisplayName(\"name: 100";

    /**
     * Issue 295: a generated member inserted where the developer wrote one of
     * their own went between the developer's Javadoc and its method - the
     * comment then sat on the generated test and the developer's had none.
     */
    @Test
    public void aHandWrittenMembersJavadocStaysWithItWhenAGeneratedMemberIsInsertedThere() {
        String yours = CONTRACT.replace(
                AFTER_REQUIRED, "    }\n\n" + HAND_WRITTEN + "    @Test\n    @DisplayName(\"name: 100");
        String generated = CONTRACT.replace(
                AFTER_REQUIRED, "    }\n\n" + GENERATED_MEMBER + "    @Test\n    @DisplayName(\"name: 100");
        ThreeWayMerge.Result r = ThreeWayMerge.mergeSource(CONTRACT, yours, generated);
        assertEquals(0, r.conflicts(), r.text());
        String text = r.text();
        // The Javadoc right above its own method, the generated test without one, both whole.
        assertTrue(text.contains(HAND_WRITTEN), text);
        assertTrue(text.contains("    }\n\n" + GENERATED_MEMBER), text);
        assertFalse(
                text.contains("*/\n    @Test\n    @DisplayName(\"name: blank is refused (400 NotBlank)\")\n"
                        + "    void nameNotBlank()"),
                text);
        assertEquals(1, text.split("Written by hand", -1).length - 1, text);
        assertTrue(text.contains("void nameIsRequired()") && text.contains("void nameSize()"), text);
    }

    /** The same with a Javadoc of several lines, a line comment and an annotation more. */
    @Test
    public void aLongerCommentAndMoreAnnotationsStayWithTheirMemberToo() {
        String hand = String.join(
                "\n",
                "    /**",
                "     * Written by hand.",
                "     */",
                "    // until the generator covers it",
                "    @Test",
                "    @Tag(\"mine\")",
                "    @DisplayName(\"name: blank is refused (400 NotBlank)\")",
                "    void nameIsNotBlank() throws Exception {",
                "        assertFieldError(post(ACCOUNT, withValue(newAccount(), \"name\", \"   \")), \"name\", \"NotBlank\");",
                "    }",
                "",
                "");
        String yours = CONTRACT.replace(AFTER_REQUIRED, "    }\n\n" + hand + "    @Test\n    @DisplayName(\"name: 100");
        String generated = CONTRACT.replace(
                AFTER_REQUIRED, "    }\n\n" + GENERATED_MEMBER + "    @Test\n    @DisplayName(\"name: 100");
        ThreeWayMerge.Result r = ThreeWayMerge.mergeSource(CONTRACT, yours, generated);
        assertEquals(0, r.conflicts(), r.text());
        assertTrue(r.text().contains(hand), r.text());
        assertTrue(r.text().contains("    }\n\n" + GENERATED_MEMBER), r.text());
    }

    /** Kotlin: the same merge, with a KDoc. */
    @Test
    public void aHandWrittenMembersKDocStaysWithItInKotlin() {
        String base = t(
                "package com.acme.crm",
                "",
                "import org.junit.jupiter.api.DisplayName",
                "import org.junit.jupiter.api.Test",
                "",
                "class AccountContractTest : CrmContractSupport() {",
                "",
                "    @Test",
                "    @DisplayName(\"name is required (400 NotNull)\")",
                "    fun nameIsRequired() {",
                "        assertFieldError(post(ACCOUNT, withValue(newAccount(), \"name\", null)), \"name\", \"NotNull\")",
                "    }",
                "",
                "    @Test",
                "    @DisplayName(\"name: 100 pass, 101 are refused (400 Size)\")",
                "    fun nameSize() {",
                "        assertFieldError(post(ACCOUNT, withValue(newAccount(), \"name\", text(101))), \"name\", \"Size\")",
                "    }",
                "}");
        String hand = String.join(
                "\n",
                "    /** Written by hand: @notblank is not generated yet. */",
                "    @Test",
                "    @DisplayName(\"name: blank is refused (400 NotBlank)\")",
                "    fun nameIsNotBlank() {",
                "        assertFieldError(post(ACCOUNT, withValue(newAccount(), \"name\", \"   \")), \"name\", \"NotBlank\")",
                "    }",
                "",
                "");
        String member = String.join(
                "\n",
                "    @Test",
                "    @DisplayName(\"name: blank is refused (400 NotBlank)\")",
                "    fun nameNotBlank() {",
                "        assertFieldError(post(ACCOUNT, withValue(newAccount(), \"name\", \"   \")), \"name\", \"NotBlank\")",
                "    }",
                "",
                "");
        String yours = base.replace(AFTER_REQUIRED, "    }\n\n" + hand + "    @Test\n    @DisplayName(\"name: 100");
        String generated =
                base.replace(AFTER_REQUIRED, "    }\n\n" + member + "    @Test\n    @DisplayName(\"name: 100");
        assertFalse(yours.equals(base) || generated.equals(base), "the model did not change");
        ThreeWayMerge.Result r = ThreeWayMerge.mergeSource(base, yours, generated);
        assertEquals(0, r.conflicts(), r.text());
        assertTrue(r.text().contains(hand), r.text());
        assertTrue(r.text().contains("    }\n\n" + member), r.text());
    }

    /**
     * A file the developer did not touch is the generated one, byte for byte;
     * and one where only the developer added is theirs.
     */
    @Test
    public void withoutHandWrittenMembersTheMergeIsTheGeneratedFile() {
        String generated = CONTRACT.replace(
                AFTER_REQUIRED, "    }\n\n" + GENERATED_MEMBER + "    @Test\n    @DisplayName(\"name: 100");
        assertEquals(
                generated,
                ThreeWayMerge.mergeSource(CONTRACT, CONTRACT, generated).text());
        String yours = CONTRACT.replace(
                AFTER_REQUIRED, "    }\n\n" + HAND_WRITTEN + "    @Test\n    @DisplayName(\"name: 100");
        assertEquals(yours, ThreeWayMerge.mergeSource(CONTRACT, yours, CONTRACT).text());
    }

    /** The alignment of base lines to other lines after joinInsertions, the second side unchanged. */
    private static int[] joined(String base, String other, int... m) {
        int[] unchanged = new int[base.split(",").length];
        for (int i = 0; i < unchanged.length; i++) unchanged[i] = i;
        return joined(base, other, base, unchanged, m);
    }

    private static int[] joined(String base, String other, String theirs, int[] mt, int... m) {
        ThreeWayMerge.joinInsertions(
                java.util.Arrays.asList(base.split(",")),
                java.util.Arrays.asList(other.split(",")),
                m,
                java.util.Arrays.asList(theirs.split(",")),
                mt);
        return m;
    }

    /**
     * A member both sides added alike is taken once: the run does not move
     * away from lines the other side added at the same place. And it moves
     * only between two pure insertions - not next to a line either side replaced.
     */
    @Test
    public void aRunDoesNotMoveAwayFromWhatBothAddedAlike() {
        // The developer added X and then J,R; the DSL added J,R at the same place.
        assertArrayEquals(
                new int[] {0, 2, 5}, joined("P,R,S", "P,X,R,J,R,S", "P,R,J,R,S", new int[] {0, 1, 4}, 0, 2, 5));
        // The DSL added something else there: it moves.
        assertArrayEquals(new int[] {0, 4, 5}, joined("P,R,S", "P,X,R,J,R,S", "P,R,K,S", new int[] {0, 1, 3}, 0, 2, 5));
        // At the start, the mirror: the DSL added X,R before R as the developer did.
        assertArrayEquals(
                new int[] {0, 3, 5}, joined("P,R,S", "P,R,X,R,J,S", "P,R,X,R,S", new int[] {0, 3, 4}, 0, 3, 5));
        // Both added X before R; a move down would join the developer's X with J,R and write X twice.
        assertArrayEquals(new int[] {0, 2, 5}, joined("P,R,S", "P,X,R,J,R,S", "P,X,R,S", new int[] {0, 2, 3}, 0, 2, 5));
        assertEquals(
                t("P", "X", "R", "J", "R", "S"),
                ThreeWayMerge.mergeSource(t("P", "R", "S"), t("P", "X", "R", "J", "R", "S"), t("P", "X", "R", "S"))
                        .text());
        // d replaced by J before the run: no move down across it.
        assertArrayEquals(new int[] {0, -1, 2, 5}, joined("a,d,x,b", "a,J,x,K,x,b", 0, -1, 2, 5));

        String base = t("P", "R", "S");
        assertEquals(
                t("P", "X", "R", "J", "R", "S"),
                ThreeWayMerge.mergeSource(base, t("P", "X", "R", "J", "R", "S"), t("P", "R", "J", "R", "S"))
                        .text());
        // Both replaced d by J alike, the developer added more after: no conflict.
        ThreeWayMerge.Result r = ThreeWayMerge.mergeSource(
                t("a", "d", "x", "b"), t("a", "J", "x", "K", "x", "b"), t("a", "J", "x", "b"));
        assertEquals(0, r.conflicts(), r.text());
        assertEquals(t("a", "J", "x", "K", "x", "b"), r.text());
    }

    /** The developer wrote the very test the DSL now writes, with a Javadoc of their own: it is there once. */
    @Test
    public void aMemberTheDeveloperWroteAsTheDslNowWritesItIsThereOnce() {
        String yours = CONTRACT.replace(
                AFTER_REQUIRED,
                "    }\n\n    /** Mine, before the generator wrote it. */\n" + GENERATED_MEMBER
                        + "    @Test\n    @DisplayName(\"name: 100");
        String generated = CONTRACT.replace(
                AFTER_REQUIRED, "    }\n\n" + GENERATED_MEMBER + "    @Test\n    @DisplayName(\"name: 100");
        ThreeWayMerge.Result r = ThreeWayMerge.mergeSource(CONTRACT, yours, generated);
        assertEquals(0, r.conflicts(), r.text());
        assertEquals(yours, r.text());
    }

    /** A kept run between two insertions moves to the end of the second, or to the start of the first. */
    @Test
    public void aRunBetweenTwoInsertionsMovesSoTheyAreOne() {
        // x kept between J and K,x: kept as the last x, one insertion J,x,K.
        assertArrayEquals(new int[] {0, 4, 5}, joined("a,x,b", "a,J,x,K,x,b", 0, 2, 5));
        // x kept between x,J and K: kept as the first x, one insertion J,x,K.
        assertArrayEquals(new int[] {0, 1, 5}, joined("a,x,b", "a,x,J,x,K,b", 0, 3, 5));
        // A run of two lines, and a run at the end of the base.
        assertArrayEquals(new int[] {0, 5, 6, 7}, joined("a,x,y,b", "a,J,x,y,K,x,y,b", 0, 2, 3, 7));
        assertArrayEquals(new int[] {0, 4}, joined("a,x", "a,J,x,K,x", 0, 2));
        // At the start of the base: kept as the first x.
        assertArrayEquals(new int[] {0, 4}, joined("x,b", "x,J,x,K,b", 2, 4));
        // A move that lets an earlier run move: y first, then x - two passes.
        assertArrayEquals(new int[] {0, 6, 7, 8}, joined("a,x,y,b", "a,P,x,y,Q,y,x,y,b", 0, 2, 5, 8));
    }

    /** Nothing moves where there is one insertion, or where the move would cross a line the side deleted. */
    @Test
    public void aRunMovesOnlyToJoinTwoInsertions() {
        // Only one insertion, before or after the run.
        assertArrayEquals(new int[] {0, 3}, joined("x,b", "x,J,x,b", 0, 3));
        assertArrayEquals(new int[] {0, 3}, joined("a,x", "a,J,x,x", 0, 3));
        assertArrayEquals(new int[] {0, 1, 4}, joined("a,x,b", "a,x,J,x,b", 0, 1, 4));
        // Two insertions, but the lines do not repeat the run.
        assertArrayEquals(new int[] {0, 2, 4}, joined("a,x,b", "a,J,x,K,b", 0, 2, 4));
        // The line after the run deleted (d): it does not move down past it ...
        assertArrayEquals(new int[] {0, 2, -1, 5}, joined("a,x,d,b", "a,J,x,K,x,b", 0, 2, -1, 5));
        // ... and the line before deleted: it does not move up past it.
        assertArrayEquals(new int[] {0, -1, 3, 5}, joined("a,d,x,b", "a,x,J,x,K,b", 0, -1, 3, 5));
    }

    private static void assertMerged(String expected, String base, String yours, String generated) {
        ThreeWayMerge.Result r = ThreeWayMerge.merge(base, yours, generated);
        assertEquals(0, r.conflicts(), r.text());
        assertEquals(expected, r.text());
    }
}
