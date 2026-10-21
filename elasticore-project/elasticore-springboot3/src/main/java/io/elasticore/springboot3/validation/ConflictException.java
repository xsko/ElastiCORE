package io.elasticore.springboot3.validation;

/**
 * A request that conflicts with what is stored, in a way no database constraint
 * describes - no copy of the book is left to lend, the loan was already returned.
 * The generated API errors answer it with 409 and this message, where a
 * DataIntegrityViolationException from the same place would have been answered
 * with the generic text for a broken unique or foreign key.
 *
 * Given a message code, it is said in the request's language the way the
 * generated refusals are: the code is looked up in {@code messages_<lang>.properties}
 * and beside the domain's bundle ({@code i18n/<domain>-messages_ko.properties}), the
 * message is what is said where no translation has it, and the body's "code"
 * is that code:
 * <pre>
 *   throw new ConflictException("loan.no-copy-left", "No copy of {0} is left to lend", isbn);
 * </pre>
 */
public class ConflictException extends RuntimeException {

    /** The message code a translation is found by, or null for a plain message. */
    private final String code;

    /** The values the message is about: {0}, {1} ... of its translations. */
    private final Object[] args;

    public ConflictException(String message) {
        super(message);
        this.code = null;
        this.args = new Object[0];
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
        this.code = null;
        this.args = new Object[0];
    }

    /**
     * A refusal said by its code: the message is the default wording, a
     * MessageFormat pattern when there are values ({0}; write ' as '').
     */
    public ConflictException(String code, String message, Object... args) {
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
