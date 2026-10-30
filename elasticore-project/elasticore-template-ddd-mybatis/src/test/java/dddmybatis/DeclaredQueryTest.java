package dddmybatis;

import io.elasticore.sample.dddmybatis.shop.application.port.out.ProductQueryPort;
import io.elasticore.sample.dddmybatis.shop.domain.model.Product;
import io.elasticore.sample.dddmybatis.shop.domain.repository.ProductRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The queries the repository DSL declares reach the ports: a question about
 * the aggregate on the domain's, a page on the application's - and they run.
 * Before, v3 generated none of them and said nothing.
 */
@SpringBootTest
@Transactional
class DeclaredQueryTest {

    @Autowired private ProductRepositoryPort products;
    @Autowired private ProductQueryPort productQuery;

    private Product product(String sku, String price, boolean active) {
        Product p = new Product();
        p.setName("P " + sku);
        p.setSku(sku);
        p.setPrice(new BigDecimal(price));
        p.setActive(active);
        return products.save(p);
    }

    @Test
    @DisplayName("a derived and a JPQL query on the domain port, a paged one on the query port")
    void declaredQueriesRun() {
        product("JQ-1", "10.00", true);
        product("JQ-2", "25.00", true);
        product("JQ-3", "40.00", false);

        assertThat(products.findBySku("JQ-2").map(Product::getPrice).orElse(null)).isEqualByComparingTo("25.00");
        assertThat(products.findBySku("none")).isEmpty();

        assertThat(products.findByPriceRange(new BigDecimal("20"), new BigDecimal("45")).stream()
                .map(Product::getSku).collect(Collectors.toList()))
                .contains("JQ-2", "JQ-3").doesNotContain("JQ-1");

        Page<Product> page = productQuery.findByActive(false, PageRequest.of(0, 10));
        assertThat(page.getContent().stream().map(Product::getSku).collect(Collectors.toList()))
                .contains("JQ-3").doesNotContain("JQ-1", "JQ-2");
    }
}
