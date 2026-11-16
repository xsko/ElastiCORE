package io.elasticore.springboot3.graphql;

import io.elasticore.springboot3.domain.Money;
import io.elasticore.springboot3.domain.Quantity;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which GraphQL type a Java value is, in one place.
 *
 * The schema writer asks it when it writes {@code graphql/*.graphqls} at
 * build time, and {@link GraphQlScalars} answers for the same names when the
 * application runs, so a field is declared as the scalar the server then
 * reads and writes it as. A value that is not here is not a scalar: an enum,
 * a list, or a class whose properties become a type of their own.
 */
public final class GraphQlTypes {

    /** The scalars GraphQL itself defines - never declared in a schema file. */
    public static final String STRING = "String";

    public static final String INT = "Int";
    public static final String FLOAT = "Float";
    public static final String BOOLEAN = "Boolean";

    /** The scalars this library declares and wires, each with what it carries. */
    private static final Map<String, String> CUSTOM;

    private static final Map<Class<?>, String> BY_CLASS;

    static {
        Map<String, String> custom = new LinkedHashMap<>();
        custom.put("Long", "A 64-bit integer.");
        custom.put("BigDecimal", "A decimal number, written as a JSON number or a string.");
        custom.put("BigInteger", "An integer of any size, written as a JSON number or a string.");
        custom.put("Date", "A date without a time, ISO-8601: 2026-11-16.");
        custom.put("DateTime", "A date and time without an offset, ISO-8601: 2026-11-16T09:30:00.");
        custom.put("Time", "A time of day, ISO-8601: 09:30:00.");
        custom.put("Instant", "An instant, ISO-8601 with an offset: 2026-11-16T00:30:00Z.");
        custom.put("OffsetDateTime", "A date and time with its offset, ISO-8601: 2026-11-16T09:30:00+09:00.");
        custom.put("UUID", "A UUID in its text form.");
        custom.put("Base64", "Bytes, Base64-encoded.");
        custom.put("Money", "An amount of money, written as a decimal number.");
        custom.put("Quantity", "A count of items, written as an integer.");
        custom.put("JSON", "Any JSON value: a map or an untyped object.");
        CUSTOM = Collections.unmodifiableMap(custom);

        Map<Class<?>, String> byClass = new LinkedHashMap<>();
        byClass.put(String.class, STRING);
        byClass.put(Character.class, STRING);
        byClass.put(char.class, STRING);
        byClass.put(Integer.class, INT);
        byClass.put(int.class, INT);
        byClass.put(Short.class, INT);
        byClass.put(short.class, INT);
        byClass.put(Byte.class, INT);
        byClass.put(byte.class, INT);
        byClass.put(Long.class, "Long");
        byClass.put(long.class, "Long");
        byClass.put(Double.class, FLOAT);
        byClass.put(double.class, FLOAT);
        byClass.put(Float.class, FLOAT);
        byClass.put(float.class, FLOAT);
        byClass.put(Boolean.class, BOOLEAN);
        byClass.put(boolean.class, BOOLEAN);
        byClass.put(BigDecimal.class, "BigDecimal");
        byClass.put(BigInteger.class, "BigInteger");
        byClass.put(LocalDate.class, "Date");
        byClass.put(LocalDateTime.class, "DateTime");
        byClass.put(LocalTime.class, "Time");
        byClass.put(Instant.class, "Instant");
        byClass.put(OffsetDateTime.class, "OffsetDateTime");
        byClass.put(ZonedDateTime.class, "OffsetDateTime");
        byClass.put(UUID.class, "UUID");
        byClass.put(byte[].class, "Base64");
        byClass.put(Money.class, "Money");
        byClass.put(Quantity.class, "Quantity");
        byClass.put(Object.class, "JSON");
        BY_CLASS = Collections.unmodifiableMap(byClass);
    }

    private GraphQlTypes() {}

    /**
     * The scalar a value of this class is, or null when it is not one.
     * A map of any kind is JSON: GraphQL has no map type, and a field typed
     * as one could otherwise not be declared at all.
     */
    public static String scalarOf(Class<?> type) {
        String scalar = BY_CLASS.get(type);
        if (scalar != null) return scalar;
        if (Map.class.isAssignableFrom(type)) return "JSON";
        return null;
    }

    /** True for a scalar this library declares in the shared schema file and wires at run time. */
    public static boolean isCustom(String scalar) {
        return CUSTOM.containsKey(scalar);
    }

    /** The scalars this library declares, by name, with the description written above each. */
    public static Map<String, String> customScalars() {
        return CUSTOM;
    }

    /**
     * The name of the type a class is in the schema when it is answered:
     * the class's simple name without a trailing DTO - MemberDTO is Member -
     * so a schema reads in the model's words, not the Java ones.
     */
    public static String outputName(Class<?> type) {
        String name = type.getSimpleName();
        if (name.endsWith("DTO") && name.length() > 3) name = name.substring(0, name.length() - 3);
        return name;
    }

    /**
     * The name of the type a class is when it is sent: an input type cannot
     * share its name with an output type, so it is the output name and Input.
     */
    public static String inputName(Class<?> type) {
        return outputName(type) + "Input";
    }

    /** The page of a search that answers pages: MemberPage, of Member. */
    public static String pageName(String itemName) {
        return itemName + "Page";
    }
}
