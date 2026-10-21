package io.elasticore.springboot3.validation;

/**
 * A request the caller got wrong in a way no bean constraint describes - a
 * negative page, a sort on a property the entity does not have. The
 * generated API errors answer it with 400, where an IllegalArgumentException
 * from the same place would have been a 500.
 *
 * Given a message code, it is said in the request's language as the
 * generated refusals are (see {@link ConflictException}):
 * <pre>
 *   throw new BadRequestException("loan.due-before-start", "The due date is before the loan starts");
 * </pre>
 */
public class BadRequestException extends RuntimeException {

    /** The message code a translation is found by, or null for a plain message. */
    private final String code;

    /** The values the message is about: {0}, {1} ... of its translations. */
    private final Object[] args;

    public BadRequestException(String message) {
        super(message);
        this.code = null;
        this.args = new Object[0];
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
        this.code = null;
        this.args = new Object[0];
    }

    /** A refusal said by its code; the message is its default wording (a MessageFormat pattern when there are values). */
    public BadRequestException(String code, String message, Object... args) {
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
