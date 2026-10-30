package io.elasticore.base.model.pub.rules;

import java.util.LinkedHashMap;
import java.util.Map;
import io.elasticore.base.util.CodeTemplate;

/**
 * The file under construction: what the rules say about it, which the
 * generator renders into its skeleton (.tmpl) and writes. Holds one
 * {@link FieldCode} per model field, in model order, the class annotations,
 * the imports and the values of the skeleton's slots.
 *
 * Rules reach it through {@code Ctx.ctx(spec)}; a rule set's tests render it
 * with {@link #render()} and check {@link #unfilledSlots()}.
 */
public final class SourceCode {

    private final EntitySpec entity;

    /** field name -> field code, insertion order = model order */
    private final Map<String, FieldCode> fieldCodes = new LinkedHashMap<String, FieldCode>();

    private boolean make = true;

    SourceCode(EntitySpec entity) {
        this.entity = entity;
        for (FieldSpec f : entity.fields()) {
            fieldCodes.put(f.name(), new FieldCode(f));
        }
    }

    /** The code being built for the given field. */
    public FieldCode field(FieldSpec spec) {
        return fieldCodes.get(spec.name());
    }

    /** Excludes the field from the generated source entirely. */
    public SourceCode removeField(FieldSpec spec) {
        FieldCode fc = fieldCodes.get(spec.name());
        if (fc != null) fc.markRemoved();
        return this;
    }

    /** Skips generation of this artifact altogether. */
    public SourceCode dontMake() {
        this.make = false;
        return this;
    }

    public boolean isMake() {
        return make;
    }

    public EntitySpec getEntity() {
        return entity;
    }

    /** The language this artifact is written in, for rules that must differ. */
    public Syntax syntax() {
        return templateSet.syntax();
    }

    /**
     * Asserts that this artifact is being written in the language the calling
     * rule writes.
     *
     * A file shell is language-specific by definition, and a bundle is only a
     * list of names - so composing the wrong two puts one language's shell in
     * front of another's skeleton. Nothing downstream notices: a template is
     * text, and Java in a .kt file is only found by a compiler, if one runs.
     * Saying it here names the composition instead.
     */
    public SourceCode requireSyntax(Syntax expected, String rule) {
        if (syntax() != expected) {
            throw new IllegalStateException(String.format(
                    "Rule '%s' writes %s, but this artifact is being written in %s. "
                            + "A bundle has been composed with a file shell for another "
                            + "language - check publisher.rules against the language asked for.",
                    rule, expected, syntax()));
        }
        return this;
    }

    /**
     * The same statement for a rule that writes more than one language.
     *
     * A set that has a skeleton per language is not language-specific the
     * way a file shell is - it is specific to the ones it has skeletons for,
     * and silent about the rest. Naming them is what keeps the third
     * language from reaching a rule that never considered it.
     */
    public SourceCode requireSyntaxAnyOf(String rule, Syntax... accepted) {
        for (Syntax s : accepted) {
            if (syntax() == s) return this;
        }
        StringBuilder names = new StringBuilder();
        for (Syntax s : accepted) {
            if (names.length() > 0) names.append(" or ");
            names.append(s);
        }
        throw new IllegalStateException(String.format(
                "Rule '%s' writes %s, but this artifact is being written in %s. "
                        + "There is no skeleton for that language in this rule set - "
                        + "check publisher.rules against the language asked for.",
                rule, names, syntax()));
    }

    /**
     * Renders the field block exactly as the 2.x publisher emits it into
     * the ${fieldList} template slot: each field is followed by two blank
     * lines (see EntitySrcFilePublisher.loadFieldInfo).
     */
    public String renderFieldBlock() {
        return renderFieldBlock(2);
    }

    /** Same as above with a configurable blank-line separator (DTO uses one). */
    public String renderFieldBlock(int blankLinesAfterField) {
        // Decided once for the block: the contested names are a property
        // of the whole artifact, not of the field being written.
        AnnoCode.NameStyle style = nameStyle();
        StringBuilder sb = new StringBuilder();
        for (FieldCode fc : fieldCodes.values()) {
            if (fc.isRemoved()) continue;
            for (String line : fc.renderLines(style, templateSet.syntax())) {
                sb.append(line).append('\n');
            }
            for (int i = 0; i < blankLinesAfterField; i++) {
                sb.append('\n');
            }
            // Trailing member (e.g. @PrePersist) gets its own blank-line group.
            java.util.List<String> after = FieldCode.annotationsAllowed(
                    fc.afterLines(), templateSet.syntax());
            if (!after.isEmpty()) {
                for (String line : after) {
                    sb.append(line).append('\n');
                }
                for (int i = 0; i < blankLinesAfterField; i++) {
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    // ---- template variables (skeleton-based artifacts) --------------------

    /** template variable name -> value */
    private final Map<String, String> vars = new LinkedHashMap<String, String>();

    /** Sets a ${var} value for skeleton templates. Empty string is a valid value. */
    /** The value a rule has set for a template variable, or null. */
    public String varValue(String name) {
        return vars.get(name);
    }

    public SourceCode var(String name, String value) {
        vars.put(name, value);
        return this;
    }

    /** Sets a ${var} value built from a format; never concatenate. */
    public SourceCode var(String name, String format, Object... args) {
        return var(name, Formatted.text(format, args));
    }

    /**
     * Renders a .tmpl skeleton with 2.x CodeTemplate semantics for this
     * prototype's scope: every ${var} on a line is substituted; a line
     * referencing a variable that was never set renders as an empty line.
     */
    private final java.util.Set<String> unfilled = new java.util.LinkedHashSet<String>();

    /**
     * The ${slots} the last render found no value for. Such a line renders
     * as an empty line rather than an error, so a misspelt slot name shows
     * nowhere in the output - a rule set's test asks here instead.
     */
    public java.util.List<String> unfilledSlots() {
        return new java.util.ArrayList<String>(unfilled);
    }

    public String renderTemplate(String templateText) {
        String[] lines = templateText.replace("\r\n", "\n").split("\n", -1);
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\$\\{(.+?)\\}");

        StringBuilder out = new StringBuilder();

        // A print-if block is decided on the substituted line, so a slot can
        // be what the condition reads.
        boolean skipping = false;
        boolean conditionMatched = false;

        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];

            String paragraphKey = paragraphKeyOf(line, p);
            if (paragraphKey != null) {
                // A slot with nothing in it takes its line with it, newline and all.
                if (collapsibleSlots.contains(paragraphKey) && paragraphVars.get(paragraphKey).size() == 0) continue;
                if (!skipping) {
                    out.append(expandParagraph(line, paragraphKey));
                    if (li < lines.length - 1) out.append('\n');
                }
                continue;
            }

            java.util.regex.Matcher m = p.matcher(line);
            StringBuffer sb = new StringBuffer();
            boolean unresolved = false;
            while (m.find()) {
                String val = vars.get(m.group(1));
                if (val == null) {
                    unresolved = true;
                    if (!skipping) unfilled.add(m.group(1));
                    break;
                }
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(val));
            }
            if (unresolved) {
                if (!skipping) out.append('\n');
                continue;
            }
            m.appendTail(sb);
            String rendered = sb.toString();

            if (isBlockDirective(rendered)) {
                boolean isEnd = rendered.indexOf("end") > 0;
                boolean isElse = rendered.indexOf("/*-- print-elseif:") >= 0;
                boolean holds = rendered.indexOf("true") > 0;

                if (isEnd) {
                    skipping = false;
                    conditionMatched = false;
                } else if (isElse) {
                    // The else arm: taken when nothing before it was.
                    skipping = conditionMatched || holds;
                    if (!skipping) conditionMatched = true;
                } else {
                    conditionMatched = holds;
                    skipping = !holds;
                }
                continue;
            }
            if (skipping) continue;

            // A multi-line value is inserted as it stands, carrying whatever
            // indentation it was built with - only the slot's own line gets
            // the template's. Values that need the slot's indentation on every
            // line are paragraphs; see varLines.
            out.append(rendered);
            if (li < lines.length - 1) out.append('\n');
        }
        // Method-level control: no-op (byte-identical) unless rules asked for it.
        return MethodSections.apply(out.toString(), effectiveExcluded(), addedMethods);
    }

    /** A line that opens, alternates or closes a conditional block. */
    private static boolean isBlockDirective(String line) {
        return line.indexOf("/*-- print-if:") >= 0 || line.indexOf("/*-- print-elseif:") >= 0;
    }

    /**
     * Slots written as a paragraph: the template line is emitted once per
     * entry, so every entry inherits the slot's indentation - including the
     * empty entries used as separators. An empty paragraph renders the line
     * away entirely, as an unset value does.
     *
     * These are 2.x CodeTemplate Paragraphs, not a parallel notion of one, so
     * a rule gets its formatting, its splitting of a multi-line value into
     * entries, its optional collapsing of repeats, and composition through
     * add(Paragraph).
     */
    private final java.util.Map<String, CodeTemplate.Paragraph> paragraphVars =
            new java.util.HashMap<String, CodeTemplate.Paragraph>();

    /** The paragraph a slot is written into, started on first use. */
    public CodeTemplate.Paragraph paragraph(String name) {
        CodeTemplate.Paragraph p = paragraphVars.get(name);
        if (p == null) {
            p = CodeTemplate.newParagraph();
            paragraphVars.put(name, p);
        }
        return p;
    }

    /**
     * A slot that collapses repeated entries. Declare it before writing to
     * it; an import list is the case that wants this.
     */
    public SourceCode paragraphOfUniqueLines(String name) {
        if (!paragraphVars.containsKey(name)) {
            paragraphVars.put(name, CodeTemplate.newParagraph(true));
        }
        return this;
    }

    /** Replaces a slot's paragraph with one entry per element. */
    public SourceCode varLines(String name, java.util.List<String> entries) {
        CodeTemplate.Paragraph p = CodeTemplate.newParagraph();
        for (String entry : entries) p.add((Object) entry);
        paragraphVars.put(name, p);
        return this;
    }

    /**
     * Appends one line to a slot's paragraph, so a rule writes it the way it
     * writes a block - a line at a time, with format arguments - instead of
     * assembling a list first.
     *
     * As elsewhere, a call with no arguments takes the text as it stands, so
     * a line containing a percent sign needs no escaping.
     */
    public SourceCode varLine(String name, String format, Object... args) {
        // Placed here rather than by the paragraph, so the argument count is
        // checked once and the text reaches the slot already written. The
        // cast keeps the paragraph from formatting it a second time, which
        // would misread a per-cent sign the line carries of its own.
        paragraph(name).add((Object) Formatted.text(format, args));
        return this;
    }

    /**
     * Rewrites the lines a slot's paragraph holds, each through the function;
     * a null answer drops the line. For a rule that reads what the rules
     * before it wrote - MyBatis taking JPA's callbacks out of a class body.
     */
    public SourceCode rewriteVarLines(String name, java.util.function.UnaryOperator<String> rewrite) {
        CodeTemplate.Paragraph p = paragraphVars.get(name);
        if (p == null) return this;
        CodeTemplate.Paragraph out = CodeTemplate.newParagraph();
        for (int i = 0; i < p.size(); i++) {
            String line = rewrite.apply(p.toString(i));
            if (line != null) out.add((Object) line);
        }
        paragraphVars.put(name, out);
        return this;
    }

    /** True once a slot's paragraph has been given a line. */
    public boolean hasVarLines(String name) {
        CodeTemplate.Paragraph p = paragraphVars.get(name);
        return p != null && p.size() > 0;
    }

    /** The paragraph variable this line consists of, or null. */
    private final java.util.Set<String> collapsibleSlots = new java.util.HashSet<String>();

    /**
     * Declares a slot a rule may fill with lines: empty, it leaves nothing
     * behind - not even the blank line an empty paragraph leaves. For lines a
     * skeleton gains only when the DSL asks for them, so a model that does
     * not ask is generated exactly as before.
     */
    public SourceCode slot(String name) {
        paragraph(name);
        collapsibleSlots.add(name);
        return this;
    }

    private String paragraphKeyOf(String line, java.util.regex.Pattern p) {
        java.util.regex.Matcher m = p.matcher(line);
        String found = null;
        while (m.find()) {
            // A line mixing a paragraph with anything else has no 2.x meaning.
            if (found != null) return null;
            if (!paragraphVars.containsKey(m.group(1))) return null;
            found = m.group(1);
        }
        return found;
    }

    private String expandParagraph(String line, String key) {
        CodeTemplate.Paragraph entries = paragraphVars.get(key);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(line.replace("${" + key + "}", entries.toString(i)));
        }
        return sb.toString();
    }

    private static String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        return line.substring(0, i);
    }

    // ---- endpoint method control (skeleton artifacts with methods) --------

    private final java.util.Set<String> excludedMethods = new java.util.LinkedHashSet<String>();
    private final java.util.List<String> addedMethods = new java.util.ArrayList<String>();

    /**
     * Drops an endpoint method of the rendered skeleton by section id:
     * create, read, search, update, batchUpdate, deleteBatch, deleteById,
     * schemaInfo.
     */
    public SourceCode excludeMethod(String... sectionIds) {
        for (String id : sectionIds) excludedMethods.add(id);
        return this;
    }

    /** Whether a rule has dropped this endpoint method - or, in the clean output, one it calls. */
    public boolean isMethodExcluded(String sectionId) {
        return effectiveExcluded().contains(sectionId);
    }

    /**
     * The methods dropped, with what calls them in the clean output: a rule
     * set that excluded update alone left batchUpdate calling a method that
     * was gone, and the service did not compile. The 2.x output drops what
     * it was told, as it always did.
     */
    private java.util.Set<String> effectiveExcluded() {
        return compatible() ? excludedMethods : withCallers(excludedMethods);
    }

    /**
     * The method ids with every method that calls one of them, in the clean
     * skeletons: update takes batchUpdate (it loops over update), deleteById
     * takes delete (the list delete calls it) and deleteBatch, delete takes
     * deleteBatch, save takes create.
     */
    public static java.util.Set<String> withCallers(java.util.Set<String> ids) {
        java.util.Set<String> out = new java.util.LinkedHashSet<String>(ids);
        if (out.contains("update")) out.add("batchUpdate");
        if (out.contains("deleteById")) out.add("delete");
        if (out.contains("delete")) out.add("deleteBatch");
        if (out.contains("save")) out.add("create");
        return out;
    }

    /** Appends a hand-written method before the closing brace of the class. */
    public SourceCode addMethod(String methodSource) {
        // A project's entity rule also runs for the developer's class of a
        // root split in two - for the class annotations. The method it adds
        // is on the generated base class that one extends, so writing it
        // again would only override it with a copy. A rule written for the
        // behavior target itself still adds its method there.
        if (Boolean.TRUE.equals(BORROWED_ENTITY_RULE.get())) return this;
        addedMethods.add(methodSource);
        return this;
    }

    /** Which template directory an artifact is rendered from. */
    public enum TemplateSet {
        /** elasticore-template/<target>.tmpl - the 2.x skeletons. */
        COMPATIBLE(""),
        /** elasticore-template/v3/<target>.tmpl - the 3.0 skeletons. */
        V3("v3/"),
        /** elasticore-template/kt/<target>.tmpl - the Kotlin skeletons. */
        KOTLIN("kt/", Syntax.KOTLIN),
        /** elasticore-template/ts/<target>.tmpl - the TypeScript skeletons. */
        TYPESCRIPT("ts/", Syntax.TYPESCRIPT);

        private final String dir;
        private final Syntax syntax;

        TemplateSet(String dir) {
            this(dir, Syntax.JAVA);
        }

        TemplateSet(String dir, Syntax syntax) {
            this.dir = dir;
            this.syntax = syntax;
        }

        /** The language this set's skeletons are written in. */
        public Syntax syntax() {
            return syntax;
        }

        public String dir() {
            return dir;
        }
    }

    private String target = "entity";
    private TemplateSet templateSet = TemplateSet.COMPATIBLE;

    void bind(String target, TemplateSet templateSet) {
        this.target = target;
        this.templateSet = templateSet;
    }

    private String artifactName;

    /**
     * The class name this run is being written as.
     *
     * Every artifact has one, and for all but a few the rules can work it
     * out from the entity. A rule set that contributes more than one
     * artifact of the same target cannot - the spec is the same for all of
     * them - so the name it is being run for is what tells them apart.
     */
    SourceCode artifactName(String name) {
        this.artifactName = name;
        return this;
    }

    public String artifactName() {
        return artifactName;
    }

    public String target() {
        return target;
    }

    /** Whether this artifact is rendered for the 2.x-compatible output, which must stay byte-identical. */
    public boolean compatible() {
        return templateSet == TemplateSet.COMPATIBLE;
    }

    /**
     * Renders the artifact with the template its target name resolves to:
     * elasticore-template/[v3/]&lt;target&gt;.tmpl. Target names, template file
     * names and env.yml namespace keys are deliberately the same word, so no
     * separate mapping table exists to drift.
     */
    /**
     * Artifacts that are not source in the bundle's language, so the shared
     * template is the right one whichever language the rest is written in.
     * A .proto is its own language; the guard below is about not writing Java
     * into a .kt file, which does not arise here.
     */
    private static final java.util.Set<String> LANGUAGE_NEUTRAL =
            new java.util.HashSet<String>(java.util.Arrays.asList("proto"));

    public String render() {
        String name = templateName == null ? target : templateName;
        String tmplName = String.format("%s%s.tmpl", templateSet.dir(), name);

        // A target with nothing mode-specific to say - Q, port, the support
        // types - carries no v3/ skeleton of its own; the compat one already
        // says exactly what it needs to.
        //
        // Only within one language, though. The fallback set is written in
        // Java, so letting a Kotlin artifact reach it would put Java in a .kt
        // file - and quietly, since a template is only ever text. A missing
        // skeleton for another language is a gap to fill, not one to paper over.
        if (templateSet.dir().length() > 0
                && SourceCode.class.getResourceAsStream("/elasticore-template/" + tmplName) == null) {
            if (templateSet.syntax() != TemplateSet.COMPATIBLE.syntax()
                    && !LANGUAGE_NEUTRAL.contains(name)) {
                throw new IllegalStateException(String.format(
                        "No %s skeleton for target '%s' (expected /elasticore-template/%s). "
                                + "The %s fallback is written in %s and would be emitted as-is.",
                        templateSet, name, tmplName,
                        TemplateSet.COMPATIBLE, TemplateSet.COMPATIBLE.syntax()));
            }
            tmplName = String.format("%s.tmpl", name);
        }
        // A skeleton that asks for the imports or the class annotations and no
        // rule filled them in gets what the rules registered - a project's
        // own skeleton works with addImport and ensureClassAnnotation without
        // a shell rule of its own. Not for the 2.x skeletons, whose output is
        // held byte for byte.
        if (projectSkeleton && (templateSet == TemplateSet.V3 || templateSet == TemplateSet.KOTLIN)) {
            // Only a slot on a line of its own: some skeletons use the same
            // name inside an import line of their own making.
            String raw = rawTemplate(tmplName);
            if (vars.get("importList") == null && standalone(raw, "importList")) {
                vars.put("importList", io.elasticore.base.model.pub.rules.base.RuleText.stripTrailing(renderImports()));
            }
            if (vars.get("classAnnotationList") == null && standalone(raw, "classAnnotationList")) {
                vars.put("classAnnotationList", renderClassAnnotations());
            }
        }
        if (templateSet.syntax() == Syntax.KOTLIN) {
            // Spring Data 4 (Spring Boot 4) declares its types with JSpecify:
            // a Specification's type is non-null, and Page.map takes a
            // function that returns a value. Nothing for Spring Boot 3.
            if (!vars.containsKey("specBound")) vars.put("specBound", io.elasticore.base.model.pub.rules.lang.kotlin.KotlinSpring.specBound());
            if (!vars.containsKey("pageNonNull")) vars.put("pageNonNull", io.elasticore.base.model.pub.rules.lang.kotlin.KotlinSpring.pageNonNull());
        }
        boolean classByText = false;
        if (!projectAdded.isEmpty() || !projectRemoved.isEmpty()) classByText = !applyProjectAnnotations();
        String rendered = renderTemplateResource(tmplName);
        if (classByText && !projectRemoved.isEmpty() && REMOVAL_WARNED.add(target + "#" + projectRemoved)) {
            // Nothing drew these annotations as a list to take them from.
            io.elasticore.base.util.ConsoleLog.printWarn(String.format(
                    "[rule] the %s file has no class annotation list; removing %s had no effect",
                    target, projectRemoved));
        }
        // TypeScript has no annotations to place, and imports of its own shape.
        if (templateSet.syntax() != Syntax.TYPESCRIPT
                && (classByText || !methodAnnotations.isEmpty() || !projectImports.isEmpty())) {
            rendered = new DeclarationText(rendered, templateSet.syntax(), target)
                    .ensureImports(projectImports)
                    .annotateType(classByText ? projectAddedStill() : java.util.Collections.<AnnoCode>emptyList())
                    .annotateMethods(methodAnnotations)
                    .text();
        }
        // A package segment Kotlin reserves - the "in" of adapter.in.web,
        // the hexagon's own naming - has to be quoted in Kotlin source, or
        // not one file of the layer compiles.
        return templateSet.syntax() == Syntax.KOTLIN
                ? io.elasticore.base.model.pub.rules.lang.kotlin.KotlinKeywords.escapeQualifiedNames(rendered)
                : rendered;
    }

    private String templateName;

    /**
     * Renders from a template whose name differs from the target's. Needed
     * only where 2.x named a template independently of the artifact it
     * produces; a target normally resolves the template of the same name.
     */
    public SourceCode template(String name) {
        this.templateName = name;
        return this;
    }

    private static String rawTemplate(String tmplName) {
        java.io.InputStream in = SourceCode.class.getResourceAsStream("/elasticore-template/" + tmplName);
        if (in == null) return "";
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return "";
        } finally {
            try { in.close(); } catch (java.io.IOException ignored) { }
        }
    }

    private boolean projectSkeleton;

    /**
     * Marks the artifact as a project's own: every rule for it comes from
     * outside ElastiCORE, so no shell rule fills its lists. Set by the engine.
     */
    void projectSkeleton(boolean own) {
        this.projectSkeleton = own;
    }

    /** Whether ${name} stands on a line by itself in the skeleton. */
    private static boolean standalone(String raw, String name) {
        for (String line : raw.split("\r?\n")) {
            if (line.trim().equals("${" + name + "}")) return true;
        }
        return false;
    }

    /** Loads a template from the elasticore-base classpath resources. */
    public String renderTemplateResource(String tmplName) {
        String path = "/elasticore-template/" + tmplName;
        java.io.InputStream in = SourceCode.class.getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("Template resource not found: " + path);
        }
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return renderTemplate(new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read template " + path, e);
        } finally {
            try { in.close(); } catch (java.io.IOException ignore) { }
        }
    }

    // ---- named line blocks (method-body artifacts like the mapper) --------

    private final Map<String, java.util.List<String>> blocks =
            new LinkedHashMap<String, java.util.List<String>>();

    /** Appends a line to a named block. Rules build method bodies with this. */
    public SourceCode line(String block, String format, Object... args) {
        java.util.List<String> list = blocks.get(block);
        if (list == null) {
            list = new java.util.ArrayList<String>();
            blocks.put(block, list);
        }
        list.add(Formatted.text(format, args));
        return this;
    }

    public String renderBlock(String block) {
        java.util.List<String> list = blocks.get(block);
        if (list == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String l : list) sb.append(l).append('\n');
        return sb.toString();
    }

    /** Lines of a named block, for assembly rules that compose blocks. Never null. */
    public java.util.List<String> blockLines(String block) {
        java.util.List<String> list = blocks.get(block);
        return list == null ? java.util.Collections.<String>emptyList() : list;
    }

    // ---- read-only relations ---------------------------------------------

    private final java.util.Set<String> readOnlyRefs = new java.util.HashSet<String>();

    /**
     * States that a DTO field carrying @ref must not be written back through
     * the relation it reads.
     *
     * A @ref names a field of a related entity, and the mapper's reverse
     * direction assigns it back - which edits a row that belongs to whatever
     * that relation points at. That is right where the relation is part of
     * the same thing being saved and wrong where it is not, and only a rule
     * that knows the model can say which. So the mapper asks instead of
     * deciding: nothing is read-only until a rule says so, which leaves the
     * default output exactly as it was.
     *
     * Assigning the relation itself is unaffected - that is a reference being
     * pointed somewhere, not a write into what it points at.
     */
    public SourceCode refReadOnly(String dtoFieldName) {
        readOnlyRefs.add(dtoFieldName);
        return this;
    }

    /** Whether a rule has declined the write-back for this DTO field. */
    public boolean isRefReadOnly(String dtoFieldName) {
        return readOnlyRefs.contains(dtoFieldName);
    }

    private final java.util.Set<String> hiddenRefs = new java.util.HashSet<String>();

    /**
     * States that a DTO field carrying @ref is not read through the relation
     * either: the DTO does not carry it (a password of the related row). As
     * with refReadOnly, nothing is hidden until a rule says so.
     */
    public SourceCode refHidden(String dtoFieldName) {
        hiddenRefs.add(dtoFieldName);
        return this;
    }

    /** Whether a rule has declined the read through the relation for this DTO field. */
    public boolean isRefHidden(String dtoFieldName) {
        return hiddenRefs.contains(dtoFieldName);
    }

    // ---- relations an aggregate root writes its members through -----------

    private final java.util.Set<String> writtenByRoot = new java.util.HashSet<String>();

    /** States that the root writes its member through this entity field (ddd-mapper-members). */
    public SourceCode writtenByRoot(String entityFieldName) {
        writtenByRoot.add(entityFieldName);
        return this;
    }

    /** Whether a rule said the root writes its member through this field. */
    public boolean isWrittenByRoot(String entityFieldName) {
        return writtenByRoot.contains(entityFieldName);
    }

    // ---- fields the persistence layer owns ----------------------------------

    private final java.util.Set<String> systemManaged = new java.util.HashSet<String>();

    /**
     * States that an entity field is written by the persistence layer and
     * never from a DTO - the optimistic-lock version and the auditing
     * columns.
     *
     * The mapper's reverse direction copies every field the two sides share,
     * which hands the version and the audit trail to whoever sends the
     * request: a PUT could name its own lastModifiedBy, and a version copied
     * onto a loaded row is silently ignored by Hibernate, so the lock it
     * claims to take is not taken. The DTO still carries them outward - a
     * client needs the version to send it back - it just cannot write them.
     *
     * Nothing is system-managed until a rule says so, which leaves the
     * default output exactly as it was.
     */
    public SourceCode systemManaged(String entityFieldName) {
        systemManaged.add(entityFieldName);
        return this;
    }

    /** Whether a rule has taken the reverse write of this field away. */
    public boolean isSystemManaged(String entityFieldName) {
        return systemManaged.contains(entityFieldName);
    }

    private final java.util.Set<String> writtenAtCreation = new java.util.HashSet<String>();

    /**
     * States that the mapper writes this entity field through the entity's
     * creation writer rather than its setter: the field takes a request's
     * value when the entity is made, and is the entity's own after that
     * (ddd-behavior). Nothing is written that way until a rule says so.
     */
    public SourceCode writtenAtCreation(String entityFieldName) {
        writtenAtCreation.add(entityFieldName);
        return this;
    }

    /** What the mapper writes an entity field on: the entity, or its creation writer. */
    public String mapperTarget(String entityFieldName) {
        return writeTarget("to", entityFieldName);
    }

    /** What code holding an entity in {@code receiver} writes a field on. */
    public String writeTarget(String receiver, String entityFieldName) {
        return writtenAtCreation.contains(entityFieldName) ? receiver + ".creation()" : receiver;
    }

    // ---- class-level annotations (v3 clean mode) --------------------------

    private final java.util.List<AnnoCode> classAnnotations = new java.util.ArrayList<AnnoCode>();

    /** Adds a class-level annotation unless one of the same type exists. */
    public SourceCode ensureClassAnnotation(AnnoCode anno) {
        for (AnnoCode a : classAnnotations) {
            if (a.getFqcn().equals(anno.getFqcn())) return this;
        }
        classAnnotations.add(anno);
        if (Boolean.TRUE.equals(PROJECT_RULE.get())) projectAdded.add(anno);
        return this;
    }

    /**
     * Whether a project's own rule (not io.elasticore) is running. What it
     * adds or takes off the class is applied when the file is written, so a
     * project rule need not know when the file shell drew the annotations.
     */
    static final ThreadLocal<Boolean> PROJECT_RULE = new ThreadLocal<Boolean>();

    /** Set while a project's entity rule runs for the developer's class of a split root (RuleEngine). */
    static final ThreadLocal<Boolean> BORROWED_ENTITY_RULE = new ThreadLocal<Boolean>();

    private java.util.Set<String> activeRules = java.util.Collections.emptySet();

    /** The rules this artifact's run has (selected and not suppressed). */
    void activeRules(java.util.Set<String> rules) {
        this.activeRules = rules == null ? java.util.Collections.<String>emptySet() : rules;
    }

    /**
     * Whether a rule of this id runs for this artifact - for two rules that
     * share one piece of output (JPA's single @PreRemove), so each writes its
     * part only when the other is there to write the rest.
     */
    public boolean hasRule(String ruleId) {
        return activeRules.contains(ruleId);
    }

    private final java.util.List<AnnoCode> projectAdded = new java.util.ArrayList<AnnoCode>();
    private final java.util.List<String> projectRemoved = new java.util.ArrayList<String>();

    /** The class annotations a project rule added and nothing took off since. */
    private java.util.List<AnnoCode> projectAddedStill() {
        java.util.List<AnnoCode> out = new java.util.ArrayList<AnnoCode>();
        for (AnnoCode a : projectAdded) if (classAnnotations.contains(a)) out.add(a);
        return out;
    }

    private static final java.util.Set<String> REMOVAL_WARNED =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /** Forgets the warnings said so far: each generation run says its own. */
    public static void resetWarnings() {
        REMOVAL_WARNED.clear();
        DSL_WARNED.clear();
        DeclarationText.resetWarnings();
    }

    private static final java.util.Set<String> DSL_WARNED =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /**
     * A DSL warning said once per run: a rule runs for each artifact of an
     * entity (its DTO, its search DTO ...), and the same finding printed twice
     * reads as two problems.
     */
    public static void dslWarnOnce(String message) {
        if (DSL_WARNED.add(message)) io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", message);
    }

    // ---- method-level annotations (project rules) --------------------------

    private final java.util.Map<String, java.util.List<AnnoCode>> methodAnnotations =
            new java.util.LinkedHashMap<String, java.util.List<AnnoCode>>();

    /**
     * A method of this file by name, to put annotations on. For project
     * rules: what it asks for is placed on the written text, which the
     * 2.x-compatible output must never be - no built-in rule calls it.
     */
    public MethodCode method(String name) {
        return new MethodCode(this, name);
    }

    void addMethodAnnotation(String name, AnnoCode anno) {
        java.util.List<AnnoCode> list = methodAnnotations.get(name);
        if (list == null) methodAnnotations.put(name, list = new java.util.ArrayList<AnnoCode>());
        for (AnnoCode a : list) if (a.getFqcn().equals(anno.getFqcn())) return;
        list.add(anno);
    }

    /**
     * A project rule's class annotations, into lists a shell rendered before
     * it ran. False when the skeleton has no such list - a 2.x skeleton that
     * leaves the slot unset - and the annotations go onto the text instead.
     */
    private final java.util.Set<String> projectImports = new java.util.LinkedHashSet<String>();

    private boolean applyProjectAnnotations() {
        String list = vars.get("classAnnotationList");
        if (list == null) return false;
        java.util.List<String> lines = new java.util.ArrayList<String>(java.util.Arrays.asList(list.split("\n", -1)));
        if (lines.size() == 1 && lines.get(0).isEmpty()) lines.clear();
        for (String gone : projectRemoved) {
            java.util.Iterator<String> it = lines.iterator();
            while (it.hasNext()) if (it.next().trim().equals(gone.trim())) it.remove();
        }
        String imports = vars.get("importList");
        Syntax syntax = templateSet.syntax();
        for (AnnoCode a : projectAdded) {
            if (!classAnnotations.contains(a)) continue;
            String rendered = a.render(nameStyle());
            boolean present = false;
            for (String l : lines) if (l.trim().equals(rendered.trim())) present = true;
            if (!present) lines.add(rendered);
            // Whatever the skeleton does with its import list - a slot of its
            // own, or an import line it writes around the slot - the written
            // file gets the import (DeclarationText.ensureImports).
            if (rendered.startsWith("@" + a.simpleName())) a.collectTypes(projectImports);
            if (imports != null && rendered.startsWith("@" + a.simpleName()) && syntax.needsImport(a.getFqcn())) {
                String line = syntax.importLine(a.getFqcn());
                if (!imports.contains(line)) {
                    imports = imports.isEmpty() ? line : imports + (imports.endsWith("\n") ? "" : "\n") + line;
                }
            }
        }
        vars.put("classAnnotationList", String.join("\n", lines));
        if (imports != null) vars.put("importList", imports);
        return true;
    }

    /** Removes a class-level annotation by type. */
    public SourceCode removeClassAnnotation(String fqcn) {
        java.util.Iterator<AnnoCode> it = classAnnotations.iterator();
        while (it.hasNext()) {
            AnnoCode a = it.next();
            if (a.getFqcn().equals(fqcn)) {
                if (Boolean.TRUE.equals(PROJECT_RULE.get())) projectRemoved.add(a.render(nameStyle()));
                it.remove();
            }
        }
        return this;
    }

    /**
     * Takes off every annotation - on the class and on each field - whose type
     * the test accepts, and the member a field carries for a lifecycle
     * callback (@PrePersist and the like), which nothing would call once its
     * persistence's annotations are gone. The entity listeners go with
     * EntityListeners.
     *
     * For a persistence that is not JPA: the same field rules describe the
     * class, and what only JPA reads is taken off afterwards. A raw line - a
     * native annotation the DSL handed through - is tested by its marker.
     */
    public SourceCode removeAnnotations(java.util.function.Predicate<String> fqcn) {
        java.util.Iterator<AnnoCode> it = classAnnotations.iterator();
        while (it.hasNext()) {
            AnnoCode a = it.next();
            if (a.getFqcn() != null && fqcn.test(a.getFqcn())) it.remove();
        }
        if (fqcn.test(ENTITY_LISTENERS)) entityListeners.clear();
        // The types those annotations' values named (GenerationType, FetchType).
        java.util.Iterator<String> imports = extraImports.iterator();
        while (imports.hasNext()) {
            if (fqcn.test(imports.next())) imports.remove();
        }
        for (FieldCode fc : fieldCodes.values()) {
            java.util.Iterator<AnnoCode> f = fc.annotations().iterator();
            while (f.hasNext()) {
                AnnoCode a = f.next();
                if (a.getFqcn() != null && fqcn.test(a.getFqcn())) f.remove();
            }
            for (String line : fc.afterLines()) {
                if (line.trim().matches("@(Pre|Post)[A-Z][A-Za-z]*")) {
                    fc.removeAfter();
                    break;
                }
            }
        }
        return this;
    }

    public String renderClassAnnotations() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < classAnnotations.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(classAnnotations.get(i).render(nameStyle()));
        }
        return sb.toString();
    }

    private static final String ENTITY_LISTENERS = "jakarta.persistence.EntityListeners";

    private final java.util.List<String> entityListeners = new java.util.ArrayList<String>();

    /** The entity listeners registered so far, by simple class name. */
    public java.util.List<String> entityListeners() {
        return new java.util.ArrayList<String>(entityListeners);
    }

    /**
     * Registers one JPA entity listener, keeping whatever another rule
     * registered.
     *
     * @EntityListeners takes a list, so it is the one class annotation that
     * more than one rule has something to say about - the auditing listener
     * is a base concern and an invariant check is a domain one, and neither
     * knows the other exists. Written directly by each of them, the second
     * would either be dropped by ensureClassAnnotation or would silently
     * replace the first depending on which ran last.
     *
     * The annotation is rebuilt from the whole list on every call, so the
     * result does not depend on rule order. One listener renders as the bare
     * class literal that 2.x writes, since the compatibility output is
     * compared to it byte for byte; two or more render as a list.
     */
    public SourceCode addEntityListener(String simpleClassName) {
        if (simpleClassName == null || entityListeners.contains(simpleClassName)) return this;
        entityListeners.add(simpleClassName);

        StringBuilder listed = new StringBuilder();
        for (String listener : entityListeners) {
            if (listed.length() > 0) listed.append(", ");
            listed.append(syntax().classLiteral(listener));
        }

        // Java braces the list; Kotlin does not, because it takes a Java
        // annotation's array attribute as varargs and rejects an array
        // literal there - "inferred type is Array<KClass<out Any>> but
        // KClass<(raw) Any> was expected", which is the compiler saying the
        // brackets are one level too many.
        String value = entityListeners.size() == 1 || syntax() == Syntax.KOTLIN
                ? listed.toString()
                : String.format("{%s}", listed);

        removeClassAnnotation(ENTITY_LISTENERS);
        return ensureClassAnnotation(AnnoCode.raw(ENTITY_LISTENERS, "@EntityListeners(%s)", value));
    }

    java.util.List<AnnoCode> classAnnotations() {
        return classAnnotations;
    }

    // ---- import collection (clean mode) -----------------------------------

    /**
     * Fully qualified types actually used by the surviving annotations of
     * this artifact, sorted. Removed fields, removed annotations and
     * suppressed rules leave no trace here - the foundation of a
     * used-imports-only rendering mode. The compatibility mode (template
     * import block, identical to 2.x) remains the default.
     */
    /** Imports registered explicitly by rules (listener types, enum types...). */
    private final java.util.TreeSet<String> extraImports = new java.util.TreeSet<String>();

    /** Registers a type the artifact references outside of annotations. */
    public SourceCode addImport(String fqcn) {
        if (fqcn != null && fqcn.lastIndexOf('.') > 0) extraImports.add(fqcn);
        return this;
    }

    /** Registers a type built from a format; never concatenate FQCN strings. */
    public SourceCode addImport(String format, Object... args) {
        return addImport(Formatted.text(format, args));
    }

    /**
     * Every fully qualified type this artifact refers to, before deciding
     * which of them may be written by simple name.
     */
    private java.util.Set<String> referencedTypes() {
        java.util.TreeSet<String> types = new java.util.TreeSet<String>(extraImports);
        java.util.List<AnnoCode> all = new java.util.ArrayList<AnnoCode>(classAnnotations);
        for (FieldCode fc : fieldCodes.values()) {
            if (fc.isRemoved()) continue;
            all.addAll(fc.annotations());
        }
        if (templateSet == TemplateSet.V3) {
            for (FieldCode fc : fieldCodes.values()) {
                if (fc.isRemoved()) continue;
                types.addAll(FieldCode.declaredTypes(fc.declaredTypeName()));
            }
        }
        for (AnnoCode a : all) {
            String fqcn = a.getFqcn();
            if (fqcn == null) continue;
            int dot = fqcn.lastIndexOf('.');
            if (dot <= 0) continue;                   // no package
            // Raw markers ("native:@Version", "native:@Convert(...)") are
            // identities, not importable types.
            if (fqcn.indexOf('@') >= 0 || fqcn.indexOf('(') >= 0
                    || fqcn.indexOf(' ') >= 0 || fqcn.indexOf(':') >= 0) continue;
            if (fqcn.startsWith("java.lang.")) continue;
            types.add(fqcn);
        }
        return types;
    }

    /** Simple name -> the types claiming it. */
    private java.util.Map<String, java.util.Set<String>> bySimpleName() {
        java.util.Map<String, java.util.Set<String>> map =
                new java.util.LinkedHashMap<String, java.util.Set<String>>();
        for (String fqcn : referencedTypes()) {
            String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
            java.util.Set<String> owners = map.get(simple);
            if (owners == null) {
                owners = new java.util.TreeSet<String>();
                map.put(simple, owners);
            }
            owners.add(fqcn);
        }
        return map;
    }

    /**
     * How this artifact writes type names. A simple name stands for one type
     * per file, so a name two packages claim - a Views or a Q of two domains,
     * a Parameter of Hibernate and of Swagger - is written in full by both,
     * and imported by neither.
     */
    public AnnoCode.NameStyle nameStyle() {
        final java.util.Set<String> contested = new java.util.HashSet<String>();
        for (java.util.Map.Entry<String, java.util.Set<String>> en : bySimpleName().entrySet()) {
            if (en.getValue().size() > 1) contested.add(en.getKey());
        }
        final java.util.Set<String> shadowing = shadowingAnnotations();
        // The clean mode imports what 2.x spelled out in full; the
        // compatibility mode writes what its rules asked for.
        final boolean clean = templateSet == TemplateSet.V3;
        final Syntax syntax = templateSet.syntax();
        return new AnnoCode.NameStyle() {
            public Syntax syntax() {
                return syntax;
            }

            public boolean useSimpleName(String fqcn, boolean ruleAsked) {
                if (fqcn == null) return true;
                int dot = fqcn.lastIndexOf('.');
                if (dot <= 0) return true;
                if (contested.contains(fqcn.substring(dot + 1))) return false;
                if (shadowing.contains(fqcn)) return false;
                return clean || !ruleAsked;
            }

            public boolean simplifiesDeclaredTypes() {
                return clean;
            }
        };
    }

    /**
     * The types this artifact imports: those it may write by simple name.
     * A contested name is spelled out where it is used instead.
     */
    public java.util.List<String> usedImports() {
        java.util.Set<String> shadowing = shadowingAnnotations();
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (java.util.Map.Entry<String, java.util.Set<String>> en : bySimpleName().entrySet()) {
            if (en.getValue().size() > 1) continue;
            for (String fqcn : en.getValue()) {
                if (!shadowing.contains(fqcn)) out.add(fqcn);
            }
        }
        java.util.Collections.sort(out);
        return out;
    }

    /** Hibernate's column comment, which every field with a "-- label" gets. */
    private static final String HIBERNATE_COMMENT = "org.hibernate.annotations.Comment";

    /**
     * The annotation types this artifact writes whose simple name is also a
     * type of the domain - Hibernate's Comment where the domain has an entity
     * named Comment. The domain's type is what the simple name has to mean in
     * a generated file: an import of the annotation would shadow an entity of
     * the same package, and a wildcard import would lose to it. So these are
     * written in full and not imported.
     *
     * Only annotations the name style writes count: a raw line keeps the
     * simple name its rule wrote and so keeps its import - and so does every
     * other use of a type that is also written as a raw line in this file,
     * since the import serves both. The compatibility
     * mode does for Comment alone what the 2.x publisher does, and its output
     * stays the 2.x output. A domain without such a name gets nothing here,
     * and its files do not change.
     */
    private java.util.Set<String> shadowingAnnotations() {
        java.util.Set<String> out = new java.util.HashSet<String>();
        java.util.Set<String> names = entity.modelTypeNames();
        java.util.List<AnnoCode> all = new java.util.ArrayList<AnnoCode>(classAnnotations);
        for (FieldCode fc : fieldCodes.values()) {
            if (fc.isRemoved()) continue;
            all.addAll(fc.annotations());
        }
        java.util.Set<String> types = new java.util.HashSet<String>();
        java.util.Set<String> raw = new java.util.HashSet<String>();
        for (AnnoCode a : all) {
            a.collectStyledTypes(types);
            a.collectRawTypes(raw);
        }
        boolean compatible = templateSet == TemplateSet.COMPATIBLE;
        for (String fqcn : types) {
            if (fqcn == null || raw.contains(fqcn)) continue;
            if (compatible && !HIBERNATE_COMMENT.equals(fqcn)) continue;
            int dot = fqcn.lastIndexOf('.');
            if (dot <= 0) continue;
            if (names.contains(fqcn.substring(dot + 1))) out.add(fqcn);
        }
        return out;
    }

    /**
     * Renders the used imports as an import block, one statement per line.
     *
     * A type the language already has in scope is left out: importing it is
     * at best noise and at worst wrong, since Kotlin's own List is not
     * java.util.List and naming both makes the name ambiguous.
     */
    public String renderImports() {
        Syntax syntax = templateSet.syntax();
        StringBuilder sb = new StringBuilder();
        for (String fqcn : usedImports()) {
            if (!syntax.needsImport(fqcn)) continue;
            sb.append(syntax.importLine(fqcn)).append('\n');
        }
        return sb.toString();
    }
}
