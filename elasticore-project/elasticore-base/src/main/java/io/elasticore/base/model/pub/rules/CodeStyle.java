package io.elasticore.base.model.pub.rules;

/**
 * The house style a project writes its generated code in: how far a level of
 * indentation reaches, what ends a line, and whether the file ends with one.
 *
 * Rules and templates never consult this. They write their natural
 * indentation - a tab, or four spaces per level - and the style is applied
 * once to the rendered file, so a project can change its mind without any
 * rule being rewritten.
 *
 * Nothing is applied by default. The compatibility mode's contract is that
 * its output matches 2.x byte for byte, and a house style would break that by
 * definition, so a project opts in.
 */
public final class CodeStyle {

    /** Spaces one level is written with when a file uses spaces. */
    private static final int SOURCE_LEVEL_WIDTH = 4;

    /** Leaves every file exactly as it was rendered. */
    public static final CodeStyle AS_WRITTEN = new CodeStyle(null, null, null);

    private final String indentUnit;
    private final String lineEnding;
    private final Boolean finalNewline;

    private CodeStyle(String indentUnit, String lineEnding, Boolean finalNewline) {
        this.indentUnit = indentUnit;
        this.lineEnding = lineEnding;
        this.finalNewline = finalNewline;
    }

    /**
     * The style a build asked for:
     *
     *   -Delasticore.style.indent=tab|2|4|8
     *   -Delasticore.style.lineEnding=lf|crlf|platform
     *   -Delasticore.style.finalNewline=true|false
     *
     * An unset setting is left as the rendered file has it.
     */
    public static CodeStyle fromSystemProperties() {
        return new CodeStyle(
                indentUnitOf(System.getProperty("elasticore.style.indent")),
                lineEndingOf(System.getProperty("elasticore.style.lineEnding")),
                booleanOf(System.getProperty("elasticore.style.finalNewline")));
    }

    /** "tab" for a tab, a number for that many spaces. */
    public static CodeStyle of(String indent, String lineEnding, Boolean finalNewline) {
        return new CodeStyle(indentUnitOf(indent), lineEndingOf(lineEnding), finalNewline);
    }

    /** True when the build left every setting alone. */
    public boolean isAsWritten() {
        return indentUnit == null && lineEnding == null && finalNewline == null;
    }

    /** What this style asks for, for the build log. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("indent=").append(indentUnit == null ? "as written"
                : "\t".equals(indentUnit) ? "tab" : indentUnit.length() + " spaces");
        sb.append(", lineEnding=").append(lineEnding == null ? "platform"
                : "\r\n".equals(lineEnding) ? "crlf" : "lf");
        sb.append(", finalNewline=").append(finalNewline == null ? "as written" : finalNewline);
        return sb.toString();
    }

    /** The line ending to write with, or null to keep the platform's. */
    public String lineEnding() {
        return lineEnding;
    }

    /**
     * Rewrites a rendered file in this style.
     *
     * Indentation is re-derived rather than substituted: a line's level is
     * what its leading whitespace already says - a tab, or four spaces - and
     * whatever is left over is kept, which is how the space that opens a
     * javadoc continuation survives.
     */
    public String apply(String rendered) {
        if (isAsWritten()) return rendered;

        String[] lines = rendered.replace("\r\n", "\n").split("\n", -1);

        // A trailing newline leaves an empty last element that is not a line.
        boolean endedWithNewline = lines.length > 0 && lines[lines.length - 1].isEmpty();
        int count = endedWithNewline ? lines.length - 1 : lines.length;

        String ending = lineEnding == null ? "\n" : lineEnding;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) out.append(ending);
            out.append(indentUnit == null ? lines[i] : reindent(lines[i]));
        }

        boolean wantsFinalNewline = finalNewline == null ? endedWithNewline : finalNewline;
        if (wantsFinalNewline && count > 0) out.append(ending);
        return out.toString();
    }

    /** One line, its indentation restated in this style. */
    private String reindent(String line) {
        int levels = 0;
        int spaces = 0;
        int i = 0;
        for (; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\t') {
                levels++;
            } else if (ch == ' ') {
                spaces++;
            } else {
                break;
            }
        }
        // A blank line carries no indentation worth restating.
        if (i == line.length()) return "";

        levels += spaces / SOURCE_LEVEL_WIDTH;

        StringBuilder sb = new StringBuilder();
        for (int n = 0; n < levels; n++) sb.append(indentUnit);
        // What did not fill a level belongs to the line, not to its depth.
        for (int n = 0; n < spaces % SOURCE_LEVEL_WIDTH; n++) sb.append(' ');
        return sb.append(line, i, line.length()).toString();
    }

    private static String indentUnitOf(String setting) {
        if (setting == null || setting.trim().isEmpty()) return null;
        String value = setting.trim();
        if ("tab".equalsIgnoreCase(value) || "\t".equals(value)) return "\t";
        try {
            int width = Integer.parseInt(value);
            if (width <= 0) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < width; i++) sb.append(' ');
            return sb.toString();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "elasticore.style.indent must be 'tab' or a number of spaces: " + setting);
        }
    }

    private static String lineEndingOf(String setting) {
        if (setting == null || setting.trim().isEmpty()) return null;
        String value = setting.trim();
        if ("lf".equalsIgnoreCase(value)) return "\n";
        if ("crlf".equalsIgnoreCase(value)) return "\r\n";
        if ("platform".equalsIgnoreCase(value)) return System.lineSeparator();
        throw new IllegalArgumentException(
                "elasticore.style.lineEnding must be lf, crlf or platform: " + setting);
    }

    private static Boolean booleanOf(String setting) {
        if (setting == null || setting.trim().isEmpty()) return null;
        return Boolean.valueOf(setting.trim());
    }
}
