package io.elasticore.springboot3.validation;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * A request the controller could not take as it was sent: a body that is not
 * JSON, or a value its type refuses - text for a number, a card type that is
 * not one, a quantity below zero. {@link RequestShapeAdvice} says these the
 * way a broken constraint is said: the field, and why.
 *
 * The field comes from Jackson's path, which is Jackson 2 or Jackson 3 by the
 * Spring Boot line; JsonPaths is the one class written twice.
 *
 * Every sentence has a code, so a translation beside the domain's bundle says
 * it in the request's language:
 * <pre>
 *   typeMismatch.lines[0].quantity   the field as sent
 *   typeMismatch.lines.quantity      the same field in any element of a list
 *   error.type.number ... .other     why, by the type the field expected
 * </pre>
 * Where the type itself refused the value (Quantity.of, an enum's fromCode),
 * its own message is said, untranslated, unless one of the first two codes is.
 */
public final class RequestErrors {

    private RequestErrors() {
    }

    /** The body field the refused value was for (lines[0].quantity), or null when the body did not get that far. */
    public static String field(HttpMessageNotReadableException ex) {
        return JsonPaths.field(ex);
    }

    /** Why the body value of the field was refused, in the request's language where a translation has it. */
    public static String message(HttpMessageNotReadableException ex, String field, ErrorMessages messages) {
        String translated = translated(field, messages);
        if (translated != null) return translated;
        String refused = JsonPaths.refusedBy(ex);
        if (refused != null) return refused;
        return expected(JsonPaths.targetType(ex), messages);
    }

    /** Why a path or query value was refused: GET /item/abc. */
    public static String message(MethodArgumentTypeMismatchException ex, ErrorMessages messages) {
        String translated = translated(ex.getName(), messages);
        if (translated != null) return translated;
        Class<?> type = ex.getRequiredType();
        if (type != null && type.isEnum()) {
            // A generated enum's PropertyEditor reads its code (fromCode) and says what it refused.
            String refused = refusedByConverter(ex);
            if (refused != null) return refused;
        }
        return expected(type, messages);
    }

    private static String translated(String field, ErrorMessages messages) {
        if (field == null) return null;
        String exact = messages.text("typeMismatch." + field, null);
        if (exact != null) return exact;
        String anyElement = field.replaceAll("\\[[^\\]]*\\]", "");
        return anyElement.equals(field) ? null : messages.text("typeMismatch." + anyElement, null);
    }

    private static String refusedByConverter(Throwable ex) {
        int depth = 0;
        for (Throwable t = ex.getCause(); t != null && depth < 16; t = t.getCause() == t ? null : t.getCause(), depth++) {
            if (t instanceof IllegalArgumentException && !(t instanceof NumberFormatException)) {
                String message = t.getMessage();
                // Spring's own by-name conversion says "No enum constant a.b.C.X": the names below say it better.
                if (message != null && !message.isEmpty() && !message.startsWith("No enum constant")) return message;
            }
        }
        return null;
    }

    static String expected(Class<?> type, ErrorMessages messages) {
        if (type == null) return messages.text("error.type.other", "has the wrong type");
        if (type.isEnum()) {
            StringBuilder names = new StringBuilder();
            Object[] values = type.getEnumConstants();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) names.append(", ");
                names.append(((Enum<?>) values[i]).name());
            }
            return messages.format("error.type.enum", "must be one of: {0}", names.toString());
        }
        if (Number.class.isAssignableFrom(type) || isNumericPrimitive(type)
                || type.getName().startsWith("io.elasticore.springboot3.domain.")) {
            return messages.text("error.type.number", "must be a number");
        }
        if (type == Boolean.class || type == boolean.class) {
            return messages.text("error.type.boolean", "must be true or false");
        }
        if (CharSequence.class.isAssignableFrom(type) || type == char.class || type == Character.class) {
            return messages.text("error.type.text", "must be text");
        }
        if (java.time.temporal.Temporal.class.isAssignableFrom(type) || java.util.Date.class.isAssignableFrom(type)) {
            return messages.text("error.type.datetime", "must be a date or time");
        }
        return messages.text("error.type.other", "has the wrong type");
    }

    private static boolean isNumericPrimitive(Class<?> type) {
        return type == int.class || type == long.class || type == short.class || type == byte.class
                || type == double.class || type == float.class;
    }
}
