package io.elasticore.sample.petstore;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET .../schema/info answered from the model (env.yml config: schemaInfo: model, issue 373), over HTTP on the pet
 * store - JPA, the hexagon, Spring Boot 4 and Jackson 3: the entity, and each field with the keys the answer always
 * had (DTOUtils.getMetaInfo) and what the model says of it, its labels in the request's language.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SchemaInfoTest {

    @Autowired MockMvc mvc;

    private String info(String entity, String language) throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/petstore/" + entity + "/schema/info")
                        .header("Accept-Language", language))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        // In the caller's language: no shared cache keeps it for another.
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private");
        response.setCharacterEncoding("UTF-8");
        return response.getContentAsString();
    }

    @Test
    void theEntityAndALabelInTheRequestsLanguage() throws Exception {
        String en = info("item", "en");
        assertThat((Integer) JsonPath.read(en, "$.metaVersion")).isEqualTo(1);
        assertThat((String) JsonPath.read(en, "$.language")).isEqualTo("en");
        assertThat((String) JsonPath.read(en, "$.entity.name")).isEqualTo("Item");
        assertThat((String) JsonPath.read(en, "$.entity.domain")).isEqualTo("petstore");
        assertThat((String) JsonPath.read(en, "$.entity.label")).isEqualTo("Item for sale");
        assertThat((String) JsonPath.read(en, "$.entity.labels.ja")).isEqualTo("販売単位");
        assertThat((String) JsonPath.read(en, "$.entity.path")).isEqualTo("/api/petstore/item");
        assertThat((String) JsonPath.read(en, "$.entity.key")).isEqualTo("itemId");
        assertThat((String) JsonPath.read(en, "$.entity.dto")).isEqualTo("ItemDTO");
        assertThat((String) JsonPath.read(en, "$.entity.searchDto")).isEqualTo("ItemSrchDTO");

        // unitCost: @label(en=Unit cost, ja=単価) -- 단가 - said in the request's language, every language in labels.
        assertThat((String) JsonPath.read(en, "$.fields.unitCost.label")).isEqualTo("Unit cost");
        assertThat((String) JsonPath.read(info("item", "ja"), "$.fields.unitCost.label")).isEqualTo("単価");
        String ko = info("item", "ko");
        assertThat((String) JsonPath.read(ko, "$.language")).isEqualTo("ko");
        assertThat((String) JsonPath.read(ko, "$.fields.unitCost.label")).isEqualTo("단가");
        assertThat((String) JsonPath.read(ko, "$.entity.label")).isEqualTo("판매 단위 (Large, Male Puppy ...)");
        Map<String, String> labels = JsonPath.read(ko, "$.fields.unitCost.labels");
        assertThat(labels).containsEntry("en", "Unit cost").containsEntry("ja", "単価").hasSize(2);
    }

    @Test
    void eachFieldKeepsWhatItAlwaysSaidAndGainsWhatTheModelSays() throws Exception {
        String json = info("item", "en");
        // name: string(30)! - today's keys, then the model's.
        assertThat((String) JsonPath.read(json, "$.fields.name.type")).isEqualTo("String");
        assertThat((String) JsonPath.read(json, "$.fields.name.description")).isEqualTo("name");
        assertThat((Integer) JsonPath.read(json, "$.fields.name.maxSize")).isEqualTo(30);
        assertThat((String) JsonPath.read(json, "$.fields.name.kind")).isEqualTo("String");
        assertThat((Boolean) JsonPath.read(json, "$.fields.name.required")).isTrue();
        assertThat((Integer) JsonPath.read(json, "$.fields.name.maxLength")).isEqualTo(30);
        assertThat((Boolean) JsonPath.read(json, "$.fields.name.readOnly")).isFalse();
        assertThat((Boolean) JsonPath.read(json, "$.fields.imagePath.required")).isFalse();
        assertThat((Integer) JsonPath.read(json, "$.fields.imagePath.maxLength")).isEqualTo(100);
        List<Object> minLength = JsonPath.read(json, "$.fields[?(@.minLength)]");
        assertThat(minLength).isEmpty();

        // unitCost: money(10.2)! @min(0.01, 'price must be positive').
        assertThat((String) JsonPath.read(json, "$.fields.unitCost.type")).isEqualTo("Money");
        assertThat((String) JsonPath.read(json, "$.fields.unitCost.description")).isEqualTo("단가");
        assertThat((String) JsonPath.read(json, "$.fields.unitCost.kind")).isEqualTo("Money");
        assertThat(new BigDecimal(String.valueOf((Object) JsonPath.read(json, "$.fields.unitCost.min"))))
                .isEqualByComparingTo("0.01");

        // productName @ref(product.name): shown, never written.
        assertThat((Boolean) JsonPath.read(json, "$.fields.productName.readOnly")).isTrue();
        assertThat((Boolean) JsonPath.read(json, "$.fields.productName.required")).isFalse();
    }

    @Test
    void aReferenceSaysWhatItPointsAtAndWhatShowsIt() throws Exception {
        String json = info("item", "en");
        // productId: the product's key (product: Product @ManyToOne, by id in the hexagon).
        assertThat((String) JsonPath.read(json, "$.fields.productId.type")).isEqualTo("Long");
        assertThat((String) JsonPath.read(json, "$.fields.productId.kind")).isEqualTo("Ref");
        assertThat((String) JsonPath.read(json, "$.fields.productId.ref.entity")).isEqualTo("Product");
        assertThat((String) JsonPath.read(json, "$.fields.productId.ref.domain")).isEqualTo("petstore");
        assertThat((String) JsonPath.read(json, "$.fields.productId.ref.key")).isEqualTo("productId");
        assertThat((String) JsonPath.read(json, "$.fields.productId.ref.search"))
                .isEqualTo("/api/petstore/product/search");
        // ItemDTO: productName @ref(product.name) beside it.
        assertThat((String) JsonPath.read(json, "$.fields.productId.ref.display.field")).isEqualTo("productName");
        assertThat((String) JsonPath.read(json, "$.fields.productId.ref.display.of")).isEqualTo("name");
        // A reference with no such field beside it has no display.
        String order = info("purchaseOrder", "en");
        Map<String, Object> customer = JsonPath.read(order, "$.fields.customerId.ref");
        assertThat(customer).containsEntry("entity", "Customer").containsEntry("key", "customerId").doesNotContainKey("display");
        // An embedded value is carried inline: no reference.
        assertThat((String) JsonPath.read(order, "$.fields.deliveryAddress.kind")).isEqualTo("Object");
    }

    @Test
    void anEnumerationsValuesInTheRequestsLanguage() throws Exception {
        String en = info("purchaseOrder", "en");
        // Today's keys: the type is "enumeration", the constants as the class has them.
        assertThat((String) JsonPath.read(en, "$.fields.status.type")).isEqualTo("enumeration");
        assertThat((String) JsonPath.read(en, "$.fields.status.enums[0].enum")).isEqualTo("PLACED");
        assertThat((String) JsonPath.read(en, "$.fields.status.enums[0].value")).isEqualTo("PLC");
        // The model's: the lifecycle (states:) writes status - read-only; @label(en=Order status) -- 주문 상태.
        assertThat((String) JsonPath.read(en, "$.fields.status.kind")).isEqualTo("Enum");
        assertThat((Boolean) JsonPath.read(en, "$.fields.status.readOnly")).isTrue();
        assertThat((String) JsonPath.read(en, "$.fields.status.label")).isEqualTo("Order status");
        assertThat((String) JsonPath.read(en, "$.fields.status.options[0].value")).isEqualTo("PLC");
        assertThat((String) JsonPath.read(en, "$.fields.status.options[0].name")).isEqualTo("PLACED");
        assertThat((String) JsonPath.read(en, "$.fields.status.options[0].label")).isEqualTo("Placed");
        assertThat((String) JsonPath.read(en, "$.fields.status.options[0].labels.ko")).isEqualTo("주문됨");
        assertThat(JsonPath.<Map<String, Object>>read(en, "$.fields.status")).doesNotContainKey("enum");

        String ko = info("purchaseOrder", "ko");
        assertThat((String) JsonPath.read(ko, "$.fields.status.label")).isEqualTo("주문 상태");
        assertThat((String) JsonPath.read(ko, "$.fields.status.options[1].label")).isEqualTo("배송됨");
    }
}
