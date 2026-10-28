package io.elasticore.springboot3.mybatis;

import jakarta.persistence.AttributeConverter;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Stores a generated enum in a MyBatis column the way the JPA code stores it.
 *
 * A generated enum declared {@code @db(code)} carries its own
 * {@code EntityConverter} - the AttributeConverter JPA applies - which writes
 * the code ("N") rather than the name (NEW). The generated MyBatis mappers
 * name this handler on every enum column, so both persistences write and read
 * the same values and a table can move between them. An enum without a
 * converter is stored by name, as JPA's {@code EnumType.STRING} would.
 *
 * MyBatis creates one per enum type through the {@code Class} constructor.
 */
public class ConvertedEnumTypeHandler<E extends Enum<E>> extends BaseTypeHandler<E> {

    private final Class<E> type;
    private final AttributeConverter<E, Object> converter;
    private final Class<?> columnType;

    @SuppressWarnings("unchecked")
    public ConvertedEnumTypeHandler(Class<E> type) {
        if (type == null) throw new IllegalArgumentException("The enum type is required");
        this.type = type;
        AttributeConverter<E, Object> found = null;
        Class<?> column = String.class;
        for (Class<?> nested : type.getDeclaredClasses()) {
            if (!AttributeConverter.class.isAssignableFrom(nested)) continue;
            try {
                found = (AttributeConverter<E, Object>) nested.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot create " + nested.getName() + " for " + type.getName(), e);
            }
            column = columnTypeOf(nested);
            break;
        }
        this.converter = found;
        this.columnType = column;
    }

    /** The database side of the converter: the second type argument of AttributeConverter. */
    private static Class<?> columnTypeOf(Class<?> converterClass) {
        for (Type t : converterClass.getGenericInterfaces()) {
            if (t instanceof ParameterizedType
                    && ((ParameterizedType) t).getRawType() == AttributeConverter.class) {
                Type db = ((ParameterizedType) t).getActualTypeArguments()[1];
                if (db instanceof Class) return (Class<?>) db;
            }
        }
        return String.class;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType) throws SQLException {
        Object value = converter != null ? converter.convertToDatabaseColumn(parameter) : parameter.name();
        if (value == null) {
            ps.setNull(i, jdbcType != null ? jdbcType.TYPE_CODE : java.sql.Types.VARCHAR);
        } else {
            ps.setObject(i, value);
        }
    }

    @Override
    public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toEnum(read(rs.getObject(columnName)));
    }

    @Override
    public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toEnum(read(rs.getObject(columnIndex)));
    }

    @Override
    public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toEnum(read(cs.getObject(columnIndex)));
    }

    /** A column value as the converter's own type: a driver hands a number as whatever width it likes. */
    private Object read(Object raw) {
        if (raw == null) return null;
        if (columnType.isInstance(raw)) return raw;
        if (columnType == String.class) return String.valueOf(raw).trim();
        if (raw instanceof Number) {
            Number n = (Number) raw;
            if (columnType == Integer.class) return n.intValue();
            if (columnType == Long.class) return n.longValue();
            if (columnType == Short.class) return n.shortValue();
        }
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if (columnType == Integer.class) return Integer.valueOf(s);
            if (columnType == Long.class) return Long.valueOf(s);
            if (columnType == Short.class) return Short.valueOf(s);
        }
        return raw;
    }

    private E toEnum(Object value) {
        if (value == null) return null;
        if (converter != null) return converter.convertToEntityAttribute(value);
        return Enum.valueOf(type, String.valueOf(value));
    }
}
