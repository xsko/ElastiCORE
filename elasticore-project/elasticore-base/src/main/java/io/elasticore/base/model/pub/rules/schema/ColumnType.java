package io.elasticore.base.model.pub.rules.schema;

/**
 * What a column holds, in words no database owns. {@link Database} turns each
 * into its own type name - the one its Hibernate dialect chooses - so a table
 * made from this schema is the table JPA expects and MyBatis reads.
 */
public enum ColumnType {
    /** Text of a length (VARCHAR, VARCHAR2, NVARCHAR). */
    TEXT,
    /** Text without a limit worth saying (CLOB, TEXT, NVARCHAR(MAX)). */
    LONG_TEXT,
    /**
     * Text of no length that JDBC calls VARCHAR - what JPA's
     * columnDefinition = "TEXT" asks for: PostgreSQL's TEXT, H2's CHARACTER
     * VARYING, SQL Server's NVARCHAR(MAX); Oracle has none (CLOB).
     */
    UNBOUNDED_TEXT,
    /** Bytes of a length (VARBINARY, RAW). */
    BINARY,
    /** Bytes without a limit (BLOB, BYTEA, VARBINARY(MAX)). */
    LONG_BINARY,
    BIGINT,
    INTEGER,
    SMALLINT,
    TINYINT,
    BOOLEAN,
    /** A fixed-point number of a precision and scale. */
    DECIMAL,
    DOUBLE,
    REAL,
    DATE,
    TIME,
    /** A date and time without a zone (LocalDateTime). */
    TIMESTAMP,
    /** A date and time with its offset (Instant, OffsetDateTime, ZonedDateTime). */
    TIMESTAMP_TZ,
    UUID,
    JSON,
    XML
}
