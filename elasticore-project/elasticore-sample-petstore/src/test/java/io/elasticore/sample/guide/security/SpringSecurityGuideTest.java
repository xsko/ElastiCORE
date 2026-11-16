package io.elasticore.sample.guide.security;

import com.jayway.jsonpath.JsonPath;
import io.elasticore.sample.petstore.PetstoreApplication;
import io.elasticore.sample.petstore.application.CustomerService;
import io.elasticore.sample.petstore.application.dto.CustomerDTO;
import io.elasticore.sample.petstore.application.dto.CustomerSrchDTO;
import io.elasticore.sample.petstore.application.port.out.CustomerQueryPort;
import io.elasticore.sample.petstore.domain.model.Address;
import io.elasticore.springboot3.security.CurrentUser;
import io.elasticore.springboot3.security.SpringSecurityCurrentUser;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The annotation guide's "Spring Security instead of @signin", run on the pet
 * store: Spring Security signs the caller in (HTTP Basic, in-memory accounts),
 * SpringSecurityCurrentUser hands the generated services the customer's key,
 * and the generated @access/@owner checks do the rest.
 */
@SpringBootTest(classes = PetstoreApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:petstore-security;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@Import(ShopSecurity.class)
class SpringSecurityGuideTest {

    private static final String CARD = "\"creditCard\":{\"creditCardNumber\":\"4111111111111111\","
            + "\"creditCardType\":\"VISA\",\"creditCardExpDate\":\"12/30\"}";

    @Autowired MockMvc mvc;
    @Autowired CustomerService customers;
    @Autowired CustomerQueryPort query;
    @Autowired CurrentUser currentUser;

    private long alice;
    private long bob;

    @BeforeEach
    void customers() {
        alice = customer("alice");
        bob = customer("bob");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** The customer row of this login, made by the application itself when there is none yet. */
    private long customer(String login) {
        return CurrentUser.callAsSystem(() -> {
            CustomerSrchDTO search = new CustomerSrchDTO();
            search.setLogin(login);
            if (customers.countBySearch(search) == 0) {
                CustomerDTO c = new CustomerDTO();
                c.setLogin(login);
                c.setPassword("not-used-here");
                c.setFirstName(login);
                c.setLastName(login);
                c.setDateOfBirth(LocalDate.of(1990, 1, 1));
                c.setHomeAddress(new Address(login + " Street", null, "San Francisco", "CA", "87653", "US"));
                customers.save(c);
            }
            return ShopSecurity.customerIdOf(query, login);
        });
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String user, String password) {
        String basic = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        return request.header("Authorization", "Basic " + basic);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void theApplicationsBeanIsTheOneTheServicesAsk() {
        assertEquals(SpringSecurityCurrentUser.class, currentUser.getClass());
    }

    @Test
    void aCustomerReadsAndSearchesTheirOwnRowOnly() throws Exception {
        mvc.perform(as(get("/api/petstore/customer/" + alice), "alice", "alice-pw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("alice"));
        // Someone else's row is answered as one that does not exist.
        mvc.perform(as(get("/api/petstore/customer/" + bob), "alice", "alice-pw"))
                .andExpect(status().isNotFound());
        mvc.perform(as(json(post("/api/petstore/customer/search"), "{}"), "alice", "alice-pw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].login").value("alice"));
    }

    @Test
    void anOrderIsPlacedForTheSignedInCustomerAndReadByThemAndTheAdministrator() throws Exception {
        String placed = mvc.perform(as(json(post("/api/petstore/purchaseOrder"),
                                "{\"lines\":[{\"itemId\":1000,\"quantity\":1}]," + CARD + "}"), "alice", "alice-pw"))
                .andExpect(status().isOk())
                // The owner is alice's key, a Long: what the bean answered, written into the new row.
                .andExpect(jsonPath("$.customerId").value(alice))
                .andReturn().getResponse().getContentAsString();
        String order = JsonPath.read(placed, "$.orderId");

        mvc.perform(as(get("/api/petstore/purchaseOrder/" + order), "alice", "alice-pw"))
                .andExpect(status().isOk());
        mvc.perform(as(get("/api/petstore/purchaseOrder/" + order), "bob", "bob-pw"))
                .andExpect(status().isNotFound());
        mvc.perform(as(json(post("/api/petstore/purchaseOrder/search"), "{}"), "bob", "bob-pw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(as(json(post("/api/petstore/purchaseOrder/search"), "{}"), "alice", "alice-pw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].customerId", everyItem(is((int) alice))));
        // Cancelling is the owner's: to bob the order is not there.
        mvc.perform(as(post("/api/petstore/purchaseOrder/" + order + "/cancel"), "bob", "bob-pw"))
                .andExpect(status().isNotFound());

        // The administrator (ROLE_ADMIN) sees every row.
        mvc.perform(as(get("/api/petstore/purchaseOrder/" + order), "admin", "admin-pw"))
                .andExpect(status().isOk());
        mvc.perform(as(json(post("/api/petstore/customer/search"), "{}"), "admin", "admin-pw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].login", hasItems("alice", "bob", "admin")));
        mvc.perform(as(post("/api/petstore/purchaseOrder/" + order + "/ship"), "admin", "admin-pw"))
                .andExpect(status().isOk());
        // Shipping is the administrator's only.
        mvc.perform(as(post("/api/petstore/purchaseOrder/" + order + "/ship"), "alice", "alice-pw"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aVisitorIsAskedToSignIn() throws Exception {
        mvc.perform(get("/api/petstore/customer/" + alice))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/petstore/purchaseOrder"), "{\"lines\":[{\"itemId\":1000,\"quantity\":1}]," + CARD + "}"))
                .andExpect(status().isUnauthorized());
        // The catalog stays public.
        mvc.perform(get("/api/petstore/item/1000")).andExpect(status().isOk());
        // A wrong password is Spring Security's to refuse.
        mvc.perform(as(get("/api/petstore/customer/" + alice), "alice", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    /** The guide's JWT and OIDC beans, with principals such as Spring's Jwt and OidcUser. */
    @Test
    void theTokenBeansMapAClaimOrALinkedAccount() {
        CurrentUser jwt = new TokenSignIn.ResourceServer().currentUser();
        signIn(new Claims("customer_id", "42"));
        assertEquals(42L, jwt.id());
        signIn(new Claims("sub", "x"));
        assertNull(jwt.id());

        Map<String, Long> links = new HashMap<String, Long>();
        links.put("google-1234", alice);
        CurrentUser oidc = new TokenSignIn.OidcLogin().currentUser(links::get);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "google-1234", "n/a", AuthorityUtils.createAuthorityList("OIDC_USER")));
        assertEquals(alice, oidc.id());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "google-9999", "n/a", AuthorityUtils.createAuthorityList("OIDC_USER")));
        assertNull(oidc.id(), "a subject linked to no customer is a visitor");
    }

    private static void signIn(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, "n/a", AuthorityUtils.NO_AUTHORITIES));
    }

    /** A token's claims, read by getClaim(String) as on Spring's Jwt. */
    public static class Claims {
        private final Map<String, Object> claims = new HashMap<String, Object>();

        Claims(String name, Object value) {
            claims.put(name, value);
        }

        public Object getClaim(String name) {
            return claims.get(name);
        }
    }
}
