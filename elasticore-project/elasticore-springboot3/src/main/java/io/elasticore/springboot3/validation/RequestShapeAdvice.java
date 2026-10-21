package io.elasticore.springboot3.validation;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * What the application answers when Spring MVC refuses a request before any
 * controller method runs - a body that is not JSON, a value its type refuses,
 * a parameter missing, a method, media type or path the application does not
 * serve - in the same RFC 9457 problem body, with a code, as the generated
 * {@code <Domain>ApiErrors} answers for its own refusals:
 *
 * <pre>
 *   400  unreadable                the body is missing or is not JSON
 *   400  invalid, fieldErrors      a value of the wrong type (code typeMismatch: text for a number,
 *                                  an unknown enum code, a quantity below zero), a parameter missing
 *                                  (NotNull), a parameter or body that breaks its constraint
 *   404  not-found                 no such path
 *   405  method-not-allowed        with Allow
 *   406  not-acceptable            415 unsupported-media-type
 * </pre>
 *
 * Registered by {@link RequestShapeErrors}, which the generated
 * {@code <Domain>ApiErrors} imports ({@code @Import(RequestShapeErrors.class)}),
 * and only by it: a component scan of io.elasticore.springboot3 - the old
 * guide's advice to 2.x applications - skips this class (its condition never
 * matches), and no auto-configuration registers it, so an application that
 * does not import RequestShapeErrors answers as before. Registered, it
 * answers for the whole application, hand-written controllers included.
 *
 * Last in line: the generated handlers are consulted before it, and so is an
 * advice of the application's own that handles the same exception (give it
 * an {@code @Order} to be sure). A browser asking for a page (Accept
 * text/html) keeps Spring Boot's error page for 404 and 405.
 *
 * The words are said in the request's language like every other API error:
 * titles and sentences by code ({@code error.<code>.title}, {@code error.<code>})
 * in {@code messages_<lang>.properties} or beside any domain's generated
 * bundle ({@code i18n/<domain>-messages_<lang>.properties}).
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
@Conditional(RequestShapeAdvice.NotScanned.class)
public class RequestShapeAdvice {

    /**
     * Never matches: a component scan, and an {@code @Import} of this class,
     * pass it by. {@link RequestShapeErrors} registers its bean definition
     * directly, which no condition is asked about.
     */
    public static final class NotScanned implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return false;
        }
    }

    private final ErrorMessages messages;

    public RequestShapeAdvice(ObjectProvider<MessageSource> messageSource) {
        // getIfUnique: two MessageSources and no primary is no reason not to start.
        this.messages = new ErrorMessages(messageSource.getIfUnique(), domainBundles());
    }

    /** Every domain's generated bundle on the classpath: i18n/<domain>-messages.properties. */
    static String[] domainBundles() {
        TreeSet<String> names = new TreeSet<String>();
        try {
            Resource[] found = new PathMatchingResourcePatternResolver(RequestShapeAdvice.class.getClassLoader())
                    .getResources("classpath*:i18n/*-messages.properties");
            for (Resource r : found) {
                String file = r.getFilename();
                if (file == null || !file.endsWith("-messages.properties")) continue;
                names.add("i18n/" + file.substring(0, file.length() - ".properties".length()));
            }
        } catch (Exception | LinkageError unreadable) {
            // No bundle to find them in: messages.properties and the defaults still answer.
        }
        return names.toArray(new String[0]);
    }

    /** A body that is not JSON (400 unreadable), or a value in it its type refuses (400 invalid, typeMismatch). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException ex) {
        String field = RequestErrors.field(ex);
        if (field == null) {
            return problem(HttpStatus.BAD_REQUEST, "unreadable", "Invalid request",
                    messages.text("error.unreadable", "The request body is missing or is not valid JSON."));
        }
        return invalid(field, "typeMismatch", RequestErrors.message(ex, field, messages));
    }

    /** A path or query value of the wrong type: GET /item/abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail typeMismatch(MethodArgumentTypeMismatchException ex) {
        return invalid(ex.getName(), "typeMismatch", RequestErrors.message(ex, messages));
    }

    /** A required query parameter that was not sent. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail missingParameter(MissingServletRequestParameterException ex) {
        String name = ex.getParameterName();
        return invalid(name, "NotNull", messages.text("NotNull." + name,
                messages.text("error.missing-value", "is required")));
    }

    /**
     * A body that fails its @Valid check at a controller the generated
     * handlers do not cover - one you wrote, in another package. Theirs is
     * consulted first for their own controllers.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalidBody(MethodArgumentNotValidException ex) {
        List<String> errors = new ArrayList<String>();
        List<Map<String, Object>> fieldErrors = new ArrayList<Map<String, Object>>();
        for (FieldError f : ex.getBindingResult().getFieldErrors()) {
            String message = messages.text(f);
            errors.add(f.getField() + ": " + message);
            fieldErrors.add(fieldError(f.getField(), f.getCode(), message));
        }
        for (org.springframework.validation.ObjectError g : ex.getBindingResult().getGlobalErrors()) {
            errors.add(messages.text(g));
        }
        return invalid(errors, fieldErrors);
    }

    /** A parameter (or a body) of a method that validates its parameters: @RequestParam @Min(1) int page. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail invalidParameters(HandlerMethodValidationException ex) {
        List<String> errors = new ArrayList<String>();
        List<Map<String, Object>> fieldErrors = new ArrayList<Map<String, Object>>();
        for (ParameterValidationResult r : ex.getValueResults()) {
            String field = parameterName(r.getMethodParameter());
            for (MessageSourceResolvable e : r.getResolvableErrors()) {
                // Every code starts with the constraint's name: Min.page, Min.int, Min.
                String[] codes = e.getCodes();
                String code = codes == null || codes.length == 0 ? null
                        : codes[0].indexOf('.') < 0 ? codes[0] : codes[0].substring(0, codes[0].indexOf('.'));
                String message = parameterMessage(r, e);
                errors.add(field + ": " + message);
                fieldErrors.add(fieldError(field, code, message));
            }
        }
        for (ParameterErrors r : ex.getBeanResults()) {
            for (FieldError f : r.getFieldErrors()) {
                String message = messages.text(f);
                errors.add(f.getField() + ": " + message);
                fieldErrors.add(fieldError(f.getField(), f.getCode(), message));
            }
        }
        return invalid(errors, fieldErrors);
    }

    /** A method the path does not serve; Allow says which it does. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> methodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                          HttpServletRequest request) throws Exception {
        declineForBrowsers(ex, request);
        return answer(ex, "method-not-allowed", "Method not allowed", "error.method-not-allowed", ex.getMethod());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> unsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return answer(ex, "unsupported-media-type", "Unsupported media type", "error.unsupported-media-type",
                ex.getContentType() == null ? "" : ex.getContentType().toString());
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ProblemDetail> notAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return answer(ex, "not-acceptable", "Not acceptable", "error.not-acceptable",
                String.valueOf(ex.getSupportedMediaTypes()));
    }

    /** No controller and no static resource for the path. */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ProblemDetail> noSuchPath(Exception ex, HttpServletRequest request) throws Exception {
        declineForBrowsers(ex, request);
        // The path as the client asked for it (the resource path has no leading slash).
        return answer((ErrorResponse) ex, "not-found", "Not found", "error.no-such-path", request.getRequestURI());
    }

    /**
     * A browser asking for a page keeps Spring Boot's error page (whitelabel,
     * or the application's error/404.html): rethrowing the exception being
     * handled makes Spring go on with its default processing, without a warning.
     */
    private static void declineForBrowsers(Exception ex, HttpServletRequest request) throws Exception {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/html")) throw ex;
    }

    /** Spring's status and headers (Allow, Accept), the body said in the request's language with its code. */
    private ResponseEntity<ProblemDetail> answer(ErrorResponse ex, String code, String title,
                                                 String detailCode, Object value) {
        ProblemDetail body = ex.getBody();
        body.setTitle(messages.text("error." + code + ".title", title));
        body.setDetail(messages.text(detailCode, body.getDetail(), value));
        body.setProperty("code", code);
        return ResponseEntity.status(ex.getStatusCode()).headers(ex.getHeaders()).body(body);
    }

    /**
     * A parameter's message: its codes (Min.page, Min ...), then the
     * built-in message in the request's language - the violation behind it
     * is reachable from Spring Framework 6.2 - then its default.
     */
    private String parameterMessage(ParameterValidationResult result, MessageSourceResolvable error) {
        return messages.textOf(error, violationOf(result, error));
    }

    /** The name the client sent the value under: @RequestParam("page"), else the parameter's own name. */
    private static String parameterName(MethodParameter parameter) {
        RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
        if (param != null) {
            if (!param.name().isEmpty()) return param.name();
            if (!param.value().isEmpty()) return param.value();
        }
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        if (path != null) {
            if (!path.name().isEmpty()) return path.name();
            if (!path.value().isEmpty()) return path.value();
        }
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null) {
            if (!header.name().isEmpty()) return header.name();
            if (!header.value().isEmpty()) return header.value();
        }
        String name = parameter.getParameterName();
        return name != null ? name : "arg" + parameter.getParameterIndex();
    }

    private static final Method UNWRAP = unwrapMethod();

    private static Method unwrapMethod() {
        try {
            return ParameterValidationResult.class.getMethod("unwrap", MessageSourceResolvable.class, Class.class);
        } catch (NoSuchMethodException | RuntimeException before62) {
            return null;
        }
    }

    private static Object violationOf(ParameterValidationResult result, MessageSourceResolvable error) {
        if (UNWRAP == null) return null;
        try {
            return UNWRAP.invoke(result, error, Object.class);
        } catch (Exception | LinkageError notThere) {
            return null;
        }
    }

    private ProblemDetail invalid(String field, String code, String message) {
        List<String> errors = new ArrayList<String>();
        errors.add(field + ": " + message);
        List<Map<String, Object>> fieldErrors = new ArrayList<Map<String, Object>>();
        fieldErrors.add(fieldError(field, code, message));
        return invalid(errors, fieldErrors);
    }

    private ProblemDetail invalid(List<String> errors, List<Map<String, Object>> fieldErrors) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "invalid", "Invalid request", String.join("; ", errors));
        body.setProperty("errors", errors);
        body.setProperty("fieldErrors", fieldErrors);
        return body;
    }

    private static Map<String, Object> fieldError(String field, String code, String message) {
        Map<String, Object> e = new LinkedHashMap<String, Object>();
        e.put("field", field);
        e.put("code", code);
        e.put("message", message);
        return e;
    }

    private ProblemDetail problem(HttpStatus status, String code, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(messages.text("error." + code + ".title", title));
        body.setProperty("code", code);
        return body;
    }
}
