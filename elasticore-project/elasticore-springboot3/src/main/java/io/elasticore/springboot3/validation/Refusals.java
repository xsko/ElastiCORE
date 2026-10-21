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
}
