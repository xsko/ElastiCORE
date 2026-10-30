package io.elasticore.springboot3.bean;

import io.elasticore.springboot3.dbms.meta.SqlQueryInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a DBMS port's SQL through JDBC, for an application with no JPA - a
 * MyBatis one: the statement on the DataSource's connection of the running
 * transaction (Spring's DataSourceUtils, as MyBatis takes it), :name bound by
 * name and a collection as a list of binds (NamedParameterJdbcTemplate). A
 * page is read by skipping its offset in the result, so the SQL is the
 * port's as written on every database.
 *
 * The port's datasource names a DataSource bean; none, the application's one.
 * JPQL is JPA's: a port that declares nativeQuery: false is refused here.
 */
public class JdbcDbmsGateway extends SqlPortGateway {

    private final NamedParameterJdbcTemplate primary;
    private final Map<String, NamedParameterJdbcTemplate> named = new HashMap<String, NamedParameterJdbcTemplate>();

    public JdbcDbmsGateway(DataSource dataSource) {
        this.primary = new NamedParameterJdbcTemplate(dataSource);
    }

    /** The application's DataSources by bean name, for a port that names its datasource. */
    @Autowired(required = false)
    public void setDataSources(Map<String, DataSource> dataSources) {
        if (dataSources == null) return;
        for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
            NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(entry.getValue());
            named.put(entry.getKey(), template);
            if (entry.getKey().endsWith("DataSource") && entry.getKey().length() > "DataSource".length()) {
                named.put(entry.getKey().substring(0, entry.getKey().length() - "DataSource".length()), template);
            }
        }
    }

    private NamedParameterJdbcTemplate jdbc(String dataSource) {
        if (dataSource == null || dataSource.isEmpty() || "primary".equals(dataSource)) return primary;
        NamedParameterJdbcTemplate t = named.get(dataSource);
        return t != null ? t : primary;
    }

    @Override
    protected List<Map<String, Object>> rows(SqlQueryInfo entry, String sql, Object input, String dataSource,
                                             int first, int max) {
        if (!entry.getNativeQuery()) {
            throw new IllegalStateException("Port query " + entry.getId() + " is JPQL (nativeQuery: false); without JPA"
                    + " a DBMS port runs SQL - set nativeQuery: true and write it in the database's SQL");
        }
        ResultSetExtractor<List<Map<String, Object>>> extract = rs -> {
            List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
            ResultSetMetaData meta = rs.getMetaData();
            int columns = meta.getColumnCount();
            int skipped = 0;
            while (rs.next()) {
                if (skipped < first) {
                    skipped++;
                    continue;
                }
                if (max >= 0 && out.size() >= max) break;
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                for (int i = 1; i <= columns; i++) {
                    row.put(meta.getColumnLabel(i), org.springframework.jdbc.support.JdbcUtils.getResultSetValue(rs, i));
                }
                out.add(row);
            }
            return out;
        };
        List<Map<String, Object>> rows = jdbc(dataSource).query(sql, source(sql, input), extract);
        return rows == null ? new ArrayList<Map<String, Object>>() : rows;
    }

    @Override
    protected long count(String sql, Object input, String dataSource) {
        Number n = jdbc(dataSource).queryForObject(sql, source(sql, input), Number.class);
        return n == null ? 0 : n.longValue();
    }

    @Override
    protected int update(String sql, Object input, String dataSource) {
        return jdbc(dataSource).update(sql, source(sql, input));
    }

    private MapSqlParameterSource source(String sql, Object input) {
        return new MapSqlParameterSource(parameters(sql, input));
    }
}
