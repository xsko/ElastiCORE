package dddmybatis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Every value type of the DSL (catalog/model/reading.yml), written over HTTP,
 * stored and read back. Until these were mapped, timestamp, uuid, clob, json,
 * blob, Instant and the zoned times were written into the entity as the bare
 * DSL word - a class no compiler accepted.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TypeRoundTripTest {

    private static final String READINGS = "/api/v1/catalog/reading";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @Test
    @DisplayName("each value type survives the request, the column and the reply")
    void everyTypeRoundTrips() throws Exception {
        byte[] image = {1, 2, 3, (byte) 0xFF};
        String body = "{"
                + "\"label\":\"probe\",\"note\":\"a longer note\",\"rawText\":\"large text\","
                + "\"payload\":\"{\\\"k\\\":1}\","
                + "\"readCount\":42,\"totalCount\":9000000000,\"bigCount\":123456789012345678901,"
                + "\"units\":5,\"ratio\":12.34,\"exactValue\":1.5,\"amount\":99.95,\"score\":0.25,\"weight\":1.5,\"active\":true,"
                + "\"readDate\":\"2026-10-03\",\"readTime\":\"09:30:00\",\"localAt\":\"2026-10-03 09:30:00\","
                + "\"measuredAt\":\"2026-10-03T09:30:00+09:00\","
                + "\"image\":\"" + Base64.getEncoder().encodeToString(image) + "\"}";

        org.springframework.mock.web.MockHttpServletResponse response = mvc.perform(post(READINGS)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        String created = response.getStatus() + " " + response.getContentAsString();
        long id = json.readTree(response.getContentAsString()).path("readingId").asLong();
        assertThat(id).as(created).isPositive();

        JsonNode r = json.readTree(mvc.perform(get(READINGS + "/" + id)).andReturn().getResponse().getContentAsString());
        assertThat(r.path("label").asText()).isEqualTo("probe");
        assertThat(r.path("note").asText()).isEqualTo("a longer note");
        assertThat(r.path("rawText").asText()).isEqualTo("large text");
        assertThat(json.readTree(r.path("payload").asText()).path("k").asInt()).isEqualTo(1);
        assertThat(r.path("readCount").asInt()).isEqualTo(42);
        assertThat(r.path("totalCount").asLong()).isEqualTo(9000000000L);
        assertThat(r.path("bigCount").bigIntegerValue().toString()).isEqualTo("123456789012345678901");
        assertThat(r.path("units").asInt()).isEqualTo(5);
        assertThat(r.path("ratio").decimalValue()).isEqualByComparingTo("12.34");
        assertThat(r.path("exactValue").decimalValue()).isEqualByComparingTo("1.5");
        assertThat(new BigDecimal(r.path("amount").asText())).isEqualByComparingTo("99.95");
        assertThat(r.path("score").asDouble()).isEqualTo(0.25);
        assertThat(r.path("weight").asDouble()).isEqualTo(1.5);
        assertThat(r.path("active").asBoolean()).isTrue();
        assertThat(r.path("readDate").asText()).isEqualTo("2026-10-03");
        assertThat(r.path("readTime").asText()).startsWith("09:30");
        assertThat(r.path("localAt").asText()).isEqualTo("2026-10-03 09:30:00");   // the DSL's datetime format
        // Sent with its offset, kept as the instant it names, read back in UTC.
        assertThat(r.path("measuredAt").asText()).isEqualTo("2026-10-03T00:30:00Z");
        assertThat(Base64.getDecoder().decode(r.path("image").asText())).containsExactly(image);
    }
}
