package io.elasticore.springboot3.graphql;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.EnumValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.language.VariableReference;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The scalars {@link GraphQlTypes} names, as the running server reads and
 * writes them.
 *
 * Numbers that do not fit GraphQL's 32-bit Int or its double - a long key, a
 * decimal amount - travel as JSON numbers and are read from numbers or
 * strings; dates and times travel as ISO-8601 text, the way the REST API
 * writes them.
 */
public final class GraphQlScalars {

    private GraphQlScalars() {}

    /** Every scalar {@link GraphQlTypes#customScalars()} declares, ready to wire. */
    public static List<GraphQLScalarType> all() {
        List<GraphQLScalarType> all = new ArrayList<>();
        all.add(number("Long", v -> v.longValueExact(), Long.class));
        all.add(number("BigDecimal", v -> v, BigDecimal.class));
        all.add(number("BigInteger", BigDecimal::toBigIntegerExact, BigInteger.class));
        all.add(text("Date", LocalDate::parse, LocalDate.class));
        all.add(text("DateTime", LocalDateTime::parse, LocalDateTime.class));
        all.add(text("Time", LocalTime::parse, LocalTime.class));
        all.add(text("Instant", Instant::parse, Instant.class));
        all.add(text("OffsetDateTime", OffsetDateTime::parse, OffsetDateTime.class, ZonedDateTime.class));
        all.add(text("UUID", UUID::fromString, UUID.class));
        all.add(base64());
        all.add(money());
        all.add(quantity());
        all.add(json());
        return all;
    }

    private static GraphQLScalarType scalar(String name, Coercing<?, ?> coercing) {
        return GraphQLScalarType.newScalar()
                .name(name)
                .description(GraphQlTypes.customScalars().get(name))
                .coercing(coercing)
                .build();
    }

    /** A number read from a JSON number or a string, and written as a number. */
    private static GraphQLScalarType number(String name, Function<BigDecimal, Object> convert, Class<?> type) {
        return scalar(name, new Coercing<Object, Object>() {
            @Override
            public Object serialize(Object value, GraphQLContext context, Locale locale) {
                if (type.isInstance(value)) return value;
                if (value instanceof Number || value instanceof String) {
                    try {
                        return convert.apply(new BigDecimal(value.toString().trim()));
                    } catch (RuntimeException e) {
                        throw new CoercingSerializeException(name + " cannot hold " + value, e);
                    }
                }
                throw new CoercingSerializeException(
                        name + " cannot hold a " + value.getClass().getName());
            }

            @Override
            public Object parseValue(Object input, GraphQLContext context, Locale locale) {
                return parse(input, CoercingParseValueException::new);
            }

            @Override
            public Object parseLiteral(
                    Value<?> input, CoercedVariables variables, GraphQLContext context, Locale locale) {
                if (input instanceof IntValue)
                    return parse(((IntValue) input).getValue(), CoercingParseLiteralException::new);
                if (input instanceof FloatValue)
                    return parse(((FloatValue) input).getValue(), CoercingParseLiteralException::new);
                if (input instanceof StringValue)
                    return parse(((StringValue) input).getValue(), CoercingParseLiteralException::new);
                throw new CoercingParseLiteralException(name + " is written as a number or a string, not " + input);
            }

            @Override
            public Value<?> valueToLiteral(Object input, GraphQLContext context, Locale locale) {
                // A decimal as text, which every one of these reads back exactly.
                Object value = parseValue(input, context, locale);
                return value instanceof BigDecimal
                        ? StringValue.of(((BigDecimal) value).toPlainString())
                        : new IntValue(new BigInteger(String.valueOf(value)));
            }

            private Object parse(Object input, Function<String, RuntimeException> failure) {
                if (!(input instanceof Number) && !(input instanceof String)) {
                    throw failure.apply(name + " is a number or a string, not " + input);
                }
                try {
                    return convert.apply(new BigDecimal(input.toString().trim()));
                } catch (RuntimeException e) {
                    throw failure.apply(name + " cannot hold " + input);
                }
            }
        });
    }

    /** A value written as ISO-8601 (or its own) text. */
    private static GraphQLScalarType text(String name, Function<String, Object> parse, Class<?>... types) {
        return scalar(name, new Coercing<Object, String>() {
            @Override
            public String serialize(Object value, GraphQLContext context, Locale locale) {
                for (Class<?> type : types) {
                    if (type.isInstance(value)) {
                        if (value instanceof ZonedDateTime)
                            return ((ZonedDateTime) value).toOffsetDateTime().toString();
                        return value.toString();
                    }
                }
                if (value instanceof String) return value.toString();
                throw new CoercingSerializeException(
                        name + " cannot hold a " + value.getClass().getName());
            }

            @Override
            public Object parseValue(Object input, GraphQLContext context, Locale locale) {
                return parse(input, CoercingParseValueException::new);
            }

            @Override
            public Object parseLiteral(
                    Value<?> input, CoercedVariables variables, GraphQLContext context, Locale locale) {
                if (!(input instanceof StringValue)) {
                    throw new CoercingParseLiteralException(name + " is written as a string, not " + input);
                }
                return parse(((StringValue) input).getValue(), CoercingParseLiteralException::new);
            }

            @Override
            public Value<?> valueToLiteral(Object input, GraphQLContext context, Locale locale) {
                return StringValue.of(serialize(parseValue(input, context, locale), context, locale));
            }

            private Object parse(Object input, Function<String, RuntimeException> failure) {
                for (Class<?> type : types) {
                    if (type.isInstance(input)) return input;
                }
                if (!(input instanceof String)) throw failure.apply(name + " is a string, not " + input);
                try {
                    return parse.apply(((String) input).trim());
                } catch (DateTimeParseException | IllegalArgumentException e) {
                    throw failure.apply(name + " cannot read '" + input + "': " + e.getMessage());
                }
            }
        });
    }

    private static GraphQLScalarType base64() {
        return scalar("Base64", new Coercing<byte[], String>() {
            @Override
            public String serialize(Object value, GraphQLContext context, Locale locale) {
                if (value instanceof byte[]) return Base64.getEncoder().encodeToString((byte[]) value);
                throw new CoercingSerializeException(
                        "Base64 cannot hold a " + value.getClass().getName());
            }

            @Override
            public byte[] parseValue(Object input, GraphQLContext context, Locale locale) {
                if (input instanceof byte[]) return (byte[]) input;
                try {
                    return Base64.getDecoder().decode(String.valueOf(input));
                } catch (IllegalArgumentException e) {
                    throw new CoercingParseValueException("Base64 cannot read the value: " + e.getMessage());
                }
            }

            @Override
            public byte[] parseLiteral(
                    Value<?> input, CoercedVariables variables, GraphQLContext context, Locale locale) {
                if (!(input instanceof StringValue))
                    throw new CoercingParseLiteralException("Base64 is written as a string");
                try {
                    return Base64.getDecoder().decode(((StringValue) input).getValue());
                } catch (IllegalArgumentException e) {
                    throw new CoercingParseLiteralException("Base64 cannot read the value: " + e.getMessage());
                }
            }

            @Override
            public Value<?> valueToLiteral(Object input, GraphQLContext context, Locale locale) {
                return StringValue.of(serialize(parseValue(input, context, locale), context, locale));
            }
        });
    }

    private static GraphQLScalarType money() {
        GraphQLScalarType decimal = number("BigDecimal", v -> v, BigDecimal.class);
        @SuppressWarnings("unchecked")
        Coercing<Object, Object> amount = (Coercing<Object, Object>) decimal.getCoercing();
        return scalar("Money", new Coercing<Money, Object>() {
            @Override
            public Object serialize(Object value, GraphQLContext context, Locale locale) {
                if (value instanceof Money) return ((Money) value).toBigDecimal();
                return amount.serialize(value, context, locale);
            }

            @Override
            public Money parseValue(Object input, GraphQLContext context, Locale locale) {
                if (input instanceof Money) return (Money) input;
                return Money.of((BigDecimal) amount.parseValue(input, context, locale));
            }

            @Override
            public Money parseLiteral(
                    Value<?> input, CoercedVariables variables, GraphQLContext context, Locale locale) {
                return Money.of((BigDecimal) amount.parseLiteral(input, variables, context, locale));
            }

            @Override
            public Value<?> valueToLiteral(Object input, GraphQLContext context, Locale locale) {
                return amount.valueToLiteral(serialize(input, context, locale), context, locale);
            }
        });
    }

    private static GraphQLScalarType quantity() {
        return scalar("Quantity", new Coercing<Quantity, Integer>() {
            @Override
            public Integer serialize(Object value, GraphQLContext context, Locale locale) {
                if (value instanceof Quantity) return ((Quantity) value).value();
                if (value instanceof Integer) return (Integer) value;
                throw new CoercingSerializeException(
                        "Quantity cannot hold a " + value.getClass().getName());
            }

            @Override
            public Quantity parseValue(Object input, GraphQLContext context, Locale locale) {
                if (input instanceof Quantity) return (Quantity) input;
                try {
                    return Quantity.of(new BigDecimal(String.valueOf(input).trim()).intValueExact());
                } catch (RuntimeException e) {
                    throw new CoercingParseValueException("Quantity cannot hold " + input + ": " + e.getMessage());
                }
            }

            @Override
            public Quantity parseLiteral(
                    Value<?> input, CoercedVariables variables, GraphQLContext context, Locale locale) {
                if (!(input instanceof IntValue))
                    throw new CoercingParseLiteralException("Quantity is written as an integer");
                try {
                    return Quantity.of(((IntValue) input).getValue().intValueExact());
                } catch (RuntimeException e) {
                    throw new CoercingParseLiteralException("Quantity cannot hold " + input + ": " + e.getMessage());
                }
            }

            @Override
            public Value<?> valueToLiteral(Object input, GraphQLContext context, Locale locale) {
                return new IntValue(BigInteger.valueOf(serialize(parseValue(input, context, locale), context, locale)));
            }
        });
    }

    /** Any JSON value, passed through: a map is written by the encoder the server already has. */
    private static GraphQLScalarType json() {
        return scalar("JSON", new Coercing<Object, Object>() {
            @Override
            public Object serialize(Object value, GraphQLContext context, Locale locale) {
                return value;
            }

            @Override
            public Object parseValue(Object input, GraphQLContext context, Locale locale) {
                return input;
            }

            @Override
            public Object parseLiteral(
                    Value<?> input, CoercedVariables variables, GraphQLContext context, Locale locale) {
                return literal(input, variables);
            }

            @Override
            public Value<?> valueToLiteral(Object input, GraphQLContext context, Locale locale) {
                return toLiteral(input);
            }
        });
    }

    private static Object literal(Value<?> input, CoercedVariables variables) {
        if (input == null || input instanceof NullValue) return null;
        if (input instanceof StringValue) return ((StringValue) input).getValue();
        if (input instanceof IntValue) return ((IntValue) input).getValue();
        if (input instanceof FloatValue) return ((FloatValue) input).getValue();
        if (input instanceof BooleanValue) return ((BooleanValue) input).isValue();
        if (input instanceof EnumValue) return ((EnumValue) input).getName();
        if (input instanceof VariableReference) {
            return variables == null ? null : variables.get(((VariableReference) input).getName());
        }
        if (input instanceof ArrayValue) {
            List<Object> list = new ArrayList<>();
            for (Value<?> v : ((ArrayValue) input).getValues()) list.add(literal(v, variables));
            return list;
        }
        if (input instanceof ObjectValue) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (ObjectField f : ((ObjectValue) input).getObjectFields())
                map.put(f.getName(), literal(f.getValue(), variables));
            return map;
        }
        throw new CoercingParseLiteralException("JSON cannot read " + input);
    }

    private static Value<?> toLiteral(Object input) {
        if (input == null) return NullValue.of();
        if (input instanceof String) return StringValue.of((String) input);
        if (input instanceof Boolean) return BooleanValue.of((Boolean) input);
        if (input instanceof Integer || input instanceof Long || input instanceof BigInteger) {
            return new IntValue(new BigInteger(input.toString()));
        }
        if (input instanceof Number) return FloatValue.of(new BigDecimal(input.toString()).doubleValue());
        if (input instanceof Iterable) {
            List<Value> values = new ArrayList<>();
            for (Object o : (Iterable<?>) input) values.add(toLiteral(o));
            return ArrayValue.newArrayValue().values(values).build();
        }
        if (input instanceof Map) {
            ObjectValue.Builder b = ObjectValue.newObjectValue();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) input).entrySet()) {
                b.objectField(new ObjectField(String.valueOf(e.getKey()), toLiteral(e.getValue())));
            }
            return b.build();
        }
        return StringValue.of(input.toString());
    }
}
