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
 * A refusal of the store - a unique value taken, a row still referenced, a stale
 * version - says its kind (code unique, referenced, required, check, conflict or
 * stale, as the REST errors read it) and never the driver's text, which names the
 * SQL, the index and the value. The status stays 409.
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
        String store = storeRefusalOf(ex);
        String code = store != null ? store : codeOf(ex);
        if (code != null) extensions.put("code", code);
        extensions.put("status", r.status);
        List<Map<String, Object>> violations = violationsOf(ex);
        if (!violations.isEmpty()) extensions.put("errors", violations);
        String message = store != null ? STORE_SAYS.get(store) : ex.getMessage();
        return GraphqlErrorBuilder.newError(env)
                .errorType(r.classification)
                .message(message != null ? message : r.classification.toString())
                .extensions(extensions)
                .build();
    }

    /** What a refusal of the store says instead of its own text: the same sentences the REST API answers. */
    private static final Map<String, String> STORE_SAYS = Map.of(
            "unique", "A unique value is already taken.",
            "referenced", "The row is still referenced by another, or refers to one that does not exist.",
            "required", "A required value is missing.",
            "check", "A value is outside what the column allows.",
            "conflict", "A unique value is already taken, or the row is still referenced by another.",
            "stale", "The row was changed after it was read. Read it again and resend.");

    /**
     * The kind of a refusal the database, MongoDB or the ORM made - unique,
     * referenced, required, check, conflict or stale - or null for one the services or the application made. Its own text
     * names the SQL statement, the index, the row and the value that broke it (an
     * email, say): not the client's business, so only the kind is said. A
     * DuplicateKeyException without a cause is the service's own (a create named a
     * taken identifier) and keeps its message.
     */
    private static String storeRefusalOf(Throwable ex) {
        if (is(ex, "org.springframework.dao.OptimisticLockingFailureException")) return "stale";
        if (is(ex, "org.springframework.dao.DuplicateKeyException")) {
            if (ex.getCause() == null) return null;
            String kind = integrityKind(ex);
            return "conflict".equals(kind) ? "unique" : kind;
        }
        if (!is(ex, "org.springframework.dao.DataIntegrityViolationException")) return null;
        return integrityKind(ex);
    }

    /**
     * The kind of integrity rule, as the generated REST errors read it: from the
     * SQLSTATE every database reports (23502 not null, 23505 unique, 23503 foreign
     * key; H2 23506) - or, where a database says only 23000, from its own error
     * code. "conflict" where it says neither (MongoDB, a driver without a state).
     */
    // Identity on purpose: a cause that is its own cause ends the walk.
    @SuppressWarnings("ReferenceEquality")
    private static String integrityKind(Throwable ex) {
        for (Throwable t = ex; t != null && t.getCause() != t; t = t.getCause()) {
            if (!(t instanceof java.sql.SQLException sql)) continue;
            String state = sql.getSQLState();
            int code = sql.getErrorCode();
            if ("23502".equals(state)) return "required";
            if ("23505".equals(state)) return "unique";
            if ("23503".equals(state) || "23506".equals(state)) return "referenced";
            if ("23513".equals(state) || "23514".equals(state) || code == 3819) return "check";
            if ("23000".equals(state)) {
                if (code == 1048 || code == 1400 || code == 515) return "required";
                if (code == 1062 || code == 1 || code == 2627 || code == 2601) return "unique";
                if (code == 2290 || (code == 547 && isCheck(sql.getMessage()))) return "check";
                if (code == 1451 || code == 1452 || code == 2291 || code == 2292 || code == 547) return "referenced";
            }
        }
        return "conflict";
    }

    /** SQL Server says 547 for a FOREIGN KEY and a CHECK alike; its message names which (in any language). */
    private static boolean isCheck(String message) {
        return message != null
                && java.util.regex.Pattern.compile("(^|\\s)CHECK\\s")
                        .matcher(message)
                        .find();
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
    private static Refusal refusalOf(Throwable ex) {
        if (ex instanceof InvariantViolationException invariant) {
            String code = invariant.getCode();
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
