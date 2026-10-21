package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.model.pub.rules.base.DtoBaseRules;
import io.elasticore.base.model.pub.rules.lang.java.DtoFileRules;
import io.elasticore.base.model.pub.rules.base.EnumBaseRules;
import io.elasticore.base.model.pub.rules.lang.java.MapperFileRules;
import io.elasticore.base.model.pub.rules.base.RepositoryBaseRules;
import io.elasticore.base.model.pub.rules.base.RepositoryHelperBaseRules;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The v3 skeletons the clean mode needs beyond the ones it already had. A v3
 * project generates the same artifacts as a 2.x one, so every artifact whose
 * shape v3 changes needs a skeleton of its own - otherwise the mode falls
 * back to a template that is not there and the artifact goes missing.
 */
public class V3SkeletonCoverageTest {

    /** Every Java artifact the v3 mode is expected to write. */
    private static final String[] V3_TARGETS = {
            "entity", "dto", "enum", "mapper", "repository", "repositoryHelper",
            "service", "service_extend", "control", "proto"
    };

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    @Test
    public void everyV3Target_hasItsOwnSkeleton() {
        for (String target : V3_TARGETS) {
            String path = String.format("/elasticore-template/v3/%s.tmpl", target);
            InputStream in = getClass().getResourceAsStream(path);
            assertNotNull(in, "missing v3 skeleton for target '" + target + "'");
            try {
                in.close();
            } catch (Exception ignore) {
                // nothing to do
            }
        }
    }

    // ---- the skeletons added here render ---------------------------------

    @Test
    public void v3Dto_rendersWithoutUnresolvedSlots() {
        EntitySpec e = new EntitySpec("Order")
                .pkg("dto", "kr.co.acme.dto")
                .dtoImport("io.swagger.v3.oas.annotations.media.Schema")
                .addField(loader.parse("orderId", "long @id"));

        String out = new RuleEngine().templateSet(SourceCode.TemplateSet.V3)
                .register(new DtoBaseRules()).register(new DtoFileRules())
                .run(e, "dto").render();

        assertTrue(out.contains("public  class OrderDTO  implements java.io.Serializable {"), out);
        assertTrue(out.contains("private Long orderId;"), out);
        assertNoUnresolvedSlot(out);
    }

    @Test
    public void v3Enum_rendersWithoutUnresolvedSlots() {
        EnumSpec spec = new EnumSpec("Status")
                .pkg("enumeration", "kr.co.acme.enums")
                .addField(loader.parse("code", "string(1)"))
                .constant("OPEN", "O")
                .dbField("code");

        String out = new RuleEngine().templateSet(SourceCode.TemplateSet.V3)
                .register(new EnumBaseRules()).run(spec, "enum").render();

        assertTrue(out.contains("public enum Status {"), out);
        assertTrue(out.contains("OPEN(\"O\");"), out);
        assertTrue(out.contains("public static Status fromCode(String code)"), out);
        assertNoUnresolvedSlot(out);
    }

    @Test
    public void v3Mapper_rendersWithoutUnresolvedSlots() {
        DomainSpec d = new DomainSpec("shop", "Shop")
                .pkg("dto", "kr.co.acme.dto").pkg("entity", "kr.co.acme.entity")
                .mapperImport("kr.co.acme.entity.*")
                .mapperSection("    public static void mapping(Order from, OrderDTO to){\n    }\n");

        String out = new RuleEngine().templateSet(SourceCode.TemplateSet.V3)
                .register(new MapperFileRules()).run(d, "mapper").render();

        assertTrue(out.contains("public class ShopMapper {"), out);
        assertTrue(out.contains("    public static void mapping(Order from, OrderDTO to){"), out);
        assertTrue(out.contains("public static <T> void setVal(T value,"), out);
        assertNoUnresolvedSlot(out);
    }

    @Test
    public void v3RepositoryHelper_rendersWithoutUnresolvedSlots() {
        DomainSpec d = new DomainSpec("shop", "Shop")
                .pkg("repository", "kr.co.acme.repository")
                .pkg("entity", "kr.co.acme.entity")
                // Exposed: a v3 run writes a repository only for an entity with a service.
                .entity(new EntitySpec("Order").meta("expose", null).addField(loader.parse("orderId", "long @id")));

        String out = new RuleEngine().templateSet(SourceCode.TemplateSet.V3)
                .register(new RepositoryHelperBaseRules()).run(d, "repositoryHelper").render();

        assertTrue(out.contains("public class ShopRepositoryHelper {"), out);
        assertTrue(out.contains("private final OrderRepository order;"), out);
        assertNoUnresolvedSlot(out);
    }

    /**
     * The v3 repository skeleton carries the declared query methods. Without
     * the slot they would be generated and then dropped on the floor.
     */
    @Test
    public void v3Repository_carriesItsQueryMethods() {
        io.elasticore.base.model.core.Items<Field> params =
                io.elasticore.base.model.core.Items.create(Field.class);
        params.addItem(loader.parse("active", "boolean"));

        io.elasticore.base.model.repo.SqlQueryInfo info =
                io.elasticore.base.model.repo.SqlQueryInfo.creat("shop",
                        "SELECT o FROM Order o WHERE o.active = :active",
                        false, false, null, "List<Order>", true);

        EntitySpec e = new EntitySpec("Order")
                .pkg("repository", "kr.co.acme.repository")
                .pkg("entity", "kr.co.acme.entity")
                .addField(loader.parse("orderId", "long @id"))
                .addQueryMethod(io.elasticore.base.model.repo.Method.builder()
                        .name("findActive")
                        .query("SELECT o FROM Order o WHERE o.active = :active")
                        .returnType("List<Order>").params(params).queryInfo(info)
                        .build());

        String out = new RuleEngine().templateSet(SourceCode.TemplateSet.V3)
                .register(new RepositoryBaseRules()).run(e, "repository").render();

        assertTrue(out.contains("List<Order> findActive(@Param(\"active\") Boolean active);"), out);
        assertNoUnresolvedSlot(out);
    }

    private static void assertNoUnresolvedSlot(String rendered) {
        assertTrue(rendered.indexOf("${") < 0,
                "unresolved template slot in output:\n" + rendered);
    }
}
