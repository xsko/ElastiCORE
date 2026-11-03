package io.elasticore.springboot3.http;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * An HTTP port call that the other system answered with an error status
 * (4xx or 5xx) the port could not return as a value.
 *
 * It carries what the other side said: the status, the response headers and
 * the body as text, with the port (domain.PortName) and the method that was
 * called. A service tells a 404 from a 409 from a 500 by the status:
 * <pre>
 *   try {
 *       label = courier.printLabel(request);
 *   } catch (HttpPortException e) {
 *       if (e.getStatusCode() == 409) { ... }
 *       throw new PortCallException(e.getPort(), e.getStatusCode(), e.getResponseBody(),
 *               "The courier refused the label", e);
 *   }
 * </pre>
 * A call that got no answer at all (refused, timed out) is not this exception
 * but the HTTP client's own (WebClientRequestException).
 */
public class HttpPortException extends RuntimeException {

    /** How much of the body the message shows; the getter has all of it. */
    static final int BODY_IN_MESSAGE = 500;

    private final String port;

    private final String method;

    private final int statusCode;

    private final HttpHeaders responseHeaders;

    private final String responseBody;

    public HttpPortException(
            String port,
            String method,
            int statusCode,
            HttpHeaders responseHeaders,
            String responseBody,
            Throwable cause) {
        super(message(port, method, statusCode, responseBody), cause);
        this.port = port;
        this.method = method;
        this.statusCode = statusCode;
        HttpHeaders copy = new HttpHeaders();
        if (responseHeaders != null) {
            copy.addAll(responseHeaders);
        }
        this.responseHeaders = HttpHeaders.readOnlyHttpHeaders(copy);
        this.responseBody = responseBody == null ? "" : responseBody;
    }

    /** The same answer, said of the port and method that were called. */
    HttpPortException called(String port, String method) {
        HttpPortException named =
                new HttpPortException(port, method, statusCode, responseHeaders, responseBody, getCause());
        named.setStackTrace(getStackTrace());
        return named;
    }

    private static String message(String port, String method, int statusCode, String body) {
        StringBuilder text = new StringBuilder();
        if (port != null) {
            text.append(port);
            if (method != null) {
                text.append('.').append(method);
            }
            text.append(": ");
        }
        text.append("HTTP ").append(statusCode);
        HttpStatus known = HttpStatus.resolve(statusCode);
        if (known != null) {
            text.append(' ').append(known.getReasonPhrase());
        }
        if (body != null && !body.isEmpty()) {
            text.append(" - ");
            if (body.length() > BODY_IN_MESSAGE) {
                text.append(body, 0, BODY_IN_MESSAGE).append("...");
            } else {
                text.append(body);
            }
        }
        return text.toString();
    }

    /** The port that was called (domain.PortName), or null when called outside a port. */
    public String getPort() {
        return port;
    }

    /** The port method that was called, or null when called outside a port. */
    public String getMethod() {
        return method;
    }

    /** The status the other system answered, for example 404. */
    public int getStatusCode() {
        return statusCode;
    }

    /** The headers of the answer (read-only, never null). */
    public HttpHeaders getResponseHeaders() {
        return responseHeaders;
    }

    /** The first value of a header of the answer, or null. */
    public String getResponseHeader(String name) {
        return responseHeaders.getFirst(name);
    }

    /** The body of the answer as text ("" when there was none). */
    public String getResponseBody() {
        return responseBody;
    }
}
