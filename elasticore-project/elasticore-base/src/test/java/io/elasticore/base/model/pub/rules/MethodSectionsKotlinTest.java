package io.elasticore.base.model.pub.rules;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MethodSections on Kotlin services and on generated tests: the sections they are named by, and what goes. */
public class MethodSectionsKotlinTest {

    private static final String SERVICE = String.join(
            "\n",
            "package a",
            "",
            "open class XCoreService(",
            "    protected val helper: Helper",
            ") {",
            "",
            "    /** Every X. */",
            "    open fun findAll(): List<XDTO> {",
            "        return emptyList()",
            "    }",
            "",
            "    /** A new X. */",
            "    @Transactional",
            "    open fun save(dto: XDTO): XDTO? {",
            "        return dto",
            "    }",
            "",
            "    /** An override. */",
            "    override fun update(dto: XDTO): XDTO? = dto",
            "",
            "    /** No modifier: public. */",
            "    fun deleteById(id: Long?): Boolean = true",
            "",
            "    /** A generic one. */",
            "    internal fun <T> convert(value: T): T = value",
            "",
            "    /** A helper of its own. */",
            "    private fun accessCreate(dto: XDTO) {",
            "    }",
            "}");

    private static String apply(String source, String... excluded) {
        return MethodSections.apply(
                source, new HashSet<String>(Arrays.asList(excluded)), Collections.<String>emptyList());
    }

    private static List<String> lines(String text) {
        return Arrays.asList(text.split("\n", -1));
    }

    @Test
    public void aKotlinMethodNamesItsSection() {
        List<String> l = lines(SERVICE);
        assertEquals("findAll", MethodSections.sectionId(l, 6, 11));
        assertEquals("save", MethodSections.sectionId(l, 11, 17));
        assertEquals("update", MethodSections.sectionId(l, 17, 20));
        assertEquals("deleteById", MethodSections.sectionId(l, 20, 23));
        assertEquals("convert", MethodSections.sectionId(l, 23, 26));
        // A private method names none, as a private Java method does not.
        assertEquals("unknown", MethodSections.sectionId(l, 26, 30));
    }

    @Test
    public void excludedKotlinMethodsGoAndTheRestStays() {
        String out = apply(SERVICE, "save", "update", "deleteById");
        assertFalse(out.contains("fun save("), out);
        assertFalse(out.contains("fun update("), out);
        assertFalse(out.contains("fun deleteById("), out);
        assertFalse(out.contains("@Transactional"), out);
        assertTrue(out.contains("open fun findAll()"), out);
        assertTrue(out.contains("private fun accessCreate("), out);
        assertTrue(out.contains("internal fun <T> convert("), out);
        assertTrue(out.endsWith("    }\n}"), out);
        // Nothing asked for, nothing changes.
        assertEquals(SERVICE, apply(SERVICE));
        assertEquals(SERVICE, apply(SERVICE, "unknownName"));
    }

    private static final String TESTS = String.join(
            "\n",
            "package a",
            "",
            "class XServiceTest {",
            "",
            "    @BeforeEach",
            "    fun setUp() {",
            "    }",
            "",
            "    @Test",
            "    @DisplayName(\"findById - found\")",
            "    fun findById_ShouldReturnEntityWhenFound() {",
            "        service.findById(1)",
            "    }",
            "",
            "    @Disabled",
            "    @Test",
            "    fun save_ShouldSave() {",
            "        service.save(dto)",
            "    }",
            "",
            "    /** A javadoc above the test opens its section. */",
            "    @Test",
            "    fun deleteBySearch_ShouldDelete() {",
            "        service.delete(search)",
            "    }",
            "",
            "    @Test",
            "    fun deleteById_ShouldReturnTrue() {",
            "        service.deleteById(1)",
            "    }",
            "}");

    @Test
    public void aTestIsASectionNamedByWhatItTests() {
        List<String> l = lines(TESTS);
        assertFalse(MethodSections.startsATest(l, 4), "@BeforeEach is no test");
        assertTrue(MethodSections.startsATest(l, 8));
        assertFalse(MethodSections.startsATest(l, 9), "the second annotation of a test");
        assertTrue(MethodSections.startsATest(l, 14), "@Disabled above @Test opens it");
        assertFalse(MethodSections.startsATest(l, 15));
        assertFalse(MethodSections.startsATest(l, 21), "a javadoc opened it already");
        assertFalse(MethodSections.startsATest(l, 11), "a line that is no annotation");
        assertTrue(MethodSections.startsATest(Arrays.asList("@Test", "fun a_B() {"), 0), "the first line");
        assertFalse(MethodSections.startsATest(Arrays.asList("@Disabled", "@DisplayName(\"x\")"), 0), "no @Test");
        assertEquals("test:findById", MethodSections.sectionId(l, 8, 14));
        assertEquals("test:save", MethodSections.sectionId(l, 14, 20));
        assertEquals("test:deleteBySearch", MethodSections.sectionId(l, 20, 26));
        assertEquals("test:deleteById", MethodSections.sectionId(l, 26, 31));
        assertEquals("test:save", MethodSections.sectionId(Arrays.asList("@Test", "void save_ShouldSave() {"), 0, 2));
        assertEquals("unknown", MethodSections.sectionId(Arrays.asList("@Test", "val x = 1"), 0, 2));
    }

    @Test
    public void theTestsOfWhatTheServiceLacksGo() {
        String out = apply(TESTS, "test:save", "test:deleteBySearch", "test:deleteById");
        assertFalse(out.contains("save_ShouldSave") || out.contains("@Disabled"), out);
        assertFalse(out.contains("deleteBySearch") || out.contains("A javadoc above"), out);
        assertFalse(out.contains("deleteById_"), out);
        assertTrue(out.contains("fun findById_ShouldReturnEntityWhenFound()"), out);
        assertTrue(out.contains("fun setUp()"), out);
        // The last test gone: no blank line is left before the closing brace.
        assertTrue(out.endsWith("        service.findById(1)\n    }\n}"), out);
        // A test that is not the last goes with its blank line; the closing stays as it was.
        String some = apply(TESTS, "test:save");
        assertFalse(some.contains("save_ShouldSave"), some);
        assertTrue(some.endsWith("        service.deleteById(1)\n    }\n}"), some);
        assertTrue(some.contains("    }\n\n    /** A javadoc above"), some);
    }
}
