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

    /**
     * The first Oracle version with a BOOLEAN column, which Hibernate's OracleDialect maps a
     * boolean to from then on (BIT, a NUMBER(1), below it).
     */
    public static final int ORACLE_BOOLEAN = 23;

    /** The title of the database the DDL is for: on Oracle 23 or later, said as that. */
    public String title(int oracleVersion) {
        return this == ORACLE && oracleVersion >= ORACLE_BOOLEAN ? "Oracle " + ORACLE_BOOLEAN + " or later" : title;
    }

    /** The column's type in this database's words, for an Oracle below 23 or unsaid. */
    public String type(Column c) {
        return type(c, 0);
    }

    /** The column's type in this database's words, for the Oracle version the DDL is for (0: unsaid). */
    public String type(Column c, int oracleVersion) {
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
                // A JPA clob is VARCHAR(MAX) on SQL Server, as Hibernate maps it (ascii); MyBatis's is NVARCHAR(MAX).
                return this == POSTGRESQL
                        ? "TEXT"
                        : this == SQLSERVER ? (c.ascii ? "VARCHAR(MAX)" : "NVARCHAR(MAX)") : "CLOB";
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
            case BOOLEAN:
                if (this == ORACLE) return oracleVersion >= ORACLE_BOOLEAN ? "BOOLEAN" : "NUMBER(1)";
                return this == SQLSERVER ? "BIT" : "BOOLEAN";
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

    /** The column as a CREATE TABLE or ADD COLUMN declares it, for the Oracle version the DDL is for. */
    public String declare(Column c, int oracleVersion) {
        StringBuilder sb = new StringBuilder(c.name).append(' ').append(type(c, oracleVersion));
        if (c.identity || (c.nativeKey && nativeIdentity())) sb.append(identity());
        if (!c.nullable) sb.append(" NOT NULL");
        return sb.toString();
    }

    /**
     * Whether Hibernate's native generator numbers a key with an identity column here (H2, SQL
     * Server) rather than a sequence (PostgreSQL, Oracle) - its dialects' choice in 6.5 to 7: an
     * identity where the database has one, but for PostgreSQL and Oracle, which prefer sequences.
     */
    public boolean nativeIdentity() {
        return this == H2 || this == SQLSERVER;
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

    public String addColumn(String table, Column c, int oracleVersion) {
        switch (this) {
            case ORACLE:
                return "ALTER TABLE " + table + " ADD (" + declare(c, oracleVersion) + ")";
            case SQLSERVER:
                return "ALTER TABLE " + table + " ADD " + declare(c, oracleVersion);
            default:
                return "ALTER TABLE " + table + " ADD COLUMN " + declare(c, oracleVersion);
        }
    }

    public String dropColumn(String table, String column) {
        return "ALTER TABLE " + table + " DROP COLUMN " + column;
    }

    /** The column's type changed; its nullability is restated where the database resets it (SQL Server). */
    public String alterType(String table, Column c, int oracleVersion) {
        switch (this) {
            case POSTGRESQL: return "ALTER TABLE " + table + " ALTER COLUMN " + c.name + " TYPE " + type(c);
            case ORACLE:
                return "ALTER TABLE " + table + " MODIFY (" + c.name + " " + type(c, oracleVersion) + ")";
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

    /**
     * What a column holds, said to the database: COMMENT ON COLUMN, which sets
     * or replaces it, and on SQL Server the MS_Description property - updated
     * where the column has one, added where it has none.
     */
    public String comment(String table, String column, String text) {
        if (this == SQLSERVER) {
            // Whether the property is there is the database's to say, not the state's: comments switched off
            // and on again, a database Hibernate made, a baseline - each can hold one the state does not.
            String args = "@name = N'MS_Description', @value = N'" + quoted(text) + "', " + levels(table, column);
            return schemaOf(table) + " IF " + described(table, column) + " EXEC sp_updateextendedproperty " + args
                    + " ELSE EXEC sp_addextendedproperty " + args;
        }
        return "COMMENT ON COLUMN " + table + "." + column + " IS '" + quoted(text) + "'";
    }

    /** A column that no longer says what it holds. Oracle reads '' as no comment; it has no NULL there. */
    public String dropComment(String table, String column) {
        switch (this) {
            case SQLSERVER:
                return schemaOf(table) + " IF " + described(table, column)
                        + " EXEC sp_dropextendedproperty @name = N'MS_Description', " + levels(table, column);
            case ORACLE:
                return "COMMENT ON COLUMN " + table + "." + column + " IS ''";
            default:
                return "COMMENT ON COLUMN " + table + "." + column + " IS NULL";
        }
    }

    /**
     * The most bytes a comment may have here, or 0 for no limit worth saying: Oracle keeps 4000
     * (counted in UTF-8), SQL Server's property 7500 (an NVARCHAR, two bytes a character).
     */
    public int commentLimit() {
        switch (this) {
            case ORACLE:
                return 4000;
            case SQLSERVER:
                return 7500;
            default:
                return 0;
        }
    }

    /** The bytes the database counts of a comment. */
    public int commentBytes(String text) {
        return text.getBytes(
                        this == SQLSERVER
                                ? java.nio.charset.StandardCharsets.UTF_16LE
                                : java.nio.charset.StandardCharsets.UTF_8)
                .length;
    }

    private static String quoted(String text) {
        return text.replace("'", "''");
    }

    /**
     * SQL Server's schema of the table, as @s: the one it names, or else the login's default, where
     * a table made unqualified is - SCHEMA_NAME(), through a variable, since EXEC takes no call.
     */
    private static String schemaOf(String table) {
        int dot = table.lastIndexOf('.');
        return "DECLARE @s sysname = " + (dot > 0 ? "N'" + table.substring(0, dot) + "'" : "SCHEMA_NAME()");
    }

    /** Whether the column has an MS_Description, in the schema @s. */
    private static String described(String table, String column) {
        String object = "OBJECT_ID(@s + N'." + bareName(table) + "')";
        return "EXISTS (SELECT 1 FROM sys.extended_properties WHERE name = N'MS_Description' AND major_id = " + object
                + " AND minor_id = COLUMNPROPERTY(" + object + ", N'" + column + "', 'ColumnId'))";
    }

    /** SQL Server's three levels of a column: the schema @s, the table, the column. */
    private static String levels(String table, String column) {
        return "@level0type = N'SCHEMA', @level0name = @s, @level1type = N'TABLE', @level1name = N'" + bareName(table)
                + "', @level2type = N'COLUMN', @level2name = N'" + column + "'";
    }

    private static String bareName(String table) {
        return table.substring(table.lastIndexOf('.') + 1);
    }

    public String createSequence(Schema.Sequence s) {
        return "CREATE SEQUENCE " + s.name + " START WITH " + s.start + " INCREMENT BY " + s.increment;
    }

    public String dropTable(String table) {
        return "DROP TABLE " + table;
    }
}
