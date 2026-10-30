package io.elasticore.base.model.pub.rules.ddd;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.Rule;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The aggregate boundary, applied to what gets generated.
 *
 * JPA lets anything with a table have a repository. A domain model does not:
 * an aggregate is loaded and saved through its root, and an entity inside one
 * is reached by navigating from that root rather than fetched on its own.
 * Where the two disagree, this is the rule that decides.
 *
 * Nothing new has to be declared for it. The DSL already says which entities
 * live and die together - a cascading relation is exactly that statement -
 * and {@link EntitySpec#aggregateRoot()} reads the boundary off it.
 *
 * These rules decline rather than emit, so a bundle that includes them
 * subtracts from what the JPA bundle would have produced. That is the whole
 * mechanism: no mode flag, no branch in a shared rule.
 *
 * What this cannot do is the part that matters most. A domain model is
 * behaviour on the aggregate, and the DSL declares data - so what is generated
 * is the boundary and a place to write the behaviour, not the behaviour.
 */
public class DddAggregateRules implements CodeRules {

    /** Only a root is fetched by identity, so only a root has a repository. */
    @Rule(value = "ddd-repository", target = "repository", order = 1)
    void repository(EntitySpec e) {
        declineUnlessRoot(e);
    }

    /** A member is changed through the root that owns it. */
    @Rule(value = "ddd-service", target = "service", order = 1)
    void service(EntitySpec e) {
        declineUnlessRoot(e);
    }

    @Rule(value = "ddd-service-extend", target = "service_extend", order = 1)
    void serviceExtend(EntitySpec e) {
        declineUnlessRoot(e);
    }

    /** Nor does a member get an endpoint of its own. */
    @Rule(value = "ddd-controller", target = "control", order = 1)
    void controller(EntitySpec e) {
        declineUnlessRoot(e);
    }

    @Rule(value = "ddd-service-test", target = "service_test", order = 1)
    void serviceTest(EntitySpec e) {
        declineUnlessRoot(e);
    }

    /**
     * The helper holds a domain's repositories, and under this reading the
     * domain has one per root. Runs before helper-repositories fills the
     * slot, since a slot is filled once and not edited afterwards.
     *
     * Without this the helper would inject repositories the rules above
     * declined to write - which is how the Kotlin compiler found it.
     */
    @Rule(value = "ddd-repository-helper", target = "repositoryHelper", order = 15)
    void repositoryHelper(io.elasticore.base.model.pub.rules.DomainSpec d) {
        // Over MyBatis a root's adapter writes its members through their
        // mappers, so the helper keeps the members a root holds.
        java.util.Set<String> held = new java.util.HashSet<String>();
        if (io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.applies(d)) {
            for (EntitySpec e : d.entities()) {
                for (io.elasticore.base.model.pub.rules.FieldSpec f
                        : io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.fields(e)) {
                    if (io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.isMemberCollection(e, f)
                            || io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.isMemberReference(e, f)) {
                        held.add(f.coreItemType());
                    }
                }
            }
        }
        java.util.Iterator<EntitySpec> it = d.entities().iterator();
        while (it.hasNext()) {
            EntitySpec e = it.next();
            if (!e.aggregateRoot() && !held.contains(e.getName())) it.remove();
        }
    }

    /**
     * A member is not queried on its own, so it has no search DTO.
     *
     * Left in place it would be a type saying you may search for something you
     * cannot reach - and nothing would use it, since the service and the
     * endpoint that would take it are already gone.
     */
    @Rule(value = "ddd-search-dto", target = "srchdto", order = 1)
    void searchDto(EntitySpec e) {
        declineUnlessRoot(e);
    }

    /**
     * The key DTO exists to be handed to delete(List&lt;KeyDTO&gt;), which is
     * an endpoint a member does not have.
     */
    @Rule(value = "ddd-key-dto", target = "dto", order = 1)
    void keyDto(EntitySpec e) {
        if (e.keyDtoClassName() != null && !e.aggregateRoot()) ctx(e).dontMake();
    }

    private void declineUnlessRoot(EntitySpec e) {
        if (!e.aggregateRoot()) ctx(e).dontMake();
    }

}
