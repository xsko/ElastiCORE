package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.model.pub.rules.base.DtoBaseRules;
import io.elasticore.base.model.pub.rules.lang.java.DtoFileRules;
import io.elasticore.base.util.ConsoleLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A DTO field that declares the key a reference by id already holds is left out for the identity (#103).
 * Under a name of its own it is said, once; under the identity's name, or as the relation's flattened
 * key the DTO adds for itself, it is not.
 */
public class ReferenceOnceTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    @BeforeEach
    @AfterEach
    void quiet() {
        ConsoleLog.clearKey("DSL_WARN");
        SourceCode.resetWarnings();
    }

    /** A DTO of SfComment with its identity and one declared field. */
    private String render(String identity, String declared, String declaredLine) {
        EntitySpec e = new EntitySpec("SfComment")
                .domain("forum", "Forum")
                .dtoClassName("SfCommentKeyedDTO")
                .pkg("dto", "io.acme.forum.dto")
                .addField(loader.parse("commentId", "long @id @sequence"))
                .addField(loader.parse(identity, "long @idof(parent)"))
                .addField(loader.parse(declared, declaredLine));
        return new RuleEngine()
                .register(new DtoBaseRules())
                .register(new DtoFileRules())
                .run(e, "dto")
                .render();
    }

    private static int warnings() {
        return ConsoleLog.countStoredLogkey("DSL_WARN");
    }

    @Test
    public void aKeyUnderANameOfItsOwnIsLeftOutAndSaid() {
        String dto = render("parentCommentId", "parentKey", "long @ref(parent.commentId)");
        assertFalse(dto.contains("parentKey;"), dto);
        assertTrue(dto.contains("private Long parentCommentId;"), dto);
        assertEquals(1, warnings());
        // Said once, however many times the rule runs for the DTO's artifacts.
        render("parentCommentId", "parentKey", "long @ref(parent.commentId)");
        assertEquals(1, warnings());
    }

    @Test
    public void theWarningSaysWhatToDo() throws Exception {
        render("parentCommentId", "parentKey", "long @ref(parent.commentId)");
        java.io.PrintStream out = System.out;
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(bytes, true, "UTF-8"));
        try {
            ConsoleLog.printStoredLog("DSL_WARN", "");
        } finally {
            System.setOut(out);
        }
        String said = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(
                said.contains("[Forum] SfCommentKeyedDTO.parentKey @ref(parent.commentId) is the key the reference by"
                        + " id already holds, so the DTO has it as parentCommentId and has no parentKey - a request that sends"
                        + " parentKey is not read. Name the field parentCommentId, or drop it."),
                said);
    }

    /** Declared under the identity's name, it is the identity's one field: kept, and nothing is said. */
    @Test
    public void aKeyUnderTheIdentitysNameIsNotSaid() {
        EntitySpec e = new EntitySpec("SfComment")
                .domain("forum", "Forum")
                .pkg("dto", "io.acme.forum.dto")
                .addField(loader.parse("commentId", "long @id @sequence"))
                .addField(loader.parse("parentCommentId", "long @ref(parent.commentId)"));
        String dto = new RuleEngine()
                .register(new DtoBaseRules())
                .register(new DtoFileRules())
                .run(e, "dto")
                .render();
        assertTrue(dto.contains("private Long parentCommentId;"), dto);
        assertEquals(0, warnings());
    }

    /** department -> departmentId: the DTO adds departmentDepartmentId for the relation itself. */
    @Test
    public void theRelationsFlattenedKeyIsNotSaid() {
        EntitySpec e = new EntitySpec("SfEmployee")
                .domain("forum", "Forum")
                .pkg("dto", "io.acme.forum.dto")
                .addField(loader.parse("empId", "long @id @sequence"))
                .addField(loader.parse("departmentId", "long @idof(department)"))
                .addField(loader.parse("departmentDepartmentId", "long @ref(department.departmentId)"));
        String out = new RuleEngine()
                .register(new DtoBaseRules())
                .register(new DtoFileRules())
                .run(e, "dto")
                .render();
        // Left out for the identity, which is named for the key itself.
        assertFalse(out.contains("departmentDepartmentId"), out);
        assertTrue(out.contains("private Long departmentId;"), out);
        assertEquals(0, warnings());
    }

    /** Not the key a reference by id holds: kept as declared, and nothing is said. */
    @Test
    public void otherRefsStay() {
        String[][] lines = {
            {"parentName", "string @ref(parent.content)"},
            {"parentRef", "long @ref(parent)"},
            {"parentDeep", "long @ref(parent.post.postId)"},
            {"otherKey", "long @ref(other.commentId)"},
        };
        for (String[] line : lines) {
            String dto = render("parentCommentId", line[0], line[1]);
            assertTrue(dto.contains(" " + line[0] + ";"), line[1] + "\n" + dto);
        }
        assertEquals(0, warnings());
    }
}
