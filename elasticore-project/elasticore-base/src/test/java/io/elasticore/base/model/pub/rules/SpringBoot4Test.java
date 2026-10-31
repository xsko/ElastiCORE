package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.model.pub.rules.base.EnumBaseRules;
import io.elasticore.base.model.pub.rules.lang.kotlin.KotlinEnumRules;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring Boot 4 reads JSON with Jackson 3, which looks past Jackson 2's
 * databind annotations: an enum written with them goes out by its name
 * instead of its code, with no error anywhere. For Boot 4 the enum is
 * written with jackson-annotations alone, the same package in both.
 */
public class SpringBoot4Test {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    @AfterEach
    public void clear() {
        System.clearProperty(RulesConfig.PROP_SPRING_BOOT);
    }

    private EnumSpec status() {
        return new EnumSpec("OrderStatus")
                .pkg("enumeration", "com.acme.erp.domain.model")
                .addField(loader.parse("code", "string(3) @code"))
                .addField(loader.parse("label", "string(20)"))
                .constant("DRAFT", "DRF", "Draft")
                .constant("CONFIRMED", "CFM", "Confirmed")
                .jsonField("code")
                .dbField("code");
    }

    private String java(SourceCode.TemplateSet set) {
        return new RuleEngine().register(new EnumBaseRules()).templateSet(set).run(status(), "enum").render();
    }

    @Test
    public void theGenerationIsReadFromTheBuild() {
        assertEquals(3, RulesConfig.springBootMajor());
        System.setProperty(RulesConfig.PROP_SPRING_BOOT, "4.0.8");
        assertEquals(4, RulesConfig.springBootMajor());
        assertTrue(RulesConfig.jackson3());
        System.setProperty(RulesConfig.PROP_SPRING_BOOT, "3.3.2");
        assertFalse(RulesConfig.jackson3());
    }

    @Test
    public void onBoot4TheEnumNamesNoJackson2Type() {
        System.setProperty(RulesConfig.PROP_SPRING_BOOT, "4");
        for (SourceCode.TemplateSet set : new SourceCode.TemplateSet[]{SourceCode.TemplateSet.V3, SourceCode.TemplateSet.COMPATIBLE}) {
            String out = java(set);
            assertFalse(out.contains("com.fasterxml.jackson.databind"), out);
            assertFalse(out.contains("com.fasterxml.jackson.core"), out);
            assertTrue(out.contains("import com.fasterxml.jackson.annotation.JsonValue;"), out);
            assertTrue(out.contains("@JsonCreator(mode = JsonCreator.Mode.DELEGATING)\n    public static OrderStatus fromCode(String code)")
                    || out.contains("@JsonCreator(mode = JsonCreator.Mode.DELEGATING)\r\n    public static OrderStatus fromCode(String code)"), out);
            assertTrue(out.contains("@JsonValue"), out);
            assertTrue(out.contains("return this.code;"), out);
            // Binding a request parameter and the column converter stay as they were.
            assertTrue(out.contains("class PropertyEditor"), out);
            assertTrue(out.contains("class EntityConverter"), out);
        }
    }

    @Test
    public void onBoot3TheEnumIsWhatItWas() {
        String out = java(SourceCode.TemplateSet.V3);
        assertTrue(out.contains("@JsonSerialize(using = OrderStatus.CustomSerializer.class)"), out);
        assertFalse(out.contains("@JsonValue"), out);
    }

    @Test
    public void theKotlinEnumToo() {
        System.setProperty(RulesConfig.PROP_SPRING_BOOT, "4");
        String out = new RuleEngine().register(new KotlinEnumRules())
                .templateSet(SourceCode.TemplateSet.KOTLIN).run(status(), "enum").render();
        assertFalse(out.contains("com.fasterxml.jackson.databind"), out);
        assertTrue(out.contains("@JsonCreator(mode = JsonCreator.Mode.DELEGATING)"), out);
        assertTrue(out.contains("fun jsonValue(): String = code"), out);
    }

    @Test
    public void theOutboxRecorderUsesTheApplicationsJackson3Mapper() {
        System.setProperty(RulesConfig.PROP_SPRING_BOOT, "4");
        DomainSpec d = new DomainSpec("erp", "Erp");
        d.pkg("service", "com.acme.erp.application");
        String out = RuleCatalog.standard().engineFor("ddd-springboot").templateSet(SourceCode.TemplateSet.V3)
                .run(d, "outboxRecorder").render();
        assertTrue(out.contains("import tools.jackson.databind.json.JsonMapper;"), out);
        assertTrue(out.contains("ObjectProvider<JsonMapper>"), out);
        assertFalse(out.contains("com.fasterxml.jackson.databind"), out);
    }

    /**
     * Spring Data 4 declares Specification with JSpecify: Kotlin needs the
     * entity type parameter non-null, and Page.map a function that returns a
     * value. Only on Spring Boot 4 - the Spring Boot 3 output stays as it was.
     */
    @Test
    public void kotlinOnBoot4BoundsTheSpecificationType() {
        DomainSpec d = new DomainSpec("erp", "Erp");
        d.pkg("repository", "com.acme.erp.infrastructure.persistence");
        d.pkg("entity", "com.acme.erp.domain.model");
        d.entity(new EntitySpec("Partner").field("partnerId", "long @id"));
        RuleEngine kt = RuleCatalog.standard().engineFor("hexagonal-springboot-kt").templateSet(SourceCode.TemplateSet.KOTLIN);

        String boot3 = kt.run(d, "repositoryHelper").render();
        assertTrue(boot3.contains("fun <T> findFieldValueBy("), boot3);

        System.setProperty(RulesConfig.PROP_SPRING_BOOT, "4");
        String boot4 = kt.run(d, "repositoryHelper").render();
        assertTrue(boot4.contains("fun <T : Any> findFieldValueBy("), boot4);
        assertTrue(boot4.contains("fun <E : Any> findStrFuncValue("), boot4);
        assertEquals(io.elasticore.base.model.pub.rules.lang.kotlin.KotlinSpring.pageNonNull(), "!!");
    }
}
