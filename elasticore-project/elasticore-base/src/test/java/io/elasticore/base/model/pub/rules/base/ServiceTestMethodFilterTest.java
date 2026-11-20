package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.RuleCatalog;
import io.elasticore.base.model.pub.rules.SourceCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated service test calls only what the service has: @readonly and @restExclude
 * take methods off the Kotlin service, and their tests go with them. The 2.x-compatible
 * output keeps every test, as its service keeps every method.
 */
public class ServiceTestMethodFilterTest {

    private static EntitySpec notice() {
        String base = "com.acme.board";
        return new EntitySpec("Notice")
                .domain("board", "Board")
                .meta("expose", "")
                .pkg("entity", base + ".entity")
                .pkg("repository", base + ".repository")
                .pkg("dto", base + ".dto")
                .pkg("service", base + ".service")
                .field("noticeId", "long @id @sequence")
                .field("text", "string(200)");
    }

    private static String render(EntitySpec e, String bundle, SourceCode.TemplateSet set, String target) {
        return RuleCatalog.standard()
                .engineFor(bundle)
                .templateSet(set)
                .run(e, target)
                .render()
                .replace("\r", "");
    }

    private static String kotlinTest(EntitySpec e) {
        return render(e, "jpa-springboot-kt", SourceCode.TemplateSet.KOTLIN, "service_test");
    }

    @Test
    public void everyTestWithoutAnExclusion() {
        String out = kotlinTest(notice());
        assertTrue(out.contains("service.save(mockDto)"), out);
        assertTrue(out.contains("service.update(mockDto)"), out);
        assertTrue(out.contains("service.deleteById("), out);
        assertTrue(out.contains("service.findById("), out);
    }

    @Test
    public void aReadonlyEntityIsTestedOnlyForItsReads() {
        String out = kotlinTest(notice().meta("readonly", null));
        assertFalse(out.contains("service.save("), out);
        assertFalse(out.contains("service.update("), out);
        assertFalse(out.contains("service.deleteById("), out);
        assertTrue(out.contains("service.findById("), out);
        assertTrue(out.contains("service.findAll()"), out);
        assertTrue(out.endsWith("    }\n}") || out.endsWith("    }\n}\n"), out);
        // The service the test calls: no write method either.
        String service =
                render(notice().meta("readonly", null), "jpa-springboot-kt", SourceCode.TemplateSet.KOTLIN, "service");
        assertFalse(
                service.contains("fun save(") || service.contains("fun update(") || service.contains("fun deleteById("),
                service);
        assertTrue(service.contains("open fun findById("), service);
    }

    @Test
    public void eachExcludedMethodTakesItsTestAlone() {
        String deleteGone = kotlinTest(notice().meta("restexclude", "deleteById"));
        assertFalse(deleteGone.contains("service.deleteById("), deleteGone);
        assertTrue(deleteGone.contains("service.save(") && deleteGone.contains("service.update("), deleteGone);

        String updateGone = kotlinTest(notice().meta("restexclude", "update"));
        assertFalse(updateGone.contains("service.update("), updateGone);
        assertTrue(updateGone.contains("service.save(") && updateGone.contains("service.deleteById("), updateGone);

        String saveGone = kotlinTest(notice().meta("restexclude", "save"));
        assertFalse(saveGone.contains("service.save("), saveGone);
        assertTrue(saveGone.contains("service.update(") && saveGone.contains("service.deleteById("), saveGone);
    }

    @Test
    public void theTwoXOutputKeepsEveryTest() {
        String out = render(
                notice().meta("readonly", null), "jpa-springboot", SourceCode.TemplateSet.COMPATIBLE, "service_test");
        assertTrue(out.contains("service.save(mockDto)"), out);
        assertTrue(out.contains("service.deleteById("), out);
        assertTrue(out.contains("service.save(mockDto);\n        Optional<"), out);
    }
}
