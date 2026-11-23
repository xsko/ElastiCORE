package io.elasticore.springboot3.validation;

import java.text.MessageFormat;
import java.util.Locale;

/**
 * What the runtime's refusals with a message code share (ConflictException,
 * BadRequestException, NotFoundException, ForbiddenException,
 * NotSignedInException, PortCallException): their default wording, with its
 * values in.
 */
public final class Refusals {

    private Refusals() {
    }

    /**
     * The message as the exception's own text (logs, getMessage): the pattern
     * with its values, or the pattern as written when it has none or does not
     * read as one.
     */
    public static String format(String pattern, Object... args) {
        if (pattern == null || args == null || args.length == 0) return pattern;
        try {
            return new MessageFormat(pattern, Locale.ROOT).format(args);
        } catch (RuntimeException notAPattern) {
            return pattern;
        }
    }

    /** A copy of the values, never null. */
    public static Object[] copy(Object[] args) {
        return args == null ? new Object[0] : args.clone();
    }

    /**
     * A value from the request as a refusal repeats it: at most 40 characters, a surrogate pair never
     * cut in half, and control, line-separator and invisible format characters - a CR/LF that would
     * fake a log line, a right-to-left override that would reorder it - shown as '?'.
     */
    public static String shown(String value) {
        if (value == null) return null;
        String head = value;
        if (value.length() > 40) {
            int end = Character.isHighSurrogate(value.charAt(39)) ? 39 : 40;
            head = value.substring(0, end) + "...";
        }
        StringBuilder shown = new StringBuilder(head.length());
        for (int i = 0; i < head.length(); ) {
            int cp = head.codePointAt(i);
            int type = Character.getType(cp);
            boolean hidden = Character.isISOControl(cp)
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR
                    || type == Character.FORMAT;
            if (hidden) shown.append('?');
            else shown.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        return shown.toString();
    }
}
