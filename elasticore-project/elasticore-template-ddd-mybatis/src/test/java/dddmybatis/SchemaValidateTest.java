package dddmybatis;

import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The schema the generator wrote (db/schema-h2.sql, elasticore { ddl }) is the
 * one the mappers read: every generated statement that reads every row - each
 * mapper's findAll, the joins and columns of its result map - runs against it,
 * so a missing table or column, or one of another type, fails here rather
 * than on a request. The outbox's table is there for its mapper too.
 */
@SpringBootTest
@Transactional
class SchemaValidateTest {

    @Autowired SqlSessionFactory sessions;
    @Autowired org.mybatis.spring.SqlSessionTemplate session;
    @Autowired JdbcTemplate jdbc;

    @Test
    void everyGeneratedReadRunsOnTheGeneratedSchema() {
        List<String> ran = new ArrayList<String>();
        for (Object o : new ArrayList<Object>(sessions.getConfiguration().getMappedStatements())) {
            if (!(o instanceof MappedStatement)) continue;
            MappedStatement ms = (MappedStatement) o;
            if (ms.getSqlCommandType() != SqlCommandType.SELECT || !ms.getId().endsWith(".findAll")) continue;
            if (!ms.getId().startsWith("io.elasticore.sample.dddmybatis.")) continue;
            session.selectList(ms.getId(), java.util.Collections.singletonMap("orderBy", null));
            ran.add(ms.getId());
        }
        assertThat(ran).as("the mappers' reads").hasSizeGreaterThan(5);
        assertThat(jdbc.queryForObject("select count(*) from shop_outbox", Long.class)).isNotNull();
    }
}
