package io.elasticore.base.model.pub.rules.ddd;

import io.elasticore.base.model.pub.rules.ArtifactContributor;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.ContributedArtifact;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.base.RuleText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The repository the domain owns, and the one that knows Spring Data.
 *
 * The hexagonal review judged this low priority and gave a reason: the payoff
 * usually quoted is being able to remove JPA, and an ElastiCORE project
 * rarely removes JPA. That reason was about the payoff most often claimed for
 * the inversion, and it is still true. What it missed is a payoff that is not
 * hypothetical, and that this generator created for itself when domain
 * services arrived:
 *
 * <pre>
 *   package ...domain.service;
 *   import ...domain.repository.OrderRepository;   // extends JpaRepository
 * </pre>
 *
 * A domain service is domain code by definition, and it was being handed the
 * persistence mechanism. The dependency points outward from the innermost
 * layer, which is the one thing the architecture the bundle is named after
 * asks not to happen. That is worth fixing whether or not anyone ever removes
 * JPA.
 *
 * <h3>What is generated</h3>
 *
 * Three files per aggregate root:
 *
 * <ul>
 *   <li>{@code OrderRepositoryPort} - an interface the domain owns, naming
 *       only what an aggregate's life consists of: find one, save it, delete
 *       it, ask whether it is there. No Spring type appears in it.</li>
 *   <li>{@code OrderQueryPort} - the application's searches, which may name
 *       Specification and Pageable because the application layer does.</li>
 *   <li>{@code OrderRepositoryAdapter} - infrastructure, implementing both
 *       by delegating to the Spring Data repository.</li>
 * </ul>
 *
 * And the application service is handed the two ports - never the adapter
 * or the Spring Data repository - so no application class imports
 * infrastructure.
 *
 * <h3>Why the port is narrow, and stays narrow</h3>
 *
 * It would be easy to mirror the Spring Data repository and call the result a
 * port. It would also be pointless: the searching, the paging and the
 * specifications are the query surface, and putting them behind an interface
 * the domain owns means putting Pageable and Specification into the domain
 * anyway - the dependency renamed rather than removed. Queries that span or
 * page aggregates are a read model's work, which chapter 17 already said, so
 * they go through a port of the application's own (see queryPort) rather than
 * through this one. An earlier version let the application service reach the
 * Spring Data repository for them instead; that put an infrastructure import
 * into every application class, which is the other half of what a hexagon
 * forbids.
 *
 * So the port is what the domain genuinely does with an aggregate, and
 * nothing else. A port that grew to cover reporting would be the same
 * mistake in a new package.
 *
 * <h3>What it does not attempt</h3>
 *
 * A root with a composite key is declined rather than guessed at: its
 * identity is a class of its own, and which class is a decision about
 * artifacts this rule set has no business making silently. Java only, for the
 * reasons {@link DddDomainEventRules} gives in full.
 */
public class DddRepositoryPortRules implements CodeRules, ArtifactContributor {

    @Override
    public List<ContributedArtifact> artifactsFor(EntitySpec e) {
        if (!applies(e)) return Collections.emptyList();

        return Arrays.asList(
                ContributedArtifact.of("repositoryPort", "domainRepository", portName(e))
                        .orNamespace("entity"),
                ContributedArtifact.of("queryPort", "queryPort", queryPortName(e))
                        .orNamespace("service"),
                ContributedArtifact.of("repositoryAdapter", "persistence", adapterName(e))
                        .orNamespace("repository"));
    }

    /**
     * The application's searches, as a port of its own.
     *
     * The domain port stays as narrow as the note on this class says it must.
     * What that note then allowed - the application service reaching the
     * Spring Data repository directly for its searches - is what put
     * {@code import ...infrastructure.persistence.*} into every application
     * service: the application layer depending on the outermost one, which a
     * hexagon forbids in the same breath as the domain doing so.
     *
     * So the searches get a port too, but it is the application's: it lives
     * beside the use cases (namespace {@code queryPort}, else the service's),
     * may name Specification and Pageable - the application already speaks
     * them, its search DTOs build them - and is implemented by the same
     * adapter as the domain port.
     */
    @Rule(value = "ddd-query-port", target = "queryPort", order = 10)
    void queryPort(EntitySpec e) {
        SourceCode c = ctx(e);
        c.requireSyntaxAnyOf("ddd-query-port", Syntax.JAVA, Syntax.KOTLIN);

        boolean kotlin = c.syntax() == Syntax.KOTLIN;
        String root = e.getName();

        importEntity(c, e);
        c.addImport("org.springframework.data.domain.Page");
        c.addImport("org.springframework.data.domain.Pageable");
        c.addImport("org.springframework.data.domain.Sort");
        c.addImport("org.springframework.data.jpa.domain.Specification");
        if (!kotlin) c.addImport("java.util.List");

        String[][] methods = kotlin ? new String[][] {
                {"Every %1$s.", "fun findAll(): List<%1$s>"},
                {"Every %1$s, sorted.", "fun findAll(sort: Sort): List<%1$s>"},
                {"Those the condition matches.", "fun findAll(spec: Specification<%1$s>): List<%1$s>"},
                {"Those the condition matches, sorted.", "fun findAll(spec: Specification<%1$s>, sort: Sort): List<%1$s>"},
                {"One page of those the condition matches.", "fun findAll(spec: Specification<%1$s>, pageable: Pageable): Page<%1$s>"},
                {"How many the condition matches.", "fun count(spec: Specification<%1$s>): Long"},
        } : new String[][] {
                {"Every %1$s.", "List<%1$s> findAll();"},
                {"Every %1$s, sorted.", "List<%1$s> findAll(Sort sort);"},
                {"Those the condition matches.", "List<%1$s> findAll(Specification<%1$s> spec);"},
                {"Those the condition matches, sorted.", "List<%1$s> findAll(Specification<%1$s> spec, Sort sort);"},
                {"One page of those the condition matches.", "Page<%1$s> findAll(Specification<%1$s> spec, Pageable pageable);"},
                {"How many the condition matches.", "long count(Specification<%1$s> spec);"},
        };
        for (int i = 0; i < methods.length; i++) {
            if (i > 0) c.varLine("bodyList", "");
            c.varLine("bodyList", "/** %s */", String.format(methods[i][0], root))
             .varLine("bodyList", methods[i][1], root);
        }
        for (Declared q : declared(e, c.syntax())) {
            if (q.domain) continue;
            q.importInto(c, kotlin);
            c.varLine("bodyList", "")
             .varLine("bodyList", "/** %s */", q.doc)
             .varLine("bodyList", "%s", c.syntax().statement(q.signature("")));
        }

        c.var("packageName", queryPortPackage(e))
         .var("className", queryPortName(e))
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("importList", RuleText.stripTrailing(c.renderImports()));
    }

    /**
     * The application service, handed ports instead of the repository helper.
     *
     * The aggregate's own life - find one, save, delete - goes through the
     * domain's port, so it is the same contract a domain service is held to;
     * the searches go through the query port. The EntityManager is kept for
     * the one thing the mapper needs it for, turning an identifier into a
     * reference; it is the JPA API, not the adapter package.
     *
     * The AI-tools extension (supportAI) is written from a skeleton with the
     * same constructor slots, so it is handed the ports too.
     */
    @Rule(value = "ddd-service-ports", target = "service", order = 15)
    void serviceTakesPorts(EntitySpec e) {
        if (!usesPorts(e)) return;
        SourceCode c = ctx(e);
        boolean kotlin = c.syntax() == Syntax.KOTLIN;
        String port = portName(e);
        String query = queryPortName(e);

        List<String> imports = new ArrayList<String>();
        imports.add(String.format(kotlin ? "import %s.%s" : "import %s.%s;", portPackage(e), port));
        imports.add(String.format(kotlin ? "import %s.%s" : "import %s.%s;", queryPortPackage(e), query));
        c.varLines("dependencyImportList", imports);

        if (kotlin) {
            c.var("ctorParams", "protected val repository: %s,\n"
                    + "    protected val query: %s,\n"
                    + "    protected val entityManager: jakarta.persistence.EntityManager", port, query);
        } else {
            List<String> lines = new ArrayList<String>();
            lines.add("/** The aggregate's own life - find one, save it, remove it - as the domain owns it. */");
            lines.add(String.format("protected final %s repository;", port));
            lines.add("");
            lines.add("/** Searching and paging: the application's business, through its own port. */");
            lines.add(String.format("protected final %s query;", query));
            lines.add("");
            lines.add("/** Identity references for the mapper. No query goes through it. */");
            lines.add("protected final EntityManager entityManager;");
            lines.add("");
            lines.add(String.format("public %sCoreService(%s repository, %s query, EntityManager entityManager) {",
                    e.getName(), port, query));
            lines.add("    this.repository = repository;");
            lines.add("    this.query = query;");
            lines.add("    this.entityManager = entityManager;");
            lines.add("}");
            c.varLines("dependencyList", lines);
        }
        c.var("cmdRef", "repository")
         .var("qryRef", "query")
         .var("emRef", "entityManager");
    }

    /**
     * The generated service test, mocking what the service is now given.
     *
     * The ports are Kotlin interfaces with non-null parameters, where
     * Mockito's any() - which answers null - would fail the call it is meant
     * to match. The identifier is matched by the value the test passes, and
     * the entity with the usual "any() ?: fallback" so the matcher is still
     * registered.
     */
    @Rule(value = "ddd-service-test-ports", target = "service_test", order = 15)
    void testMocksThePorts(EntitySpec e) {
        if (!usesPorts(e)) return;
        SourceCode c = ctx(e);
        if (c.syntax() != Syntax.KOTLIN) return;

        List<String> mocks = new ArrayList<String>();
        mocks.add("    @Mock");
        mocks.add(String.format("    private lateinit var repository: %s.%s", portPackage(e), portName(e)));
        mocks.add("");
        mocks.add("    @Mock");
        mocks.add(String.format("    private lateinit var query: %s.%s", queryPortPackage(e), queryPortName(e)));
        mocks.add("");
        mocks.add("    @Mock");
        mocks.add("    private lateinit var entityManager: jakarta.persistence.EntityManager");
        c.var("entityManagerMock", "true");
        c.varLines("mockDeclarationList", mocks);

        // Any identifier of the key's type: the matcher is registered by
        // any(), and the elvis hands the non-null parameter a value when
        // any() answers null - which it does for every object type.
        FieldSpec id = e.pkField();
        String type = RuleText.simpleName(id.typeName());
        c.var("mockSetUp", "")
         .var("queryMock", "query")
         .var("anyId", "(any(%s::class.javaObjectType) ?: %s)", type,
                 io.elasticore.base.model.pub.rules.base.ServiceTestBaseRules.keySampleOf(c, id))
         .var("anyEntity", "(any(%1$s::class.java) ?: mockEntity)", e.getName());
    }

    /** The extension's constructor, agreeing with the core service's. */
    @Rule(value = "ddd-service-extend-ports", target = "service_extend", order = 15)
    void extensionHandsPortsOn(EntitySpec e) {
        if (!usesPorts(e)) return;
        SourceCode c = ctx(e);
        boolean kotlin = c.syntax() == Syntax.KOTLIN;
        String port = portName(e);
        String query = queryPortPackage(e) + "." + queryPortName(e);

        // The skeleton has one import line; the domain port takes it and the
        // rest are written in full - the same answer the use-case clause gives.
        c.var("extendImport", "%s.%s", portPackage(e), port)
         .var("extendCtorParams", kotlin
                 ? String.format("repository: %s, query: %s, entityManager: jakarta.persistence.EntityManager",
                         port, query)
                 : String.format("%s repository, %s query, jakarta.persistence.EntityManager entityManager",
                         port, query))
         .var("extendSuperArgs", "repository, query, entityManager");
    }

    /** The interface the domain owns: an aggregate's life, and nothing else. */
    @Rule(value = "ddd-repository-port", target = "repositoryPort", order = 10)
    void port(EntitySpec e) {
        SourceCode c = ctx(e);
        c.requireSyntaxAnyOf("ddd-repository-port", Syntax.JAVA, Syntax.KOTLIN);

        boolean kotlin = c.syntax() == Syntax.KOTLIN;
        String root = e.getName();
        String var = e.varName();
        FieldSpec id = e.pkField();

        c.addImport("java.util.Optional");
        importEntity(c, e);

        c.varLine("bodyList", "/** The %s with this identity, or empty. */", root)
         .varLine("bodyList",
                 kotlin ? "fun findById(%3$s: %2$s): Optional<%1$s>" : "Optional<%1$s> findById(%2$s %3$s);",
                 root, id.typeName(), id.name())
         .varLine("bodyList", "")
         .varLine("bodyList", "/** Writes it, and answers what was written. */")
         .varLine("bodyList",
                 kotlin ? "fun save(%2$s: %1$s): %1$s" : "%1$s save(%1$s %2$s);", root, var)
         .varLine("bodyList", "")
         .varLine("bodyList", "/** Removes it, and its members with it. */")
         .varLine("bodyList",
                 kotlin ? "fun delete(%2$s: %1$s)" : "void delete(%1$s %2$s);", root, var)
         .varLine("bodyList", "")
         .varLine("bodyList", "/** Whether one with this identity exists. */")
         .varLine("bodyList",
                 kotlin ? "fun existsById(%2$s: %1$s): Boolean" : "boolean existsById(%1$s %2$s);",
                 id.typeName(), id.name());

        for (Declared q : declared(e, c.syntax())) {
            if (!q.domain) continue;
            q.importInto(c, kotlin);
            c.varLine("bodyList", "")
             .varLine("bodyList", "/** %s */", q.doc)
             .varLine("bodyList", "%s", c.syntax().statement(q.signature("")));
        }

        c.var("packageName", portPackage(e))
         .var("className", portName(e))
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("importList", RuleText.stripTrailing(c.renderImports()));
    }

    /**
     * The implementation, which is allowed to know Spring Data because
     * knowing it is its whole job.
     */
    @Rule(value = "ddd-repository-adapter", target = "repositoryAdapter", order = 10)
    void adapter(EntitySpec e) {
        SourceCode c = ctx(e);
        c.requireSyntaxAnyOf("ddd-repository-adapter", Syntax.JAVA, Syntax.KOTLIN);

        boolean kotlin = c.syntax() == Syntax.KOTLIN;
        String root = e.getName();
        String var = e.varName();
        FieldSpec id = e.pkField();
        String springRepository = root + "Repository";

        c.addImport("java.util.Optional");
        c.addImport("org.springframework.stereotype.Repository");
        importEntity(c, e);

        String repositoryPkg = e.pkg("repository");
        if (repositoryPkg != null && !repositoryPkg.isEmpty()) {
            c.addImport("%s.%s", repositoryPkg, springRepository);
        }
        String portPkg = portPackage(e);
        if (portPkg != null && !portPkg.equals(adapterPackage(e))) {
            c.addImport("%s.%s", portPkg, portName(e));
        }
        String queryPkg = queryPortPackage(e);
        if (queryPkg != null && !queryPkg.equals(adapterPackage(e))) {
            c.addImport("%s.%s", queryPkg, queryPortName(e));
        }
        c.addImport("org.springframework.data.domain.Page");
        c.addImport("org.springframework.data.domain.Pageable");
        c.addImport("org.springframework.data.domain.Sort");
        c.addImport("org.springframework.data.jpa.domain.Specification");
        if (!kotlin) c.addImport("java.util.List");

        if (kotlin) {
            c.varLine("ctorList", "private val repository: %s", springRepository)
             .varLine("bodyList", "override fun findById(%s: %s): Optional<%s> =",
                     id.name(), id.typeName(), root)
             .varLine("bodyList", "    repository.findById(%s)", id.name())
             .varLine("bodyList", "")
             .varLine("bodyList", "override fun save(%s: %s): %s =", var, root, root)
             .varLine("bodyList", "    repository.save(%s)", var)
             .varLine("bodyList", "")
             .varLine("bodyList", "override fun delete(%s: %s) {", var, root)
             .varLine("bodyList", "    repository.delete(%s)", var)
             .varLine("bodyList", "}")
             .varLine("bodyList", "")
             .varLine("bodyList", "override fun existsById(%s: %s): Boolean =",
                     id.name(), id.typeName())
             .varLine("bodyList", "    repository.existsById(%s)", id.name())
             .varLine("bodyList", "")
             .varLine("bodyList", "override fun findAll(): List<%s> = repository.findAll()", root)
             .varLine("bodyList", "")
             .varLine("bodyList", "override fun findAll(sort: Sort): List<%s> = repository.findAll(sort)", root)
             .varLine("bodyList", "")
             .varLine("bodyList", "override fun findAll(spec: Specification<%1$s>): List<%1$s> = repository.findAll(spec)", root)
             .varLine("bodyList", "")
             .varLine("bodyList", "override fun findAll(spec: Specification<%1$s>, sort: Sort): List<%1$s> =", root)
             .varLine("bodyList", "    repository.findAll(spec, sort)")
             .varLine("bodyList", "")
             .varLine("bodyList", "override fun findAll(spec: Specification<%1$s>, pageable: Pageable): Page<%1$s> =", root)
             .varLine("bodyList", "    repository.findAll(spec, pageable)")
             .varLine("bodyList", "")
             .varLine("bodyList", "override fun count(spec: Specification<%s>): Long = repository.count(spec)", root);
        } else {
            c.varLine("bodyList", "private final %s repository;", springRepository)
             .varLine("bodyList", "")
             .varLine("bodyList", "public %s(%s repository) {", adapterName(e), springRepository)
             .varLine("bodyList", "    this.repository = repository;")
             .varLine("bodyList", "}")
             .varLine("bodyList", "")
             .varLine("bodyList", "@Override")
             .varLine("bodyList", "public Optional<%s> findById(%s %s) {", root, id.typeName(), id.name())
             .varLine("bodyList", "    return repository.findById(%s);", id.name())
             .varLine("bodyList", "}")
             .varLine("bodyList", "")
             .varLine("bodyList", "@Override")
             .varLine("bodyList", "public %s save(%s %s) {", root, root, var)
             .varLine("bodyList", "    return repository.save(%s);", var)
             .varLine("bodyList", "}")
             .varLine("bodyList", "")
             .varLine("bodyList", "@Override")
             .varLine("bodyList", "public void delete(%s %s) {", root, var)
             .varLine("bodyList", "    repository.delete(%s);", var)
             .varLine("bodyList", "}")
             .varLine("bodyList", "")
             .varLine("bodyList", "@Override")
             .varLine("bodyList", "public boolean existsById(%s %s) {", id.typeName(), id.name())
             .varLine("bodyList", "    return repository.existsById(%s);", id.name())
             .varLine("bodyList", "}");
            String[][] reads = {
                    {"List<%1$s> findAll()", "repository.findAll()"},
                    {"List<%1$s> findAll(Sort sort)", "repository.findAll(sort)"},
                    {"List<%1$s> findAll(Specification<%1$s> spec)", "repository.findAll(spec)"},
                    {"List<%1$s> findAll(Specification<%1$s> spec, Sort sort)", "repository.findAll(spec, sort)"},
                    {"Page<%1$s> findAll(Specification<%1$s> spec, Pageable pageable)", "repository.findAll(spec, pageable)"},
                    {"long count(Specification<%1$s> spec)", "repository.count(spec)"},
            };
            for (String[] r : reads) {
                c.varLine("bodyList", "")
                 .varLine("bodyList", "@Override")
                 .varLine("bodyList", "public " + r[0] + " {", root)
                 .varLine("bodyList", "    return %s;", r[1])
                 .varLine("bodyList", "}");
            }
        }

        for (Declared q : declared(e, c.syntax())) {
            q.importInto(c, kotlin);
            c.varLine("bodyList", "");
            if (kotlin) {
                boolean returns = !"void".equals(q.returnType);
                c.varLine("bodyList", "%s%s", q.signature("override"), returns ? " =" : " {")
                 .varLine("bodyList", "    %s", q.delegation(true));
                if (!returns) c.varLine("bodyList", "}");
            } else {
                c.varLine("bodyList", "@Override")
                 .varLine("bodyList", "%s {", q.signature("public"))
                 .varLine("bodyList", "    %s%s;",
                         "void".equals(q.returnType) ? "" : "return ", q.delegation(false))
                 .varLine("bodyList", "}");
            }
        }

        c.var("packageName", adapterPackage(e))
         .var("className", adapterName(e))
         .var("portName", portName(e) + ", " + queryPortName(e))
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("importList", RuleText.stripTrailing(c.renderImports()));
    }

    /**
     * A query the repository DSL declares for this root, as the ports see it.
     *
     * One that answers with the root itself - one, maybe one, or a list - and
     * reads rather than writes is a question about the aggregate's life, and
     * goes on the domain's port: findByItemAndWarehouse is how a domain
     * service finds the stock it is about to take from. Everything else - a
     * page, a projection, a count, a bulk write - is the application's, and
     * goes on the query port. The adapter implements both by delegating to
     * the Spring Data method the repository rules render from the same DSL.
     */
    static final class Declared {
        String name;
        String returnType;
        String params;
        String args;
        String doc;
        boolean domain;
        /** The DTO a projecting query answers with, or null. */
        String projection;
        /** list, page, optional or one - how the rows are held. */
        String container;

        /** The adapter's body: a plain delegation, or rows mapped to the DTO. */
        String delegation(boolean kotlin) {
            String call = String.format("repository.%s(%s)", name, args);
            if (projection == null) return call;
            return String.format("io.elasticore.springboot3.mapper.TupleMapper.%s(%s, %s)",
                    container, call, kotlin ? projection + "::class.java" : projection + ".class");
        }
        final java.util.List<String> types = new java.util.ArrayList<String>();
        private Syntax syntax;

        String signature(String modifier) {
            return syntax.methodSignature(modifier, returnType, name, params);
        }

        void importInto(SourceCode c, boolean kotlin) {
            if (returnType.contains("Optional<")) c.addImport("java.util.Optional");
            if (!kotlin && returnType.startsWith("List<")) c.addImport("java.util.List");
            if (returnType.startsWith("Page<")) c.addImport("org.springframework.data.domain.Page");
            if (params.contains("Pageable")) c.addImport("org.springframework.data.domain.Pageable");
            for (String t : types) {
                String pkg = RuleText.componentPackage(t);
                if (pkg != null && !pkg.isEmpty()) c.addImport("%s.%s", pkg, t);
            }
        }
    }

    static java.util.List<Declared> declared(EntitySpec e, Syntax syntax) {
        java.util.List<Declared> out = new java.util.ArrayList<Declared>();
        String root = e.getName();
        for (io.elasticore.base.model.repo.Method m : e.queryMethods()) {
            if (!m.isEnable()) continue;
            // A conditional query is assembled at runtime, not declared; the
            // repository rules skip it, so there is nothing to delegate to.
            if (m.getQuery() != null && m.isNeedQueryAnnotation() && m.getQuery().indexOf("/* if:") > 0) continue;

            // The ports already declare these; a DSL query of the same name
            // would be a second method with the same signature, or clash.
            if (RESERVED.contains(m.getName())) {
                String key = e.getName() + "." + m.getName();
                if (WARNED.add(key)) {
                    io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                            "[%s] repository %s.%s: the ports already have a method of that name; this query is"
                                    + " left off them. Give it a name of its own.",
                            e.domainClassName(), e.getName(), m.getName()));
                }
                continue;
            }
            Declared q = new Declared();
            q.syntax = syntax;
            q.name = m.getName();
            String declared = m.getReturnType();
            String item = io.elasticore.base.model.pub.rules.base.RepositoryBaseRules.typeArgument(declared);
            // The repository returns a DTO projection as named rows; the port
            // says the DTO, and the adapter maps the rows (TupleMapper).
            declared = io.elasticore.base.model.pub.rules.base.RepositoryBaseRules.pageableOrList(e, m, declared);
            item = io.elasticore.base.model.pub.rules.base.RepositoryBaseRules.typeArgument(declared);
            q.returnType = declared;
            String dto = item != null ? item : declared;
            if (dto != null && e.isKnownDto(dto.trim())) {
                q.projection = dto.trim();
                q.container = declared.startsWith("Page<") ? "page"
                        : declared.startsWith("Optional<") ? "optional"
                        : declared.startsWith("List<") ? "list" : "one";
            }
            addType(q, item != null ? item : declared);

            StringBuilder params = new StringBuilder();
            StringBuilder args = new StringBuilder();
            io.elasticore.base.model.core.Items<io.elasticore.base.model.entity.Field> ps = m.getParams();
            if (ps != null) {
                for (io.elasticore.base.model.entity.Field f : ps.getItemList()) {
                    if (params.length() > 0) { params.append(", "); args.append(", "); }
                    String type = f.getTypeInfo().getDefaultTypeName();
                    params.append(syntax.parameter(type, f.getName()));
                    args.append(f.getName());
                    addType(q, type);
                }
            }
            boolean pageable = !m.isNative() && m.isPageable();
            if (pageable) {
                if (params.length() > 0) { params.append(", "); args.append(", "); }
                params.append(syntax.parameter("Pageable", "pageable"));
                args.append("pageable");
            }
            q.params = params.toString();
            q.args = args.toString();

            boolean select = m.getQueryInfo() == null || m.getQueryInfo().isSelectQuery();
            q.domain = select && !pageable && (root.equals(declared)
                    || ("Optional<" + root + ">").equals(declared)
                    || ("List<" + root + ">").equals(declared));
            String description = m.getMetaInfo() == null ? null : m.getDescription();
            q.doc = description != null && !description.isEmpty() ? description
                    : select ? "Declared in the repository DSL."
                    : "Declared in the repository DSL. A bulk write: it does not pass through the aggregate.";
            out.add(q);
        }
        return out;
    }

    /** Method names the ports and the adapter declare themselves. */
    private static final java.util.Set<String> RESERVED = new java.util.HashSet<String>(
            java.util.Arrays.asList("findById", "save", "delete", "existsById", "findAll", "count"));

    private static final java.util.Set<String> WARNED =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    private static void addType(Declared q, String type) {
        if (type == null) return;
        String t = type.trim();
        if (t.isEmpty() || t.contains("<") || t.contains("[")) return;
        if (Character.isUpperCase(t.charAt(0))) q.types.add(t);
    }

    /** The port and its implementation may sit in different packages. */
    private void importEntity(SourceCode c, EntitySpec e) {
        String entityPkg = e.pkg("entity");
        if (entityPkg == null || entityPkg.isEmpty()) return;
        c.addImport("%s.%s", entityPkg, e.getName());
    }

    /**
     * Roots with one identity field.
     *
     * A member has no repository to invert - the boundary took it away - and
     * a composite key names an identity class this rule set would have to
     * choose. Declining is the same answer the other DDD rules give where the
     * model has not said enough.
     */
    static boolean applies(EntitySpec e) {
        // A root nothing serves has no Spring Data repository generated, so
        // an adapter for it would import a class that does not exist.
        return e.aggregateRoot() && e.pkCount() == 1 && e.pkField() != null
                && (e.hasMeta("expose") || e.hasMeta("service"));
    }

    /** A root whose application service is handed ports rather than the helper. */
    private boolean usesPorts(EntitySpec e) {
        return applies(e);
    }

    static String queryPortName(EntitySpec e) {
        return e.getName() + "QueryPort";
    }

    /** Where the application's search port goes: its namespace, or the service's. */
    static String queryPortPackage(EntitySpec e) {
        String pkg = e.pkg("queryPort");
        return (pkg == null || pkg.isEmpty()) ? e.pkg("service") : pkg;
    }

    static String portName(EntitySpec e) {
        return e.getName() + "RepositoryPort";
    }

    static String adapterName(EntitySpec e) {
        return e.getName() + "RepositoryAdapter";
    }

    /** Where the domain's own interface goes: its namespace, or the model's. */
    static String portPackage(EntitySpec e) {
        String pkg = e.pkg("domainRepository");
        return (pkg == null || pkg.isEmpty()) ? e.pkg("entity") : pkg;
    }

    /** Where the implementation goes: its namespace, or beside the Spring one. */
    static String adapterPackage(EntitySpec e) {
        String pkg = e.pkg("persistence");
        return (pkg == null || pkg.isEmpty()) ? e.pkg("repository") : pkg;
    }

    /** The ports an aggregate's own domain code should be handed. */
    static List<String> portsFor(List<String> aggregates) {
        List<String> out = new ArrayList<String>();
        for (String aggregate : aggregates) out.add(aggregate + "RepositoryPort");
        return out;
    }
}
