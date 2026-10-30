package io.elasticore.base.model.pub.rules.ddd;

import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * What a reference across an aggregate boundary may do.
 *
 * The rule an aggregate exists to enforce is that one transaction changes one
 * aggregate. A reference to another root is where that gets broken, and it
 * gets broken two ways: by cascading a write through it, and by loading the
 * other aggregate eagerly so that having it in hand invites the write.
 *
 * Both are annotations on the field, so both can be answered here.
 *
 * Which references cross a boundary needs nothing new to be declared. Inside
 * an aggregate the DSL already says so with a cascading relation - that is
 * what {@link EntitySpec#aggregateRoot()} reads - so a relation WITHOUT
 * cascade is, by the same reading, a reference to something outside.
 *
 * A collection of things outside the aggregate is answered the same way and
 * for the same reason. Holding other roots is what invites loading a dozen
 * aggregates and changing them together, so the way to change them is taken
 * away: the list can be read, and each of its elements is written through the
 * root that owns it. Reading is left alone, because a view that spans
 * aggregates is what a read model is for.
 *
 * What this deliberately does not do is replace the reference with the other
 * root's identity, which is the form the pattern is usually stated in. That
 * changes the field's type, and the mapper, the DTO and the Q class are
 * generated from the same spec by rules that would still see the old one. It
 * is a spec-level change, not a source-level one, and is left out rather than
 * half-done. Severing the write path is the part that carries the guarantee;
 * the identity field is the part that makes it obvious.
 *
 * Ordered before the file shell, which renders the field block at 90.
 */
public class DddReferenceRules implements CodeRules, io.elasticore.base.model.pub.rules.ReferencesById {

    private static final String JPA = "jakarta.persistence.%s";

    /**
     * A relation into another domain is a relation into another bounded
     * context: the two models then share a table's shape and change together,
     * which is what separating them was meant to stop. It still generates -
     * a model mid-migration has such references - but it is said, once per
     * field, with the way out: keep the other context's identity in a column
     * of its own and ask that context through a port (the port DSL), which is
     * where its language is translated into this one.
     */
    private static final java.util.Set<String> SAID_CROSS_CONTEXT =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    @Rule(value = "ddd-reference-context", target = "entity", order = 3)
    void crossContextReference(EntitySpec e) {
        for (FieldSpec f : e.fields()) {
            String type = f.coreItemType();
            if (type == null || f.isBaseType() || e.isKnownEntity(type) || !f.isEntityRef()) continue;
            String key = e.domainClassName() + "." + e.getName() + "." + f.name();
            if (!SAID_CROSS_CONTEXT.add(key)) continue;
            io.elasticore.base.util.ConsoleLog.printWarn(String.format(
                    "[ddd] %s.%s refers to %s of another domain - another bounded context. Keep its identity"
                            + " in a column (e.g. %sId) and reach it through a port, which translates its model"
                            + " into this one; a direct relation couples the two models.",
                    e.getName(), f.name(), type, Character.toLowerCase(type.charAt(0)) + type.substring(1)));
        }
    }

    @Rule(value = "ddd-reference", target = "entity", order = 82, phase = Rule.Phase.TAIL)
    void crossAggregateReference(EntitySpec e) {
        if (e.hasMeta("embeddable")) return;

        SourceCode c = ctx(e);
        for (FieldSpec f : e.fields()) {
            if (!crossesBoundary(e, f)) continue;

            if (f.isList()) {
                sealCollectionOfOthers(c, f);
                continue;
            }

            c.addImport(JPA, "FetchType");
            String byId = f.dslValue("idref");
            if (byId != null && !byId.isEmpty()) {
                // The reference is the id field; this is the same column,
                // joined read-only, for queries and for reading.
                c.field(f)
                 .merge(AnnoCode.of(JPA, relationOf(f)).valRaw("fetch", "FetchType.LAZY"))
                 .comment("Another aggregate, to read. The reference is %s; change that to point elsewhere.", byId);
                readOnly(c, f);
                continue;
            }
            c.field(f)
             .merge(AnnoCode.of(JPA, relationOf(f)).valRaw("fetch", "FetchType.LAZY"))
             .comment("Another aggregate: referenced, never written through.");
        }
    }

    /** No setter: nothing writes the object, only its id. */
    private void readOnly(SourceCode c, FieldSpec f) {
        if (c.syntax() == Syntax.KOTLIN) {
            c.field(f).modifier("final var").accessor("    private set");
        } else if (c.syntax() == Syntax.JAVA) {
            c.addImport("lombok.AccessLevel");
            c.field(f).ensure(AnnoCode.raw("lombok.Setter", "@Setter(AccessLevel.NONE)"));
        }
    }

    /**
     * A reference by identity is joined read-only, so a row just saved holds
     * the id and not yet the other aggregate: the reply would show no partner
     * name until the next read. The service reads the row back first - one
     * query, only for entities that reference another by id. After the
     * service's variables (10) and ports (15).
     */
    @Rule(value = "ddd-reference-read-back", target = "service", order = 16)
    void readBackAfterSave(EntitySpec e) {
        if (!readsBack(e)) return;
        // A MyBatis service reads every write back through its port (mybatis-ddd-service).
        if (io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.applies(e)) return;
        SourceCode c = ctx(e);
        String em = c.varValue("emRef") == null ? "entityManager" : c.varValue("emRef");
        c.var("readBackOpen", "readBack(").var("readBackClose", ")");
        if (c.syntax() == Syntax.KOTLIN) {
            c.addMethod(String.format(
                    "    /** Read back after a save: the reference by id is joined read-only, and the reply shows what it points at. */%n"
                            + "    private fun readBack(saved: %1$s): %1$s {%n"
                            + "        try {%n"
                            + "            %2$s.flush()%n"
                            + "        } catch (ex: RuntimeException) {%n"
                            + "            throw org.springframework.orm.jpa.vendor.HibernateJpaDialect().translateExceptionIfPossible(ex) ?: ex%n"
                            + "        }%n"
                            + "        %2$s.refresh(saved)%n"
                            + "        return saved%n"
                            + "    }", e.getName(), em).replace("\r\n", "\n"));
        } else {
            c.addMethod(String.format(
                    "    /** Read back after a save: the reference by id is joined read-only, and the reply shows what it points at. */%n"
                            + "    private %1$s readBack(%1$s saved) {%n"
                            + "        try {%n"
                            + "            %2$s.flush();%n"
                            + "        } catch (RuntimeException ex) {%n"
                            + "            // As the commit would have said it: a duplicate key is a 409, not a 500.%n"
                            + "            RuntimeException translated = new org.springframework.orm.jpa.vendor.HibernateJpaDialect().translateExceptionIfPossible(ex);%n"
                            + "            throw translated != null ? translated : ex;%n"
                            + "        }%n"
                            + "        %2$s.refresh(saved);%n"
                            + "        return saved;%n"
                            + "    }", e.getName(), em).replace("\r\n", "\n"));
        }
    }

    /**
     * The service test, when the service reads back through the repository
     * helper: the mocked helper answers null for its entity manager, so it is
     * handed a mocked one. Lenient, because only the tests that save touch it.
     * Tests given ports already mock the entity manager. After the ports (15).
     */
    @Rule(value = "ddd-reference-read-back-test", target = "service_test", order = 16)
    void readBackInTheServiceTest(EntitySpec e) {
        if (!readsBack(e)) return;
        SourceCode c = ctx(e);
        if (c.syntax() != Syntax.KOTLIN || !"repository".equals(c.varValue("queryMock"))) return;
        c.varLine("mockDeclarationList", "")
         .varLine("mockDeclarationList", "    @Mock")
         .varLine("mockDeclarationList", "    private lateinit var entityManager: jakarta.persistence.EntityManager");
        c.var("entityManagerMock", "true");
        String setUp = c.varValue("mockSetUp");
        c.var("mockSetUp", "%s\n        org.mockito.Mockito.lenient().`when`(helper.entityManager).thenReturn(entityManager)",
                setUp == null ? "" : setUp);
    }

    /**
     * An entity that holds another by id, itself or through a member, and is
     * saved through its own service: a read-only one has no save to follow.
     */
    private boolean readsBack(EntitySpec e) {
        if (e.hasMeta("readonly")) return false;
        if (e.aggregateReferencesById(e.getName())) return true;
        for (FieldSpec f : e.fields()) {
            if (f.dslValue("idref") != null) return true;
        }
        return false;
    }

    /**
     * The mapper does not write an aggregate referenced by id: the id field
     * carries the reference. Before the mapper's own fields (20).
     */
    @Rule(value = "ddd-reference-by-id-mapper", target = "mapper", order = 11)
    void mapperWritesTheId(EntitySpec e) {
        SourceCode c = ctx(e);
        for (FieldSpec f : e.allFields().isEmpty() ? e.fields() : e.allFields()) {
            if (f.dslValue("idref") != null) c.systemManaged(f.name());
        }
    }

    /**
     * A collection of things this aggregate does not own.
     *
     * The relation is rewritten rather than merged because the base rule
     * emits a to-many as one line, leaving no attribute to merge into. It is
     * rewritten to exactly what the boundary allows: lazy, and no cascade -
     * a write travelling out of an aggregate is the thing being prevented,
     * whatever the model happened to declare.
     *
     * The setter goes with it, so the list cannot be swapped wholesale. That
     * part is Lombok's and so Java's; Kotlin's generated property is a var
     * because Hibernate writes it, and sealing it needs the type changed.
     */
    private void sealCollectionOfOthers(SourceCode c, FieldSpec f) {
        String relation = f.hasDsl("manytomany") ? "ManyToMany" : "OneToMany";
        String mappedBy = f.dslProperty("onetomany", "mappedBy");
        if (mappedBy == null) mappedBy = f.dslProperty("manytomany", "mappedBy");

        c.addImport(JPA, "FetchType");
        c.field(f)
         .replace(AnnoCode.raw(String.format(JPA, relation),
                 "@%s(fetch = FetchType.LAZY%s)", relation,
                 mappedBy == null ? "" : String.format(", mappedBy = \"%s\"", mappedBy)))
         .comment("Other aggregates. Read from here; each is changed through its own root.");

        if (c.syntax() != Syntax.JAVA) return;
        c.addImport("lombok.AccessLevel");
        c.field(f).add(AnnoCode.raw("lombok.Setter", "@Setter(AccessLevel.NONE)"));
    }

    /**
     * A relation to an entity this one does not own, of either arity.
     *
     * An embedded value has no life of its own and so no boundary to cross.
     */
    private boolean crossesBoundary(EntitySpec e, FieldSpec f) {
        if (!f.isEntityRef()) return false;
        if (f.hasDsl("embedded")) return false;
        if (e.isEmbeddableType(f.typeName())) return false;

        // The model's answer where it has one; the relation's otherwise.
        return e.knowsAggregateOf(f.coreItemType())
                ? !e.sameAggregate(f.coreItemType())
                : !f.hasDsl("cascade");
    }

    private String relationOf(FieldSpec f) {
        return f.hasDsl("onetoone") ? "OneToOne" : "ManyToOne";
    }

    // ---- the rows a reference names must exist -------------------------------

    /**
     * A reference held by its identity (loan.memberId, line.itemId) can name a
     * row that is not there. The insert then failed on the foreign key - a 409
     * that says "conflicts with stored data" - and every project wrote the
     * lookup by hand to answer 404. The entity checks it instead, and its
     * members' references with it:
     *
     * <pre>
     * public void requireReferences(Lookup lookup) {
     *     if (this.memberId != null &amp;&amp; lookup.find(Member.class, this.memberId) == null)
     *         throw new InvariantViolationException("reference.Loan.member", "Member " + ... + " does not exist", ...);
     * }
     * </pre>
     *
     * The code reference.&lt;Entity&gt;.&lt;relation&gt; is the one @snapshot refuses
     * a missing source with; the generated API answers both with 404.
     */
    @Rule(value = "ddd-reference-exists", target = "entity", order = 97, phase = Rule.Phase.TAIL)
    void requireReferences(EntitySpec e) {
        if (e.hasMeta("embeddable") || !e.references(e.getName())) return;
        SourceCode c = ctx(e);
        boolean kt = c.syntax() == Syntax.KOTLIN;
        java.util.List<String> body = new java.util.ArrayList<String>();
        for (FieldSpec id : e.fields()) {
            String relation = id.dslValue("idof");
            if (relation == null) continue;
            FieldSpec rel = field(e, relation);
            if (rel == null || rel.typeName() == null) continue;
            String type = rel.typeName().replace("?", "").trim();
            String code = missingCode(e, relation);
            recordMissing(e, relation, type);
            if (kt) {
                body.add(String.format("if (this.%s != null && lookup.find(%s::class.java, this.%s) == null)", id.name(), type, id.name()));
                body.add(String.format("    throw io.elasticore.springboot3.validation.InvariantViolationException(\"%s\", \"%s \" + this.%s + \" does not exist\", this.%s.toString())",
                        code, type, id.name(), id.name()));
            } else {
                body.add(String.format("if (this.%s != null && lookup.find(%s.class, this.%s) == null)", id.name(), type, id.name()));
                body.add(String.format("    throw new io.elasticore.springboot3.validation.InvariantViolationException(\"%s\", \"%s \" + this.%s + \" does not exist\", String.valueOf(this.%s));",
                        code, type, id.name(), id.name()));
            }
        }
        for (FieldSpec f : e.fields()) {
            // The same members SpecFactory.registerReferencing counted: owned, or cascaded to.
            if (!f.isList() || !f.isEntityRef() || !e.references(f.coreItemType())) continue;
            if (!DddCollectionRules.owns(e, f) && !f.hasDsl("cascade")) continue;
            if (kt) {
                body.add(String.format("this.%s?.forEach { it.requireReferences(lookup) }", f.name()));
            } else {
                body.add(String.format("if (this.%s != null) for (%s member : this.%s) member.requireReferences(lookup);",
                        f.name(), f.coreItemType(), f.name()));
            }
        }
        // The class this one extends (@extend) may check references itself: the
        // model counts an entity as referencing by every field it has, inherited
        // ones included, while the body above reads only its own. With nothing of
        // its own to add, the parent's method is this class's - one written here
        // would replace it. With something to add, the parent's checks run first.
        boolean inherits = inheritsReferences(e);
        if (body.isEmpty() && inherits) return;
        if (inherits) body.add(0, kt ? "super.requireReferences(lookup)" : "super.requireReferences(lookup);");
        // Otherwise written even with nothing to check: whatever holds this entity,
        // and its service, call it because the model says it references - a method
        // that is missing would not compile, one that is empty costs nothing.
        c.varLine("fieldList", "")
         .varLine("fieldList", "/**")
         .varLine("fieldList", " * Refuses an identity that names no row: each row this one refers to by its")
         .varLine("fieldList", " * identity must exist (the API answers 404). Called by the service before")
         .varLine("fieldList", " * each save.")
         .varLine("fieldList", " */");
        if (inherits && !kt) c.varLine("fieldList", "@Override");
        c.varLine("fieldList", kt
                        ? (inherits ? "override fun requireReferences(lookup: %s) {" : "fun requireReferences(lookup: %s) {")
                        : "public void requireReferences(%s lookup) {",
                "io.elasticore.springboot3.domain.Lookup");
        for (String line : body) c.varLine("fieldList", "    %s", line);
        c.varLine("fieldList", "}");
    }

    /**
     * Whether the class this entity extends already has requireReferences:
     * its @extend parent is itself counted as referencing (and so has the
     * method, or inherits it the same way). Kotlin's all-open plugin opens the
     * parent's method, so the child overrides it.
     */
    static boolean inheritsReferences(EntitySpec e) {
        String parent = e.parentName();
        return parent != null && !parent.trim().isEmpty() && !parent.equals(e.getName())
                && e.references(parent.trim());
    }

    /**
     * The service test saves a fixture whose references name ids no mocked
     * entity manager has: each lookup answers a mock of the class asked for,
     * so the check passes and a @snapshot reads a row of the right type.
     * Lenient, because only the tests that save look anything up. After the
     * ports (15) and the read-back mocks (16), which declare the manager.
     */
    @Rule(value = "ddd-reference-exists-test", target = "service_test", order = 17)
    void referencesExistInTheServiceTest(EntitySpec e) {
        if (e.hasMeta("embeddable") || !e.references(e.getName())) return;
        SourceCode c = ctx(e);
        if (c.syntax() != Syntax.KOTLIN) return;
        if (!"true".equals(c.varValue("entityManagerMock"))) {
            c.varLine("mockDeclarationList", "")
             .varLine("mockDeclarationList", "    @Mock")
             .varLine("mockDeclarationList", "    private lateinit var entityManager: jakarta.persistence.EntityManager");
            c.var("entityManagerMock", "true");
            if ("repository".equals(c.varValue("queryMock"))) {
                String setUp = c.varValue("mockSetUp");
                c.var("mockSetUp", "%s\n        org.mockito.Mockito.lenient().`when`(helper.entityManager).thenReturn(entityManager)",
                        setUp == null ? "" : setUp);
            }
        }
        String setUp = c.varValue("mockSetUp");
        String stub = "        org.mockito.Mockito.lenient().`when`(entityManager.find(org.mockito.ArgumentMatchers.any<Class<Any>>(),"
                + " org.mockito.ArgumentMatchers.any<Any>()))\n"
                + "            .thenAnswer(org.mockito.stubbing.Answer<Any> { call -> org.mockito.Mockito.mock(call.getArgument<Class<Any>>(0)) })";
        c.var("mockSetUp", "%s", setUp == null || setUp.isEmpty() ? stub : setUp + "\n" + stub);
    }

    /** The service checks before it derives values and before beforeSave: ahead of anything that reads them. */
    @Rule(value = "ddd-reference-exists-service", target = "service", order = 35)
    void requireReferencesBeforeSave(EntitySpec e) {
        if (e.hasMeta("embeddable") || !e.references(e.getName())) return;
        SourceCode c = ctx(e);
        String em = c.varValue("emRef");
        if (em == null || em.isEmpty()) em = "entityManager";
        c.varLine("deriveBeforeSave", "%s", c.syntax() == Syntax.KOTLIN
                ? String.format("entity.requireReferences { type, id -> %s.find(type, id) }", em)
                : String.format("entity.requireReferences(%s::find);", em));
    }

    /** The message code of a reference to a row that does not exist: reference.<Entity>.<relation>. */
    static String missingCode(EntitySpec e, String relation) {
        return "reference." + e.getName() + "." + relation;
    }

    /** Records the code with its wording, so the domain's message bundle (and a translation of it) has it. */
    static void recordMissing(EntitySpec e, String relation, String type) {
        io.elasticore.base.model.pub.rules.ErrorCatalog.record(missingCode(e, relation), type + " {0} does not exist");
    }

    private static FieldSpec field(EntitySpec e, String name) {
        for (FieldSpec f : e.fields()) {
            if (f.name().equals(name)) return f;
        }
        return null;
    }
}
