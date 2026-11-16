package io.elasticore.sample.guide.security;

import io.elasticore.springboot3.security.CurrentUser;
import io.elasticore.springboot3.security.SpringSecurityCurrentUser;
import org.springframework.context.annotation.Bean;

/**
 * The guide's two other cases, as the application would declare them. They
 * need a token issuer, so the pet store's tests call the beans directly
 * (SpringSecurityGuideTest) rather than start them.
 */
public class TokenSignIn {

    /** A JWT resource server: the token carries the customer's key in a claim. */
    static class ResourceServer {
        @Bean
        CurrentUser currentUser() {
            return SpringSecurityCurrentUser.of(SpringSecurityCurrentUser.claim("customer_id", Long::valueOf));
        }
    }

    /** OAuth2 / OIDC login: the provider's subject, linked to a customer by the application. */
    static class OidcLogin {
        @Bean
        CurrentUser currentUser(AccountLinks links) {
            return SpringSecurityCurrentUser.of(auth -> links.customerIdOf(auth.getName()));
        }
    }

    /** The application's table of linked accounts: the customer's key for a provider's subject, or null. */
    public interface AccountLinks {
        Long customerIdOf(String subject);
    }
}
