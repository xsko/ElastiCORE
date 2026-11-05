package ddd;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.elasticore.sample.ddd.billing.application.ReceiptService;
import io.elasticore.sample.ddd.billing.application.dto.ReceiptDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Receipt is @expose(20, methods="read,search"): billing's process manager
 * writes receipts, the API only reads them. Over HTTP the reads answer and the
 * deletes the full CRUD would have had are not there; the service still has
 * every method, which is how the application writes one.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExposeMethodsApiTest {

    private static final String RECEIPTS = "/api/v1/billing/receipt";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private ReceiptService receipts;

    @Test
    @DisplayName("a receipt is read and searched over HTTP, and cannot be deleted there")
    void readAndSearchOnly() throws Exception {
        ReceiptDTO saved = receipts.save(ReceiptDTO.builder()
                .orderId("order-expose-1").amount(new BigDecimal("12.50")).build());
        long id = saved.getReceiptId();

        assertThat(mvc.perform(get(RECEIPTS + "/" + id)).andReturn().getResponse().getStatus()).isEqualTo(200);
        String found = mvc.perform(post(RECEIPTS + "/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"order-expose-1\"}")).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(found).path("content").get(0).path("receiptId").asLong()).isEqualTo(id);

        // Not served: the method is not allowed on a path the controller does map.
        assertThat(mvc.perform(delete(RECEIPTS + "/" + id)).andReturn().getResponse().getStatus()).isEqualTo(405);
        assertThat(mvc.perform(post(RECEIPTS + "/delete").contentType(MediaType.APPLICATION_JSON)
                .content("[{\"receiptId\":" + id + "}]")).andReturn().getResponse().getStatus()).isEqualTo(405);
        assertThat(receipts.findById(id)).isPresent();
    }
}
