package io.elasticore.sample.petstore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * elasticore.export.max-rows (issue 271): a search of more rows than it is refused with 400 and the count, before a
 * row is written - the answer is the problem body, not a file; one of as many rows or fewer is written.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:petstore-export-limit;DB_CLOSE_DELAY=-1",
        "elasticore.export.max-rows=10"})
@AutoConfigureMockMvc
class ExportLimitTest {

    @Autowired MockMvc mvc;

    @Test
    void aSearchOfMoreRowsThanTheLimitIsRefusedWithTheCount() throws Exception {
        // The 32 items the shop is seeded with: counted first, through the same search.
        mvc.perform(post("/api/petstore/item/export").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("export.too-many-rows"))
                .andExpect(jsonPath("$.detail").value(
                        "The search matches 32 rows; an export writes at most 10 (elasticore.export.max-rows)."
                                + " Narrow the search."));
        // As an .xlsx workbook asked for by Accept: the same refusal, in the problem body.
        mvc.perform(post("/api/petstore/item/export").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .accept("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(status().isNotAcceptable());
        mvc.perform(post("/api/petstore/item/export").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"search\":{\"name\":\"Puppy\"}}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"));
    }
}
