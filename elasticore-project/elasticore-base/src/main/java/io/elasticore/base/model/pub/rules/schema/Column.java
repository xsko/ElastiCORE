package io.elasticore.base.model.pub.rules.schema;

import java.util.Objects;

/**
 * One column of a table, as the database holds it: its name, a type in the
 * database-neutral words of {@link ColumnType}, its size, and whether it may
 * be empty. {@link Database} says each type in its own words.
 */
public final class Column {

    public final String name;
    public final ColumnType type;
    /** The length of a text or binary column; 0 for none said (the database's default, 255). */
    public final int length;
    /** The digits of a decimal, and those after the point; 0 for none said. */
    public final int precision;
    public final int scale;
    public final boolean nullable;
    /** A key the database numbers (an identity column). */
    public final boolean identity;
    /** Text known to be ASCII - an enum's code - which SQL Server keeps in VARCHAR, not NVARCHAR. */
    public final boolean ascii;

    public Column(String name, ColumnType type, int length, int precision, int scale, boolean nullable,
                  boolean identity, boolean ascii) {
        this.name = name;
        this.type = type;
        this.length = length;
        this.precision = precision;
        this.scale = scale;
        this.nullable = nullable;
        this.identity = identity;
        this.ascii = ascii;
    }

    public static Column of(String name, ColumnType type) {
        return new Column(name, type, 0, 0, 0, true, false, false);
    }

    public Column length(int length) {
        return new Column(name, type, length, precision, scale, nullable, identity, ascii);
    }

    public Column decimal(int precision, int scale) {
        return new Column(name, type, length, precision, scale, nullable, identity, ascii);
    }

    public Column nullable(boolean nullable) {
        return new Column(name, type, length, precision, scale, nullable, identity, ascii);
    }

    public Column identity(boolean identity) {
        return new Column(name, type, length, precision, scale, nullable, identity, ascii);
    }

    public Column ascii(boolean ascii) {
        return new Column(name, type, length, precision, scale, nullable, identity, ascii);
    }

    /** A column of another name holding the same - a foreign key holding a key. */
    public Column named(String other) {
        return new Column(other, type, length, precision, scale, nullable, false, ascii);
    }

    /** Whether the database holds the two alike: what a migration need not change. */
    public boolean sameStorage(Column o) {
        return type == o.type && length == o.length && precision == o.precision && scale == o.scale
                && ascii == o.ascii;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Column)) return false;
        Column c = (Column) o;
        return name.equals(c.name) && sameStorage(c) && nullable == c.nullable && identity == c.identity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, length, precision, scale, nullable, identity, ascii);
    }

    @Override
    public String toString() {
        return name + " " + type + (length > 0 ? "(" + length + ")" : "")
                + (precision > 0 ? "(" + precision + "," + scale + ")" : "") + (nullable ? "" : " NOT NULL")
                + (identity ? " IDENTITY" : "");
    }
}
