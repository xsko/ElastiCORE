package io.elasticore.base.model.pub.rules.mybatis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /** The order cut from inside a conditional block leaves the block closed. */
    @Test
    public void aBlockTheOrderStoodInIsStillClosed() {
        String counted = MyBatisPortRules.withoutOrderBy("SELECT a FROM t\n/* if:x */\nWHERE b = :x\nORDER BY a\n/* if-end */");
        assertEquals("SELECT a FROM t\n/* if:x */\nWHERE b = :x\n/* if-end */", counted);
        assertTrue(counted.endsWith("/* if-end */"));
    }
}
