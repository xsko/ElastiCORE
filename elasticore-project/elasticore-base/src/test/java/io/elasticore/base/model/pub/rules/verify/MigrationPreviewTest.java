package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The migration check's report, from what a run printed and what it would have merged. */
public class MigrationPreviewTest {

    @Test
    public void statusesAreReadOffTheLinesTheRunPrints() throws Exception {
        MigrationPreview p = new MigrationPreview();
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        PrintStream out = p.tee(new PrintStream(console, true, "UTF-8"));
        out.println("    service      src/main/java/a/OrderCoreService.java  MERGED(your edits kept)");
        out.println("    control      src/main/java/a/OrderController.java  CONFLICT");
        out.println("    entity       src/main/java/a/Order.java  WROTE");
        out.println("=== elcore3: 1 written / 0 unchanged ===");
        assertTrue(
                new String(console.toByteArray(), StandardCharsets.UTF_8).contains("CONFLICT"),
                "the console still shows it");
        assertEquals(Integer.valueOf(1), p.counts().get("MERGED"));
        assertEquals(Integer.valueOf(1), p.counts().get("CONFLICT"));
        assertEquals(Collections.singletonList("src/main/java/a/OrderController.java"), p.withStatus("CONFLICT"));
    }

    @Test
    public void aMergeThatTakesAnImportOutIsListedWithIt() {
        MigrationPreview p = new MigrationPreview();
        // The generated code had the wildcard; a line the developer wrote names a class only it imported.
        String base = "package a;\nimport a.dto.*;\nimport java.util.List;\nclass FolderController {\n}\n";
        p.merged(
                "src/main/java/a/FolderController.java",
                base,
                "package a;\nimport a.dto.*;\nimport java.util.List;\nclass FolderController {\n"
                        + "    // by hand\n    FolderSearchDTO search;\n}\n",
                "package a;\nimport a.dto.FolderDTO;\nimport java.util.List;\nclass FolderController {\n"
                        + "    FolderSearchDTO search;\n}\n");
        p.merged("src/main/java/a/Kept.java", "import a.B;\n", "import a.B;\n", "import a.B;\n");
        assertEquals(
                Collections.singletonList("import a.dto.*"),
                p.droppedImports().get("src/main/java/a/FolderController.java"));
        assertEquals(1, p.droppedImports().size());
        String report = p.report("/p");
        assertTrue(report.contains("- `src/main/java/a/FolderController.java`: `import a.dto.*`"), report);
        assertTrue(report.contains("- files that would lose an import your code may need: 1"), report);
    }

    @Test
    public void anImportOnlyTheGeneratedCodeUsedIsNoLoss() {
        MigrationPreview p = new MigrationPreview();
        String base = "package a;\nimport java.util.*;\nimport lombok.Data;\nclass Dto {\n}\n";
        String yours = "package a;\nimport java.util.*;\nimport lombok.Data;\nclass Dto {\n"
                + "    /** Written by hand. */\n    public String ping() { return \"OK\"; }\n}\n";
        String merged =
                "package a;\nimport java.util.List;\nclass Dto {\n    public String ping() { return \"OK\"; }\n}\n";
        p.merged("Dto.java", base, yours, merged);
        assertTrue(p.droppedImports().isEmpty(), p.droppedImports().toString());
    }

    @Test
    public void anImportYouAddedOrAClassYouNameIsKeptInView() {
        MigrationPreview p = new MigrationPreview();
        String base = "package a;\nimport lombok.Data;\nimport java.time.LocalDate;\nclass Dto {\n}\n";
        String yours = "package a;\nimport lombok.Data;\nimport java.time.LocalDate;\nimport x.Mine;\nclass Dto {\n"
                + "    LocalDate day() { return null; }\n}\n";
        p.merged("Dto.java", base, yours, "package a;\nclass Dto {\n    LocalDate day() { return null; }\n}\n");
        assertEquals(
                Arrays.asList("import java.time.LocalDate", "import x.Mine"),
                p.droppedImports().get("Dto.java"));
    }

    @Test
    public void aWildcardIsNeededOnlyForAClassNoKeptImportNames() {
        java.util.Set<String> kept = new java.util.HashSet<String>(Arrays.asList("import a.dto.FolderDTO"));
        assertTrue(MigrationPreview.wildcardNeeded("import a.dto.*", "FolderSearchDTO s;\n", kept));
        assertTrue(!MigrationPreview.wildcardNeeded("import a.dto.*", "FolderDTO s; String t; MAX;\n", kept));
        assertTrue(!MigrationPreview.wildcardNeeded("import a.dto.FolderDTO", "Other o;\n", kept));
        assertTrue(!MigrationPreview.namedIn("import a.*", "a"));
        assertTrue(MigrationPreview.namedIn("import a.B", "new B()"));
        assertTrue(!MigrationPreview.namedIn("import a.B", "new BB()"));
        assertEquals("x();\n", MigrationPreview.addedLines("a\n", "a\nimport q;\n// c\n/* d */\n * e\nx();\n"));
    }

    @Test
    public void aRemovedFileYourCodeUsesComesFirst() {
        MigrationPreview p = new MigrationPreview();
        p.removed(new File("x/CodeRepository.java"), "src/x/CodeRepository.java");
        p.removedUsers("src/x/CodeRepository.java", Arrays.asList("src/x/CodeReader.java"));
        p.leftBehind("src/x/OldThing.java", Collections.<String>emptyList());
        String report = p.report("/p");
        assertEquals(1, p.usedLeftBehind());
        assertTrue(
                report.indexOf("(removed) - used by `src/x/CodeReader.java`") < report.indexOf("## Conflicts"), report);
        assertTrue(report.contains("- `src/x/OldThing.java` - nothing of yours uses it"), report);
    }
}
