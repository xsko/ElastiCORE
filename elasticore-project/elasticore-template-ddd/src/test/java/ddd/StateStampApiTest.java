package ddd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * @stamp(STATE) over HTTP and in the table (shop/model/shipment.yml): the
 * command that moves the shipment into a state records when, and a request
 * cannot write the time itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StateStampApiTest {

    private static final String SHIPMENTS = "/api/v1/shop/shipment";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    private String create(String body) throws Exception {
        String reply = mvc.perform(post(SHIPMENTS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(reply).path("shipmentId").asText();
    }

    private JsonNode read(String id) throws Exception {
        return json.readTree(mvc.perform(get(SHIPMENTS + "/" + id)).andReturn().getResponse().getContentAsString());
    }

    /** Not in the reply, or null: the reply leaves out what is empty. */
    private static boolean blank(JsonNode node, String field) {
        return node.path(field).isNull() || node.path(field).isMissingNode();
    }

    private int command(String id, String name) throws Exception {
        return mvc.perform(post(SHIPMENTS + "/" + id + "/" + name)).andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("send records when it was sent; nothing else is stamped")
    void theCommandStampsTheStateItEnters() throws Exception {
        Instant before = Instant.now().minusSeconds(1);
        String id = create("{\"carrier\":\"DHL\"}");
        assertThat(blank(read(id), "sentAt")).isTrue();

        assertThat(command(id, "send")).isEqualTo(200);

        JsonNode sent = read(id);
        assertThat(sent.path("status").asText()).isEqualTo("SNT");
        Instant sentAt = Instant.parse(sent.path("sentAt").asText());
        assertThat(sentAt).isAfter(before).isBefore(Instant.now().plusSeconds(1));
        assertThat(blank(sent, "cancelledAt")).isTrue();
        assertThat(blank(sent, "refundedOn")).isTrue();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT sent_at, cancelled_at FROM shop_shipment WHERE shipment_id = ?", id);
        assertThat(row.get("sent_at")).isNotNull();
        assertThat(row.get("cancelled_at")).isNull();
    }

    @Test
    @DisplayName("a command of two lifecycles stamps each state it enters")
    void aCommandOfTwoLifecyclesStampsBoth() throws Exception {
        String id = create("{\"carrier\":\"UPS\"}");
        assertThat(command(id, "pay")).isEqualTo(200);
        assertThat(command(id, "cancel")).isEqualTo(200);

        JsonNode cancelled = read(id);
        assertThat(cancelled.path("status").asText()).isEqualTo("CAN");
        assertThat(cancelled.path("fee").asText()).isEqualTo("REF");
        assertThat(blank(cancelled, "cancelledAt")).isFalse();
        // Today - or yesterday, for a run that crossed midnight.
        assertThat(LocalDate.parse(cancelled.path("refundedOn").asText())).isBetween(LocalDate.now().minusDays(1), LocalDate.now());
        assertThat(blank(cancelled, "sentAt")).isTrue();
    }

    @Test
    @DisplayName("a refused command stamps nothing")
    void aRefusalStampsNothing() throws Exception {
        String id = create("{\"carrier\":\"FedEx\"}");
        // The fee is due: cancel is refused by the fee's lifecycle.
        assertThat(command(id, "cancel")).isEqualTo(409);
        JsonNode same = read(id);
        assertThat(blank(same, "cancelledAt")).isTrue();
        assertThat(blank(same, "refundedOn")).isTrue();
    }

    @Test
    @DisplayName("a request cannot write the time: the stamp is the lifecycle's")
    void aRequestCannotWriteTheStamp() throws Exception {
        String id = create("{\"carrier\":\"DHL\",\"sentAt\":\"2000-01-01T00:00:00Z\",\"refundedOn\":\"2000-01-01\"}");
        JsonNode made = read(id);
        assertThat(blank(made, "sentAt")).isTrue();
        assertThat(blank(made, "refundedOn")).isTrue();

        mvc.perform(put(SHIPMENTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"shipmentId\":\"" + id + "\",\"carrier\":\"DHL\",\"sentAt\":\"2000-01-01T00:00:00Z\"}"));
        assertThat(blank(read(id), "sentAt")).isTrue();
    }
}
