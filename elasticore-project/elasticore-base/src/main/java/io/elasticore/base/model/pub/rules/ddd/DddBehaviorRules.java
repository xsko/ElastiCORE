package io.elasticore.base.model.pub.rules.ddd;

import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.ArtifactContributor;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.ContributedArtifact;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.base.JpaEntityAnnotationRules;
import io.elasticore.base.model.pub.rules.lang.kotlin.KotlinClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * A place for the aggregate's behaviour: the generation gap.
 *
 * The entity is regenerated from the DSL, so a method written into it is
 * lost, and the behaviour ends up in a service - the anemic model DDD exists
 * to avoid. An entity marked {@code @behavior} is therefore split in two:
 *
 * <pre>
 *   OrderBase  - @MappedSuperclass, abstract. The state: every field, the
 *                event, collection and invariant plumbing. Regenerated.
 *   Order      - @Entity, extends OrderBase. The behaviour: confirm(),
 *                cancel(). Written once and the developer's from then on.
 * </pre>
 *
 * The concrete class keeps the entity's name, so the mapper, the repository,
 * the ports and every reference to it are unchanged: only a superclass is
 * added. This is the split Core Data's mogenerator and Apache Cayenne make.
 *
 * A field marked {@code @behavior} is changed only by the behaviour: its
 * setter is protected (Lombok in Java, "protected set" in Kotlin), the mapper
 * does not write it, and the DTO takes it as read-only.
 *
 * <h3>Sealed by default</h3>
 *
 * Every other field of a split root is sealed: a request gives it a value
 * when the root is made, and from then on it changes only through the root's
 * own methods. Its setter is protected too; what the mapper writes through is
 * the root's creation writer, {@code creation()}, which takes any value while
 * the root is new and, once it has been saved or loaded, accepts a value only
 * if it is the one already held - a PUT echoing the whole object is fine, one
 * that changes a sealed value is refused (422). A field marked
 * {@code @editable} keeps its public setter and is written as before.
 *
 * With the DDD rule sets every root is split this way unless it is marked
 * {@code @editable} - plain data on purpose, master data edited field by
 * field - so the model does not start out anemic (SpecFactory).
 *
 * The table-level annotations are on the concrete class, where JPA reads
 * them. That class is not regenerated, so a later change to the table name,
 * indexes or schema is reported by the runner rather than applied.
 */
public class DddBehaviorRules implements CodeRules, ArtifactContributor,
        io.elasticore.base.model.pub.rules.EntityClassNamer {

    @Override
    public String entityClassName(EntitySpec spec) {
        return splits(spec) ? baseName(spec) : null;
    }

    /** Suffix of the regenerated half. */
    public static final String BASE_SUFFIX = "Base";

    /**
     * Whether this entity is split. Not for a shape JPA inherits differently
     * or that has no table of its own: an embeddable, an abstract parent, a
     * single-table hierarchy, a roll-up, a composite key.
     *
     * Decided once from the entity (SpecFactory) and asked by name, because
     * a DTO or mapper spec carries the DTO's meta annotations, not the
     * entity's.
     */
    public static boolean splits(EntitySpec e) {
        return e != null && e.isSplitForBehavior(e.getName());
    }

    /** The class the regenerated half is written as. */
    public static String baseName(EntitySpec e) {
        return e.getName() + BASE_SUFFIX;
    }

    /**
     * How the root names itself when it hands itself to a member. Inside the
     * base "this" is the base type, which a member's owner field does not
     * accept; the object is always the concrete entity, so the cast holds.
     */
    public static String self(EntitySpec e, Syntax syntax) {
        if (!splits(e)) return "this";
        return syntax == Syntax.KOTLIN
                ? String.format("this as %s", e.getName())
                : String.format("(%s) this", e.getName());
    }

    /**
     * A value the model works out or keeps itself - a lifecycle's state, a
     * @snapshot, a @compute. No request writes one, split or not: on an
     * @editable entity a client could otherwise send the price it pays.
     */
    static boolean derived(FieldSpec f) {
        return f.hasDslAny("statefield", "snapshot", "compute") && !f.isList();
    }

    /** Whether a field is changed only through the entity's behaviour. */
    static boolean guarded(FieldSpec f) {
        // A lifecycle's state, the time it entered one (@stamp), a value taken
        // from what it refers to and a computed one are the entity's to set,
        // exactly as @behavior says.
        return f.hasDslAny("behavior", "statefield", DddStateRules.STAMP, "snapshot", "compute")
                && !f.isList()
                && !f.hasDslAny("id", "pk");
    }

    /**
     * Whether a field of a split root takes a request's value only when the
     * root is made. Not the identity, which is the persistence layer's to
     * compare; not what the persistence layer writes (version, audit); not a
     * collection other than the aggregate's own members; and not a field the
     * DSL opened with @editable.
     */
    public static boolean sealed(EntitySpec e, FieldSpec f) {
        if (!splits(e) || guarded(f)) return false;
        if (f.hasDsl("editable") || f.hasDslAny("id", "pk") || f.systemManaged()) return false;
        if (f.hasDslAny("transient", "formula")) return false;
        // Joined read-only on another field's column: that field is the reference.
        if (f.hasDslAny("joinby", "joinfield")) return false;
        // An inherited field's setter is its parent's, which is not split.
        if (f.isParentField()) return false;
        String declaring = f.declaringName();
        if (declaring != null && !declaring.equals(e.getName())) return false;
        if (f.isList()) return DddCollectionRules.owns(e, f);
        return true;
    }

    // ---- the concrete entity, written once ---------------------------------

    @Override
    public List<ContributedArtifact> artifactsFor(EntitySpec e) {
        if (!splits(e)) return Collections.emptyList();
        List<ContributedArtifact> out = new ArrayList<ContributedArtifact>();
        out.add(ContributedArtifact.of("behavior", "entity", e.getName()));
        return out;
    }

    @Rule(value = "ddd-behavior-entity", target = "behavior", order = 10)
    void concreteEntity(EntitySpec e) {
        SourceCode c = ctx(e);
        c.requireSyntaxAnyOf("ddd-behavior", Syntax.JAVA, Syntax.KOTLIN);
        JpaEntityAnnotationRules.persistenceAnnotations(e);
        boolean kotlin = c.syntax() == Syntax.KOTLIN;
        String annotations = c.renderClassAnnotations();
        // Only what the annotations name: the field types other rules
        // register belong to the base, and would be unused here.
        StringBuilder imports = new StringBuilder();
        for (String line : c.renderImports().split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            String simple = t.replaceAll(";$", "");
            simple = simple.substring(simple.lastIndexOf('.') + 1);
            // Kotlin names a nested annotation without the @: Index(...).
            if (annotations.contains("@" + simple) || annotations.contains(simple + "(")) {
                imports.append(line).append('\n');
            }
        }
        c.var("packageName", e.pkg("entity"))
         .var("className", e.getName())
         .var("baseName", baseName(e))
         .var("classAnnotationList", annotations)
         .var("importList", io.elasticore.base.model.pub.rules.base.RuleText.stripTrailing(imports.toString()))
         .var("classDeclaration", kotlin
                 ? String.format("class %s : %s() {", e.getName(), baseName(e))
                 : String.format("public class %s extends %s {", e.getName(), baseName(e)));
    }

    // ---- the regenerated half ----------------------------------------------

    /** After the persistence annotations (5): the table moves to the concrete class. */
    @Rule(value = "ddd-behavior-base", target = "entity", order = 8)
    void baseIsMappedSuperclass(EntitySpec e) {
        if (!splits(e)) return;
        SourceCode c = ctx(e);
        for (String fqcn : JpaEntityAnnotationRules.TABLE_LEVEL) {
            c.removeClassAnnotation(fqcn);
        }
        c.ensureClassAnnotation(AnnoCode.of("jakarta.persistence.MappedSuperclass"));
    }

    /** After the file shell (90): the same body, under the base's name. */
    @Rule(value = "ddd-behavior-base-name", target = "entity", order = 91, phase = Rule.Phase.TAIL)
    void renameToBase(EntitySpec e) {
        if (!splits(e)) return;
        SourceCode c = ctx(e);
        String base = baseName(e);
        String parent = e.parentName();
        c.var("className", base);
        if (c.syntax() == Syntax.KOTLIN) {
            c.var("classDeclaration", KotlinClass.declaration(base, "abstract", parent, "java.io.Serializable"));
        } else {
            c.var("classDeclaration", "public abstract class %s%s implements java.io.Serializable {",
                    base, parent == null ? "" : " extends " + parent);
        }
    }

    /** A guarded field's setter is the behaviour's, and so is a sealed one's. */
    @Rule(value = "ddd-behavior-guarded-field", target = "entity", order = 60)
    void guardedField(EntitySpec e, FieldSpec f) {
        SourceCode c = ctx(e);
        if (sealed(e, f) && !f.isList()) {
            // A member's reference to its root is set by the root when it
            // takes the member in. Java's protected reaches it within the
            // package; Kotlin's does not, so the root could not compile.
            if (c.syntax() == Syntax.KOTLIN && referencesItsRoot(e, f)) {
                c.field(f).modifier("var").accessor("    internal set");
                return;
            }
            protectSetter(c, f);
            return;
        }
        if (!guarded(f)) return;
        if (!splits(e)) {
            // A derived value is kept out of requests by the DTO and the mapper
            // whether or not there is a behaviour class (below); only @behavior
            // needs the class to be changed from.
            if (derived(f)) return;
            // The lifecycle rules say why a stamp is not written here (DddStateRules.warnUnstamped).
            if (f.hasDsl(DddStateRules.STAMP) && !f.hasDsl("behavior")) return;
            io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                    "[%s] %s.%s is @behavior but %s is not - mark the entity @behavior so there is"
                            + " a class to change it from. The field stays writable.",
                    e.domainClassName(), e.getName(), f.name(), e.getName()));
            return;
        }
        protectSetter(c, f);
    }

    /** Whether the field is a member's reference back to the root that holds it. */
    private static boolean referencesItsRoot(EntitySpec e, FieldSpec f) {
        String root = e.containedIn();
        if (root == null || f.isList() || !f.isEntityRef()) return false;
        String type = f.typeName() == null ? "" : f.typeName().replace("?", "").trim();
        return type.equals(root) || type.endsWith("." + root);
    }

    private static void protectSetter(SourceCode c, FieldSpec f) {
        if (c.syntax() == Syntax.KOTLIN) {
            // Open, unlike a collection's private set: Kotlin allows a
            // protected setter on an open property, and a lazy proxy has to
            // be able to override the getter or it reads an empty field.
            c.field(f).modifier("var").accessor("    protected set");
        } else if (c.syntax() == Syntax.JAVA) {
            c.addImport("lombok.AccessLevel");
            c.field(f).ensure(AnnoCode.raw("lombok.Setter", "@Setter(AccessLevel.PROTECTED)"));
        }
    }

    /**
     * The creation writer: the one way a request's values reach a sealed
     * field, and only while the root is new - or when the value is the one
     * it already holds. After the collection methods (95), whose replace it
     * calls.
     */
    @Rule(value = "ddd-behavior-creation", target = "entity", order = 96, phase = Rule.Phase.TAIL)
    void creationWriter(EntitySpec e) {
        if (!splits(e)) return;
        SourceCode c = ctx(e);
        List<FieldSpec> sealed = new ArrayList<FieldSpec>();
        for (FieldSpec f : e.fields()) {
            if (sealed(e, f)) sealed.add(f);
        }
        if (sealed.isEmpty()) return;
        if (c.syntax() == Syntax.KOTLIN) {
            creationWriterKt(c, e, sealed);
        } else if (c.syntax() == Syntax.JAVA) {
            creationWriterJava(c, e, sealed);
        }
    }

    private static final String GUARD = "io.elasticore.springboot3.entity.FixedAtCreation";

    private void creationWriterJava(SourceCode c, EntitySpec e, List<FieldSpec> sealed) {
        String base = baseName(e);
        String name = e.getName();
        c.varLine("fieldList", "")
         .varLine("fieldList", "/** Saved or loaded: from then on the sealed fields change only through %s's methods. */", name)
         .varLine("fieldList", "@jakarta.persistence.Transient")
         .varLine("fieldList", "@lombok.Getter(lombok.AccessLevel.NONE)")
         .varLine("fieldList", "@lombok.Setter(lombok.AccessLevel.NONE)")
         .varLine("fieldList", "private transient boolean settled;")
         .varLine("fieldList", "")
         // One method per event: a mapping written in orm.xml gives a
         // method one callback, so the two do not share one.
         .varLine("fieldList", "@jakarta.persistence.PostLoad")
         .varLine("fieldList", "void settleOnLoad() {")
         .varLine("fieldList", "    this.settled = true;")
         .varLine("fieldList", "}")
         .varLine("fieldList", "")
         .varLine("fieldList", "@jakarta.persistence.PostPersist")
         .varLine("fieldList", "void settleOnPersist() {")
         .varLine("fieldList", "    this.settled = true;")
         .varLine("fieldList", "}")
         .varLine("fieldList", "")
         .varLine("fieldList", "/**")
         .varLine("fieldList", " * What a request writes a %s with. Any value while it is new; after it", name)
         .varLine("fieldList", " * has been saved or loaded, only the value it already holds - the rest is")
         .varLine("fieldList", " * %s's own to change, through its methods.", name)
         .varLine("fieldList", " */")
         .varLine("fieldList", "public Creation creation() {")
         .varLine("fieldList", "    return new Creation();")
         .varLine("fieldList", "}")
         .varLine("fieldList", "")
         .varLine("fieldList", "/** The creation writer of %s. */", name)
         .varLine("fieldList", "public final class Creation {")
         .varLine("fieldList", "")
         .varLine("fieldList", "    private Creation() {")
         .varLine("fieldList", "    }");
        for (FieldSpec f : sealed) {
            c.varLine("fieldList", "");
            if (f.isList()) {
                String item = f.coreItemType();
                c.varLine("fieldList", "    /** The members it is made with. */")
                 .varLine("fieldList", "    public void replace%s(java.util.List<%s> members) {", f.capitalName(), item)
                 .varLine("fieldList", "        %s.checkMembers(settled, \"%s\", \"%s\");", GUARD, name, f.name())
                 .varLine("fieldList", "        %s.this.replace%s(members);", base, f.capitalName())
                 .varLine("fieldList", "    }");
                continue;
            }
            c.varLine("fieldList", "    public void set%s(%s value) {", f.capitalName(), javaType(f))
             .varLine("fieldList", "        if (!settled && value == null) return;")
             .varLine("fieldList", "        %s.check(settled, \"%s\", \"%s\", %s, %s);", GUARD, name, f.name(),
                     compared(e, f, false, false), compared(e, f, true, false))
             .varLine("fieldList", "        %s.this.%s = value;", base, f.name())
             .varLine("fieldList", "    }");
        }
        c.varLine("fieldList", "}");
    }

    private void creationWriterKt(SourceCode c, EntitySpec e, List<FieldSpec> sealed) {
        String base = baseName(e);
        String name = e.getName();
        c.varLine("fieldList", "")
         .varLine("fieldList", "/** Saved or loaded: from then on the sealed fields change only through %s's methods. */", name)
         .varLine("fieldList", "@jakarta.persistence.Transient")
         .varLine("fieldList", "@kotlin.jvm.Transient")
         .varLine("fieldList", "private var settled: Boolean = false")
         .varLine("fieldList", "")
         .varLine("fieldList", "@jakarta.persistence.PostLoad")
         .varLine("fieldList", "fun settleOnLoad() {")
         .varLine("fieldList", "    settled = true")
         .varLine("fieldList", "}")
         .varLine("fieldList", "")
         .varLine("fieldList", "@jakarta.persistence.PostPersist")
         .varLine("fieldList", "fun settleOnPersist() {")
         .varLine("fieldList", "    settled = true")
         .varLine("fieldList", "}")
         .varLine("fieldList", "")
         .varLine("fieldList", "/**")
         .varLine("fieldList", " * What a request writes a %s with. Any value while it is new; after it", name)
         .varLine("fieldList", " * has been saved or loaded, only the value it already holds - the rest is")
         .varLine("fieldList", " * %s's own to change, through its methods.", name)
         .varLine("fieldList", " */")
         .varLine("fieldList", "fun creation(): Creation = Creation()")
         .varLine("fieldList", "")
         .varLine("fieldList", "/** The creation writer of %s. */", name)
         .varLine("fieldList", "inner class Creation internal constructor() {");
        for (FieldSpec f : sealed) {
            c.varLine("fieldList", "");
            if (f.isList()) {
                String item = f.coreItemType();
                c.varLine("fieldList", "    fun replace%s(members: List<%s>?) {", f.capitalName(), item)
                 .varLine("fieldList", "        %s.checkMembers(settled, \"%s\", \"%s\")", GUARD, name, f.name())
                 .varLine("fieldList", "        this@%s.replace%s(members)", base, f.capitalName())
                 .varLine("fieldList", "    }");
                continue;
            }
            c.varLine("fieldList", "    var %s: %s", f.name(), ktType(c, f))
             .varLine("fieldList", "        get() = this@%s.%s", base, f.name())
             .varLine("fieldList", "        set(value) {")
             .varLine("fieldList", "            if (!settled && value == null) return")
             .varLine("fieldList", "            %s.check(settled, \"%s\", \"%s\", %s, %s)", GUARD, name, f.name(),
                     compared(e, f, false, true).replace("@@", "this@" + base), compared(e, f, true, true))
             .varLine("fieldList", "            this@%s.%s = value", base, f.name())
             .varLine("fieldList", "        }");
        }
        c.varLine("fieldList", "}");
    }

    /**
     * What is compared for a field: the value itself, or for a reference to
     * another entity its identity - a proxy and the loaded row are the same
     * reference, and asking a proxy for its id does not load it.
     */
    private static String compared(EntitySpec e, FieldSpec f, boolean incoming, boolean kotlin) {
        String read = incoming ? "value" : (kotlin ? "@@." + f.name() : f.name());
        if (!f.isEntityRef() || e.isEmbeddableType(f.typeName())) return read;
        String pk = e.pkFieldOf(f.typeName());
        if (pk == null) return read;
        if (kotlin) return String.format("%s?.%s", read, pk);
        return String.format("(%s == null ? null : %s.get%s())", read, read,
                Character.toUpperCase(pk.charAt(0)) + pk.substring(1));
    }

    private static String javaType(FieldSpec f) {
        String t = f.typeName();
        return t == null ? "Object" : t;
    }

    private static String ktType(SourceCode c, FieldSpec f) {
        String t = c.syntax().typeName(f.typeName());
        return t.endsWith("?") ? t : t + "?";
    }

    /**
     * The mapper cannot reach a protected setter, and should not: before its
     * fields (20). A guarded field is not written at all; a sealed one is
     * written through the creation writer.
     */
    @Rule(value = "ddd-behavior-mapper", target = "mapper", order = 12)
    void mapperSkipsGuarded(EntitySpec e) {
        SourceCode c = ctx(e);
        boolean split = splits(e);
        for (FieldSpec f : e.allFields().isEmpty() ? e.fields() : e.allFields()) {
            if (!split) {
                if (derived(f)) c.systemManaged(f.name());
                continue;
            }
            if (guarded(f)) c.systemManaged(f.name());
            else if (sealed(e, f)) c.writtenAtCreation(f.name());
        }
    }

    /**
     * The service test builds its entity fixture the way a request would:
     * through the creation writer, leaving what only the behaviour writes.
     * Before the test's variables (10).
     */
    @Rule(value = "ddd-behavior-service-test", target = "service_test", order = 5)
    void serviceTestFixture(EntitySpec e) {
        SourceCode c = ctx(e);
        boolean split = splits(e);
        for (FieldSpec f : e.fields()) {
            if (!split) {
                if (derived(f)) c.systemManaged(f.name());
                continue;
            }
            if (guarded(f)) c.systemManaged(f.name());
            else if (sealed(e, f)) c.writtenAtCreation(f.name());
        }
    }

    /**
     * The DTO does not carry the entity's default for a field the root keeps.
     *
     * A default in the DTO is sent back on every update whether the caller
     * meant it or not: a PUT naming only the notes would carry a total of
     * zero, and a sealed total refuses to change. The default is the
     * entity's, which starts out with it - the creation writer leaves a new
     * root's value alone when the request says nothing. After the DTO
     * default (30).
     */
    @Rule(value = "ddd-behavior-dto-default", target = "dto", order = 31)
    void dtoDefaultIsTheEntitys(EntitySpec e, FieldSpec f) {
        if (!splits(e) || !f.hasDsl("default")) return;
        if (!guarded(f) && !sealed(e, f)) return;
        ctx(e).field(f).noInitial().removeAnnotation("lombok.Builder");
    }

    /** And a request cannot send it: shown, not taken. After dto-json (25). */
    @Rule(value = "ddd-behavior-dto", target = "dto", order = 26)
    void dtoReadOnly(EntitySpec e, FieldSpec f) {
        if (!guarded(f) || (!splits(e) && !derived(f))) return;
        ctx(e).field(f).ensure(AnnoCode.raw("com.fasterxml.jackson.annotation.JsonProperty",
                "@JsonProperty(access=JsonProperty.Access.READ_ONLY)"));
    }
}
