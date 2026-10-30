package io.elasticore.quality.format;

import com.palantir.javaformat.java.Formatter;
import com.palantir.javaformat.java.JavaFormatterOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatChangedLinesTest {

    private final Formatter formatter = Formatter.createFormatter(JavaFormatterOptions.builder()
            .style(JavaFormatterOptions.Style.PALANTIR)
            .build());

    private static final String SOURCE =
            String.join("\n", "class A {", "    int   a  =  1;", "    int   b  =  2;", "}", "");

    @Test
    void onlyTheChangedLinesAreFormatted() throws Exception {
        assertEquals(
                String.join("\n", "class A {", "    int   a  =  1;", "    int b = 2;", "}", ""),
                FormatChangedLines.format(formatter, SOURCE, "3:3"));
    }

    @Test
    void aFormattedLineIsLeftAsItIs() throws Exception {
        String formatted = FormatChangedLines.format(formatter, SOURCE, "3:3");
        assertEquals(formatted, FormatChangedLines.format(formatter, formatted, "3:3"));
    }

    @Test
    void windowsLineEndingsAreKept() throws Exception {
        String crlf = SOURCE.replace("\n", "\r\n");
        assertEquals(
                FormatChangedLines.format(formatter, SOURCE, "2:2").replace("\n", "\r\n"),
                FormatChangedLines.format(formatter, crlf, "2:2"));
    }

    @Test
    void aRangePastTheEndIsIgnored() throws Exception {
        assertEquals(SOURCE, FormatChangedLines.format(formatter, SOURCE, "40:41"));
    }

    @Test
    void checkAnswersUnformattedFilesWithTheirOwnExitCodeAndEndsWithDone(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("A.java");
        Files.write(source, SOURCE.getBytes(StandardCharsets.UTF_8));
        Path list = dir.resolve("list.txt");
        Files.write(list, Arrays.asList(source + "|3:3"), StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int code = FormatChangedLines.run(new String[] {"check", list.toString()}, new PrintStream(out, true, "UTF-8"));

        assertEquals(FormatChangedLines.UNFORMATTED, code);
        assertEquals(Arrays.asList("UNFORMATTED " + source + "|3:3", "DONE"), lines(out));
        assertEquals(SOURCE, new String(Files.readAllBytes(source), StandardCharsets.UTF_8), "check leaves the file");
    }

    @Test
    void applyFormatsAndAFormattedFileThenPasses(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("A.java");
        Files.write(source, SOURCE.getBytes(StandardCharsets.UTF_8));
        Path list = dir.resolve("list.txt");
        Files.write(list, Arrays.asList(source + "|3:3"), StandardCharsets.UTF_8);

        ByteArrayOutputStream applied = new ByteArrayOutputStream();
        assertEquals(
                0,
                FormatChangedLines.run(
                        new String[] {"apply", list.toString()}, new PrintStream(applied, true, "UTF-8")));
        assertEquals(Arrays.asList("FORMATTED " + source, "DONE"), lines(applied));

        ByteArrayOutputStream checked = new ByteArrayOutputStream();
        assertEquals(
                0,
                FormatChangedLines.run(
                        new String[] {"check", list.toString()}, new PrintStream(checked, true, "UTF-8")));
        assertEquals(Arrays.asList("DONE"), lines(checked));
    }

    @Test
    void aFileThatCannotBeFormattedIsAnErrorNeverAPass(@TempDir Path dir) throws Exception {
        Path broken = dir.resolve("B.java");
        Files.write(broken, "class B { void m( }\n".getBytes(StandardCharsets.UTF_8));
        Path missing = dir.resolve("Missing.java");
        Path list = dir.resolve("list.txt");
        Files.write(list, Arrays.asList(broken + "|1:1", missing + "|1:1"), StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int code = FormatChangedLines.run(new String[] {"check", list.toString()}, new PrintStream(out, true, "UTF-8"));

        assertEquals(1, code);
        List<String> lines = lines(out);
        assertEquals(3, lines.size(), lines.toString());
        assertTrue(lines.get(0).startsWith("ERROR " + broken + "|"), lines.toString());
        assertTrue(lines.get(1).startsWith("ERROR " + missing + "|"), lines.toString());
        assertEquals("DONE", lines.get(2));
    }

    @Test
    void wrongArgumentsAreRefused() throws Exception {
        assertEquals(2, FormatChangedLines.run(new String[] {"fix"}, new PrintStream(new ByteArrayOutputStream())));
    }

    private static List<String> lines(ByteArrayOutputStream out) throws Exception {
        return Arrays.asList(out.toString("UTF-8").replace("\r\n", "\n").split("\n"));
    }

    @Test
    void theLinesThatDifferAreThoseOfTheFirstText() {
        assertArrayEquals(new int[] {3, 3}, FormatChangedLines.differingLines("a\nb\nc\nd", "a\nb\nC\nd"));
        assertArrayEquals(new int[] {2, 3}, FormatChangedLines.differingLines("a\nb\nc\nd", "a\nB\nC\nd"));
    }
}
