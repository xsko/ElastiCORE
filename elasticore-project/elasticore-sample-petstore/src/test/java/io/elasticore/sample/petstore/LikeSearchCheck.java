package io.elasticore.sample.petstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import io.elasticore.sample.petstore.domain.model.Category;
import io.elasticore.sample.petstore.infrastructure.persistence.CategoryRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A like search (Category.name @search(like)) looks for the value as written, on whichever database
 * the test runs: a % or _ in it is a character, not a wildcard (issue 159).
 */
class LikeSearchCheck {

    @Autowired CategoryRepository categories;

    public void searchesTheValueAsWritten(MockMvc mvc) throws Exception {
        List<Long> ids = new ArrayList<Long>();
        try {
            for (String name : new String[] {"Like 100% wool", "Like 1000 wool", "Like a_b", "Like axb"}) {
                Category c = new Category();
                c.setName(name);
                c.setDescription("a like search finds the value as written");
                ids.add(categories.saveAndFlush(c).getCategoryId());
            }
            assertThat(names(mvc, "100%")).containsExactly("Like 100% wool");
            assertThat(names(mvc, "a_b")).containsExactly("Like a_b");
            assertThat(names(mvc, "Like ")).hasSize(4);
        } finally {
            categories.deleteAllById(ids);
        }
    }

    private static List<String> names(MockMvc mvc, String value) throws Exception {
        String json = mvc.perform(post("/api/petstore/category/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + value + "\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(json, "$.content[*].name");
    }
}
