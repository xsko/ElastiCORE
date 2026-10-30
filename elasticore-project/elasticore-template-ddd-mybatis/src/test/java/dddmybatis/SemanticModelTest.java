package dddmybatis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.yaml.snakeyaml.Yaml;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Apache Ossie semantic model the build exports (blueprint/semantic_model.yml)
 * - two domains, inherited audit columns, a self-reference, every value type -
 * describes the database this application really has: every dataset is a
 * table, every field a column, every key and every join the ones the tables
 * carry. A semantic model that names a column the database does not have is
 * a query that fails in somebody's BI tool.
 */
@SpringBootTest
class SemanticModelTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @SuppressWarnings("unchecked")
    void theExportedModelIsTheDatabase() throws Exception {
        Map<String, Object> model;
        try (InputStream in = getClass().getResourceAsStream("/blueprint/semantic_model.yml")) {
            assertThat(in).as("the build exports blueprint/semantic_model.yml").isNotNull();
            model = new Yaml().load(in);
        }
        assertThat(model.get("version")).isEqualTo("0.2.0.dev0");

        List<String> problems = new ArrayList<String>();
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            Map<String, Set<String>> columnsOf = new java.util.HashMap<String, Set<String>>();
            for (Map<String, Object> ds : (List<Map<String, Object>>) model.get("datasets")) {
                String table = (String) ds.get("source");
                Set<String> columns = columns(md, table);
                columnsOf.put((String) ds.get("name"), columns);
                if (columns.isEmpty()) {
                    problems.add("no table " + table);
                    continue;
                }
                for (Map<String, Object> f : (List<Map<String, Object>>) ds.get("fields")) {
                    if (!columns.contains(((String) f.get("name")).toLowerCase())) {
                        problems.add(table + " has no column " + f.get("name"));
                    }
                }
                assertThat(new LinkedHashSet<String>((List<String>) ds.get("primary_key")))
                        .as(table + " primary key").isEqualTo(primaryKey(md, table));
            }
            for (Map<String, Object> r : (List<Map<String, Object>>) model.get("relationships")) {
                for (String col : (List<String>) r.get("from_columns")) {
                    if (!columnsOf.get(r.get("from")).contains(col)) problems.add(r.get("name") + ": no " + r.get("from") + "." + col);
                }
                for (String col : (List<String>) r.get("to_columns")) {
                    if (!columnsOf.get(r.get("to")).contains(col)) problems.add(r.get("name") + ": no " + r.get("to") + "." + col);
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    private static Set<String> columns(DatabaseMetaData md, String table) throws Exception {
        Set<String> out = new LinkedHashSet<String>();
        try (ResultSet rs = md.getColumns(null, null, table.toUpperCase(), null)) {
            while (rs.next()) out.add(rs.getString("COLUMN_NAME").toLowerCase());
        }
        return out;
    }

    private static Set<String> primaryKey(DatabaseMetaData md, String table) throws Exception {
        Set<String> out = new LinkedHashSet<String>();
        try (ResultSet rs = md.getPrimaryKeys(null, null, table.toUpperCase())) {
            while (rs.next()) out.add(rs.getString("COLUMN_NAME").toLowerCase());
        }
        return out;
    }
}
