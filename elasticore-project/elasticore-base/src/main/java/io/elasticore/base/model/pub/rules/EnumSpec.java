package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only view of a DSL enumeration for rule authors.
 *
 * Its declared fields reuse {@link FieldSpec}, so the type questions the
 * rules ask - is this a string, what does its getter look like - are the same
 * ones they ask of an entity field.
 */
public final class EnumSpec {

    /** One declared constant, with the values it passes to the constructor. */
    public static final class Constant {
        private final String name;
        private final List<String> values;

        Constant(String name, List<String> values) {
            this.name = name;
            this.values = new ArrayList<String>(values);
        }

        public String name() { return name; }
        public List<String> values() { return values; }
    }

    private final String name;
    private final List<FieldSpec> fields = new ArrayList<FieldSpec>();
    private final List<Constant> constants = new ArrayList<Constant>();
    private final Map<String, String> packages = new LinkedHashMap<String, String>();

    /** Field named by @json / @db, or null when the enum declares neither. */
    private String jsonFieldName;
    private String dbFieldName;

    private SourceCode code;

    public EnumSpec(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public EnumSpec addField(Field field) {
        fields.add(new FieldSpec(field, null));
        return this;
    }

    public List<FieldSpec> fields() {
        return fields;
    }

    public EnumSpec constant(String name, String... values) {
        constants.add(new Constant(name, java.util.Arrays.asList(values)));
        return this;
    }

    public List<Constant> constants() {
        return constants;
    }

    /** The field @json names, whose value crosses the JSON boundary. */
    public EnumSpec jsonField(String fieldName) {
        this.jsonFieldName = fieldName;
        return this;
    }

    /** The field @db names, whose value is stored in the column. */
    public EnumSpec dbField(String fieldName) {
        this.dbFieldName = fieldName;
        return this;
    }

    public String jsonFieldName() {
        return jsonFieldName;
    }

    public String dbFieldName() {
        return dbFieldName;
    }

    /** The declared field of the given name, or null. */
    public FieldSpec field(String fieldName) {
        if (fieldName == null) return null;
        for (FieldSpec f : fields) {
            if (f.name().equals(fieldName)) return f;
        }
        return null;
    }

    public EnumSpec pkg(String artifact, String packageName) {
        packages.put(artifact, packageName);
        return this;
    }

    public String pkg(String artifact) {
        return packages.get(artifact);
    }

    void attach(SourceCode code) {
        this.code = code;
    }

    SourceCode code() {
        return code;
    }
}
