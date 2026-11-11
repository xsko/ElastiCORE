package io.elasticore.springboot3.mybatis;

import io.elasticore.springboot3.bean.PortRow;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rows of a database port's mapper statement have every column the SQL
 * selects, a NULL one as null - as the port runtime reads them over JDBC.
 * MyBatis alone leaves a NULL column out of a map, and answers a row of NULLs
 * as null; any other statement is still read that way.
 */
class PortRowColumnsTest {

    private static UnpooledDataSource dataSource;
    private static SqlSessionFactory sessions;

    /** Another plugin on the same handler: the statement is found through it. */
    @Intercepts(@Signature(type = ResultSetHandler.class, method = "handleResultSets", args = Statement.class))
    public static class Passing implements Interceptor {
        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            return invocation.proceed();
        }
    }

    @BeforeAll
    static void startDatabase() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:portrow;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            s.execute("CREATE TABLE port_row (id INT PRIMARY KEY, name VARCHAR(20), note VARCHAR(20))");
            s.execute("INSERT INTO port_row VALUES (1, 'Pen', NULL), (2, NULL, NULL)");
        }
        Configuration configuration =
                new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(new Passing());
        configuration.addInterceptor(new PortRowColumns());
        String xml = String.join(
                "\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"https://mybatis.org/dtd/mybatis-3-mapper.dtd\">",
                "<mapper namespace=\"port\">",
                "  <select id=\"rows\" resultType=\"io.elasticore.springboot3.bean.PortRow\">",
                "    SELECT id, name AS item_name, note FROM port_row ORDER BY id",
                "  </select>",
                "  <select id=\"names\" resultType=\"io.elasticore.springboot3.bean.PortRow\">",
                "    SELECT name, note FROM port_row ORDER BY id",
                "  </select>",
                "  <select id=\"maps\" resultType=\"java.util.LinkedHashMap\">",
                "    SELECT id, name, note FROM port_row ORDER BY id",
                "  </select>",
                "</mapper>");
        new XMLMapperBuilder(
                        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                        configuration,
                        "port.xml",
                        configuration.getSqlFragments())
                .parse();
        sessions = new DefaultSqlSessionFactory(configuration);
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS port_row");
        }
    }

    @Test
    void aPortRowHasEveryColumnInTheOrderSelected() {
        try (SqlSession session = sessions.openSession()) {
            List<Map<String, Object>> rows = session.selectList("port.rows");
            assertThat(rows).hasSize(2).allSatisfy(r -> assertThat(r).isInstanceOf(PortRow.class));
            assertThat(rows.get(0).keySet()).containsExactly("ID", "ITEM_NAME", "NOTE");
            assertThat(rows.get(0)).containsEntry("ITEM_NAME", "Pen").containsEntry("NOTE", null);
            assertThat(rows.get(1)).containsEntry("ITEM_NAME", null).containsEntry("NOTE", null);
        }
    }

    @Test
    void aRowOfNullsIsARowOfNullColumns() {
        try (SqlSession session = sessions.openSession()) {
            List<Map<String, Object>> rows = session.selectList("port.names");
            assertThat(rows).hasSize(2);
            assertThat(rows.get(1)).isNotNull().containsOnlyKeys("NAME", "NOTE").containsEntry("NAME", null);
        }
    }

    @Test
    void anyOtherStatementIsReadAsMyBatisReadsIt() {
        try (SqlSession session = sessions.openSession()) {
            List<Map<String, Object>> rows = session.selectList("port.maps");
            assertThat(rows.get(0)).isNotInstanceOf(PortRow.class).doesNotContainKey("NOTE");
        }
    }
}
