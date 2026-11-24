package io.elasticore.springboot3.validation;

/**
 * An aggregate was about to be written in a state it promised never to be in.
 *
 * Thrown by the generated invariant listeners. An IllegalStateException, as
 * it always was - code that caught that still catches this - but a type of
 * its own, so the API can tell a broken business rule (the request asked for
 * something the domain refuses: 409 for a step its state does not allow, 404 for a
 * row it refers to that is not there, 422 for any other rule) from a broken program (500). Spring
 * wraps what a repository throws, so a handler looks for this one in the
 * cause chain rather than at the top.
 */
public class InvariantViolationException extends IllegalStateException {

    /** The message code a translation is found by, or null for a plain message. */
    private final String code;

    /** The values the message is about: {0}, {1} ... of its translations. */
    private final Object[] args;

    public InvariantViolationException(String message) {
        super(message);
        this.code = null;
        this.args = new Object[0];
    }

    /**
     * A refusal the API can say in the caller's language: the code is looked
     * up in the application's messages, the message is what is said when no
     * translation has it, with its {0}, {1} ... filled from the values, as
     * the other refusals fill theirs. A message without a placeholder is kept
     * as it is: the generated checks pass their own sentence, which may quote
     * a value ('CANCELLED') that a MessageFormat would strip.
     */
    public InvariantViolationException(String code, String message, Object... args) {
        super(placeholders(message) ? Refusals.format(message, args) : message);
        this.code = code;
        this.args = args == null ? new Object[0] : args.clone();
    }

    /** Whether the message says {0}, {1} ... somewhere. */
    static boolean placeholders(String message) {
        if (message == null) return false;
        for (int i = message.indexOf('{'); i >= 0 && i + 1 < message.length(); i = message.indexOf('{', i + 1)) {
            if (Character.isDigit(message.charAt(i + 1))) return true;
        }
        return false;
    }

    public String getCode() {
        return code;
    }

    public Object[] getArgs() {
        return args.clone();
    }

    /** This, wherever it sits in the chain of causes, or null. */
    public static InvariantViolationException in(Throwable failure) {
        // Bounded: a cause chain that loops back on itself must not hang the handler.
        Throwable t = failure;
        for (int depth = 0; t != null && depth < 32; depth++, t = t.getCause()) {
            if (t instanceof InvariantViolationException) return (InvariantViolationException) t;
        }
        return null;
    }
}
