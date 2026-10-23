package io.elasticore.springboot3.bean;

import io.elasticore.springboot3.dbms.meta.SqlQueryInfo;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A DBMS port method that answers one value - {@code long countProducts()}, a
 * name, a total - gets the first column of the row, converted to the method's
 * type. A number used to fail ("Failed to map tuple to DTO: Long" - the row was
 * made into a DTO with {@code new Long()}), a String came back empty
 * ({@code new String()}).
 */
class DbTransactionGatewayValueTest {

    private static LocalContainerEntityManagerFactoryBean factory;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static DbTransactionGateway gateway;

    @BeforeAll
    static void startDatabase() throws Exception {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:h2:mem:singlevalue;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE value_item (id INT PRIMARY KEY, name VARCHAR(20), price DECIMAL(10,2), active BOOLEAN)");
        jdbc.execute("INSERT INTO value_item VALUES (1, 'Pen', 1.50, TRUE), (2, 'Ink', 3.00, FALSE)");

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
        if (jdbc != null) jdbc.execute("DROP TABLE IF EXISTS value_item");
    }

    private static <O> List<O> run(String sql, Class<O> type) {
        SqlQueryInfo entry = new SqlQueryInfo();
        entry.setId("value.query");
        entry.setSql(sql);
        entry.setNativeQuery("true");
        return tx.execute(status -> {
            try {
                return gateway.executeQuery(entry, Collections.emptyMap(), type, null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    void aCountIsANumber() {
        assertThat(run("SELECT COUNT(*) FROM value_item", Long.class)).containsExactly(2L);
        assertThat(run("SELECT COUNT(*) FROM value_item", long.class)).containsExactly(2L);
        assertThat(run("SELECT COUNT(*) FROM value_item", Integer.class)).containsExactly(2);
    }

    @Test
    void otherSingleValues() {
        assertThat(run("SELECT name FROM value_item ORDER BY id", String.class)).containsExactly("Pen", "Ink");
        assertThat(run("SELECT SUM(price) FROM value_item", BigDecimal.class).get(0)).isEqualByComparingTo("4.50");
        assertThat(run("SELECT COUNT(*) FROM value_item", BigDecimal.class).get(0)).isEqualByComparingTo("2");
        assertThat(run("SELECT active FROM value_item ORDER BY id", Boolean.class)).containsExactly(true, false);
        assertThat(run("SELECT price FROM value_item ORDER BY id", Double.class)).containsExactly(1.5, 3.0);
    }

    @Test
    void aNullValueStaysNull() {
        assertThat(run("SELECT MAX(name) FROM value_item WHERE id > 10", String.class)).containsExactly((String) null);
        assertThat(run("SELECT SUM(price) FROM value_item WHERE id > 10", BigDecimal.class)).containsExactly((BigDecimal) null);
        // no row at all: no value
        assertThat(run("SELECT name FROM value_item WHERE id > 10", String.class)).isEmpty();
    }

    @Test
    void aOneLetterColumnIsAChar() {
        assertThat(run("SELECT SUBSTRING(name, 1, 1) FROM value_item ORDER BY id", Character.class)).containsExactly('P', 'I');
        assertThat(run("SELECT SUBSTRING(name, 1, 1) FROM value_item ORDER BY id", char.class)).containsExactly('P', 'I');
    }

    /** A String answer used to be "new String()" - an empty string, not the name. */
    @Test
    void aStringIsTheColumnNotAnEmptyString() {
        assertThat(run("SELECT name FROM value_item WHERE id = 1", String.class)).containsExactly("Pen");
    }

    @Test
    void whatCountsAsOneValue() {
        assertThat(DbTransactionGateway.isSingleValue(long.class)).isTrue();
        assertThat(DbTransactionGateway.isSingleValue(Long.class)).isTrue();
        assertThat(DbTransactionGateway.isSingleValue(BigDecimal.class)).isTrue();
        assertThat(DbTransactionGateway.isSingleValue(java.time.LocalDate.class)).isTrue();
        assertThat(DbTransactionGateway.isSingleValue(String.class)).isTrue();
        assertThat(DbTransactionGateway.isSingleValue(Map.class)).isFalse();
        assertThat(DbTransactionGateway.isSingleValue(DbTransactionGatewayValueTest.class)).isFalse();
    }
}
