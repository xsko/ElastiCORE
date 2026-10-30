package io.elasticore.quality.format;

import com.palantir.javaformat.java.Formatter;
import com.palantir.javaformat.java.JavaFormatterOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void theLinesThatDifferAreThoseOfTheFirstText() {
        assertArrayEquals(new int[] {3, 3}, FormatChangedLines.differingLines("a\nb\nc\nd", "a\nb\nC\nd"));
        assertArrayEquals(new int[] {2, 3}, FormatChangedLines.differingLines("a\nb\nc\nd", "a\nB\nC\nd"));
    }
}
