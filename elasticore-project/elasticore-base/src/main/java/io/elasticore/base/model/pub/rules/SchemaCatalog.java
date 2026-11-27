package io.elasticore.base.model.pub.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /schema/info answered from the model (env.yml {@code config: schemaInfo: model}, issue 373).
 *
 * The generator writes {@code src/main/resources/elasticore/<domain>-schema.json}: for each entity whose
 * controller serves the endpoint, the entity (its label, path, key, DTO and search DTO) and each field of the
 * DTO the endpoint describes, with the facts the TypeScript client is written from (FieldFacts): kind, label,
 * labels, required, lengths, bounds, read-only, html, the enumeration's values and what a reference points at.
 * The controller's line calls the runtime's SchemaInfo, which reads that file, says each label in the request's
 * language and adds the keys the answer always had (DTOUtils.getMetaInfo). The file is ASCII: every other
 * character is a JSON escape, so any reader decodes it alike.
 *
 * Unset, nothing here is written and the controller says what it always said.
 */
public final class SchemaCatalog {

    private SchemaCatalog() {}

    /** The env.yml config key. */
    public static final String SETTING = "schemaInfo";

    /** The value that asks for the model's answer. */
    public static final String MODEL = "model";

    /** The shape of the answer, for a screen to rely on. */
    public static final int META_VERSION = 1;

    /** The runtime class the controller calls. */
    public static final String RUNTIME = "io.elasticore.springboot3.schema.SchemaInfo";

    /** The controller skeleton's slot for what getMetaInfo() returns. */
    public static final String SLOT = "schemaInfoAnswer";

    /** Whether a domain's setting asks for the model's answer: exactly "model". */
    public static boolean asked(String value) {
        return value != null && MODEL.equals(value.trim());
    }

    /** A warning for a value the setting does not take; null for none, or for model. */
    public static String settingProblem(String domain, String value) {
        if (value == null || value.trim().isEmpty() || asked(value)) return null;
        return String.format(
                "[%s] env.yml config.schemaInfo '%s' is not a value it takes - model (GET /schema/info answers from"
                        + " the model); it is ignored, and the endpoint answers as before",
                domain, value.trim());
    }

    /** The warning for the setting in a domain that generates the 2.x code. */
    public static String compatibleProblem(String domain) {
        return String.format(
                "[%s] config: schemaInfo: model is written for the 3.0 code - this domain generates the 2.x code"
                        + " (output = 'v2'), whose GET /schema/info answers as before (DTOUtils.getMetaInfo)",
                domain);
    }

    /** Where the file is, under src/main/resources and on the classpath. */
    public static String resource(String domain) {
        return "elasticore/" + domain + "-schema.json";
    }

    /**
     * What the controller's getMetaInfo() returns: the runtime's answer from the model, or - without the
     * setting - the line it always had.
     */
    public static String answer(boolean model, boolean kotlin, String domain, String entity, String dtoName) {
        if (model) return String.format("%s.answer(%s, %s)", RUNTIME, string(domain), string(entity));
        return kotlin
                ? String.format("ResponseEntity.ok(DTOUtils.getMetaInfo(%s::class.java))", dtoName)
                : String.format("ResponseEntity.ok(DTOUtils.getMetaInfo(%s.class))", dtoName);
    }

    /** The warning for a controller skeleton without the slot - a project's own control.tmpl; null when it has it. */
    public static String skeletonProblem(String domain, String entity, String skeleton) {
        if (skeleton == null || skeleton.contains("${" + SLOT + "}")) return null;
        return String.format(
                "[%s] %s: config: schemaInfo: model, but the controller skeleton (control.tmpl) has no ${%s}, so"
                        + " GET /schema/info answers as before - write 'return ${%s}' in its getMetaInfo()",
                domain, entity, SLOT, SLOT);
    }

    /**
     * The warning for a controller the developer keeps whose GET /schema/info does not call the runtime the
     * generator now writes - it answers as before. Null when the generator writes no such call (the setting is
     * off, the endpoint is left out), when the file calls it, or when the file serves no /schema/info.
     */
    public static String keptControllerProblem(String path, String wouldWrite, String onDisk) {
        if (wouldWrite == null || onDisk == null) return null;
        String call = callIn(wouldWrite);
        if (call == null || onDisk.contains("SchemaInfo.answer(") || !onDisk.contains("/schema/info")) return null;
        return String.format(
                "[elcore3] %s is yours, and its GET /schema/info does not call SchemaInfo.answer - it answers as"
                        + " before, not from the model (config: schemaInfo: model). Return %s there, or delete the"
                        + " file to have it generated anew.",
                path, call);
    }

    /** The runtime call in a rendered controller, or null. */
    private static String callIn(String text) {
        int at = text.indexOf(RUNTIME + ".answer(");
        if (at < 0) return null;
        int end = text.indexOf(')', at);
        return end < 0 ? null : text.substring(at, end + 1);
    }

    // ---- the file ----------------------------------------------------------------

    /** An entity whose controller serves GET /schema/info. */
    public static final class Served {
        final EntitySpec entity;
        final String path;
        final String dto;
        final String searchDto;

        /**
         * @param entity    the controller's entity
         * @param path      the controller's path (apiPath/domain/entity)
         * @param dto       the DTO the endpoint describes, by its class name
         * @param searchDto the search DTO POST /search takes, by its class name
         */
        public Served(EntitySpec entity, String path, String dto, String searchDto) {
            this.entity = entity;
            this.path = path;
            this.dto = dto;
            this.searchDto = searchDto;
        }

        public EntitySpec entity() {
            return entity;
        }
    }

    /** A DTO as it was written: its class, its spec and the fields the class declares, in order. */
    public static final class Dto {
        final String className;
        final EntitySpec spec;
        final List<FieldSpec> fields;

        public Dto(String className, EntitySpec spec, List<FieldSpec> fields) {
            this.className = className;
            this.spec = spec;
            this.fields = fields;
        }
    }

    /** What the file needs from the run beyond the entity itself. */
    public interface Model {
        /** A DTO this run wrote, by its class name; null when it wrote none. */
        Dto dto(String className);

        /** An entity of the run, by name; null when none is called that. */
        EntitySpec entity(String name);

        /** An enumeration of the run, by name; null when none is called that. */
        EnumSpec enumeration(String name);

        /** The domain an enumeration of the run is declared in, whose label bundle names its values; null when none. */
        String enumerationDomain(String name);

        /** Whether the entity's controller serves POST /search. */
        boolean searches(EntitySpec entity);
    }

    /** The file's text: one entry per entity, in the order given. */
    public static String json(String domain, List<Served> served, Model model) {
        Map<String, Object> entities = new LinkedHashMap<String, Object>();
        for (Served s : served) entities.put(s.entity.getName(), entry(domain, s, model));
        Map<String, Object> file = new LinkedHashMap<String, Object>();
        file.put("metaVersion", META_VERSION);
        file.put("domain", domain);
        file.put("entities", entities);
        StringBuilder sb = new StringBuilder();
        write(sb, file, "");
        return sb.append('\n').toString();
    }

    private static Map<String, Object> entry(String domain, Served s, Model model) {
        Dto dto = model.dto(s.dto);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (dto != null) out.put("class", dto.className);
        out.put("entity", entity(domain, s, dto));
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        if (dto != null) {
            for (FieldSpec f : dto.fields) fields.put(f.name(), field(f, s.entity, dto.fields, model));
        }
        out.put("fields", fields);
        return out;
    }

    private static Map<String, Object> entity(String domain, Served s, Dto dto) {
        FieldFacts.Label label = FieldFacts.entityLabel(dto != null ? dto.spec : s.entity);
        // A DTO without a label of its own, of a type the model does not know: the entity's own label.
        if (label == null && dto != null) label = FieldFacts.entityLabel(s.entity);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("name", s.entity.getName());
        out.put("domain", domain);
        out.put("label", label != null ? label.text() : s.entity.getName());
        out.put("labels", label != null ? label.translations() : Collections.<String, String>emptyMap());
        out.put("path", s.path);
        out.put("key", key(s.entity.pkFields()));
        out.put("dto", s.dto);
        out.put("searchDto", s.searchDto);
        return out;
    }

    /** One key field by its name; a composite key as the list of its names. */
    private static Object key(List<FieldSpec> keys) {
        if (keys.isEmpty()) return null;
        if (keys.size() == 1) return keys.get(0).name();
        List<String> names = new ArrayList<String>();
        for (FieldSpec k : keys) names.add(k.name());
        return names;
    }

    /** A field's facts, as FieldFacts states them; a key of a reference is a Ref. */
    static Map<String, Object> field(FieldSpec f, EntitySpec entity, List<FieldSpec> siblings, Model model) {
        Map<String, Object> ref = ref(f, entity, siblings, model);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("kind", ref != null ? "Ref" : FieldFacts.kind(f));
        out.put("label", FieldFacts.label(f));
        out.put("labels", FieldFacts.labels(f));
        out.put("required", FieldFacts.required(f));
        putIfSet(out, "maxLength", FieldFacts.maxLength(f));
        putIfSet(out, "minLength", FieldFacts.minLength(f));
        String min = FieldFacts.min(f);
        if (min != null) out.put("min", JsonNumber.of(min));
        String max = FieldFacts.max(f);
        if (max != null) out.put("max", JsonNumber.of(max));
        out.put("readOnly", FieldFacts.readOnly(f));
        out.put("html", FieldFacts.html(f));
        EnumSpec type = f.isBaseType() ? null : model.enumeration(simple(f.coreItemType()));
        if (type != null) {
            // The enumeration's name: the runtime says each value's label by it (Enum.CONSTANT).
            out.put("enum", type.getName());
            // And its domain: an enumeration of another domain is named by that domain's label bundle.
            putIfSet(out, "enumDomain", model.enumerationDomain(type.getName()));
            List<Object> options = new ArrayList<Object>();
            for (EnumSpec.Constant c : type.constants()) {
                Map<String, Object> o = new LinkedHashMap<String, Object>();
                o.put("value", FieldFacts.enumWireValue(type, c));
                o.put("name", c.name());
                o.put("label", FieldFacts.enumLabel(type, c));
                o.put("labels", FieldFacts.enumLabels(type, c));
                options.add(o);
            }
            out.put("options", options);
        }
        if (ref != null) out.put("ref", ref);
        return out;
    }

    /**
     * What a field holding a reference's key points at: the entity, its domain, its key and - where that
     * entity's controller serves it - its POST /search. A field of the DTO that shows the same relation's
     * other value (productName @ref(product.name)) is its display. Null for a field that holds no key: a
     * value, a list, or a value shown through a relation.
     */
    static Map<String, Object> ref(FieldSpec f, EntitySpec entity, List<FieldSpec> siblings, Model model) {
        String path = FieldFacts.reference(f);
        FieldSpec relation;
        String keyName;
        if (path != null) {
            int dot = path.indexOf('.');
            if (dot <= 0) return null;
            relation = fieldOf(entity, path.substring(0, dot));
            keyName = path.substring(dot + 1).trim();
        } else if (!f.isList() && !f.isBaseType() && f.isEntityRef()) {
            // A nested row of another entity (product: Product @dtype(ProductDTO)): the field is the relation.
            relation = f;
            keyName = "";
        } else {
            return null;
        }
        if (relation == null || relation.isList()) return null;
        EntitySpec target = model.entity(simple(relation.coreItemType()));
        if (target == null || target.pkFields().isEmpty()) return null;
        List<String> keys = new ArrayList<String>();
        for (FieldSpec k : target.pkFields()) keys.add(k.name());
        if (!keyName.isEmpty() && !keys.contains(keyName)) return null;

        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("entity", target.getName());
        out.put("domain", target.domainName());
        out.put("key", keys.size() == 1 ? keys.get(0) : keys);
        if (model.searches(target)) {
            out.put(
                    "search",
                    String.format("%s/%s/%s/search", target.apiPath(), target.domainName(), target.varName()));
        }
        Map<String, Object> display = display(f, relation.name(), keys, siblings);
        if (display != null) out.put("display", display);
        return out;
    }

    /** The sibling that shows another value of the same relation, as {field, of}; null when the DTO has none. */
    private static Map<String, Object> display(
            FieldSpec f, String relation, List<String> keys, List<FieldSpec> siblings) {
        for (FieldSpec g : siblings) {
            if (g == f) continue;
            String ref = g.dslValue("ref");
            if (ref == null || !ref.startsWith(relation + ".")) continue;
            String of = ref.substring(relation.length() + 1).trim();
            if (of.isEmpty() || of.indexOf('.') >= 0 || keys.contains(of)) continue;
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("field", g.name());
            out.put("of", of);
            return out;
        }
        return null;
    }

    private static FieldSpec fieldOf(EntitySpec e, String name) {
        for (FieldSpec f : e.allFields()) {
            if (f.name().equals(name)) return f;
        }
        return null;
    }

    /** A type name without its domain or package: common:Office -> Office, a.b.Office -> Office. */
    static String simple(String type) {
        if (type == null) return null;
        String t = type.substring(type.lastIndexOf(':') + 1).trim();
        return t.substring(t.lastIndexOf('.') + 1);
    }

    private static void putIfSet(Map<String, Object> out, String key, Object value) {
        if (value != null) out.put(key, value);
    }

    // ---- JSON ----------------------------------------------------------------------

    /** A number as the DSL writes it (@min(0.01)): written as a JSON number. */
    static final class JsonNumber {
        final String text;

        JsonNumber(String text) {
            this.text = text;
        }

        /** The number as JSON writes it: 007 is 7, which a JSON reader would refuse as written. */
        static JsonNumber of(String declared) {
            return new JsonNumber(new java.math.BigDecimal(declared).toPlainString());
        }
    }

    private static void write(StringBuilder sb, Object value, String indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append(string((String) value));
        } else if (value instanceof Boolean || value instanceof Integer) {
            sb.append(value);
        } else if (value instanceof JsonNumber) {
            sb.append(((JsonNumber) value).text);
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            String inner = indent + "  ";
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> en : map.entrySet()) {
                sb.append(inner).append(string(String.valueOf(en.getKey()))).append(": ");
                write(sb, en.getValue(), inner);
                sb.append(++i < map.size() ? ",\n" : "\n");
            }
            sb.append(indent).append('}');
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            String inner = indent + "  ";
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append(inner);
                write(sb, list.get(i), inner);
                sb.append(i + 1 < list.size() ? ",\n" : "\n");
            }
            sb.append(indent).append(']');
        } else {
            sb.append(string(String.valueOf(value)));
        }
    }

    /** A JSON string literal, ASCII only: a quote, a backslash, a control and every non-ASCII character escaped. */
    static String string(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c < 0x20 || c > 0x7e) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.append('"').toString();
    }
}
