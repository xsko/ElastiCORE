package io.elasticore.sample.guide.security;

import io.elasticore.sample.petstore.application.port.out.CustomerQueryPort;
import io.elasticore.sample.petstore.domain.model.Customer;
import io.elasticore.springboot3.security.CurrentUser;
import io.elasticore.springboot3.security.SpringSecurityCurrentUser;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The pet store signed in by Spring Security instead of @signin: Spring
 * Security says who is asking, the generated @access checks decide what they
 * may do. The accounts are in memory here; an application keeps its own.
 */
@Configuration
@EnableWebSecurity
public class ShopSecurity {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    UserDetailsService accounts() {
        return new InMemoryUserDetailsManager(
                User.withUsername("alice").password("{noop}alice-pw").roles("USER").build(),
                User.withUsername("bob").password("{noop}bob-pw").roles("USER").build(),
                User.withUsername("admin").password("{noop}admin-pw").roles("ADMIN").build());
    }

    @Bean
    CurrentUser currentUser(CustomerQueryPort customers) {
        return SpringSecurityCurrentUser.of(auth -> customerIdOf(customers, auth.getName()));
    }

    /** The customer's key (a Long, as customerId is) for the signed-in username, or null when there is none. */
    static Long customerIdOf(CustomerQueryPort customers, String login) {
        Specification<Customer> byLogin = (r, q, cb) -> cb.equal(r.get("login"), login);
        List<Customer> found = customers.findAll(byLogin);
        return found.size() == 1 ? found.get(0).getCustomerId() : null;
    }
}
