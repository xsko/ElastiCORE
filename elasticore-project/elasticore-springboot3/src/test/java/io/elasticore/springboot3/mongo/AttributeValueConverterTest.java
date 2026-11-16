package io.elasticore.springboot3.mongo;

import jakarta.persistence.AttributeConverter;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MongoConversionContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class AttributeValueConverterTest {

    /** An enum as the generator writes one with @db(code): its JPA converter stores the code. */
    enum Status {
        NEW("N"),
        PAID("P") {
            @Override
            public String toString() {
                return "a constant with a body of its own";
            }
        };

        private final String code;

        Status(String code) {
            this.code = code;
        }

        static Status fromCode(String code) {
            for (Status s : values()) if (s.code.equals(code)) return s;
            throw new IllegalArgumentException(code);
        }

        public static class EntityConverter implements AttributeConverter<Status, String> {
            @Override
            public String convertToDatabaseColumn(Status attribute) {
                return attribute == null ? null : attribute.code;
            }

            @Override
            public Status convertToEntityAttribute(String dbData) {
                return dbData == null ? null : fromCode(dbData);
            }
        }
    }

    /** A value type stored as text, as Money and Quantity are (JpaConverter). */
    static final class Amount {
        final int cents;

        Amount(int cents) {
            this.cents = cents;
        }

        static class JpaConverter implements AttributeConverter<Amount, String> {
            private JpaConverter() {}

            @Override
            public String convertToDatabaseColumn(Amount attribute) {
                return String.valueOf(attribute.cents);
            }

            @Override
            public Amount convertToEntityAttribute(String dbData) {
                return new Amount(Integer.parseInt(dbData));
            }
        }
    }

    /** A converter that cannot be made: an abstract class. */
    static final class Broken {
        abstract static class EntityConverter implements AttributeConverter<Broken, String> {}
    }

    @Test
    void anEnumIsStoredByItsCodeAndReadBack() {
        assertEquals("N", AttributeValueConverter.stored(Status.NEW));
        // A constant with a body is a subclass of its enum: the enum's converter still applies.
        assertEquals("P", AttributeValueConverter.stored(Status.PAID));
        assertSame(Status.PAID, AttributeValueConverter.readAs("P", Status.class));
        assertEquals(
                Arrays.asList("N", "P"), AttributeValueConverter.storedAll(Arrays.asList(Status.NEW, Status.PAID)));
        assertEquals(Collections.emptyList(), AttributeValueConverter.storedAll(null));
    }

    @Test
    void aValueTypeIsStoredThroughItsJpaConverter() {
        assertEquals("250", AttributeValueConverter.stored(new Amount(250)));
        assertEquals(250, ((Amount) AttributeValueConverter.readAs("250", Amount.class)).cents);
    }

    @Test
    void whatHasNoConverterIsStoredAsItIs() {
        assertEquals("text", AttributeValueConverter.stored("text"));
        assertEquals(7, AttributeValueConverter.readAs(7, Integer.class));
        assertEquals("x", AttributeValueConverter.readAs("x", Integer.class));
        assertNull(AttributeValueConverter.stored(null));
        assertNull(AttributeValueConverter.readAs(null, Status.class));
        // A value already of the type is not converted again.
        assertSame(Status.NEW, AttributeValueConverter.readAs(Status.NEW, Status.class));
    }

    @Test
    void theConverterReadsThePropertysType() {
        MongoPersistentProperty property = mock(MongoPersistentProperty.class);
        doReturn(Status.class).when(property).getType();
        MongoConversionContext context = mock(MongoConversionContext.class);
        doReturn(property).when(context).getProperty();
        AttributeValueConverter converter = new AttributeValueConverter();
        assertSame(Status.NEW, converter.read("N", context));
        assertEquals("N", converter.write(Status.NEW, context));
        assertNull(converter.read(null, context));
        // Without a property to read the type of, the value is left as it is.
        assertEquals("N", converter.read("N", null));
        MongoConversionContext noProperty = mock(MongoConversionContext.class);
        assertEquals("N", converter.read("N", noProperty));
    }

    @Test
    void aConverterThatCannotBeMadeIsSaid() {
        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> AttributeValueConverter.readAs("x", Broken.class));
        assertEquals(true, e.getMessage().contains("Broken$EntityConverter"), e.getMessage());
    }
}
