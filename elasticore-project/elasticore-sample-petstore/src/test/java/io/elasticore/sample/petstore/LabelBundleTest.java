package io.elasticore.sample.petstore;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The labels the DSL writes in other languages (Item: @label(en=..., ja=...) -- 단가)
 * as the build generates them: a bundle Spring's MessageSource reads by the
 * request's Locale, and the Ossie synonyms of the field.
 */
class LabelBundleTest {

    private static ResourceBundleMessageSource labels() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/petstore-labels");
        // The default file is the domain's own language, not the machine's.
        source.setFallbackToSystemLocale(false);
        return source;
    }

    @Test
    void eachLanguageGetsItsLabelAndTheRestTheDomainsOwn() {
        ResourceBundleMessageSource source = labels();
        assertThat(source.getMessage("Item.unitCost", null, Locale.ENGLISH)).isEqualTo("Unit cost");
        assertThat(source.getMessage("Item.unitCost", null, Locale.JAPANESE)).isEqualTo("単価");
        assertThat(source.getMessage("Item.unitCost", null, Locale.KOREAN)).isEqualTo("단가");
        assertThat(source.getMessage("Item.unitCost", null, Locale.FRENCH)).isEqualTo("단가");
        assertThat(source.getMessage("Item", null, Locale.ENGLISH)).isEqualTo("Item for sale");
        // A label with no translation: the "--" text in every language.
        assertThat(source.getMessage("Category", null, Locale.ENGLISH)).isEqualTo("분류 (Fish, Dogs ...)");
    }

    /**
     * env.yml labelLanguage: ko - the "--" labels have a file of their own
     * language, which a refusal said in Korean reads; English has only what
     * is written in English, so an English answer never says the Korean text.
     */
    @Test
    void theLabelLanguageHasAFileOfItsOwn() throws Exception {
        java.util.Properties ko = file("i18n/petstore-labels_ko.properties");
        assertThat(ko.getProperty("PurchaseOrder")).isEqualTo("주문");
        assertThat(ko.getProperty("PurchaseOrder.status")).isEqualTo("주문 상태");
        assertThat(ko.getProperty("OrderStatus.SHIPPED")).isEqualTo("배송됨");
        java.util.Properties en = file("i18n/petstore-labels_en.properties");
        assertThat(en.getProperty("PurchaseOrder.status")).isEqualTo("Order status");
        assertThat(en.getProperty("PurchaseOrder")).isNull();
        assertThat(en.getProperty("OrderStatus.SHIPPED")).isNull();
    }

    private static java.util.Properties file(String name) throws Exception {
        java.util.Properties p = new java.util.Properties();
        try (InputStream in = LabelBundleTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as(name).isNotNull();
            p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        return p;
    }

    /** Why the guide says fallback-to-system-locale: false - Spring Boot's default answers in the machine's language. */
    @Test
    void withTheSystemLocaleFallbackALanguageWithoutAFileGetsTheMachinesOne() {
        Locale machine = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ENGLISH);
            ResourceBundleMessageSource source = labels();
            source.setFallbackToSystemLocale(true);
            assertThat(source.getMessage("Item.unitCost", null, Locale.FRENCH)).isEqualTo("Unit cost");
        } finally {
            Locale.setDefault(machine);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void theTranslationsAreTheFieldsOssieSynonyms() throws Exception {
        Map<String, Object> model;
        try (InputStream in = getClass().getResourceAsStream("/blueprint/semantic_model.yml")) {
            model = new Yaml().load(in);
        }
        for (Map<String, Object> dataset : (List<Map<String, Object>>) model.get("datasets")) {
            if (!"Item".equals(dataset.get("name"))) continue;
            assertThat((List<String>) ((Map<String, Object>) dataset.get("ai_context")).get("synonyms"))
                    .containsExactly("pet", "반려동물", "상품", "Item for sale", "販売単位");
            for (Map<String, Object> field : (List<Map<String, Object>>) dataset.get("fields")) {
                if (!"unit_cost".equals(field.get("name"))) continue;
                assertThat(field.get("description")).isEqualTo("단가");
                assertThat((List<String>) ((Map<String, Object>) field.get("ai_context")).get("synonyms"))
                        .containsExactly("price", "가격", "Unit cost", "単価");
                return;
            }
        }
        throw new AssertionError("Item.unit_cost is not in the semantic model");
    }
}
