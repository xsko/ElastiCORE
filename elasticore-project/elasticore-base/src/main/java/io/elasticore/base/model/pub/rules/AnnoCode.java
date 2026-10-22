package io.elasticore.base.model.pub.rules;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single Java annotation under construction.
 *
 * Values are given with typed setters so the renderer decides quoting:
 * - val(key, String)  renders as  key = "value"
 * - val(key, int)     renders as  key = 30
 * - val(key, boolean) renders as  key = false
 * - valRaw(key, code) renders the code fragment verbatim (enum constants, class literals)
 *
 * The annotation type is always given as a full package name; the renderer
 * decides between simple name plus import or inline FQCN.
 */
public final class AnnoCode {

    private final String fqcn;

    /** attribute name -> rendered value fragment (insertion order preserved) */
    private final Map<String, Object> attrs = new LinkedHashMap<String, Object>();

    /** When set, render() returns this text verbatim (2.x format-quirk escape hatch). */
    private String rawLine;

    /**
     * What separates an attribute from its value. JPA annotations are written
     * spaced out; the runtime port ones are written tight, and both forms are
     * the ones 2.x emits.
     */
    private String assign = " = ";

    /** When set, the annotation is written by its full name, as 2.x writes some. */
    private boolean qualified;

    /**
     * When set, attributes are written one per line, each carrying this indent,
     * with the closing paren on a line of its own. 2.x writes a few annotations
     * this way - the generator and the validation pattern - and the indent is
     * given rather than assumed, because the ones it writes do not agree on it.
     */
    private String blockIndent;

    /** The single-element form's value when that value is a braced list. */
    private AnnoList valueList;

    /** The single-element form's value as a code fragment: @Min(0), @Foo("x"). */
    private String singleValue;

    /**
     * How a braced list of nested annotations is laid out. 2.x writes the two
     * it emits differently - one puts the separating comma after an entry, the
     * other ahead of the next one - so neither is treated as the default.
     */
    public static final class AnnoList {

        private final java.util.List<AnnoCode> items = new java.util.ArrayList<AnnoCode>();
        private String itemIndent = "";
        private String closeIndent = "";
        private boolean commaLeads;
        private boolean inline = true;

        private AnnoList() {
        }

        /** A list of nested annotations. Only annotations belong in one. */
        public static AnnoList of(AnnoCode... items) {
            AnnoList list = new AnnoList();
            for (AnnoCode item : items) list.items.add(item);
            return list;
        }

        public static AnnoList of(java.util.List<AnnoCode> items) {
            AnnoList list = new AnnoList();
            list.items.addAll(items);
            return list;
        }

        /** Writes one entry per line, each carrying the given indent. */
        public AnnoList itemIndent(String indent) {
            this.itemIndent = indent;
            this.inline = false;
            return this;
        }

        /** The indent of the closing brace; the margin unless told otherwise. */
        public AnnoList closeIndent(String indent) {
            this.closeIndent = indent;
            return this;
        }

        /** Writes the separator ahead of an entry rather than after the previous one. */
        public AnnoList commaLeads() {
            this.commaLeads = true;
            return this;
        }

        String render(NameStyle style) {
            String open = style.syntax() == Syntax.KOTLIN ? "[" : "{";
            String close = style.syntax() == Syntax.KOTLIN ? "]" : "}";
            if (items.isEmpty()) return open + close;
            StringBuilder sb = new StringBuilder(open);
            for (int i = 0; i < items.size(); i++) {
                boolean first = i == 0;
                boolean last = i == items.size() - 1;
                String text = items.get(i).render(style);
                // Nested as an argument, Kotlin writes a constructor call.
                if (style.syntax() == Syntax.KOTLIN && text.startsWith("@")) {
                    text = text.substring(1);
                }
                if (inline) {
                    if (!first) sb.append(", ");
                    sb.append(text);
                    continue;
                }
                sb.append('\n').append(itemIndent);
                if (commaLeads) {
                    sb.append(first ? " " : ", ").append(text);
                } else {
                    sb.append(text).append(last ? "" : ",");
                }
            }
            if (!inline) sb.append('\n').append(closeIndent);
            return sb.append(close).toString();
        }
    }

    /**
     * Writes the attributes one per line with the given indent, as 2.x writes
     * the annotations it spreads out.
     */
    public AnnoCode block(String attrIndent) {
        this.blockIndent = attrIndent;
        return this;
    }

    /** An attribute whose value is another annotation. */
    public AnnoCode val(String key, AnnoCode nested) {
        attrs.put(key, nested);
        return this;
    }

    /** An attribute whose value is a braced list of nested annotations. */
    public AnnoCode val(String key, AnnoList list) {
        attrs.put(key, list);
        return this;
    }

    /** The single-element form whose value is a braced list: @Foo({ ... }). */
    public AnnoCode value(AnnoList list) {
        this.valueList = list;
        return this;
    }

    /**
     * Writes the annotation by its full name rather than its simple one.
     * 2.x spells several out in full - the format and validation ones - so
     * the generated file needs no import for them.
     */
    public AnnoCode qualified() {
        this.qualified = true;
        return this;
    }

    /** Writes attributes as key=value rather than key = value. */
    public AnnoCode compact() {
        this.assign = "=";
        return this;
    }

    private AnnoCode(String fqcn) {
        this.fqcn = fqcn;
    }

    public static AnnoCode of(String fqcn) {
        return new AnnoCode(fqcn);
    }

    /** Annotation type built from a format; never concatenate FQCN strings. */
    public static AnnoCode of(String format, Object... args) {
        return new AnnoCode(Formatted.text(format, args));
    }

    /**
     * A verbatim annotation line. Used by base rules ported 1:1 from 2.x
     * publishers whose formatting is not uniform (e.g. trailing spaces).
     * The fqcn still identifies the annotation for ensure/merge/remove.
     */
    public static AnnoCode raw(String fqcn, String line) {
        AnnoCode a = new AnnoCode(fqcn);
        a.rawLine = line;
        return a;
    }

    /**
     * Same as {@link #raw(String, String)} with format arguments. Rule code
     * must use this instead of string concatenation so the emitted shape
     * stays readable in the rule source.
     */
    public static AnnoCode raw(String fqcn, String format, Object... args) {
        AnnoCode a = new AnnoCode(fqcn);
        a.rawLine = Formatted.text(format, args);
        return a;
    }

    /** The single-element form without quotes: @Min(0), @Foo(Bar.class). */
    public AnnoCode valueRaw(String code) {
        this.singleValue = code;
        return this;
    }

    /** The single-element form: @Foo("x") rather than @Foo(value = "x"). */
    public AnnoCode value(String value) {
        this.singleValue = String.format("\"%s\"", value);
        return this;
    }

    public AnnoCode val(String key, String value) {
        attrs.put(key, String.format("\"%s\"", value));
        return this;
    }

    /**
     * The attribute with the value it falls back to when the model does not
     * give one, so a rule states the default where the attribute is written
     * rather than resolving it beforehand.
     */
    public AnnoCode val(String key, String value, String fallback) {
        return val(key, (value == null || value.isEmpty()) ? fallback : value);
    }

    public AnnoCode val(String key, int value) {
        attrs.put(key, String.valueOf(value));
        return this;
    }

    public AnnoCode val(String key, boolean value) {
        attrs.put(key, String.valueOf(value));
        return this;
    }

    /** Adds the attribute only when the condition holds. Keeps chains flat. */
    public AnnoCode valIf(boolean condition, String key, String value) {
        if (condition) val(key, value);
        return this;
    }

    public AnnoCode valIf(boolean condition, String key, int value) {
        if (condition) val(key, value);
        return this;
    }

    public AnnoCode valIf(boolean condition, String key, boolean value) {
        if (condition) val(key, value);
        return this;
    }

    /** Verbatim code fragment: enum constant, class literal, nested expression. */
    public AnnoCode valRaw(String key, String code) {
        attrs.put(key, code);
        return this;
    }

    /** Verbatim code fragment built from a format; never concatenate. */
    public AnnoCode valRaw(String key, String format, Object... args) {
        attrs.put(key, Formatted.text(format, args));
        return this;
    }

    public String getFqcn() {
        return fqcn;
    }

    /** True when the annotation carries no attribute, list or raw line. */
    public boolean isEmpty() {
        return rawLine == null && valueList == null && singleValue == null && attrs.isEmpty();
    }

    /**
     * Decides whether a type may be written by its simple name in the body.
     *
     * A simple name only stands for one type per file, so when two packages
     * claim the same one - and a domain-per-package generator produces plenty
     * of those, a Views and a Q for every domain - the file has to spell at
     * least one of them out in full. The artifact knows which names are
     * contested; an annotation on its own does not.
     */
    public interface NameStyle {

        /**
         * The language being written. An annotation's shape is the same in
         * both, but an array of values is braced in Java and bracketed in
         * Kotlin, so the renderer has to know which it is writing.
         */
        Syntax syntax();

        /**
         * @param fqcn      the type being written
         * @param ruleAsked whether the rule asked for the full name, which
         *                  the compatibility mode honours and the clean mode
         *                  overrides - 2.x spells several annotations out
         *                  that an import would say more briefly
         */
        boolean useSimpleName(String fqcn, boolean ruleAsked);

        /**
         * Whether a field's declared type may be shortened too. Only the
         * clean mode does this; 2.x writes java.time and BigDecimal types
         * in full and the compatibility mode has to match it.
         */
        boolean simplifiesDeclaredTypes();
    }

    /** An attribute's value, rendered now that the naming style is known. */
    private static String value(Object v, NameStyle style) {
        if (v instanceof AnnoCode) return ((AnnoCode) v).render(style);
        if (v instanceof AnnoList) return ((AnnoList) v).render(style);
        return String.valueOf(v);
    }

    /** How this annotation is written: its full name, or its simple one. */
    private String name(NameStyle style) {
        if (style == null) return qualified ? fqcn : simpleName();
        return style.useSimpleName(fqcn, qualified) ? simpleName() : fqcn;
    }

    public String simpleName() {
        int p = fqcn.lastIndexOf('.');
        return p < 0 ? fqcn : fqcn.substring(p + 1);
    }

    /**
     * Every annotation type this one writes, nested ones included: what a
     * file has to import to write it by simple names.
     */
    void collectTypes(java.util.Set<String> out) {
        out.add(fqcn);
        if (valueList != null) {
            for (AnnoCode item : valueList.items) item.collectTypes(out);
        }
        for (Object v : attrs.values()) {
            if (v instanceof AnnoCode) ((AnnoCode) v).collectTypes(out);
            if (v instanceof AnnoList) {
                for (AnnoCode item : ((AnnoList) v).items) item.collectTypes(out);
            }
        }
    }

    /**
     * The annotation types the name style decides for: this one and the ones
     * nested in it, except written lines. A raw line keeps the name its rule
     * wrote, so it can never be spelled out in full and needs its import.
     */
    void collectStyledTypes(java.util.Set<String> out) {
        if (isRaw()) return;
        out.add(fqcn);
        if (valueList != null) {
            for (AnnoCode item : valueList.items) item.collectStyledTypes(out);
        }
        for (Object v : attrs.values()) {
            if (v instanceof AnnoCode) ((AnnoCode) v).collectStyledTypes(out);
            if (v instanceof AnnoList) {
                for (AnnoCode item : ((AnnoList) v).items) item.collectStyledTypes(out);
            }
        }
    }

    /** The annotation types written as fixed lines: this one if it is one, and any nested in it. */
    void collectRawTypes(java.util.Set<String> out) {
        if (isRaw()) {
            out.add(fqcn);
            return;
        }
        if (valueList != null) {
            for (AnnoCode item : valueList.items) item.collectRawTypes(out);
        }
        for (Object v : attrs.values()) {
            if (v instanceof AnnoCode) ((AnnoCode) v).collectRawTypes(out);
            if (v instanceof AnnoList) {
                for (AnnoCode item : ((AnnoList) v).items) item.collectRawTypes(out);
            }
        }
    }

    /** Whether this annotation is a line written out by its rule rather than built from attributes. */
    boolean isRaw() {
        return rawLine != null;
    }

    /**
     * Merges another annotation's attributes into this written line: an
     * attribute the line already has takes the new value, any other is
     * appended before the closing parenthesis. Other wins on conflict, as
     * in {@link #mergeFrom}.
     */
    void mergeIntoLine(AnnoCode other) {
        String line = rawLine;
        int close = line.lastIndexOf(')');
        for (Map.Entry<String, Object> en : other.attrs.entrySet()) {
            String value = value(en.getValue(), null);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\b" + java.util.regex.Pattern.quote(en.getKey()) + "\\s*=\\s*(\"[^\"]*\"|[^,)]+)")
                    .matcher(line);
            if (m.find()) {
                line = line.substring(0, m.start(1)) + value + line.substring(m.end(1));
            } else if (close < 0) {
                line = line + "(" + en.getKey() + " = " + value + ")";
            } else {
                String head = line.substring(0, close).replaceAll("\\s+$", "");
                String sep = head.endsWith("(") ? "" : ", ";
                line = head + sep + en.getKey() + " = " + value + line.substring(close);
            }
            close = line.lastIndexOf(')');
        }
        rawLine = line;
    }

    /** Merges attributes from another annotation of the same type. Other wins on conflict. */
    void mergeFrom(AnnoCode other) {
        attrs.putAll(other.attrs);
    }

    /**
     * Renders the annotation line using the simple type name.
     * Import handling is the renderer's concern, not the rule author's.
     */
    public String render() {
        return render(null);
    }

    /**
     * Renders the annotation, writing type names the way the given style
     * allows. A raw line was spelled out by its rule and is returned as is.
     */
    public String render(NameStyle style) {
        if (rawLine != null) {
            return rawLine;
        }
        if (valueList != null) {
            return String.format("@%s(%s)", name(style), valueList.render(style));
        }
        if (singleValue != null) {
            return String.format("@%s(%s)", name(style), singleValue);
        }
        if (attrs.isEmpty()) {
            return String.format("@%s", name(style));
        }
        StringBuilder attrText = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> en : attrs.entrySet()) {
            if (blockIndent != null) {
                if (!first) attrText.append(',');
                attrText.append('\n').append(blockIndent);
            } else if (!first) {
                attrText.append(", ");
            }
            attrText.append(String.format("%s%s%s", en.getKey(), assign, value(en.getValue(), style)));
            first = false;
        }
        if (blockIndent != null) attrText.append('\n');
        return String.format("@%s(%s)", name(style), attrText);
    }
}
