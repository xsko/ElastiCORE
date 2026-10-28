package io.elasticore.springboot3.mybatis;

import io.elasticore.springboot3.domain.Money;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Stores {@link Money} in a MyBatis column as its amount, a DECIMAL - the
 * value Money.JpaConverter writes under JPA, so either persistence reads the
 * other's rows. The generated mappers name it on every Money column.
 */
public class MoneyTypeHandler extends BaseTypeHandler<Money> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Money parameter, JdbcType jdbcType) throws SQLException {
        BigDecimal amount = parameter.toBigDecimal();
        if (amount == null) ps.setNull(i, Types.DECIMAL);
        else ps.setBigDecimal(i, amount);
    }

    @Override
    public Money getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return getNullableResult(rs, rs.findColumn(columnName));
    }

    @Override
    public Money getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return of(rs.getBigDecimal(columnIndex), rs.getMetaData().getScale(columnIndex));
    }

    @Override
    public Money getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return of(cs.getBigDecimal(columnIndex), 0);
    }

    /**
     * The amount with the column's decimal places: Oracle keeps a NUMBER(10,2)
     * of 100.00 as 100 and hands it back so, where the other databases hand
     * back 100.00 - the same money, said alike whichever database holds it.
     */
    private static Money of(BigDecimal amount, int scale) {
        if (amount == null) return null;
        if (scale > 0 && amount.scale() < scale) amount = amount.setScale(scale);
        return Money.of(amount);
    }
}
