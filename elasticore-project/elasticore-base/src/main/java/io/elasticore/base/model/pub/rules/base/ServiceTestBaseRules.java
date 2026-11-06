package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Lines;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;

import java.util.ArrayList;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Base rules for the generated service test, ported from ServiceTestPublisher.
 *
 * The test itself is template text; the rules supply the names it refers to,
 * the sample data it builds its fixtures from, and the shape of the key it
 * looks rows up by.
 *
 * This artifact is written once and then belongs to the developer: 2.x skips
 * a file that already exists rather than regenerating it.
 */
public class ServiceTestBaseRules implements CodeRules {

    @Rule(value = "service-test-vars", target = "service_test", order = 10)
    void vars(EntitySpec e) {
        SourceCode c = ctx(e);
        String name = e.getName();

        c.var("packageName", e.pkg("service"))
                .var("entityPackageName", e.pkg("entity"))
                .var("dtoPackageName", e.pkg("dto"))
                .var("repositoryPackageName", e.pkg("repository"))
                .var("j2eePkgName", "jakarta")
                .var("className", "%sService", name)
                .var("testClassName", "%sServiceTest", name)
                .var("domainName", e.domainClassName())
                .var("entityClassName", name)
                .var("isAbstractEntity", String.valueOf(e.isAbstractEntity()))
                // An abstract entity has no instance to make, so the fixture is a mock that keeps its accessors.
                .var(
                        "newEntity",
                        e.isAbstractEntity()
                                ? String.format(
                                        "org.mockito.Mockito.mock(%s::class.java, org.mockito.Mockito.CALLS_REAL_METHODS)",
                                        name)
                                : String.format("%s()", name))
                .var("orgEntityClassName", name)
                .var("dtoClassName", "%sDTO", name)
                .var("mapperName", "%sMapper", e.domainClassName())
                .var("searchDTOClassName", e.searchDtoClassName())
                .var("searchResultDTOClassName", searchResultDto(e))
                // 2.x tests the extended service of an exposed entity and asserts
                // nothing about the shape of its search result; only the CoreService
                // of an unexposed one gets a list or page section, chosen by whether
                // the search DTO names a page size.
                .var("isExposeOutput", String.valueOf(exposed(e)))
                .var("isPageOutput", String.valueOf(!exposed(e) && e.searchPageable()))
                .var("isListOutput", String.valueOf(!exposed(e) && !e.searchPageable()))
                // The helper holds its repositories as fields with accessors in Java
                // and as properties in Kotlin, as the service itself does.
                .var("repositoryRef", c.syntax().getter("helper", RuleText.uncapital(name)))
                .var("entityFieldName", RuleText.uncapital(name));

        // An entity with @access is tested as the application itself: these
        // tests are of what the service does, and nobody signs in to them.
        boolean kt = c.syntax() == io.elasticore.base.model.pub.rules.Syntax.KOTLIN;
        String mockito = kt ? "MockitoExtension::class" : "MockitoExtension.class";
        String both = mockito + ", io.elasticore.springboot3.security.RunAsSystemExtension" + (kt ? "::class" : ".class");
        // Kotlin takes the classes as varargs; Java's annotation takes one array.
        c.var("testExtensions", ServiceAccessRules.guarded(e) ? (kt ? both : "{" + both + "}") : mockito);

        // What the service is constructed with, mocked. The repository helper
        // and the Spring Data repository behind it by default; a rule set
        // that hands the service ports instead says so (DddRepositoryPortRules).
        String helper = String.format("%sRepositoryHelper", e.domainClassName());
        c.varLine("mockDeclarationList", "    @Mock")
         .varLine("mockDeclarationList", "    private lateinit var helper: %s", helper)
         .varLine("mockDeclarationList", "")
         .varLine("mockDeclarationList", "    @Mock")
         .varLine("mockDeclarationList", "    private lateinit var repository: %sRepository", name)
         .var("mockSetUp", "        `when`(%s).thenReturn(repository)",
                 c.syntax().getter("helper", RuleText.uncapital(name)))
         .var("queryMock", "repository")
         .var("anyEntity", "any(%s::class.java)", name);

        // The entity fixture takes the entity's fields; the DTO fixture takes
        // the DTO's, because a field the DTO left out has no setter there.
        sampleData(c, e, "testDataCreation", "entity", e.fields());
        sampleData(c, e, "testDtoDataCreation", "dto",
                e.dtoFields().isEmpty() ? e.fields() : e.dtoFields());

        key(c, e);
    }

    /** An entity that declares a service or an api is tested through it. */
    private boolean exposed(EntitySpec e) {
        return e.hasMeta("expose") || e.hasMeta("service");
    }

    private String searchResultDto(EntitySpec e) {
        String declared = e.searchResultDtoName();
        return declared == null ? String.format("%sDTO", e.getName()) : declared;
    }

    /**
     * How the test addresses a row. A single key is read back off the fixture;
     * a composite one is built from a variable per field, and the test passes
     * them individually.
     */
    private void key(SourceCode c, EntitySpec e) {
        List<FieldSpec> keys = new ArrayList<FieldSpec>();
        for (FieldSpec f : e.fields()) {
            if (f.hasDsl("id") || f.hasDsl("pk")) keys.add(f);
        }
        if (keys.isEmpty()) return;

        boolean composite = keys.size() > 1;
        c.var("isCompositeKey", String.valueOf(composite));
        c.var("pkType", keys.get(0).typeName());
        c.var("pkName", RuleText.capital(keys.get(0).name()));

        StringBuilder define = new StringBuilder();
        for (FieldSpec f : keys) {
            if (define.length() > 0) define.append(", ");
            define.append(c.syntax().parameter(f.typeName(), f.name()));
        }
        c.var("pkDtoDefine", define.toString());

        if (!composite) {
            FieldSpec pk = keys.get(0);
            c.var("pkDtoInfo2", pk.name())
                    .var("idConstruction", c.syntax().getter("mockEntity", pk.name()))
                    .var("pkFieldParams", "id")
                    .var("idVariableCreation", c.syntax().localVar(pk.typeName(), pk.name(), sampleValue(c, pk)))
                    .var("setupMockDtoKeyValues", "")
                    // The fixture leaves a generated key unset, but the test still
                    // has to look a row up by something - so the key it passes is a
                    // value of the right type rather than the fixture's empty one.
                    .var("pkTestValue", keySample(c, pk))
                    .var("anyId", anyKey(c, e, pk))
                    // The update path looks the row up by the DTO's own key, so the
                    // fixture has to carry one - a generated key leaves it unset.
                    .var(
                            "pkDtoAssign",
                            c.syntax().statement(c.syntax().setter("mockDto", pk.name(), keySample(c, pk))));
            return;
        }

        StringBuilder args = new StringBuilder();
        List<String> variables = new ArrayList<String>();
        List<String> mockSetup = new ArrayList<String>();
        for (FieldSpec f : keys) {
            if (args.length() > 0) args.append(", ");
            args.append(f.name());

            // The declaration closes itself, the way the language closes one,
            // so the template no longer writes a terminator after the slot.
            String close = "";
            variables.add(String.format("%s%s",
                    c.syntax().localVar(f.typeName(), f.name(), sampleValue(c, f)), close));
            mockSetup.add(String.format("//when(%s).thenReturn(%s);",
                    c.syntax().getter("mockDto", f.name()), f.name()));
        }

        // Both slots sit indented in the template: it places their first line,
        // the rest have to place themselves.
        c.var("anyId", "any()");
        c.var("pkDtoInfo2", args.toString())
         .var("idConstruction", args.toString())
         .var("pkFieldParams", args.toString())
         .var("idVariableCreation", Lines.indented("        ", variables))
         .var("setupMockDtoKeyValues", Lines.indented("        ", mockSetup));
    }

    /**
     * The matcher for the key a repository call takes. With @deleted the Kotlin
     * repository redeclares findById and existsById with a key that is not null,
     * where Mockito's any() is null - so the test gives a value of the type when
     * it is (as the DDD port test does). Any other keeps any().
     */
    private String anyKey(SourceCode c, EntitySpec e, FieldSpec pk) {
        if (c.syntax() != io.elasticore.base.model.pub.rules.Syntax.KOTLIN || !SoftDelete.applies(e)) return "any()";
        return String.format(
                "(any(%s::class.javaObjectType) ?: %s)", RuleText.simpleName(pk.typeName()), keySample(c, pk));
    }

    /**
     * A value for the key the test looks rows up by. sampleValue withholds one
     * for a key the database generates, which is right for the fixture and
     * wrong here: a lookup needs something to look up.
     */
    /** A value of the key's type, for a rule that writes into the same test. */
    public static String keySampleOf(SourceCode c, FieldSpec pk) {
        return new ServiceTestBaseRules().keySample(c, pk);
    }

    private String keySample(SourceCode c, FieldSpec pk) {
        String value = sampleValue(c, pk);
        if (value != null && !"null".equals(value)) return value;
        String type = RuleText.simpleName(pk.typeName()).toLowerCase();
        if ("long".equals(type)) return "1L";
        if ("integer".equals(type) || "int".equals(type)) return "1";
        return String.format("\"%s\"", RuleText.capital(pk.name()));
    }

    /** The declarations, each closed with the semicolon that separates them. */
    private List<String> declarations(List<String> variables) {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < variables.size(); i++) {
            boolean last = i == variables.size() - 1;
            out.add(last ? variables.get(i) : String.format("%s;", variables.get(i)));
        }
        return out;
    }

    /**
     * One assignment per field the test can give a value to.
     *
     * Written as a single multi-line value rather than a paragraph: the
     * template line carries "return entity;" after the slot, and a paragraph
     * repeats the whole line once per entry - which would put a return
     * statement after every assignment.
     */
    private void sampleData(SourceCode c, EntitySpec e, String slot, String variable,
                            List<FieldSpec> source) {
        java.util.Set<String> seen = new java.util.HashSet<String>();
        List<String> lines = new ArrayList<String>();

        boolean entity = "entity".equals(variable);
        for (FieldSpec f : source) {
            if (!seen.add(f.name())) continue;
            // A field only the entity's own methods write has no setter here.
            if (entity && c.isSystemManaged(f.name())) continue;
            String value = sampleValue(c, f);
            if (value == null) continue;
            String target = entity ? c.writeTarget(variable, f.name()) : variable;
            lines.add(String.format("        %s", c.syntax().statement(
                    c.syntax().setter(target, f.name(), value))));
        }
        // The trailing empty entry puts the template's own "return" on a line
        // of its own; without it the last assignment and the return would run
        // together.
        if (!lines.isEmpty()) lines.add("");
        c.var(slot, Lines.join(lines));
    }

    /**
     * A value of the field's type the test can assert against. A relation has
     * none, and neither does a key the database generates.
     *
     * The type is matched on its simple name. 2.x matched the whole name, so
     * every type it spells out in full - the temporals, the big numbers, the
     * uuid - fell through to null and the generated tests asserted against
     * fixtures that were never populated.
     */
    private String sampleValue(SourceCode c, FieldSpec f) {
        if (!f.isBaseType()) return null;
        if ((f.hasDsl("id") || f.hasDsl("pk")) && f.hasDsl("genid")) return null;

        String type = RuleText.simpleName(f.typeName()).toLowerCase();
        String name = RuleText.capital(f.name());

        if ("string".equals(type)) {
            // Cut to the column only where the service validates: Kotlin is the
            // one language the rule path writes this test in for a v3 service.
            // The Java test is the 2.x one and keeps 2.x's fixtures exactly.
            return c.syntax() == io.elasticore.base.model.pub.rules.Syntax.KOTLIN
                    ? fit(stringSample(name), f) : stringSample(name);
        }
        if ("integer".equals(type) || "int".equals(type)) return "1";
        if ("long".equals(type)) return "1L";
        if ("boolean".equals(type)) return "true";
        if ("double".equals(type)) return "1.0";
        if ("float".equals(type)) return "1.0f";
        if ("short".equals(type)) return c.syntax().cast("short", "1");
        if ("byte".equals(type)) return c.syntax().cast("byte", "1");
        if ("char".equals(type)) return "'A'";
        if ("instant".equals(type)) return "java.time.Instant.now()";
        if ("byte[]".equals(type)) {
            return c.syntax() == io.elasticore.base.model.pub.rules.Syntax.KOTLIN ? "byteArrayOf(1)" : "new byte[] {1}";
        }
        if ("bigdecimal".equals(type)) return c.syntax().newInstance("java.math.BigDecimal", "\"1.0\"");
        if ("biginteger".equals(type)) return c.syntax().newInstance("java.math.BigInteger", "\"1\"");
        if ("money".equals(type)) return "io.elasticore.springboot3.domain.Money.of(\"1.0\")";
        if ("quantity".equals(type)) return "io.elasticore.springboot3.domain.Quantity.of(1)";
        if ("uuid".equals(type)) return "java.util.UUID.randomUUID()";
        if ("localdate".equals(type)) return "java.time.LocalDate.now()";
        if ("localdatetime".equals(type)) return "java.time.LocalDateTime.now()";
        if ("localtime".equals(type)) return "java.time.LocalTime.now()";
        if ("date".equals(type)) return c.syntax().newInstance("java.util.Date", "");
        return "null";
    }

    /**
     * The sample, cut to the length the field declares.
     *
     * The services validate what they are given now, so a fixture that
     * breaks its own column - "SampleMethod" for a string(10) - is refused
     * before the test reaches what it meant to check.
     */
    private String fit(String quoted, FieldSpec f) {
        int max = f.validationMaxLength();
        if (max <= 0) max = f.maxLength();
        if (max <= 0) return quoted;
        String body = quoted.substring(1, quoted.length() - 1);
        if (body.length() <= max) return quoted;
        return "\"" + body.substring(0, max) + "\"";
    }

    /** A string reads better when it looks like what the field holds. */
    private String stringSample(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("phone")) return "\"010-1234-5678\"";
        if (lower.contains("email")) return "\"test@example.com\"";
        if (lower.contains("name")) return String.format("\"Sample%s\"", name);
        if (lower.contains("no") || lower.contains("contract")) {
            return String.format("\"%s001\"", name.toUpperCase());
        }
        return String.format("\"Sample%s\"", name);
    }
}
