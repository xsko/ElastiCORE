package io.elasticore.springboot3.export;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the two Jackson generations' JsonRows share: the columns' order, views, and the values a sheet types. */
final class ExportColumns {

    private ExportColumns() {}

    /**
     * The names in the order the class declares its fields, a superclass's before its own; a name without a field
     * keeps its place after them, in the order given.
     */
    static List<String> inDeclarationOrder(Class<?> type, List<String> names) {
        List<Class<?>> chain = new ArrayList<Class<?>>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) chain.add(0, c);
        Map<String, Integer> position = new LinkedHashMap<String, Integer>();
        for (Class<?> c : chain) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                if (!position.containsKey(f.getName())) position.put(f.getName(), position.size());
            }
        }
        List<String> declared = new ArrayList<String>();
        List<String> rest = new ArrayList<String>();
        for (String name : names) {
            if (position.containsKey(name)) declared.add(name);
            else rest.add(name);
        }
        declared.sort((a, b) -> Integer.compare(position.get(a), position.get(b)));
        declared.addAll(rest);
        return declared;
    }

    /** Whether a property of these views is written in the view: no views is the mapper's default inclusion. */
    static boolean inView(Class<?>[] views, Class<?> view, boolean byDefault) {
        if (views == null || views.length == 0) return byDefault;
        for (Class<?> v : views) {
            if (v.isAssignableFrom(view)) return true;
        }
        return false;
    }

    /** Whether a sheet writes the value as a date cell. */
    static boolean isDate(Object value) {
        return value instanceof LocalDate
                || value instanceof LocalDateTime
                || value instanceof Instant
                || value instanceof OffsetDateTime
                || value instanceof ZonedDateTime
                || value instanceof Date;
    }

    /** A JSON number as written: a BigDecimal, or the text itself where it is not one (NaN). */
    static Object number(String text) {
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException notADecimal) {
            return text;
        }
    }
}
