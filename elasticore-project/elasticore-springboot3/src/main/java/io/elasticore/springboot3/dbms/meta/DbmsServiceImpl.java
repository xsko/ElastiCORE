package io.elasticore.springboot3.dbms.meta;


import io.elasticore.runtime.port.DbmsService;

import java.lang.annotation.Annotation;
import java.util.Objects;

/**
 * Implementation of the DbmsService annotation interface.
 * This class provides a concrete implementation of the DbmsService annotation,
 * allowing programmatic creation and configuration of DBMS service metadata.
 * 
 * <p>This implementation holds the configuration details for database connectivity
 * including the service identifier, datasource name, and SQL source configuration.</p>
 * 
 * @author ElastiCore Framework
 * @since 1.0
 */
public class DbmsServiceImpl implements DbmsService {

    private final String id;
    private final String datasource;
    private final String sqlSource;

    /**
     * Constructs a new DbmsServiceImpl with the specified configuration.
     * 
     * @param id the unique identifier for this DBMS service
     * @param datasource the name of the datasource to use for database connections
     * @param sqlSource the source configuration for SQL queries and operations
     */
    public DbmsServiceImpl(String id, String datasource, String sqlSource) {
        this.id = id;
        this.datasource = datasource;
        this.sqlSource = sqlSource;
    }

    /**
     * Returns the datasource name for this DBMS service.
     * 
     * @return the name of the datasource to use for database connections
     */
    @Override
    public String datasource() {
        return datasource;
    }

    /**
     * Returns the unique identifier for this DBMS service.
     * 
     * @return the service identifier
     */
    @Override
    public String id() {
        return id;
    }

    /**
     * Returns the SQL source configuration for this DBMS service.
     * 
     * @return the source configuration for SQL queries and operations
     */
    @Override
    public String sqlSource() {
        return sqlSource;
    }

    /**
     * Returns the annotation type represented by this implementation.
     * 
     * @return the DbmsService annotation class
     */
    @Override
    public Class<? extends Annotation> annotationType() {
        return DbmsService.class;
    }

    /**
     * Equal to any {@code DbmsService} with the same members, the annotation the
     * JVM makes from a class's {@code @DbmsService} too - the contract of
     * {@link Annotation#equals(Object)}.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DbmsService other)) {
            return false;
        }
        return Objects.equals(id, other.id())
                && Objects.equals(datasource, other.datasource())
                && Objects.equals(sqlSource, other.sqlSource());
    }

    /** The hash {@link Annotation#hashCode()} defines, so that it agrees with the JVM's annotations. */
    @Override
    public int hashCode() {
        return member("datasource", datasource) + member("id", id) + member("sqlSource", sqlSource);
    }

    private static int member(String name, String value) {
        return (127 * name.hashCode()) ^ (value == null ? 0 : value.hashCode());
    }

    @Override
    public String toString() {
        return "@" + DbmsService.class.getName() + "(datasource=\"" + datasource + "\", id=\"" + id + "\", sqlSource=\""
                + sqlSource + "\")";
    }
}
