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
import graphql.schema.GraphQLScalarType;
import io.elasticore.springboot3.domain.Money;
import io.elasticore.springboot3.domain.Quantity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Each scalar reads what a client sends, writes what the server answers, and refuses what it cannot hold. */
class GraphQlScalarsTest {

    private static final GraphQLContext CTX = GraphQLContext.getDefault();
    private static final Locale L = Locale.ROOT;

    @SuppressWarnings("unchecked")
    private static Coercing<Object, Object> scalar(String name) {
        for (GraphQLScalarType s : GraphQlScalars.all()) {
            if (s.getName().equals(name)) return (Coercing<Object, Object>) s.getCoercing();
        }
        throw new AssertionError(name);
    }

    @Test
    void everyDeclaredScalarIsWired() {
        assertThat(GraphQlScalars.all())
                .extracting(GraphQLScalarType::getName)
                .containsExactlyInAnyOrderElementsOf(
                        GraphQlTypes.customScalars().keySet());
    }

    @Test
    void numbers() {
        Coercing<Object, Object> longs = scalar("Long");
        assertThat(longs.serialize(5L, CTX, L)).isEqualTo(5L);
        assertThat(longs.serialize(7, CTX, L)).isEqualTo(7L);
        assertThat(longs.serialize("9", CTX, L)).isEqualTo(9L);
        assertThat(longs.parseValue(" 11 ", CTX, L)).isEqualTo(11L);
        assertThat(longs.parseLiteral(new IntValue(BigInteger.valueOf(12)), CoercedVariables.emptyVariables(), CTX, L))
                .isEqualTo(12L);
        assertThat(longs.parseLiteral(StringValue.of("13"), CoercedVariables.emptyVariables(), CTX, L))
                .isEqualTo(13L);
        assertThat(longs.valueToLiteral(14L, CTX, L)).isInstanceOf(IntValue.class);
        assertThatThrownBy(() -> longs.parseValue("1.5", CTX, L)).hasMessageContaining("Long cannot hold 1.5");
        assertThatThrownBy(() -> longs.parseValue(true, CTX, L)).hasMessageContaining("number or a string");
        assertThatThrownBy(() -> longs.serialize(new Object(), CTX, L)).hasMessageContaining("cannot hold a");
        assertThatThrownBy(() -> longs.serialize("x", CTX, L)).hasMessageContaining("cannot hold x");
        assertThatThrownBy(() -> longs.parseLiteral(BooleanValue.of(true), CoercedVariables.emptyVariables(), CTX, L))
                .hasMessageContaining("written as a number");

        Coercing<Object, Object> decimals = scalar("BigDecimal");
        assertThat(decimals.parseLiteral(FloatValue.of(0.125), CoercedVariables.emptyVariables(), CTX, L))
                .isEqualTo(new BigDecimal("0.125"));
        assertThat(((StringValue) decimals.valueToLiteral("1.50", CTX, L)).getValue())
                .isEqualTo("1.50");
        assertThat(scalar("BigInteger").parseValue("123456789012345678901234567890", CTX, L))
                .isEqualTo(new BigInteger("123456789012345678901234567890"));
    }

    @Test
    void datesAndTimes() {
        Coercing<Object, Object> date = scalar("Date");
        assertThat(date.serialize(LocalDate.of(2026, 11, 16), CTX, L)).isEqualTo("2026-11-16");
        assertThat(date.serialize("2026-11-16", CTX, L)).isEqualTo("2026-11-16");
        assertThat(date.parseValue("2026-11-16", CTX, L)).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(date.parseValue(LocalDate.of(2026, 1, 1), CTX, L)).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(date.parseLiteral(StringValue.of("2026-11-16"), CoercedVariables.emptyVariables(), CTX, L))
                .isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(((StringValue) date.valueToLiteral("2026-11-16", CTX, L)).getValue())
                .isEqualTo("2026-11-16");
        assertThatThrownBy(() -> date.parseValue("16/11/2026", CTX, L)).hasMessageContaining("Date cannot read");
        assertThatThrownBy(() -> date.parseValue(5, CTX, L)).hasMessageContaining("is a string");
        assertThatThrownBy(() -> date.serialize(5, CTX, L)).hasMessageContaining("cannot hold a");
        assertThatThrownBy(() ->
                        date.parseLiteral(new IntValue(BigInteger.ONE), CoercedVariables.emptyVariables(), CTX, L))
                .hasMessageContaining("written as a string");

        assertThat(scalar("DateTime").parseValue("2026-11-16T09:30:00", CTX, L))
                .isEqualTo(LocalDateTime.of(2026, 11, 16, 9, 30));
        assertThat(scalar("Time").parseValue("09:30", CTX, L)).isEqualTo(LocalTime.of(9, 30));
        assertThat(scalar("Instant").parseValue("2026-11-16T00:30:00Z", CTX, L))
                .isEqualTo(Instant.parse("2026-11-16T00:30:00Z"));
        Coercing<Object, Object> offset = scalar("OffsetDateTime");
        assertThat(offset.serialize(ZonedDateTime.of(2026, 11, 16, 9, 30, 0, 0, ZoneOffset.ofHours(9)), CTX, L))
                .isEqualTo("2026-11-16T09:30+09:00");
        assertThat(offset.parseValue("2026-11-16T09:30:00+09:00", CTX, L)).isInstanceOf(OffsetDateTime.class);
        UUID id = UUID.randomUUID();
        assertThat(scalar("UUID").parseValue(id.toString(), CTX, L)).isEqualTo(id);
        assertThatThrownBy(() -> scalar("UUID").parseValue("not-a-uuid", CTX, L))
                .hasMessageContaining("UUID cannot read");
    }

    @Test
    void bytes() {
        Coercing<Object, Object> b64 = scalar("Base64");
        byte[] data = {1, 2, 3};
        String text = (String) b64.serialize(data, CTX, L);
        assertThat((byte[]) b64.parseValue(text, CTX, L)).containsExactly(1, 2, 3);
        assertThat((byte[]) b64.parseValue(data, CTX, L)).isSameAs(data);
        assertThat((byte[]) b64.parseLiteral(StringValue.of(text), CoercedVariables.emptyVariables(), CTX, L))
                .containsExactly(1, 2, 3);
        assertThat(((StringValue) b64.valueToLiteral(text, CTX, L)).getValue()).isEqualTo(text);
        assertThatThrownBy(() -> b64.parseValue("***", CTX, L)).hasMessageContaining("Base64 cannot read");
        assertThatThrownBy(() -> b64.parseLiteral(StringValue.of("***"), CoercedVariables.emptyVariables(), CTX, L))
                .hasMessageContaining("Base64 cannot read");
        assertThatThrownBy(
                        () -> b64.parseLiteral(new IntValue(BigInteger.ONE), CoercedVariables.emptyVariables(), CTX, L))
                .hasMessageContaining("written as a string");
        assertThatThrownBy(() -> b64.serialize("text", CTX, L)).hasMessageContaining("cannot hold a");
    }

    @Test
    void moneyAndQuantity() {
        Coercing<Object, Object> money = scalar("Money");
        assertThat(money.serialize(Money.of("12.50"), CTX, L)).isEqualTo(new BigDecimal("12.50"));
        assertThat(money.serialize(new BigDecimal("3"), CTX, L)).isEqualTo(new BigDecimal("3"));
        assertThat(money.parseValue("12.50", CTX, L)).isEqualTo(Money.of("12.50"));
        Money m = Money.of(1);
        assertThat(money.parseValue(m, CTX, L)).isSameAs(m);
        assertThat(money.parseLiteral(FloatValue.of(2.5), CoercedVariables.emptyVariables(), CTX, L))
                .isEqualTo(Money.of("2.5"));
        assertThat(((StringValue) money.valueToLiteral(Money.of("7.10"), CTX, L)).getValue())
                .isEqualTo("7.10");

        Coercing<Object, Object> quantity = scalar("Quantity");
        assertThat(quantity.serialize(Quantity.of(3), CTX, L)).isEqualTo(3);
        assertThat(quantity.serialize(4, CTX, L)).isEqualTo(4);
        assertThat(quantity.parseValue("5", CTX, L)).isEqualTo(Quantity.of(5));
        Quantity q = Quantity.of(6);
        assertThat(quantity.parseValue(q, CTX, L)).isSameAs(q);
        assertThat(quantity.parseLiteral(new IntValue(BigInteger.TEN), CoercedVariables.emptyVariables(), CTX, L))
                .isEqualTo(Quantity.of(10));
        assertThat(((IntValue) quantity.valueToLiteral(2, CTX, L)).getValue()).isEqualTo(BigInteger.TWO);
        assertThatThrownBy(() -> quantity.parseValue("-1", CTX, L)).hasMessageContaining("Quantity cannot hold");
        assertThatThrownBy(() -> quantity.parseLiteral(
                        new IntValue(BigInteger.valueOf(-1)), CoercedVariables.emptyVariables(), CTX, L))
                .hasMessageContaining("Quantity cannot hold");
        assertThatThrownBy(() -> quantity.parseLiteral(StringValue.of("1"), CoercedVariables.emptyVariables(), CTX, L))
                .hasMessageContaining("written as an integer");
        assertThatThrownBy(() -> quantity.serialize("1", CTX, L)).hasMessageContaining("cannot hold a");
    }

    @Test
    void json() {
        Coercing<Object, Object> json = scalar("JSON");
        Map<String, Object> map = Collections.singletonMap("a", 1);
        assertThat(json.serialize(map, CTX, L)).isSameAs(map);
        assertThat(json.parseValue(map, CTX, L)).isSameAs(map);

        ObjectValue literal = ObjectValue.newObjectValue()
                .objectField(new ObjectField("s", StringValue.of("x")))
                .objectField(new ObjectField("i", new IntValue(BigInteger.ONE)))
                .objectField(new ObjectField("f", FloatValue.of(1.5)))
                .objectField(new ObjectField("b", BooleanValue.of(true)))
                .objectField(new ObjectField("e", EnumValue.of("GOLD")))
                .objectField(new ObjectField("n", NullValue.of()))
                .objectField(new ObjectField("v", VariableReference.of("var")))
                .objectField(new ObjectField(
                        "l",
                        ArrayValue.newArrayValue().value(StringValue.of("y")).build()))
                .build();
        CoercedVariables vars = CoercedVariables.of(Collections.singletonMap("var", "fromVar"));
        @SuppressWarnings("unchecked")
        Map<String, Object> read = (Map<String, Object>) json.parseLiteral(literal, vars, CTX, L);
        assertThat(read)
                .containsEntry("s", "x")
                .containsEntry("i", BigInteger.ONE)
                .containsEntry("b", true)
                .containsEntry("e", "GOLD")
                .containsEntry("n", null)
                .containsEntry("v", "fromVar")
                .containsEntry("l", Collections.singletonList("y"));
        assertThat(GraphQlScalars.literal(VariableReference.of("v"), null)).isNull();

        Map<String, Object> back = new LinkedHashMap<>();
        back.put("s", "x");
        back.put("b", false);
        back.put("i", 3);
        back.put("d", 1.5);
        back.put("n", null);
        back.put("l", Arrays.asList(1L, "two"));
        back.put("o", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        Value<?> written = json.valueToLiteral(back, CTX, L);
        assertThat(written).isInstanceOf(ObjectValue.class);
        List<ObjectField> fields = ((ObjectValue) written).getObjectFields();
        assertThat(fields).extracting(ObjectField::getName).containsExactly("s", "b", "i", "d", "n", "l", "o");
        assertThat(fields.get(6).getValue()).isInstanceOf(StringValue.class);
    }
}
