package io.elasticore.springboot3.mybatis;

import java.lang.reflect.Field;
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

    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<String, Field>();

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
        String key = type.getName() + "#" + name;
        Field cached = FIELDS.get(key);
        if (cached != null) return cached;
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                FIELDS.put(key, f);
                return f;
            } catch (NoSuchFieldException ignored) {
                // the class it extends, then
            }
        }
        throw new IllegalStateException(type.getName() + " has no field " + name);
    }
}
