package io.elasticore.base.model.pub.rules;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text a rule asked to emit, with its arguments placed.
 *
 * Rules build emitted code through format strings rather than concatenation,
 * and a miscounted call used to reach the generated source intact: passing
 * two arguments to a one-slot format silently drops the second, and a format
 * built from another format leaves a literal "%s" behind. Neither fails at
 * compile time, and neither throws - the wrong text is simply written, to be
 * noticed later by whoever reads the output. The count is checked here so
 * that the call fails where the mistake is.
 *
 * A call with no arguments means its string literally. That matters because
 * emitted code carries per-cent signs of its own - a LIKE pattern, a column
 * name - and formatting those would corrupt them.
 */
final class Formatted {

    private Formatted() {
    }

    /**
     * A conversion as String.format reads one: an optional argument index,
     * flags, width and precision, then the conversion character.
     */
    private static final Pattern CONVERSION =
            Pattern.compile("%(\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z%])");

    static String text(String format, Object... args) {
        if (format == null || args == null || args.length == 0) return format;

        int slots = argumentSlots(format);
        if (slots >= 0 && slots != args.length) {
            throw new IllegalArgumentException(String.format(
                    "Emitted text takes %d argument(s) but %d were given.%n"
                            + "  format: %s%n"
                            + "  Pass text carrying a '%%' as an argument rather than as the format.",
                    slots, args.length, format));
        }
        return String.format(format, args);
    }

    /**
     * How many arguments the format consumes, or -1 when that cannot be told.
     * "%%" is a literal per-cent and "%n" a line separator, so neither takes
     * one; an indexed conversion may take one argument several times, and a
     * format using those is left unchecked rather than guessed at.
     */
    private static int argumentSlots(String format) {
        Matcher m = CONVERSION.matcher(format);
        int slots = 0;
        while (m.find()) {
            if (m.group(1) != null) return -1;              // indexed: %1$s
            char conversion = m.group(2).charAt(0);
            if (conversion == '%' || conversion == 'n') continue;
            slots++;
        }
        return slots;
    }
}
