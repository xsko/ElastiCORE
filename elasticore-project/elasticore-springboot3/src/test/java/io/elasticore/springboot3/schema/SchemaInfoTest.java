package io.elasticore.springboot3.schema;

import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GET /schema/info answered from the model (config: schemaInfo: model, issue 373): the file the generator writes
 * (elasticore/desk-schema.json), the labels said in the request's language by the domain's label bundle, and the
 * keys the answer always had, read off the DTO as before.
 */
public class SchemaInfoTest {

    enum ItemStatus {
        ON_SALE("ON"),
        OFF("OF");

        private final String code;

        ItemStatus(String code) {
            this.code = code;
        }
    }

    /** The DTO the file names: what DTOUtils.getMetaInfo reads. */
    public static class ItemDTO {
        @Size(max = 30)
        private String name;

        private BigDecimal unitCost;
        private ItemStatus status;
        private Long productId;
        private String note;
    }

    @AfterEach
    void reset() {
        LocaleContextHolder.resetLocaleContext();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    /** The names a class declares, without the synthetic ones a coverage or mutation agent adds to it ($jacocoData). */
    private static List<String> declared(java.util.Collection<String> names) {
        List<String> out = new java.util.ArrayList<String>();
        for (String n : names) {
            if (!n.startsWith("$")) out.add(n);
        }
        return out;
    }

    private static Map<String, Object> field(Map<String, Object> answer, String name) {
        return map(map(answer.get("fields")).get(name));
    }

    @Test
    public void theAnswerIsTheEntityAndItsFieldsInTheRequestsLanguage() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        Map<String, Object> answer = SchemaInfo.of("desk", "Item");
        assertEquals(1, answer.get("metaVersion"));
        assertEquals("ko", answer.get("language"));
        Map<String, Object> entity = map(answer.get("entity"));
        assertEquals("Item", entity.get("name"));
        assertEquals("판매 단위", entity.get("label"));
        assertEquals("Item for sale", map(entity.get("labels")).get("en"));
        assertEquals("/api/desk/item", entity.get("path"));
        assertEquals("itemId", entity.get("key"));

        assertEquals("단가", field(answer, "unitCost").get("label"));
        assertEquals("Unit cost", map(field(answer, "unitCost").get("labels")).get("en"));
        assertEquals(0, new BigDecimal("0.01").compareTo((BigDecimal)
                        field(answer, "unitCost").get("min")));
        // The DTO's own label of the field first.
        assertEquals("상품", field(answer, "productId").get("label"));
        List<Object> options = (List<Object>) field(answer, "status").get("options");
        assertEquals("판매 중", map(options.get(0)).get("label"));
        assertEquals("ON", map(options.get(0)).get("value"));
        assertEquals("Off", map(options.get(1)).get("label"));
        // The enumeration's name is the file's, not the answer's.
        assertFalse(field(answer, "status").containsKey("enum"));
        // An enumeration of another domain: named by that domain's label bundle (i18n/common-labels).
        List<Object> currency = (List<Object>) field(answer, "currency").get("options");
        assertEquals("원", map(currency.get(0)).get("label"));
        assertFalse(field(answer, "currency").containsKey("enumDomain"));
        // What the answer hands out cannot change the file the next answer is made from.
        assertThrows(
                UnsupportedOperationException.class,
                () -> map(map(answer.get("entity")).get("labels")).put("fr", "x"));
        assertEquals(
                "Item for sale",
                map(map(SchemaInfo.of("desk", "Item").get("entity")).get("labels"))
                        .get("en"));

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        Map<String, Object> en = SchemaInfo.of("desk", "Item");
        // The label bundle has an _en file: English is a language of its own here.
        assertEquals("en", en.get("language"));
        assertEquals("Item for sale", map(en.get("entity")).get("label"));
        assertEquals("Unit cost", field(en, "unitCost").get("label"));
        // No English label: the default file - the "--" text.
        assertEquals("이름", field(en, "name").get("label"));
        assertEquals(
                "On sale",
                map(((List<Object>) field(en, "status").get("options")).get(0)).get("label"));
        // No such key in any file: the file's label.
        assertEquals("productId", field(en, "productId").get("label"));
    }

    /** Nobody chose a language: the bundles' own wording, and no language named. */
    @Test
    public void withoutALanguageTheBundlesOwnWording() {
        LocaleContextHolder.setLocale(Locale.ROOT);
        Map<String, Object> answer = SchemaInfo.of("desk", "Item");
        assertNull(answer.get("language"));
        assertEquals("Unit cost", field(answer, "unitCost").get("label"));
        // A language without a file of its own: the default file, the "--" text.
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        assertEquals("단가", field(SchemaInfo.of("desk", "Item"), "unitCost").get("label"));
    }

    @Test
    public void eachFieldKeepsTheKeysItAlwaysHad() {
        Map<String, Object> answer = SchemaInfo.of("desk", "Item");
        // In the order the class declares them, then what only the file has.
        assertEquals(
                Arrays.asList("name", "unitCost", "status", "productId", "note", "currency", "written"),
                declared(map(answer.get("fields")).keySet()));
        Map<String, Object> name = field(answer, "name");
        assertEquals("String", name.get("type"));
        assertEquals(30, name.get("maxSize"));
        assertEquals("String", name.get("kind"));
        assertEquals(Boolean.TRUE, name.get("required"));
        assertEquals(30, name.get("maxLength"));
        assertEquals(
                Arrays.asList(
                        "type", "maxSize", "kind", "label", "labels", "required", "maxLength", "readOnly", "html"),
                Arrays.asList(name.keySet().toArray()));
        assertEquals("enumeration", field(answer, "status").get("type"));
        assertTrue(field(answer, "status").containsKey("enums"));
        // A field only the class has keeps only what it always said.
        assertEquals("String", field(answer, "note").get("type"));
        assertFalse(field(answer, "note").containsKey("kind"));
        assertEquals("String", field(answer, "written").get("kind"));
        assertFalse(field(answer, "written").containsKey("type"));
        assertEquals("Product", map(field(answer, "productId").get("ref")).get("entity"));
    }

    @Test
    public void aClassThatIsNotThereLeavesTheModelsFacts() {
        Map<String, Object> gone = SchemaInfo.of("desk", "Gone");
        assertEquals("Integer", field(gone, "goneId").get("kind"));
        assertEquals(1, map(gone.get("fields")).size());
        // No class and no fields at all: an empty field list.
        assertTrue(map(SchemaInfo.of("desk", "Bare").get("fields")).isEmpty());
    }

    @Test
    public void theAnswerIsPrivate() {
        ResponseEntity<Map<String, Object>> response = SchemaInfo.answer("desk", "Item");
        assertEquals(200, response.getStatusCode().value());
        assertEquals("private", response.getHeaders().getCacheControl());
        assertEquals("Item", map(response.getBody().get("entity")).get("name"));
    }

    @Test
    public void whatTheFileDoesNotHaveIsSaid() {
        IllegalStateException entity = assertThrows(IllegalStateException.class, () -> SchemaInfo.of("desk", "Nope"));
        assertEquals("Nope is not in elasticore/desk-schema.json - generate the code again", entity.getMessage());
        IllegalStateException file = assertThrows(
                IllegalStateException.class, () -> SchemaInfo.read(SchemaInfoTest.class.getClassLoader(), "nowhere"));
        assertEquals(
                "elasticore/nowhere-schema.json is not on the classpath: GET /schema/info answers from it with env.yml"
                        + " config: schemaInfo: model - generate the code again",
                file.getMessage());
        assertEquals("elasticore/shop-schema.json", SchemaInfo.resource("shop"));
    }
}
