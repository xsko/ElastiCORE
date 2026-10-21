package io.elasticore.springboot3.validation;

import org.springframework.util.ClassUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.ValueInstantiationException;

import java.util.List;

/**
 * Jackson 3 (Spring Boot 4): where in a request body a value was refused, and
 * why. Only a mapping failure has a field; a body that is not JSON at all has
 * none, even where Jackson noted the property it was reading at the time.
 * elasticore-springboot3 has the same class on Jackson 2.
 *
 * Jackson 3 keeps the path on the first of its exceptions in the chain,
 * whichever kind it is - a number too large for an int keeps it on the
 * coercion failure itself.
 *
 * A Boot 4 application whose web layer was switched back to Jackson 2
 * (MappingJackson2HttpMessageConverter) sends Jackson 2's exceptions: they
 * are read the way elasticore-springboot3 reads them, where Jackson 2 is on
 * the class path.
 */
final class JsonPaths {

    private static final int DEPTH = 16;

    private static final boolean JACKSON2 = ClassUtils.isPresent(
            "com.fasterxml.jackson.databind.JsonMappingException", JsonPaths.class.getClassLoader());

    private JsonPaths() {
    }

    static String field(Throwable ex) {
        if (unparsable(ex)) return null;
        int depth = 0;
        for (Throwable t = ex; t != null && depth < DEPTH; t = next(t), depth++) {
            if (t instanceof JacksonException) return render(((JacksonException) t).getPath());
            if (JACKSON2 && Jackson2.isMapping(t)) return Jackson2.path(t);
        }
        return null;
    }

    /**
     * The message of what refused the value - a creator (Quantity.of, an
     * enum's @JsonCreator fromCode), or a deserializer that threw
     * IllegalArgumentException - or null.
     */
    static String refusedBy(Throwable ex) {
        int depth = 0;
        for (Throwable t = ex; t != null && depth < DEPTH; t = next(t), depth++) {
            if (t instanceof ValueInstantiationException && t.getCause() != null) return t.getCause().getMessage();
            if (t instanceof JacksonException && !(t instanceof MismatchedInputException)
                    && t.getCause() instanceof IllegalArgumentException
                    && !(t.getCause() instanceof NumberFormatException)) {
                return t.getCause().getMessage();
            }
            if (JACKSON2) {
                String refused = Jackson2.refusedBy(t);
                if (refused != null) return refused;
            }
        }
        return null;
    }

    /** The type the field expected, where Jackson says: a number, an enum, a date. */
    static Class<?> targetType(Throwable ex) {
        int depth = 0;
        for (Throwable t = ex; t != null && depth < DEPTH; t = next(t), depth++) {
            if (t instanceof MismatchedInputException) return ((MismatchedInputException) t).getTargetType();
            if (t instanceof InputCoercionException) return ((InputCoercionException) t).getTargetType();
            if (JACKSON2) {
                Class<?> type = Jackson2.targetType(t);
                if (type != null) return type;
            }
        }
        return null;
    }

    /** Not JSON: a parse failure anywhere in the chain (a number too large to hold is a value, not a parse failure). */
    private static boolean unparsable(Throwable ex) {
        int depth = 0;
        for (Throwable t = ex; t != null && depth < DEPTH; t = next(t), depth++) {
            if (t instanceof StreamReadException && !(t instanceof InputCoercionException)) return true;
            if (JACKSON2 && Jackson2.unparsable(t)) return true;
        }
        return false;
    }

    private static Throwable next(Throwable t) {
        return t.getCause() == t ? null : t.getCause();
    }

    private static String render(List<JacksonException.Reference> path) {
        StringBuilder sb = new StringBuilder();
        for (JacksonException.Reference r : path) {
            if (r.getPropertyName() != null) {
                if (sb.length() > 0) sb.append('.');
                sb.append(r.getPropertyName());
            } else if (r.getIndex() >= 0) {
                sb.append('[').append(r.getIndex()).append(']');
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Everything that names Jackson 2, loaded only where Jackson 2 is there. */
    private static final class Jackson2 {

        static boolean isMapping(Throwable t) {
            return t instanceof com.fasterxml.jackson.databind.JsonMappingException;
        }

        static String path(Throwable t) {
            StringBuilder sb = new StringBuilder();
            for (com.fasterxml.jackson.databind.JsonMappingException.Reference r
                    : ((com.fasterxml.jackson.databind.JsonMappingException) t).getPath()) {
                if (r.getFieldName() != null) {
                    if (sb.length() > 0) sb.append('.');
                    sb.append(r.getFieldName());
                } else if (r.getIndex() >= 0) {
                    sb.append('[').append(r.getIndex()).append(']');
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        }

        static String refusedBy(Throwable t) {
            if (t instanceof com.fasterxml.jackson.databind.exc.ValueInstantiationException && t.getCause() != null) {
                return t.getCause().getMessage();
            }
            if (t instanceof com.fasterxml.jackson.databind.JsonMappingException
                    && !(t instanceof com.fasterxml.jackson.databind.exc.MismatchedInputException)
                    && t.getCause() instanceof IllegalArgumentException
                    && !(t.getCause() instanceof NumberFormatException)) {
                return t.getCause().getMessage();
            }
            return null;
        }

        static Class<?> targetType(Throwable t) {
            if (t instanceof com.fasterxml.jackson.databind.exc.MismatchedInputException) {
                return ((com.fasterxml.jackson.databind.exc.MismatchedInputException) t).getTargetType();
            }
            if (t instanceof com.fasterxml.jackson.core.exc.InputCoercionException) {
                return ((com.fasterxml.jackson.core.exc.InputCoercionException) t).getTargetType();
            }
            return null;
        }

        static boolean unparsable(Throwable t) {
            return t instanceof com.fasterxml.jackson.core.exc.StreamReadException
                    && !(t instanceof com.fasterxml.jackson.core.exc.InputCoercionException);
        }
    }
}
