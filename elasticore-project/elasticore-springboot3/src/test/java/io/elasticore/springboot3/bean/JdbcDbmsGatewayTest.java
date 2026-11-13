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

    /** A block inside a block closes at its own if-end; a conditional line inside a block is read as one outside. */
    @Test
    void blocksInsideBlocks() throws Exception {
        String sql = String.join(
                "\n",
                "SELECT id FROM port_item",
                "WHERE 1=1",
                "/* if:min */",
                "AND price >= :min",
                "/* if:name */",
                "AND item_name = :name",
                "/* if-end */",
                "AND id < :below /* if:below */",
                "AND id <> 2",
                "/* if-end */",
                "ORDER BY id");
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        input.put("name", "Pen");
        input.put("below", 3);
        assertThat(gateway.executeQuery(entry(sql, true), input, Integer.class, null))
                .as("no min: the outer block and all inside it are left out")
                .containsExactly(1, 2, 3);
        input.put("min", new BigDecimal("1.00"));
        assertThat(gateway.executeQuery(entry(sql, true), input, Integer.class, null))
                .containsExactly(1);
        input.remove("name");
        assertThat(gateway.executeQuery(entry(sql, true), input, Integer.class, null))
                .containsExactly(1);
        input.remove("below");
        assertThat(gateway.executeQuery(entry(sql, true), input, Integer.class, null))
                .containsExactly(1, 3);
    }

    /** A Pageable among the call's values names the order, before the SQL's own; checked as a sort code is. */
    @Test
    void aPageablesSortOrdersTheRows() throws Exception {
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        input.put(
                "pageable",
                PageRequest.of(
                        0,
                        2,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "price")));
        // The runtime sorts SQL that has a conditional line (the port guide).
        Page<Integer> page = gateway.executePageQuery(
                entry("SELECT id FROM port_item WHERE 1=1\nAND id > :min /* if:min */\nORDER BY id", true),
                input,
                Integer.class,
                null,
                (PageRequest) input.get("pageable"),
                true);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).containsExactly(2, 3);

        assertThat(PortRows.orderBy(PageRequest.of(0, 5, org.springframework.data.domain.Sort.by("item_name"))))
                .isEqualTo("item_name ASC");
        assertThat(PortRows.orderBy(
                        new Object(),
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Order.desc("price"),
                                org.springframework.data.domain.Sort.Order.asc("id"))))
                .isEqualTo("price DESC, id ASC");
        assertThat(PortRows.orderBy(PageRequest.of(0, 5))).isNull();
        // Every row: Pageable.unpaged() has no offset to ask; it is the first page of all.
        org.springframework.data.domain.Pageable all = PortRows.pageable(
                PortRows.named("name", "x", "pageable", org.springframework.data.domain.Pageable.unpaged()));
        assertThat(all.getOffset()).isZero();
        assertThat(all.getPageSize()).isEqualTo(Integer.MAX_VALUE);
        assertThat(PortRows.pageable(PageRequest.of(2, 4))).isEqualTo(PageRequest.of(2, 4));
        input.put(
                "pageable",
                PageRequest.of(0, 2, org.springframework.data.domain.Sort.by("price; DROP TABLE port_item")));
        assertThatThrownBy(() -> PortRows.orderBy(input))
                .isInstanceOf(io.elasticore.springboot3.validation.BadRequestException.class);
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

    /** A list bound by name is the values of IN (:ids) (issue 164). */
    @Test
    void aListBoundByNameIsTheInClause() throws Exception {
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        input.put("ids", java.util.Arrays.asList(1L, 3L));
        assertThat(gateway.executeQuery(
                        entry("SELECT id FROM port_item WHERE id IN (:ids) ORDER BY id", true),
                        input,
                        Integer.class,
                        null))
                .containsExactly(1, 3);
    }

    /** An enum as the generator writes one: a @db code, fromCode(String) to read it. */
    public enum Status {
        DRAFT("DRF"),
        PUBLISHED("PUB");

        private final String code;

        Status(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        public static Status fromCode(String code) {
            for (Status s : values()) {
                if (s.code.equals(code)) return s;
            }
            return null;
        }
    }

    /** An enum without fromCode: read by the constant's name. */
    public enum Kind {
        SMALL,
        LARGE
    }

    /** The row of a port answering a DTO with enum fields. */
    public static class Article {
        private Integer id;
        private Status status;
        private Kind kind;
    }

    /**
     * An enum column is read as the enum (issue 165): by fromCode where the enum
     * has it, by the constant's name otherwise, by the ordinal from a number.
     */
    @Test
    void anEnumColumnIsReadAsTheEnum() throws Exception {
        String rows = "SELECT 1 AS id, 'PUB' AS status, 'LARGE' AS kind UNION ALL "
                + "SELECT 2, 'DRAFT', 'SMALL' ORDER BY id";
        List<Article> articles = gateway.executeQuery(entry(rows, true), Collections.emptyMap(), Article.class, null);
        assertThat(articles.get(0).status).as("the @db code").isEqualTo(Status.PUBLISHED);
        assertThat(articles.get(0).kind).isEqualTo(Kind.LARGE);
        assertThat(articles.get(1).status).as("the constant's name").isEqualTo(Status.DRAFT);
        assertThat(articles.get(1).kind).isEqualTo(Kind.SMALL);

        // One enum value per row, and an ordinal.
        assertThat(gateway.executeQuery(
                        entry("SELECT 'PUB' UNION ALL SELECT 'DRF'", true), Collections.emptyMap(), Status.class, null))
                .containsExactly(Status.PUBLISHED, Status.DRAFT);
        assertThat(gateway.executeQuery(entry("SELECT 1", true), Collections.emptyMap(), Kind.class, null))
                .containsExactly(Kind.LARGE);
        // The last ordinal is the last constant; one past it, or below 0, is said.
        assertThat(SqlPortGateway.convertValue(0, Kind.class)).isEqualTo(Kind.SMALL);
        assertThatThrownBy(() -> SqlPortGateway.convertValue(2, Kind.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ordinal 2");
        assertThatThrownBy(() -> SqlPortGateway.convertValue(-1, Kind.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ordinal -1");
        assertThatThrownBy(() -> SqlPortGateway.convertValue("NONE", Kind.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NONE");
    }

    @Test
    void jpqlIsRefusedWithoutJpa() {
        assertThatThrownBy(() -> gateway.executeQuery(entry("SELECT i FROM Item i", false), Collections.emptyMap(),
                Item.class, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JPQL");
    }
}
