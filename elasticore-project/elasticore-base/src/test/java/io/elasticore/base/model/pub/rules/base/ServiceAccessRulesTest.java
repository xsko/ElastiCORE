package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceAccessRulesTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    /** A Coverage carrying a Product, nestedRows not said (readers, the default); a null level is no @access. */
    private EntitySpec coverage(String holderRead, String productRead) {
        return coverage(holderRead, productRead, null);
    }

    private EntitySpec coverage(String holderRead, String productRead, String nestedRows) {
        EntitySpec entity = new EntitySpec("Coverage")
                .knownEntity("Product")
                .addField(loader.parse("product", "Product @ManyToOne"))
                .config(ServiceAccessRules.NESTED_ROWS, nestedRows);

        if (holderRead != null) {
            entity.knownReadLevel("Coverage", holderRead);
        }
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

    /** The holder, the referenced entity's @access as the DSL writes it, and whether the row is carried. */
    private static final String[][] TABLE = {
        // a holder without @access is read by anyone
        {null, "read=user", "false"},
        {null, "read=public", "true"},
        {null, null, "true"},
        // a referenced @access without read: "*", else admin
        {"read=user", "create=user", "false"},
        {"read=admin", "create=user", "true"},
        {"read=user", "*=user, create=admin", "true"},
        {"read=public", "*=user", "false"},
        {"read=MANAGER", "update=user, *=MANAGER|AGENT", "true"},
        // nothing implies the owner of another row
        {"read=owner", "read=owner", "false"},
        {"read=user", "read=owner", "false"},
        {"read=admin", "read=owner", "false"},
        // the issue's case, and the roles
        {"read=MANAGER, create=user", "read=MANAGER", "true"},
        {"read=MANAGER|AGENT", "read=MANAGER", "false"},
        {"read=MANAGER", "read=MANAGER|AGENT", "true"},
        {"read=owner", "read=user", "true"},
        {"read=user", "read=none", "false"},
    };

    @Test
    void theLevelsDecideWhetherTheRowIsCarried() {
        for (String[] row : TABLE) {
            EntitySpec entity = coverage(
                    row[0] == null ? null : ServiceAccessRules.readLevel(row[0]),
                    row[1] == null ? null : ServiceAccessRules.readLevel(row[1]));
            assertEquals(
                    Boolean.parseBoolean(row[2]),
                    ServiceAccessRules.nestedIsVisible(entity, productField(entity)),
                    java.util.Arrays.toString(row));
        }
    }

    /** nestedRows: public - a row is carried only when anyone may read it, as 3.0 does by default. */
    @Test
    void publicCarriesOnlyWhatAnyoneMayRead() {
        for (String nestedRows : new String[] {"public", "PUBLIC", " public "}) {
            for (String[] row : TABLE) {
                String product = row[1] == null ? null : ServiceAccessRules.readLevel(row[1]);
                EntitySpec entity =
                        coverage(row[0] == null ? null : ServiceAccessRules.readLevel(row[0]), product, nestedRows);
                boolean anyone = product == null || "public".equals(product);
                assertEquals(
                        anyone,
                        ServiceAccessRules.nestedIsVisible(entity, productField(entity)),
                        nestedRows + " " + java.util.Arrays.toString(row));
                assertTrue(ServiceAccessRules.carriedRows(entity).isEmpty());
                assertEquals("m.toDTO(x)", ServiceAccessRules.answered(entity, "m.toDTO(x)"));
            }
        }
        // Not said, or readers (3.0's opt-in, kept so a 3.0 project keeps working), carries for the readers.
        for (String nestedRows : new String[] {null, "readers", " Readers "}) {
            EntitySpec entity = coverage("MANAGER", "MANAGER", nestedRows);
            assertTrue(ServiceAccessRules.readersCarry(entity), String.valueOf(nestedRows));
            assertTrue(ServiceAccessRules.nestedIsVisible(entity, productField(entity)), String.valueOf(nestedRows));
            assertEquals(1, ServiceAccessRules.carriedRows(entity).size());
        }
    }

    // ---- what ACCESS = AccessPolicy.parse(...) is given (issue 373)

    @Test
    void thePolicyIsTheEntityItsDeclarationAndTheAdministratorsRole() {
        EntitySpec order = new EntitySpec("Order").metaKeyValues("access", " read=owner, delete=admin ");
        ServiceAccessRules.PolicyArguments args = ServiceAccessRules.policyArguments(order);
        assertEquals("Order", args.entity());
        assertEquals("read=owner, delete=admin", args.declaration());
        assertEquals("ADMIN", args.adminRole());
        assertEquals("\"Order\", \"read=owner, delete=admin\", \"ADMIN\"", args.literal());
    }

    @Test
    void theAdministratorsRoleIsTheConfiguredOne() {
        EntitySpec order = new EntitySpec("Order").meta("access", "user").config("adminRole", "BOSS");
        ServiceAccessRules.PolicyArguments args = ServiceAccessRules.policyArguments(order);
        assertEquals("user", args.declaration());
        assertEquals("BOSS", args.adminRole());
        assertEquals("\"Order\", \"user\", \"BOSS\"", args.literal());
        // Set but empty is not set.
        assertEquals(
                "ADMIN",
                ServiceAccessRules.policyArguments(new EntitySpec("Order").config("adminRole", ""))
                        .adminRole());
    }

    @Test
    void withoutADeclarationThePolicyIsGivenNull() {
        ServiceAccessRules.PolicyArguments args = ServiceAccessRules.policyArguments(new EntitySpec("Order"));
        assertEquals(null, args.declaration());
        assertEquals("\"Order\", null, \"ADMIN\"", args.literal());
    }

    /** A double quote becomes a single one; a backslash is the source's to escape, not the policy's. */
    @Test
    void theDeclarationIsWhatTheParserReceives() {
        EntitySpec odd = new EntitySpec("Order").meta("access", "read=\"MANAGER\\x\"");
        ServiceAccessRules.PolicyArguments args = ServiceAccessRules.policyArguments(odd);
        assertEquals("read='MANAGER\\x'", args.declaration());
        assertEquals("\"Order\", \"read='MANAGER\\\\x'\", \"ADMIN\"", args.literal());
    }

    /** Carried for readers alone: shown, and of an entity not everyone may read - cleared from write answers. */
    @Test
    void onlyARowNotEveryoneMayReadIsClearedFromWriteAnswers() {
        EntitySpec carried = coverage("MANAGER", "MANAGER");
        assertEquals(1, ServiceAccessRules.carriedRows(carried).size());
        assertEquals("withoutCarriedRows(m.toDTO(x))", ServiceAccessRules.answered(carried, "m.toDTO(x)"));
        assertTrue(ServiceAccessRules.nestedNotWritten(carried, productField(carried)));

        // Anyone's row was carried in every answer before, and stays so; a row not shown needs nothing.
        for (EntitySpec plain :
                new EntitySpec[] {coverage("user", "public"), coverage("user", "MANAGER"), coverage(null, null)}) {
            assertTrue(ServiceAccessRules.carriedRows(plain).isEmpty());
            assertEquals("m.toDTO(x)", ServiceAccessRules.answered(plain, "m.toDTO(x)"));
        }
        assertFalse(ServiceAccessRules.nestedNotWritten(
                coverage("user", "public"), productField(coverage("user", "public"))));
    }
}
