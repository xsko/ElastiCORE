package io.elasticore.base.model.pub.rules.mybatis;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The domain rules over MyBatis: what JPA did for an aggregate by itself,
 * said in the code instead (bundles ddd-mybatis-springboot and
 * hexagonal-mybatis-springboot).
 *
 * The domain rules write a root's moments as JPA marks them - @PrePersist and
 * @PreUpdate on its invariants listener, @PreRemove on the flag that tells its
 * members it is being deleted and on its lifecycle's delete guard, @PostLoad
 * and @PostPersist on the flag that seals its creation values, Spring Data's
 * @DomainEvents on the events it raised. MyBatis calls none of them. So the
 * root implements io.elasticore.springboot3.domain.Lifecycle, whose methods
 * call the same code, and the generated repository adapter
 * (MyBatisPortWriter) calls those where JPA would; the annotations go, and
 * with them every import of JPA and Spring Data from the domain.
 *
 * A member's own moments are its root's to keep: the adapter checks the
 * root's invariants on every save, which is what a member's removal check
 * asked for.
 */
public class MyBatisDddRules implements CodeRules {

    /** The JPA callbacks a class body may carry, and the Lifecycle method that calls the method under each. */
    private static final Map<String, String> CALLBACKS = new LinkedHashMap<String, String>();

    static {
        CALLBACKS.put("PrePersist", "beforeInsert");
        CALLBACKS.put("PreUpdate", "beforeUpdate");
        CALLBACKS.put("PreRemove", "beforeDelete");
        CALLBACKS.put("PostLoad", "afterLoad");
        CALLBACKS.put("PostPersist", "afterInsert");
        CALLBACKS.put("DomainEvents", "pendingEvents");
        CALLBACKS.put("AfterDomainEventPublication", "eventsPublished");
    }

    private static final Pattern ANNOTATION = Pattern.compile(
            "^\\s*@((?:jakarta|javax)\\.persistence\\.|org\\.hibernate\\.[a-z.]*|org\\.springframework\\.data\\.[a-z.]*)?([A-Z][A-Za-z]*)(\\(.*\\))?\\s*$");
    private static final Pattern METHOD = Pattern.compile("^\\s*(?:public |protected |private )?[\\w.<>, ]+\\s(\\w+)\\(\\s*\\)\\s*\\{\\s*$");

    /** Where the runner keeps a root's listeners between the two entity rules. */
    private static final String LISTENERS = "mybatis.listeners";

    /** Whether the aggregate has moments the adapter calls: a root of a MyBatis domain. */
    static boolean livesThroughLifecycle(EntitySpec e) {
        return MyBatisSql.applies(e) && e.aggregateRoot() && !e.hasMeta("abstract") && !e.hasMeta("embeddable");
    }

    /**
     * Before the file shell renders the class annotations (90): the entity
     * listeners the domain rules named go - JPA's mechanism - and are
     * remembered, so the root calls them itself.
     */
    @Rule(value = "mybatis-ddd-listeners", target = "entity", order = 89, phase = Rule.Phase.TAIL)
    void listeners(EntitySpec e) {
        if (!MyBatisSql.applies(e)) return;
        SourceCode c = ctx(e);
        if (c.syntax() != Syntax.JAVA) return;
        c.var(LISTENERS, String.join(",", c.entityListeners()));
        c.removeAnnotations(new java.util.function.Predicate<String>() {
            public boolean test(String fqcn) {
                return MyBatisEntityFileRules.persistenceOnly(fqcn);
            }
        });
    }

    /**
     * After every rule that writes the class body (the lifecycle's delete
     * guard is the last, at 99): the JPA and Spring Data annotations go from
     * the body and the imports, and a root implements Lifecycle with the
     * methods they marked.
     */
    @Rule(value = "mybatis-ddd-lifecycle", target = "entity", order = 120, phase = Rule.Phase.TAIL)
    void lifecycle(EntitySpec e) {
        if (!MyBatisSql.applies(e)) return;
        SourceCode c = ctx(e);
        if (c.syntax() != Syntax.JAVA) return;

        final Map<String, List<String>> hooks = new LinkedHashMap<String, List<String>>();
        final String[] pending = {null};
        c.rewriteVarLines("fieldList", line -> {
            Matcher a = ANNOTATION.matcher(line);
            if (a.matches() && persistenceAnnotation(a.group(1), a.group(2))) {
                String hook = CALLBACKS.get(a.group(2));
                if (hook != null) pending[0] = hook;
                return null;
            }
            if (pending[0] != null) {
                Matcher m = METHOD.matcher(line);
                if (m.matches()) {
                    String call = "pendingEvents".equals(pending[0]) ? "return " + m.group(1) + "();" : m.group(1) + "();";
                    hooks.computeIfAbsent(pending[0], k -> new ArrayList<String>()).add(call);
                    pending[0] = null;
                }
            }
            return line;
        });
        String imports = c.varValue("importList");
        if (imports != null) c.var("importList", withoutPersistence(imports));

        if (!livesThroughLifecycle(e)) return;
        String root = e.getName();
        String listeners = c.varValue(LISTENERS);
        if (listeners != null && !listeners.isEmpty()) {
            for (String listener : listeners.split(",")) {
                if (listener.equals(root + "Invariants")) {
                    String check = String.format("new %s().check((%s) this);", listener, root);
                    hooks.computeIfAbsent("beforeInsert", k -> new ArrayList<String>()).add(0, check);
                    hooks.computeIfAbsent("beforeUpdate", k -> new ArrayList<String>()).add(0, check);
                } else if (!listener.equals("AuditingEntityListener")) {
                    io.elasticore.base.model.pub.rules.ModelCheck.warn(String.format(
                            "[%s] %s: the entity listener %s is JPA's to call; under mode: mybatis nothing calls it",
                            e.domainClassName(), root, listener));
                }
            }
        }
        if (hooks.isEmpty()) return;

        String declaration = c.varValue("classDeclaration");
        if (declaration != null && declaration.contains(" implements ")) {
            c.var("classDeclaration", declaration.replace(" implements ",
                    " implements io.elasticore.springboot3.domain.Lifecycle, "));
        } else if (declaration != null) {
            c.var("classDeclaration", declaration.replaceFirst("\\s*\\{\\s*$",
                    " implements io.elasticore.springboot3.domain.Lifecycle {"));
        }
        c.varLine("fieldList", "")
         .varLine("fieldList", "// The moments JPA would mark; the MyBatis repository adapter calls them.");
        for (Map.Entry<String, List<String>> h : hooks.entrySet()) {
            String signature = "pendingEvents".equals(h.getKey())
                    ? "public java.util.Collection<Object> pendingEvents() {"
                    : "public void " + h.getKey() + "() {";
            c.varLine("fieldList", "")
             .varLine("fieldList", "@Override")
             .varLine("fieldList", "%s", signature);
            for (String call : h.getValue()) c.varLine("fieldList", "    %s", call);
            c.varLine("fieldList", "}");
        }
    }

    /**
     * The application service of a root with ports: the domain's port for
     * its life, the application's for its searches, the Lookup for what it
     * refers to (v3/mybatis_port_service.tmpl). After the MyBatis service's
     * variables (10), before the ports are handed over (15). The reply to a
     * write is the aggregate read back, as the layered MyBatis service's is.
     */
    @Rule(value = "mybatis-ddd-service", target = "service", order = 11)
    void portService(EntitySpec e) {
        if (!MyBatisSql.applies(e) || !io.elasticore.base.model.pub.rules.ddd.DddRepositoryPortRules.applies(e)) return;
        SourceCode c = ctx(e);
        if (c.syntax() != Syntax.JAVA) return;
        c.template("mybatis_port_service");
        c.slot("deriveBeforeSave");
        String root = e.getName();
        c.var("readBackOpen", "readBack(").var("readBackClose", ")");
        c.addMethod(String.format(
                "    /** The aggregate as stored, read again after a save: with what the database filled in and the rows it points at. */%n"
                        + "    private %1$s readBack(%1$s saved) {%n"
                        + "        return repository.findById(%2$s).orElse(saved);%n"
                        + "    }", root, c.syntax().getter("saved", e.pkField().name())).replace("\r\n", "\n"));
    }

    /** The concrete class of a split root, written once: no table to name under MyBatis. */
    @Rule(value = "mybatis-ddd-behavior", target = "behavior", order = 20)
    void behavior(EntitySpec e) {
        if (!MyBatisSql.applies(e)) return;
        SourceCode c = ctx(e);
        c.var("classAnnotationList", "").var("importList", "");
    }

    /** The invariants class: the check stays, its JPA marks go - the root calls it. */
    @Rule(value = "mybatis-ddd-invariants", target = "invariants", order = 120)
    void invariants(EntitySpec e) {
        plainBody(e);
    }

    /** A member's removal check: the root's invariants, checked by the adapter on every save. */
    @Rule(value = "mybatis-ddd-removal", target = "removalCheck", order = 120)
    void removalCheck(EntitySpec e) {
        plainBody(e);
    }

    private static void plainBody(EntitySpec e) {
        if (!MyBatisSql.applies(e)) return;
        SourceCode c = ctx(e);
        if (c.syntax() != Syntax.JAVA) return;
        c.rewriteVarLines("bodyList", line -> {
            Matcher a = ANNOTATION.matcher(line);
            return a.matches() && persistenceAnnotation(a.group(1), a.group(2)) ? null : line;
        });
        String imports = c.varValue("importList");
        if (imports != null) c.var("importList", withoutPersistence(imports));
        String annotations = c.varValue("classAnnotationList");
        if (annotations != null) c.var("classAnnotationList", withoutPersistence(annotations));
    }

    /** Whether an annotation line is JPA's or Spring Data's: a qualified name of theirs, or a callback named bare. */
    private static boolean persistenceAnnotation(String qualifier, String name) {
        if (qualifier != null) return true;
        return CALLBACKS.containsKey(name) || "Transient".equals(name) || "PostUpdate".equals(name)
                || "PostRemove".equals(name);
    }

    /** Lines of text without those that import or name JPA, Hibernate or Spring Data. */
    static String withoutPersistence(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\\r?\\n", -1)) {
            String t = line.trim();
            if (t.startsWith("import ") && MyBatisEntityFileRules.persistenceOnly(t.substring(7).replace(";", "").trim())) continue;
            if (t.startsWith("@jakarta.persistence.") || t.startsWith("@javax.persistence.")
                    || t.startsWith("@org.hibernate.") || t.startsWith("@EntityListeners")) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }
}
