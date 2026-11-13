package io.elasticore.springboot3.domain.counts;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/** Every statement Hibernate sends, for a test to read. */
public class SqlSeen implements StatementInspector {

    public static final List<String> SQL = new CopyOnWriteArrayList<String>();

    @Override
    public String inspect(String sql) {
        SQL.add(sql);
        return sql;
    }
}
