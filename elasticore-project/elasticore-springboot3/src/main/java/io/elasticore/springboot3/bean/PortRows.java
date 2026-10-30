package io.elasticore.springboot3.bean;

import io.elasticore.springboot3.util.ReflectUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a generated DBMS port adapter over MyBatis does with what the mapper
 * answers and what the call carries - the same as the port runtime does
 * ({@link SqlPortGateway}, DbmsServiceProxyFactory), so a port answers alike
 * whichever runs it: rows as the method's type (a DTO by column label,
 * camel-cased; a map; one value from the first column), the page a call
 * asks for, the sort it names (checked: a column name and a direction, never
 * SQL), and whether a value is there for a conditional line.
 */
public final class PortRows {

    private PortRows() {
    }

    /**
     * Rows as the type the method answers a list of. MyBatis leaves a NULL
     * column out of a row's map, and answers a row of NULLs alone as null: an
     * absent column is a null field, and a null row one of null fields.
     */
    @SuppressWarnings("unchecked")
    public static <O> List<O> list(List<? extends Map<String, Object>> rows, Class<O> type) {
        List<O> out = new ArrayList<O>();
        if (rows == null) return out;
        for (Map<String, Object> read : rows) {
            Map<String, Object> row = read == null ? new LinkedHashMap<String, Object>() : read;
            if (Map.class.isAssignableFrom(type)) {
                Map<String, Object> map = new LinkedHashMap<String, Object>();
                row.forEach((label, value) -> map.put(SqlPortGateway.toCamelCase(label), value));
                out.add((O) map);
            } else {
                out.add(SqlPortGateway.rowToDto(row, type));
            }
        }
        return out;
    }

    /** The first row as the type the method answers, or null for none. */
    public static <O> O first(List<? extends Map<String, Object>> rows, Class<O> type) {
        List<O> all = list(rows == null || rows.isEmpty() ? rows : rows.subList(0, 1), type);
        return all.isEmpty() ? null : all.get(0);
    }

    /** A value of the type a method answers one of, converted as the port runtime converts it. */
    @SuppressWarnings("unchecked")
    public static <O> O value(Object value, Class<O> type) {
        return (O) SqlPortGateway.convertValue(value, type);
    }

    /**
     * Whether a conditional line of the port's SQL is kept (/* if:name *&#47;):
     * the value is there, and a text is not empty.
     */
    public static boolean present(Object value) {
        if (value == null) return false;
        if (value instanceof CharSequence) return ((CharSequence) value).length() > 0;
        return true;
    }

    /** A call's values by their parameter names - name, value, name, value - as the runtime reads several. */
    public static Map<String, Object> named(Object... namesAndValues) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) map.put(String.valueOf(namesAndValues[i]), namesAndValues[i + 1]);
        return map;
    }

    /** The ORDER BY body the call's sort code names, checked term by term; null when it names none. */
    public static String orderBy(Object input) {
        String clause = SqlPortGateway.toOrderByClause(SqlPortGateway.sortCodeOf(input));
        return clause.isEmpty() ? null : clause;
    }

    /**
     * The page a call asks for: a Pageable, an input's getPageable(), a map's
     * page and size, an input's page and size fields - page 0 of 50 when it
     * names none (as DbmsServiceProxyFactory reads it).
     */
    public static Pageable pageable(Object input) {
        if (input instanceof Pageable) {
            return (Pageable) input;
        }
        int defaultPage = 0;
        int defaultPageSize = 50;
        if (input == null) return PageRequest.of(defaultPage, defaultPageSize);

        for (Method method : input.getClass().getMethods()) {
            if ((method.getName().equals("getPageable") || method.getName().equals("pageable"))
                    && Pageable.class.isAssignableFrom(method.getReturnType())
                    && method.getParameterCount() == 0) {
                try {
                    return (Pageable) method.invoke(input);
                } catch (Exception e) {
                    // read as the fields below
                }
            }
        }
        if (input instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) input;
            for (Object v : map.values()) {
                if (v instanceof Pageable) return (Pageable) v;
            }
            Object pageVal = map.get("pageNumber");
            if (pageVal == null) pageVal = map.get("page");
            Object sizeVal = map.get("sizeVal");
            if (sizeVal == null) sizeVal = map.get("size");
            if (pageVal instanceof Number && sizeVal instanceof Number) {
                return PageRequest.of(((Number) pageVal).intValue(), ((Number) sizeVal).intValue());
            }
        } else {
            try {
                Class<?> c = input.getClass();
                Field pageField = ReflectUtils.getField(c, "pageNumber");
                if (pageField == null) pageField = ReflectUtils.getField(c, "page");
                Object pageVal = pageField.get(input);
                if (pageVal instanceof Integer) defaultPage = (Integer) pageVal;
                Field sizeField = ReflectUtils.getField(c, "sizeVal");
                if (sizeField == null) sizeField = ReflectUtils.getField(c, "size");
                Object sizeVal = sizeField.get(input);
                if (sizeVal instanceof Integer) defaultPageSize = (Integer) sizeVal;
            } catch (NullPointerException | IllegalAccessException ignored) {
                // page 0 of 50
            }
        }
        return PageRequest.of(defaultPage, defaultPageSize);
    }
}
