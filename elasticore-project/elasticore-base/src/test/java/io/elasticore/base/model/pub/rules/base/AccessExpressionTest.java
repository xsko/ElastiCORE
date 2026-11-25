package io.elasticore.base.model.pub.rules.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessExpressionTest {

    @Test
    void roleImpliesUser() {
        assertTrue(AccessExpression.parse("MANAGER").implies(AccessExpression.parse("user")));
    }

    @Test
    void ownerImpliesUser() {
        assertTrue(AccessExpression.parse("owner").implies(AccessExpression.parse("user")));
    }

    @Test
    void adminImpliesUser() {
        assertTrue(AccessExpression.parse("admin").implies(AccessExpression.parse("user")));
    }

    @Test
    void publicDoesNotImplyUser() {
        assertFalse(AccessExpression.parse("public").implies(AccessExpression.parse("user")));
    }

    @Test
    void noneDoesNotImplyUser() {
        assertFalse(AccessExpression.parse("none").implies(AccessExpression.parse("user")));
    }

    @Test
    void roleDoesNotImplyOwner() {
        assertFalse(AccessExpression.parse("MANAGER").implies(AccessExpression.parse("owner")));
    }

    @Test
    void ownerDoesNotImplyReferencedOwner() {
        assertFalse(AccessExpression.parse("owner").implies(AccessExpression.parse("owner")));
    }

    @Test
    void anySourceImpliesPublic() {
        assertTrue(AccessExpression.parse("MANAGER").implies(AccessExpression.parse("public")));
    }

    @Test
    void sourceWithMultipleAlternativesMustImplyTarget() {
        assertTrue(AccessExpression.parse("MANAGER|ADMIN").implies(AccessExpression.parse("user")));

        assertFalse(AccessExpression.parse("MANAGER|public").implies(AccessExpression.parse("user")));
    }

    @Test
    void userDoesNotImplySpecificRole() {
        assertFalse(AccessExpression.parse("user").implies(AccessExpression.parse("MANAGER|ADMIN")));
    }

    @Test
    void sourceAndTargetTermsRespectAndConditions() {
        assertTrue(AccessExpression.parse("MANAGER&owner").implies(AccessExpression.parse("user")));

        assertFalse(AccessExpression.parse("MANAGER&owner").implies(AccessExpression.parse("user&owner")));
    }

    @Test
    void sourceWithAdditionalRequirementsCanImplyTarget() {
        assertTrue(AccessExpression.parse("MANAGER&REP").implies(AccessExpression.parse("MANAGER")));
    }

    @Test
    void roleExpressionImpliesExpandedRoleAlternatives() {
        // Role names only: ADMIN would be read as the level admin, not as a role.
        assertTrue(AccessExpression.parse("MANAGER|AGENT").implies(AccessExpression.parse("MANAGER|AGENT|AUDITOR")));
        assertFalse(AccessExpression.parse("MANAGER|AGENT|AUDITOR").implies(AccessExpression.parse("MANAGER|AGENT")));
    }

    @Test
    void adminIsTheLevelNotARole() {
        assertTrue("admin".equals(AccessExpression.parse("ADMIN").text()));
        assertFalse(AccessExpression.parse("MANAGER").implies(AccessExpression.parse("admin")));
    }

    @Test
    void nullTargetIsNotImplied() {
        assertFalse(AccessExpression.parse("MANAGER").implies(null));
    }
}
