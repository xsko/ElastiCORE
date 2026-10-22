package io.elasticore.base.model.pub.rules;

import java.util.List;

/**
 * Multi-line values written one line per argument, so rule code never spells
 * a newline itself.
 *
 * Most emitted text never needs this: {@code code.line(block, ...)},
 * {@code field.after(...)} and {@code code.varLines(...)} already take one
 * line at a time. What is left are the two shapes a template can force:
 *
 *   - a single slot holding a value that spans lines, such as an annotation
 *     2.x writes across several - see {@link #of}
 *   - a slot sitting inline ahead of the template's own text, where an
 *     optional line has to close itself - see {@link #leading}
 */
public final class Lines {

    private Lines() {
    }

    /** One line per argument. */
    public static String of(String... lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    /** One line per element. */
    public static String join(List<String> lines) {
        return of(lines.toArray(new String[0]));
    }

    /**
     * One line per argument, with every line after the first carrying the
     * given indent. For a slot that sits indented in the template: its own
     * line is placed by the template, the rest have to place themselves.
     */
    public static String indented(String indent, String... lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n').append(indent);
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    /** The same, over a list. */
    public static String indented(String indent, List<String> lines) {
        return indented(indent, lines.toArray(new String[0]));
    }

    /**
     * A line placed ahead of the template text the slot sits inline with, so
     * that text still starts a line of its own. An empty value leaves the
     * slot contributing nothing.
     */
    public static String leading(String line) {
        return line + '\n';
    }
}
