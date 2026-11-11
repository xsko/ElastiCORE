package io.elasticore.base.model.pub.rules.mybatis;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The SQL of a database port as the MyBatis rules read it: its own order, at its top level only. */
public class MyBatisPortSqlTest {

    @Test
    public void aPageIsCountedWithoutItsOrder() {
        assertEquals("SELECT a FROM t", MyBatisPortRules.withoutOrderBy("SELECT a FROM t ORDER BY a"));
        assertEquals("SELECT a FROM t WHERE x IN (SELECT y FROM u ORDER BY y)",
                MyBatisPortRules.withoutOrderBy("SELECT a FROM t WHERE x IN (SELECT y FROM u ORDER BY y) ORDER BY a"));
    }

    /** A LIMIT, OFFSET or FETCH after it says which rows there are: the order stays. */
    @Test
    public void anOrderThatAPageFollowsStays() {
        String limited = "SELECT a FROM t ORDER BY a DESC LIMIT 10";
        assertEquals(limited, MyBatisPortRules.withoutOrderBy(limited));
        String fetched = "SELECT a FROM t ORDER BY a OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY";
        assertEquals(fetched, MyBatisPortRules.withoutOrderBy(fetched));
    }

    @Test
    public void anOrderInTextOrACommentIsNotTheQuerys() {
        String sql = "SELECT a FROM t WHERE b = 'order by x' /* order by z */ -- order by y\n";
        assertEquals(-1, MyBatisPortRules.orderByAt(sql));
        assertEquals(sql, MyBatisPortRules.withoutOrderBy(sql));
    }

    /** The names an order is kept by: the blocks around it, outermost first, and its own line's. */
    @Test
    public void theConditionsAnOrderStandsUnder() {
        assertNull(MyBatisPortRules.orderConditions("SELECT a FROM t"));
        assertEquals(Collections.emptyList(), MyBatisPortRules.orderConditions("SELECT a FROM t ORDER BY a"));
        assertEquals(
                Collections.singletonList("x"),
                MyBatisPortRules.orderConditions("SELECT a FROM t\nORDER BY a /* if:x */"));
        assertEquals(
                Arrays.asList("x", "y"),
                MyBatisPortRules.orderConditions(
                        "SELECT a FROM t\n/* if:x */\n/* if:y */\nORDER BY a\n/* if-end */\n/* if-end */"));
        assertEquals(
                Collections.singletonList("x"),
                MyBatisPortRules.orderConditions(
                        "SELECT a FROM t\n/* if:x */\n/* if:y */\nAND b = 1\n/* if-end */\nORDER BY a\n/* if-end */"));
        assertEquals(
                Collections.emptyList(),
                MyBatisPortRules.orderConditions(
                        "SELECT a FROM t\n/* if:x */\nWHERE b = :x\n/* if-end */\nORDER BY a"));
    }

    /** A block inside a block is cut whole with the order inside it, both still closed. */
    @Test
    public void nestedBlocksTheOrderStoodInAreStillClosed() {
        assertEquals(
                "SELECT a FROM t\n/* if:x */\n/* if:y */\nWHERE b = :x\n/* if-end */\n/* if-end */",
                MyBatisPortRules.withoutOrderBy(
                        "SELECT a FROM t\n/* if:x */\n/* if:y */\nWHERE b = :x\nORDER BY a\n/* if-end */\n/* if-end */"));
    }

    /** The order cut from inside a conditional block leaves the block closed. */
    @Test
    public void aBlockTheOrderStoodInIsStillClosed() {
        String counted = MyBatisPortRules.withoutOrderBy("SELECT a FROM t\n/* if:x */\nWHERE b = :x\nORDER BY a\n/* if-end */");
        assertEquals("SELECT a FROM t\n/* if:x */\nWHERE b = :x\n/* if-end */", counted);
        assertTrue(counted.endsWith("/* if-end */"));
    }
}
