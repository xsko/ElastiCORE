package io.elasticore.springboot3.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Issue 365: an operation that belongs to no entity - a compare, a report - guarded by a
 * hand-written class with the role names of @access and its administrator and system rules.
 * RateTools is the example of the annotation guide ("An operation of no entity"): its policy, its
 * CurrentUser and its checks as written there, the work of each operation left out.
 */
public class AccessPolicyOwnOperationTest {

    /** The guide's example; purge is an operation the declaration does not name. */
    static class RateTools {

        private static final AccessPolicy ACCESS =
                AccessPolicy.parse("RateTools", "compare=RATING_ADMIN, performance=BRANCH_SALES|ACCOUNTING", "ADMIN");

        private final CurrentUser currentUser;

        RateTools(ObjectProvider<CurrentUser> currentUser) {
            this.currentUser = currentUser.getIfAvailable(SessionCurrentUser::new);
        }

        String compare(long from, long to) {
            ACCESS.require(currentUser, "compare");
            return "compared " + from + " with " + to;
        }

        String performance() {
            ACCESS.require(currentUser, "performance");
            return "report";
        }

        String purge() {
            ACCESS.require(currentUser, "purge"); // not named: the administrator's
            return "purged";
        }
    }

    /** No CurrentUser bean - the session's caller, as the generated services take it. */
    private final RateTools tools = new RateTools(new StaticListableBeanFactory().getBeanProvider(CurrentUser.class));

    @AfterEach
    void unbind() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void signIn(Object id, String... roles) {
        RequestContextHolder.resetRequestAttributes();
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (id != null) SessionCurrentUser.signIn(request, id, roles);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void aVisitorIsAskedToSignIn() {
        signIn(null);
        NotSignedInException refused = assertThrows(NotSignedInException.class, () -> tools.compare(1, 2));
        assertEquals("Sign in to compare RateTools", refused.getMessage());
    }

    @Test
    void theRoleNamedPassesAndAnotherIsRefused() {
        signIn(7L, "RATING_ADMIN");
        assertEquals("compared 1 with 2", tools.compare(1, 2));
        ForbiddenException refused = assertThrows(ForbiddenException.class, tools::performance);
        assertEquals("performance RateTools is for BRANCH_SALES|ACCOUNTING", refused.getMessage());

        signIn(8L, "ACCOUNTING");
        assertEquals("report", tools.performance());
        refused = assertThrows(ForbiddenException.class, () -> tools.compare(1, 2));
        assertEquals("compare RateTools is for RATING_ADMIN", refused.getMessage());

        signIn(9L, "USER");
        assertThrows(ForbiddenException.class, tools::performance);
        refused = assertThrows(ForbiddenException.class, tools::purge);
        assertEquals("purge RateTools is for the administrator", refused.getMessage());
    }

    @Test
    void theAdministratorAndTheApplicationPassEverything() {
        signIn(1L, "ADMIN");
        assertEquals("compared 1 with 2", tools.compare(1, 2));
        assertEquals("report", tools.performance());
        assertEquals("purged", tools.purge());

        signIn(null);
        assertEquals("purged", CurrentUser.callAsSystem(tools::purge));
    }

    @Test
    void theAdministratorIsTheRoleGiven() {
        AccessPolicy policy = AccessPolicy.parse("RateTools", "compare=RATING_ADMIN", "SUPERVISOR");
        CurrentUser user = new SessionCurrentUser();
        signIn(1L, "ADMIN");
        assertThrows(ForbiddenException.class, () -> policy.require(user, "compare"));
        signIn(1L, "SUPERVISOR");
        policy.require(user, "compare");
        assertEquals(true, policy.isAdmin(user));
    }

    /** With no row to compare, owner holds for anyone signed in - not a level for such an operation. */
    @Test
    void ownerWithoutARowIsAnyoneSignedIn() {
        AccessPolicy policy = AccessPolicy.parse("RateTools", "export=owner", "ADMIN");
        CurrentUser user = new SessionCurrentUser();
        signIn(5L, "USER");
        policy.require(user, "export");
        signIn(null);
        assertThrows(NotSignedInException.class, () -> policy.require(user, "export"));
    }
}
