package io.elasticore.base.model.pub.rules.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A table of the schema: its columns in order, its key, the foreign keys
 * that leave it and the indexes on it. What made it - the entity of which
 * domain - is kept for the messages.
 */
public final class Table {

    public final String name;
    /** "Domain.Entity", for messages; not part of what the table is. */
    public final String source;
    private final Map<String, Column> columns = new LinkedHashMap<String, Column>();
    private final List<String> primaryKey = new ArrayList<String>();
    private final List<ForeignKey> foreignKeys = new ArrayList<ForeignKey>();
    private final List<Index> indexes = new ArrayList<Index>();

    public Table(String name, String source) {
        this.name = name;
        this.source = source;
    }

    /** Adds a column; one already there by the name is kept (a relation read through a column the row holds). */
    public Table column(Column c) {
        if (!columns.containsKey(c.name)) columns.put(c.name, c);
        return this;
    }

    /** Puts a column in place of the one of its name. */
    public Table replace(Column c) {
        columns.put(c.name, c);
        return this;
    }

    public Table primaryKey(String... names) {
        primaryKey.clear();
        Collections.addAll(primaryKey, names);
        return this;
    }

    public Table primaryKey(List<String> names) {
        primaryKey.clear();
        primaryKey.addAll(names);
        return this;
    }

    public Table foreignKey(ForeignKey fk) {
        for (ForeignKey f : foreignKeys) if (f.name.equals(fk.name)) return this;
        foreignKeys.add(fk);
        return this;
    }

    public Table index(Index i) {
        for (Index x : indexes) {
            if (x.name.equals(i.name) || (x.columns.equals(i.columns) && x.unique == i.unique)) return this;
        }
        indexes.add(i);
        return this;
    }

    public Column column(String name) {
        return columns.get(name);
    }

    public List<Column> columns() {
        return new ArrayList<Column>(columns.values());
    }

    public List<String> primaryKey() {
        return Collections.unmodifiableList(primaryKey);
    }

    public List<ForeignKey> foreignKeys() {
        return Collections.unmodifiableList(foreignKeys);
    }

    public List<Index> indexes() {
        return Collections.unmodifiableList(indexes);
    }

    /** A foreign key: its columns and the table and columns they point at. */
    public static final class ForeignKey {
        public final String name;
        public final List<String> columns;
        public final String table;
        public final List<String> referenced;

        public ForeignKey(String name, List<String> columns, String table, List<String> referenced) {
            this.name = name;
            this.columns = Collections.unmodifiableList(new ArrayList<String>(columns));
            this.table = table;
            this.referenced = Collections.unmodifiableList(new ArrayList<String>(referenced));
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ForeignKey)) return false;
            ForeignKey f = (ForeignKey) o;
            return name.equals(f.name) && columns.equals(f.columns) && table.equals(f.table) && referenced.equals(f.referenced);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    /** An index, or a unique constraint said as one. */
    public static final class Index {
        public final String name;
        public final List<String> columns;
        public final boolean unique;

        public Index(String name, List<String> columns, boolean unique) {
            this.name = name;
            this.columns = Collections.unmodifiableList(new ArrayList<String>(columns));
            this.unique = unique;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Index)) return false;
            Index i = (Index) o;
            return name.equals(i.name) && columns.equals(i.columns) && unique == i.unique;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    /**
     * {prefix}{table}_{suffix}, cut to 30 characters - Oracle's limit before
     * 12.2 - with a hash of the whole name keeping two long ones apart.
     */
    public static String constraintName(String prefix, String table, String suffix) {
        String name = (prefix + table.replace('.', '_') + "_" + suffix).toLowerCase(java.util.Locale.ROOT);
        if (name.length() <= 30) return name;
        String hash = Integer.toHexString(name.hashCode() & 0x7fffffff);
        return name.substring(0, 29 - hash.length()) + "_" + hash;
    }
}
