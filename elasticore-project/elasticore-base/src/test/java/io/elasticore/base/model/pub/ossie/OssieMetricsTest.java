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
     * @db code: PLACED='PLC', CANCELLED='CAN'; kind Kind stored by ordinal: SHOP=0, WEB=1;
     * region Region stored by name: OR, WA, whose first constant reads like a word of a filter;
     * orderNo and android, whose names hold one; weight float). Log has a table and no key.
     */
    private final OssieMetrics.Model shop = new OssieMetrics.Model() {
        public String dataset(String entity) {
            return "Order".equals(entity) || "Log".equals(entity) ? entity : null;
        }

        public String column(String entity, String field) {
            if (!"Order".equals(entity)) return null;
            return Arrays.asList("total", "status", "note", "qty", "kind", "region", "orderNo", "android", "weight")
                            .contains(field)
                    ? field
                    : null;
        }

        public String datatype(String entity, String field) {
            if ("total".equals(field)) return "Decimal";
            if ("weight".equals(field)) return "Float";
            if ("qty".equals(field) || "kind".equals(field)) return "Integer";
            return "String";
        }

        public String enumLiteral(String entity, String field, String constant) {
            if ("status".equals(field))
                return "PLACED".equals(constant) ? "'PLC'" : "CANCELLED".equals(constant) ? "'CAN'" : null;
            if ("kind".equals(field)) return "SHOP".equals(constant) ? "0" : "WEB".equals(constant) ? "1" : null;
            if ("region".equals(field))
                return "OR".equals(constant) || "WA".equals(constant) ? "'" + constant + "'" : null;
            return null;
        }

        public boolean isEnum(String entity, String field) {
            return "status".equals(field) || "kind".equals(field) || "region".equals(field);
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

    /** The same metrics on the dataset's fields by their own names, as a metric of that dataset writes them. */
    @Test
    public void onItsDatasetAMetricNamesTheFieldsAlone() {
        OssieMetrics r = new OssieMetrics(shop);
        OssieMetrics.Metric revenue = r.read("revenue", "sum(Order.total) where status != CANCELLED and qty >= 1");
        assertEquals("SUM(CASE WHEN status <> 'CAN' AND qty >= 1 THEN total END)", revenue.local);
        assertEquals("SUM(CASE WHEN Order.status <> 'CAN' AND Order.qty >= 1 THEN Order.total END)", revenue.sql);
        assertEquals(Arrays.asList("Order"), revenue.datasets);
        assertEquals("COUNT(order_id)", r.read("orders", "count(Order)").local);
        assertEquals("MAX(total)", r.read("largest", "max(Order.total)").local);
        assertEquals("COUNT(DISTINCT note)", r.read("notes", "count(distinct Order.note)").local);
        assertEquals(
                "COUNT(CASE WHEN status IN ('PLC') OR NOT (kind = 1 AND qty = 0) THEN 1 END)",
                r.read("some", "count(Order) where status in (PLACED) or not (kind = WEB and qty = 0)").local);
        assertEquals(
                "COUNT(DISTINCT CASE WHEN status NOT IN ('CAN') THEN note END)",
                r.read("kept", "count(distinct Order.note) where status not in (CANCELLED)").local);

        // A metric made of the dataset's metrics is on its fields too.
        OssieMetrics.Metric average = r.read("average", "revenue / orders");
        assertEquals(
                "(SUM(CASE WHEN status <> 'CAN' AND qty >= 1 THEN total END)) / NULLIF((COUNT(order_id)), 0)",
                average.local);
        assertEquals(Arrays.asList("Order"), average.datasets);
        assertEquals(
                "CAST((COUNT(DISTINCT note)) AS DECIMAL(38, 10)) / NULLIF((COUNT(order_id)), 0)",
                r.read("share", "notes / orders").local);
        assertEquals("(COUNT(order_id)) * 2", r.read("twice", "orders * 2").local);
        assertEquals("3 - (MAX(total))", r.read("left", "3 - largest").local);
        assertTrue(r.problems().isEmpty(), r.problems().toString());
    }

    /** What the two forms share is as it was: the type of an average, and the rows of an entity with no key. */
    @Test
    public void anAverageAndAKeylessCountAreAsBefore() {
        OssieMetrics r = new OssieMetrics(shop);
        assertEquals("Float", r.read("weight", "avg(Order.weight)").datatype);
        assertEquals("Decimal", r.read("total", "avg(Order.total)").datatype);
        assertEquals("Decimal", r.read("qty", "avg(Order.qty)").datatype);
        OssieMetrics.Metric logs = r.read("logs", "count(Log)");
        assertEquals("COUNT(*)", logs.sql);
        assertEquals("COUNT(*)", logs.local);
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
        assertNull(r.read("i", "count(Order) where note = 'A' 'B'"));
        assertEquals(9, r.problems().size(), r.problems().toString());
        String[] expected = {
                "Invoice is not an entity with a table", "Order has no column for price", "has no constant SHIPPED",
                "is not an enum, so hello needs quotes", "later is not a metric declared before it", "sum needs a field",
                "is neither an aggregate", "sum needs a number, and Order.note is String", "write one value"};
        for (int i = 0; i < expected.length; i++) {
            assertTrue(r.problems().get(i).contains(expected[i]), r.problems().get(i));
        }
    }

    private String sql(String line) {
        OssieMetrics r = new OssieMetrics(shop);
        OssieMetrics.Metric m = r.read("m", line);
        assertTrue(r.problems().isEmpty(), line + ": " + r.problems());
        return m.sql;
    }

    private String filter(String where) {
        String sql = sql("count(Order) where " + where);
        assertTrue(sql.startsWith("COUNT(CASE WHEN ") && sql.endsWith(" THEN 1 END)"), sql);
        return sql.substring("COUNT(CASE WHEN ".length(), sql.length() - " THEN 1 END)".length());
    }

    /** Each value is what it would be alone: an enum constant by how it is stored, a text quoted for SQL. */
    @Test
    public void aListIsItsValues() {
        assertEquals("Order.status IN ('PLC', 'CAN')", filter("status in (PLACED, CANCELLED)"));
        assertEquals("Order.status NOT IN ('CAN')", filter("status not in (CANCELLED)"));
        assertEquals("Order.kind IN (0, 1)", filter("kind in (SHOP, WEB)"));
        assertEquals("Order.qty IN (1, 2)", filter("qty in (1,2)"));
        assertEquals("Order.note IN ('a, b', 'O''Brien', 'c)')", filter("note in ('a, b', \"O'Brien\", 'c)')"));
        assertEquals("Order.status IN ('PLC')", filter("status IN(PLACED)"));
        assertEquals("Order.status NOT IN ('PLC')", filter("status NOT  IN (PLACED)"));
        assertEquals(
                "SUM(CASE WHEN Order.status IN ('PLC') THEN Order.total END)",
                sql("sum(Order.total) where status in (PLACED)"));
        assertEquals("Order.status IN ('PLC') AND Order.qty >= 1", filter("status in (PLACED) and qty >= 1"));
    }

    /** As in SQL: "and" binds tighter, and what is written goes out in the order it was written. */
    @Test
    public void orBindsLooserThanAndAndParenthesesGroup() {
        assertEquals("Order.qty = 1 OR Order.qty = 2", filter("qty = 1 or qty = 2"));
        assertEquals("Order.qty = 1 OR Order.qty = 2", filter("qty = 1 OR qty = 2"));
        assertEquals("Order.qty = 1 AND Order.qty = 2 OR Order.qty = 3", filter("qty = 1 and qty = 2 or qty = 3"));
        assertEquals("Order.qty = 1 OR Order.qty = 2 AND Order.qty = 3", filter("qty = 1 or qty = 2 and qty = 3"));
        assertEquals(
                "(Order.status = 'PLC' OR Order.status = 'CAN') AND Order.total > 0",
                filter("(status = PLACED or status = CANCELLED) and total > 0"));
        assertEquals("Order.qty = 1 AND (Order.qty = 2 OR Order.qty = 3)", filter("qty = 1 and (qty = 2 or qty = 3)"));
        assertEquals("((Order.qty = 1))", filter("( ( qty = 1 ) )"));
        assertEquals("(Order.qty = 1) OR (Order.qty = 2)", filter("(qty = 1)or(qty = 2)"));
        assertEquals("NOT (Order.qty = 1 OR Order.qty = 2)", filter("not (qty = 1 or qty = 2)"));
        assertEquals("NOT (Order.qty = 1) AND Order.qty = 2", filter("NOT(qty = 1) and qty = 2"));
        assertEquals(
                "Order.status NOT IN ('CAN') OR (Order.kind IN (0, 1) AND Order.qty > 0)",
                filter("status not in (CANCELLED) or (kind in (SHOP, WEB) and qty > 0)"));
    }

    /** A filter's words are words only where they join: a line that does not use them reads as it did. */
    @Test
    public void aWordInsideAValueOrANameIsNotAJoin() {
        assertEquals("Order.note = 'a or b'", filter("note = 'a or b'"));
        assertEquals("Order.note = 'a and (b'", filter("note = 'a and (b'"));
        assertEquals("Order.note = 'O''Brien or not'", filter("note = \"O'Brien or not\""));
        assertEquals("Order.orderNo = 1 OR Order.android = TRUE", filter("orderNo = 1 or android = true"));
        assertEquals("Order.region = 'OR'", filter("region = OR"));
        assertEquals("Order.region = 'OR' AND Order.qty >= 1", filter("region = OR and qty >= 1"));
        assertEquals("Order.region = 'OR' OR Order.region = 'WA'", filter("region = OR or region = WA"));
        assertEquals("Order.region <> 'OR'", filter("region != OR"));
        assertEquals("Order.region IN ('OR', 'WA')", filter("region in (OR, WA)"));
    }

    @Test
    public void countDistinctCountsAFieldsValues() {
        OssieMetrics r = new OssieMetrics(shop);
        OssieMetrics.Metric notes = r.read("notes", "count(distinct Order.note) @terms(unique) -- Different notes");
        assertEquals("COUNT(DISTINCT Order.note)", notes.sql);
        assertEquals("Integer", notes.datatype);
        assertEquals("Different notes", notes.label);
        assertEquals(Arrays.asList("unique"), notes.terms);
        assertEquals("COUNT(DISTINCT Order.note)", r.read("upper", "COUNT( DISTINCT  Order.note )").sql);
        assertEquals(
                "COUNT(DISTINCT CASE WHEN Order.status IN ('PLC') OR Order.qty > 1 THEN Order.note END)",
                r.read("filtered", "count(distinct Order.note) where status in (PLACED) or qty > 1").sql);
        r.read("orders", "count(Order)");
        assertEquals(
                "CAST((COUNT(DISTINCT Order.note)) AS DECIMAL(38, 10)) / NULLIF((COUNT(Order.order_id)), 0)",
                r.read("share", "notes / orders").sql);
        assertTrue(r.problems().isEmpty(), r.problems().toString());
        assertTrue(OssieSql.portable(notes.sql));
    }

    /** Said with the line's own text, and left out as any other metric that cannot be read. */
    @Test
    public void aMalformedFilterIsSaidAndLeftOut() {
        String[][] lines = {
            {"count(Order) where (qty = 1", "the parentheses of '(qty = 1' do not balance"},
            {"count(Order) where qty = 1)", "the parentheses of 'qty = 1)' do not balance"},
            {"count(Order) where )qty = 1(", "do not balance"},
            {"count(Order) where status in ()", "status in () lists no value"},
            {"count(Order) where status in ( )", "status in () lists no value"},
            {"count(Order) where status in (PLACED,,CANCELLED)", "status in (PLACED,,CANCELLED) has an empty value"},
            {"count(Order) where status in (PLACED,)", "has an empty value"},
            {"count(Order) where qty = 1 or", "'qty = 1 or' has an or with nothing on one side of it"},
            {"count(Order) where or qty = 1", "'or qty = 1' has an or with nothing on one side of it"},
            {"count(Order) where qty = 1 or or qty = 2", "has an or with nothing on one side of it"},
            {"count(Order) where qty = 1 and or qty = 2", "has an and with nothing on one side of it"},
            {"count(Order) where qty = 1 and", "'qty = 1 and' has an and with nothing on one side of it"},
            {"count(Order) where (qty = 1 or )", "has an or with nothing on one side of it"},
            {"count(Order) where ()", "'()' has nothing in its parentheses"},
            {"count(Order) where not ()", "'()' has nothing in its parentheses"},
            {"count(Order) where (qty = 1) qty = 2", "'(qty = 1) qty = 2' is not FIELD OP VALUE"},
            {"count(Order) where not qty = 1", "'not qty = 1' is not FIELD OP VALUE"},
            {"count(Order) where status in PLACED", "'status in PLACED' is not FIELD OP VALUE"},
            {"count(Order) where status in (PLACED) qty", "'status in (PLACED) qty' is not FIELD OP VALUE"},
        };
        for (String[] line : lines) {
            OssieMetrics r = new OssieMetrics(shop);
            assertNull(r.read("m", line[0]), line[0]);
            assertEquals(1, r.problems().size(), line[0] + ": " + r.problems());
            String said = r.problems().get(0);
            assertTrue(
                    said.contains(line[1]) && said.startsWith("metric m: ") && said.endsWith("; it is left out."),
                    said);
        }
    }

    /** Every value of a list is checked the way a single one is, and the one that is wrong is named. */
    @Test
    public void aValueOfAListIsCheckedAsASingleOneIs() {
        String[][] lines = {
            {"count(Order) where status in (PLACED, SHIPPED)", "Order.status has no constant SHIPPED"},
            {"count(Order) where status not in (SHIPPED)", "Order.status has no constant SHIPPED"},
            {"count(Order) where note in ('a', hello)", "Order.note is not an enum, so hello needs quotes"},
            {"count(Order) where note in ('a' 'b')", "write one value"},
            {"count(Order) where price in (1)", "Order has no column for price"},
            {"count(Order) where status in (PLACED, null)", "Order.status has no constant null"},
        };
        for (String[] line : lines) {
            OssieMetrics r = new OssieMetrics(shop);
            assertNull(r.read("m", line[0]), line[0]);
            assertTrue(r.problems().get(0).contains(line[1]), r.problems().toString());
        }
    }

    @Test
    public void distinctGoesOnlyWithCountAndNeedsAField() {
        String[][] lines = {
            {"sum(distinct Order.total)", "distinct goes only with count, not with sum"},
            {"avg(distinct Order.total)", "distinct goes only with count, not with avg"},
            {"min(distinct Order.total)", "distinct goes only with count, not with min"},
            {"max(distinct Order.total) where qty = 1", "distinct goes only with count, not with max"},
            {"count(distinct Order)", "count(distinct ...) needs a field: count(distinct Order.field)"},
            {"count(distinct Order.price)", "Order has no column for price"},
            {"count(distinct Invoice.total)", "Invoice is not an entity with a table"},
            {"count(distinct Order.note) where qty =", "'qty =' is not FIELD OP VALUE"},
        };
        for (String[] line : lines) {
            OssieMetrics r = new OssieMetrics(shop);
            assertNull(r.read("m", line[0]), line[0]);
            assertTrue(r.problems().get(0).contains(line[1]), r.problems().toString());
        }
    }
}
