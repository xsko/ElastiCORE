package io.elasticore.base.model.pub.ossie;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How an expression is declared: as the portable top-level sql for what the
 * Ossie language says (core-spec/expression_language.md, "Supported SQL
 * Constructs"), as an ANSI_SQL dialect entry for what it rules out ("Not
 * Supported in Expressions" - a query of any kind).
 */
public class OssieSqlTest {

    @Test
    public void whatTheOssieLanguageSaysIsPortable() {
        String[] portable = {
                "order_id",
                "total",
                "unit_price * quantity",
                "CASE WHEN status = 'CAN' THEN 0 ELSE total END",
                "COALESCE(discount, 0)",
                "SUM(CASE WHEN PurchaseOrder.status <> 'CAN' THEN PurchaseOrder.total END)",
                "(COUNT(*)) / NULLIF((COUNT(CASE WHEN status = 'SHP' THEN 1 END)), 0)",
                "CAST(total AS DECIMAL(38, 10))",
                "EXTRACT(YEAR FROM order_date)",
                "first_name || ' ' || last_name",
                "PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY total)",
                // A word of a query inside a string literal, or inside a name, is not a query.
                "CASE WHEN note = 'select one' THEN 1 END",
                "select_flag",
                "created_by",
                "\"with\"",
                "update_count + 1",
        };
        for (String sql : portable) {
            assertTrue(OssieSql.portable(sql), sql);
        }
    }

    @Test
    public void aQueryIsOpaqueSql() {
        String[] opaque = {
                "(select count(*) from cms_content_category c where c.parent_category_id = category_id)",
                "coalesce((select m.name from fx_member m where m.member_id = created_by), created_by)",
                "WITH latest AS (SELECT MAX(order_date) d FROM orders) SELECT d FROM latest",
                "(SELECT 1) UNION (SELECT 2)",
                "select\tcount(*) from t",
        };
        for (String sql : opaque) {
            assertFalse(OssieSql.portable(sql), sql);
        }
    }

    @Test
    public void nothingIsAQuery() {
        assertTrue(OssieSql.portable(null));
        assertTrue(OssieSql.portable(""));
    }
}
