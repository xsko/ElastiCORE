package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.model.pub.rules.base.ServiceAccessRules;
import io.elasticore.base.model.pub.rules.ddd.DddCommandRules;
import io.elasticore.base.model.pub.rules.ddd.DddStateRules;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The actions part of GET /schema/info from the model (issue 373), as the generator records it: the operations the
 * controller serves, those a row's state decides, and what the service checks with - its AccessPolicy arguments,
 * the owner it compares, a create a visitor cannot key.
 */
public class SchemaActionsTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    private EntitySpec spec(String name, String... fieldLines) {
        EntitySpec e = new EntitySpec(name).domain("shop", "Shop").apiPath("/api");
        for (int i = 0; i < fieldLines.length; i += 2) e.addField(loader.parse(fieldLines[i], fieldLines[i + 1]));
        return e;
    }

    /** A controller as a run leaves it: the sections dropped, the rules it had. */
    private static SourceCode code(EntitySpec e, List<String> rules, String... excluded) {
        return code(SourceCode.TemplateSet.V3, e, rules, excluded);
    }

    private static SourceCode code(SourceCode.TemplateSet set, EntitySpec e, List<String> rules, String... excluded) {
        SourceCode c = new SourceCode(e);
        c.bind("control", set);
        c.excludeMethod(excluded);
        c.activeRules(new HashSet<String>(rules));
        return c;
    }

    @Test
    public void whatTheControllerServes() {
        EntitySpec order = spec("Order", "orderId", "long @id");
        List<String> none = Collections.emptyList();
        assertEquals(
                Arrays.asList("read", "create", "update", "delete"), SchemaActions.served(order, code(order, none)));
        // @readonly, @expose(methods="read,search"): only what is left.
        assertEquals(
                Arrays.asList("read"),
                SchemaActions.served(
                        order, code(order, none, "create", "update", "batchUpdate", "deleteBatch", "deleteById")));
        // Without PUT there is no PUT /batch, without DELETE /{key} no POST /delete: the batches call them, and go
        // with them (SourceCode.withCallers). The one-row endpoint alone is enough (below).
        assertTrue(SchemaActions.served(order, code(order, none, "read", "search", "create", "update", "deleteById"))
                .isEmpty());
        assertEquals(
                Arrays.asList("read", "update", "delete"),
                SchemaActions.served(order, code(order, none, "read", "create", "batchUpdate", "deleteBatch")));
        // Where a batch stands without its one-row endpoint (a skeleton that drops only what it is told), it is enough.
        assertEquals(
                Arrays.asList("update", "delete"),
                SchemaActions.served(
                        order,
                        code(
                                SourceCode.TemplateSet.COMPATIBLE,
                                order,
                                none,
                                "read",
                                "search",
                                "create",
                                "update",
                                "deleteById")));
        assertEquals(
                Arrays.asList("read", "create", "delete"), SchemaActions.served(order, code(order, none, "update")));
        assertEquals(
                Arrays.asList("read", "create", "update"),
                SchemaActions.served(order, code(order, none, "deleteById")));
        assertEquals(
                Arrays.asList("read"),
                SchemaActions.served(
                        order,
                        code(order, none, "search", "create", "update", "batchUpdate", "deleteBatch", "deleteById")));
        // No section that reads: read is left out.
        assertEquals(
                Arrays.asList("create"),
                SchemaActions.served(
                        order,
                        code(order, none, "read", "search", "update", "batchUpdate", "deleteBatch", "deleteById")));
        // POST /export reads too, where @expose(methods) lists it and no rule set took it out.
        order.meta("expose", "").metaProperty("expose", "methods", "export");
        String[] noneServed = {"read", "search", "create", "update", "batchUpdate", "deleteBatch", "deleteById"};
        assertEquals(Arrays.asList("read"), SchemaActions.served(order, code(order, none, noneServed)));
        String[] noExport = Arrays.copyOf(noneServed, noneServed.length + 1);
        noExport[noneServed.length] = "export";
        assertTrue(SchemaActions.served(order, code(order, none, noExport)).isEmpty());
    }

    /** DELETE /{key} alone reads the row first; with POST /delete, or with no delete at all, nothing is read first. */
    @Test
    public void aDeleteThatReadsTheRowFirst() {
        EntitySpec order = spec("Order", "orderId", "long @id");
        List<String> none = Collections.emptyList();
        assertFalse(SchemaActions.deleteReadsFirst(code(order, none)));
        assertTrue(SchemaActions.deleteReadsFirst(code(order, none, "deleteBatch")));
        assertFalse(SchemaActions.deleteReadsFirst(code(order, none, "deleteById")));
        assertFalse(SchemaActions.deleteReadsFirst(code(SourceCode.TemplateSet.COMPATIBLE, order, none, "deleteById")));
    }

    @Test
    public void commandsWhereTheirRulesWroteTheEndpoints() {
        EntitySpec order = spec("Order", "orderId", "long @id");
        order.addCommand("ship", null).addCommand("cancel", null);
        // Not a root the behaviour rules split (layered): no command endpoint, whatever the rules.
        assertFalse(DddCommandRules.serves(order));
        assertEquals(
                Arrays.asList("read", "create", "update", "delete"),
                SchemaActions.served(order, code(order, Arrays.asList(DddCommandRules.CONTROL_RULE))));
        assertFalse(DddCommandRules.serves(spec("Plain", "plainId", "long @id")));
    }

    @Test
    public void aStateDecidesOnlyWhereTheLifecycleRulesRan() {
        EntitySpec order = spec("Order", "orderId", "long @id", "status", "OrderStatus");
        order.addCommand("ship", null);
        Map<String, String> states = new LinkedHashMap<String, String>();
        states.put("initial", "PLACED");
        states.put("ship", "PLACED -> SHIPPED");
        states.put("delete", "SHIPPED");
        order.addStateMachine("status", states);
        // Without the lifecycle rules (layered), or not split: nothing the state decides.
        assertTrue(SchemaActions.stateBound(order, code(order, Collections.<String>emptyList()))
                .isEmpty());
        assertTrue(SchemaActions.stateBound(
                        order, code(order, Arrays.asList(DddStateRules.MOVES_RULE, DddStateRules.DELETE_RULE)))
                .isEmpty());
    }

    @Test
    public void theServiceChecksWhereItsRuleRanAndHasSomethingToCheck() {
        EntitySpec open = spec("Open", "openId", "long @id");
        EntitySpec guarded = spec("Guarded", "guardedId", "long @id");
        guarded.meta("access", "read=public");
        List<String> rule = Arrays.asList(ServiceAccessRules.SERVICE_RULE);
        assertTrue(SchemaActions.checked(guarded, code(guarded, rule)));
        assertFalse(SchemaActions.checked(open, code(open, rule)));
        assertFalse(SchemaActions.checked(guarded, code(guarded, Collections.<String>emptyList())));
    }

    @Test
    public void theFactsAreTheServicesOwn() {
        EntitySpec order = spec("Order", "orderId", "long @id @sequence", "customerId", "long");
        order.meta("access", "read=owner|MANAGER, cancel=owner").meta("owner", "customerId");
        order.config("adminRole", "BOSS");
        SchemaActions.Facts facts = SchemaActions.of(
                order, Arrays.asList("read", "create", "cancel"), Collections.singleton("cancel"), true, true);
        Map<String, Object> json = SchemaActions.json(facts);
        assertEquals(
                Arrays.asList("policy", "owner", "createNeedsSignIn", "deleteReadsFirst", "operations"),
                Arrays.asList(json.keySet().toArray()));
        assertEquals(Boolean.TRUE, json.get("deleteReadsFirst"));
        Map<?, ?> policy = (Map<?, ?>) json.get("policy");
        ServiceAccessRules.PolicyArguments service = ServiceAccessRules.policyArguments(order);
        assertEquals(service.declaration(), policy.get("declaration"));
        assertEquals("BOSS", policy.get("adminRole"));
        assertEquals("customerId", json.get("owner"));
        assertEquals(Boolean.FALSE, json.get("createNeedsSignIn"));
        Map<?, ?> operations = (Map<?, ?>) json.get("operations");
        assertEquals(
                Arrays.asList("read", "create", "cancel"),
                Arrays.asList(operations.keySet().toArray()));
        assertEquals(Collections.emptyList(), operations.get("read"));
        assertEquals(Arrays.asList("state"), operations.get("cancel"));

        // A service that checks nothing: no policy, no owner - the runtime lets everyone in.
        Map<String, Object> open = SchemaActions.json(
                SchemaActions.of(order, Arrays.asList("read"), Collections.<String>emptySet(), false, false));
        assertNull(open.get("policy"));
        assertEquals(Boolean.FALSE, open.get("deleteReadsFirst"));
        assertEquals(
                Boolean.TRUE,
                SchemaActions.json(SchemaActions.of(
                                order, Arrays.asList("delete"), Collections.<String>emptySet(), true, false))
                        .get("deleteReadsFirst"));
        assertNull(open.get("owner"));
        assertEquals(Boolean.FALSE, open.get("createNeedsSignIn"));

        // Without @access: the service's policy has no declaration (AccessPolicy: everything public).
        EntitySpec owned = spec("Note", "noteId", "long @id @sequence", "writer", "long");
        owned.meta("owner", "writer");
        Map<?, ?> undeclared = (Map<?, ?>) SchemaActions.json(
                        SchemaActions.of(owned, Arrays.asList("read"), Collections.<String>emptySet(), false, true))
                .get("policy");
        assertTrue(undeclared.containsKey("declaration"));
        assertNull(undeclared.get("declaration"));
        assertEquals("ADMIN", undeclared.get("adminRole"));
    }

    @Test
    public void theOwnerTheServiceCompares() {
        // A long, int, string or UUID identity is compared; anything else is not, nor a name nothing has.
        EntitySpec e = spec("Note", "noteId", "long @id @sequence", "writer", "long", "when", "date");
        e.meta("owner", "writer");
        assertEquals("writer", ServiceAccessRules.comparedOwner(e).name());
        e.meta("owner", "when");
        assertNull(ServiceAccessRules.comparedOwner(e));
        e.meta("owner", "nobody");
        assertNull(ServiceAccessRules.comparedOwner(e));
        EntitySpec none = spec("Free", "freeId", "long @id");
        assertNull(ServiceAccessRules.comparedOwner(none));
        assertFalse(ServiceAccessRules.createNeedsSignIn(none));
    }

    @Test
    public void aRowKeyedByItsOwnerNeedsSomeoneToKeyItBy() {
        // @owner on a key the request gives: a visitor has no identity to key a new row by.
        EntitySpec member = spec("Member", "login", "string(20) @id");
        member.meta("owner", "login");
        assertTrue(ServiceAccessRules.createNeedsSignIn(member));
        assertEquals(
                Boolean.TRUE,
                SchemaActions.json(SchemaActions.of(
                                member, Arrays.asList("create"), Collections.<String>emptySet(), false, true))
                        .get("createNeedsSignIn"));
        // A generated key is the server's: the owner is claimed, and nothing needs signing in for it.
        EntitySpec customer = spec("Customer", "customerId", "long @id @sequence");
        customer.meta("owner", "customerId");
        assertFalse(ServiceAccessRules.createNeedsSignIn(customer));
        // An owner that is not the key: claimed, not keyed by.
        EntitySpec order = spec("Order", "orderId", "string(20) @id", "customerId", "long");
        order.meta("owner", "customerId");
        assertFalse(ServiceAccessRules.createNeedsSignIn(order));
    }
}
