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
        return of(rs.getBigDecimal(columnName));
    }

    @Override
    public Money getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return of(rs.getBigDecimal(columnIndex));
    }

    @Override
    public Money getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return of(cs.getBigDecimal(columnIndex));
    }

    private static Money of(BigDecimal amount) {
        return amount == null ? null : Money.of(amount);
    }
}
