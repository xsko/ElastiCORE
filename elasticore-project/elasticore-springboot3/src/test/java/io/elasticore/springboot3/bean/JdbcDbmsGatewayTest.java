package io.elasticore.springboot3.bean;

import io.elasticore.springboot3.dbms.meta.SqlQueryInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A DBMS port's SQL run through JDBC, in an application without JPA: rows are
 * mapped as the JPA gateway maps them (a DTO by column label, one value from
 * the first column), :name binds by name, a conditional line is kept or
 * dropped by its value, a page is a count and a slice, and a write answers
 * the rows it changed.
 */
class JdbcDbmsGatewayTest {

    private static JdbcTemplate jdbc;
    private static JdbcDbmsGateway gateway;

    /** The row of a port answering a DTO. */
    public static class Item {
        private Integer id;
        private String itemName;
        private BigDecimal price;
    }

    @BeforeAll
    static void startDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:jdbcport;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE port_item (id INT PRIMARY KEY, item_name VARCHAR(20), price DECIMAL(10,2))");
        jdbc.execute("INSERT INTO port_item VALUES (1, 'Pen', 1.50), (2, 'Ink', 3.00), (3, 'Pad', 2.25)");
        gateway = new JdbcDbmsGateway(dataSource);
    }

    @AfterAll
    static void stopDatabase() {
        if (jdbc != null) jdbc.execute("DROP TABLE IF EXISTS port_item");
    }

    private static SqlQueryInfo entry(String sql, boolean nativeQuery) {
        SqlQueryInfo entry = new SqlQueryInfo();
        entry.setId("port.query");
        entry.setSql(sql);
        entry.setNativeQuery(String.valueOf(nativeQuery));
        return entry;
    }

    @Test
    void rowsAreTheMethodsType() throws Exception {
        List<Item> items = gateway.executeQuery(entry("SELECT id, item_name, price FROM port_item ORDER BY id", true),
                Collections.emptyMap(), Item.class, null);
        assertThat(items).hasSize(3);
        assertThat(items.get(0).itemName).as("item_name, camel-cased").isEqualTo("Pen");
        assertThat(items.get(1).price).isEqualByComparingTo("3.00");

        assertThat(gateway.executeQuery(entry("SELECT COUNT(*) FROM port_item", true), Collections.emptyMap(),
                Long.class, null)).containsExactly(3L);
    }

    @Test
    void namesBindAndConditionalLinesFollowTheInput() throws Exception {
        String sql = "SELECT id FROM port_item\nWHERE 1=1\nAND price >= :min /* if:min */\nORDER BY id";
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        assertThat(gateway.executeQuery(entry(sql, true), input, Integer.class, null)).containsExactly(1, 2, 3);
        input.put("min", new BigDecimal("2.00"));
        assertThat(gateway.executeQuery(entry(sql, true), input, Integer.class, null)).containsExactly(2, 3);
    }

    @Test
    void aPageIsACountAndASlice() throws Exception {
        Page<Integer> page = gateway.executePageQuery(entry("SELECT id FROM port_item ORDER BY id", true),
                Collections.emptyMap(), Integer.class, null, PageRequest.of(1, 2), true);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).containsExactly(3);
    }

    /** SQL Server refuses an ORDER BY inside the derived table a page is counted over. */
    @Test
    void aPageIsCountedWithoutTheQuerysOrder() {
        assertThat(SqlPortGateway.withoutOrderBy("SELECT id FROM t WHERE x IN (SELECT y FROM u ORDER BY y)\nORDER BY id"))
                .isEqualTo("SELECT id FROM t WHERE x IN (SELECT y FROM u ORDER BY y)\n");
        assertThat(SqlPortGateway.withoutOrderBy("SELECT reorder_by FROM t")).isEqualTo("SELECT reorder_by FROM t");
        // One a LIMIT or FETCH follows says which rows there are: it stays.
        String limited = "SELECT id FROM t ORDER BY id DESC LIMIT 10";
        assertThat(SqlPortGateway.withoutOrderBy(limited)).isEqualTo(limited);
        String fetched = "SELECT id FROM t ORDER BY id OFFSET 0 ROWS FETCH FIRST 10 ROWS ONLY";
        assertThat(SqlPortGateway.withoutOrderBy(fetched)).isEqualTo(fetched);
        // An order by in a literal or a comment is not the query's.
        String quoted = "SELECT id FROM t WHERE note = 'order by x' -- order by y\n";
        assertThat(SqlPortGateway.withoutOrderBy(quoted)).isEqualTo(quoted);
    }

    @Test
    void aLimitedPageCountsItsRowsOnly() throws Exception {
        Page<Integer> page = gateway.executePageQuery(entry("SELECT id FROM port_item ORDER BY id DESC LIMIT 2", true),
                Collections.emptyMap(), Integer.class, null, PageRequest.of(0, 5), true);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void aWriteAnswersTheRowsItChanged() {
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        input.put("id", 3);
        input.put("price", new BigDecimal("9.99"));
        assertThat(gateway.update("UPDATE port_item SET price = :price WHERE id = :id", input, null)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT price FROM port_item WHERE id = 3", BigDecimal.class))
                .isEqualByComparingTo("9.99");
    }

    @Test
    void jpqlIsRefusedWithoutJpa() {
        assertThatThrownBy(() -> gateway.executeQuery(entry("SELECT i FROM Item i", false), Collections.emptyMap(),
                Item.class, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JPQL");
    }
}
