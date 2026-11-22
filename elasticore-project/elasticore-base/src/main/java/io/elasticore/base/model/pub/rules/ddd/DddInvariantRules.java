package io.elasticore.base.model.pub.rules.ddd;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.ErrorCatalog;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * What the aggregate promises about itself, checked where it cannot be evaded.
 *
 * The boundary rules say who may write to an aggregate. They do not say what
 * the aggregate is allowed to be once written, and that is the other half of
 * why a boundary exists: an order with no lines is not a smaller order, it is
 * not an order. Evans calls the root the thing that keeps the invariant; up
 * to here the generated root had nothing to keep it with.
 *
 *   Order:
 *     meta: entity @root @invariant(items.size > 0, 'an order has at least one line')
 *
 * Written more than once for more than one promise. The condition is read,
 * not evaluated: this is a small grammar of field-and-comparison, deliberately
 * not an expression language. A generator that accepts arbitrary expressions
 * has to parse them, and a parser that is nearly right is worse than a form
 * that is obviously limited - the DSL would start looking like a place to put
 * logic, which is the one thing it is not.
 *
 * <h3>Where the check runs</h3>
 *
 * At {@code @PrePersist} and {@code @PreUpdate}, so it runs when the row is
 * written and on every path that writes one. Chapter 5 sketched calling it at
 * the end of each state-changing method instead; that was written before
 * there were generated state-changing methods to speak of, and it would have
 * covered only the ones this generator happens to own. A caller that sets a
 * field through a setter, or a service that changes two things and saves
 * once, would have walked past it. The persistence callback is the same
 * argument the domain events made and won: saving is the moment the aggregate
 * becomes a fact, so it is the moment to insist it is a valid one.
 *
 * <h3>Why a listener rather than a method on the root</h3>
 *
 * JPA allows one callback method per event per class, and the root already
 * spends its {@code @PrePersist} on generating an id. A second one is not a
 * warning - the persistence unit refuses to start, which is how this was
 * found: every test in the sample failed at context load, none of them at an
 * assertion. So the check is a class of its own and is named in
 * {@code @EntityListeners}, which takes a list precisely because more than
 * one concern wants to watch the same events.
 *
 * The failure is an {@link IllegalStateException} and not a validation
 * message, because this is not user input being rejected - it is the domain
 * saying it has been put into a state it promised never to be in. Bean
 * Validation is the right tool for the other one and is already generated
 * from @length and friends.
 *
 * <h3>A member leaving is a write to its root</h3>
 *
 * Running on the root's write alone meant not running when there was no write
 * on the root: removing an aggregate's last member deletes the member's row
 * and leaves the root's, so the promise could be broken by subtraction. It
 * was stated as a limitation and held down by a test.
 *
 * Closed by a second listener, on each member of a root that promises
 * anything: when a member row is deleted, the root's promises are checked on
 * what the root still holds. It runs at flush - after a replace has refilled
 * the collection - so swapping every line at once is still allowed, which is
 * why the check could not live in the remove method itself. The remove
 * method keeps the member's reference to its root so the listener can find
 * it; the row is deleted at the same flush.
 *
 * Promises are declared on roots only: a member has no promise of its own to
 * keep - the boundary took that away. Java and Kotlin; TypeScript gets the
 * question, not the enforcement (see ts-model).
 */
public class DddInvariantRules implements CodeRules,
        io.elasticore.base.model.pub.rules.ArtifactContributor {

    private static final String JPA = "jakarta.persistence.%s";

    /** The listener class, beside the aggregate whose promises it holds. */
    @Override
    public List<io.elasticore.base.model.pub.rules.ContributedArtifact> artifactsFor(EntitySpec e) {
        if (ownerField(e) != null) {
            return Collections.singletonList(
                    io.elasticore.base.model.pub.rules.ContributedArtifact
                            .of("removalCheck", "entity", removalCheckName(e)));
        }
        if (!declaresInvariants(e)) {
            return Collections.<io.elasticore.base.model.pub.rules.ContributedArtifact>emptyList();
        }
        return Collections.singletonList(
                io.elasticore.base.model.pub.rules.ContributedArtifact
                        .of("invariants", "entity", listenerName(e)));
    }

    /**
     * The root names the listener that watches it.
     *
     * Ordered before the file shell renders the class annotations and the
     * import block at 90.
     */
    @Rule(value = "ddd-invariant-listener", target = "entity", order = 85, phase = Rule.Phase.TAIL)
    void nameTheListener(EntitySpec e) {
        SourceCode c = ctx(e);
        if (ownerField(e) != null && (c.syntax() == Syntax.JAVA || c.syntax() == Syntax.KOTLIN)) {
            c.addEntityListener(removalCheckName(e));
            return;
        }
        if (!declaresInvariants(e)) return;

        c.addEntityListener(listenerName(e));
    }

    /**
     * Whether this rule set writes the root's @PreRemove (markBeingRemoved):
     * then the lifecycle's delete check is called from it, since JPA allows
     * one @PreRemove per class.
     */
    public static boolean marksRemoval(EntitySpec e) {
        if (e.hasMeta("abstract") || e.hasMeta("embeddable")) return false;
        if (!(e.aggregateRoot() && (e.hasMeta("invariant") || hasFieldBounds(e)))) return false;
        return !(e.parentName() != null && e.rootHasInvariants(e.parentName()));
    }

    /**
     * Whether this root declares any promise at all.
     *
     * The listener class is generated whenever it does - even if no condition
     * could be read, when its check is empty - because a member's removal
     * check calls it by name, and a name that exists only sometimes is a
     * compile error in the file that trusted it.
     */
    private boolean declaresInvariants(EntitySpec e) {
        // An @abstract parent is no aggregate of its own: its bounds are
        // checked on each entity that extends it, which inherits the fields.
        if (e.hasMeta("abstract") || e.hasMeta("embeddable")) return false;
        return e.aggregateRoot() && (e.hasMeta("invariant") || hasFieldBounds(e));
    }

    /**
     * Whether a field of this entity declares a numeric bound (@min / @max).
     *
     * A bound on a field is a rule about the aggregate like any other. The
     * DTO checks it on the way in (400), but a value the domain computes - a
     * stock taken below zero by a shipment - never passes through a DTO. So a
     * root's bounds are checked where its @invariant conditions are, whenever
     * the row is written, and a rule about one field is written on that field.
     */
    static boolean hasFieldBounds(EntitySpec e) {
        for (FieldSpec f : e.fields()) {
            if (f.isList()) {
                if (f.dslValueAny("minsize", "maxsize") != null) return true;
                continue;
            }
            if (f.dslValueAny("min", "minval", "minvalue") != null
                    || f.dslValueAny("max", "maxval", "maxvalue") != null) return true;
        }
        return false;
    }

    /**
     * The root says when it is being deleted.
     *
     * Its members go with it by cascade, and each member's removal check
     * would otherwise ask the root to keep a promise it no longer needs to -
     * refusing, for example, to delete an order whose last line was removed
     * in the same transaction, or a row stored before the promise existed.
     * JPA calls the root's @PreRemove before it cascades to the members, so
     * the flag is set by the time they ask. Class-body lines, after the shell
     * has rendered the field block (order 90).
     */
    /** The rule that writes the root's @PreRemove; the lifecycle's delete check is called from it. */
    public static final String REMOVING_RULE = "ddd-invariant-root-removing";

    @Rule(value = REMOVING_RULE, target = "entity", order = 96, phase = Rule.Phase.TAIL)
    void rootSaysWhenItIsDeleted(EntitySpec e) {
        if (!declaresInvariants(e)) return;
        // Declared once, on the topmost root that promises: a subclass
        // redeclaring them would clash in Kotlin (no override) and shadow in
        // Java, and JPA runs the inherited @PreRemove for it anyway.
        if (e.parentName() != null && e.rootHasInvariants(e.parentName())) return;
        SourceCode c = ctx(e);
        if (c.syntax() == Syntax.KOTLIN) {
            c.varLine("fieldList", "")
             .varLine("fieldList", "/** Set while this root is being deleted: members leaving with it are not a change to it. */")
             .varLine("fieldList", "@jakarta.persistence.Transient")
             .varLine("fieldList", "private var beingRemoved: Boolean = false")
             .varLine("fieldList", "")
             .varLine("fieldList", "/** Whether this root is being deleted. Read by its members' removal checks. */")
             .varLine("fieldList", "fun isBeingRemoved(): Boolean = beingRemoved")
             .varLine("fieldList", "")
             .varLine("fieldList", "@jakarta.persistence.PreRemove")
             .varLine("fieldList", "fun markBeingRemoved() {");
            // The lifecycle's delete check shares the one @PreRemove a class may have.
            if (DddStateRules.guardsDelete(e) && c.hasRule(DddStateRules.DELETE_RULE)) c.varLine("fieldList", "    checkDeletable()");
            c.varLine("fieldList", "    beingRemoved = true")
             .varLine("fieldList", "}")
             .varLine("fieldList", "")
             .varLine("fieldList", "/** Written again after a remove: it is staying after all. */")
             .varLine("fieldList", "fun clearBeingRemoved() {")
             .varLine("fieldList", "    beingRemoved = false")
             .varLine("fieldList", "}");
        } else if (c.syntax() == Syntax.JAVA) {
            c.varLine("fieldList", "")
             .varLine("fieldList", "/** Set while this root is being deleted: members leaving with it are not a change to it. */")
             .varLine("fieldList", "@jakarta.persistence.Transient")
             .varLine("fieldList", "@lombok.Setter(lombok.AccessLevel.NONE)")
             .varLine("fieldList", "private transient boolean beingRemoved;")
             .varLine("fieldList", "")
             .varLine("fieldList", "/** Whether this root is being deleted. Read by its members' removal checks. */")
             .varLine("fieldList", "public boolean isBeingRemoved() {")
             .varLine("fieldList", "    return this.beingRemoved;")
             .varLine("fieldList", "}")
             .varLine("fieldList", "")
             .varLine("fieldList", "@jakarta.persistence.PreRemove")
             .varLine("fieldList", "void markBeingRemoved() {");
            // The lifecycle's delete check shares the one @PreRemove a class may have.
            if (DddStateRules.guardsDelete(e) && c.hasRule(DddStateRules.DELETE_RULE)) c.varLine("fieldList", "    checkDeletable();");
            c.varLine("fieldList", "    this.beingRemoved = true;")
             .varLine("fieldList", "}")
             .varLine("fieldList", "")
             .varLine("fieldList", "/** Written again after a remove: it is staying after all. */")
             .varLine("fieldList", "void clearBeingRemoved() {")
             .varLine("fieldList", "    this.beingRemoved = false;")
             .varLine("fieldList", "}");
        } else {
            return;
        }
    }

    /**
     * On a member: the root's promises, checked when a member row goes.
     *
     * The root's invariants class is reused rather than restated, so there is
     * one place a promise is written and two moments it is asked.
     */
    @Rule(value = "ddd-invariant-removal", target = "removalCheck", order = 10)
    void removalCheck(EntitySpec e) {
        SourceCode c = ctx(e);
        // A browser deletes no rows; the TypeScript model gets the question
        // (ts-model's invariant check), not a listener.
        if (c.syntax() == Syntax.TYPESCRIPT) {
            c.dontMake();
            return;
        }
        c.requireSyntaxAnyOf("ddd-invariant-removal", Syntax.JAVA, Syntax.KOTLIN);
        c.template("invariants");

        boolean kotlin = c.syntax() == Syntax.KOTLIN;
        String member = e.getName();
        String root = e.ownAggregate();
        String owner = ownerField(e);

        c.addImport(JPA, "PreRemove");
        c.varLine("bodyList", "/**")
         .varLine("bodyList", " * A %s leaving is a change to its %s: what the %s promises is", member, root, root)
         .varLine("bodyList", " * checked on what it still holds, when the row is deleted - after a")
         .varLine("bodyList", " * replace has refilled it, so swapping every member at once is allowed.")
         .varLine("bodyList", " */")
         .varLine("bodyList", "@PreRemove");
        // The root may be a lazy proxy whose own fields are empty - Kotlin's
        // final property getters are not intercepted - so it is unproxied
        // before its promises are read.
        if (kotlin) {
            c.varLine("bodyList", "fun leaving(member: %s?) {", member)
             .varLine("bodyList", "    val owner = member?.%s ?: return", owner)
             .varLine("bodyList", "    val root = io.elasticore.springboot3.entity.Proxies.real(owner, %s::class.java)", root)
             .varLine("bodyList", "    if (root.isBeingRemoved()) return")
             .varLine("bodyList", "    %sInvariants().check(root)", root)
             .varLine("bodyList", "}");
        } else {
            String read = "member.get" + io.elasticore.base.model.pub.rules.base.RuleText.capital(owner) + "()";
            c.varLine("bodyList", "void leaving(%s member) {", member)
             .varLine("bodyList", "    if (member == null || %s == null) return;", read)
             .varLine("bodyList", "    %s root = io.elasticore.springboot3.entity.Proxies.real(%s, %s.class);", root, read, root)
             .varLine("bodyList", "    if (root.isBeingRemoved()) return;")
             .varLine("bodyList", "    new %sInvariants().check(root);", root)
             .varLine("bodyList", "}");
        }

        c.var("packageName", e.pkg("entity"))
         .var("className", removalCheckName(e))
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("importList", io.elasticore.base.model.pub.rules.base.RuleText.stripTrailing(c.renderImports()));
    }

    /**
     * The member's field that holds its root, when this entity is a member of
     * a root that promises something - or null.
     */
    private String ownerField(EntitySpec e) {
        if (e.aggregateRoot() || e.hasMeta("embeddable")) return null;
        String root = e.ownAggregate();
        if (root == null || root.equals(e.getName()) || !e.rootHasInvariants(root)) return null;
        String owner = e.ownerFieldOf(e.getName());
        if (owner == null && warned.add(e.domainClassName() + "." + e.getName())) {
            // Said rather than silently skipped: without a reference back to
            // its root, a member's removal cannot be checked against it.
            io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                    "%s is a member of %s, which declares @invariant, but has no field pointing back at it;"
                            + " removing a %s cannot be checked against %s's promises. Map the relation"
                            + " both ways (e.g. %s @ManyToOne on %s, mappedBy on %s).",
                    e.getName(), root, e.getName(), root, root, e.getName(), root));
        }
        return owner;
    }

    /** Said once per run of this rule set, per domain and member - not once per JVM. */
    private final java.util.Set<String> warned = new java.util.HashSet<String>();

    private String removalCheckName(EntitySpec e) {
        return e.getName() + "RemovalCheck";
    }

    /**
     * The listener: every promise this aggregate makes, checked on the two
     * events that write a row.
     */
    @Rule(value = "ddd-invariant", target = "invariants", order = 10)
    void invariantListener(EntitySpec e) {
        SourceCode c = ctx(e);
        c.requireSyntaxAnyOf("ddd-invariant", Syntax.JAVA, Syntax.KOTLIN, Syntax.TYPESCRIPT);

        if (c.syntax() == Syntax.TYPESCRIPT) {
            typescriptInvariants(c, e);
            return;
        }

        boolean kotlin = c.syntax() == Syntax.KOTLIN;
        String root = e.getName();
        String var = e.varName();
        List<Invariant> invariants = invariantsOf(e, var, c.syntax());

        c.addImport(JPA, "PrePersist");
        c.addImport(JPA, "PreUpdate");

        c.varLine("bodyList", "/**")
         .varLine("bodyList", " * What %s promises is always true of it.", root)
         .varLine("bodyList", " *")
         .varLine("bodyList", " * Checked when the row is written rather than at the end of each")
         .varLine("bodyList", " * change, so no path that writes one can walk past it.")
         .varLine("bodyList", " */")
         .varLine("bodyList", "@PrePersist")
         .varLine("bodyList", "@PreUpdate")
         .varLine("bodyList",
                 kotlin ? "fun check(%2$s: %1$s?) {" : "void check(%1$s %2$s) {", root, var)
         .varLine("bodyList",
                 kotlin ? "    if (%s == null) return" : "    if (%s == null) return;", var);
        // Written again - persisted after a remove, in the same persistence
        // context - so it is no longer on its way out, and its members'
        // removal checks apply again.
        c.varLine("bodyList", kotlin ? "    %s.clearBeingRemoved()" : "    %s.clearBeingRemoved();", var);

        for (Invariant invariant : invariants) {
            c.varLine("bodyList", "    if (%s) {", invariant.violation);
            // Still an IllegalStateException, but one the API can recognize:
            // a broken promise is the request's fault (422), not the program's.
            // Its values, {0} the field and {1} the entity, are said by their
            // labels in the response's language.
            c.varLine(
                    "bodyList",
                    kotlin
                            ? "        throw io.elasticore.springboot3.validation.InvariantViolationException(\"%s\", \"%s\", %s, %s)"
                            : "        throw new io.elasticore.springboot3.validation.InvariantViolationException(\"%s\", \"%s\", %s, %s);",
                    literal(invariant.code, kotlin),
                    literal(invariant.message, kotlin),
                    label(invariant.field, kotlin),
                    label(e.getName(), kotlin));
            ErrorCatalog.record(invariant.code, invariant.sentence);
            c.varLine("bodyList", "    }");
        }

        c.varLine("bodyList", "}");

        c.var("packageName", e.pkg("entity"))
         .var("className", listenerName(e))
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("importList", io.elasticore.base.model.pub.rules.base.RuleText
                 .stripTrailing(c.renderImports()));
    }

    /**
     * The same promises, asked before the request is sent.
     *
     * Not enforcement, and the generated file says so. The server refuses to
     * write an aggregate that breaks its promise and that is what makes the
     * promise true; a browser cannot be trusted with it and is not being
     * asked to be. What this buys is the other thing - telling someone their
     * order needs a line while they are still looking at the form, in the
     * domain's own words rather than a message written twice.
     *
     * A list of what is broken rather than a thrown error, because a form
     * wants to show all of them at once and an exception gives you the first.
     */
    private void typescriptInvariants(SourceCode c, EntitySpec e) {
        String dto = e.dtoClassName();
        String var = e.varName();
        List<Invariant> invariants = invariantsOf(e, var, Syntax.TYPESCRIPT);

        c.var("className", dto + " invariants")
         .var("importList", c.syntax().importLine(dto) + "\n");

        c.varLine("bodyList", "/**")
         .varLine("bodyList", " * What %s promises about itself, checked here as a courtesy.", dto)
         .varLine("bodyList", " *")
         .varLine("bodyList", " * The server enforces these when the row is written - that is what")
         .varLine("bodyList", " * makes them true. This is so a form can say what is wrong before")
         .varLine("bodyList", " * the request, in the domain's own words.")
         .varLine("bodyList", " *")
         .varLine("bodyList", " * @returns the promises this value breaks, empty when it keeps them")
         .varLine("bodyList", " */")
         .varLine("bodyList", "export function violations(%s: %s | null | undefined): string[] {",
                 var, dto)
         .varLine("bodyList", "    const broken: string[] = [];")
         .varLine("bodyList", "    if (%s == null) return broken;", var);

        for (Invariant invariant : invariants) {
            c.varLine("bodyList", "    if (%s) {", invariant.violation)
             .varLine("bodyList", "        broken.push('%s');", invariant.message.replace("\\", "\\\\").replace("'", "\\'"))
             .varLine("bodyList", "    }");
        }

        c.varLine("bodyList", "    return broken;")
         .varLine("bodyList", "}")
         .varLine("bodyList", "")
         .varLine("bodyList", "/** Whether %s keeps every promise it makes. */", dto)
         .varLine("bodyList", "export function isValid(%s: %s | null | undefined): boolean {",
                 var, dto)
         .varLine("bodyList", "    return violations(%s).length === 0;", var)
         .varLine("bodyList", "}");
    }

    private String listenerName(EntitySpec e) {
        return e.getName() + "Invariants";
    }

    /** One promise: the condition that breaks it, and what to say when it does. */
    private static final class Invariant {
        private final String violation;
        /** What is said where no bundle has the code: the DSL's sentence, or one naming the field. */
        private final String message;
        /** The message code a translation is found by: invariant.<Entity>.<what>. */
        private final String code;
        /** The label bundle's key of the field the promise is about ({0}): Order.total, or the entity's. */
        private final String field;
        /** The bundle's default sentence: the DSL's as written, or a pattern naming {0} the field, {1} the entity. */
        private final String sentence;
        /** The condition as @invariant declares it; null for a field's bound (@min, @max, @minsize, @maxsize). */
        private String declared;

        private Invariant(String violation, String message, String code, String field, String sentence) {
            this.violation = violation;
            this.message = message;
            this.code = code;
            this.field = field;
            this.sentence = sentence;
        }

        Invariant coded(String other) {
            Invariant copy = new Invariant(violation, message, other, field, sentence);
            copy.declared = declared;
            return copy;
        }
    }

    /**
     * The conditions the root's @invariant declares that the listener checks, in order, each with the code a
     * refusal carries: {condition, code}. The bounds of its fields are left out - a request is refused for those
     * by the DTO (400) before the root is written. For the contract tests (testCode: contract).
     */
    public static List<String[]> declaredPromises(EntitySpec e) {
        List<String[]> out = new ArrayList<String[]>();
        for (Invariant i : invariantsOf(e, e.varName(), Syntax.JAVA)) {
            if (i.declared != null) out.add(new String[] {i.declared, i.code});
        }
        return out;
    }

    /** The runtime's Label of a key of the label bundle: Order, Order.total. */
    static String label(String key, boolean kotlin) {
        return "io.elasticore.springboot3.validation.Label.of(\"" + literal(key, kotlin) + "\")";
    }

    /**
     * The label bundle's key of a field: Type.field, by the type that
     * declares it - an inherited field is its parent's.
     */
    static String fieldKey(EntitySpec e, FieldSpec f) {
        String type = f.isParentField() && e.parentName() != null ? e.parentName() : e.getName();
        return type + "." + f.name();
    }

    /** A word of a condition that may name a field. */
    private static final java.util.regex.Pattern WORD = java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** The key of the first field a condition reads (items.size > 0: items); the entity's when it reads none. */
    private static String fieldKeyOf(EntitySpec e, String condition) {
        String code = condition.replaceAll("'[^']*'|\"[^\"]*\"", " ");
        java.util.regex.Matcher m = WORD.matcher(code);
        while (m.find()) {
            for (FieldSpec f : e.fields()) {
                if (f.name().equals(m.group())) return fieldKey(e, f);
            }
        }
        return e.getName();
    }

    /**
     * Text as a MessageFormat pattern says it literally: an apostrophe
     * doubled; text with a brace quoted whole (quoting each brace apart would
     * join two neighbours into an apostrophe: '}''{').
     */
    static String literalPattern(String text) {
        String doubled = text.replace("'", "''");
        return text.indexOf('{') < 0 && text.indexOf('}') < 0 ? doubled : "'" + doubled + "'";
    }

    /**
     * The promises this entity declares, in order, skipping any this rule set
     * cannot read.
     *
     * An unreadable condition is dropped with a comment rather than failing
     * the run, for the same reason a member's event declaration is ignored:
     * one mistyped annotation should not stop a domain generating. It is not
     * silent either - the run says so in the generated source, where the
     * developer who wrote the annotation will see it.
     */
    private static List<Invariant> invariantsOf(EntitySpec e, String var, Syntax syntax) {
        List<Invariant> out = new ArrayList<Invariant>();
        if (!e.aggregateRoot()) return out;

        // The field bounds first: they read one value each, and a violated one
        // is the plainer thing to report.
        for (FieldSpec f : e.fields()) {
            if (f.isList()) {
                addSizeBounds(out, e, f, var, syntax);
                continue;
            }
            addBound(out, e, f, f.dslValueAny("min", "minval", "minvalue"), "<", "at least", "min", var, syntax);
            addBound(out, e, f, f.dslValueAny("max", "maxval", "maxvalue"), ">", "at most", "max", var, syntax);
        }
        if (e.hasMeta("invariant")) {
            for (String declared : e.metaValues("invariant")) {
                Invariant invariant = parse(e, declared, var, syntax);
                if (invariant != null) out.add(invariant);
            }
        }
        // Two promises never share a code: the second of a pair is numbered,
        // so its translation is its own.
        java.util.Set<String> codes = new java.util.HashSet<String>();
        for (int i = 0; i < out.size(); i++) {
            Invariant inv = out.get(i);
            String code = inv.code;
            for (int n = 2; !codes.add(code); n++) code = inv.code + "-" + n;
            if (!code.equals(inv.code)) out.set(i, inv.coded(code));
        }
        return out;
    }

    /**
     * How many members a collection holds: {@code lines: List<Line> @minsize(1, '...')}.
     *
     * A minimum is broken by a root with no collection at all - it holds
     * none - so it is checked the way an @invariant is, as a promise; a
     * maximum is not, since nothing is fewer than any number.
     */
    private static void addSizeBounds(List<Invariant> out, EntitySpec e, FieldSpec f, String var, Syntax syntax) {
        String min = f.dslValueAny("minsize");
        if (min != null) {
            String[] bound = io.elasticore.base.model.pub.rules.base.DtoBaseRules.Bound.split(min);
            int least;
            try {
                least = Integer.parseInt(bound[0].trim());
            } catch (NumberFormatException ex) {
                return;
            }
            // The condition grammar compares with > and <: at least n is more than n - 1.
            Invariant i = parse(e, String.format("%s.size > %d, 'x'", f.name(), least - 1), var, syntax);
            if (i != null) out.add(bounded(e, f, i.violation, bound, "must hold at least", "minsize"));
        }
        String max = f.dslValueAny("maxsize");
        if (max != null) {
            String[] bound = io.elasticore.base.model.pub.rules.base.DtoBaseRules.Bound.split(max);
            String violation = DddCondition.expression(e, f.name() + ".size > " + bound[0], var, false, syntax);
            if (violation == null) return;
            out.add(bounded(e, f, violation, bound, "must hold at most", "maxsize"));
        }
    }

    /**
     * A bound of one field: the DSL's sentence as written, else "<field>
     * <words> <bound>" - in the bundle with {0} for the field, which the
     * response says by its label.
     */
    private static Invariant bounded(
            EntitySpec e, FieldSpec f, String violation, String[] bound, String words, String kind) {
        String code = codeOf(e, f.name() + "." + kind);
        if (bound[1] != null) {
            String said = bound[1].replace("\"", "'");
            return new Invariant(violation, said, code, fieldKey(e, f), said);
        }
        return new Invariant(
                violation,
                String.format("%s %s %s", f.name(), words, bound[0]),
                code,
                fieldKey(e, f),
                "{0} " + literalPattern(words + " " + bound[0]));
    }

    private static void addBound(
            List<Invariant> out,
            EntitySpec e,
            FieldSpec f,
            String declared,
            String violatingOperator,
            String words,
            String kind,
            String var,
            Syntax syntax) {
        if (declared == null) return;
        String[] bound = io.elasticore.base.model.pub.rules.base.DtoBaseRules.Bound.split(declared);
        // Null is not a breach here: whether the value is required is said by
        // '!', and a missing value has nothing to compare.
        String violation = DddCondition.expression(e, f.name() + " " + violatingOperator + " " + bound[0],
                var, false, syntax);
        if (violation == null) return;
        out.add(bounded(e, f, violation, bound, "must be " + words, kind));
    }

    /**
     * Reads one declaration: a condition, and optionally the sentence to
     * raise when it does not hold.
     *
     * The message is separated by the last comma rather than the first, so a
     * condition may contain one and a message certainly may.
     */
    private static Invariant parse(EntitySpec e, String declared, String var, Syntax syntax) {
        if (declared == null) return null;

        String text = declared.trim();
        if (text.isEmpty()) return null;

        String condition = text;
        String message = null;

        int quote = firstQuote(text);
        if (quote > 0) {
            int comma = text.lastIndexOf(',', quote);
            if (comma > 0) {
                condition = text.substring(0, comma).trim();
                message = unquote(text.substring(comma + 1).trim());
            }
        }

        String violation = violationOf(e, condition, var, syntax);
        if (violation == null) return null;

        String code = codeOf(e, ErrorCatalog.slug(condition));
        String field = fieldKeyOf(e, condition);
        Invariant read;
        if (message == null || message.isEmpty()) {
            String written = condition.replace("\"", "'");
            read = new Invariant(
                    violation,
                    String.format("%s violates %s", e.getName(), written),
                    code,
                    field,
                    "{1} violates " + literalPattern(written));
        } else {
            String said = message.replace("\"", "'");
            read = new Invariant(violation, said, code, field, said);
        }
        read.declared = condition;
        return read;
    }

    /**
     * Text for a string literal: a backslash stays a backslash, and in Kotlin
     * a dollar stays a dollar rather than starting a template. (Double quotes
     * were made single where the message was read.)
     */
    private static String literal(String text, boolean kotlin) {
        String s = text.replace("\\", "\\\\");
        return kotlin ? s.replace("$", "\\$") : s;
    }

    private static String codeOf(EntitySpec e, String what) {
        return "invariant." + e.getName() + "." + what;
    }

    /** The condition turned round: true exactly when the promise is broken. */
    private static String violationOf(EntitySpec e, String condition, String var, Syntax syntax) {
        return DddCondition.expression(e, condition, var, true, syntax);
    }

    private static int firstQuote(String text) {
        int single = text.indexOf('\'');
        int doubleQuote = text.indexOf('"');
        if (single < 0) return doubleQuote;
        if (doubleQuote < 0) return single;
        return Math.min(single, doubleQuote);
    }

    private static String unquote(String text) {
        if (text.length() < 2) return text;

        char first = text.charAt(0);
        char last = text.charAt(text.length() - 1);
        if ((first == '\'' || first == '"') && first == last) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }
}
