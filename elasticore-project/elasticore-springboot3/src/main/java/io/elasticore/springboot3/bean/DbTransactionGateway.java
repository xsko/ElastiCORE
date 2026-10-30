package io.elasticore.springboot3.bean;


import io.elasticore.runtime.port.DbmsService;
import io.elasticore.springboot3.dbms.meta.SqlQueryInfo;
import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Transaction Gateway Service for executing dynamic queries with multiple data sources.
 *
 * Runs a DBMS port's SQL through JPA's EntityManager - in the transaction the
 * EntityManager is in - and reads a JPQL query as JPA does. What is done with
 * the SQL before and the rows after is {@link SqlPortGateway}'s.
 */
@Service
public class DbTransactionGateway extends SqlPortGateway {

    private final EntityManagerFactory defaultEntityManagerFactory;
    private final Map<String, EntityManagerFactory> entityManagerFactories;

    /**
     * Constructs a new DbTransactionGateway with the default EntityManagerFactory.
     * 
     * @param defaultEntityManagerFactory the default EntityManagerFactory to use for database operations
     * @throws IOException if an error occurs during initialization
     */
    @Autowired
    public DbTransactionGateway(EntityManagerFactory defaultEntityManagerFactory) throws IOException {
        this.defaultEntityManagerFactory = defaultEntityManagerFactory;
        this.entityManagerFactories = new HashMap<>();
        this.entityManagerFactories.put("primary", defaultEntityManagerFactory);
    }



    /**
     * Sets multiple EntityManagerFactories for multi-datasource support.
     * This method is called by Spring to inject additional EntityManagerFactories
     * when multiple datasources are configured.
     * 
     * @param entityManagerFactories a map of datasource names to their corresponding EntityManagerFactories
     * @throws IOException if an error occurs during configuration
     */
    @Autowired(required = false)
    public void setEntityManagerFactories(Map<String, EntityManagerFactory> entityManagerFactories) throws IOException {
        if (entityManagerFactories != null && !entityManagerFactories.isEmpty()) {
            for (Map.Entry<String, EntityManagerFactory> entry : entityManagerFactories.entrySet()) {
                String key = entry.getKey();
                EntityManagerFactory value = entry.getValue();

                this.entityManagerFactories.put(key, value);

                if (key.endsWith("EntityManagerFactory")) {
                    String simplifiedKey = key.substring(0, key.length() - "EntityManagerFactory".length());
                    if (!simplifiedKey.isEmpty()) {
                        this.entityManagerFactories.put(simplifiedKey, value);
                    }
                }
            }
        }
    }

    @Override
    protected List<Map<String, Object>> rows(SqlQueryInfo entry, String sql, Object input, String dataSource,
                                             int first, int max) {
        EntityManager entityManager = getEntityManager(dataSource);
        Query query = entry.getNativeQuery()
                ? entityManager.createNativeQuery(sql, Tuple.class)
                : entityManager.createQuery(sql, Tuple.class);
        bindQueryParameters(query, sql, input);
        if (first > 0) query.setFirstResult(first);
        if (max >= 0) query.setMaxResults(max);
        @SuppressWarnings("unchecked")
        List<Tuple> resultList = query.getResultList();
        List<Map<String, Object>> rows = new ArrayList<>(resultList.size());
        for (Tuple tuple : resultList) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (TupleElement<?> e : tuple.getElements()) row.put(e.getAlias(), tuple.get(e));
            rows.add(row);
        }
        return rows;
    }

    @Override
    protected long count(String sql, Object input, String dataSource) {
        Query countQuery = getEntityManager(dataSource).createNativeQuery(sql);
        bindQueryParameters(countQuery, sql, input);
        return ((Number) countQuery.getSingleResult()).longValue();
    }

    @Override
    protected int update(String sql, Object input, String dataSource) {
        Query query = getEntityManager(dataSource).createNativeQuery(sql);
        bindQueryParameters(query, sql, input);
        return query.executeUpdate();
    }


    /**
     * Executes an update, insert, or delete operation once per element of the input list
     * within a single transaction. Each element of the list is used as the parameter
     * source for one execution of the SQL, and the affected row counts are summed.
     *
     * <p>For static SQL (no inline substitution or conditional blocks), the Query object
     * is created once and only the parameters are rebound for each element. For dynamic
     * SQL, the query is processed and created per element because the resulting SQL
     * may differ depending on the element values.</p>
     *
     * @param dbmsSvcMeta the DBMS service metadata configuration
     * @param methodName the name of the method/operation to execute
     * @param inputList the list of parameter source objects, one per execution
     * @return the total number of rows affected by all executions
     * @throws IOException if an I/O error occurs during execution
     * @throws InvocationTargetException if the underlying method throws an exception
     * @throws NoSuchMethodException if the specified method cannot be found
     */
    @Override
    @Transactional
    public int executeBatchUpdate(DbmsService dbmsSvcMeta, String methodName, List<?> inputList) throws IOException, InvocationTargetException, NoSuchMethodException {
        if (inputList == null || inputList.isEmpty())
            return 0;

        String dataSource = dbmsSvcMeta.datasource();
        EntityManager entityManager = getEntityManager(dataSource);
        SqlQueryInfo entry = findSqlQueryInfo(dbmsSvcMeta, methodName);
        if (entry == null) {
            throw new RuntimeException("Process ID not found: " + dbmsSvcMeta.id() + "." + methodName);
        }

        String rawSql = entry.getSql();
        boolean isDynamicSql = rawSql.indexOf("${") >= 0 || rawSql.indexOf("/* if") >= 0;

        int total = 0;
        if (!isDynamicSql) {
            // Static SQL: create the query once and rebind parameters for each element
            Query query = entityManager.createNativeQuery(rawSql);
            for (Object item : inputList) {
                if (item == null)
                    continue;
                bindQueryParameters(query, rawSql, item);
                total += query.executeUpdate();
            }
        } else {
            // Dynamic SQL: conditional blocks or inline substitution may differ per element
            for (Object item : inputList) {
                if (item == null)
                    continue;
                String processedQuery = processConditionalQuery(rawSql, item);
                Query query = entityManager.createNativeQuery(processedQuery);
                bindQueryParameters(query, processedQuery, item);
                total += query.executeUpdate();
            }
        }
        return total;
    }

    private EntityManager getEntityManager(String dataSource) {
        if (dataSource == null || dataSource.isEmpty()) {
            dataSource = "primary";
        }

        EntityManagerFactory emf = entityManagerFactories.getOrDefault(dataSource, defaultEntityManagerFactory);
        return EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
    }

    /**
     * Binds parameters to a JPA Query based on named parameters found in the SQL.
     * Parameters are extracted from the input object using reflection or Map access.
     *
     * @param query the JPA Query object to bind parameters to
     * @param sql the SQL string containing named parameters
     * @param input the input object containing parameter values
     */
    private void bindQueryParameters(Query query, String sql, Object input) {
        for (Map.Entry<String, Object> p : parameters(sql, input).entrySet()) {
            query.setParameter(p.getKey(), p.getValue());
        }
    }
}
