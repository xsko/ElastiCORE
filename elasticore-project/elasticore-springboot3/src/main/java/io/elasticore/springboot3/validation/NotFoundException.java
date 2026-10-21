package io.elasticore.springboot3.validation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The row a request names is not there: 404.
 *
 * An EntityNotFoundException, so the generated API errors answer it as they
 * answer the services' own (a problem body, code not-found) - and marked 404
 * itself, so where no handler takes it (an application that switched the
 * generated one off, a controller outside its package) Spring still answers
 * 404 rather than 500. The generated controllers' findById and delete throw it
 * where they used to answer an empty 404.
 *
 * Given a message code, it is said in the request's language as the
 * generated refusals are (see {@link ConflictException}).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends jakarta.persistence.EntityNotFoundException {

    /** The message code a translation is found by, or null for a plain message. */
    private final String code;

    /** The values the message is about: {0}, {1} ... of its translations. */
    private final Object[] args;

    public NotFoundException(String message) {
        super(message);
        this.code = null;
        this.args = new Object[0];
    }

    /** A refusal said by its code; the message is its default wording (a MessageFormat pattern when there are values). */
    public NotFoundException(String code, String message, Object... args) {
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
