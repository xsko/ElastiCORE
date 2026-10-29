package io.elasticore.base.model.pub.rules.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The tables and sequences a project's model needs in its database, in the
 * database-neutral words of {@link Column}. Built from the model
 * (MyBatisTables, JpaSchemaReader), written per database by
 * {@link SchemaWriter}, compared with the last one written by
 * {@link SchemaDiff}, and kept between runs as JSON (the migration state).
 */
public final class Schema {

    private final Map<String, Table> tables = new LinkedHashMap<String, Table>();
    private final Map<String, Sequence> sequences = new LinkedHashMap<String, Sequence>();
    /** The databases a migration history has been started for. */
    private final Set<String> databases = new LinkedHashSet<String>();

    /** Adds a table; one already there by the name stays and the new one's columns are added to it. */
    public Table table(Table t) {
        Table had = tables.get(t.name);
        if (had == null) {
            tables.put(t.name, t);
            return t;
        }
        for (Column c : t.columns()) had.column(c);
        for (Table.ForeignKey fk : t.foreignKeys()) had.foreignKey(fk);
        for (Table.Index i : t.indexes()) had.index(i);
        if (had.primaryKey().isEmpty()) had.primaryKey(t.primaryKey());
        return had;
    }

    public Table table(String name) {
        return tables.get(name);
    }

    /** Puts a table in place of the one of its name, where that one was. */
    public void replace(Table t) {
        tables.put(t.name, t);
    }

    public List<Table> tables() {
        return new ArrayList<Table>(tables.values());
    }

    public void sequence(Sequence s) {
        if (!sequences.containsKey(s.name)) sequences.put(s.name, s);
    }

    public List<Sequence> sequences() {
        return new ArrayList<Sequence>(sequences.values());
    }

    public Set<String> databases() {
        return databases;
    }

    public boolean isEmpty() {
        return tables.isEmpty() && sequences.isEmpty();
    }

    /** A sequence a key is taken from. */
    public static final class Sequence {
        public final String name;
        public final long start;
        public final int increment;

        public Sequence(String name, long start, int increment) {
            this.name = name;
            this.start = start;
            this.increment = increment;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Sequence && ((Sequence) o).name.equals(name) && ((Sequence) o).start == start
                    && ((Sequence) o).increment == increment;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    // ---- as JSON -------------------------------------------------------------

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public String toJson() {
        ObjectNode root = JSON.createObjectNode();
        root.put("comment", "The schema the migrations in this directory build, written by ElastiCORE after each one."
                + " Commit it with them; the next change of the model is a migration from this.");
        ArrayNode dbs = root.putArray("databases");
        for (String d : databases) dbs.add(d);
        ArrayNode ts = root.putArray("tables");
        for (Table t : tables.values()) {
            ObjectNode tn = ts.addObject();
            tn.put("name", t.name);
            tn.put("source", t.source);
            ArrayNode cs = tn.putArray("columns");
            for (Column c : t.columns()) {
                ObjectNode cn = cs.addObject();
                cn.put("name", c.name);
                cn.put("type", c.type.name());
                if (c.length > 0) cn.put("length", c.length);
                if (c.precision > 0) {
                    cn.put("precision", c.precision);
                    cn.put("scale", c.scale);
                }
                if (!c.nullable) cn.put("nullable", false);
                if (c.identity) cn.put("identity", true);
                if (c.ascii) cn.put("ascii", true);
            }
            ArrayNode pk = tn.putArray("primaryKey");
            for (String p : t.primaryKey()) pk.add(p);
            ArrayNode fks = tn.putArray("foreignKeys");
            for (Table.ForeignKey fk : t.foreignKeys()) {
                ObjectNode fn = fks.addObject();
                fn.put("name", fk.name);
                ArrayNode fc = fn.putArray("columns");
                for (String s : fk.columns) fc.add(s);
                fn.put("table", fk.table);
                ArrayNode fr = fn.putArray("referenced");
                for (String s : fk.referenced) fr.add(s);
            }
            ArrayNode ix = tn.putArray("indexes");
            for (Table.Index i : t.indexes()) {
                ObjectNode in = ix.addObject();
                in.put("name", i.name);
                ArrayNode ic = in.putArray("columns");
                for (String s : i.columns) ic.add(s);
                if (i.unique) in.put("unique", true);
            }
        }
        ArrayNode ss = root.putArray("sequences");
        for (Sequence s : sequences.values()) {
            ObjectNode sn = ss.addObject();
            sn.put("name", s.name);
            sn.put("start", s.start);
            sn.put("increment", s.increment);
        }
        try {
            return JSON.writeValueAsString(root) + "\n";
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Schema fromJson(String text) {
        Schema s = new Schema();
        JsonNode root;
        try {
            root = JSON.readTree(text);
        } catch (IOException e) {
            throw new IllegalArgumentException("The schema state is not JSON: " + e.getMessage(), e);
        }
        for (JsonNode d : root.path("databases")) s.databases.add(d.asText());
        for (JsonNode tn : root.path("tables")) {
            Table t = new Table(tn.path("name").asText(), tn.path("source").asText(""));
            for (JsonNode cn : tn.path("columns")) {
                t.column(new Column(cn.path("name").asText(), ColumnType.valueOf(cn.path("type").asText()),
                        cn.path("length").asInt(0), cn.path("precision").asInt(0), cn.path("scale").asInt(0),
                        cn.path("nullable").asBoolean(true), cn.path("identity").asBoolean(false),
                        cn.path("ascii").asBoolean(false)));
            }
            List<String> pk = new ArrayList<String>();
            for (JsonNode p : tn.path("primaryKey")) pk.add(p.asText());
            t.primaryKey(pk);
            for (JsonNode fn : tn.path("foreignKeys")) {
                t.foreignKey(new Table.ForeignKey(fn.path("name").asText(), texts(fn.path("columns")),
                        fn.path("table").asText(), texts(fn.path("referenced"))));
            }
            for (JsonNode in : tn.path("indexes")) {
                t.index(new Table.Index(in.path("name").asText(), texts(in.path("columns")), in.path("unique").asBoolean(false)));
            }
            s.tables.put(t.name, t);
        }
        for (JsonNode sn : root.path("sequences")) {
            s.sequence(new Sequence(sn.path("name").asText(), sn.path("start").asLong(1), sn.path("increment").asInt(1)));
        }
        return s;
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<String>();
        for (JsonNode n : array) out.add(n.asText());
        return out;
    }
}
