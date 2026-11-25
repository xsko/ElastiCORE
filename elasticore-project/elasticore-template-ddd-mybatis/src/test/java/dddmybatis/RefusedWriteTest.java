package dddmybatis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Issue 344: a refused update leaves no trace in what its transaction reads. The request is mapped onto the
 * row MyBatis handed out - the object its session cache keeps - then refused by the check the write makes
 * after the mapping, a parent that names no row (404 reference.Category.parentCategory). A read of the row
 * later in the same transaction (one test transaction: a batch, a caller that catches the refusal) answers the
 * stored name.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefusedWriteTest {

    private static final String CATEGORIES = "/api/v1/shop/category";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @Test
    @DisplayName("a refused update is not what the same transaction reads next")
    void aRefusedUpdateIsNotReadBack() throws Exception {
        MockHttpServletResponse created = mvc.perform(post(CATEGORIES).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"original\"}")).andReturn().getResponse();
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(200);
        long id = json.readTree(created.getContentAsString()).path("catId").asLong();

        MockHttpServletResponse refused = mvc.perform(put(CATEGORIES).contentType(MediaType.APPLICATION_JSON)
                .content("{\"catId\":" + id + ",\"name\":\"REFUSED CHANGE\",\"parentCategoryCatId\":987654}"))
                .andReturn().getResponse();
        assertThat(refused.getStatus()).as(refused.getContentAsString()).isEqualTo(404);

        MockHttpServletResponse read = mvc.perform(get(CATEGORIES + "/" + id)).andReturn().getResponse();
        assertThat(json.readTree(read.getContentAsString()).path("name").asText())
                .as("the refused change is not what the transaction reads")
                .isEqualTo("original");
    }
}
