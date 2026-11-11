package io.elasticore.springboot3.mybatis;

import io.elasticore.springboot3.bean.PortRow;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gives the rows of a statement that answers {@link PortRow} - a database
 * port's SQL in a generated MyBatis mapper - every column the SQL selects.
 *
 * MyBatis builds a map of a row from the columns that are not NULL, and
 * answers a row whose columns are all NULL as null; the port runtime reads
 * the same SQL over JDBC and has every column, a NULL one as null. This reads
 * the column labels off the result set as MyBatis opens it, lets MyBatis read
 * the rows, and puts the missing columns in - in the order the SQL selects
 * them. Every other statement is left as it is.
 *
 * The MyBatis Spring Boot starter applies it as it applies any Interceptor
 * bean; the runtime library registers one (ElastiCoreMyBatisAutoConfiguration).
 */
@Intercepts(@Signature(type = ResultSetHandler.class, method = "handleResultSets", args = Statement.class))
public class PortRowColumns implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = mappedStatement(invocation.getTarget());
        if (statement == null || !answersPortRows(statement)) return invocation.proceed();
        List<String> labels = new ArrayList<String>();
        Object[] args = invocation.getArgs();
        args[0] = labelled(
                (Statement) args[0], labels, statement.getConfiguration().isUseColumnLabel());
        return complete(invocation.proceed(), labels);
    }

    /** Whether the statement reads its rows as port rows. */
    static boolean answersPortRows(MappedStatement statement) {
        for (ResultMap map : statement.getResultMaps()) {
            if (PortRow.class.equals(map.getType())) return true;
        }
        return false;
    }

    /** The rows MyBatis read, each with every column of the labels - a NULL one, and a row of NULLs, as null. */
    @SuppressWarnings("unchecked")
    static Object complete(Object result, List<String> labels) {
        if (!(result instanceof List) || labels.isEmpty()) return result;
        List<Object> rows = new ArrayList<Object>();
        for (Object read : (List<?>) result) {
            if (read != null && !(read instanceof Map)) {
                rows.add(read);
                continue;
            }
            Map<String, Object> was = (Map<String, Object>) read;
            PortRow row = new PortRow();
            for (String label : labels) {
                if (!row.containsKey(label)) row.put(label, was == null ? null : was.get(label));
            }
            if (was != null) {
                for (Map.Entry<String, Object> e : was.entrySet()) {
                    if (!row.containsKey(e.getKey())) row.put(e.getKey(), e.getValue());
                }
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * The statement MyBatis reads from, as it is, but for the labels of the
     * first result set it hands out - read off its metadata before a row is.
     */
    private static Statement labelled(Statement statement, List<String> labels, boolean useColumnLabel) {
        Class<?> type = statement instanceof CallableStatement
                ? CallableStatement.class
                : statement instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
        InvocationHandler handler = (proxy, method, methodArgs) -> {
            Object answer;
            try {
                answer = method.invoke(statement, methodArgs);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            if ("getResultSet".equals(method.getName()) && answer instanceof ResultSet rs && labels.isEmpty()) {
                labels.addAll(labels(rs, useColumnLabel));
            }
            return answer;
        };
        return (Statement)
                Proxy.newProxyInstance(PortRowColumns.class.getClassLoader(), new Class<?>[] {type}, handler);
    }

    /** The column names MyBatis keys a row's map by: the labels, unless told the names. */
    static List<String> labels(ResultSet rs, boolean useColumnLabel) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        List<String> labels = new ArrayList<String>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            labels.add(useColumnLabel ? meta.getColumnLabel(i) : meta.getColumnName(i));
        }
        return labels;
    }

    /** The statement the result set handler reads for, through any plugin that wraps it. */
    private static MappedStatement mappedStatement(Object target) {
        Object handler = target;
        while (handler != null && Proxy.isProxyClass(handler.getClass())) {
            InvocationHandler h = Proxy.getInvocationHandler(handler);
            if (!(h instanceof Plugin)) return null;
            handler = SystemMetaObject.forObject(h).getValue("target");
        }
        if (handler == null) return null;
        MetaObject meta = SystemMetaObject.forObject(handler);
        if (!meta.hasGetter("mappedStatement")) return null;
        Object statement = meta.getValue("mappedStatement");
        return statement instanceof MappedStatement mapped ? mapped : null;
    }
}
