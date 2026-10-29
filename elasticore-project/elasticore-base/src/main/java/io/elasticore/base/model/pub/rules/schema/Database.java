package io.elasticore.base.model.pub.rules.schema;

import java.util.List;
import java.util.Locale;

/**
 * A database a schema is written for, and how it says each thing: a column
 * type as its Hibernate dialect names it, a generated key, and each change a
 * migration makes - ALTER TABLE is where the four differ most.
 *
 * The id is Spring Boot's name for the database (DatabaseDriver), so
 * spring.flyway.locations=classpath:db/migration/{vendor} finds its folder.
 */
public enum Database {
    H2("h2", "H2"),
    POSTGRESQL("postgresql", "PostgreSQL"),
    ORACLE("oracle", "Oracle 12c or later"),
    SQLSERVER("sqlserver", "SQL Server 2016 or later");

    public final String id;
    public final String title;

    Database(String id, String title) {
        this.id = id;
        this.title = title;
    }

    /** The database an id names (h2, postgresql, oracle, sqlserver; postgres and mssql too), or null. */
    public static Database of(String id) {
        if (id == null) return null;
        String s = id.trim().toLowerCase(Locale.ROOT);
        if (s.equals("postgres")) s = "postgresql";
        if (s.equals("mssql")) s = "sqlserver";
        for (Database d : values()) if (d.id.equals(s)) return d;
        return null;
    }

    /** Hibernate's default length of a text or binary column whose length is not said. */
    public static final int DEFAULT_LENGTH = 255;
    /** Hibernate's default precision and scale of a decimal whose own are not said. */
    public static final int DEFAULT_PRECISION = 38;
    public static final int DEFAULT_SCALE = 2;

    /** The column's type in this database's words. */
    public String type(Column c) {
        int len = c.length > 0 ? c.length : DEFAULT_LENGTH;
        switch (c.type) {
            case TEXT:
                switch (this) {
                    case ORACLE: return len > 4000 ? "CLOB" : "VARCHAR2(" + len + " CHAR)";
                    // Text in NVARCHAR, so any language is kept whatever the collation; a code is ASCII.
                    case SQLSERVER: return c.ascii ? "VARCHAR(" + Math.min(len, 8000) + ")"
                            : len > 4000 ? "NVARCHAR(MAX)" : "NVARCHAR(" + len + ")";
                    default: return "VARCHAR(" + len + ")";
                }
            case LONG_TEXT:
                return this == POSTGRESQL ? "TEXT" : this == SQLSERVER ? "NVARCHAR(MAX)" : "CLOB";
            case UNBOUNDED_TEXT:
                return this == POSTGRESQL ? "TEXT" : this == SQLSERVER ? "NVARCHAR(MAX)" : this == ORACLE ? "CLOB"
                        : "CHARACTER VARYING";
            case BINARY:
                switch (this) {
                    case POSTGRESQL: return "BYTEA";
                    case ORACLE: return len > 2000 ? "BLOB" : "RAW(" + len + ")";
                    case SQLSERVER: return len > 8000 ? "VARBINARY(MAX)" : "VARBINARY(" + len + ")";
                    default: return "VARBINARY(" + len + ")";
                }
            case LONG_BINARY:
                return this == POSTGRESQL ? "BYTEA" : this == SQLSERVER ? "VARBINARY(MAX)" : "BLOB";
            case BIGINT: return this == ORACLE ? "NUMBER(19)" : "BIGINT";
            case INTEGER: return this == ORACLE ? "NUMBER(10)" : this == SQLSERVER ? "INT" : "INTEGER";
            case SMALLINT: return this == ORACLE ? "NUMBER(5)" : "SMALLINT";
            // SQL Server's TINYINT has no negative values; a Java byte has.
            case TINYINT: return this == ORACLE ? "NUMBER(3)" : this == H2 ? "TINYINT" : "SMALLINT";
            case BOOLEAN: return this == ORACLE ? "NUMBER(1)" : this == SQLSERVER ? "BIT" : "BOOLEAN";
            case DECIMAL: {
                int p = c.precision > 0 ? c.precision : DEFAULT_PRECISION;
                int s = c.precision > 0 ? Math.max(0, c.scale) : DEFAULT_SCALE;
                String ps = p + "," + s;
                return this == ORACLE ? "NUMBER(" + ps + ")" : this == POSTGRESQL ? "NUMERIC(" + ps + ")" : "DECIMAL(" + ps + ")";
            }
            case DOUBLE: return this == ORACLE ? "BINARY_DOUBLE" : this == SQLSERVER ? "FLOAT(53)" : "DOUBLE PRECISION";
            case REAL: return this == ORACLE ? "BINARY_FLOAT" : "REAL";
            case DATE: return "DATE";
            case TIME: return this == ORACLE ? "DATE" : "TIME";
            case TIMESTAMP: return this == SQLSERVER ? "DATETIME2(6)" : this == H2 ? "TIMESTAMP" : "TIMESTAMP(6)";
            case TIMESTAMP_TZ:
                return this == SQLSERVER ? "DATETIMEOFFSET(6)" : this == H2 ? "TIMESTAMP WITH TIME ZONE"
                        : "TIMESTAMP(6) WITH TIME ZONE";
            case UUID: return this == ORACLE ? "RAW(16)" : this == SQLSERVER ? "UNIQUEIDENTIFIER" : "UUID";
            case JSON: return this == POSTGRESQL ? "JSONB" : this == SQLSERVER ? "NVARCHAR(MAX)" : "JSON";
            case XML: return this == POSTGRESQL ? "XML" : this == ORACLE ? "XMLTYPE" : this == SQLSERVER ? "XML" : "CLOB";
            default: throw new IllegalArgumentException(String.valueOf(c.type));
        }
    }

    /** The column as a CREATE TABLE or ADD COLUMN declares it. */
    public String declare(Column c) {
        StringBuilder sb = new StringBuilder(c.name).append(' ').append(type(c));
        if (c.identity) sb.append(identity());
        if (!c.nullable) sb.append(" NOT NULL");
        return sb.toString();
    }

    /** How a key the database generates is declared. */
    public String identity() {
        switch (this) {
            case ORACLE: return " GENERATED BY DEFAULT ON NULL AS IDENTITY";
            case SQLSERVER: return " IDENTITY(1,1)";
            default: return " GENERATED BY DEFAULT AS IDENTITY";
        }
    }

    // ---- what a migration changes -------------------------------------------

    public String addColumn(String table, Column c) {
        switch (this) {
            case ORACLE: return "ALTER TABLE " + table + " ADD (" + declare(c) + ")";
            case SQLSERVER: return "ALTER TABLE " + table + " ADD " + declare(c);
            default: return "ALTER TABLE " + table + " ADD COLUMN " + declare(c);
        }
    }

    public String dropColumn(String table, String column) {
        return "ALTER TABLE " + table + " DROP COLUMN " + column;
    }

    /** The column's type changed; its nullability is restated where the database resets it (SQL Server). */
    public String alterType(String table, Column c) {
        switch (this) {
            case POSTGRESQL: return "ALTER TABLE " + table + " ALTER COLUMN " + c.name + " TYPE " + type(c);
            case ORACLE: return "ALTER TABLE " + table + " MODIFY (" + c.name + " " + type(c) + ")";
            case SQLSERVER: return "ALTER TABLE " + table + " ALTER COLUMN " + c.name + " " + type(c) + (c.nullable ? " NULL" : " NOT NULL");
            default: return "ALTER TABLE " + table + " ALTER COLUMN " + c.name + " SET DATA TYPE " + type(c);
        }
    }

    public String alterNullable(String table, Column c) {
        switch (this) {
            case ORACLE: return "ALTER TABLE " + table + " MODIFY (" + c.name + (c.nullable ? " NULL" : " NOT NULL") + ")";
            case SQLSERVER: return "ALTER TABLE " + table + " ALTER COLUMN " + c.name + " " + type(c) + (c.nullable ? " NULL" : " NOT NULL");
            default: return "ALTER TABLE " + table + " ALTER COLUMN " + c.name + (c.nullable ? " DROP NOT NULL" : " SET NOT NULL");
        }
    }

    public String addForeignKey(String table, Table.ForeignKey fk) {
        // H2 looks for an unqualified referenced table in the referencing table's schema, not the default one.
        String target = this == H2 && table.indexOf('.') > 0 && fk.table.indexOf('.') < 0 ? "PUBLIC." + fk.table : fk.table;
        return "ALTER TABLE " + table + " ADD CONSTRAINT " + fk.name + " FOREIGN KEY (" + String.join(", ", fk.columns)
                + ") REFERENCES " + target + " (" + String.join(", ", fk.referenced) + ")";
    }

    public String dropConstraint(String table, String name) {
        return "ALTER TABLE " + table + " DROP CONSTRAINT " + name;
    }

    /**
     * An index. A unique one over a column that may be empty is filtered on
     * SQL Server, whose unique index takes a single NULL; the others take any
     * number.
     */
    public String createIndex(Table t, Table.Index i) {
        StringBuilder sb = new StringBuilder(i.unique ? "CREATE UNIQUE INDEX " : "CREATE INDEX ").append(i.name)
                .append(" ON ").append(t.name).append(" (").append(String.join(", ", i.columns)).append(")");
        if (i.unique && this == SQLSERVER) {
            List<String> nullable = new java.util.ArrayList<String>();
            for (String col : i.columns) {
                Column c = t.column(col);
                if (c == null || c.nullable) nullable.add(col + " IS NOT NULL");
            }
            if (!nullable.isEmpty()) sb.append(" WHERE ").append(String.join(" AND ", nullable));
        }
        return sb.toString();
    }

    /** An index's drop; elsewhere than on SQL Server an index is in its table's schema and named by it. */
    public String dropIndex(String table, String name) {
        if (this == SQLSERVER) return "DROP INDEX " + name + " ON " + table;
        int dot = table.lastIndexOf('.');
        return "DROP INDEX " + (dot > 0 ? table.substring(0, dot + 1) : "") + name;
    }

    /**
     * A schema the tables are in. On Oracle a schema is a user, which a
     * script of this one's tables does not make: it is said as a comment.
     */
    public String createSchema(String schema) {
        switch (this) {
            case ORACLE: return "-- " + schema + " is a user of its own on Oracle: create it, and let this user create its tables";
            case SQLSERVER: return "IF SCHEMA_ID('" + schema + "') IS NULL EXEC('CREATE SCHEMA " + schema + "')";
            default: return "CREATE SCHEMA IF NOT EXISTS " + schema;
        }
    }

    public String createSequence(Schema.Sequence s) {
        return "CREATE SEQUENCE " + s.name + " START WITH " + s.start + " INCREMENT BY " + s.increment;
    }

    public String dropTable(String table) {
        return "DROP TABLE " + table;
    }
}
