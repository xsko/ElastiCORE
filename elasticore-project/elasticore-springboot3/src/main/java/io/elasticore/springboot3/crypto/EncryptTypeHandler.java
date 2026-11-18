package io.elasticore.springboot3.crypto;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * A MyBatis column kept encrypted ({@link FieldCrypto}), as {@link EncryptConverter} keeps a JPA one: written
 * {@code v1:...}, read as the value. The generated mappers name it on every {@code @encrypt} column.
 */
public class EncryptTypeHandler extends BaseTypeHandler<String> {

    public EncryptTypeHandler() {
        FieldCrypto.markUsed();
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, FieldCrypto.current().encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decrypt(cs.getString(columnIndex));
    }

    private static String decrypt(String stored) {
        return stored == null ? null : FieldCrypto.current().decrypt(stored);
    }
}
