package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A generated ApiErrors and an application's catch-all advice, both last in line, are said. */
public class AdviceOrderTest {

    private static final String GENERATED_LAST = "package a.web;\n"
            + "import io.elasticore.springboot3.validation.RequestShapeErrors;\n"
            + "@Order(Ordered.LOWEST_PRECEDENCE)\n"
            + "@RestControllerAdvice(basePackageClasses = ShopApiErrors.class)\n"
            + "@Import(RequestShapeErrors.class)\n"
            + "public class ShopApiErrors {\n"
            + "    @ExceptionHandler(EntityNotFoundException.class)\n"
            + "    ResponseEntity<?> notFound(EntityNotFoundException e) { return null; }\n"
            + "}\n";

    private static final String CATCH_ALL = "package a.support;\n"
            + "@RestControllerAdvice\n"
            + "public class CustomGlobalExceptionHandler {\n"
            + "    @ExceptionHandler(Throwable.class)\n"
            + "    ResponseEntity<?> any(Throwable t) { return null; }\n"
            + "}\n";

    private static List<String> clashes(String generated, String house) {
        Map<String, String> sources = new TreeMap<String, String>();
        sources.put("src/main/java/a/web/ShopApiErrors.java", generated);
        sources.put("src/main/java/a/support/CustomGlobalExceptionHandler.java", house);
        return RulesGenRunner.adviceOrderClashes(sources);
    }

    @Test
    public void aCatchAllWithNoOrderTiesWithTheGeneratedAdvice() {
        List<String> out = clashes(GENERATED_LAST, CATCH_ALL);
        assertEquals(1, out.size(), out.toString());
        assertTrue(
                out.get(0)
                        .contains("src/main/java/a/support/CustomGlobalExceptionHandler.java answers every"
                                + " exception and is last in line, as src/main/java/a/web/ShopApiErrors.java is"),
                out.get(0));
        assertTrue(out.get(0).contains("@Order(Ordered.LOWEST_PRECEDENCE - 1)"), out.get(0));
        // Exception, RuntimeException, and the lowest order written out, tie as well.
        assertEquals(
                1,
                clashes(GENERATED_LAST, CATCH_ALL.replace("Throwable.class", "{Exception.class}"))
                        .size());
        assertEquals(
                1,
                clashes(
                                GENERATED_LAST,
                                CATCH_ALL
                                        .replace("Throwable.class", "value = RuntimeException.class")
                                        .replace(
                                                "@RestControllerAdvice",
                                                "@Order(Ordered.LOWEST_PRECEDENCE)\n@ControllerAdvice"))
                        .size());
    }

    @Test
    public void anOrderSettlesIt() {
        // The generated advice next to last, as it is generated now,
        assertTrue(clashes(GENERATED_LAST.replace("LOWEST_PRECEDENCE)", "LOWEST_PRECEDENCE - 1)"), CATCH_ALL)
                .isEmpty());
        // or the application's advice with an order of its own,
        assertTrue(clashes(GENERATED_LAST, "@Order(10)\n" + CATCH_ALL.substring(CATCH_ALL.indexOf('@')))
                .isEmpty());
        // or one that does not answer every exception.
        assertTrue(clashes(GENERATED_LAST, CATCH_ALL.replace("Throwable.class", "IllegalStateException.class"))
                .isEmpty());
        // A comment that says "class" or "@Order (" before the annotations does not hide them.
        String commented = GENERATED_LAST
                .replace("LOWEST_PRECEDENCE)", "LOWEST_PRECEDENCE - 1)")
                .replace("@Order(", "/** Handle one in this class. */\n// with no @Order (a catch-all)\n@Order(");
        assertTrue(clashes(commented, CATCH_ALL).isEmpty());
        // A project without a generated advice has nothing to say.
        assertTrue(clashes(CATCH_ALL.replace("CustomGlobalExceptionHandler", "ShopApiErrors"), CATCH_ALL)
                .isEmpty());
    }

    /** What is generated now, Java and Kotlin, is next to last: no warning, build after build. */
    @Test
    public void theGeneratedAdviceIsNotFlagged() throws Exception {
        for (String tmpl : new String[] {"v3/apiErrors.tmpl", "kt/apiErrors.tmpl"}) {
            java.io.InputStream in = AdviceOrderTest.class.getResourceAsStream("/elasticore-template/" + tmpl);
            assertTrue(in != null, tmpl);
            String text;
            try {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) bytes.write(buf, 0, n);
                text = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            } finally {
                in.close();
            }
            String generated = text.replaceAll("\\$\\{className\\}", "ShopApiErrors")
                    .replaceAll("\\$\\{[A-Za-z]+\\}", "x");
            assertTrue(clashes(generated, CATCH_ALL).isEmpty(), tmpl);
            // and the same file at the lowest precedence is still said.
            assertEquals(
                    1,
                    clashes(generated.replace("LOWEST_PRECEDENCE - 1", "LOWEST_PRECEDENCE"), CATCH_ALL).size(),
                    tmpl);
        }
    }
}
