package io.elasticore.springboot3.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A refused write leaves no trace in what its transaction reads (issue 344): MyBatis answers a second read of
 * a row from the session's cache - the object the request changed - until the cache is cleared. MyBatis-Spring
 * on H2, under a Spring transaction.
 */
@DisplayName("RefusedWrite: a refused write clears the transaction's MyBatis session cache")
class RefusedWriteTest {

    /** A row, filled by MyBatis through its fields. */
    public static class RfRow {
        public Long id;
        public String name;
    }

    public interface RfRows {
        @Select("select id, name from rf_row where id = #{id}")
        RfRow find(@Param("id") long id);
    }

    private static RfRows rows;
    private static TransactionTemplate tx;

    @BeforeAll
    static void start() throws Exception {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:h2:mem:refusedwritemybatis;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table rf_row (id bigint primary key, name varchar(20))");
        jdbc.update("insert into rf_row (id, name) values (1, 'stored')");
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        SqlSessionFactory sessions = bean.getObject();
        sessions.getConfiguration().addMapper(RfRows.class);
        rows = new SqlSessionTemplate(sessions).getMapper(RfRows.class);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    @DisplayName("without it, the transaction's next read answers the changed object; with it, the stored row")
    void theNextReadReadsTheDatabase() {
        tx.executeWithoutResult(s -> {
            RfRow row = rows.find(1);
            row.name = "REFUSED";
            assertThat(rows.find(1)).as("the session's cache: the same object").isSameAs(row);

            RuntimeException refused = new IllegalStateException("refused");
            assertThat(RefusedWrite.discard(refused)).isSameAs(refused);

            RfRow again = rows.find(1);
            assertThat(again).isNotSameAs(row);
            assertThat(again.name).isEqualTo("stored");
        });
    }

    @Test
    @DisplayName("outside a transaction there is nothing to clear")
    void outsideATransaction() {
        RuntimeException refused = new IllegalStateException("refused");
        assertThat(RefusedWrite.discard(refused)).isSameAs(refused);
        assertThat(rows.find(1).name).isEqualTo("stored");
    }
}
