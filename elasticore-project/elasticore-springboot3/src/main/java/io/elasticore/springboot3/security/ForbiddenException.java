package io.elasticore.springboot3.security;

import io.elasticore.springboot3.validation.Refusals;

/**
 * The caller is signed in and the operation is not theirs to do: 403.
 *
 * Given a message code, it is said in the request's language as the
 * generated refusals are (see {@code ConflictException}):
 * <pre>
 *   throw new ForbiddenException("review.not-the-writer", "Only the writer may change a review");
 * </pre>
 */
public class ForbiddenException extends RuntimeException {

    /** The message code a translation is found by, or null for a plain message. */
    private final String code;

    /** The values the message is about: {0}, {1} ... of its translations. */
    private final Object[] args;

    public ForbiddenException(String message) {
        super(message);
        this.code = null;
        this.args = new Object[0];
    }

    /** A refusal said by its code; the message is its default wording (a MessageFormat pattern when there are values). */
    public ForbiddenException(String code, String message, Object... args) {
        super(Refusals.format(message, args));
        this.code = code;
        this.args = Refusals.copy(args);
    }

    public String getCode() {
        return code;
    }

    public Object[] getArgs() {
        return args.clone();
    }
}
