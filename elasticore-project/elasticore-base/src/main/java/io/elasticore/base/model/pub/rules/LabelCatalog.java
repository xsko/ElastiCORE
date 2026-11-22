package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.MetaInfo;
import io.elasticore.base.model.ModelComponentItems;
import io.elasticore.base.model.core.Annotation;
import io.elasticore.base.model.core.LabelTranslations;
import io.elasticore.base.model.entity.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The labels of a domain's model as a resource bundle, written when the DSL
 * gives a label in another language (@label(en="Unit cost") -- ...):
 * {@code i18n/<domain>-labels.properties} holds every label as the "--" text
 * says it, {@code <domain>-labels_<language>.properties} the ones written in
 * that language. Keys: {@code Item} for a type's meta label, {@code Item.unitCost}
 * for a field's. A label with no translation falls back to the default file,
 * as a ResourceBundle does. An enumeration with label_en, label_ja columns
 * (EnumLabelColumns) adds {@code Status.ACTIVE} for each constant - by the
 * constant's name - and is reason enough to write the bundle.
 */
public final class LabelCatalog {

    private LabelCatalog() {}

    /** A type of the model: its name, its meta line and the fields it declares. */
    private static final class Type {
        final String name;
        final MetaInfo meta;
        final List<Field> fields;
        /** An enumeration's constants: name -> the label column; empty without label_en columns. */
        final Map<String, String> constants;
        /** An enumeration's constants: name -> language -> label, from its label_en columns. */
        final Map<String, Map<String, String>> translations;
        /** An enumeration's constants: name -> the label column, whether or not it has label_en columns. */
        final Map<String, String> own;

        Type(String name, MetaInfo meta, List<Field> fields) {
            this(
                    name,
                    meta,
                    fields,
                    Collections.<String, Map<String, String>>emptyMap(),
                    Collections.<String, String>emptyMap(),
                    Collections.<String, String>emptyMap());
        }

        Type(
                String name,
                MetaInfo meta,
                List<Field> fields,
                Map<String, Map<String, String>> translations,
                Map<String, String> constants,
                Map<String, String> own) {
            this.name = name;
            this.meta = meta;
            this.fields = fields;
            this.translations = translations;
            this.constants = constants;
            this.own = own;
        }

        Annotation label() {
            Map<String, Annotation> map = meta == null ? null : meta.getMetaAnnotationMap();
            return map == null ? null : map.get("label");
        }
    }

    /** Every language a label of the model is written in, in name order; empty when none. */
    public static Set<String> languages(ECoreModel model) {
        Set<String> out = new TreeSet<String>();
        for (Type t : types(model)) {
            out.addAll(LabelTranslations.of(t.label()).keySet());
            for (Field f : t.fields)
                out.addAll(LabelTranslations.of(f.getAnnotation("label")).keySet());
            for (Map<String, String> c : t.translations.values()) out.addAll(c.keySet());
        }
        return out;
    }

    /**
     * Every language the bundle has a file for: the languages labels are
     * written in, and the language of the "--" labels (config: labelLanguage)
     * when it is set - a file of its own, so an answer in that language finds
     * them without falling back to the default file, which English never reads.
     */
    public static Set<String> languages(ECoreModel model, String labelLanguage) {
        Set<String> out = languages(model);
        if (labelLanguage != null) out.add(labelLanguage);
        return out;
    }

    /** Whether an enumeration's constants are in the bundle: it has label_en columns. */
    public static boolean hasConstants(ECoreModel model) {
        for (Type t : types(model)) {
            if (!t.constants.isEmpty()) return true;
        }
        return false;
    }

    /** Whether the file of a language holds constants: label_en columns, or the label columns in the labelLanguage file. */
    public static boolean hasConstants(ECoreModel model, String language, String labelLanguage) {
        if (hasConstants(model)) return true;
        if (language == null || !language.equals(labelLanguage)) return false;
        for (Type t : types(model)) {
            if (!t.own.isEmpty()) return true;
        }
        return false;
    }

    /**
     * key -> label: the "--" text when language is null, else the labels
     * written in that language. In the order the model declares them.
     */
    public static Map<String, String> labels(ECoreModel model, String language) {
        return labels(model, language, null);
    }

    /**
     * key -> label, as {@link #labels(ECoreModel, String)}; the file of the
     * labelLanguage holds every label: the one written in that language, else
     * the "--" text - and every constant's label column where it has no
     * column of that language.
     */
    public static Map<String, String> labels(ECoreModel model, String language, String labelLanguage) {
        boolean own = language != null && language.equals(labelLanguage);
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Type t : types(model)) {
            put(out, t.name, t.label(), language, own);
            for (Field f : t.fields) put(out, t.name + "." + f.getName(), f.getAnnotation("label"), language, own);
            // Status.ACTIVE: a constant by its name, the label column or its label_<language> column.
            for (Map.Entry<String, String> c : (own ? t.own : t.constants).entrySet()) {
                Map<String, String> texts = t.translations.get(c.getKey());
                String text = language == null ? c.getValue() : texts == null ? null : texts.get(language);
                if (own && (text == null || text.trim().isEmpty())) text = c.getValue();
                if (text != null && !text.trim().isEmpty()) out.put(t.name + "." + c.getKey(), text.trim());
            }
        }
        return out;
    }

    /** The file's name without the extension: shop-labels, shop-labels_zh_TW. */
    public static String fileName(String domainName, String language) {
        return domainName + "-labels" + (language == null ? "" : "_" + language.replace('-', '_'));
    }

    /** The properties file, every non-ASCII character escaped (as the message bundle). */
    public static String properties(String domainName, String language, Map<String, String> labels) {
        return properties(domainName, language, labels, false);
    }

    /**
     * The properties file; constants: the model's enumerations have label_en
     * columns, whose constants the file holds too - the header says so only then.
     */
    public static String properties(String domainName, String language, Map<String, String> labels, boolean constants) {
        return properties(domainName, language, labels, constants, null);
    }

    /** The properties file; the labelLanguage file says that it holds the "--" text too. */
    public static String properties(
            String domainName, String language, Map<String, String> labels, boolean constants, String labelLanguage) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated by ElastiCORE - the labels of the ")
                .append(domainName)
                .append(" model");
        if (language == null) {
            sb.append(", as the DSL's \"--\" text says them.\n");
        } else if (language.equals(labelLanguage)) {
            sb.append(" in ")
                    .append(language)
                    .append(", the language of the \"--\" text (config: labelLanguage): from @label(")
                    .append(language)
                    .append(
                            constants
                                    ? "=...) and the _" + language.replace('-', '_')
                                            + " columns, else the \"--\" text and the label columns.\n"
                                    : "=...), else the \"--\" text.\n");
        } else {
            sb.append(" in ")
                    .append(language)
                    .append(", from @label(")
                    .append(language)
                    .append(
                            constants
                                    ? "=...) and the enumerations' _" + language.replace('-', '_') + " columns.\n"
                                    : "=...).\n");
        }
        sb.append("# Regenerated with the model: do not edit; change the DSL.\n")
                .append(
                        constants
                                ? "# Keys: Type for a type's label, Type.field for a field's, Enum.CONSTANT for a constant's.\n\n"
                                : "# Keys: Type for a type's label, Type.field for a field's.\n\n");
        for (Map.Entry<String, String> en : labels.entrySet()) {
            sb.append(ErrorCatalog.escape(en.getKey(), true))
                    .append('=')
                    .append(ErrorCatalog.escape(en.getValue(), false))
                    .append('\n');
        }
        return sb.toString();
    }

    private static void put(Map<String, String> out, String key, Annotation label, String language, boolean own) {
        if (label == null) return;
        String text = language == null
                ? label.getValue()
                : LabelTranslations.of(label).get(language);
        if (own && (text == null || text.trim().isEmpty())) text = label.getValue();
        if (text != null && !text.trim().isEmpty()) out.put(key, text.trim());
    }

    /** The entities, the DTOs and the enumerations, in that order. */
    private static List<Type> types(ECoreModel model) {
        List<Type> out = new ArrayList<Type>();
        if (model.getEntityModels() != null) {
            ModelComponentItems<io.elasticore.base.model.entity.Entity> items =
                    model.getEntityModels().getItems();
            for (int i = 0; i < items.size(); i++) {
                io.elasticore.base.model.entity.Entity m = items.get(i);
                out.add(new Type(m.getIdentity().getName(), m.getMetaInfo(), fields(m.getItems())));
            }
        }
        if (model.getDataTransferModels() != null) {
            ModelComponentItems<io.elasticore.base.model.dto.DataTransfer> items =
                    model.getDataTransferModels().getItems();
            for (int i = 0; i < items.size(); i++) {
                io.elasticore.base.model.dto.DataTransfer m = items.get(i);
                out.add(new Type(m.getIdentity().getName(), m.getMetaInfo(), fields(m.getItems())));
            }
        }
        if (model.getEnumModels() != null) {
            ModelComponentItems<io.elasticore.base.model.enums.EnumModel> items =
                    model.getEnumModels().getItems();
            for (int i = 0; i < items.size(); i++) {
                io.elasticore.base.model.enums.EnumModel m = items.get(i);
                // An enumeration's columns are not labels a screen shows; its own label is, and
                // its constants' when label_<language> columns translate them.
                Field label = m.getFieldItems() == null
                        ? null
                        : EnumLabelColumns.labelColumn(
                                m.getFieldItems().getItemList(),
                                m.getMetaInfo() == null ? null : m.getMetaInfo().getMetaAnnotationValue("json"));
                Map<String, Map<String, String>> translations = EnumLabelColumns.translations(m, label);
                Map<String, String> own = EnumLabelColumns.labels(m, label);
                out.add(new Type(
                        m.getIdentity().getName(),
                        m.getMetaInfo(),
                        Collections.<Field>emptyList(),
                        translations,
                        translations.isEmpty() ? Collections.<String, String>emptyMap() : own,
                        own));
            }
        }
        return out;
    }

    private static List<Field> fields(ModelComponentItems<Field> items) {
        List<Field> out = new ArrayList<Field>();
        if (items == null) return out;
        for (int i = 0; i < items.size(); i++) out.add(items.get(i));
        return out;
    }
}
