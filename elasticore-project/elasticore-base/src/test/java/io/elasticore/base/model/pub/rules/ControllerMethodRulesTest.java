package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.model.pub.rules.base.ControllerBaseRules;
import org.junit.jupiter.api.Test;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Endpoint-level freedom for the controller artifact: the template remains
 * the single source of the method shapes, while DSL meta annotations and
 * user rules decide which methods exist for a given entity.
 *
 * The default path (no exclusions, no additions) stays byte-identical to
 * the template output - proven by ArtifactChainPrototypeTest and the
 * elcoreRulesCheck gate, which both keep passing unchanged.
 */
public class ControllerMethodRulesTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    private EntitySpec order() {
        String base = "io.elasticore.blueprint.domain.shop";
        return new EntitySpec("Order")
                .domain("shop", "Shop")
                .apiPath("/api/v1")
                .pkg("dto", base + ".dto")
                .pkg("service", base + ".service")
                .pkg("control", base + ".controller")
                .addField(loader.parse("orderId", "long @id @sequence"));
    }

    private String render(EntitySpec e, CodeRules... extraRules) {
        RuleEngine engine = new RuleEngine().register(new ControllerBaseRules());
        for (CodeRules r : extraRules) engine.register(r);
        SourceCode code = engine.run(e, "control");
        return code.render();
    }

    /** The 3.0 skeleton: @expose(methods) is read by the 3.0 output only. */
    private String renderV3(EntitySpec e) {
        return new RuleEngine()
                .templateSet(SourceCode.TemplateSet.V3)
                .register(new ControllerBaseRules())
                .run(e, "control")
                .render();
    }

    // ------------------------------------------------------------------

    @Test
    public void restExclude_dropsListedMethods() {
        EntitySpec e = order().meta("restexclude", "deleteById,batchUpdate");

        String out = render(e);

        assertFalse(out.contains("@DeleteMapping"), "deleteById must be dropped:\n" + out);
        assertFalse(out.contains("@PutMapping(\"/batch\")"), "batchUpdate must be dropped:\n" + out);
        // the rest survives
        assertTrue(out.contains("@PostMapping\n"), out);
        assertTrue(out.contains("@PostMapping(\"/search\")"), out);
        assertTrue(out.contains("@PostMapping(\"/delete\")"), out);
        assertTrue(out.contains("@GetMapping(\"/schema/info\")"), out);
    }

    @Test
    public void readonly_keepsOnlyReadEndpoints() {
        EntitySpec e = order().meta("readonly", null);

        String out = render(e);

        assertFalse(out.contains("@PostMapping\n"), "create must be dropped:\n" + out);
        assertFalse(out.contains("@PutMapping"), "update/batch must be dropped:\n" + out);
        assertFalse(out.contains("@DeleteMapping"), "delete must be dropped:\n" + out);
        assertFalse(out.contains("@PostMapping(\"/delete\")"), out);
        assertTrue(out.contains("@GetMapping(\"/{orderId}\")"), out);
        assertTrue(out.contains("@PostMapping(\"/search\")"), out);
        assertTrue(out.contains("@GetMapping(\"/schema/info\")"), out);
        // class skeleton intact
        assertTrue(out.trim().endsWith("}"), out);
    }

    @Test
    public void exposeMethods_keepsOnlyTheListedEndpoints() {
        EntitySpec e = order().meta("expose", "20").metaProperty("expose", "methods", "read,search");

        // The 2.x-compatible output does not read it.
        String compatible = render(e);
        assertTrue(compatible.contains("@DeleteMapping") && compatible.contains("@PutMapping"), compatible);

        String out = renderV3(e);

        assertTrue(out.contains("@GetMapping(\"/{orderId}\")"), out);
        assertTrue(out.contains("@PostMapping(\"/search\")"), out);
        assertTrue(out.contains("@GetMapping(\"/schema/info\")"), out);
        assertFalse(out.contains("@PostMapping\n"), "create must be dropped:\n" + out);
        assertFalse(out.contains("@PutMapping"), "update and batchUpdate must be dropped:\n" + out);
        assertFalse(out.contains("@DeleteMapping") || out.contains("@PostMapping(\"/delete\")"), out);
    }

    @Test
    public void exposeMethods_eachNameStandsForItsEndpoints() {
        EntitySpec e = order().meta("expose", null).metaProperty("expose", "methods", "update, delete");

        java.util.Set<String> omitted = ControllerBaseRules.exposeOmitted(e);

        assertTrue(omitted.containsAll(java.util.Arrays.asList("create", "read", "search")), omitted.toString());
        assertFalse(
                omitted.contains("update")
                        || omitted.contains("batchUpdate")
                        || omitted.contains("deleteBatch")
                        || omitted.contains("deleteById"),
                omitted.toString());
    }

    @Test
    public void exposeWithoutMethods_omitsNothingAndSaysNothing() {
        EntitySpec e = order().meta("expose", "20");

        assertTrue(ControllerBaseRules.exposeOmitted(e).isEmpty());
        assertTrue(ControllerBaseRules.exposeProblems(e).isEmpty());
        assertTrue(renderV3(e).contains("@DeleteMapping"));
    }

    @Test
    public void exposeProblems_nameEachMistake() {
        EntitySpec e = order().meta("expose", "20")
                .meta("readonly", null)
                .metaProperty("expose", "methods", "read,serch,create")
                .metaProperty("expose", "pagesize", "20");

        java.util.List<String> said = ControllerBaseRules.exposeProblems(e);

        assertTrue(
                said.contains(
                        "[Order] @expose(pagesize=...): @expose takes methods=\"...\" only; pagesize is ignored."),
                said.toString());
        assertTrue(
                said.contains(
                        "[Order] @expose(methods=\"read,serch,create\"): serch is not one of create, read, search,"
                                + " update, delete; it serves nothing."),
                said.toString());
        assertTrue(
                said.contains("[Order] @expose(methods=\"read,serch,create\") lists create, which @readonly takes away;"
                        + " drop one of the two."),
                said.toString());
    }

    @Test
    public void restExcludeDeprecation_namesTheSameListOrTheNearestAndWhatItChanges() {
        assertTrue(ControllerBaseRules.restExcludeDeprecation(order().meta("restexclude", "deleteById"))
                .equals("[Order] @restExclude is deprecated: list what the controller serves instead,"
                        + " @expose(methods=\"create,read,search,update\"). It still applies."));
        assertTrue(ControllerBaseRules.restExcludeDeprecation(order().meta("restexclude", "deleteBatch"))
                .equals("[Order] @restExclude is deprecated, and no @expose(methods) list serves the same endpoints:"
                        + " the nearest is @expose(methods=\"create,read,search,update\"), but it also drops"
                        + " DELETE /{id}. It still applies."));
        assertTrue(ControllerBaseRules.restExcludeDeprecation(order().meta("restexclude", "schemaInfo"))
                .equals("[Order] @restExclude is deprecated, and no @expose(methods) list serves the same endpoints:"
                        + " the nearest is @expose(methods=\"create,read,search,update,delete\"), but GET /schema/info"
                        + " stays. It still applies."));
        assertTrue(ControllerBaseRules.restExcludeDeprecation(order().meta("restexclude", "update, read"))
                .equals("[Order] @restExclude is deprecated: list what the controller serves instead,"
                        + " @expose(methods=\"create,search,delete\"). It still applies."));
        // With @readonly: only the reads are left either way.
        assertTrue(ControllerBaseRules.restExcludeDeprecation(
                        order().meta("readonly", null).meta("restexclude", "search"))
                .equals("[Order] @restExclude is deprecated: list what the controller serves instead,"
                        + " @expose(methods=\"read\"). It still applies."));
    }

    @Test
    public void exposeMethods_keyAndNamesAreReadWithoutRegardToCase() {
        EntitySpec e = order().meta("expose", null).metaProperty("Expose", "Methods", "Read, SEARCH");

        java.util.Set<String> omitted = ControllerBaseRules.exposeOmitted(e);

        assertFalse(omitted.contains("read") || omitted.contains("search"), omitted.toString());
        assertTrue(
                omitted.containsAll(
                        java.util.Arrays.asList("create", "update", "batchUpdate", "deleteBatch", "deleteById")),
                omitted.toString());
        assertTrue(
                ControllerBaseRules.exposeProblems(e).isEmpty(),
                ControllerBaseRules.exposeProblems(e).toString());
        assertTrue("Read, SEARCH".equals(e.metaProperty("expose", "Methods")));
        assertTrue(e.metaProperty("expose", "nothing") == null && e.metaProperty("other", "Methods") == null);
        assertTrue(e.metaProperties("other").isEmpty());
        // A parameter without a value is not recorded.
        assertTrue(order().metaProperty("expose", "methods", null)
                .metaProperties("expose")
                .isEmpty());
    }

    @Test
    public void exposeMethods_withoutExposeSaysNothingAndOmitsNothing() {
        EntitySpec e = order().metaProperty("expose", "methods", "read");

        assertTrue(ControllerBaseRules.exposeOmitted(e).isEmpty());
        assertTrue(ControllerBaseRules.exposeProblems(e).isEmpty());
    }

    /** A corporate rule set adding a custom endpoint under its own condition. */
    public static class AcmeControllerRules implements CodeRules {
        @Rule(value = "acme-approve-endpoint", target = "control", order = 30)
        void approveEndpoint(EntitySpec e) {
            if (!e.hasMeta("approvable")) return;
            ctx(e).addMethod(
                    "    @Operation(summary = \"Approve a " + e.getName() + "\")\n"
                  + "    @PostMapping(\"/{" + e.pkField().name() + "}/approve\")\n"
                  + "    public ResponseEntity<" + e.getName() + "DTO> approve(@PathVariable(\""
                          + e.pkField().name() + "\") Long " + e.pkField().name() + ") {\n"
                  + "        return ResponseEntity.ok(" + e.varName() + "Service.approve(" + e.pkField().name() + "));\n"
                  + "    }");
        }
    }

    @Test
    public void userRule_addsCustomMethodBeforeClosingBrace() {
        EntitySpec e = order().meta("approvable", null);

        String out = render(e, new AcmeControllerRules());

        assertTrue(out.contains("@PostMapping(\"/{orderId}/approve\")"), out);
        assertTrue(out.contains("orderService.approve(orderId)"), out);
        // added before the closing brace, class still closes
        int approveIdx = out.indexOf("/approve");
        int braceIdx = out.lastIndexOf("\n}");
        assertTrue(approveIdx < braceIdx, "custom method must sit inside the class:\n" + out);
        // default endpoints untouched
        assertTrue(out.contains("@DeleteMapping"), out);
    }

    @Test
    public void noFilter_outputUnchanged() {
        String plain = render(order());
        // Same spec rendered through the section machinery with no ops must
        // be identical to the raw substitution result.
        assertTrue(plain.contains("@DeleteMapping"), plain);
        assertTrue(plain.contains("@PostMapping\n"), plain);
        assertTrue(plain.contains("@GetMapping(\"/schema/info\")"), plain);
    }
}
