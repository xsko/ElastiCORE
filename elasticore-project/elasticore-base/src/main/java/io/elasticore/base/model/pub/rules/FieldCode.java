package io.elasticore.base.model.pub.rules;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The code being built for one field: comment, annotations and declaration.
 * The declaration itself (modifier, type, name) always comes from the model;
 * rules only decorate or adjust it.
 */
public final class FieldCode {

    private final FieldSpec spec;

    private String comment;
    private final List<AnnoCode> annotations = new ArrayList<AnnoCode>();

    private String modifier = "private";
    private String typeName;

    /** Name to declare the field under, when it differs from the model's. */
    private String declaredName;

    /** Declared as possibly absent even where the DSL requires a value. */
    private boolean optional;
    private String defaultValue = "";
    private boolean removed = false;

    /** Lines emitted before the annotations, e.g. a description comment block. */
    private final List<String> beforeLines = new ArrayList<String>();

    /** Extra member emitted right after this field (e.g. a @PrePersist method). */
    private final List<String> afterLines = new ArrayList<String>();

    FieldCode(FieldSpec spec) {
        this.spec = spec;
        this.typeName = spec.typeName();
    }

    // ---- annotation verbs -------------------------------------------------

    private AnnoCode find(String fqcn) {
        for (AnnoCode a : annotations) {
            if (a.getFqcn().equals(fqcn)) return a;
        }
        return null;
    }

    /**
     * Appends the annotation unconditionally. Used where 2.x emits the same
     * annotation type twice from different code paths (e.g. @JoinColumn from
     * a relation column name and from @join).
     */
    public FieldCode add(AnnoCode anno) {
        annotations.add(anno);
        return this;
    }

    /** Adds the annotation unless one of the same type is already present. */
    public FieldCode ensure(AnnoCode anno) {
        if (find(anno.getFqcn()) == null) {
            annotations.add(anno);
        }
        return this;
    }

    /** Merges attributes into an existing annotation of the same type, or adds it. */
    public FieldCode merge(AnnoCode anno) {
        AnnoCode existing = find(anno.getFqcn());
        if (existing == null) {
            annotations.add(anno);
        } else if (existing.isRaw() && Boolean.TRUE.equals(SourceCode.PROJECT_RULE.get())) {
            // A 2.x annotation kept as its written line - the DTO's @Schema -
            // has no attributes to merge into, so a project rule's merge
            // used to vanish. It goes into the line instead. Built-in rules
            // keep the old behaviour: their output is held byte for byte.
            existing.mergeIntoLine(anno);
        } else {
            existing.mergeFrom(anno);
        }
        return this;
    }

    /** Replaces an existing annotation of the same type entirely; no-op when absent. */
    public FieldCode replace(AnnoCode anno) {
        Iterator<AnnoCode> it = annotations.iterator();
        while (it.hasNext()) {
            if (it.next().getFqcn().equals(anno.getFqcn())) {
                it.remove();
                annotations.add(anno);
                return this;
            }
        }
        return this;
    }

    /** Removes the annotation of the given type when present. */
    public FieldCode removeAnnotation(String fqcn) {
        Iterator<AnnoCode> it = annotations.iterator();
        while (it.hasNext()) {
            if (it.next().getFqcn().equals(fqcn)) {
                it.remove();
            }
        }
        return this;
    }

    // ---- declaration ------------------------------------------------------

    public FieldCode comment(String format, Object... args) {
        this.comment = Formatted.text(format, args);
        return this;
    }

    public FieldCode modifier(String modifier) {
        this.modifier = modifier;
        return this;
    }

    public FieldCode type(String typeName) {
        this.typeName = typeName;
        return this;
    }

    private final List<String> accessorLines = new ArrayList<String>();

    /**
     * A line that belongs to the declaration rather than after it.
     *
     * Kotlin writes an accessor's visibility as a continuation of the
     * property - "private set", indented under it - which is neither an
     * annotation before nor a member after. {@link #after} would put it in
     * the trailing-member group with a blank line in front, where it is a
     * syntax error rather than a restriction.
     *
     * This existed once with no consumer and was removed for that reason. It
     * is back because there is one.
     */
    public FieldCode accessor(String format, Object... args) {
        accessorLines.add(String.format(format, args));
        return this;
    }

    /**
     * Declares the field under a different name. The search DTO needs it: a
     * range condition turns one model field into a From/To pair, and an alias
     * lets the DSL name the property a caller sends.
     */
    public FieldCode rename(String name) {
        this.declaredName = name;
        return this;
    }

    /**
     * Declares the field as one a value may lack, whatever the DSL requires
     * of a request. A TypeScript interface is the shape of a reply as well,
     * and a write-only field is never in one.
     */
    public FieldCode optional() {
        this.optional = true;
        return this;
    }

    /** Initializer expression, e.g. "false" renders as "= false". */
    public FieldCode initial(String expression) {
        this.defaultValue = String.format(" = %s", expression);
        return this;
    }

    /** Drops the initializer, so the field starts out absent. */
    public FieldCode noInitial() {
        this.defaultValue = "";
        return this;
    }

    /** Initializer built from a format; never concatenate. */
    public FieldCode initial(String format, Object... args) {
        return initial(Formatted.text(format, args));
    }

    /** Appends a line emitted before the annotations of this field. */
    public FieldCode before(String format, Object... args) {
        beforeLines.add(Formatted.text(format, args));
        return this;
    }

    /** Appends a line to the member emitted after this field (own blank-line group). */
    public FieldCode after(String format, Object... args) {
        afterLines.add(Formatted.text(format, args));
        return this;
    }

    /** Drops the trailing member a base rule attached (e.g. a @PrePersist block). */
    public FieldCode removeAfter() {
        afterLines.clear();
        return this;
    }

    List<String> afterLines() {
        return afterLines;
    }

    void markRemoved() {
        this.removed = true;
    }

    boolean isRemoved() {
        return removed;
    }

    FieldSpec spec() {
        return spec;
    }

    List<AnnoCode> annotations() {
        return annotations;
    }

    // ---- rendering --------------------------------------------------------

    /** The type as declared, before any shortening. */
    String declaredTypeName() {
        return typeName;
    }

    /**
     * The fully qualified types named inside a type expression, so a
     * List&lt;java.time.LocalDate&gt; contributes the element type too.
     */
    static List<String> declaredTypes(String typeExpr) {
        List<String> out = new ArrayList<String>();
        if (typeExpr == null) return out;
        java.util.regex.Matcher m = QUALIFIED_TYPE.matcher(typeExpr);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** A package-qualified type name: lower-case packages, then a class. */
    private static final java.util.regex.Pattern QUALIFIED_TYPE =
            java.util.regex.Pattern.compile(
                    "\\b([a-z][a-zA-Z0-9_]*(?:\\.[a-z][a-zA-Z0-9_]*)*\\.[A-Z][A-Za-z0-9_]*)\\b");

    /** Rewrites a type expression, shortening what the style allows. */
    private static String writeType(String typeExpr, AnnoCode.NameStyle style) {
        if (typeExpr == null || style == null || !style.simplifiesDeclaredTypes()) return typeExpr;
        java.util.regex.Matcher m = QUALIFIED_TYPE.matcher(typeExpr);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String fqcn = m.group(1);
            String written = style.useSimpleName(fqcn, false)
                    ? fqcn.substring(fqcn.lastIndexOf('.') + 1) : fqcn;
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(written));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    List<String> renderLines(AnnoCode.NameStyle style) {
        return renderLines(style, Syntax.JAVA);
    }

    /**
     * The before/after lines a rule wrote as text, minus the ones that are
     * annotations where the language has none.
     *
     * A rule that reproduces 2.x byte for byte sometimes writes an annotation
     * as raw text rather than through AnnoCode - the format pair a range
     * property repeats does. Those bypass the annotation list, so the same
     * question is asked of them here. A line opening with "@" in this position
     * is an annotation; nothing else the emitters write starts that way.
     */
    static List<String> annotationsAllowed(List<String> lines, Syntax syntax) {
        if (syntax.supportsAnnotations()) return lines;
        List<String> kept = new ArrayList<String>();
        for (String line : lines) {
            if (line != null && line.trim().startsWith("@")) continue;
            kept.add(line);
        }
        return kept;
    }

    List<String> renderLines(AnnoCode.NameStyle style, Syntax syntax) {
        List<String> lines = new ArrayList<String>();
        lines.addAll(annotationsAllowed(beforeLines, syntax));
        if (comment != null) {
            lines.add(String.format("/** %s */", comment));
        }
        if (syntax.supportsAnnotations()) {
            for (AnnoCode anno : annotations) {
                lines.add(anno.render(style));
            }
        }
        String name = declaredName == null ? spec.name() : declaredName;
        String declared = modifier.equals("private") ? syntax.fieldModifier() : modifier;
        lines.add(syntax.declaration(declared, writeType(typeName, style), name,
                defaultValue, spec.required() && !optional));
        lines.addAll(accessorLines);
        return lines;
    }
}
