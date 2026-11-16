package io.elasticore.springboot3.security;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The caller as Spring Security's context holds it, and @access checks made with it. */
public class SpringSecurityCurrentUserTest {

    private final AccessPolicy order =
            AccessPolicy.parse("Order", "read=owner, create=user, cancel=owner, ship=admin", "ADMIN");

    /** A principal with getClaim(String), as Spring's Jwt and OidcUser have. */
    public static class TokenPrincipal {
        private final Map<String, Object> claims = new HashMap<String, Object>();

        TokenPrincipal claim(String name, Object value) {
            claims.put(name, value);
            return this;
        }

        public Object getClaim(String name) {
            return claims.get(name);
        }
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(Object principal, String... authorities) {
        List<GrantedAuthority> held = AuthorityUtils.createAuthorityList(authorities);
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, "n/a", held));
    }

    @Test
    void nobodySignedInIsAVisitor() {
        CurrentUser user = new SpringSecurityCurrentUser();
        assertNull(user.id());
        assertFalse(user.signedIn());
        assertFalse(user.hasRole("USER"));
        assertFalse(user.system());
        assertThrows(NotSignedInException.class, () -> order.require(user, "read"));
    }

    @Test
    void anAnonymousOrUnauthenticatedCallerIsAVisitor() {
        CurrentUser user = new SpringSecurityCurrentUser();
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertNull(user.id());
        assertFalse(user.hasRole("ANONYMOUS"));

        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated("alice", "secret"));
        assertNull(user.id());
        assertFalse(user.hasRole("USER"));
    }

    @Test
    void theDefaultIdentityIsTheName() {
        signIn("alice", "ROLE_USER");
        CurrentUser user = new SpringSecurityCurrentUser();
        assertEquals("alice", user.id());
        assertTrue(user.signedIn());
        // Right for a string key that holds the username; where the key is a Long, the name owns no row
        // and the search a generated service narrows by it (AccessPolicy.as) fails.
        assertFalse(order.owns(user, "read", 7L));
        assertThrows(NumberFormatException.class, () -> AccessPolicy.as(order.ownerFilter(user, "read"), Long.class));
    }

    @Test
    void aRoleIsReadWithSpringsPrefixOrAsItIs() {
        signIn("alice", "ROLE_ADMIN", "AUDITOR");
        CurrentUser user = new SpringSecurityCurrentUser();
        assertTrue(user.hasRole("ADMIN"));
        assertTrue(user.hasRole("ROLE_ADMIN"));
        assertTrue(user.hasRole("AUDITOR"));
        assertFalse(user.hasRole("USER"));
        assertFalse(user.hasRole(null));

        CurrentUser scoped =
                SpringSecurityCurrentUser.of(Authentication::getName).withRolePrefix("SCOPE_");
        signIn("bob", "SCOPE_ADMIN");
        assertTrue(scoped.hasRole("ADMIN"));
        assertFalse(new SpringSecurityCurrentUser().hasRole("ADMIN"));
        assertFalse(scoped.hasRole("SCOPE"));

        CurrentUser bare = new SpringSecurityCurrentUser().withRolePrefix(null);
        assertTrue(bare.hasRole("SCOPE_ADMIN"));
        assertFalse(bare.hasRole("ADMIN"));
    }

    @Test
    void anAuthenticationWithNoAuthoritiesHoldsNoRole() {
        TestingAuthenticationToken token =
                new TestingAuthenticationToken("alice", "n/a", (List<GrantedAuthority>) null);
        token.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(token);
        assertFalse(new SpringSecurityCurrentUser().hasRole("USER"));

        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        "alice", "n/a", Arrays.asList(new SimpleGrantedAuthority("ROLE_USER"))));
        assertTrue(new SpringSecurityCurrentUser().hasRole("USER"));
    }

    @Test
    void theApplicationMapsTheAuthenticationToTheOwnerKey() {
        Map<String, Long> customers = new HashMap<String, Long>();
        customers.put("alice", 7L);
        CurrentUser user = SpringSecurityCurrentUser.of(auth -> customers.get(auth.getName()));

        signIn("alice", "ROLE_USER");
        assertEquals(7L, user.id());
        assertTrue(order.owns(user, "read", 7L));
        assertFalse(order.owns(user, "read", 8L));
        assertEquals(7L, order.ownerFilter(user, "read"));
        assertEquals(7L, order.ownerToClaim(user));
        assertThrows(ForbiddenException.class, () -> order.require(user, "ship"));

        // Signed in, but no row of ours: a visitor, so never an owner nor a search over every row.
        signIn("mallory", "ROLE_USER");
        assertNull(user.id());
        assertFalse(user.signedIn());
        assertFalse(order.owns(user, "read", 7L));
        assertThrows(NotSignedInException.class, () -> order.require(user, "read"));
    }

    @Test
    void theAdministratorSeesEveryRow() {
        CurrentUser user = SpringSecurityCurrentUser.of(auth -> 1L);
        signIn("admin", "ROLE_ADMIN");
        order.require(user, "ship");
        assertTrue(order.owns(user, "read", 99L));
        assertNull(order.ownerFilter(user, "read"));
        assertNull(order.ownerToClaim(user));
    }

    @Test
    void aClaimIsTheIdentityOfItsType() {
        CurrentUser user = SpringSecurityCurrentUser.of(SpringSecurityCurrentUser.claim("customer_id", Long::valueOf));
        signIn(new TokenPrincipal().claim("customer_id", "42"), "SCOPE_orders");
        assertEquals(42L, user.id());
        assertEquals(42L, order.ownerFilter(user, "read"));

        signIn(new TokenPrincipal().claim("customer_id", 43L));
        assertEquals(43L, user.id());

        signIn(new TokenPrincipal());
        assertNull(user.id(), "a token without the claim is a visitor");
    }

    @Test
    void aClaimOfAPrincipalWithoutClaimsIsRefused() {
        CurrentUser user = SpringSecurityCurrentUser.of(SpringSecurityCurrentUser.claim("sub", String::valueOf));
        signIn("alice");
        IllegalStateException e = assertThrows(IllegalStateException.class, user::id);
        assertTrue(e.getMessage().contains("getClaim"));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(null, "n/a", AuthorityUtils.NO_AUTHORITIES));
        assertNull(user.id());
    }

    @Test
    void aClaimThatCannotBeReadIsSaidSo() {
        CurrentUser user = SpringSecurityCurrentUser.of(SpringSecurityCurrentUser.claim("sub", String::valueOf));
        signIn(new TokenPrincipal() {
            @Override
            public Object getClaim(String name) {
                throw new IllegalArgumentException("malformed");
            }
        });
        IllegalStateException e = assertThrows(IllegalStateException.class, user::id);
        assertTrue(e.getMessage().contains("sub"));
    }

    @Test
    void aMappingAndAClaimNeedWhatTheyAreMadeOf() {
        assertThrows(IllegalArgumentException.class, () -> SpringSecurityCurrentUser.of(null));
        assertThrows(IllegalArgumentException.class, () -> SpringSecurityCurrentUser.claim(null, Long::valueOf));
        assertThrows(IllegalArgumentException.class, () -> SpringSecurityCurrentUser.claim("sub", null));
    }

    @Test
    void theApplicationItselfPassesEveryCheck() {
        CurrentUser user = SpringSecurityCurrentUser.of(auth -> 7L);
        CurrentUser.runAsSystem(() -> {
            assertTrue(user.system());
            order.require(user, "ship");
            assertNull(order.ownerFilter(user, "read"));
        });
        assertFalse(user.system());
    }
}
