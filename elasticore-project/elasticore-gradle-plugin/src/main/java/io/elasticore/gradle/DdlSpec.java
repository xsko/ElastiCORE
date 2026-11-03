package io.elasticore.gradle;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * The project's DDL: which databases its tables are written for, and whether
 * the changes of the model are written as Flyway migrations.
 *
 * <pre>
 * elasticore {
 *     ddl {
 *         databases = ['postgresql', 'oracle']   // h2, postgresql, oracle, sqlserver
 *         migration = 'flyway'                   // none (the default): the schema only
 *         comments = true                        // each column's label as its comment (off by default)
 *     }
 * }
 * </pre>
 *
 * Every domain's tables - JPA and MyBatis alike - go into one schema per
 * database, src/main/resources/db/schema-{db}.sql; with migrations, each
 * change is db/migration/{db}/V{time}__{what}.sql, beside the state it
 * builds (schema-state.json). A domain changes what it needs in its
 * env.yml: {@code ddl: false}, {@code ddl.databases}, {@code ddl.migration},
 * {@code ddl.comments}, {@code ddl.separate: true}.
 */
public abstract class DdlSpec {

    public abstract ListProperty<String> getDatabases();

    public abstract Property<String> getMigration();

    /**
     * Whether each column's label is said to the database as its comment.
     * Off by default: a state written without comments would otherwise get
     * every one of them in its next migration.
     */
    public abstract Property<Boolean> getComments();
}
