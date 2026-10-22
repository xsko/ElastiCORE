package io.elasticore.base.model.pub.rules;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Discovers {@link Rule} methods on registered {@link CodeRules} sets and
 * runs them against an entity. Iteration is engine-owned: entity-level rules
 * run once, field-level rules run once per field. Rule authors never loop.
 */
public final class RuleEngine {

    private static final class RuleMethod {
        final Object ruleSet;
        final Method method;
        final Rule meta;
        final int registrationIndex;
        /** A project's entity rule run again for the developer's class of a split root. */
        final boolean borrowedForBehavior;

        RuleMethod(Object ruleSet, Method method, Rule meta, int registrationIndex) {
            this(ruleSet, method, meta, registrationIndex, false);
        }

        RuleMethod(Object ruleSet, Method method, Rule meta, int registrationIndex, boolean borrowedForBehavior) {
            this.ruleSet = ruleSet;
            this.method = method;
            this.meta = meta;
            this.registrationIndex = registrationIndex;
            this.borrowedForBehavior = borrowedForBehavior;
        }

        /** A project's own rule rather than ElastiCORE's. */
        boolean project() {
            return !ruleSet.getClass().getName().startsWith("io.elasticore.");
        }
    }

    private final List<Object> ruleSets = new ArrayList<Object>();
    private final Set<String> suppressed = new HashSet<String>();

    private SourceCode.TemplateSet templateSet = SourceCode.TemplateSet.COMPATIBLE;

    /** Selects the template directory artifacts of this engine render from. */
    public RuleEngine templateSet(SourceCode.TemplateSet set) {
        this.templateSet = set;
        return this;
    }

    public RuleEngine register(CodeRules ruleSet) {
        // A rule set says what it says once. Registering the same one twice -
        // a configured selection plus an artifact's own requirement - would
        // run every one of its rules twice, which annotation de-duplication
        // hides but line-appending rules do not.
        for (Object existing : this.ruleSets) {
            if (existing.getClass() == ruleSet.getClass()) return this;
        }
        this.ruleSets.add(ruleSet);
        return this;
    }

    /** Disables a rule by id, e.g. suppress("jpa-column"). */
    public RuleEngine suppress(String ruleId) {
        this.suppressed.add(ruleId);
        return this;
    }

    private String artifactName;

    /** The class name the next run writes, for rules that contribute several. */
    public RuleEngine artifactName(String name) {
        this.artifactName = name;
        return this;
    }

    private String packageName;

    /**
     * The package the next run's file is written to, when the runner has
     * already decided it - a contributed artifact's namespace. Given to the
     * rules as ${packageName}, so the package line and the file's folder
     * cannot disagree; a rule may still set its own.
     */
    public RuleEngine packageName(String pkg) {
        this.packageName = pkg;
        return this;
    }

    /** Runs all rules for the given target artifact and returns the built source. */
    public SourceCode run(EntitySpec entity, String target) {
        SourceCode code = new SourceCode(entity);
        code.bind(target, templateSet);
        code.artifactName(artifactName);
        if (packageName != null) code.var("packageName", packageName);
        entity.attach(code);
        if (everyRuleSuppressed(target)) {
            code.dontMake();
            return code;
        }

        List<RuleMethod> headRules = new ArrayList<RuleMethod>();
        List<RuleMethod> fieldRules = new ArrayList<RuleMethod>();
        List<RuleMethod> tailRules = new ArrayList<RuleMethod>();
        collect(target, headRules, fieldRules, tailRules);
        // Which rules this run has, so two that share one piece of output can
        // agree on who writes it (SourceCode.hasRule).
        Set<String> active = new HashSet<String>();
        for (RuleMethod rm : headRules) active.add(rm.meta.value());
        for (RuleMethod rm : fieldRules) active.add(rm.meta.value());
        for (RuleMethod rm : tailRules) active.add(rm.meta.value());
        code.activeRules(active);

        for (RuleMethod rm : headRules) {
            invoke(rm, new Object[]{entity});
            if (!code.isMake()) {
                return code;
            }
        }

        for (RuleMethod rm : fieldRules) {
            for (FieldSpec f : entity.fields()) {
                invoke(rm, new Object[]{entity, f});
            }
        }

        for (RuleMethod rm : tailRules) {
            invoke(rm, new Object[]{entity});
        }
        code.projectSkeleton(onlyProjectRules(target));
        return code;
    }

    public SourceCode run(EntitySpec entity) {
        return run(entity, "entity");
    }

    /**
     * Runs the rules of a non-Java artifact whose model is a proto service.
     * Rules take a single {@link ProtoServiceSpec} parameter; the engine owns
     * the iteration over rpcs, messages and enums inside those rules.
     */
    public SourceCode run(ProtoServiceSpec service, String target) {
        SourceCode code = new SourceCode(new EntitySpec(service.getName()));
        code.bind(target, templateSet);
        service.attach(code);
        if (everyRuleSuppressed(target)) {
            code.dontMake();
            return code;
        }
        runSingle(ProtoServiceSpec.class, target, service);
        return code;
    }

    /**
     * Runs the rules of an artifact that exists once per domain rather than
     * once per entity. Rules take a single {@link DomainSpec} parameter and
     * own the iteration over its entities.
     */
    public SourceCode run(DomainSpec domain, String target) {
        SourceCode code = new SourceCode(new EntitySpec(domain.getName()));
        code.bind(target, templateSet);
        // Told which of its kind it is writing, as the entity path already
        // was. It went unnoticed here because every domain artifact was one
        // of a kind - Q, the mapper, the repository helper - until domain
        // services arrived, which are several and had no way to tell
        // themselves apart.
        code.artifactName(artifactName);
        if (packageName != null) code.var("packageName", packageName);
        domain.attach(code);
        if (everyRuleSuppressed(target)) {
            code.dontMake();
            return code;
        }
        runSingle(DomainSpec.class, target, domain);
        code.projectSkeleton(onlyProjectRules(target));
        return code;
    }

    /**
     * Runs the rules of an enumeration artifact. Rules take a single
     * {@link EnumSpec} parameter and own the iteration over its constants.
     */
    public SourceCode run(EnumSpec enumeration, String target) {
        SourceCode code = new SourceCode(new EntitySpec(enumeration.getName()));
        code.bind(target, templateSet);
        enumeration.attach(code);
        if (everyRuleSuppressed(target)) {
            code.dontMake();
            return code;
        }
        runSingle(EnumSpec.class, target, enumeration);
        return code;
    }

    /**
     * Runs the rules of a transaction port. Rules take a single
     * {@link PortSpec} parameter and own the iteration over its operations.
     */
    public SourceCode run(PortSpec port, String target) {
        SourceCode code = new SourceCode(new EntitySpec(port.getName()));
        code.bind(target, templateSet);
        port.attach(code);
        if (everyRuleSuppressed(target)) {
            code.dontMake();
            return code;
        }
        runSingle(PortSpec.class, target, port);
        return code;
    }

    /** Whether the artifact has rules and every one of them is a project's own (not io.elasticore). */
    private boolean onlyProjectRules(String target) {
        boolean any = false;
        for (Object rs : ruleSets) {
            for (Method m : rs.getClass().getDeclaredMethods()) {
                Rule meta = m.getAnnotation(Rule.class);
                if (meta == null || !meta.target().equals(target)) continue;
                if (rs.getClass().getName().startsWith("io.elasticore.")) return false;
                any = true;
            }
        }
        return any;
    }

    /**
     * Whether the artifact has rules and a project suppressed all of them.
     * Nothing then fills its skeleton, and writing it anyway left a file with
     * no package and no class name - so it is not made, as if the rule set
     * that declares it had declined it.
     */
    private boolean everyRuleSuppressed(String target) {
        if (suppressed.isEmpty()) return false;
        boolean any = false;
        for (Object rs : ruleSets) {
            for (Method m : rs.getClass().getDeclaredMethods()) {
                Rule meta = m.getAnnotation(Rule.class);
                if (meta == null || !meta.target().equals(target)) continue;
                if (!suppressed.contains(meta.value())) return false;
                any = true;
            }
        }
        return any;
    }

    private static final java.util.Set<String> NEVER_CALLED =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /** Dispatches the rules of a whole-model artifact, in declared order. */
    private void runSingle(Class<?> specType, String target, Object spec) {
        List<RuleMethod> rules = new ArrayList<RuleMethod>();
        int index = 0;
        for (Object rs : ruleSets) {
            for (Method m : rs.getClass().getDeclaredMethods()) {
                Rule meta = m.getAnnotation(Rule.class);
                if (meta == null) continue;
                if (!meta.target().equals(target)) continue;
                if (suppressed.contains(meta.value())) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 1 && p[0] == specType) {
                    rules.add(new RuleMethod(rs, m, meta, index));
                } else if (p.length >= 1 && p[0] == EntitySpec.class
                        && !rs.getClass().getName().startsWith("io.elasticore.")
                        && NEVER_CALLED.add(rs.getClass().getName() + "#" + m.getName())) {
                    // A project's rule written for an entity on a file made once
                    // per domain: it would never run, and nothing would say so.
                    io.elasticore.base.util.ConsoleLog.printWarn(String.format(
                            "[rules] %s.%s never runs: target '%s' is made once per %s, so the rule must take (%s).",
                            rs.getClass().getSimpleName(), m.getName(), target,
                            specType.getSimpleName().replace("Spec", "").toLowerCase(), specType.getSimpleName()));
                }
            }
            index++;
        }
        Collections.sort(rules, new Comparator<RuleMethod>() {
            @Override
            public int compare(RuleMethod a, RuleMethod b) {
                // A project's rules come after ElastiCORE's in the same stage,
                // whatever their order: what they add or take off then stays
                // as they left it, and their author need not know ours.
                if (a.project() != b.project()) return a.project() ? 1 : -1;
                if (a.meta.order() != b.meta.order())
                    return Integer.compare(a.meta.order(), b.meta.order());
                if (a.registrationIndex != b.registrationIndex)
                    return Integer.compare(a.registrationIndex, b.registrationIndex);
                return a.method.getName().compareTo(b.method.getName());
            }
        });
        for (RuleMethod rm : rules) {
            invoke(rm, new Object[]{spec});
        }
    }

    private void collect(String target, List<RuleMethod> headRules,
                         List<RuleMethod> fieldRules, List<RuleMethod> tailRules) {
        int index = 0;
        for (Object rs : ruleSets) {
            for (Method m : rs.getClass().getDeclaredMethods()) {
                Rule meta = m.getAnnotation(Rule.class);
                if (meta == null) continue;
                if (!meta.target().equals(target)) continue;
                if (suppressed.contains(meta.value())) continue;

                Class<?>[] p = m.getParameterTypes();
                RuleMethod rm = new RuleMethod(rs, m, meta, index);
                if (p.length == 1 && p[0] == EntitySpec.class) {
                    if (meta.phase() == Rule.Phase.TAIL) {
                        tailRules.add(rm);
                    } else {
                        headRules.add(rm);
                    }
                } else if (p.length == 2 && p[0] == EntitySpec.class && p[1] == FieldSpec.class) {
                    fieldRules.add(rm);
                } else if (p.length == 1 && (p[0] == DomainSpec.class || p[0] == EnumSpec.class
                        || p[0] == PortSpec.class || p[0] == ProtoServiceSpec.class)) {
                    // A whole-model rule of the same target - the mapper file
                    // beside its per-entity sections - runs in the other kind
                    // of run, not this one.
                    continue;
                } else {
                    throw new IllegalStateException("Unsupported @Rule signature: "
                            + rs.getClass().getSimpleName() + "#" + m.getName());
                }
            }
            index++;
        }
        // A root split in two has its class the developer works in as the
        // behavior file. A project's rule for the entity - "every root is an
        // @AggregateRoot" - means that class too, and should not have to know
        // the entity was split.
        if ("behavior".equals(target)) {
            int i = 0;
            for (Object rs : ruleSets) {
                if (!rs.getClass().getName().startsWith("io.elasticore.")) {
                    for (Method m : rs.getClass().getDeclaredMethods()) {
                        Rule meta = m.getAnnotation(Rule.class);
                        if (meta == null || !"entity".equals(meta.target())) continue;
                        if (suppressed.contains(meta.value())) continue;
                        Class<?>[] p = m.getParameterTypes();
                        if (p.length != 1 || p[0] != EntitySpec.class) continue;
                        RuleMethod rm = new RuleMethod(rs, m, meta, i, true);
                        if (meta.phase() == Rule.Phase.TAIL) tailRules.add(rm); else headRules.add(rm);
                    }
                }
                i++;
            }
        }
        Comparator<RuleMethod> byOrder = new Comparator<RuleMethod>() {
            @Override
            public int compare(RuleMethod a, RuleMethod b) {
                // A project's rules come after ElastiCORE's in the same stage,
                // whatever their order: what they add or take off then stays
                // as they left it, and their author need not know ours.
                if (a.project() != b.project()) return a.project() ? 1 : -1;
                if (a.meta.order() != b.meta.order())
                    return Integer.compare(a.meta.order(), b.meta.order());
                if (a.registrationIndex != b.registrationIndex)
                    return Integer.compare(a.registrationIndex, b.registrationIndex);
                return a.method.getName().compareTo(b.method.getName());
            }
        };
        Collections.sort(headRules, byOrder);
        Collections.sort(fieldRules, byOrder);
        Collections.sort(tailRules, byOrder);
    }

    private void invoke(RuleMethod rm, Object[] args) {
        boolean project = !rm.ruleSet.getClass().getName().startsWith("io.elasticore.");
        try {
            rm.method.setAccessible(true);
            if (project) SourceCode.PROJECT_RULE.set(Boolean.TRUE);
            if (rm.borrowedForBehavior) SourceCode.BORROWED_ENTITY_RULE.set(Boolean.TRUE);
            rm.method.invoke(rm.ruleSet, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access rule " + rm.meta.value(), e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Rule '" + rm.meta.value() + "' failed: "
                    + e.getTargetException().getMessage(), e.getTargetException());
        } finally {
            if (project) SourceCode.PROJECT_RULE.remove();
            if (rm.borrowedForBehavior) SourceCode.BORROWED_ENTITY_RULE.remove();
        }
    }
}
