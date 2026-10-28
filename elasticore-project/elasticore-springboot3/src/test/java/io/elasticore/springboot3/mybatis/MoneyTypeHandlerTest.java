package io.elasticore.springboot3.mybatis;

import io.elasticore.springboot3.domain.Money;
import io.elasticore.springboot3.domain.Quantity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** What the handlers read of a column, whatever form the driver hands it in. */
class MoneyTypeHandlerTest {

    /** A result set of one column: its value, and the scale its metadata declares. */
    private static ResultSet column(Object value, int scale) {
        ResultSetMetaData meta = (ResultSetMetaData) Proxy.newProxyInstance(MoneyTypeHandlerTest.class.getClassLoader(),
                new Class<?>[]{ResultSetMetaData.class}, (p, m, a) -> m.getName().equals("getScale") ? scale : null);
        return (ResultSet) Proxy.newProxyInstance(MoneyTypeHandlerTest.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "findColumn": return 1;
                        case "getBigDecimal": return value;
                        case "getObject": return value;
                        case "getMetaData": return meta;
                        case "wasNull": return value == null;
                        default: return null;
                    }
                });
    }

    @Test
    void anAmountOracleHandsBackWithoutItsDecimalsHasThemAgain() throws Exception {
        // Oracle keeps NUMBER(10,2) 100.00 as 100.
        Money money = new MoneyTypeHandler().getNullableResult(column(new BigDecimal("100"), 2), "price");
        assertEquals("100.00", money.toBigDecimal().toPlainString());
    }

    @Test
    void anAmountWithMoreDecimalsThanItsColumnIsLeftAsItIs() throws Exception {
        Money money = new MoneyTypeHandler().getNullableResult(column(new BigDecimal("1.255"), 2), 1);
        assertEquals("1.255", money.toBigDecimal().toPlainString());
        // An unconstrained NUMBER reports no scale (Oracle: -127, others 0).
        assertEquals("7", new MoneyTypeHandler().getNullableResult(column(new BigDecimal("7"), -127), 1)
                .toBigDecimal().toPlainString());
    }

    @Test
    void anEmptyColumnIsNoMoneyAndNoQuantity() throws Exception {
        assertNull(new MoneyTypeHandler().getNullableResult(column(null, 2), "price"));
        assertNull(new QuantityTypeHandler().getNullableResult(column(null, 0), "count"));
    }

    @Test
    void aCountInWhateverNumberTheDriverChoseIsAQuantity() throws Exception {
        // Oracle hands an INTEGER column back as a BigDecimal, SQL Server a small one as a Short.
        assertEquals(Quantity.of(3), new QuantityTypeHandler().getNullableResult(column(new BigDecimal("3"), 0), "count"));
        assertEquals(Quantity.of(4), new QuantityTypeHandler().getNullableResult(column((short) 4, 0), 1));
    }
}
