package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.core.LabelTranslations;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.enums.EnumConstant;
import io.elasticore.base.model.enums.EnumModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The label of an enumeration's constants in other languages, as columns:
 * beside the label column (label, name ...), a column named
 * {@code <label column>_<language>} - label_en, label_ja, label_pt_BR - holds
 * the constant's label in that language. The language is read as an
 * {@code @label(xx=...)} key is (LabelTranslations): pt_BR is pt-BR.
 *
 * Which column is the label is each output's own choice and is passed in;
 * {@link #labelColumn} is the one the TypeScript client and the label bundle
 * show. A column whose suffix is not a language (label_long) is not a
 * translation, and the build says so once.
 */
public final class EnumLabelColumns {

    private EnumLabelColumns() {}

    /** The names a label column goes by when no field is marked @label, in this order. */
    private static final String[] LABEL_NAMES = {"label", "name", "desc", "description", "title"};

    /**
     * The column a screen shows for a constant: the field marked @label, else
     * the first named label, name, desc, description or title that is not the
     * field the wire carries. Null when the enumeration has none.
     */
    public static Field labelColumn(List<Field> fields, String wireField) {
        if (fields == null) return null;
        for (Field f : fields) {
            if (f.hasAnnotation("label")) return f;
        }
        for (String name : LABEL_NAMES) {
            for (Field f : fields) {
                if (!f.getName().equals(wireField) && f.getName().equalsIgnoreCase(name)) return f;
            }
        }
        return null;
    }

    /**
     * The columns that translate the label column: language -> index of the
     * column in fields, in language name order; empty when there are none.
     * The first column of a language is kept; the fields named in except
     * (the code the database and the wire carry) are never translations.
     */
    public static Map<String, Integer> columns(List<Field> fields, Field label, String... except) {
        Map<String, Integer> out = new TreeMap<String, Integer>();
        for (int i = 0; fields != null && label != null && i < fields.size(); i++) {
            String suffix = suffix(fields.get(i), label, except);
            if (suffix == null || !LabelTranslations.isLanguage(suffix)) continue;
            String language = LabelTranslations.normalized(suffix);
            if (!out.containsKey(language)) out.put(language, i);
        }
        return out;
    }

    /** The columns named {@code <label column>_<suffix>} whose suffix is not a language tag. */
    public static List<String> notLanguages(List<Field> fields, Field label, String... except) {
        List<String> out = new ArrayList<String>();
        for (int i = 0; fields != null && label != null && i < fields.size(); i++) {
            String suffix = suffix(fields.get(i), label, except);
            if (suffix != null && !LabelTranslations.isLanguage(suffix))
                out.add(fields.get(i).getName());
        }
        return out;
    }

    /**
     * Each constant's label in the other languages: constant name -> language
     * -> text, in the order the constants are declared; a constant whose line
     * gives no value for a language has none in it. Empty when the
     * enumeration has no translation column. Warns once for a column whose
     * suffix is not a language.
     */
    public static Map<String, Map<String, String>> translations(EnumModel en, Field label) {
        if (en == null || label == null || en.getFieldItems() == null) return Collections.emptyMap();
        List<Field> fields = en.getFieldItems().getItemList();
        String[] except = codeFields(en);
        for (String column : notLanguages(fields, label, except)) {
            SourceCode.dslWarnOnce(String.format(
                    "[%s] enum %s: column '%s' is not a translation of '%s' - '%s' is not a language tag;"
                            + " a label in another language is a column named %s_<language> (%s_en, %s_pt_BR)",
                    en.getIdentity().getDomainId(),
                    en.getIdentity().getName(),
                    column,
                    label.getName(),
                    column.substring(label.getName().length() + 1),
                    label.getName(),
                    label.getName(),
                    label.getName()));
        }
        Map<String, Integer> columns = columns(fields, label, except);
        if (columns.isEmpty() || en.getEnumConstantItems() == null) return Collections.emptyMap();
        Map<String, Map<String, String>> out = new LinkedHashMap<String, Map<String, String>>();
        for (EnumConstant c : en.getEnumConstantItems().getItemList()) {
            Map<String, String> texts = new TreeMap<String, String>();
            List<EnumConstant.ConstructParam> params = c.getConstructParamList();
            for (Map.Entry<String, Integer> column : columns.entrySet()) {
                int at = column.getValue();
                String text = params != null && at < params.size() ? text(params.get(at)) : null;
                if (text != null) texts.put(column.getKey(), text);
            }
            out.put(c.getIdentity().getName(), Collections.unmodifiableMap(texts));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * Each constant's label column, as the line writes it: constant name ->
     * text, in declaration order. A line that leaves the column out has the
     * constant's name there, as the enum itself does.
     */
    public static Map<String, String> labels(EnumModel en, Field label) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (en == null || label == null || en.getEnumConstantItems() == null || en.getFieldItems() == null) return out;
        int at = en.getFieldItems().getItemList().indexOf(label);
        for (EnumConstant c : en.getEnumConstantItems().getItemList()) {
            List<EnumConstant.ConstructParam> params = c.getConstructParamList();
            String text = at >= 0 && params != null && at < params.size()
                    ? unquoted(String.valueOf(params.get(at))).trim()
                    : c.getIdentity().getName();
            out.put(c.getIdentity().getName(), text.isEmpty() ? c.getIdentity().getName() : text);
        }
        return out;
    }

    /** The text a constant line gives for one column, its quotes taken off; null when the line gives none. */
    public static String text(EnumConstant.ConstructParam p) {
        if (p == null || p.isMissing()) return null;
        String text = unquoted(String.valueOf(p)).trim();
        return text.isEmpty() ? null : text;
    }

    /** The column the enumeration's @db and @json name: a code, never a label. */
    private static String[] codeFields(EnumModel en) {
        if (en.getMetaInfo() == null) return new String[0];
        return new String[] {
            en.getMetaInfo().getMetaAnnotationValue("db"), en.getMetaInfo().getMetaAnnotationValue("json")
        };
    }

    /** What follows "<label>_" in the field's name, or null when the field is not named so. */
    private static String suffix(Field f, Field label, String... except) {
        String name = f.getName();
        if (name == null || name.equals(label.getName())) return null;
        for (String e : except) {
            if (name.equals(e)) return null;
        }
        String prefix = label.getName().toLowerCase(Locale.ROOT) + "_";
        if (name.length() <= prefix.length() || !name.toLowerCase(Locale.ROOT).startsWith(prefix)) return null;
        return name.substring(prefix.length());
    }

    private static String unquoted(String v) {
        if (v != null && v.length() > 1 && v.charAt(0) == '"' && v.endsWith("\"")) {
            return v.substring(1, v.length() - 1);
        }
        return v == null ? "" : v;
    }
}
