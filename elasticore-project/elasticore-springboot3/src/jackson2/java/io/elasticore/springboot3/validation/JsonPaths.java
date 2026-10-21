package io.elasticore.springboot3.validation;

import com.fasterxml.jackson.core.exc.InputCoercionException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;

import java.util.List;

/**
 * Jackson 2 (Spring Boot 3): where in a request body a value was refused, and
 * why. Only a mapping failure has a field; a body that is not JSON at all has
 * none, even where Jackson noted the property it was reading at the time.
 * elasticore-springboot4 has the same class on Jackson 3.
 */
final class JsonPaths {

    private static final int DEPTH = 16;

    private JsonPaths() {
    }

    static String field(Throwable ex) {
        if (unparsable(ex)) return null;
        int depth = 0;
        for (Throwable t = ex; t != null && depth < DEPTH; t = next(t), depth++) {
            if (t instanceof JsonMappingException) return render(((JsonMappingException) t).getPath());
        }
        return null;
    }

    /**
     * The message of what refused the value - a creator (Quantity.of, an
     * enum's @JsonCreator fromCode), or a deserializer that threw
     * IllegalArgumentException (a generated enum's CustomDeserializer, which
     * calls fromCode) - or null.
     */
    static String refusedBy(Throwable ex) {
        int depth = 0;
        for (Throwable t = ex; t != null && depth < DEPTH; t = next(t), depth++) {
            if (t instanceof ValueInstantiationException && t.getCause() != null) return t.getCause().getMessage();
            if (t instanceof JsonMappingException && !(t instanceof MismatchedInputException)
                    && t.getCause() instanceof IllegalArgumentException
                    && !(t.getCause() instanceof NumberFormatException)) {
                return t.getCause().getMessage();
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
        }
        return null;
    }

    /** Not JSON: a parse failure anywhere in the chain (a number too large to hold is a value, not a parse failure). */
    private static boolean unparsable(Throwable ex) {
        int depth = 0;
        for (Throwable t = ex; t != null && depth < DEPTH; t = next(t), depth++) {
            if (t instanceof StreamReadException && !(t instanceof InputCoercionException)) return true;
        }
        return false;
    }

    private static Throwable next(Throwable t) {
        return t.getCause() == t ? null : t.getCause();
    }

    private static String render(List<JsonMappingException.Reference> path) {
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference r : path) {
            if (r.getFieldName() != null) {
                if (sb.length() > 0) sb.append('.');
                sb.append(r.getFieldName());
            } else if (r.getIndex() >= 0) {
                sb.append('[').append(r.getIndex()).append(']');
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
