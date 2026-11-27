package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.core.Annotation;
import io.elasticore.base.model.core.LabelTranslations;
import io.elasticore.base.model.pub.ossie.OssieSemanticModelExporter;
import io.elasticore.base.model.pub.rules.base.HtmlRules;
import io.elasticore.base.model.pub.rules.ddd.DddBehaviorRules;
import io.elasticore.base.model.pub.rules.ddd.DddStateRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the model says about a field, for a screen: its label, its limits,
 * whether a request may write it, what an enumeration's value is called.
 *
 * One function per fact, so that everything that tells a screen about a
 * field - the TypeScript client's XxxFields, XxxMeta and XxxLabel, and an
 * answer over HTTP (issue 373) - says the same thing. The facts are stated
 * as the TypeScript client states them: a field no request writes is
 * read-only, and is asked for nothing else (not required, no lengths, no
 * bounds).
 */
public final class FieldFacts {

    private FieldFacts() {}

    // ---- label ------------------------------------------------------------------

    /** What a screen calls the field: the "--" text (or @label), trimmed, or else the field's name. */
    public static String label(FieldSpec f) {
        String label = f.label();
        return label == null || label.trim().isEmpty() ? f.name() : label.trim();
    }

    /** The label in other languages, @label(en="Unit cost"): language -> text; empty when none. */
    public static Map<String, String> labels(FieldSpec f) {
        return f.labelTranslations();
    }

    /** Markup the server cleans before saving (@html): a screen may render it as HTML. */
    public static boolean html(FieldSpec f) {
        return f.hasDsl(HtmlRules.HTML);
    }

    // ---- what a request may write -----------------------------------------------

    /** What a request may write: not shown-only, not derived, not the persistence layer's. */
    public static boolean writable(FieldSpec f) {
        if (f.hasDslAny("readonly", "noinput", "behavior", "statefield", "snapshot", "compute", "formula"))
            return false;
        if (f.dslValue("ref") != null || f.systemManaged()) return false;
        // A stamp is the lifecycle's to write - where there is one to write it (a split root).
        return !(f.hasDsl(DddStateRules.STAMP) && DddBehaviorRules.splits(f.entity()));
    }

    /** What no request writes: the opposite of {@link #writable}. */
    public static boolean readOnly(FieldSpec f) {
        return !writable(f);
    }

    /** Whether a request must carry the field: a writable field the DSL marks required ("!"). */
    public static boolean required(FieldSpec f) {
        return writable(f) && f.required();
    }

    /** The most characters a writable text may hold, or null: string(30), @length(max=30). */
    public static Integer maxLength(FieldSpec f) {
        if (!writable(f) || !f.isStringType() || f.validationMaxLength() < 0) return null;
        return f.validationMaxLength();
    }

    /** The fewest characters a writable text must hold, or null: @length(min=2). */
    public static Integer minLength(FieldSpec f) {
        if (!writable(f) || !f.isStringType() || f.minLength() <= 0) return null;
        return f.minLength();
    }

    /** The least number a writable field may hold, as written (@min(0.01)), or null. */
    public static String min(FieldSpec f) {
        return writable(f) ? bound(f.dslValueAny("min", "minval", "minvalue")) : null;
    }

    /** The greatest number a writable field may hold, as written (@max(999)), or null. */
    public static String max(FieldSpec f) {
        return writable(f) ? bound(f.dslValueAny("max", "maxval", "maxvalue")) : null;
    }

    /**
     * The number of @min(1) or @min(0.01, 'message'): the text before the first comma; null when
     * none, or not a number - @min(,) too, which has no text there.
     */
    public static String bound(String declared) {
        if (declared == null) return null;
        int comma = declared.indexOf(',');
        String v = (comma < 0 ? declared : declared.substring(0, comma)).trim();
        return v.matches("-?\\d+(\\.\\d+)?") ? v : null;
    }

    // ---- the type's label --------------------------------------------------------

    /** A label and the same label in other languages (language -> text). */
    public static final class Label {
        private final String text;
        private final Map<String, String> translations;

        Label(String text, Map<String, String> translations) {
            this.text = text;
            this.translations = translations;
        }

        /** The label in the model's own language, trimmed; never blank. */
        public String text() {
            return text;
        }

        /** The label in other languages, by language in name order; empty when none is written. */
        public Map<String, String> translations() {
            return translations;
        }
    }

    /**
     * What a screen calls the type a DTO stands for: its own meta label with
     * its translations, or else its entity's; null when neither gives one.
     * The entity is looked up only when the type has no label of its own.
     */
    public static Label entityLabel(EntitySpec e) {
        String own = e.metaValue("label");
        if (!blank(own)) return new Label(own.trim(), e.labelTranslations());
        return entityLabel(modelLabel(e));
    }

    /** The label an entity's meta annotation declares, or null when it is absent or blank. */
    static Label entityLabel(Annotation modelLabel) {
        String text = modelLabel == null ? null : modelLabel.getValue();
        return blank(text) ? null : new Label(text.trim(), LabelTranslations.of(modelLabel));
    }

    /**
     * The meta label of the entity a DTO stands for, or null: a DTO without a
     * label of its own is shown by its entity's.
     */
    private static Annotation modelLabel(EntitySpec e) {
        io.elasticore.base.model.DataModelComponent named;
        try {
            io.elasticore.base.ECoreModelContext ctx = io.elasticore.base.model.core.BaseECoreModelContext.getContext();
            named = ctx == null ? null : ctx.findModelComponent(e.getName());
        } catch (RuntimeException notInTheModel) {
            return null;
        }
        if (!(named instanceof io.elasticore.base.model.entity.Entity)) return null;
        io.elasticore.base.model.MetaInfo meta = ((io.elasticore.base.model.entity.Entity) named).getMetaInfo();
        Map<String, Annotation> map = meta == null ? null : meta.getMetaAnnotationMap();
        return map == null ? null : map.get("label");
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // ---- enumerations ------------------------------------------------------------

    /**
     * What the server sends for a constant: the @json field's value where the
     * DSL named one, and the constant's own name where it did not - which is
     * what Jackson writes for a plain enum.
     */
    public static String enumWireValue(EnumSpec e, EnumSpec.Constant c) {
        FieldSpec wire = e.field(e.jsonFieldName());
        if (wire != null) {
            int i = e.fields().indexOf(wire);
            if (i >= 0 && i < c.values().size()) return unquoted(c.values().get(i));
        }
        return c.name();
    }

    /**
     * What a screen shows for a constant: the field the DSL names @label - or
     * one called label, name, desc or description - trimmed, and the
     * constant's name in words where there is none (MASTER_CARD -> Master card).
     */
    public static String enumLabel(EnumSpec e, EnumSpec.Constant c) {
        FieldSpec label = labelField(e);
        int at = label == null ? -1 : e.fields().indexOf(label);
        String text = at >= 0 && at < c.values().size() ? unquoted(c.values().get(at)) : words(c.name());
        return text == null ? c.name() : text.trim();
    }

    /**
     * A constant's label in other languages, from the enumeration's label_en,
     * label_ja columns: language -> text; empty when it has none.
     */
    public static Map<String, String> enumLabels(EnumSpec e, EnumSpec.Constant c) {
        Map<String, String> texts = e.labelTranslations().get(c.name());
        return texts == null ? Collections.<String, String>emptyMap() : texts;
    }

    private static FieldSpec labelField(EnumSpec e) {
        FieldSpec wire = e.field(e.jsonFieldName());
        List<io.elasticore.base.model.entity.Field> fields = new ArrayList<io.elasticore.base.model.entity.Field>();
        for (FieldSpec f : e.fields()) fields.add(f.raw());
        io.elasticore.base.model.entity.Field label =
                EnumLabelColumns.labelColumn(fields, wire == null ? null : wire.name());
        int at = fields.indexOf(label);
        return at < 0 ? null : e.fields().get(at);
    }

    private static String unquoted(String v) {
        if (v != null && v.length() > 1 && v.charAt(0) == '"' && v.endsWith("\"")) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    /** MASTER_CARD -> Master card. */
    private static String words(String constant) {
        String lower = constant.replace('_', ' ').toLowerCase(Locale.ROOT);
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    // ---- kind --------------------------------------------------------------------

    /**
     * What kind of value the field holds, in the words the Ossie export uses
     * for a value (String, Integer, Decimal, Float, Boolean, Date, Time,
     * DateTime, DateTimeTz, Opaque), and where the model knows more than that:
     * List (any collection), Ref (another entity), Enum (an enumeration of
     * the model), Object (a DTO or value object carried inline), Money (the
     * runtime's amount) and Html (markup the server cleans).
     *
     * Provisional: nothing calls it yet, and the vocabulary - its words and
     * their spelling - is not decided (issue 373). It may change before the
     * first writer reads it.
     */
    public static String kind(FieldSpec f) {
        if (f.isList()) return "List";
        if (!f.isBaseType()) {
            if (f.isEntityRef()) return "Ref";
            if (isEnum(f)) return "Enum";
            return "Object";
        }
        if (f.isMoney()) return "Money";
        if (html(f)) return "Html";
        return OssieSemanticModelExporter.ossieType(f.raw());
    }

    /** Whether the field's type is an enumeration of its domain, or of another the model knows. */
    private static boolean isEnum(FieldSpec f) {
        String type = f.typeName();
        if (f.entity() != null && f.entity().isKnownEnum(type)) return true;
        try {
            io.elasticore.base.ECoreModelContext ctx = io.elasticore.base.model.core.BaseECoreModelContext.getContext();
            return ctx != null && ctx.findModelComponent(type, io.elasticore.base.model.enums.EnumModel.class) != null;
        } catch (RuntimeException notInTheModel) {
            return false;
        }
    }
}
