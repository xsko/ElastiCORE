package io.elasticore.base.model.pub.ossie;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A metric line, read into SQL - or said back as a problem and left out, never guessed. */
public class OssieMetricsTest {

    /**
     * Order(total money, qty int, note string, status OrderStatus stored by
     * @db code: PLACED='PLC', CANCELLED='CAN'; kind Kind stored by ordinal: SHOP=0, WEB=1).
     */
    private final OssieMetrics.Model shop = new OssieMetrics.Model() {
        public String dataset(String entity) {
            return "Order".equals(entity) ? "Order" : null;
        }

        public String column(String entity, String field) {
            if (!"Order".equals(entity)) return null;
            return Arrays.asList("total", "status", "note", "qty", "kind").contains(field) ? field : null;
        }

        public String datatype(String entity, String field) {
            if ("total".equals(field)) return "Decimal";
            if ("qty".equals(field) || "kind".equals(field)) return "Integer";
            return "String";
        }

        public String enumLiteral(String entity, String field, String constant) {
            if ("status".equals(field)) return "PLACED".equals(constant) ? "'PLC'" : "CANCELLED".equals(constant) ? "'CAN'" : null;
            if ("kind".equals(field)) return "SHOP".equals(constant) ? "0" : "WEB".equals(constant) ? "1" : null;
            return null;
        }

        public boolean isEnum(String entity, String field) {
            return "status".equals(field) || "kind".equals(field);
        }

        public String keyColumn(String entity) {
            return "Order".equals(entity) ? "order_id" : null;
        }
    };

    @Test
    public void anAggregateWithAFilterIsSqlOnTheDataset() {
        OssieMetrics r = new OssieMetrics(shop);
        OssieMetrics.Metric m = r.read("revenue", "sum(Order.total) where status != CANCELLED and qty >= 1 @terms(sales, 'gross') -- Revenue");
        assertEquals("SUM(CASE WHEN Order.status <> 'CAN' AND Order.qty >= 1 THEN Order.total END)", m.sql);
        assertEquals("Decimal", m.datatype);
        assertEquals("Revenue", m.label);
        assertEquals(Arrays.asList("sales", "gross"), m.terms);
        // An enum stored by its ordinal is compared with the number.
        assertEquals("COUNT(CASE WHEN Order.kind = 1 THEN 1 END)", r.read("web", "count(Order) where kind = WEB").sql);
        assertTrue(r.problems().isEmpty(), r.problems().toString());
    }

    @Test
    public void countsRowsOrValuesAndDividesSafely() {
        OssieMetrics r = new OssieMetrics(shop);
        // The rows by their key: COUNT(*) named no dataset a BI tool could find.
        assertEquals("COUNT(Order.order_id)", r.read("all", "count(Order)").sql);
        assertEquals("COUNT(CASE WHEN Order.note = 'gift' THEN 1 END)", r.read("quoted", "count(Order) where note = 'gift'").sql);
        // A quote inside the text is doubled for SQL; " -- " and " and " inside quotes are the text's own.
        assertEquals("COUNT(CASE WHEN Order.note = 'O''Brien' THEN 1 END)", r.read("named", "count(Order) where note = \"O'Brien\"").sql);
        OssieMetrics.Metric inside = r.read("inside", "count(Order) where note = 'rock and roll -- live' -- Concert orders");
        assertEquals("COUNT(CASE WHEN Order.note = 'rock and roll -- live' THEN 1 END)", inside.sql);
        assertEquals("Concert orders", inside.label);

        r.read("revenue", "sum(Order.total)");
        r.read("orders", "count(Order)");
        OssieMetrics.Metric avg = r.read("average", "revenue / orders");
        assertEquals("(SUM(Order.total)) / NULLIF((COUNT(Order.order_id)), 0)", avg.sql);
        assertEquals("Decimal", avg.datatype);
        assertEquals("Integer", r.read("twice", "orders * 2").datatype);
        // Two counts divide as whole numbers in most databases: the ratio is made a decimal first.
        OssieMetrics.Metric share = r.read("share", "quoted / orders");
        assertEquals("CAST((COUNT(CASE WHEN Order.note = 'gift' THEN 1 END)) AS DECIMAL(38, 10)) / NULLIF((COUNT(Order.order_id)), 0)", share.sql);
        assertEquals("Decimal", share.datatype);
        assertTrue(r.problems().isEmpty(), r.problems().toString());
    }

    @Test
    public void whatCannotBeReadIsSaidAndLeftOut() {
        OssieMetrics r = new OssieMetrics(shop);
        assertNull(r.read("a", "sum(Invoice.total)"));
        assertNull(r.read("b", "sum(Order.price)"));
        assertNull(r.read("c", "count(Order) where status = SHIPPED"));
        assertNull(r.read("d", "count(Order) where note = hello"));
        assertNull(r.read("e", "later / sooner"));
        assertNull(r.read("f", "sum(Order)"));
        assertNull(r.read("g", "the total of all orders"));
        assertNull(r.read("h", "sum(Order.note)"));
        assertNull(r.read("i", "count(Order) where note = 'A' or note = 'B'"));
        assertEquals(9, r.problems().size(), r.problems().toString());
        String[] expected = {
                "Invoice is not an entity with a table", "Order has no column for price", "has no constant SHIPPED",
                "is not an enum, so hello needs quotes", "later is not a metric declared before it", "sum needs a field",
                "is neither an aggregate", "sum needs a number, and Order.note is String", "write one value"};
        for (int i = 0; i < expected.length; i++) {
            assertTrue(r.problems().get(i).contains(expected[i]), r.problems().get(i));
        }
    }
}
