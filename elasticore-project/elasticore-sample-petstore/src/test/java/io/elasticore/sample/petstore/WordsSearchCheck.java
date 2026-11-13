package io.elasticore.sample.petstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import io.elasticore.sample.petstore.domain.model.Category;
import io.elasticore.sample.petstore.domain.model.Product;
import io.elasticore.sample.petstore.infrastructure.persistence.CategoryRepository;
import io.elasticore.sample.petstore.infrastructure.persistence.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * One search box (ProductSrchDTO.keyword @search(words:name, description, category.name)), on whichever
 * database the test runs: every word has to be found, each in any of the fields, ignoring case and as
 * written; the category is joined LEFT, so a product without one is still found (issue 182).
 */
class WordsSearchCheck {

    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    public void findsEveryWordInAnyField(MockMvc mvc) throws Exception {
        List<Long> productIds = new ArrayList<Long>();
        Long categoryId = null;
        try {
            Category reptiles = new Category();
            reptiles.setName("Wordy Reptiles");
            reptiles.setDescription("cold blooded");
            categoryId = categories.saveAndFlush(reptiles).getCategoryId();
            productIds.add(product("Wordy Iguana", "green and calm", categoryId));
            productIds.add(product("Wordy Gecko", "sticky feet, 100% cute", categoryId));
            productIds.add(product("Wordy Stone", "no category at all", null));

            assertThat(names(mvc, "wordy REPTILES")).containsExactly("Wordy Gecko", "Wordy Iguana");
            assertThat(names(mvc, "wordy green")).containsExactly("Wordy Iguana");
            assertThat(names(mvc, "  wordy   category ")).containsExactly("Wordy Stone");
            assertThat(names(mvc, "wordy 100%")).containsExactly("Wordy Gecko");
            assertThat(names(mvc, "wordy nothing-like-this")).isEmpty();
        } finally {
            // A product is deleted softly (@deleted): its row stays and names the category, so it goes by SQL.
            for (Long id : productIds) jdbc.update("DELETE FROM ps_product WHERE product_id = ?", id);
            if (categoryId != null) categories.deleteById(categoryId);
        }
    }

    private Long product(String name, String description, Long categoryId) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setCategoryId(categoryId);
        return products.saveAndFlush(p).getProductId();
    }

    private static List<String> names(MockMvc mvc, String keyword) throws Exception {
        String json = mvc.perform(post("/api/petstore/product/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"" + keyword + "\",\"pageSize\":50}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(json, "$.content[*].name");
    }
}
