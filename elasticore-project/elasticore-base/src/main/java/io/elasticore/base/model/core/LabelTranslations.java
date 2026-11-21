package io.elasticore.base.model.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * A label in other languages: {@code @label(en="Unit cost", de=Einzelpreis)}.
 *
 * The "--" text stays the label in the domain's own language; each
 * {@code language=text} pair written in an explicit @label is that label in
 * another language. The key is a language tag (en, ja, pt-BR, zh-TW; pt_BR is
 * the same tag). A value runs to the next comma, spaces and apostrophes
 * included; it is quoted when it holds a comma.
 *
 * Only an @label the developer writes is read this way. The one the loader
 * builds from a "--" comment is read as it always was, so a comment such as
 * "-- sum = price * qty" is never a translation.
 */
public final class LabelTranslations {

    private LabelTranslations() {
    }

    /** A language tag as a key: a language, then optional script, region or variant parts. */
    private static final Pattern LANGUAGE = Pattern.compile("[A-Za-z]{2,3}(?:[-_][A-Za-z0-9]{2,8})*");

    /**
     * The key as one form of the tag: the language in lower case, a script
     * title-cased, a region in upper case, joined with '-' (pt_BR -> pt-BR,
     * zh-hant-tw -> zh-Hant-TW) - so en-US and en_US are one language, and
     * the bundle file is the name a ResourceBundle looks for.
     */
    public static String normalized(String key) {
        String[] parts = key.split("[-_]");
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase(Locale.ROOT));
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            sb.append('-');
            if (p.length() == 4 && Character.isLetter(p.charAt(0))) {
                sb.append(p.substring(0, 1).toUpperCase(Locale.ROOT)).append(p.substring(1).toLowerCase(Locale.ROOT));
            } else if (p.length() == 2 || (p.length() == 3 && Character.isDigit(p.charAt(0)))) {
                sb.append(p.toUpperCase(Locale.ROOT));
            } else {
                sb.append(p.toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    /**
     * The parameters of an explicit @label read as a label and its
     * translations, or null when they are not that - then they are read as
     * they always were. They are that when at least one part is
     * {@code language=text} and every part with '=' is one; the other parts,
     * joined, are the label itself.
     */
    public static Annotation parse(String name, String parameters) {
        if (parameters == null || parameters.indexOf('=') < 0) return null;
        Properties translations = new Properties();
        List<String> label = new ArrayList<String>();
        for (String part : parts(parameters)) {
            String t = part.trim();
            int eq = isQuote(t) ? -1 : t.indexOf('=');
            if (eq < 0) {
                if (!t.isEmpty()) label.add(unquoted(t));
                continue;
            }
            String key = t.substring(0, eq).trim();
            if (!LANGUAGE.matcher(key).matches()) return null;
            String value = unquoted(t.substring(eq + 1).trim());
            if (!value.isEmpty()) translations.setProperty(normalized(key), value);
        }
        if (translations.isEmpty()) return null;
        return Annotation.createTranslatedLabel(name, label.isEmpty() ? null : join(label), translations);
    }

    /**
     * The translations of a label annotation, by language in name order;
     * empty when it has none. Only a label read as translations has them.
     */
    public static Map<String, String> of(Annotation label) {
        if (label == null || !label.isTranslatedLabel() || label.getProperties() == null) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new TreeMap<String, String>();
        for (String key : label.getProperties().stringPropertyNames()) {
            out.put(key, label.getProperties().getProperty(key));
        }
        return out;
    }

    /**
     * Every @label of a line as one: the label is the first one written
     * (the "--" text when the explicit ones give none), the translations
     * those of every explicit @label, the first of a language kept.
     * {@code @label(en=Unit cost) -- Stueckpreis} is one label, Stueckpreis,
     * with an English one. Null when no @label of the line has a translation:
     * then nothing changes.
     */
    public static Annotation merged(Annotation first) {
        if (first == null) return null;
        // The siblings start with the annotation itself, then each later one on the line.
        List<Annotation> all = first.getSiblings();
        Properties translations = new Properties();
        String value = null;
        for (Annotation a : all) {
            for (Map.Entry<String, String> t : of(a).entrySet()) {
                if (!translations.containsKey(t.getKey())) translations.setProperty(t.getKey(), t.getValue());
            }
            if (value == null && a.hasValue()) value = a.value;
        }
        if (translations.isEmpty() || (all.size() == 1 && first.isTranslatedLabel())) return null;
        return Annotation.createTranslatedLabel(first.getName(), value, translations);
    }

    /**
     * The parts between commas. A quote opens a quoted value only where a
     * value starts - a part, or after its '=' - so an apostrophe inside a
     * value (Owner's name) is a letter.
     */
    private static List<String> parts(String s) {
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean start = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (start && (c == '"' || c == '\'')) {
                int end = s.indexOf(c, i + 1);
                if (end > 0) {
                    cur.append(s, i, end + 1);
                    i = end;
                    start = false;
                    continue;
                }
            }
            if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
                start = true;
                continue;
            }
            cur.append(c);
            if (c == '=') {
                start = true;
            } else if (c != ' ' && c != '\t') {
                start = false;
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static boolean isQuote(String s) {
        return !s.isEmpty() && (s.charAt(0) == '"' || s.charAt(0) == '\'');
    }

    private static String unquoted(String s) {
        if (s.length() >= 2 && isQuote(s) && s.charAt(s.length() - 1) == s.charAt(0)) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p);
        }
        return sb.toString();
    }
}
