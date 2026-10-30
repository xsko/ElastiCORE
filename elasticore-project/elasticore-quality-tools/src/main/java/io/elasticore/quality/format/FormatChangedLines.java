package io.elasticore.quality.format;

import com.google.common.collect.Range;
import com.palantir.javaformat.java.Formatter;
import com.palantir.javaformat.java.FormatterException;
import com.palantir.javaformat.java.JavaFormatterOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Formats the lines a change adds or edits, and only those, as
 * palantir-java-format formats them - what "git clang-format" does for C++.
 * The rest of a file stays as it is, so a pull request carries no reformatting
 * of code it did not write (gradle/quality.gradle, formatCheck and formatChanged).
 *
 * Arguments: {@code check|apply <list file>}. Each line of the list file is
 * {@code <path>|<first>:<last>,<first>:<last>...}, 1-based and inclusive. In
 * check mode the program prints {@code <path>|<first>:<last>} for every file
 * whose changed lines are not formatted - the lines the formatter would
 * rewrite - and exits with 1; in apply mode it rewrites those files.
 */
public final class FormatChangedLines {

    private FormatChangedLines() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2 || !(args[0].equals("check") || args[0].equals("apply"))) {
            System.err.println("usage: FormatChangedLines check|apply <list file>");
            System.exit(2);
        }
        boolean apply = args[0].equals("apply");
        Formatter formatter = Formatter.createFormatter(JavaFormatterOptions.builder()
                .style(JavaFormatterOptions.Style.PALANTIR)
                .build());
        int unformatted = 0;
        int failed = 0;
        for (String entry : Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8)) {
            if (entry.trim().isEmpty()) {
                continue;
            }
            int bar = entry.lastIndexOf('|');
            Path file = Paths.get(entry.substring(0, bar));
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String formatted;
            try {
                formatted = format(formatter, text, entry.substring(bar + 1));
            } catch (FormatterException e) {
                // A file that does not parse is the compiler's to report.
                System.out.println("ERROR " + file + "|" + e.getMessage().replace('\n', ' '));
                failed++;
                continue;
            }
            if (formatted.equals(text)) {
                continue;
            }
            unformatted++;
            if (apply) {
                Files.write(file, formatted.getBytes(StandardCharsets.UTF_8));
                System.out.println("FORMATTED " + file);
            } else {
                int[] lines = differingLines(text, formatted);
                System.out.println("UNFORMATTED " + file + "|" + lines[0] + ":" + lines[1]);
            }
        }
        System.exit(failed > 0 || (!apply && unformatted > 0) ? 1 : 0);
    }

    /** The text with the given 1-based line ranges formatted, line endings kept. */
    static String format(Formatter formatter, String text, String lineRanges) throws FormatterException {
        String eol = text.contains("\r\n") ? "\r\n" : "\n";
        String lf = text.replace("\r\n", "\n");
        List<Range<Integer>> ranges = new ArrayList<>();
        int[] starts = lineStarts(lf);
        for (String range : lineRanges.split(",")) {
            String[] ends = range.split(":");
            int first = Integer.parseInt(ends[0].trim());
            int last = Integer.parseInt(ends[ends.length - 1].trim());
            if (first > starts.length) {
                continue;
            }
            int from = starts[first - 1];
            int to = last >= starts.length ? lf.length() : starts[last];
            ranges.add(Range.closedOpen(from, Math.max(from + 1, to))
                    .intersection(Range.closedOpen(0, Math.max(1, lf.length()))));
        }
        if (ranges.isEmpty()) {
            return text;
        }
        String out = formatter.formatSource(lf, ranges);
        return eol.equals("\n") ? out : out.replace("\n", eol);
    }

    /** The offset each line starts at. */
    static int[] lineStarts(String lf) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < lf.length(); i++) {
            if (lf.charAt(i) == '\n' && i + 1 < lf.length()) {
                starts.add(i + 1);
            }
        }
        int[] out = new int[starts.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = starts.get(i);
        }
        return out;
    }

    /** The first and the last line, 1-based, at which the two texts differ (of the first text). */
    static int[] differingLines(String a, String b) {
        String[] x = a.replace("\r\n", "\n").split("\n", -1);
        String[] y = b.replace("\r\n", "\n").split("\n", -1);
        int head = 0;
        while (head < x.length && head < y.length && x[head].equals(y[head])) {
            head++;
        }
        int tail = 0;
        while (tail < x.length - head
                && tail < y.length - head
                && x[x.length - 1 - tail].equals(y[y.length - 1 - tail])) {
            tail++;
        }
        int first = head + 1;
        int last = Math.max(first, x.length - tail);
        return new int[] {first, last};
    }
}
