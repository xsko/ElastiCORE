package io.elasticore.springboot3.mybatis;

import io.elasticore.springboot3.domain.Quantity;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Stores a {@link Quantity} in a MyBatis column as its count, an INTEGER - the
 * value Quantity.JpaConverter writes under JPA. The generated mappers name it
 * on every Quantity column.
 *
 * Read through getObject rather than getInt, which answers 0 for NULL: a
 * column without a value is a null quantity, not none.
 */
public class QuantityTypeHandler extends BaseTypeHandler<Quantity> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Quantity parameter, JdbcType jdbcType) throws SQLException {
        ps.setInt(i, parameter.value());
    }

    @Override
    public Quantity getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return of(rs.getObject(columnName));
    }

    @Override
    public Quantity getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return of(rs.getObject(columnIndex));
    }

    @Override
    public Quantity getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return of(cs.getObject(columnIndex));
    }

    /** A driver hands an integer column as whatever number it likes: Oracle a BigDecimal, SQL Server a Short. */
    private static Quantity of(Object column) {
        if (column == null) return null;
        if (column instanceof Number) return Quantity.of(((Number) column).intValue());
        return Quantity.of(Integer.parseInt(column.toString().trim()));
    }
}
