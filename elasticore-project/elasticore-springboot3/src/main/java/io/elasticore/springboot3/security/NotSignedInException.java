package io.elasticore.springboot3.security;

import io.elasticore.springboot3.validation.Refusals;

/**
 * The operation is for signed-in users and the caller is not one: 401.
 *
 * Given a message code, it is said in the request's language as the
 * generated refusals are (see {@code ConflictException}).
 */
public class NotSignedInException extends RuntimeException {

    /** The message code a translation is found by, or null for a plain message. */
    private final String code;

    /** The values the message is about: {0}, {1} ... of its translations. */
    private final Object[] args;

    public NotSignedInException(String message) {
        super(message);
        this.code = null;
        this.args = new Object[0];
    }

    /** A refusal said by its code; the message is its default wording (a MessageFormat pattern when there are values). */
    public NotSignedInException(String code, String message, Object... args) {
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
