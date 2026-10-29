package io.elasticore.base.model.pub.rules.schema;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What changed between the schema the migrations have built (the state) and
 * the one the model needs now, as statements per database.
 *
 * What only adds or widens - a table, a column that may be empty, a longer
 * text, a foreign key, an index - runs as written. What can lose data or
 * fail on rows already there - dropping a table or a column, narrowing or
 * changing a type, making a column NOT NULL, a new NOT NULL column - is
 * written as a comment to review: the migration is the developer's file from
 * then on, and a statement that destroys data is theirs to uncomment.
 */
public final class SchemaDiff {

    /** One change: a short description, and whether it runs as written or waits for review. */
    public static final class Change {
        public final String description;
        public final boolean safe;
        /** The statements per database, in order. */
        private final java.util.Map<Database, List<String>> sql = new java.util.EnumMap<Database, List<String>>(Database.class);

        Change(String description, boolean safe) {
            this.description = description;
            this.safe = safe;
        }

        Change sql(Database db, String statement) {
            List<String> l = sql.get(db);
            if (l == null) sql.put(db, l = new ArrayList<String>());
            l.add(statement);
            return this;
        }

        public List<String> sql(Database db) {
            List<String> l = sql.get(db);
            return l == null ? new ArrayList<String>() : l;
        }
    }

    private SchemaDiff() {
    }

    public static List<Change> between(Schema before, Schema after) {
        List<Change> out = new ArrayList<Change>();

        for (Schema.Sequence s : after.sequences()) {
            Schema.Sequence was = null;
            for (Schema.Sequence b : before.sequences()) if (b.name.equals(s.name)) was = b;
            if (was == null) {
                Change c = new Change("sequence " + s.name, true);
                for (Database db : Database.values()) c.sql(db, db.createSequence(s));
                out.add(c);
            } else if (!was.equals(s)) {
                out.add(new Change(String.format("sequence %s now starts at %d and counts by %d (was %d, %d) - ALTER SEQUENCE"
                        + " by hand", s.name, s.start, s.increment, was.start, was.increment), false));
            }
        }

        // A schema a new table is the first of.
        Set<String> had = SchemaWriter.schemas(before);
        for (String schema : SchemaWriter.schemas(after)) {
            if (had.contains(schema)) continue;
            Change c = new Change("schema " + schema, true);
            for (Database db : Database.values()) c.sql(db, db.createSchema(schema));
            out.add(c);
        }

        // New tables, then the keys and indexes that may point at them.
        List<Table> added = new ArrayList<Table>();
        for (Table t : after.tables()) {
            if (before.table(t.name) != null) continue;
            added.add(t);
            Change c = new Change("table " + t.name, true);
            for (Database db : Database.values()) c.sql(db, SchemaWriter.createTable(t, db).trim().replaceAll(";$", ""));
            out.add(c);
        }
        for (Table t : added) {
            for (Table.ForeignKey fk : t.foreignKeys()) out.add(addForeignKey(t, fk));
            for (Table.Index i : t.indexes()) out.add(createIndex(t, i));
        }

        for (Table t : after.tables()) {
            Table old = before.table(t.name);
            if (old == null) continue;
            // What goes, first: SQL Server alters no column an index or a key is still on.
            for (Table.ForeignKey fk : old.foreignKeys()) {
                if (!t.foreignKeys().contains(fk)) {
                    Change c = new Change("drop foreign key " + fk.name, true);
                    for (Database db : Database.values()) c.sql(db, db.dropConstraint(t.name, fk.name));
                    out.add(c);
                }
            }
            for (Table.Index i : old.indexes()) {
                if (!t.indexes().contains(i)) {
                    Change c = new Change("drop index " + i.name, true);
                    for (Database db : Database.values()) c.sql(db, db.dropIndex(t.name, i.name));
                    out.add(c);
                }
            }
            columns(old, t, out);
            if (!old.primaryKey().equals(t.primaryKey())) {
                out.add(new Change(String.format("the key of %s is now (%s), was (%s) - change it by hand",
                        t.name, String.join(", ", t.primaryKey()), String.join(", ", old.primaryKey())), false));
            }
            for (Table.ForeignKey fk : t.foreignKeys()) {
                if (!old.foreignKeys().contains(fk)) out.add(addForeignKey(t, fk));
            }
            for (Table.Index i : t.indexes()) {
                if (!old.indexes().contains(i)) out.add(createIndex(t, i));
            }
        }

        for (Table t : before.tables()) {
            if (after.table(t.name) != null) continue;
            Change c = new Change("table " + t.name + " is no longer in the model - drop it once its rows are not needed", false);
            for (Database db : Database.values()) c.sql(db, db.dropTable(t.name));
            out.add(c);
        }
        for (Schema.Sequence s : before.sequences()) {
            boolean kept = false;
            for (Schema.Sequence n : after.sequences()) kept |= n.name.equals(s.name);
            if (!kept) out.add(new Change("sequence " + s.name + " is no longer used - drop it by hand", false));
        }
        return out;
    }

    private static void columns(Table old, Table t, List<Change> out) {
        for (Column c : t.columns()) {
            Column o = old.column(c.name);
            if (o == null) {
                if (c.nullable || c.identity) {
                    Change ch = new Change("column " + t.name + "." + c.name, true);
                    for (Database db : Database.values()) ch.sql(db, db.addColumn(t.name, c));
                    out.add(ch);
                } else {
                    // NOT NULL on a table with rows needs a value first: added empty, closed by hand.
                    Change ch = new Change("column " + t.name + "." + c.name, true);
                    for (Database db : Database.values()) ch.sql(db, db.addColumn(t.name, c.nullable(true)));
                    out.add(ch);
                    Change close = new Change(String.format("%s.%s is required: give the rows a value, then make it NOT NULL",
                            t.name, c.name), false);
                    for (Database db : Database.values()) close.sql(db, db.alterNullable(t.name, c));
                    out.add(close);
                }
                continue;
            }
            boolean keyColumn = t.primaryKey().contains(c.name);
            if (storageDiffers(o, c)) {
                boolean widens = widens(o, c);
                Change ch = new Change(String.format("%s.%s: %s -> %s%s", t.name, c.name, describe(o), describe(c),
                        keyColumn ? " - a column of the key; change it by hand"
                                : widens ? "" : " - narrows or changes the type; check the rows first"), widens && !keyColumn);
                // The type alone: a NOT NULL the change also brings is its own step (below), and a review one.
                Column typed = c.nullable(o.nullable);
                for (Database db : Database.values()) alterAround(ch, db, old, t, c, db.alterType(t.name, typed));
                out.add(ch);
            }
            if (o.nullable != c.nullable && !keyColumn) {
                Change ch = new Change(String.format("%s.%s %s", t.name, c.name,
                        c.nullable ? "may now be empty" : "is now required: give empty rows a value first"), c.nullable);
                for (Database db : Database.values()) alterAround(ch, db, old, t, c, db.alterNullable(t.name, c));
                out.add(ch);
            }
            if (o.identity != c.identity) {
                out.add(new Change(String.format("%s.%s %s generated by the database - change it by hand", t.name, c.name,
                        c.identity ? "is now" : "is no longer"), false));
            }
        }
        for (Column o : old.columns()) {
            if (t.column(o.name) != null) continue;
            Change ch = new Change(String.format("%s.%s is no longer in the model - drop it once its values are not needed",
                    t.name, o.name), false);
            for (Database db : Database.values()) ch.sql(db, db.dropColumn(t.name, o.name));
            out.add(ch);
        }
    }

    /** Whether a database holds the two differently: the type each one says, compared on every database. */
    static boolean storageDiffers(Column o, Column c) {
        for (Database db : Database.values()) if (!db.type(o).equals(db.type(c))) return true;
        return false;
    }

    /**
     * Whether the change only widens: on every database the same type, only
     * longer, or more digits on both sides of the point - with the defaults a
     * column that said none has (255, DECIMAL(38,2)). A text that grows past
     * what Oracle keeps inline becomes a CLOB there, which no ALTER makes of a
     * VARCHAR2: a type change, for review.
     */
    static boolean widens(Column o, Column c) {
        for (Database db : Database.values()) {
            String a = db.type(o), b = db.type(c);
            if (!base(a).equals(base(b))) return false;
            int[] x = sizes(a), y = sizes(b);
            if (x.length != y.length) return false;
            if (x.length >= 1 && y[0] < x[0]) return false;
            if (x.length == 2 && (y[1] < x[1] || (y[0] - y[1]) < (x[0] - x[1]))) return false;
        }
        return true;
    }

    /** VARCHAR2(30 CHAR) -> VARCHAR2 CHAR; NUMBER(12,2) -> NUMBER. */
    private static String base(String type) {
        return type.replaceAll("\\(\\s*\\d+(\\s*,\\s*\\d+)?", "(").replaceAll("\\s+", " ").trim();
    }

    /** The numbers in a type's parentheses: (30) -> [30], (12,2) -> [12, 2], MAX or none -> []. */
    private static int[] sizes(String type) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(\\s*(\\d+)(?:\\s*,\\s*(\\d+))?").matcher(type);
        if (!m.find()) return new int[0];
        return m.group(2) == null ? new int[]{Integer.parseInt(m.group(1))}
                : new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
    }

    /**
     * An ALTER COLUMN, with what SQL Server needs around it: it changes no
     * column an index is on, so the indexes the column is in - the ones that
     * stay - are dropped first and made again after.
     */
    private static void alterAround(Change ch, Database db, Table old, Table t, Column c, String alter) {
        List<Table.Index> around = new ArrayList<Table.Index>();
        List<Table.ForeignKey> keys = new ArrayList<Table.ForeignKey>();
        if (db == Database.SQLSERVER) {
            for (Table.Index i : old.indexes()) {
                if (i.columns.contains(c.name) && t.indexes().contains(i)) around.add(i);
            }
            for (Table.ForeignKey fk : old.foreignKeys()) {
                if (fk.columns.contains(c.name) && t.foreignKeys().contains(fk)) keys.add(fk);
            }
        }
        for (Table.ForeignKey fk : keys) ch.sql(db, db.dropConstraint(t.name, fk.name));
        for (Table.Index i : around) ch.sql(db, db.dropIndex(t.name, i.name));
        ch.sql(db, alter);
        for (Table.Index i : around) ch.sql(db, db.createIndex(t, i));
        for (Table.ForeignKey fk : keys) ch.sql(db, db.addForeignKey(t.name, fk));
    }

    private static Change addForeignKey(Table t, Table.ForeignKey fk) {
        Change c = new Change("foreign key " + fk.name, true);
        for (Database db : Database.values()) c.sql(db, db.addForeignKey(t.name, fk));
        return c;
    }

    private static Change createIndex(Table t, Table.Index i) {
        Change c = new Change((i.unique ? "unique index " : "index ") + i.name, true);
        for (Database db : Database.values()) c.sql(db, db.createIndex(t, i));
        return c;
    }

    private static String describe(Column c) {
        return c.type + (c.length > 0 ? "(" + c.length + ")" : "") + (c.precision > 0 ? "(" + c.precision + "," + c.scale + ")" : "");
    }

    /**
     * A migration's SQL for one database: what runs, then what waits for
     * review, as comments under a heading saying why.
     */
    public static String render(List<Change> changes, Database db, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- ").append(title).append(", for ").append(db.title).append(".\n");
        sb.append("-- Written by ElastiCORE from the change of the model; yours from now on - it is never written again.\n");
        List<Change> review = new ArrayList<Change>();
        for (Change c : changes) if (!c.safe) review.add(c);
        if (!review.isEmpty()) {
            sb.append("-- REVIEW: ").append(review.size()).append(review.size() == 1 ? " change waits" : " changes wait")
              .append(" for you at the end, commented out - each can lose data or fail on rows already there.\n");
        }
        sb.append("\n");
        for (Change c : changes) {
            if (!c.safe) continue;
            sb.append("-- ").append(c.description).append("\n");
            for (String s : c.sql(db)) sb.append(s).append(";\n");
            sb.append("\n");
        }
        if (!review.isEmpty()) {
            sb.append("-- ---- to review ----------------------------------------------------------\n\n");
            for (Change c : review) {
                sb.append("-- ").append(c.description).append("\n");
                for (String s : c.sql(db)) {
                    for (String line : s.split("\n")) sb.append("-- ").append(line).append("\n");
                    sb.setLength(sb.length() - 1);
                    sb.append(";\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim() + "\n";
    }

    /** A few words of what changed, for the migration's file name: add_order_note, change_customer ... */
    public static String summary(List<Change> changes) {
        Set<String> words = new LinkedHashSet<String>();
        for (Change c : changes) {
            String d = c.description;
            if (d.startsWith("table ") && c.safe) words.add("create_" + d.substring(6).split(" ")[0]);
            else if (d.startsWith("column ")) words.add("add_" + d.substring(7).split(" ")[0].replace('.', '_'));
            else {
                int dot = d.indexOf('.');
                String table = dot > 0 ? d.substring(0, dot).replaceAll(".*\\s", "") : null;
                words.add(table != null && !table.isEmpty() ? "change_" + table : "change");
            }
        }
        String s = String.join("_and_", words).replaceAll("[^A-Za-z0-9_]", "_").toLowerCase(java.util.Locale.ROOT);
        return s.length() > 60 ? s.substring(0, 60).replaceAll("_+$", "") : s;
    }
}
