package io.elasticore.springboot3.mongo;

import jakarta.persistence.AttributeConverter;
import org.springframework.data.mongodb.core.convert.MongoConversionContext;
import org.springframework.data.mongodb.core.convert.MongoValueConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores a value in MongoDB as JPA stores it in a column: through the
 * AttributeConverter its type carries - an enum with {@code @db(code)} by its
 * code (EntityConverter), Money and Quantity by their text (JpaConverter).
 * A generated MongoDB entity names it on such a field
 * ({@code @ValueConverter(AttributeValueConverter.class)}); the generated
 * search writes a condition's value through {@link #stored(Object)}, so a
 * query compares what the document holds.
 *
 * A type without such a converter is stored as Spring Data stores it.
 */
public class AttributeValueConverter implements MongoValueConverter<Object, Object> {

    /** The names of the nested converter classes looked for, in order. */
    private static final String[] NESTED = {"EntityConverter", "JpaConverter"};

    /** The converter of each type looked at; NONE where it has none. */
    private static final Map<Class<?>, AttributeConverter<Object, Object>> CONVERTERS =
            new ConcurrentHashMap<Class<?>, AttributeConverter<Object, Object>>();

    private static final AttributeConverter<Object, Object> NONE = new AttributeConverter<Object, Object>() {
        @Override
        public Object convertToDatabaseColumn(Object attribute) {
            return attribute;
        }

        @Override
        public Object convertToEntityAttribute(Object dbData) {
            return dbData;
        }
    };

    @Override
    public Object read(Object value, MongoConversionContext context) {
        if (value == null) return null;
        Class<?> type = context == null || context.getProperty() == null
                ? null
                : context.getProperty().getType();
        return type == null ? value : readAs(value, type);
    }

    @Override
    public Object write(Object value, MongoConversionContext context) {
        return stored(value);
    }

    /** The value of the type, from what the document holds. */
    public static Object readAs(Object value, Class<?> type) {
        if (value == null || type.isInstance(value)) return value;
        // A BigDecimal column value (Money's) is kept as Decimal128, and read back as one.
        if (value instanceof org.bson.types.Decimal128) value = ((org.bson.types.Decimal128) value).bigDecimalValue();
        return converterOf(type).convertToEntityAttribute(value);
    }

    /** What the document holds for a value: its converter's column value, or the value itself. */
    public static Object stored(Object value) {
        if (value == null) return null;
        Class<?> type = value instanceof Enum ? ((Enum<?>) value).getDeclaringClass() : value.getClass();
        return converterOf(type).convertToDatabaseColumn(value);
    }

    /** Each value as the document holds it, for an in condition. */
    public static List<Object> storedAll(Collection<?> values) {
        List<Object> out = new ArrayList<Object>();
        if (values == null) return out;
        for (Object v : values) out.add(stored(v));
        return out;
    }

    static AttributeConverter<Object, Object> converterOf(Class<?> type) {
        AttributeConverter<Object, Object> known = CONVERTERS.get(type);
        if (known != null) return known;
        AttributeConverter<Object, Object> found = find(type);
        CONVERTERS.put(type, found);
        return found;
    }

    /** The first nested converter the type declares, in the order of NESTED; NONE without one. */
    @SuppressWarnings("unchecked")
    private static AttributeConverter<Object, Object> find(Class<?> type) {
        for (String name : NESTED) {
            for (Class<?> nested : type.getDeclaredClasses()) {
                if (!nested.getSimpleName().equals(name) || !AttributeConverter.class.isAssignableFrom(nested))
                    continue;
                try {
                    java.lang.reflect.Constructor<?> ctor = nested.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    return (AttributeConverter<Object, Object>) ctor.newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Cannot make " + nested.getName() + " to store " + type.getName(), e);
                }
            }
        }
        return NONE;
    }
}
