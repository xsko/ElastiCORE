package io.elasticore.sample.guide.signin;

import io.elasticore.sample.petstore.PetstoreApplication;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The annotation guide's password reset by mail and sign-in from the
 * application's own code (@signin, "Around the generated sign-in"), run on the
 * pet store: the classes of this package are the guide's code.
 */
@SpringBootTest(classes = PetstoreApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:petstore-reset;DB_CLOSE_DELAY=-1",
        "shop.password-reset.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
@AutoConfigureMockMvc
@Import({PasswordResetConfig.class, PasswordReset.class, PasswordResetController.class, PasswordResetGuideTest.Outbox.class})
class PasswordResetGuideTest {

    private static final String RESET = "/api/petstore/account/password-reset";

    @Autowired MockMvc mvc;
    @Autowired Outbox outbox;

    /** The test's mail adapter: what would have been sent. */
    static class Outbox implements PasswordResetMail {
        final List<String[]> sent = new CopyOnWriteArrayList<String[]>();

        @Override
        public void send(String to, String token) {
            sent.add(new String[] {to, token});
        }

        List<String> tokensTo(String address) {
            List<String> tokens = new ArrayList<String>();
            for (String[] mail : sent) if (mail[0].equals(address)) tokens.add(mail[1]);
            return tokens;
        }
    }

    @Test
    void aForgottenPasswordIsSetAgainByMailAndTheCustomerIsSignedInInANewSession() throws Exception {
        signUp("reset1", "reset1@example.com");
        mvc.perform(json(post(RESET), "{\"login\":\"reset1\"}")).andExpect(status().isAccepted());
        List<String> mails = outbox.tokensTo("reset1@example.com");
        assertEquals(1, mails.size());
        String token = mails.get(0);

        // A session someone may have fixed before: worthless after the sign-in.
        MockHttpSession before = new MockHttpSession();
        MvcResult confirmed = mvc.perform(json(post(RESET + "/confirm"), "{\"token\":\"" + token + "\",\"password\":\"new-pass-1234\"}")
                        .session(before))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("reset1"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        assertTrue(before.isInvalid());
        MockHttpSession after = (MockHttpSession) confirmed.getRequest().getSession(false);
        assertNotEquals(before.getId(), after.getId());
        // Signed in, and the rest of the row as it was: only the password changed.
        mvc.perform(get("/api/petstore/customer/me").session(after))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("reset1"))
                .andExpect(jsonPath("$.firstName").value("F"))
                .andExpect(jsonPath("$.email").value("reset1@example.com"))
                .andExpect(jsonPath("$.role").value("U"));

        signIn("reset1", "force-1234").andExpect(status().isUnauthorized());
        signIn("reset1", "new-pass-1234").andExpect(status().isOk());

        // The hash it was bound to is gone: the same link does not work twice.
        mvc.perform(json(post(RESET + "/confirm"), "{\"token\":\"" + token + "\",\"password\":\"other-pass-1234\"}"))
                .andExpect(status().isBadRequest());
        signIn("reset1", "new-pass-1234").andExpect(status().isOk());
    }

    /**
     * @signin(refresh=password): the sessions opened before the reset end with their next request; the one the
     * confirm signs in is stamped with the new password and keeps going.
     */
    @Test
    void aResetEndsTheSessionsOpenedBeforeItAndKeepsTheOneItSignsIn() throws Exception {
        MvcResult signedUp = signUp("reset6", "reset6@example.com");
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(signedUp.getResponse().getContentAsString(), "$.customerId")).longValue();
        MockHttpSession elsewhere = (MockHttpSession) signIn("reset6", "force-1234").andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
        mvc.perform(get("/api/petstore/customer/me").session(elsewhere)).andExpect(status().isOk());

        mvc.perform(json(post(RESET), "{\"login\":\"reset6\"}")).andExpect(status().isAccepted());
        String token = outbox.tokensTo("reset6@example.com").get(0);
        MvcResult confirmed = mvc.perform(json(post(RESET + "/confirm"), "{\"token\":\"" + token + "\",\"password\":\"new-pass-1234\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession after = (MockHttpSession) confirmed.getRequest().getSession(false);

        String telephone = "{\"customerId\":" + id + ",\"telephone\":\"+1 555 0199\"}";
        mvc.perform(json(put("/api/petstore/customer"), telephone).session(after)).andExpect(status().isOk());
        mvc.perform(json(put("/api/petstore/customer"), telephone).session(after)).andExpect(status().isOk());
        mvc.perform(json(put("/api/petstore/customer"), telephone).session(elsewhere)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/petstore/customer/me").session(elsewhere)).andExpect(status().isUnauthorized());
    }

    @Test
    void theAnswerIsTheSameForEveryLoginAndAMinuteSendsOneMail() throws Exception {
        int before = outbox.sent.size();
        mvc.perform(json(post(RESET), "{\"login\":\"nobody\"}")).andExpect(status().isAccepted());
        assertEquals(before, outbox.sent.size());

        signUp("reset2", "reset2@example.com");
        mvc.perform(json(post(RESET), "{\"login\":\"reset2\"}")).andExpect(status().isAccepted());
        mvc.perform(json(post(RESET), "{\"login\":\"reset2\"}")).andExpect(status().isAccepted());
        assertEquals(1, outbox.tokensTo("reset2@example.com").size());

        // No address on the row: nothing to send it to.
        signUp("reset3", null);
        mvc.perform(json(post(RESET), "{\"login\":\"reset3\"}")).andExpect(status().isAccepted());
        assertEquals(before + 1, outbox.sent.size());
    }

    @Test
    void aLinkThatWasChangedOrIsWrongOrAShortPasswordIsRefused() throws Exception {
        signUp("reset4", "reset4@example.com");
        mvc.perform(json(post(RESET), "{\"login\":\"reset4\"}")).andExpect(status().isAccepted());
        String token = outbox.tokensTo("reset4@example.com").get(0);

        String otherCustomer = "1" + token;
        for (String wrong : new String[] {otherCustomer, token + "x", "", "a.b.c"}) {
            mvc.perform(json(post(RESET + "/confirm"), "{\"token\":\"" + wrong + "\",\"password\":\"new-pass-1234\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(json(post(RESET + "/confirm"), "{\"token\":\"" + token + "\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        // None of them used it up.
        mvc.perform(json(post(RESET + "/confirm"), "{\"token\":\"" + token + "\",\"password\":\"new-pass-1234\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void aPasswordChangedAnotherWayEndsTheLinkToo() throws Exception {
        MvcResult signedUp = signUp("reset5", "reset5@example.com");
        mvc.perform(json(post(RESET), "{\"login\":\"reset5\"}")).andExpect(status().isAccepted());
        String token = outbox.tokensTo("reset5@example.com").get(0);

        // The customer remembers it and changes it themselves, signed in.
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(signedUp.getResponse().getContentAsString(), "$.customerId")).longValue();
        MockHttpSession session = (MockHttpSession) signedUp.getRequest().getSession(false);
        mvc.perform(json(put("/api/petstore/customer"), "{\"customerId\":" + id + ",\"password\":\"changed-1234\"}").session(session))
                .andExpect(status().isOk());

        mvc.perform(json(post(RESET + "/confirm"), "{\"token\":\"" + token + "\",\"password\":\"new-pass-1234\"}"))
                .andExpect(status().isBadRequest());
        signIn("reset5", "changed-1234").andExpect(status().isOk());
    }

    @Test
    void aTokenNamesItsCustomerForThirtyMinutesAndOnlyWithTheHashAndSecretItWasMadeWith() {
        byte[] secret = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        Instant issued = Instant.parse("2026-11-16T09:00:00Z");
        String token = new PasswordResetTokens(secret, Clock.fixed(issued, ZoneOffset.UTC)).issue(7L, "hash-1");

        PasswordResetTokens at29 = new PasswordResetTokens(secret, Clock.fixed(issued.plus(Duration.ofMinutes(29)), ZoneOffset.UTC));
        PasswordResetTokens at31 = new PasswordResetTokens(secret, Clock.fixed(issued.plus(Duration.ofMinutes(31)), ZoneOffset.UTC));
        assertEquals(Optional.of(7L), at29.customerOf(token));
        assertEquals(Optional.empty(), at31.customerOf(token));

        assertTrue(at29.matches(token, "hash-1"));
        assertFalse(at29.matches(token, "hash-2"));
        assertFalse(at29.matches(token.replaceFirst("^7\\.", "8."), "hash-1"));
        byte[] another = "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.US_ASCII);
        assertFalse(new PasswordResetTokens(another, Clock.fixed(issued, ZoneOffset.UTC)).matches(token, "hash-1"));

        for (String malformed : new String[] {null, "", "7", "7.x.y", "x.1.y"}) {
            assertEquals(Optional.empty(), at29.customerOf(malformed), String.valueOf(malformed));
        }
        // No hash - what a DTO holds, since none carries it - binds nothing.
        assertThrows(IllegalArgumentException.class, () -> at29.issue(7L, null));
        assertFalse(at29.matches(token, null));
    }

    private MvcResult signUp(String login, String email) throws Exception {
        return mvc.perform(json(post("/api/petstore/account/signup"), "{\"login\":\"" + login + "\",\"password\":\"force-1234\","
                        + (email == null ? "" : "\"email\":\"" + email + "\",")
                        + "\"firstName\":\"F\",\"lastName\":\"L\",\"homeAddress\":{\"street1\":\"1 Main St\",\"city\":\"Mos Eisley\","
                        + "\"zipcode\":\"12345\",\"countryCode\":\"US\"}}"))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions signIn(String login, String password) throws Exception {
        return mvc.perform(json(post("/api/petstore/customer/signin"),
                "{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
