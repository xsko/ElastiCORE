package io.elasticore.base.model.pub.rules;

import org.junit.jupiter.api.Test;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a project's own rule set writes reaches the running application: a
 * constraint a rule puts on the search DTO is checked (the v3 search endpoint
 * validates its body, as create does), and a method a rule adds to an entity
 * class is written even when the class has no methods of its own.
 */
public class ProjectRuleReachTest {

    /** A team rule: a LIKE search needs two characters. */
    public static class LikeMinRules implements CodeRules {
        @Rule(value = "test-like-min", target = "srchdto")
        void likeMin(EntitySpec search, FieldSpec f) {
            if ("like".equals(f.dslValue("search"))) {
                ctx(search).field(f).ensure(AnnoCode.of("jakarta.validation.constraints.Size").val("min", 2));
            }
        }
    }

    /** A team rule: every root says who it is in an audit log. */
    public static class AuditLabelRules implements CodeRules {
        @Rule("test-audit-label")
        void auditLabel(EntitySpec e) {
            ctx(e).addMethod("\tpublic String auditLabel() {\n\t\treturn \"" + e.getName() + "#\" + getCategoryId();\n\t}");
        }
    }

    private EntitySpec category() {
        String base = "com.acme.shop";
        return new EntitySpec("Category")
                .domain("shop", "Shop")
                .meta("root", "")
                .pkg("entity", base + ".domain.model")
                .pkg("dto", base + ".application.dto")
                .pkg("service", base + ".application")
                .pkg("control", base + ".adapter.in.web")
                .field("categoryId", "long @id @sequence")
                .field("name", "string(30)! @search(like)");
    }

    @Test
    public void theV3SearchEndpointValidatesItsBody() {
        String java = RuleCatalog.standard().engineFor("hexagonal-springboot")
                .templateSet(SourceCode.TemplateSet.V3).run(category(), "control").render();
        assertTrue(java.contains("search(@Valid @RequestBody CategorySrchDTO searchDTO)"), java);

        String kotlin = RuleCatalog.standard().engineFor("hexagonal-springboot-kt")
                .templateSet(SourceCode.TemplateSet.KOTLIN).run(category(), "control").render();
        assertTrue(kotlin.contains("fun search(@Valid @RequestBody searchDTO: CategorySrchDTO)"), kotlin);
    }

    @Test
    public void aRuleConstraintLandsOnTheSearchDto() {
        String out = RuleCatalog.standard().engineFor("hexagonal-springboot")
                .register(new LikeMinRules())
                .templateSet(SourceCode.TemplateSet.V3).run(category(), "srchdto").render();
        assertTrue(out.contains("@Size(min = 2)"), out);
    }

    @Test
    public void anAddedMethodIsWrittenOnAnEntityWithoutMethods() {
        String out = RuleCatalog.standard().engineFor("hexagonal-springboot")
                .register(new AuditLabelRules())
                .templateSet(SourceCode.TemplateSet.V3).run(category(), "entity").render();
        assertTrue(out.contains("public String auditLabel() {"), out);
        assertTrue(out.trim().endsWith("}"), out);
        int method = out.indexOf("public String auditLabel()");
        int lastField = out.lastIndexOf("private String name;");
        assertTrue(lastField >= 0 && method > lastField, out);
    }

    /** A team rule written for the developer's class itself. */
    public static class BehaviorHelperRules implements CodeRules {
        @Rule(value = "test-behavior-helper", target = "behavior")
        void helper(EntitySpec e) {
            ctx(e).addMethod("\tpublic boolean isNamed() {\n\t\treturn getName() != null;\n\t}");
        }
    }

    /** A team rule for enums. */
    public static class EnumHelperRules implements CodeRules {
        @Rule(value = "test-enum-helper", target = "enum")
        void helper(EnumSpec e) {
            ctx(e).addMethod("\tpublic boolean isFinal() {\n\t\treturn this == DONE;\n\t}");
        }
    }

    @Test
    public void aRuleForTheDevelopersClassStillAddsItsMethod() {
        EntitySpec split = category().entitySplitForBehavior("Category");
        String mine = RuleCatalog.standard().engineFor("hexagonal-springboot")
                .register(new BehaviorHelperRules()).templateSet(SourceCode.TemplateSet.V3)
                .run(split, "behavior").render();
        assertTrue(mine.contains("public boolean isNamed()"), mine);
    }

    @Test
    public void anAddedMethodIsWrittenOnAKotlinEntityWithoutMethods() {
        String out = RuleCatalog.standard().engineFor("hexagonal-springboot-kt")
                .register(new KotlinAuditLabelRules()).templateSet(SourceCode.TemplateSet.KOTLIN)
                .run(category(), "entity").render();
        assertTrue(out.contains("fun auditLabel(): String"), out);
        assertTrue(out.indexOf("fun auditLabel()") < out.lastIndexOf("}"), out);
    }

    /** The Kotlin form of the audit label. */
    public static class KotlinAuditLabelRules implements CodeRules {
        @Rule("test-audit-label-kt")
        void auditLabel(EntitySpec e) {
            ctx(e).addMethod("    fun auditLabel(): String = \"" + e.getName() + "#\" + categoryId");
        }
    }

    @Test
    public void anAddedMethodIsWrittenOnAnEnum() {
        EnumSpec status = new EnumSpec("TaskStatus").constant("OPEN", "O", "Open").constant("DONE", "D", "Done")
                .pkg("enumeration", "com.acme.shop.domain.model");
        String out = RuleCatalog.standard().engineFor("hexagonal-springboot")
                .register(new EnumHelperRules()).templateSet(SourceCode.TemplateSet.V3)
                .run(status, "enum").render();
        assertTrue(out.contains("public boolean isFinal()"), out);
        assertTrue(out.indexOf("public boolean isFinal()") > out.indexOf("DONE("), out);
    }

    @Test
    public void exclusionsWithoutSectionsLeaveTheTextAsItIs() {
        String text = "package a;\r\n\r\npublic class A {\r\n\tprivate int x;\r\n}";
        assertTrue(text == MethodSections.apply(text, java.util.Collections.singleton("deleteById"),
                java.util.Collections.<String>emptyList()));
    }

    @Test
    public void aSplitRootGetsTheAddedMethodOnItsGeneratedBaseOnly() {
        EntitySpec split = category().entitySplitForBehavior("Category");
        RuleEngine engine = RuleCatalog.standard().engineFor("hexagonal-springboot")
                .register(new AuditLabelRules()).templateSet(SourceCode.TemplateSet.V3);
        String base = engine.run(split, "entity").render();
        String mine = engine.run(split, "behavior").render();
        assertTrue(base.contains("public String auditLabel()"), base);
        assertTrue(!mine.contains("auditLabel"), mine);
    }
}
