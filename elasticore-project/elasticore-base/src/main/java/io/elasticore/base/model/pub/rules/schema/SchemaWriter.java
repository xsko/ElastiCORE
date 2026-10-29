package io.elasticore.base.model.pub.rules.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * A schema as one database's SQL: the sequences, every table with its key,
 * then the foreign keys - after every table, so the tables can be created in
 * any order - and the indexes, unique ones among them.
 */
public final class SchemaWriter {

    private SchemaWriter() {
    }

    /** The statements, each ending in a semicolon, separated by blank lines between the groups. */
    public static String body(Schema schema, Database db) {
        StringBuilder sb = new StringBuilder();
        for (String s : schemas(schema)) {
            String statement = db.createSchema(s);
            sb.append(statement).append(statement.startsWith("--") ? "\n" : ";\n");
        }
        for (Schema.Sequence s : schema.sequences()) sb.append(db.createSequence(s)).append(";\n");
        for (Table t : schema.tables()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(createTable(t, db));
        }
        StringBuilder keys = new StringBuilder();
        for (Table t : schema.tables()) {
            for (Table.ForeignKey fk : t.foreignKeys()) keys.append(db.addForeignKey(t.name, fk)).append(";\n");
        }
        if (keys.length() > 0) sb.append("\n").append(keys);
        StringBuilder indexes = new StringBuilder();
        for (Table t : schema.tables()) {
            for (Table.Index i : t.indexes()) indexes.append(db.createIndex(t, i)).append(";\n");
        }
        if (indexes.length() > 0) sb.append("\n").append(indexes);
        return sb.toString().trim();
    }

    /** The schemas the tables name (acct.ledger), in order. */
    public static java.util.Set<String> schemas(Schema schema) {
        java.util.Set<String> out = new java.util.LinkedHashSet<String>();
        for (Table t : schema.tables()) {
            int dot = t.name.lastIndexOf('.');
            if (dot > 0) out.add(t.name.substring(0, dot));
        }
        return out;
    }

    public static String createTable(Table t, Database db) {
        List<String> lines = new ArrayList<String>();
        for (Column c : t.columns()) lines.add(db.declare(c));
        if (!t.primaryKey().isEmpty()) lines.add("PRIMARY KEY (" + String.join(", ", t.primaryKey()) + ")");
        StringBuilder sb = new StringBuilder("CREATE TABLE ").append(t.name).append(" (\n");
        for (int i = 0; i < lines.size(); i++) {
            sb.append("    ").append(lines.get(i)).append(i < lines.size() - 1 ? "," : "").append('\n');
        }
        return sb.append(");\n").toString();
    }
}
