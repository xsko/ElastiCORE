package io.elasticore.base.model.pub.rules.ddd;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.ContributedArtifact;
import io.elasticore.base.model.pub.rules.DomainSpec;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.base.RuleText;

import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * An operation the domain performs that belongs to no single aggregate.
 *
 * The last of Evans' tactical patterns, and the one that waited longest -
 * not for a mechanism, which the artifact extension point had provided, but
 * for an answer to what counts as one. Every other pattern here is declared
 * on an entity, and a domain service is defined by not being any entity's.
 * Naming a root to hang it on would have been the cheap option and would
 * have contradicted the only thing that makes it this pattern rather than a
 * method somebody forgot to put on an aggregate.
 *
 * So it is declared where it lives - at the domain:
 *
 *   domainService:
 *     Pricing:
 *       desc: What an order costs, which no single aggregate can answer
 *       uses: [Order, Product]
 *
 * <h3>What is generated</h3>
 *
 * A {@code PricingDomainServiceBase}, regenerated on every build, holding the
 * ports of the aggregates it spans as injected fields, and a
 * {@code PricingDomainService} carrying the generate-once marker that extends
 * it. The wiring is the mechanical half and is worth generating - and
 * regenerating, so a name added to 'uses' needs no edit in the developer's
 * class; the operation is not, for the same reason a factory body is not -
 * the DSL declares data, so what can be generated is the place to write
 * behaviour rather than the behaviour. A process manager is split the same
 * way, its listeners in the base calling an abstract handle(...) per event.
 *
 * <h3>Why {@code uses} is required rather than decorative</h3>
 *
 * It is the sentence that justifies the service existing. A service spanning
 * one aggregate is a method that belongs on that aggregate; the loader says
 * so as a DSL warning rather than refusing, because a model may be mid-edit,
 * but it does not pass silently.
 *
 * It also decides where the file goes. The service is domain code and must
 * not sit in the application layer beside the use cases - the sample's
 * inward-dependency test reads every generated file and would say so - hence
 * a namespace key of its own, falling back to the domain model's package.
 *
 * Java and Kotlin, each from its own template set.
 */
public class DddDomainServiceRules implements CodeRules,
        io.elasticore.base.model.pub.rules.DomainArtifactContributor {

    /**
     * Two files per declared service, named and placed by this rule set: the
     * wiring, regenerated from the DSL on every build (XxxBase), and the class
     * that extends it, written once and the developer's from then on.
     *
     * The split keeps the DSL the place a change is made. A name added to
     * 'uses' is a field of the regenerated base, usable without touching the
     * developer's class; an event added to a process manager's 'on' is a new
     * listener in the base and an abstract handle(...) the compiler asks the
     * developer's class for - the way a command added to an entity is.
     *
     * Declared here rather than looped over in the runner, which is what the
     * runner did first: it imported this class, called these two helpers and
     * named the rule set in a string. That is the arrangement the extension
     * point exists to remove, and it had grown back on the domain side while
     * the entity side was clean.
     */
    @Override
    public java.util.List<ContributedArtifact> artifactsFor(DomainSpec domain) {
        java.util.List<ContributedArtifact> out =
                new java.util.ArrayList<ContributedArtifact>();

        for (DomainSpec.DomainServiceSpec service : domain.domainServices()) {
            if (service.processManager()) {
                out.add(ContributedArtifact.of("processManagerBase", "processManager",
                        processClassName(service) + BASE).orNamespace("service"));
                out.add(ContributedArtifact.of("processManager", "processManager", processClassName(service))
                        .orNamespace("service"));
            } else {
                out.add(ContributedArtifact.of("domainServiceBase", "domainService",
                        className(service) + BASE).orNamespace("entity"));
                out.add(ContributedArtifact.of("domainService", "domainService", className(service))
                        .orNamespace("entity"));
            }
        }
        return out;
    }

    /** The suffix of the regenerated half. */
    static final String BASE = "Base";

    /** One event a process manager reacts to, as both of its files write it. */
    private static final class Handler {
        final String event;
        final String typeName;
        final String importName;

        Handler(String event, String typeName, String importName) {
            this.event = event;
            this.typeName = typeName;
            this.importName = importName;
        }
    }

    /**
     * The events a process manager can react to, resolved the same way for
     * both of its files. What cannot be had is said - once, by the base's
     * rule - and left out, so neither file names a class nobody generates.
     *
     * @param own  the package the files go to, where nothing is imported
     * @param warn whether this call says what it leaves out
     */
    private List<Handler> handlers(DomainSpec d, DomainSpec.DomainServiceSpec pm, String own, boolean warn) {
        java.util.Set<String> known = new java.util.HashSet<String>();
        for (DddOutboxRules.Declared ev : DddOutboxRules.events(d)) known.add(ev.eventClass);
        String eventPkg = d.pkg("event");
        if (eventPkg == null || eventPkg.isEmpty()) eventPkg = d.pkg("entity");

        List<Handler> out = new java.util.ArrayList<Handler>();
        // Simple names taken so far, so a second event of the same simple name
        // (another context's) is written in full rather than imported over it.
        java.util.Map<String, String> imported = new java.util.HashMap<String, String>();
        // One handler per event class, or the class does not compile.
        java.util.Set<String> handled = new java.util.HashSet<String>();
        for (String event : pm.on()) {
            String eventClass;
            String pkgOfEvent;
            if (event.indexOf(':') > 0) {
                // shop:OrderPlaced - another bounded context's event, which it
                // generates; the context map checked that this one may use it.
                String fq = d.foreignEventClass(event);
                if (fq == null) {
                    String why = d.foreignEventProblem(event);
                    if (warn) {
                        io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", why != null
                                ? String.format("[%s] processManager %s: %s - %s.", d.getName(), pm.name(), event, why)
                                : String.format("[%s] processManager %s: %s declares no event %s - name another context's"
                                        + " event as context:RootEvent (shop:OrderPlaced), as that root's @event declares it.",
                                        d.getName(), pm.name(), event.substring(0, event.indexOf(':')).trim(),
                                        event.substring(event.indexOf(':') + 1).trim()));
                    }
                    continue;
                }
                eventClass = fq.substring(fq.lastIndexOf('.') + 1);
                pkgOfEvent = fq.substring(0, fq.lastIndexOf('.'));
            } else {
                eventClass = event.endsWith("Event") ? event : event + "Event";
                pkgOfEvent = eventPkg;
                if (!known.contains(eventClass)) {
                    String other = d.contextOfEvent(event);
                    if (warn) {
                        io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", other != null
                                ? String.format("[%s] processManager %s: %s is an event of %s - write %s:%s"
                                        + " (and list %s in config.context.uses).",
                                        d.getName(), pm.name(), event, other, other, event, other)
                                : String.format("[%s] processManager %s: no root declares the event %s - add it to that root's @event.",
                                        d.getName(), pm.name(), event));
                    }
                    continue;
                }
            }
            if (!handled.add(pkgOfEvent + "." + eventClass)) {
                if (warn) {
                    // One handler for the two names: a decision said (see RulesGenRunner, DSL_NOTE).
                    io.elasticore.base.util.ConsoleLog.storeLog("DSL_NOTE", String.format(
                            "[%s] processManager %s: %s is named twice in 'on'; it gets one handler.",
                            d.getName(), pm.name(), event));
                }
                continue;
            }
            String taken = imported.get(eventClass);
            if (taken == null) imported.put(eventClass, pkgOfEvent);
            if (taken != null && !taken.equals(pkgOfEvent)) {
                out.add(new Handler(event, pkgOfEvent + "." + eventClass, null));
            } else {
                out.add(new Handler(event, eventClass, pkgOfEvent.equals(own) ? null : pkgOfEvent + "." + eventClass));
            }
        }
        return out;
    }

    /** What the class docs say it reacts to: the events that got a handler. */
    private static String reactsTo(List<Handler> handlers) {
        if (handlers.isEmpty()) return "no event yet (see the generator's DSL warnings)";
        StringBuilder sb = new StringBuilder();
        for (Handler h : handlers) sb.append(sb.length() > 0 ? ", " : "").append(h.event);
        return sb.toString();
    }

    /** The process manager a file of this name is written for, or null. */
    private DomainSpec.DomainServiceSpec processNamed(DomainSpec d, String className) {
        for (DomainSpec.DomainServiceSpec s : d.domainServices()) {
            if (s.processManager() && processClassName(s).equals(className)) return s;
        }
        return null;
    }

    /** The name without the base suffix, or null when it has none. */
    private static String ownerOf(String baseName) {
        return baseName != null && baseName.endsWith(BASE)
                ? baseName.substring(0, baseName.length() - BASE.length()) : null;
    }

    /**
     * The ports of what a service spans, as injected fields of the base.
     *
     * Fields rather than a constructor, deliberately: a constructor would be
     * repeated in the developer's class, and a name added to 'uses' would then
     * be an edit there - the DSL change would stop being the whole change.
     */
    private void ports(SourceCode c, DomainSpec d, List<String> uses, String own, boolean kotlin) {
        if (uses.isEmpty()) return;
        c.addImport("org.springframework.beans.factory.annotation.Autowired");
        String portPkg = portPackage(d);
        for (String used : uses) {
            String pkg = packageOf(d, used, portPkg);
            if (pkg != null && !pkg.isEmpty() && !pkg.equals(own)) c.addImport("%s.%s", pkg, injectedType(d, used));
            c.varLine("bodyList", "@Autowired");
            if (kotlin) {
                c.varLine("bodyList", "protected lateinit var %s: %s", fieldName(d, used), injectedType(d, used));
            } else {
                c.varLine("bodyList", "protected %s %s;", injectedType(d, used), fieldName(d, used));
            }
            c.varLine("bodyList", "");
        }
    }

    /**
     * A process manager's wiring: reacts to events and carries a piece of
     * work on across aggregates - what the order shipping means for billing,
     * say. Regenerated on every build.
     *
     * Each listener runs after the transaction that raised the event has
     * committed, in a transaction of its own: the step it takes is a new
     * decision, and a failure in it must not undo the change that already
     * happened. The step itself is behaviour - handle(...), which the
     * developer's class writes, including what to do when it fails (the
     * compensation of a saga).
     */
    @Rule(value = "ddd-process-manager-base", target = "processManagerBase", order = 10)
    void processManagerBase(DomainSpec d) {
        SourceCode c = ctx(d);
        c.requireSyntaxAnyOf("ddd-domain-service", Syntax.JAVA, Syntax.KOTLIN);

        String owner = ownerOf(c.artifactName());
        DomainSpec.DomainServiceSpec pm = owner == null ? null : processNamed(d, owner);
        if (pm == null) {
            c.dontMake();
            return;
        }

        boolean kotlin = c.syntax() == Syntax.KOTLIN;
        String own = processPackage(d);
        ports(c, d, aggregatesOf(d, pm, true), own, kotlin);

        List<Handler> handlers = handlers(d, pm, own, true);
        if (!handlers.isEmpty()) {
            c.addImport("org.springframework.transaction.annotation.Propagation");
            c.addImport("org.springframework.transaction.annotation.Transactional");
            c.addImport("org.springframework.transaction.event.TransactionPhase");
            c.addImport("org.springframework.transaction.event.TransactionalEventListener");
        }
        for (Handler h : handlers) {
            if (h.importName != null) c.addImport(h.importName);
            c.varLine("bodyList", "/** After %s has committed, in a transaction of its own. */", h.typeName)
             .varLine("bodyList", "@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)")
             .varLine("bodyList", "@Transactional(propagation = Propagation.REQUIRES_NEW)");
            if (kotlin) {
                c.varLine("bodyList", "open fun on(event: %s) {", h.typeName)
                 .varLine("bodyList", "    handle(event)")
                 .varLine("bodyList", "}")
                 .varLine("bodyList", "")
                 .varLine("bodyList", "/** What %s means here - the next step, written in %s. */", h.event, owner)
                 .varLine("bodyList", "protected abstract fun handle(event: %s)", h.typeName);
            } else {
                c.varLine("bodyList", "public void on(%s event) {", h.typeName)
                 .varLine("bodyList", "    handle(event);")
                 .varLine("bodyList", "}")
                 .varLine("bodyList", "")
                 .varLine("bodyList", "/** What %s means here - the next step, written in %s. */", h.event, owner)
                 .varLine("bodyList", "protected abstract void handle(%s event);", h.typeName);
            }
            c.varLine("bodyList", "");
        }

        c.var("packageName", own)
         .var("className", c.artifactName())
         .var("ownerName", owner)
         .var("classDoc", pm.description() == null ? "" : pm.description())
         .var("reactsTo", reactsTo(handlers))
         .var("importList", RuleText.stripTrailing(c.renderImports()));
    }

    /**
     * The developer's process manager, written once: one handle(...) per event
     * the DSL names, each a place for the next step. An event added later is
     * an abstract method of the regenerated base, so the compiler says what is
     * missing here instead of the step being silently absent.
     */
    @Rule(value = "ddd-process-manager", target = "processManager", order = 10)
    void processManager(DomainSpec d) {
        SourceCode c = ctx(d);
        c.requireSyntaxAnyOf("ddd-domain-service", Syntax.JAVA, Syntax.KOTLIN);

        DomainSpec.DomainServiceSpec pm = processNamed(d, c.artifactName());
        if (pm == null) {
            c.dontMake();
            return;
        }

        boolean kotlin = c.syntax() == Syntax.KOTLIN;
        String own = processPackage(d);
        c.addImport("org.springframework.stereotype.Component");

        List<Handler> handlers = handlers(d, pm, own, false);
        for (Handler h : handlers) {
            if (h.importName != null) c.addImport(h.importName);
            if (kotlin) {
                c.varLine("bodyList", "override fun handle(event: %s) {", h.typeName);
            } else {
                c.varLine("bodyList", "@Override")
                 .varLine("bodyList", "protected void handle(%s event) {", h.typeName);
            }
            c.varLine("bodyList", "    // The next step goes here.")
             .varLine("bodyList", "}")
             .varLine("bodyList", "");
        }

        c.var("packageName", own)
         .var("className", c.artifactName())
         .var("baseName", c.artifactName() + BASE)
         .var("classDoc", pm.description() == null ? "" : pm.description())
         .var("reactsTo", reactsTo(handlers))
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("importList", RuleText.stripTrailing(c.renderImports()));
    }

    /** Where process managers go: their own namespace, or the application's. */
    public static String processPackage(DomainSpec d) {
        String pkg = d.pkg("processManager");
        if (pkg == null || pkg.isEmpty()) pkg = d.pkg("service");
        return (pkg == null || pkg.isEmpty()) ? d.pkg("entity") : pkg;
    }

    public static String processClassName(DomainSpec.DomainServiceSpec pm) {
        String name = pm.name();
        return name.endsWith("ProcessManager") ? name : name + "ProcessManager";
    }

    /**
     * A domain service's wiring: the ports of what it spans, regenerated on
     * every build.
     *
     * A domain-level target, so this is one of the few rules that reads
     * {@link DomainSpec} rather than {@link EntitySpec} - which is the shape
     * of the pattern rather than an inconvenience.
     */
    @Rule(value = "ddd-domain-service-base", target = "domainServiceBase", order = 10)
    void domainServiceBase(DomainSpec d) {
        SourceCode c = ctx(d);
        c.requireSyntaxAnyOf("ddd-domain-service", Syntax.JAVA, Syntax.KOTLIN);

        String owner = ownerOf(c.artifactName());
        DomainSpec.DomainServiceSpec service = owner == null ? null : current(d, owner);
        if (service == null) {
            c.dontMake();
            return;
        }

        List<String> uses = aggregatesOf(d, service, true);
        ports(c, d, uses, servicePackage(d), c.syntax() == Syntax.KOTLIN);

        c.var("packageName", servicePackage(d))
         .var("className", c.artifactName())
         .var("ownerName", owner)
         .var("classDoc", service.description() == null ? "" : service.description())
         .var("spans", spans(uses))
         .var("importList", RuleText.stripTrailing(c.renderImports()));
    }

    /** The service class: a place to write what it does, given the base's ports. */
    @Rule(value = "ddd-domain-service", target = "domainService", order = 10)
    void domainService(DomainSpec d) {
        SourceCode c = ctx(d);
        c.requireSyntaxAnyOf("ddd-domain-service", Syntax.JAVA, Syntax.KOTLIN);

        DomainSpec.DomainServiceSpec service = current(d, c.artifactName());
        if (service == null) {
            c.dontMake();
            return;
        }

        List<String> uses = aggregatesOf(d, service, false);
        c.addImport("org.springframework.stereotype.Service");

        c.varLine("bodyList", "// The operation goes here. It was not generated because the model")
         .varLine("bodyList", "// declares data and this is behaviour - what could be generated is")
         .varLine("bodyList", "// the place and the wiring: the ports of what it spans are fields of")
         .varLine("bodyList", "// %s%s, regenerated from 'uses'.", c.artifactName(), BASE)
         .varLine("bodyList", "//")
         .varLine("bodyList", "// This file is written once and is yours from then on.");

        c.var("packageName", servicePackage(d))
         .var("className", c.artifactName())
         .var("baseName", c.artifactName() + BASE)
         .var("classDoc", service.description() == null ? "" : service.description())
         .var("spans", spans(uses))
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("importList", RuleText.stripTrailing(c.renderImports()));
    }

    /** The declaration this run is writing, matched by the class name. */
    private DomainSpec.DomainServiceSpec current(DomainSpec d, String className) {
        for (DomainSpec.DomainServiceSpec service : d.domainServices()) {
            if (className(service).equals(className)) return service;
        }
        return null;
    }

    /** Where domain services go: their own namespace, or the domain model's. */
    public static String servicePackage(DomainSpec d) {
        String pkg = d.pkg("domainService");
        return (pkg == null || pkg.isEmpty()) ? d.pkg("entity") : pkg;
    }

    public static String className(DomainSpec.DomainServiceSpec service) {
        String name = service.name();
        return name.endsWith("DomainService") ? name : name + "DomainService";
    }

    /**
     * What a domain service is handed for an aggregate it spans.
     *
     * The port the domain owns, not the Spring Data repository. This was the
     * repository inversion's first real consumer and the reason the
     * hexagonal review's "low priority" was revisited: a domain service is
     * domain code by definition, and handing it a JpaRepository points the
     * innermost layer's dependency outward - the one thing the architecture
     * this bundle is named after asks not to happen.
     *
     * Falls back to nothing special: a project without ddd-repository-port
     * selected has no port to inject, and the name it resolves to is then
     * whatever its repository namespace holds.
     */
    /**
     * What the service is handed for one name in its uses list.
     *
     * A name is either an aggregate or a port the DSL declares, and the model
     * knows which - so the two are told apart by asking rather than by a
     * naming convention nobody would remember. An aggregate becomes the
     * repository port the domain owns; a declared port is already an
     * interface and is handed over as itself.
     *
     * This is the whole of what fusing the sql and http ports into the
     * hexagon amounts to at the DSL level: one list, and the generator
     * resolves each entry to the port of the right kind.
     */
    private String injectedType(DomainSpec d, String used) {
        return used + "RepositoryPort";
    }

    private String packageOf(DomainSpec d, String used, String defaultPkg) {
        return defaultPkg;
    }

    /**
     * The aggregates a service spans, with anything that is not one dropped.
     *
     * A dbms or http port is an outbound port of the <em>application</em>:
     * its contract is written in application DTOs and, where it pages, in
     * Spring's Page. A domain service handed one would be domain code
     * depending on the layer above it, which is the single thing this
     * architecture asks not to happen - and the sample's own inward-dependency
     * check refuses it, which is how this was found rather than shipped.
     *
     * So the two kinds of outbound port are not interchangeable and the DSL
     * says so here rather than in a paragraph: an aggregate gives the domain
     * a repository port it owns, and a declared port is the application's to
     * use. Dropped with a warning rather than refused, for the same reason
     * every other unreadable declaration is: a model may be mid-edit.
     */
    private List<String> aggregatesOf(DomainSpec d, DomainSpec.DomainServiceSpec service, boolean warn) {
        List<String> out = new java.util.ArrayList<String>();
        for (String used : service.uses()) {
            if (d.isPort(used)) {
                if (warn) io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                        "[%s] domainService %s: '%s' is a declared port, which belongs to the"
                                + " application - its contract names application types. A domain"
                                + " service takes the aggregates it spans; the port is used by"
                                + " the service that calls it.",
                        d.getName(), service.name(), used));
                continue;
            }
            // The port is generated by exactly one rule; asking it whether it
            // applies is the only way the two cannot disagree.
            EntitySpec root = entityNamed(d, used);
            if (root == null || !DddRepositoryPortRules.applies(root)) {
                String why = root == null ? "is not an entity of this domain"
                        : !root.aggregateRoot() ? "is a member of another aggregate; a domain service spans roots"
                        : root.pkCount() != 1 ? "has a composite key, which the repository port does not cover"
                        : "has no repository - it is neither @expose nor @service; add @service to it";
                if (warn) io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                        "[%s] domainService %s: '%s' %s, so there is no %sRepositoryPort to hand the service.",
                        d.getName(), service.name(), used, why, used));
                continue;
            }
            out.add(used);
        }
        return out;
    }

    private EntitySpec entityNamed(DomainSpec d, String name) {
        for (EntitySpec e : d.entities()) {
            if (name.equals(e.getName())) return e;
        }
        return null;
    }

    /** Where the ports live: the domain's own namespace, or the model's. */
    private String portPackage(DomainSpec d) {
        String pkg = d.pkg("domainRepository");
        return (pkg == null || pkg.isEmpty()) ? d.pkg("entity") : pkg;
    }

    private String fieldName(DomainSpec d, String used) {
        String type = injectedType(d, used);
        return Character.toLowerCase(type.charAt(0)) + type.substring(1);
    }

    /** The namespaces a domain service reads, so SpecFactory carries them. */
    static final String[] NAMESPACES = {"domainRepository", "entity"};

    private String spans(List<String> uses) {
        StringBuilder sb = new StringBuilder();
        for (String aggregate : uses) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(aggregate);
        }
        return sb.toString();
    }
}
