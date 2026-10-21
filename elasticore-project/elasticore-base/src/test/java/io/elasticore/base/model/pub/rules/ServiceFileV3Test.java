package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CoreService rendering in the 3.0 clean mode, with method-level selection
 * consistent across artifacts: one @readonly meta drops the write methods
 * from the CONTROLLER and from the SERVICE, so excluding an endpoint never
 * leaves dead service code behind.
 */
public class ServiceFileV3Test {

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
                .pkg("entity", base + ".entity")
                .pkg("dto", base + ".dto")
                .pkg("repository", base + ".repository")
                .pkg("service", base + ".service")
                .pkg("control", base + ".controller")
                .addField(loader.parse("orderId", "long @id @sequence"))
                .addField(loader.parse("orderNo", "string(30)"));
    }

    private String renderService(EntitySpec e) {
        SourceCode code = RuleCatalog.standard()
                .engineFor("jpa-service-file")
                .templateSet(SourceCode.TemplateSet.V3)
                .run(e, "service");
        return code.render();
    }

    // ------------------------------------------------------------------

    @Test
    public void defaultService_rendersAllMethodsWithSubstitutedVars() {
        String out = renderService(order());

        assertTrue(out.startsWith("package io.elasticore.blueprint.domain.shop.service;"), out);
        assertTrue(out.contains("public class OrderCoreService {"), out);
        assertTrue(out.contains("protected final ShopRepositoryHelper helper;"), out);

        // every method present
        assertTrue(out.contains("public List<OrderDTO> findAll()"), out);
        assertTrue(out.contains("public Optional<OrderDTO> findById(Long orderId)"), out);
        assertTrue(out.contains("public Page<OrderDTO> findBySearch(OrderSrchDTO searchDTO)"), out);
        assertTrue(out.contains("public long countBySearch(OrderSrchDTO searchDTO)"), out);
        assertTrue(out.contains("public OrderDTO save(OrderDTO dto)"), out);
        assertTrue(out.contains("public OrderDTO update(OrderDTO dto)"), out);
        assertTrue(out.contains("public void batchUpdate(List<OrderDTO> dtoList)"), out);
        assertTrue(out.contains("public List<Boolean> delete(List<OrderKeyDTO> dtoList)"), out);
        assertTrue(out.contains("public boolean deleteById(Long orderId)"), out);

        // pk expressions substituted, no leftover slots
        assertTrue(out.contains("findById(dto.getOrderId())"), out);
        assertFalse(out.contains("${"), "no unresolved template variables:\n" + out);
    }

    /** A rule set of the project's that drops update alone: batchUpdate, which calls it, goes too. */
    @Test
    public void excludingUpdateByARuleTakesTheBatch() {
        SourceCode code = RuleCatalog.standard()
                .engineFor("jpa-service-file")
                .templateSet(SourceCode.TemplateSet.V3)
                .run(order(), "service");
        code.excludeMethod("update");
        String out = code.render();

        assertFalse(out.contains("public OrderDTO update("), out);
        assertFalse(out.contains("batchUpdate"), out);
        assertTrue(out.contains("public OrderDTO save(OrderDTO dto)"), out);
        assertTrue(code.isMethodExcluded("batchUpdate"));
    }

    @Test
    public void readonly_dropsWriteMethodsFromService() {
        String out = renderService(order().meta("readonly", null));

        assertFalse(out.contains(" save("), "save must be dropped:\n" + out);
        assertFalse(out.contains(" update("), out);
        assertFalse(out.contains("batchUpdate"), out);
        assertFalse(out.contains("deleteById"), out);
        assertFalse(out.contains("public List<Boolean> delete("), out);
        assertFalse(out.contains("@Transactional\n    public"), "no write transactions left:\n" + out);

        // query methods survive and the class still closes
        assertTrue(out.contains("public List<OrderDTO> findAll()"), out);
        assertTrue(out.contains("public Optional<OrderDTO> findById(Long orderId)"), out);
        assertTrue(out.contains("public Page<OrderDTO> findBySearch(OrderSrchDTO searchDTO)"), out);
        assertTrue(out.trim().endsWith("}"), out);
    }

    @Test
    public void restExclude_dropsMatchingServiceMethodOnly() {
        String out = renderService(order().meta("restexclude", "deleteById"));

        assertFalse(out.contains("public boolean deleteById"), out);
        // the list delete calls deleteById, so it goes with it rather than not compiling
        assertFalse(out.contains("public List<Boolean> delete(List<OrderKeyDTO> dtoList)"), out);
        assertTrue(out.contains("public OrderDTO save(OrderDTO dto)"), out);
        assertTrue(out.contains("public void batchUpdate("), out);
    }

    /** One meta drives both artifacts - no dead service code behind a removed endpoint. */
    @Test
    public void readonly_isConsistentAcrossControllerAndService() {
        EntitySpec e = order().meta("readonly", null);

        String service = renderService(e);

        EntitySpec e2 = order().meta("readonly", null);
        SourceCode controllerCode = RuleCatalog.standard()
                .engineFor("spring-controller")
                .run(e2, "control");
        String controller = controllerCode.render();

        assertFalse(controller.contains("@DeleteMapping"), controller);
        assertFalse(service.contains("deleteById"), service);
        assertFalse(controller.contains("@PutMapping"), controller);
        assertFalse(service.contains(" update("), service);
    }
}
