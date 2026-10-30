package io.elasticore.springboot3.mybatis;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hands a row read by a generated MyBatis adapter what the aggregate does not
 * let anyone else set: the members of a collection it changes only through
 * its own methods (addX, removeX, replaceX), whose setter it withholds.
 *
 * Reading a row is not changing it, so the adapter fills the field directly,
 * the way MyBatis fills a field without a setter and JPA fills any field.
 */
public final class Rows {

    private Rows() {
    }

    /**
     * The fields found, per class: kept with the class itself, so a class
     * loaded again (a DevTools restart) is looked up afresh and the old one
     * is not held.
     */
    private static final ClassValue<Map<String, Field>> FIELDS = new ClassValue<Map<String, Field>>() {
        @Override
        protected Map<String, Field> computeValue(Class<?> type) {
            return new ConcurrentHashMap<String, Field>();
        }
    };

    /** Sets the field of that name, declared by the row's class or one it extends. */
    public static void set(Object row, String name, Object value) {
        if (row == null) return;
        Field f = field(row.getClass(), name);
        try {
            f.set(row, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot set " + row.getClass().getName() + "." + name, e);
        }
    }

    private static Field field(Class<?> type, String name) {
        Map<String, Field> fields = FIELDS.get(type);
        Field cached = fields.get(name);
        if (cached != null) return cached;
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                fields.put(name, f);
                return f;
            } catch (NoSuchFieldException ignored) {
                // the class it extends, then
            }
        }
        throw new IllegalStateException(type.getName() + " has no field " + name);
    }

    /** The escape character of a LIKE the generated statements write: one every database reads in a literal. */
    public static final char LIKE_ESCAPE = '!';

    /**
     * The pattern of a StartingWith, EndingWith or Containing query: the value
     * with its own % and _ escaped - matched as written, as Spring Data JPA
     * matches it - between the wildcards the query adds. SQL Server also reads
     * [ as a wildcard, escaped there only: Oracle refuses an escape before any
     * other character. Null for a null value, which matches no row. The
     * wildcards are Objects: a statement's OGNL reads '%' as a character.
     */
    public static String like(Object value, Object before, Object after, boolean lowerCase, String databaseId) {
        if (value == null) return null;
        String text = value.toString();
        if (lowerCase) text = text.toLowerCase(Locale.ROOT);
        boolean sqlServer = "sqlserver".equals(databaseId);
        StringBuilder sb = new StringBuilder(text.length() + 4).append(before == null ? "" : before);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == LIKE_ESCAPE || ch == '%' || ch == '_' || (sqlServer && ch == '[')) sb.append(LIKE_ESCAPE);
            sb.append(ch);
        }
        return sb.append(after == null ? "" : after).toString();
    }
}
