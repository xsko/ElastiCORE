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
 *     }
 * }
 * </pre>
 *
 * Every domain's tables - JPA and MyBatis alike - go into one schema per
 * database, src/main/resources/db/schema-{db}.sql; with migrations, each
 * change is db/migration/{db}/V{time}__{what}.sql, beside the state it
 * builds (schema-state.json). A domain changes what it needs in its
 * env.yml: {@code ddl: false}, {@code ddl.databases}, {@code ddl.migration},
 * {@code ddl.separate: true}.
 */
public abstract class DdlSpec {

    public abstract ListProperty<String> getDatabases();

    public abstract Property<String> getMigration();
}
