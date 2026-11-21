package ddd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.elasticore.springboot3.crypto.Bidx;
import io.elasticore.springboot3.crypto.FieldCrypto;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Encrypted fields over HTTP: Subscriber.phone and Subscriber.email are @encrypt - the database holds v1:...
 * ciphertext, the API the value - and the phone is @s(eq), searched by its blind index (phone_bidx), so a phone
 * written with or without its dashes finds the row. The keys are made for the run (TestCryptoKeys).
 */
@SpringBootTest
@AutoConfigureMockMvc
class BoardEncryptTest {

    private static final String SUBSCRIBERS = "/api/v1/board/subscriber";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    private JsonNode send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            Object body) throws Exception {
        MockHttpServletResponse res = mvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andReturn().getResponse();
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        return json.readTree(res.getContentAsString());
    }

    private long create(String nickname, String phone, String email) throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("nickname", nickname);
        body.put("phone", phone);
        body.put("email", email);
        return send(post(SUBSCRIBERS), body).path("subscriberId").asLong();
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("select phone, phone_bidx, email from brd_subscriber where subscriber_id = ?", id);
    }

    @Test
    @DisplayName("the columns hold v1: ciphertext, the API the value")
    void theDatabaseHoldsCiphertext() throws Exception {
        long id = create("encrypted", "010-1234-5678", "reader@example.com");

        Map<String, Object> row = row(id);
        String phone = (String) row.get("PHONE");
        String email = (String) row.get("EMAIL");
        assertThat(phone).startsWith("v1:").doesNotContain("1234").doesNotContain("5678");
        assertThat(email).startsWith("v1:").doesNotContain("example");
        assertThat(FieldCrypto.current().decrypt(phone)).isEqualTo("010-1234-5678");
        assertThat(row.get("PHONE_BIDX")).isEqualTo(Bidx.ofDigits("01012345678"));

        JsonNode read = json.readTree(mvc.perform(get(SUBSCRIBERS + "/" + id)).andReturn().getResponse()
                .getContentAsString());
        assertThat(read.path("phone").asText()).isEqualTo("010-1234-5678");
        assertThat(read.path("email").asText()).isEqualTo("reader@example.com");
        assertThat(read.has("phoneBidx")).as(read.toString()).isFalse();

        // Hibernate made the converter when it started: a missing key would have stopped the start.
        assertThat(FieldCrypto.used()).isTrue();
    }

    @Test
    @DisplayName("the same phone is stored differently in each row")
    void theSameValueIsStoredDifferently() throws Exception {
        long a = create("twin a", "010-2222-3333", null);
        long b = create("twin b", "010-2222-3333", null);
        assertThat(row(a).get("PHONE")).isNotEqualTo(row(b).get("PHONE"));
        assertThat(row(a).get("PHONE_BIDX")).isEqualTo(row(b).get("PHONE_BIDX"));
        assertThat(row(a).get("EMAIL")).isNull();
    }

    @Test
    @DisplayName("a search by the phone finds the row with or without the dashes, and no other")
    void theSearchFindsByTheBlindIndex() throws Exception {
        long id = create("searched", "010-9876-5432", "s@example.com");
        create("other", "010-9876-5433", null);

        for (String phone : new String[] {"010-9876-5432", "01098765432", " 010 9876 5432 "}) {
            JsonNode found = send(post(SUBSCRIBERS + "/search"), json.createObjectNode().put("phone", phone));
            assertThat(found.path("content").findValuesAsText("subscriberId")).as(phone)
                    .containsExactly(String.valueOf(id));
            assertThat(found.path("content").get(0).path("phone").asText()).isEqualTo("010-9876-5432");
        }
        JsonNode none = send(post(SUBSCRIBERS + "/search"), json.createObjectNode().put("phone", "010-9876-5439"));
        assertThat(none.path("content").size()).isZero();
    }

    /**
     * The move command's parameter is kept by the aggregate's own method, past the service's save path: the index is
     * written when JPA writes the row (BlindIndexListener), so the new phone finds it and the old one does not.
     */
    @Test
    @DisplayName("a command that sets the phone re-indexes it")
    void aCommandReIndexes() throws Exception {
        long id = create("mover", "010-5555-1111", null);
        String before = (String) row(id).get("PHONE");

        JsonNode moved = send(post(SUBSCRIBERS + "/" + id + "/move"), json.createObjectNode().put("phone", "010-5555-2222"));
        assertThat(moved.path("status").asText()).isEqualTo("MOV");
        assertThat(moved.path("phone").asText()).isEqualTo("010-5555-2222");

        Map<String, Object> row = row(id);
        assertThat(row.get("PHONE")).isNotEqualTo(before);
        assertThat(FieldCrypto.current().decrypt((String) row.get("PHONE"))).isEqualTo("010-5555-2222");
        assertThat(row.get("PHONE_BIDX")).isEqualTo(Bidx.ofDigits("01055552222"));
        assertThat(send(post(SUBSCRIBERS + "/search"), json.createObjectNode().put("phone", "01055552222"))
                .path("content").findValuesAsText("subscriberId")).containsExactly(String.valueOf(id));
        assertThat(send(post(SUBSCRIBERS + "/search"), json.createObjectNode().put("phone", "010-5555-1111"))
                .path("content").size()).isZero();
    }
}
