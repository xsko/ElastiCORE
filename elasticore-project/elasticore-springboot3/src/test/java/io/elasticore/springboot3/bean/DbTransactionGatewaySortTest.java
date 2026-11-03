package io.elasticore.springboot3.bean;

import io.elasticore.springboot3.dbms.meta.SqlQueryInfo;
import io.elasticore.springboot3.validation.BadRequestException;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The sort a DBMS port appends to its SQL comes from the request (sortCode, or
 * sortColumn + sortAscending) and is written into the SQL text, so it must be a
 * column name and a direction and nothing else. Checked on the clause builder and
 * against a real database.
 */
@DisplayName("DBMS port sort: only column names reach ORDER BY")
class DbTransactionGatewaySortTest {

    @Nested
    @DisplayName("toOrderByClause")
    class OrderByClause {

        @Test
        @DisplayName("turns + and - into ASC and DESC")
        void directions() {
            assertThat(DbTransactionGateway.toOrderByClause("title+,name-")).isEqualTo("title ASC, name DESC");
        }

        @Test
        @DisplayName("sorts ascending when the direction is left out, keeping the whole name")
        void noDirection() {
            assertThat(DbTransactionGateway.toOrderByClause("title")).isEqualTo("title ASC");
            // "name+" in a URL query string arrives as "name "
            assertThat(DbTransactionGateway.toOrderByClause("name ,created_at-")).isEqualTo("name ASC, created_at DESC");
        }

        @Test
        @DisplayName("accepts qualified and non-ASCII column names")
        void qualifiedAndUnicode() {
            assertThat(DbTransactionGateway.toOrderByClause("o.created_at-")).isEqualTo("o.created_at DESC");
            assertThat(DbTransactionGateway.toOrderByClause("이름+")).isEqualTo("이름 ASC");
            // a combining mark after the first letter (Thai)
            assertThat(DbTransactionGateway.toOrderByClause("กิ-")).isEqualTo("กิ DESC");
            // whitespace between the name and the direction
            assertThat(DbTransactionGateway.toOrderByClause("name -")).isEqualTo("name DESC");
        }

        @Test
        @DisplayName("refuses an over-long term as a bad request, repeating only its start")
        void longTerms() {
            String name = "a".repeat(255);
            assertThat(DbTransactionGateway.toOrderByClause(name + "-")).isEqualTo(name + " DESC");
            assertThatThrownBy(() -> DbTransactionGateway.toOrderByClause(name + "bc"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageStartingWith("Invalid sort term: ");

            // thousands of segments in one term: a refusal, not a StackOverflowError
            String dotted = "a" + ".a".repeat(4_000);
            assertThatThrownBy(() -> DbTransactionGateway.toOrderByClause(dotted))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid sort term: " + dotted.substring(0, 40) + "...");

            // hundreds of thousands: refused before the sort code is even split
            String huge = "a" + ".a".repeat(200_000);
            assertThatThrownBy(() -> DbTransactionGateway.toOrderByClause(huge))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Sort code too long: more than 8224 characters");
        }

        @Test
        @DisplayName("refuses more than 32 terms")
        void tooManyTerms() {
            String thirtyTwo = String.join(",", Collections.nCopies(32, "a+"));
            assertThat(DbTransactionGateway.toOrderByClause(thirtyTwo)).isEqualTo(String.join(", ", Collections.nCopies(32, "a ASC")));
            assertThatThrownBy(() -> DbTransactionGateway.toOrderByClause(thirtyTwo + ",b-"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Too many sort terms: more than 32");

            // 32 terms of the longest length fit (8,223 characters, 8,224 with a space); a sort code
            // longer than that is refused before it is split
            String longest = String.join(",", Collections.nCopies(32, "a".repeat(255) + "-"));
            assertThat(DbTransactionGateway.toOrderByClause(longest)).startsWith("a".repeat(255) + " DESC, ");
            assertThat(DbTransactionGateway.toOrderByClause(longest + " ")).startsWith("a".repeat(255) + " DESC, ");
            assertThatThrownBy(() -> DbTransactionGateway.toOrderByClause(longest + "  "))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Sort code too long: more than 8224 characters");
            assertThatThrownBy(() -> DbTransactionGateway.toOrderByClause("a,".repeat(5_000)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Sort code too long: more than 8224 characters");
        }

        @Test
        @DisplayName("repeats a refused term without control characters or half a surrogate pair")
        void errorMessage() {
            String fakeLogLine = "a" + (char) 13 + (char) 10 + "FAKE LOG LINE";
            assertThatThrownBy(() -> DbTransactionGateway.toOrderByClause(fakeLogLine))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid sort term: a??FAKE LOG LINE");

            // line and paragraph separators, a right-to-left override, a zero-width space
            String invisible = "a" + (char) 0x2028 + (char) 0x2029 + (char) 0x202E + (char) 0x200B + "b";
            assertThatThrownBy(() -> DbTransactionGateway.toOrderByClause(invisible))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid sort term: a????b");

            // an invisible tag character outside the BMP (U+E0041) is one '?', not two halves
            String tag = "a" + new String(Character.toChars(0xE0041)) + "b";
            assertThatThrownBy(() -> DbTransactionGateway.toOrderByClause(tag))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid sort term: a?b");

            // a letter outside the BMP (U+1D400) whose surrogate pair straddles the 40-character cut
            String pairAt39 = "x".repeat(39) + new String(Character.toChars(0x1D400)) + "-y";
            assertThatThrownBy(() -> DbTransactionGateway.toOrderByClause(pairAt39))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid sort term: " + "x".repeat(39) + "...");
        }

        @Test
        @DisplayName("has nothing to sort by for null, blank or empty terms")
        void nothing() {
            assertThat(DbTransactionGateway.toOrderByClause(null)).isEmpty();
            assertThat(DbTransactionGateway.toOrderByClause("  ")).isEmpty();
            assertThat(DbTransactionGateway.toOrderByClause(" , ,")).isEmpty();
            assertThat(DbTransactionGateway.toOrderByClause(" ,name-, ")).isEqualTo("name DESC");
        }

        @Test
        @DisplayName("refuses anything that is not a column name and a direction")
        void refusesSql() {
            String[] attacks = {
                    "name;DROP TABLE users--",
                    "name desc",
                    "name+ -- comment",
                    "name/**/",
                    "(select password from users)",
                    "case when (select 1)=1 then id else name end",
                    "name'",
                    "\"name\"",
                    "1+",
                    "name,(select 1)",
                    "name+-",
                    "LOWER(name)+",
                    "`order`+",
                    "[order]+",
                    "$name",
                    "a.$b",
                    "na me",
            };
            for (String attack : attacks) {
                assertThatThrownBy(() -> DbTransactionGateway.toOrderByClause(attack))
                        .as(attack)
                        .isInstanceOf(BadRequestException.class)
                        .hasMessageStartingWith("Invalid sort term: ");
            }
        }
    }

    // ---- against H2: the clause the gateway appends is the one the database runs

    private static final String SQL =
            "SELECT id, name FROM sort_item WHERE 1=1\n"
            + "AND name LIKE :keyword /* if:keyword */\n";

    private static LocalContainerEntityManagerFactoryBean factory;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static DbTransactionGateway gateway;

    @BeforeAll
    static void startDatabase() throws Exception {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:h2:mem:sortclause;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE sort_item (id INT PRIMARY KEY, name VARCHAR(20), grp VARCHAR(20))");
        jdbc.execute("INSERT INTO sort_item VALUES (1, 'b', 'x'), (2, 'c', 'y'), (3, 'a', 'x')");

        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPersistenceProvider(new HibernatePersistenceProvider());
        factory.setPackagesToScan("io.elasticore.springboot3.bean.noentities");
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        factory.afterPropertiesSet();

        EntityManagerFactory emf = factory.getObject();
        tx = new TransactionTemplate(new JpaTransactionManager(emf));
        gateway = new DbTransactionGateway(emf);
    }

    @AfterAll
    static void stopDatabase() {
        if (factory != null) factory.destroy();
        if (jdbc != null) jdbc.execute("DROP TABLE IF EXISTS sort_item");
    }

    public static class Search {
        private final String keyword;
        private final String sortCode;

        Search(String keyword, String sortCode) {
            this.keyword = keyword;
            this.sortCode = sortCode;
        }

        public String getKeyword() { return keyword; }
        public String getSortCode() { return sortCode; }
    }

    public static class ColumnSearch {
        private final String sortColumn;
        private final Boolean sortAscending;

        ColumnSearch(String sortColumn, Boolean sortAscending) {
            this.sortColumn = sortColumn;
            this.sortAscending = sortAscending;
        }

        public String getKeyword() { return null; }
        public String getSortColumn() { return sortColumn; }
        public Boolean getSortAscending() { return sortAscending; }
    }

    private static List<Object> ids(Object input) {
        return ids(SQL, input);
    }

    private static SqlQueryInfo entry(String sql) {
        SqlQueryInfo entry = new SqlQueryInfo();
        entry.setId("sort.findItems");
        entry.setSql(sql);
        entry.setNativeQuery("true");
        return entry;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Object> ids(String sql, Object input) {
        List<Map> rows = tx.execute(status -> {
            try {
                return gateway.executeQuery(entry(sql), input, Map.class, null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        return idsOf(rows);
    }

    @SuppressWarnings("rawtypes")
    private static List<Object> idsOf(List<Map> rows) {
        return rows.stream().map(row -> ((Number) row.get("id")).intValue()).collect(Collectors.toList());
    }

    private static int rowCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sort_item", Integer.class);
    }

    @Test
    @DisplayName("H2: a sortCode orders the rows")
    void sortCodeOrdersRows() {
        assertThat(ids(new Search(null, "name-"))).containsExactly(2, 1, 3);
        assertThat(ids(new Search(null, "name"))).containsExactly(3, 1, 2);
        assertThat(ids(new Search("%", "id-"))).containsExactly(3, 2, 1);
    }

    @Test
    @DisplayName("H2: sortColumn and sortAscending order the rows")
    void sortColumnOrdersRows() {
        assertThat(ids(new ColumnSearch("id", false))).containsExactly(3, 2, 1);
        assertThat(ids(new ColumnSearch("name", true))).containsExactly(3, 1, 2);
    }

    @Test
    @DisplayName("H2: a Map input's sortCode orders the rows and is checked the same way")
    void mapSortCode() {
        assertThat(ids(Map.of("sortCode", "name-"))).containsExactly(2, 1, 3);
        assertThatThrownBy(() -> ids(Map.of("sortCode", "name; DROP TABLE sort_item")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith("Invalid sort term: ");
        assertThat(rowCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("H2: a paged query counts and pages with the checked clause")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pagedQuery() {
        Page<Map> page = tx.execute(status -> {
            try {
                return gateway.executePageQuery(entry(SQL), new Search(null, "name-"), Map.class, null,
                        PageRequest.of(0, 2), true);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(idsOf(page.getContent())).containsExactly(2, 1);

        assertThatThrownBy(() -> tx.execute(status -> {
            try {
                return gateway.executePageQuery(entry(SQL), new Search(null, "name) X; DELETE FROM sort_item --"),
                        Map.class, null, PageRequest.of(0, 2), true);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        })).isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith("Invalid sort term: ");
        assertThat(rowCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("H2: SQL in a sortCode or sortColumn is refused before it reaches the database")
    void sqlInSortIsRefused() {
        assertThatThrownBy(() -> ids(new Search(null, "name; DROP TABLE sort_item")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith("Invalid sort term: ");
        assertThatThrownBy(() -> ids(new Search(null,
                "(CASE WHEN (SELECT COUNT(*) FROM sort_item) > 0 THEN id ELSE name END)+")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith("Invalid sort term: ");
        assertThatThrownBy(() -> ids(new ColumnSearch("id; DELETE FROM sort_item", true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith("Invalid sort term: ");
        assertThat(rowCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("H2: SQL without a conditional block does not use the sort at all (as the port guide says)")
    void noConditionalBlockNoSort() {
        String plain = "SELECT id, name FROM sort_item ORDER BY id";
        assertThat(ids(plain, new Search(null, "name-"))).containsExactly(1, 2, 3);
        assertThat(ids(plain, new Search(null, "name; DROP TABLE sort_item"))).containsExactly(1, 2, 3);
        assertThat(rowCount()).isEqualTo(3);
    }

    @Nested
    @DisplayName("withSort")
    class WithSort {

        @Test
        @DisplayName("appends the clause to a query without an ORDER BY or a LIMIT of its own, as before")
        void appends() {
            assertThat(DbTransactionGateway.withSort("SELECT id FROM t\n", "name DESC"))
                    .isEqualTo("SELECT id FROM t\n ORDER BY name DESC");
        }

        @Test
        @DisplayName("puts the requested terms before the query's own")
        void beforeOwnTerms() {
            assertThat(DbTransactionGateway.withSort("SELECT id FROM t\nORDER BY id\n", "name DESC"))
                    .isEqualTo("SELECT id FROM t\nORDER BY name DESC, id\n");
            assertThat(DbTransactionGateway.withSort("SELECT id FROM t order  by id LIMIT 2", "name DESC"))
                    .isEqualTo("SELECT id FROM t order  by name DESC, id LIMIT 2");
        }

        @Test
        @DisplayName("goes before a LIMIT, OFFSET or FETCH, not a parameter or a column of that name")
        void beforePages() {
            assertThat(DbTransactionGateway.withSort("SELECT id FROM t WHERE a = :limit\nLIMIT :limit", "name ASC"))
                    .isEqualTo("SELECT id FROM t WHERE a = :limit\nORDER BY name ASC LIMIT :limit");
            assertThat(DbTransactionGateway.withSort("SELECT t.offset FROM t\nOFFSET 1 ROWS", "name ASC"))
                    .isEqualTo("SELECT t.offset FROM t\nORDER BY name ASC OFFSET 1 ROWS");
            assertThat(DbTransactionGateway.withSort("SELECT id FROM t FETCH FIRST 1 ROWS ONLY", "name ASC"))
                    .isEqualTo("SELECT id FROM t ORDER BY name ASC FETCH FIRST 1 ROWS ONLY");
        }

        @Test
        @DisplayName("leaves an ORDER BY or a LIMIT in parentheses, quotes and comments alone")
        void notTopLevel() {
            String nested = "SELECT id FROM (SELECT id FROM t ORDER BY id LIMIT 3) s\n";
            assertThat(DbTransactionGateway.withSort(nested, "id DESC")).isEqualTo(nested + " ORDER BY id DESC");
            String hidden = "SELECT id FROM t WHERE a <> 'order by x limit 1' /* order by y */\n-- fetch first\n";
            assertThat(DbTransactionGateway.withSort(hidden, "id DESC")).isEqualTo(hidden + " ORDER BY id DESC");
            // a dotted capital I lengthens the text in lower case; the positions still hold
            String dotted = "SELECT id FROM t WHERE a <> 'İİ' ORDER BY id";
            assertThat(DbTransactionGateway.withSort(dotted, "b ASC"))
                    .isEqualTo("SELECT id FROM t WHERE a <> 'İİ' ORDER BY b ASC, id");
        }
    }

    @Test
    @DisplayName("H2: a requested sort goes before the query's own ORDER BY, which breaks its ties")
    void ownOrderByBreaksTies() {
        String ordered = SQL + "ORDER BY id\n";
        assertThat(ids(ordered, new Search(null, null))).containsExactly(1, 2, 3);
        assertThat(ids(ordered, new Search(null, "name-"))).containsExactly(2, 1, 3);
        assertThat(ids(ordered, new Search(null, "grp-"))).containsExactly(2, 1, 3);
        assertThat(ids(ordered, new Search(null, "grp"))).containsExactly(1, 3, 2);
        assertThat(ids(SQL + "ORDER BY id DESC\n", new Search(null, "grp"))).containsExactly(3, 1, 2);
        assertThat(ids(ordered, new ColumnSearch("grp", false))).containsExactly(2, 1, 3);
    }

    @Test
    @DisplayName("H2: a requested sort goes before a LIMIT, OFFSET or FETCH")
    void sortBeforePages() {
        assertThat(ids(SQL + "LIMIT 2\n", new Search(null, "name-"))).containsExactly(2, 1);
        assertThat(ids(SQL + "ORDER BY id LIMIT 2\n", new Search(null, "grp"))).containsExactly(1, 3);
        assertThat(ids(SQL + "OFFSET 1 ROWS FETCH NEXT 1 ROWS ONLY\n", new Search(null, "name-"))).containsExactly(1);
        assertThat(ids(SQL + "FETCH FIRST 2 ROWS ONLY\n", new Search(null, "name"))).containsExactly(3, 1);
        assertThat(ids(SQL + "offset 2 rows\n", new Search(null, "id-"))).containsExactly(1);
    }

    @Test
    @DisplayName("H2: a paged query with its own ORDER BY counts all rows and pages by the requested sort")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pagedOwnOrderBy() {
        Page<Map> page = tx.execute(status -> {
            try {
                return gateway.executePageQuery(entry(SQL + "ORDER BY id\n"), new Search(null, "grp-"), Map.class, null,
                        PageRequest.of(0, 2), true);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(idsOf(page.getContent())).containsExactly(2, 1);
    }

    @Test
    @DisplayName("H2: an ORDER BY or LIMIT in a subquery, a string or a comment is not the query's own")
    void nestedOrderByUntouched() {
        String nested = "SELECT id, name FROM (SELECT id, name FROM sort_item ORDER BY id LIMIT 3) s WHERE 1=1\n"
                + "AND name LIKE :keyword /* if:keyword */\n";
        assertThat(ids(nested, new Search(null, "name-"))).containsExactly(2, 1, 3);

        String hidden = "SELECT id, name FROM sort_item WHERE name <> 'order by id limit 1'\n"
                + "AND name LIKE :keyword /* if:keyword */\n"
                + "/* order by id offset 1 rows */\n"
                + "-- order by id limit 1\n";
        assertThat(ids(hidden, new Search(null, "name-"))).containsExactly(2, 1, 3);
    }
}
