package io.elasticore.springboot3.security;

import java.lang.reflect.Method;
import java.util.function.Function;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The {@link CurrentUser} of an application on Spring Security: who is asking
 * is who the security context holds. Not registered by itself - the
 * application declares it as a bean, and the generated services take it in
 * place of {@link SessionCurrentUser}:
 *
 * <pre>
 * &#64;Bean
 * CurrentUser currentUser(CustomerQueryPort customers) {
 *     return SpringSecurityCurrentUser.of(auth -&gt; customerIdOf(auth.getName()));
 * }
 * </pre>
 *
 * The identity must be the key the {@code @owner} fields hold, of their type
 * ({@code Long} for a {@code long} key): the generated services compare it
 * with a row's owner, search by it and write it into a new row's owner field.
 * The mapping from the authentication to that key is the application's -
 * {@link #of(Function)}, or {@link #claim(String, Function)} for a token claim.
 *
 * Roles are the authentication's authorities: {@code hasRole("ADMIN")} holds
 * for {@code ROLE_ADMIN} (Spring's convention) and for {@code ADMIN} itself.
 *
 * Nobody signed in, an anonymous authentication, or a mapping that answers
 * null is a visitor. Inside {@link CurrentUser#runAsSystem} the caller is the
 * application itself, as with the session.
 */
public class SpringSecurityCurrentUser implements CurrentUser {

    /** Spring Security's role prefix: {@code hasRole("ADMIN")} reads the authority {@code ROLE_ADMIN}. */
    public static final String ROLE_PREFIX = "ROLE_";

    private final Function<Authentication, Object> id;
    private final String rolePrefix;

    /**
     * The identity is the authentication's name - right only when the
     * {@code @owner} key is that name (a string key holding the username).
     */
    public SpringSecurityCurrentUser() {
        this(Authentication::getName, ROLE_PREFIX);
    }

    private SpringSecurityCurrentUser(Function<Authentication, Object> id, String rolePrefix) {
        if (id == null) throw new IllegalArgumentException("the mapping from the authentication to the key is null");
        this.id = id;
        this.rolePrefix = rolePrefix == null ? "" : rolePrefix;
    }

    /** The identity is what this mapping makes of a signed-in authentication: the {@code @owner} key, of its type. */
    public static SpringSecurityCurrentUser of(Function<Authentication, Object> id) {
        return new SpringSecurityCurrentUser(id, ROLE_PREFIX);
    }

    /** The same mapping, with roles read under another authority prefix ("" for none). */
    public SpringSecurityCurrentUser withRolePrefix(String prefix) {
        return new SpringSecurityCurrentUser(id, prefix);
    }

    /**
     * A token claim as the identity: {@code claim("customer_id", Long::valueOf)}.
     * Read from the principal's {@code getClaim(String)} - Spring's {@code Jwt}
     * and {@code OidcUser} have it - and converted from its text; a principal
     * without the claim is a visitor.
     */
    public static Function<Authentication, Object> claim(String name, Function<String, ?> type) {
        if (name == null || type == null) throw new IllegalArgumentException("a claim needs its name and its type");
        return authentication -> {
            Object value = claimOf(authentication.getPrincipal(), name);
            return value == null ? null : type.apply(String.valueOf(value));
        };
    }

    private static Object claimOf(Object principal, String name) {
        if (principal == null) return null;
        Method getClaim;
        try {
            getClaim = principal.getClass().getMethod("getClaim", String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    principal.getClass().getName() + " has no getClaim(String): map the "
                            + "authentication with SpringSecurityCurrentUser.of instead",
                    e);
        }
        try {
            return getClaim.invoke(principal, name);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the claim " + name + " could not be read", e);
        }
    }

    @Override
    public boolean system() {
        return CurrentUser.inSystemScope();
    }

    @Override
    public Object id() {
        Authentication authentication =
                signedIn(SecurityContextHolder.getContext().getAuthentication());
        return authentication == null ? null : id.apply(authentication);
    }

    @Override
    public boolean hasRole(String role) {
        Authentication authentication =
                signedIn(SecurityContextHolder.getContext().getAuthentication());
        if (authentication == null || role == null || authentication.getAuthorities() == null) return false;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String held = authority == null ? null : authority.getAuthority();
            if (role.equals(held) || (rolePrefix + role).equals(held)) return true;
        }
        return false;
    }

    /** The authentication of someone signed in, or null for a visitor. */
    private static Authentication signedIn(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        if (authentication instanceof AnonymousAuthenticationToken) return null;
        return authentication;
    }
}
