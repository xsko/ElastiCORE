package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;

/**
 * Read-only view of a DSL field for rule authors.
 * Wraps the existing model {@link Field} and narrows the query surface
 * so rules do not depend on model internals.
 */
public final class FieldSpec {

    private final Field field;
    private final EntitySpec owner;

    /** True for fields inherited from the parent entity (@extend). */
    private final boolean parentField;

    FieldSpec(Field field, EntitySpec owner) {
        this(field, owner, false);
    }

    FieldSpec(Field field, EntitySpec owner, boolean parentField) {
        this.field = field;
        this.owner = owner;
        this.parentField = parentField;
    }

    public String name() {
        return field.getName();
    }

    /** Java type name used in the field declaration (e.g. Long, String, Article). */
    public String typeName() {
        return field.getTypeInfo().getDefaultTypeName();
    }

    /** True for primitives, wrappers and String: no mapper conversion needed. */
    public boolean isBaseType() {
        return field.getTypeInfo().isBaseType();
    }

    /**
     * True when the ITEM of a collection is a value - List&lt;String&gt; yes,
     * List&lt;Article&gt; no.
     *
     * Distinct from {@link #isBaseType()}, which asks about the field: a
     * List&lt;String&gt; is not a base type but its items are, and a rule
     * deciding whether a collection can be searched or mapped is asking
     * about the items. Answering it with isBaseType() is wrong in exactly
     * the cases that matter.
     */
    public boolean isBaseItemType() {
        return field.getTypeInfo().getCoreItemBaseFieldType() != null;
    }

    /**
     * The type as the field would initialise it - the primitive form where
     * one exists, so a @primitive Long declares itself long.
     */
    public String initTypeName() {
        return field.getTypeInfo().getInitTypeInfo();
    }

    /** True when the declared type carries a type parameter, e.g. List&lt;X&gt;. */
    public boolean isGenericType() {
        return field.getTypeInfo().isGenericType();
    }

    /** Name of the type underlying a generic declaration, or null. */
    public String baseFieldTypeName() {
        io.elasticore.base.model.entity.BaseFieldType type = field.getTypeInfo().getBaseFieldType();
        return type == null ? null : type.getName();
    }

    /** True when the field has the given DSL annotation (e.g. hasDsl("id")). */
    public boolean hasDsl(String name) {
        return field.hasAnnotation(name);
    }

    /** Value of a DSL annotation, or null. */
    public String dslValue(String name) {
        return field.getAnnotationValue(name);
    }

    /**
     * Aliases of the label annotation, in the order the comment block reads
     * them (EntityAnnotation.COMMENT).
     */
    private static final String[] LABEL = {"label", "comment", "desc", "description"};

    /**
     * The same four aliases in the order the schema attribute reads them
     * (EntityAnnotation.DESCRIPTION). The order matters: a "-- label | text"
     * comment populates more than one of them, and whichever comes first wins.
     */
    private static final String[] DESCRIPTION = {"description", "desc", "label", "comment"};

    /** What the schema attribute calls the field, or null. */
    public String dslDescription() {
        return dslValueAny(DESCRIPTION);
    }

    /**
     * What the field calls itself in prose, or null if it says nothing.
     * Every documentation artifact asks this, so the aliases live in one place.
     */
    public String label() {
        String desc = description();
        return desc != null ? desc : dslLabel();
    }

    /** Aliases of the search annotation: @searchable / @search / @s / @srch / @filter. */
    private static final String[] SEARCH = {"searchable", "search", "s", "srch", "filter"};

    /** The declared search condition, from any alias, or null. */
    public String searchValue() {
        return dslValueAny(SEARCH);
    }

    /** True when any search alias is declared, with or without a value. */
    public boolean hasSearch() {
        for (String alias : SEARCH) {
            if (field.hasAnnotation(alias)) return true;
        }
        return false;
    }

    private boolean declaredInDto;

    /**
     * True when the DTO's own body declares this field, as opposed to one
     * carried in by the entity an @searchable DTO references. A declared field
     * stays in the search DTO even without a search condition.
     */
    public FieldSpec declaredInDto(boolean declared) {
        this.declaredInDto = declared;
        return this;
    }

    public boolean declaredInDto() {
        return declaredInDto;
    }

    /** Aliases of the DTO type override: @dtotype / @dtype / @dto. */
    private static final String[] DTO_TYPE = {"dtotype", "dtype", "dto"};

    /**
     * The type this field takes when it appears in a DTO, or null when it
     * keeps the type it declares. A relation names its counterpart's DTO here.
     */
    public String dtoTypeName() {
        return dslValueAny(DTO_TYPE);
    }

    /** The declared label alone, without the spec-level description. */
    public String dslLabel() {
        return dslValueAny(LABEL);
    }

    public String dslValueAny(String... names) {
        return field.getAnnotationValue(names);
    }

    /**
     * The regular expression of @pattern, or null.
     *
     * Two forms are read. The named one, @pattern(regexp="...", message="..."),
     * comes first. Otherwise the positional one, @pattern('regex', 'message')
     * or @pattern('regex'): the value as written, split at the first comma
     * outside quotes and brackets - so a quantifier such as {10,13} stays whole -
     * with the quotes taken off each part.
     *
     * The text is the content of a string literal, as written: a backslash is
     * written doubled (\\d), or avoided with a class such as [0-9].
     */
    public String patternRegexp() {
        String named = unquoted(dslValueAny("pattern.regexp"));
        if (named != null) return named.isEmpty() ? null : named;
        return patternArguments()[0];
    }

    /**
     * The message of @pattern, or null when none is given. Double quotes in it
     * become single ones, as in the message of a bound, so it can sit in a
     * string literal.
     */
    public String patternMessage() {
        String message = unquoted(dslValueAny("pattern.message"));
        if (message == null) message = patternArguments()[1];
        if (message == null || message.trim().isEmpty()) return null;
        return message.replace("\"", "'");
    }

    /** {regex, message}, each null when absent, from the positional form of @pattern. */
    private String[] patternArguments() {
        io.elasticore.base.model.core.Annotation a = field.getAnnotation("pattern");
        // The raw value: getValue() takes off an outer pair of quotes, which
        // in 'regex', 'message' are the first and the last of two strings.
        String raw = a == null || a.getProperties() != null ? null : a.value;
        if (raw == null || raw.trim().isEmpty()) return new String[]{null, null};
        String t = raw.trim();
        int comma = -1;
        char quote = 0;
        int depth = 0;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == '\\') {
                i++;
                continue;
            }
            if (quote != 0) {
                if (ch == quote) quote = 0;
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
            } else if (ch == '[' || ch == '{' || ch == '(') {
                depth++;
            } else if ((ch == ']' || ch == '}' || ch == ')') && depth > 0) {
                depth--;
            } else if (ch == ',' && depth == 0) {
                comma = i;
                break;
            }
        }
        String regex = unquoted(comma < 0 ? t : t.substring(0, comma).trim());
        String message = comma < 0 ? null : unquoted(t.substring(comma + 1).trim());
        return new String[]{regex == null || regex.isEmpty() ? null : regex, message};
    }

    /** 'x' or "x" -> x; anything else as it is. */
    private static String unquoted(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() >= 2 && (t.charAt(0) == '\'' || t.charAt(0) == '"')
                && t.charAt(t.length() - 1) == t.charAt(0)) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    /** True when any of the alias names is present. */
    public boolean hasDslAny(String... names) {
        for (String n : names) {
            if (field.hasAnnotation(n)) return true;
        }
        return false;
    }

    /** Free-text description of the field, or null. */
    public String description() {
        return field.getDescription();
    }

    /** Declared generation type (field.getGenerationType()), or null. */
    public String generationType() {
        return field.getGenerationType();
    }

    /** DB column name: @column value when present, otherwise camel_to_snake of the field name. */
    public String dbColumnName() {
        return field.getDbColumnName();
    }

    /** Min length from @length(min=..); negative when undefined. */
    public int minLength() {
        return field.getMinLength();
    }

    /** Max length as the validation layer sees it; negative when undefined. */
    public int validationMaxLength() {
        return field.getMaxLength();
    }

    /** Max length from @length or string(n) type parameter; 0 when undefined. */
    public int maxLength() {
        int max = field.getMaxLength();
        if (max > 0) return max;
        int len = field.getLength();
        return len > 0 ? len : 0;
    }

    /** True when the field type refers to another entity (single or list). */
    public boolean isEntityRef() {
        if (field.getTypeInfo().isBaseType()) return false;
        String type = field.getTypeInfo().getCoreItemType();
        if (owner.isKnownEntity(type)) return true;
        // A relation can target another domain's entity; 2.x resolves it
        // through the global context.
        try {
            io.elasticore.base.ECoreModelContext ctx =
                    io.elasticore.base.model.core.BaseECoreModelContext.getContext();
            return ctx != null && ctx.findModelComponent(type,
                    io.elasticore.base.model.entity.Entity.class) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isList() {
        return field.getTypeInfo().isList();
    }

    public EntitySpec entity() {
        return owner;
    }

    public boolean isParentField() {
        return parentField;
    }

    /** Field name with the first letter upper-cased, for accessor names. */
    public String capitalName() {
        String n = field.getName();
        return n.isEmpty() ? n : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** Element type of a relation/list field (e.g. List of Comment gives Comment). */
    public String coreItemType() {
        return field.getTypeInfo().getCoreItemType();
    }

    /** True when the DSL marks the field required (trailing '!' or @notnull). */
    public boolean required() {
        return hasDsl("notnull") || hasDsl("required") || hasDsl("nn");
    }

    /**
     * What the @Schema attribute calls required. Wider than {@link #required()}
     * because 2.x asks EntityAnnotation.NOT_NULL there, and that list names
     * @notblank as well.
     */
    public boolean schemaRequired() {
        return required() || hasDsl("notblank");
    }

    public boolean isDateTime() {
        return "java.time.LocalDateTime".equals(typeName());
    }

    /** A date without a time part, which formats differently. */
    public boolean isDate() {
        return "java.time.LocalDate".equals(typeName());
    }

    /**
     * Type name in protobuf terms: the proto scalar for base types
     * (string, int32, bool...) and the declared name for message/enum types.
     * A list becomes "repeated" its own item type, mapped the same way.
     * The same field model therefore serves Java and non-Java artifacts -
     * only the type mapping differs, which is what language neutrality
     * requires of the emission layer.
     */
    public String protoTypeName() {
        if (isList()) {
            return String.format("repeated %s", protoScalarOrName(coreItemType()));
        }
        io.elasticore.base.model.entity.TypeInfo t = field.getTypeInfo();
        if (t.isBaseType()) {
            String p = t.getBaseFieldType().getProtoType();
            if (p != null) return p;
        }
        return t.getInitTypeInfo();
    }

    /**
     * The proto scalar for a base type name (String, Long, ...), or the name
     * itself for a message/enum item type - the lookup coreItemType() alone
     * cannot make, since it hands back a bare name with no TypeInfo attached.
     */
    private static String protoScalarOrName(String typeName) {
        String p = io.elasticore.base.model.entity.BaseFieldType.findByName(typeName).getProtoType();
        return p != null ? p : typeName;
    }

    public boolean isStringType() {
        return field.getTypeInfo().isStringType();
    }

    public boolean isBigDecimal() {
        return field.getTypeInfo().isBigDecimal();
    }

    /** The runtime's Money: an amount, stored as a decimal. */
    public boolean isMoney() {
        return field.getTypeInfo().isMoney();
    }

    /** The runtime's Quantity: a count, stored as an integer. */
    public boolean isQuantity() {
        return field.getTypeInfo().isQuantity();
    }

    public boolean hasPrecisionScale() {
        return field.getTypeInfo().hasPrecisionScale();
    }

    public int precision() {
        return field.getTypeInfo().getPrecision();
    }

    public int scale() {
        return field.getTypeInfo().getScale();
    }

    /** Base type name from the type system, e.g. LONG, DOUBLE, DATETIME. */
    public String baseTypeName() {
        return String.valueOf(field.getTypeInfo().getBaseFieldType());
    }

    /** Raw declared length (string(36) gives 36); 0 when undefined. */
    public int rawLength() {
        int len = field.getLength();
        return len > 0 ? len : 0;
    }

    /**
     * Property of a parameterized DSL annotation,
     * e.g. dslProperty("onetomany", "mappedBy") for @OneToMany(mappedBy="parent").
     */
    public String dslProperty(String annotation, String key) {
        io.elasticore.base.model.core.Annotation a = field.getAnnotation(annotation);
        if (a == null) return null;
        java.util.Properties props = a.getProperties();
        if (props == null) return null;
        String v = props.getProperty(key);
        if (v == null) v = props.getProperty(key.toLowerCase());
        return v;
    }

    /**
     * Native annotation lines carried by the DSL: keys prefixed "jpa:"
     * (e.g. @jpa:Version) and "entity:" (env.yml annotation mappings such
     * as @env:stringarray resolved by the loader). Rendered exactly as the
     * 2.x setNativeAnnotation does, jpa entries first.
     */
    public java.util.List<String> nativeAnnotationLines() {
        java.util.List<String> out = new java.util.ArrayList<String>();
        java.util.Map<String, io.elasticore.base.model.core.Annotation> map = field.getAnnotationMap();
        if (map == null) return out;
        for (java.util.Map.Entry<String, io.elasticore.base.model.core.Annotation> en : map.entrySet()) {
            if (en.getKey().contains("jpa:")) out.add(String.valueOf(en.getValue()));
        }
        for (java.util.Map.Entry<String, io.elasticore.base.model.core.Annotation> en : map.entrySet()) {
            if (en.getKey().contains("entity:")) out.add(String.valueOf(en.getValue()));
        }
        return out;
    }

    /**
     * Annotations the DSL passes through to the DTO verbatim, e.g.
     * "dto:@Masking(...)". JPA-targeted ones are the entity's business.
     */
    public java.util.List<String> dtoNativeAnnotationLines() {
        java.util.List<String> out = new java.util.ArrayList<String>();
        java.util.Map<String, io.elasticore.base.model.core.Annotation> map = field.getAnnotationMap();
        if (map == null) return out;
        for (java.util.Map.Entry<String, io.elasticore.base.model.core.Annotation> en : map.entrySet()) {
            if (!en.getKey().contains("dto:")) continue;
            if (en.getKey().contains("jpa:")) continue;
            out.add(String.valueOf(en.getValue()));
        }
        return out;
    }

    /** The name of the model that declared this field, or null. */
    public String declaringName() {
        try {
            io.elasticore.base.model.MetaInfo meta = field.getParentMetaInfo();
            return meta == null ? null : meta.getMetaAnnotationValue("name");
        } catch (Throwable t) {
            return null;
        }
    }

    /** Whether the declaring model is @abstract. */
    public boolean declaringAbstract() {
        try {
            io.elasticore.base.model.MetaInfo meta = field.getParentMetaInfo();
            return meta != null && meta.hasMetaAnnotation("abstract");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Whether the persistence layer, not the application, writes this field:
     * a JPA version, or one of Spring Data's four auditing annotations.
     *
     * Read from the DSL's native annotations ("@jpa:Version",
     * "@jpa:org.springframework.data.annotation.CreatedBy", ...), matched on
     * the simple name so either spelling is recognized.
     */
    public boolean systemManaged() {
        java.util.Map<String, io.elasticore.base.model.core.Annotation> map = field.getAnnotationMap();
        if (map == null) return false;
        for (String key : map.keySet()) {
            String k = key.toLowerCase();
            if (!k.contains("jpa:")) continue;
            String simple = k.substring(k.lastIndexOf(k.contains(".") ? '.' : ':') + 1);
            if (simple.equals("version") || simple.equals("createdby") || simple.equals("createddate")
                    || simple.equals("lastmodifiedby") || simple.equals("lastmodifieddate")) {
                return true;
            }
        }
        return false;
    }

    /** Whether this field is the JPA optimistic-lock version. */
    public boolean isVersion() {
        java.util.Map<String, io.elasticore.base.model.core.Annotation> map = field.getAnnotationMap();
        if (map == null) return false;
        for (String key : map.keySet()) {
            String k = key.toLowerCase();
            if (k.contains("jpa:") && (k.endsWith(":version") || k.endsWith(".version"))) return true;
        }
        return false;
    }

    /** Escape hatch to the underlying model object. */
    public Field raw() {
        return field;
    }
}
