package ddd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.elasticore.sample.ddd.shop.domain.model.Category;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A root's reference to its own kind, over HTTP: a category names its parent by
 * its id, parentCategoryCatId, and the relation only reads that column. What
 * that means for a caller was checked only in the generated text.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SelfReferenceApiTest {

    private static final String CATEGORIES = "/api/v1/shop/category";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private TransactionTemplate tx;
    @PersistenceContext private EntityManager em;

    private MockHttpServletResponse create(String body) throws Exception {
        return mvc.perform(post(CATEGORIES).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
    }

    private long createdId(String body) throws Exception {
        MockHttpServletResponse response = create(body);
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return json.readTree(response.getContentAsString()).path("catId").asLong();
    }

    @Test
    @DisplayName("a parent id that names no row is a 404 naming the relation, not the foreign key's 409")
    void aParentThatIsNotThere() throws Exception {
        MockHttpServletResponse response = create("{\"name\":\"orphan\",\"parentCategoryCatId\":987654}");

        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(404);
        JsonNode problem = json.readTree(response.getContentAsString());
        assertThat(problem.path("code").asText()).isEqualTo("reference.Category.parentCategory");
    }

    @Test
    @DisplayName("a parent that is there is written by its id, and the relation reads it")
    void aParentThatIsThere() throws Exception {
        long parent = createdId("{\"name\":\"parent\"}");

        MockHttpServletResponse response = create("{\"name\":\"child\",\"parentCategoryCatId\":" + parent + "}");

        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        JsonNode child = json.readTree(response.getContentAsString());
        assertThat(child.path("parentCategoryCatId").asLong()).isEqualTo(parent);
        long childId = child.path("catId").asLong();
        tx.executeWithoutResult(status -> assertThat(
                        em.find(Category.class, childId).getParentCategory().getCatId())
                .isEqualTo(parent));
    }

    @Test
    @DisplayName("setting the id does not set the relation: it is null until the row is read back")
    void theRelationIsNullBeforeTheReadBack() throws Exception {
        long parent = createdId("{\"name\":\"parent, in memory\"}");

        Category child = new Category();
        child.setParentCategoryCatId(parent);

        assertThat(child.getParentCategoryCatId()).isEqualTo(parent);
        assertThat(child.getParentCategory()).isNull();
    }
}
