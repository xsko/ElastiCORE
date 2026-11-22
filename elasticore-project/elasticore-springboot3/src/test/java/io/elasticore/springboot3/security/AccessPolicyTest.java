package io.elasticore.springboot3.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** @access at run time, with the session-based caller the generated services default to. */
public class AccessPolicyTest {

    private final AccessPolicy order = AccessPolicy.parse("Order",
            "read=owner, create=user, cancel=owner, ship=admin, browse=public, purge=none", "ADMIN");
    private final CurrentUser user = new SessionCurrentUser();

    @AfterEach
    void unbind() {
        RequestContextHolder.resetRequestAttributes();
    }

    private MockHttpServletRequest request(Object id, String... roles) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (id != null) SessionCurrentUser.signIn(request, id, roles);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    @Test
    void theApplicationMayDoAnythingOnlyWhenItSaysSo() {
        CurrentUser.runAsSystem(() -> {
            assertTrue(user.system());
            order.require(user, "purge");
            order.requireAll(user, "read");
            assertTrue(order.owns(user, "read", 99L));
            assertNull(order.ownerFilter(user, "read"));
        });
        assertEquals("ok", CurrentUser.callAsSystem(() -> user.system() ? "ok" : "no"));
        assertFalse(user.system(), "the scope ends with the work");
    }

    /** No request is not the application: a WebFlux handler or an @Async task has none either. */
    @Test
    void withNoRequestTheCallerIsAVisitor() {
        assertFalse(user.system());
        assertThrows(NotSignedInException.class, () -> order.require(user, "read"));
        assertThrows(ForbiddenException.class, () -> order.require(user, "purge"));
    }

    @Test
    void aVisitorIsAskedToSignIn() {
        request(null);
        order.require(user, "browse");
        assertThrows(NotSignedInException.class, () -> order.require(user, "create"));
        assertThrows(NotSignedInException.class, () -> order.require(user, "read"));
        assertThrows(NotSignedInException.class, () -> order.require(user, "ship"));
    }

    @Test
    void aUserReachesOnlyTheirOwnRows() {
        request(7L, "USER");
        order.require(user, "create");
        order.require(user, "read");
        assertTrue(order.owns(user, "read", 7L));
        assertTrue(order.owns(user, "read", "7"), "compared as identities, whatever their Java type");
        assertFalse(order.owns(user, "cancel", 8L));
        assertEquals(7L, order.ownerFilter(user, "read"));
        assertEquals(7L, order.ownerToClaim(user));
        assertThrows(ForbiddenException.class, () -> order.require(user, "ship"));
        assertThrows(ForbiddenException.class, () -> order.require(user, "purge"));
        assertThrows(ForbiddenException.class, () -> order.requireAll(user, "read"));
    }

    @Test
    void theAdministratorSeesEveryRowButNotWhatNobodyMayDo() {
        request(1L, "ADMIN");
        order.require(user, "ship");
        order.requireAll(user, "read");
        assertTrue(order.owns(user, "cancel", 8L));
        assertNull(order.ownerFilter(user, "read"));
        assertNull(order.ownerToClaim(user), "an administrator creates for whoever the request names");
        assertThrows(ForbiddenException.class, () -> order.require(user, "purge"));
    }

    @Test
    void whatIsNotNamedIsTheAdministrators() {
        AccessPolicy closed = AccessPolicy.parse("Item", "read=public", null);
        assertEquals(AccessPolicy.Level.ADMIN, closed.level("delete"));
        AccessPolicy open = AccessPolicy.parse("Item", "read=public, *=user", null);
        assertEquals(AccessPolicy.Level.USER, open.level("delete"));
        assertEquals(AccessPolicy.Level.PUBLIC, AccessPolicy.parse("Item", null, null).level("delete"));
        IllegalArgumentException wrong =
                assertThrows(IllegalArgumentException.class, () -> AccessPolicy.parse("Item", "read=everyone", null));
        assertEquals(
                "Item @access: 'everyone' is not one of public, user, owner, admin, none",
                wrong.getMessage(),
                "a word that cannot be a role name is refused as it was before role names");
    }

    // ---- role names, | and & (issue 269) ----------------------------------------------

    private final AccessPolicy ticket = AccessPolicy.parse(
            "Ticket",
            "read=owner|MANAGER, create=REP|MANAGER, update=REP&owner, close=REP & owner | MANAGER,"
                    + " browse=public|MANAGER, archive=MANAGER, purge=none",
            "ADMIN");

    @Test
    void anEntryNamesRolesAndCombinesWords() {
        assertNull(ticket.level("read"), "an entry that combines words has no single level");
        assertEquals(AccessPolicy.Level.NONE, ticket.level("purge"));
        assertEquals(
                AccessPolicy.Level.USER,
                AccessPolicy.parse("T", "read=USER", null).level("read"),
                "a word that is a level is the level, in any case");
        assertThrows(IllegalArgumentException.class, () -> AccessPolicy.parse("T", "read=owner|", null));
        assertThrows(
                IllegalArgumentException.class,
                () -> AccessPolicy.parse("T", "read=owner|manager", null),
                "a role name starts with a capital letter; a lower-case word is a misspelt level");
        assertThrows(IllegalArgumentException.class, () -> AccessPolicy.parse("T", "read=owner&&REP", null));
    }

    @Test
    void eitherWordLetsTheCallerIn() {
        request(7L, "MANAGER");
        ticket.require(user, "create");
        ticket.require(user, "read");
        ticket.requireAll(user, "read");
        assertTrue(ticket.owns(user, "read", 8L), "a MANAGER reads another's row");
        assertNull(ticket.ownerFilter(user, "read"), "a MANAGER's search is not narrowed");
        ticket.require(user, "archive");
        unbind();

        request(9L, "USER");
        ticket.require(user, "read");
        assertTrue(ticket.owns(user, "read", 9L));
        assertFalse(ticket.owns(user, "read", 8L), "a plain user reads only their own");
        assertEquals(9L, ticket.ownerFilter(user, "read"), "and searches only their own");
        assertThrows(ForbiddenException.class, () -> ticket.requireAll(user, "read"));
        ForbiddenException refused = assertThrows(ForbiddenException.class, () -> ticket.require(user, "create"));
        assertEquals("create Ticket is for REP|MANAGER", refused.getMessage());
        assertThrows(ForbiddenException.class, () -> ticket.require(user, "archive"));
    }

    @Test
    void bothWordsMustHold() {
        request(5L, "REP");
        ticket.require(user, "update");
        assertTrue(ticket.owns(user, "update", 5L));
        assertFalse(ticket.owns(user, "update", 6L), "a REP changes only their own");
        assertTrue(ticket.owns(user, "close", 5L));
        assertFalse(ticket.owns(user, "close", 6L));
        unbind();

        request(5L, "USER");
        assertThrows(ForbiddenException.class, () -> ticket.require(user, "update"), "an owner who is no REP");
        assertFalse(ticket.owns(user, "update", 5L));
        unbind();

        request(4L, "MANAGER");
        ticket.require(user, "close");
        assertTrue(ticket.owns(user, "close", 6L), "& binds tighter than |: REP&owner, or MANAGER");
        assertThrows(ForbiddenException.class, () -> ticket.require(user, "update"), "a MANAGER is no REP");
    }

    @Test
    void theSearchFilterOfABothEntry() {
        AccessPolicy repOwn = AccessPolicy.parse("Visit", "read=REP&owner", null);
        request(5L, "REP");
        repOwn.require(user, "read");
        assertEquals(5L, repOwn.ownerFilter(user, "read"), "a REP searches their own rows");
        assertThrows(ForbiddenException.class, () -> repOwn.requireAll(user, "read"));
        unbind();
        request(6L, "USER");
        assertThrows(ForbiddenException.class, () -> repOwn.require(user, "read"), "nobody else searches at all");
        unbind();
        request(1L, "ADMIN");
        repOwn.requireAll(user, "read");
        assertNull(repOwn.ownerFilter(user, "read"));
    }

    @Test
    void theAdministratorPassesEveryEntryButNone() {
        request(1L, "ADMIN");
        ticket.require(user, "create");
        ticket.require(user, "update");
        ticket.require(user, "archive");
        assertTrue(ticket.owns(user, "update", 8L));
        assertNull(ticket.ownerFilter(user, "read"));
        assertThrows(ForbiddenException.class, () -> ticket.require(user, "purge"));
        unbind();
        CurrentUser.runAsSystem(() -> ticket.require(user, "purge"));
    }

    @Test
    void aVisitorIsAskedToSignInForARole() {
        request(null);
        ticket.require(user, "browse");
        assertThrows(NotSignedInException.class, () -> ticket.require(user, "read"));
        assertThrows(NotSignedInException.class, () -> ticket.require(user, "archive"));
        assertFalse(ticket.owns(user, "read", 7L));
    }

    @Test
    void signingInStartsANewSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String before = request.getSession(true).getId();
        SessionCurrentUser.signIn(request, 5L, "USER");
        assertFalse(before.equals(request.getSession(false).getId()), "a session fixed before sign-in is not the one after");
        SessionCurrentUser.signOut(request);
        assertNull(request.getSession(false));
    }

    @Test
    void anIdentityTakesTheOwnerFieldsType() {
        assertEquals(Long.valueOf(3), AccessPolicy.as("3", Long.class));
        assertEquals("3", AccessPolicy.as(3L, String.class));
        assertNull(AccessPolicy.as(null, Long.class));
    }

    @Test
    void aPasswordIsKeptOnlyAsASaltedHash() {
        PasswordHasher hasher = new Pbkdf2PasswordHasher(1000);
        String stored = hasher.hash("s3cret");
        assertTrue(stored.startsWith("pbkdf2-sha256$1000$"), stored);
        assertFalse(stored.contains("s3cret"));
        assertTrue(hasher.matches("s3cret", stored));
        assertFalse(hasher.matches("S3cret", stored));
        assertFalse(stored.equals(hasher.hash("s3cret")), "salted: the same password hashes differently");
        assertFalse(hasher.matches("s3cret", "plain-text"), "an unknown format never matches");
        assertTrue(new Pbkdf2PasswordHasher(2000).matches("s3cret", stored), "the stored count is the one used");
    }
}
