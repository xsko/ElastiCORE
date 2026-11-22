package io.elasticore.springboot3.export;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * Whether a sheet may write the date itself for what the JSON writes as text: a date the property's own format
     * (a {@code @JsonFormat} pattern) shows in full - the year, month and day, and for a date with a time its hours
     * and minutes too - or shows without a pattern. A pattern that leaves a part out ({@code yyyy-MM}) keeps the text,
     * as the response shows it.
     */
    static boolean dateInFull(Object raw, com.fasterxml.jackson.annotation.JsonFormat format) {
        if (!isDate(raw)) return false;
        String pattern = format == null ? "" : format.pattern().replaceAll("'[^']*'", "");
        if (pattern.isEmpty()) return true;
        boolean day = (pattern.contains("yyyy") || pattern.contains("uuuu"))
                && pattern.contains("MM")
                && pattern.contains("dd");
        if (!day) return false;
        if (raw instanceof LocalDate) return true;
        return pattern.contains("HH") && pattern.contains("mm");
    }

    /** The names an enumeration's label column goes by, in the order the generator looks for one. */
    private static final String[] LABEL_NAMES = {"label", "name", "desc", "description", "title"};

    /** Each enumeration's getters of those names that answer text, in that order. */
    private static final Map<Class<?>, List<Method>> LABEL_GETTERS = new ConcurrentHashMap<Class<?>, List<Method>>();

    /**
     * A constant's label column as the generated enumeration holds it: the first of label, name, desc, description
     * and title that is not the code the JSON shows - the column the label bundle's default file holds where it holds
     * the enumeration. Null when it has none (or it is empty there).
     */
    static String labelColumn(Enum<?> constant, Object shown) {
        Class<?> type = constant.getDeclaringClass();
        for (Method getter : LABEL_GETTERS.computeIfAbsent(type, ExportColumns::labelGetters)) {
            Object text;
            try {
                text = getter.invoke(constant);
            } catch (ReflectiveOperationException | RuntimeException unreadable) {
                continue;
            }
            // The column the JSON carries is the code, not a label.
            if (text == null || text.equals(shown)) continue;
            String said = text.toString().trim();
            return said.isEmpty() ? null : said;
        }
        return null;
    }

    private static List<Method> labelGetters(Class<?> type) {
        List<Method> out = new ArrayList<Method>();
        for (String name : LABEL_NAMES) {
            try {
                Method getter = type.getMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
                if (getter.getReturnType() == String.class && !Modifier.isStatic(getter.getModifiers()))
                    out.add(getter);
            } catch (NoSuchMethodException none) {
                // Not a column of this enumeration.
            }
        }
        return out;
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
