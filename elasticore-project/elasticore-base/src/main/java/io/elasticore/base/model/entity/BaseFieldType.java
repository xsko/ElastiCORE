package io.elasticore.base.model.entity;


import lombok.Getter;

import java.util.stream.Stream;


/**
 *
 */

@Getter
public enum BaseFieldType {


    OBJECT("Object", null, "Object" , null),
    STRING("String", null, "String" ,"string"),
    STRINGARRAY("String[]", null, "String[]" ,null),
    INTEGERARRAY("int[]", null, "Integer[]",null),
    P_INT("int", "int", "Integer" ,"int64"),
    P_LONG("long", "long", "Long" ,"uint64"),
    P_FLOAT("float", "float", "Float" ,"float"),
    P_DOUBLE("double", "double", "Double" ,"double"),
    P_BOOLEAN("boolean", "boolean", "Boolean" ,"bool"),

    DATETIME("DateTime", null, "java.time.LocalDateTime" , null),
    TIME("Time", null, "java.time.LocalTime", null),
    DATE("Date", null, "java.time.LocalDate", null),

    LIST("List", null, "java.util.List", null),
    //MAP("Map", null, "java.util.Map"),

    INTEGER("Integer", "int", "Integer", null),
    LONG("Long", "long", "Long", "long"),
    FLOAT("Float", "float", "Float", "float"),
    DOUBLE("Double", "double", "Double", "double"),
    BOOLEAN("boolean", "boolean", "Boolean", "bool"),

    LocalDateTime("LocalDateTime", null, "java.time.LocalDateTime", null),
    LocalDate("LocalDate", null, "java.time.LocalDate", null),
    LocalTime("LocalTime", null, "java.time.LocalTime", null),

    BIGDECIMAL("BigDecimal", null, "java.math.BigDecimal", null),
    BIGINTEGER("BigInteger", null, "java.math.BigInteger", null),

    // Value types of the runtime library (3.0): money(14.2) and quantity.
    MONEY("Money", null, "io.elasticore.springboot3.domain.Money", null),
    QUANTITY("Quantity", null, "io.elasticore.springboot3.domain.Quantity", null),

    // timestamp (an instant) and blob (bytes), known to the 3.0 rule engine
    // only. Its clean output imports only what it uses, so Instant was written
    // without an import and did not compile. The 2.x generator imports
    // java.time.*, where Instant and byte[] already compiled - its output
    // stays as it was (see ruleEngineTypes).
    INSTANT("Instant", null, "java.time.Instant", null, true),
    BYTES("byte[]", null, "byte[]", "bytes", true),

    UNKNOWN("unknown", null, null, null);


    private final String name;

    private final String primitiveTypeName;
    private final String wrapperClassName;

    private final String protoType;

    /** Known to the 3.0 rule engine only; see {@link #ruleEngineTypes(boolean)}. */
    private final boolean ruleEngineOnly;

    BaseFieldType(String name, String primitiveTypeName, String wrapperClassName, String protoType) {
        this(name, primitiveTypeName, wrapperClassName, protoType, false);
    }

    BaseFieldType(String name, String primitiveTypeName, String wrapperClassName, String protoType,
                  boolean ruleEngineOnly) {
        this.name = name;
        this.primitiveTypeName = primitiveTypeName;
        this.wrapperClassName = wrapperClassName;
        this.protoType = protoType;
        this.ruleEngineOnly = ruleEngineOnly;
    }

    private static volatile boolean ruleEngineTypes = false;

    /**
     * Whether the types only the 3.0 rule engine knows are resolved. The rule
     * engine turns this on while it loads and generates (RulesGenRunner.run)
     * and off again after, so the 2.x generator - the annotation processor,
     * elcore - keeps resolving them as it always did.
     */
    public static void ruleEngineTypes(boolean on) {
        ruleEngineTypes = on;
    }

    public static boolean ruleEngineTypes() {
        return ruleEngineTypes;
    }


    public String getName() {
        return name;
    }

    public String getPrimitiveTypeName() {
        return primitiveTypeName;
    }

    public String getWarpperClassName() {
        return wrapperClassName;
    }

    public static BaseFieldType findByName(String name) {
        BaseFieldType findType = null;
        Stream<BaseFieldType> stream = java.util.Arrays.stream(BaseFieldType.values());


        try {
            findType = stream.filter(s -> s.name.equals(name) && (!s.ruleEngineOnly || ruleEngineTypes)).findFirst().get();
        } catch (RuntimeException re) {

        }

        if (findType == null) {
            try {
                findType = java.util.Arrays.stream(BaseFieldType.values())
                        .filter(s -> s.name.equalsIgnoreCase(name) && (!s.ruleEngineOnly || ruleEngineTypes)).findFirst().get();
            } catch (RuntimeException re) {

            }
        }

        if (findType == null) {
            return UNKNOWN;
        }

        return findType;
    }

    /**
     * Checks if this type is suitable for COUNT aggregate function.
     * COUNT always returns a numeric value.
     */
    public boolean isCountCompatible() {
        return this == P_INT || this == P_LONG || this == INTEGER || this == LONG;
    }

    /**
     * Checks if this type is suitable for SUM aggregate function.
     * SUM returns numeric values.
     */
    public boolean isSumCompatible() {
        return this == P_INT || this == P_LONG || this == INTEGER || this == LONG || 
               this == P_DOUBLE || this == DOUBLE || this == P_FLOAT || this == FLOAT ||
               this == BIGDECIMAL || this == BIGINTEGER;
    }

    /**
     * Checks if this type is suitable for AVG aggregate function.
     * AVG typically returns decimal values.
     */
    public boolean isAvgCompatible() {
        return this == P_DOUBLE || this == DOUBLE || this == P_FLOAT || this == FLOAT ||
               this == BIGDECIMAL || this == BIGINTEGER;
    }

    /**
     * Checks if this type is suitable for MAX/MIN aggregate functions.
     * MAX/MIN can work with most comparable types.
     */
    public boolean isMaxMinCompatible() {
        return this != UNKNOWN && this != OBJECT && this != LIST;
    }

    /**
     * Gets the most appropriate type for a given SQL aggregate function.
     */
    public static BaseFieldType getAggregateReturnType(String aggregateFunction) {
        if (aggregateFunction == null) {
            return UNKNOWN;
        }
        
        String func = aggregateFunction.toLowerCase().trim();
        if (func.contains("count")) {
            return LONG;  // COUNT returns long for safety
        }
        if (func.contains("sum")) {
            return LONG;  // SUM returns long by default, can be overridden
        }
        if (func.contains("avg") || func.contains("average")) {
            return DOUBLE;  // AVG returns decimal
        }
        if (func.contains("max") || func.contains("min")) {
            return STRING;  // MAX/MIN return type depends on column, use String as safe default
        }
        
        return UNKNOWN;
    }

    /**
     * Checks if this type represents a primitive numeric type.
     */
    public boolean isPrimitiveNumeric() {
        return this == P_INT || this == P_LONG || this == P_DOUBLE || 
               this == P_FLOAT || this == INTEGER || this == LONG || 
               this == DOUBLE || this == FLOAT;
    }

    /**
     * Checks if this type can be used in single value queries.
     */
    public boolean isSingleValueCompatible() {
        return this != LIST && this != UNKNOWN && this != OBJECT;
    }
}
