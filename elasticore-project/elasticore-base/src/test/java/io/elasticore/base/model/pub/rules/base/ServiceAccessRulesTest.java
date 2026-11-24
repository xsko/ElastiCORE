package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceAccessRulesTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    private EntitySpec coverage(String holderRead, String productRead) {
        EntitySpec entity = new EntitySpec("Coverage")
                .knownEntity("Product")
                .addField(loader.parse("product", "Product @ManyToOne"))
                .knownReadLevel("Coverage", holderRead);

        if (productRead != null) {
            entity.knownReadLevel("Product", productRead);
        }

        return entity;
    }

    private FieldSpec productField(EntitySpec entity) {
        return entity.fields().stream()
                .filter(field -> field.name().equals("product"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("product field missing"));
    }

    @Test
    void sameUserReadLevelMakesNestedReferenceVisible() {
        EntitySpec entity = coverage("user", "user");

        assertTrue(ServiceAccessRules.nestedIsVisible(entity, productField(entity)));
    }

    @Test
    void roleHolderCanSeeUserProtectedReference() {
        EntitySpec entity = coverage("MANAGER", "user");

        assertTrue(ServiceAccessRules.nestedIsVisible(entity, productField(entity)));
    }

    @Test
    void publicHolderCannotSeeUserProtectedReference() {
        EntitySpec entity = coverage("public", "user");

        assertFalse(ServiceAccessRules.nestedIsVisible(entity, productField(entity)));
    }

    @Test
    void userHolderCannotSeeRoleProtectedReference() {
        EntitySpec entity = coverage("user", "MANAGER");

        assertFalse(ServiceAccessRules.nestedIsVisible(entity, productField(entity)));
    }

    @Test
    void anyHolderCanSeePublicReference() {
        EntitySpec entity = coverage("public", "public");

        assertTrue(ServiceAccessRules.nestedIsVisible(entity, productField(entity)));
    }

    @Test
    void missingReferencedReadLevelIsTreatedAsPublic() {
        EntitySpec entity = coverage("public", null);

        assertTrue(ServiceAccessRules.nestedIsVisible(entity, productField(entity)));
    }
}
