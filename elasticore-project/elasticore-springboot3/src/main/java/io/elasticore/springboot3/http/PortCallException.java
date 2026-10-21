package io.elasticore.springboot3.http;

import io.elasticore.springboot3.validation.Refusals;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Another system this one called failed it: 502 Bad Gateway.
 *
 * A port (port: in the DSL) answers an error status as a value - the reply
 * with its fields empty - and a call that never got an answer (refused,
 * timed out, unreadable) as an exception of the HTTP client's. Neither says to
 * the client of this API that the fault is upstream. Throw this where the
 * application decides the call failed:
 * <pre>
 *   ShipmentReply reply;
 *   try {
 *       reply = courier.ship(request);
 *   } catch (RestClientException | WebClientException unreachable) {   // spring mode | runtime mode
 *       throw new PortCallException("The courier could not be reached", unreachable);
 *   }
 *   if (reply == null || reply.getTrackingNo() == null) {
 *       throw new PortCallException("The courier did not accept the shipment");
 *   }
 * </pre>
 * Thrown inside a transaction (a @Transactional service method), it rolls
 * the transaction back like any unchecked exception.
 *
 * The generated API errors answer it 502 with code "upstream" and the reason
 * as the detail. It is a ResponseStatusException, so an application whose
 * API errors were generated before it existed still answers 502 (in Spring
 * Boot's error body). Given a message code, the reason is said in the
 * request's language as the generated refusals are.
 */
public class PortCallException extends ResponseStatusException {

    /** The message code a translation is found by, or null for a plain message. */
    private final String code;

    /** The values the message is about: {0}, {1} ... of its translations. */
    private final Object[] args;

    /** The port that was called (domain.PortName), when known. */
    private final String port;

    /** The status the called system answered, when it answered one. */
    private final Integer upstreamStatus;

    /** What the called system answered, when it answered something. */
    private final String upstreamBody;

    public PortCallException(String reason) {
        this(null, null, null, reason, null, null);
    }

    public PortCallException(String reason, Throwable cause) {
        this(null, null, null, reason, cause, null);
    }

    /** A failure said by its code; the reason is its default wording (a MessageFormat pattern when there are values). */
    public PortCallException(String code, String reason, Object... args) {
        this(code, null, null, reason, null, args);
    }

    /**
     * What the called system answered, for the log and for a handler that
     * wants to look: the port, its status, its body.
     */
    public PortCallException(String port, Integer upstreamStatus, String upstreamBody, String reason, Throwable cause) {
        this(null, port, upstreamStatus, upstreamBody, reason, cause, null);
    }

    private PortCallException(String code, String port, Integer upstreamStatus, String reason, Throwable cause,
                              Object[] args) {
        this(code, port, upstreamStatus, null, reason, cause, args);
    }

    private PortCallException(String code, String port, Integer upstreamStatus, String upstreamBody,
                              String reason, Throwable cause, Object[] args) {
        super(HttpStatus.BAD_GATEWAY, Refusals.format(reason, args), cause);
        this.code = code;
        this.args = Refusals.copy(args);
        this.port = port;
        this.upstreamStatus = upstreamStatus;
        this.upstreamBody = upstreamBody;
    }

    public String getCode() {
        return code;
    }

    public Object[] getArgs() {
        return args.clone();
    }

    public String getPort() {
        return port;
    }

    public Integer getUpstreamStatus() {
        return upstreamStatus;
    }

    public String getUpstreamBody() {
        return upstreamBody;
    }
}
