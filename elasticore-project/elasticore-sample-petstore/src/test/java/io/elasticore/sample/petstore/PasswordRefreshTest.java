package io.elasticore.sample.petstore;

import io.elasticore.springboot3.security.SessionCurrentUser;
import jakarta.persistence.EntityManagerFactory;
import java.util.Collections;
import java.util.LinkedHashSet;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @signin(refresh=password)} on the customer (issue 216): everything refresh=request does
 * (ShopFlowTest.theSessionFollowsTheCustomersRow), and a session opened before a password change ends.
 * Through HTTP, on the generated application.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:petstore-password-refresh;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
class PasswordRefreshTest {

    @Autowired MockMvc mvc;
    @Autowired EntityManagerFactory entityManagerFactory;

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpSession signIn(String login, String password) throws Exception {
        MvcResult r = mvc.perform(json(post("/api/petstore/customer/signin"),
                        "{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    /** Signed up, and signed in by the application's own code (SessionCurrentUser.signIn). */
    private MockHttpSession signUp(String login) throws Exception {
        MvcResult r = mvc.perform(json(post("/api/petstore/account/signup"), "{\"login\":\"" + login
                        + "\",\"password\":\"force-1234\",\"firstName\":\"F\",\"lastName\":\"L\",\"homeAddress\":"
                        + "{\"street1\":\"1 Main St\",\"city\":\"Mos Eisley\",\"zipcode\":\"12345\",\"countryCode\":\"US\"}}"))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    private long idOf(MockHttpSession session) throws Exception {
        return ((Number) com.jayway.jsonpath.JsonPath.read(mvc.perform(get("/api/petstore/customer/me").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.customerId")).longValue();
    }

    /** A write that needs the customer signed in: their own telephone. */
    private org.springframework.test.web.servlet.ResultActions write(MockHttpSession session, long id) throws Exception {
        return mvc.perform(json(put("/api/petstore/customer"), "{\"customerId\":" + id + ",\"telephone\":\"+1 555 0100\"}")
                .session(session));
    }

    private void setPassword(MockHttpSession by, long id, String password) throws Exception {
        mvc.perform(json(put("/api/petstore/customer"), "{\"customerId\":" + id + ",\"password\":\"" + password + "\"}")
                        .session(by))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void anOwnChangeKeepsTheSessionThatMadeItAndEndsTheOthers() throws Exception {
        MockHttpSession changer = signUp("pwr1");
        MockHttpSession other = signIn("pwr1", "force-1234");
        long id = idOf(changer);
        assertEquals(id, idOf(other));

        setPassword(changer, id, "new-pass-1234");

        // The session that made the change keeps going, its next requests too.
        write(changer, id).andExpect(status().isOk());
        assertEquals(id, idOf(changer));
        write(changer, id).andExpect(status().isOk());
        // The other session ends: its next write is answered 401, and it stays a visitor's.
        write(other, id).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/petstore/customer/me").session(other)).andExpect(status().isUnauthorized());
        // The new password signs in; the old one does not.
        signIn("pwr1", "new-pass-1234");
        mvc.perform(json(post("/api/petstore/customer/signin"), "{\"login\":\"pwr1\",\"password\":\"force-1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anAdministratorsChangeEndsTheMembersSessionsAndKeepsTheirOwn() throws Exception {
        MockHttpSession member = signUp("pwr2");
        MockHttpSession again = signIn("pwr2", "force-1234");
        long id = idOf(member);
        MockHttpSession admin = signIn("admin", "admin");

        setPassword(admin, id, "set-by-admin-1");

        write(member, id).andExpect(status().isUnauthorized());
        write(again, id).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/petstore/customer/me").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("admin"));
        mvc.perform(json(post("/api/petstore/customer/search"), "{}").session(admin)).andExpect(status().isOk());
        signIn("pwr2", "set-by-admin-1");
    }

    /** No gap between a sign-in and its first request: the sign-in's own request stamps the session. */
    @Test
    void aSessionIsStampedByTheRequestThatSignedItIn() throws Exception {
        long id = idOf(signUp("pwr3"));
        MockHttpSession unused = signIn("pwr3", "force-1234");
        setPassword(signIn("admin", "admin"), id, "set-by-admin-2");
        mvc.perform(get("/api/petstore/customer/me").session(unused)).andExpect(status().isUnauthorized());
    }

    /** A session signed in before the option was on holds no stamp: it takes the row's on its first request. */
    @Test
    void aSessionWithoutAStampTakesOneOnItsFirstRequest() throws Exception {
        long id = idOf(signUp("pwr4"));
        MockHttpSession old = new MockHttpSession();
        old.setAttribute(SessionCurrentUser.ID, id);
        old.setAttribute(SessionCurrentUser.ROLES, new LinkedHashSet<String>(Collections.singleton("USER")));

        write(old, id).andExpect(status().isOk());
        assertTrue(old.getAttribute("elasticore.user.stamp") != null, "stamped by its first request");
        // The session holds a stamp, never the hash.
        String stamp = String.valueOf(old.getAttribute("elasticore.user.stamp"));
        assertEquals(16, stamp.length());
        assertFalse(stamp.contains("$"), stamp);

        setPassword(signIn("admin", "admin"), id, "set-by-admin-3");
        write(old, id).andExpect(status().isUnauthorized());
    }

    /** The stamp is read in the read refresh=request makes: one read of the customer per signed-in request. */
    @Test
    void oneReadOfTheCustomerPerSignedInRequest() throws Exception {
        MockHttpSession user = signIn("user", "user");
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        String customer = io.elasticore.sample.petstore.domain.model.Customer.class.getName();

        long before = statistics.getEntityStatistics(customer).getLoadCount();
        // A request that reads no customer itself: the catalog.
        mvc.perform(json(post("/api/petstore/category/search"), "{}").session(user)).andExpect(status().isOk());
        assertEquals(1, statistics.getEntityStatistics(customer).getLoadCount() - before);

        before = statistics.getEntityStatistics(customer).getLoadCount();
        mvc.perform(json(post("/api/petstore/category/search"), "{}")).andExpect(status().isOk());
        assertEquals(0, statistics.getEntityStatistics(customer).getLoadCount() - before, "a visitor is not read");
    }
}
