package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.pub.rules.base.ControllerBaseRules;
import io.elasticore.base.model.pub.rules.base.ServiceAccessRules;
import io.elasticore.base.model.pub.rules.ddd.DddCommandRules;
import io.elasticore.base.model.pub.rules.ddd.DddStateRules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The actions part of GET /schema/info from the model (env.yml {@code config: schemaInfo: model}, issue 373): what
 * the signed-in user of the request may do to the entity - read, create, update, delete and each command the
 * controller serves. The runtime (SchemaInfo) decides it per request with the check the service applies:
 * AccessPolicy.parse of the service's own arguments, require and owns, for the CurrentUser the service takes.
 *
 * The generator records only the facts that check is written from, as the run wrote them:
 * <ul>
 *   <li>the operations the controller serves: read (GET /{key}, POST /search or POST /export), create (POST),
 *       update (PUT, PUT /batch), delete (DELETE /{key}, POST /delete), then each command (ddd, hexagonal);</li>
 *   <li>whether the service checks anything (ServiceAccessRules) - and, where it does, the arguments of its
 *       AccessPolicy (policyArguments), the @owner identity it compares a row by, and whether create needs a
 *       signed-in caller (an entity keyed by its owner);</li>
 *   <li>the operations a row's state decides: a command a lifecycle moves (refused, 409, in a state it does not
 *       start in) and a delete the lifecycle guards (states: delete).</li>
 * </ul>
 */
public final class SchemaActions {

    private SchemaActions() {}

    public static final String READ = "read";
    public static final String CREATE = "create";
    public static final String UPDATE = "update";
    public static final String DELETE = "delete";

    /** A condition a row's own state sets, as the answer names it. */
    public static final String STATE = "state";

    /** What the actions of one entity are decided from. */
    public static final class Facts {
        final List<String> operations;
        final Set<String> stateBound;
        final ServiceAccessRules.PolicyArguments policy;
        final String owner;
        final boolean createNeedsSignIn;
        final boolean deleteReadsFirst;

        /**
         * @param operations        the operations the controller serves, in the answer's order
         * @param stateBound        those a row's state decides
         * @param policy            the service's AccessPolicy arguments; null when the service checks nothing
         * @param owner             the @owner identity the service compares; null when it compares none
         * @param createNeedsSignIn whether create refuses a visitor who is not the administrator
         * @param deleteReadsFirst  whether delete is served only as DELETE /{key}, which reads the row first
         */
        public Facts(
                List<String> operations,
                Collection<String> stateBound,
                ServiceAccessRules.PolicyArguments policy,
                String owner,
                boolean createNeedsSignIn,
                boolean deleteReadsFirst) {
            this.operations = operations;
            this.stateBound = new LinkedHashSet<String>(stateBound);
            this.policy = policy;
            this.owner = owner;
            this.createNeedsSignIn = createNeedsSignIn;
            this.deleteReadsFirst = deleteReadsFirst;
        }
    }

    /**
     * The facts of an entity: the operations its controller serves, those its state decides, whether its delete
     * reads the row first, and - where its service checks (checked) - what the service checks with.
     */
    public static Facts of(
            EntitySpec e,
            List<String> operations,
            Collection<String> stateBound,
            boolean deleteReadsFirst,
            boolean checked) {
        if (!checked) return new Facts(operations, stateBound, null, null, false, deleteReadsFirst);
        FieldSpec owner = ServiceAccessRules.comparedOwner(e);
        return new Facts(
                operations,
                stateBound,
                ServiceAccessRules.policyArguments(e),
                owner == null ? null : owner.name(),
                ServiceAccessRules.createNeedsSignIn(e),
                deleteReadsFirst);
    }

    /**
     * Whether the controller deletes only through DELETE /{key}, which asks the service for the row first (read)
     * and then deletes it (delete) - no POST /delete, which asks delete alone.
     */
    public static boolean deleteReadsFirst(SourceCode control) {
        return serves(control, "deleteById") && !serves(control, "deleteBatch");
    }

    /** Whether the service written for the entity checks anything: the access rule ran, and had something to do. */
    public static boolean checked(EntitySpec e, SourceCode service) {
        return service.hasRule(ServiceAccessRules.SERVICE_RULE) && ServiceAccessRules.guarded(e);
    }

    /**
     * What a controller serves, as the run wrote it: of read, create, update and delete each whose endpoint it
     * has, then each command its rules wrote an endpoint for.
     */
    public static List<String> served(EntitySpec e, SourceCode control) {
        List<String> out = new ArrayList<String>();
        boolean exported = ControllerBaseRules.exports(e) && !control.isMethodExcluded(ControllerBaseRules.EXPORT);
        if (serves(control, "read") || serves(control, "search") || exported) out.add(READ);
        if (serves(control, "create")) out.add(CREATE);
        // Either endpoint of each pair; the batch is dropped with the one-row method today (SourceCode.withCallers).
        if (serves(control, "update") || serves(control, "batchUpdate")) out.add(UPDATE);
        if (serves(control, "deleteById") || serves(control, "deleteBatch")) out.add(DELETE);
        if (control.hasRule(DddCommandRules.CONTROL_RULE) && DddCommandRules.serves(e)) {
            for (EntitySpec.CommandSpec cmd : e.commands()) {
                if (!out.contains(cmd.name())) out.add(cmd.name());
            }
        }
        return out;
    }

    private static boolean serves(SourceCode control, String sectionId) {
        return !control.isMethodExcluded(sectionId);
    }

    /**
     * The operations a row's state decides, as the entity's rules wrote them: each command the lifecycle moves,
     * and delete where the lifecycle says when a row may be deleted.
     */
    public static Set<String> stateBound(EntitySpec e, SourceCode entity) {
        Set<String> out = new LinkedHashSet<String>();
        if (entity.hasRule(DddStateRules.MOVES_RULE)) {
            for (EntitySpec.CommandSpec cmd : e.commands()) {
                if (DddStateRules.writesMove(e, cmd.name())) out.add(cmd.name());
            }
        }
        if (entity.hasRule(DddStateRules.DELETE_RULE) && DddStateRules.guardsDelete(e)) out.add(DELETE);
        return out;
    }

    /**
     * The file's actions entry: {@code {policy, owner, createNeedsSignIn, deleteReadsFirst, operations}}. {@code policy} is
     * {@code {declaration, adminRole}}, null where the service checks nothing; {@code operations} names each
     * served operation with the conditions its row sets ({@code ["state"]}, or none).
     */
    public static Map<String, Object> json(Facts f) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        Map<String, Object> policy = null;
        if (f.policy != null) {
            policy = new LinkedHashMap<String, Object>();
            policy.put("declaration", f.policy.declaration());
            policy.put("adminRole", f.policy.adminRole());
        }
        out.put("policy", policy);
        out.put("owner", f.owner);
        out.put("createNeedsSignIn", f.createNeedsSignIn);
        out.put("deleteReadsFirst", f.deleteReadsFirst);
        Map<String, Object> operations = new LinkedHashMap<String, Object>();
        for (String op : f.operations) {
            operations.put(
                    op,
                    f.stateBound.contains(op)
                            ? Collections.<Object>singletonList(STATE)
                            : Collections.<Object>emptyList());
        }
        out.put("operations", operations);
        return out;
    }
}
