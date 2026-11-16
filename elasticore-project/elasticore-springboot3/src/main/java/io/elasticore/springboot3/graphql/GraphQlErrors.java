package io.elasticore.springboot3.graphql;

import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import io.elasticore.springboot3.validation.InvariantViolationException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a GraphQL operation answers when the service refuses it - the same
 * refusals the generated REST API answers with a status, as a GraphQL error
 * with that status and the refusal's code in its extensions:
 *
 * <pre>
 *   NotFoundException, EntityNotFoundException       NOT_FOUND     404
 *   ForbiddenException                               FORBIDDEN     403
 *   NotSignedInException                             UNAUTHORIZED  401
 *   BadRequestException, ConstraintViolationException BAD_REQUEST  400
 *   ConflictException, DuplicateKeyException,
 *   OptimisticLockingFailureException,
 *   DataIntegrityViolationException                  CONFLICT      409
 *   InvariantViolationException  state.*             CONFLICT      409
 *                                reference.*         NOT_FOUND     404
 *                                any other           UNPROCESSABLE 422
 *   PortCallException                                BAD_GATEWAY   502
 * </pre>
 *
 * Anything else is left to the next resolver, and in the end to Spring for
 * GraphQL's own INTERNAL_ERROR, which does not show the exception's message.
 * Classes an application may not have (Spring's data access exceptions, Bean
 * Validation) are recognised by name, so the resolver loads where they are
 * missing.
 */
public class GraphQlErrors extends DataFetcherExceptionResolverAdapter {

    /** The classifications GraphQL's own ErrorType has no word for. */
    public enum Classification implements ErrorClassification {
        CONFLICT,
        UNPROCESSABLE,
        BAD_GATEWAY
    }

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        Refusal r = refusalOf(ex);
        if (r == null) return null;
        Map<String, Object> extensions = new LinkedHashMap<>();
        String code = codeOf(ex);
        if (code != null) extensions.put("code", code);
        extensions.put("status", r.status);
        List<Map<String, Object>> violations = violationsOf(ex);
        if (!violations.isEmpty()) extensions.put("errors", violations);
        return GraphqlErrorBuilder.newError(env)
                .errorType(r.classification)
                .message(ex.getMessage() != null ? ex.getMessage() : r.classification.toString())
                .extensions(extensions)
                .build();
    }

    private static final class Refusal {
        final ErrorClassification classification;
        final int status;

        Refusal(ErrorClassification classification, int status) {
            this.classification = classification;
            this.status = status;
        }
    }

    /** The refusal an exception is, or null for one the generated services do not raise. */
    static Refusal refusalOf(Throwable ex) {
        if (ex instanceof InvariantViolationException) {
            String code = ((InvariantViolationException) ex).getCode();
            if (code != null && code.startsWith("state.")) return new Refusal(Classification.CONFLICT, 409);
            if (code != null && code.startsWith("reference.")) return new Refusal(ErrorType.NOT_FOUND, 404);
            return new Refusal(Classification.UNPROCESSABLE, 422);
        }
        if (is(ex, "jakarta.persistence.EntityNotFoundException")) return new Refusal(ErrorType.NOT_FOUND, 404);
        if (is(ex, "io.elasticore.springboot3.security.ForbiddenException"))
            return new Refusal(ErrorType.FORBIDDEN, 403);
        if (is(ex, "io.elasticore.springboot3.security.NotSignedInException")) {
            return new Refusal(ErrorType.UNAUTHORIZED, 401);
        }
        if (is(ex, "io.elasticore.springboot3.validation.BadRequestException")
                || is(ex, "jakarta.validation.ConstraintViolationException")) {
            return new Refusal(ErrorType.BAD_REQUEST, 400);
        }
        if (is(ex, "io.elasticore.springboot3.validation.ConflictException")
                || is(ex, "org.springframework.dao.DuplicateKeyException")
                || is(ex, "org.springframework.dao.OptimisticLockingFailureException")
                || is(ex, "org.springframework.dao.DataIntegrityViolationException")) {
            return new Refusal(Classification.CONFLICT, 409);
        }
        if (is(ex, "io.elasticore.springboot3.http.PortCallException"))
            return new Refusal(Classification.BAD_GATEWAY, 502);
        return null;
    }

    private static boolean is(Throwable ex, String className) {
        for (Class<?> c = ex.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals(className)) return true;
        }
        return false;
    }

    /** The code a refusal of this library carries (getCode()), or null. */
    private static String codeOf(Throwable ex) {
        try {
            Method m = ex.getClass().getMethod("getCode");
            Object code = m.invoke(ex);
            return code == null ? null : code.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Each broken constraint of a ConstraintViolationException: the property and its message. */
    private static List<Map<String, Object>> violationsOf(Throwable ex) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!is(ex, "jakarta.validation.ConstraintViolationException")) return out;
        try {
            Object set = ex.getClass().getMethod("getConstraintViolations").invoke(ex);
            if (!(set instanceof Iterable)) return out;
            for (Object v : (Iterable<?>) set) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put(
                        "field",
                        String.valueOf(v.getClass().getMethod("getPropertyPath").invoke(v)));
                one.put(
                        "message",
                        String.valueOf(v.getClass().getMethod("getMessage").invoke(v)));
                out.add(one);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // The message alone says it.
        }
        return out;
    }
}
