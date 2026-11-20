package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * elasticore { runtimePackages }: the rewrite of the runtime's package
 * prefixes, the names it leaves behind, and the classes the project then has
 * to provide.
 */
public class RuntimePackagesTest {

    private static final RuntimePackages MAPPING =
            RuntimePackages.parse("io.elasticore.springboot3=com.test.springboot,"
                    + "io.elasticore.runtime=com.test.runtime,"
                    + "io.elasticore.base.util=com.test.util");

    @Test
    public void unsetIsNoMapping() {
        assertNull(RuntimePackages.parse(null));
        assertNull(RuntimePackages.parse(""));
        assertNull(RuntimePackages.parse("  , "));
    }

    @Test
    public void onlyThePrefixChanges() {
        assertEquals(
                "@ExceptionHandler(com.test.springboot.validation.NotFoundException.class)",
                MAPPING.rewrite("@ExceptionHandler(io.elasticore.springboot3.validation.NotFoundException.class)"));
        assertEquals(
                "String column = com.test.util.StringUtils.camelToSnake(name);",
                MAPPING.rewrite("String column = io.elasticore.base.util.StringUtils.camelToSnake(name);"));
        assertEquals(
                "com.test.springboot.entity.FixedAtCreation.check(this, before);",
                MAPPING.rewrite("io.elasticore.springboot3.entity.FixedAtCreation.check(this, before);"));
    }

    @Test
    public void importsAndInlineNamesAlike() {
        String java = String.join(
                "\n",
                "package com.acme.shop.web;",
                "",
                "import io.elasticore.springboot3.validation.NotFoundException;",
                "import static io.elasticore.springboot3.util.IdUtils.newId;",
                "import io.elasticore.runtime.port.*;",
                "",
                "class X {",
                "    void f() { throw new io.elasticore.springboot3.validation.ForbiddenException(\"no\"); }",
                "}",
                "");
        String out = MAPPING.rewrite(java);
        assertTrue(out.contains("import com.test.springboot.validation.NotFoundException;"), out);
        assertTrue(out.contains("import static com.test.springboot.util.IdUtils.newId;"), out);
        assertTrue(out.contains("import com.test.runtime.port.*;"), out);
        assertTrue(out.contains("new com.test.springboot.validation.ForbiddenException(\"no\")"), out);
        assertFalse(out.contains("io.elasticore"), out);
    }

    @Test
    public void kotlinToo() {
        String kotlin = String.join(
                "\n",
                "import io.elasticore.springboot3.security.CurrentUser",
                "@ExceptionHandler(io.elasticore.springboot3.validation.NotFoundException::class)",
                "val c = io.elasticore.base.util.StringUtils.camelToSnake(n)",
                "");
        String out = MAPPING.rewrite(kotlin);
        assertTrue(out.contains("import com.test.springboot.security.CurrentUser\n"), out);
        assertTrue(out.contains("@ExceptionHandler(com.test.springboot.validation.NotFoundException::class)"), out);
        assertTrue(out.contains("val c = com.test.util.StringUtils.camelToSnake(n)"), out);
    }

    @Test
    public void wholeSegmentsOnly() {
        // Not a prefix of a longer segment, nor a name inside another one.
        assertEquals(
                "import io.elasticore.springboot3x.Foo;", MAPPING.rewrite("import io.elasticore.springboot3x.Foo;"));
        assertEquals(
                "import org.io.elasticore.springboot3.Foo;",
                MAPPING.rewrite("import org.io.elasticore.springboot3.Foo;"));
        assertEquals("import xio.elasticore.runtime.Foo;", MAPPING.rewrite("import xio.elasticore.runtime.Foo;"));
        // The package itself, said alone.
        assertEquals(
                "@ComponentScan(\"com.test.springboot\")",
                MAPPING.rewrite("@ComponentScan(\"io.elasticore.springboot3\")"));
        // A key in the middle of a sentence ends where the name does.
        assertEquals("see com.test.runtime.", MAPPING.rewrite("see io.elasticore.runtime."));
    }

    @Test
    public void theLongestKeyWins() {
        // Given shortest first: the order written must not matter.
        RuntimePackages m =
                RuntimePackages.parse("io.elasticore.springboot3=com.a,io.elasticore.springboot3.security=com.sec");
        assertEquals(
                "import com.sec.CurrentUser;", m.rewrite("import io.elasticore.springboot3.security.CurrentUser;"));
        assertEquals(
                "import com.a.validation.NotFoundException;",
                m.rewrite("import io.elasticore.springboot3.validation.NotFoundException;"));
        assertEquals(
                "io.elasticore.springboot3.security",
                m.mapping().keySet().iterator().next());
        // A longer key that is no whole-segment prefix does not win.
        assertEquals("import com.a.securityx.Foo;", m.rewrite("import io.elasticore.springboot3.securityx.Foo;"));
    }

    @Test
    public void aTextNamingNoRuntimeIsTheSameText() {
        String text = "package com.acme;\nimport java.util.List;\n";
        assertSame(text, MAPPING.rewrite(text));
        String own = "import io.elasticore.blueprint.domain.shop.entity.Order;\n";
        assertSame(own, MAPPING.rewrite(own));
    }

    @Test
    public void keysAndValuesAreChecked() {
        IllegalArgumentException notRuntime =
                assertThrows(IllegalArgumentException.class, () -> RuntimePackages.parse("com.acme.runtime=com.test"));
        assertTrue(
                notRuntime.getMessage().contains("'com.acme.runtime' is not an ElastiCORE runtime package"),
                notRuntime.getMessage());
        assertThrows(IllegalArgumentException.class, () -> RuntimePackages.parse("io.elasticore=com.test"));
        IllegalArgumentException badValue = assertThrows(
                IllegalArgumentException.class, () -> RuntimePackages.parse("io.elasticore.runtime=com.1test"));
        assertTrue(
                badValue.getMessage().contains("'com.1test' of 'io.elasticore.runtime' is not a Java package name"),
                badValue.getMessage());
        assertThrows(IllegalArgumentException.class, () -> RuntimePackages.parse("io.elasticore.runtime=com.class.x"));
        assertThrows(IllegalArgumentException.class, () -> RuntimePackages.parse("io.elasticore.runtime=com..x"));
        assertThrows(IllegalArgumentException.class, () -> RuntimePackages.parse("io.elasticore.runtime="));
        assertThrows(IllegalArgumentException.class, () -> RuntimePackages.parse("io.elasticore.runtime"));
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimePackages.parse("io.elasticore.runtime=com.a,io.elasticore.runtime=com.b"));
    }

    @Test
    public void whatIsLeftUnderTheRuntimeIsFound() {
        RuntimePackages partial = RuntimePackages.parse("io.elasticore.springboot3=com.test.springboot");
        String text = partial.rewrite(String.join(
                "\n",
                "package io.elasticore.blueprint.domain.shop.service;",
                "import io.elasticore.blueprint.domain.shop.entity.Order;",
                "import io.elasticore.blueprint.domain.shop.dto.OrderDTO;",
                "import io.elasticore.springboot3.validation.NotFoundException;",
                "import io.elasticore.runtime.port.*;",
                "class S { String c = io.elasticore.base.util.StringUtils.camelToSnake(\"x\"); }",
                ""));
        Set<String> own = RuntimePackages.ownRoots(Arrays.asList(
                "io.elasticore.blueprint.domain.shop.service", "io.elasticore.blueprint.domain.shop.entity"));
        assertEquals(
                Arrays.asList("io.elasticore.base.util", "io.elasticore.runtime.port"),
                new java.util.ArrayList<String>(RuntimePackages.leftovers(text, own)));
        // Nothing left when every package is mapped.
        assertTrue(RuntimePackages.leftovers(MAPPING.rewrite(text), own).isEmpty());
    }

    @Test
    public void theProjectsOwnPackagesAreNotLeftovers() {
        // A sample under io.elasticore: its own classes are not the runtime's.
        Set<String> own = RuntimePackages.ownRoots(Arrays.asList(
                "io.elasticore.sample.petstore.domain.model", "io.elasticore.sample.petstore.adapter.in.web"));
        assertTrue(own.contains("io.elasticore.sample.petstore"), own.toString());
        String text = "import io.elasticore.sample.petstore.application.dto.PetDTO;\n";
        assertTrue(RuntimePackages.leftovers(text, own).isEmpty());
        // The shared root of packages spread wide is no root: io.elasticore would hide every leftover.
        Set<String> spread =
                RuntimePackages.ownRoots(Arrays.asList("io.elasticore.sample.a", "io.elasticore.blueprint.b"));
        assertFalse(spread.contains("io.elasticore"), spread.toString());
        assertEquals(
                Collections.singleton("io.elasticore.runtime.port"),
                RuntimePackages.leftovers("import io.elasticore.runtime.port.HttpEndpoint;", spread));
    }

    @Test
    public void theClassesToProvideAreListed() {
        String text = MAPPING.rewrite(String.join(
                "\n",
                "import io.elasticore.springboot3.validation.NotFoundException;",
                "import static io.elasticore.springboot3.util.IdUtils.newId;",
                "import io.elasticore.runtime.port.*;",
                "@ExceptionHandler(io.elasticore.springboot3.validation.NotFoundException.class)",
                "Object e = io.elasticore.springboot3.http.Problem.Detail.of(x);",
                "String c = io.elasticore.base.util.StringUtils.camelToSnake(n);",
                "import com.test.shop.entity.Order;",
                ""));
        Set<String> required = MAPPING.requiredClasses(text, Collections.singleton("com.test.shop"));
        assertEquals(
                Arrays.asList(
                        "com.test.runtime.port.*",
                        "com.test.springboot.http.Problem",
                        "com.test.springboot.util.IdUtils",
                        "com.test.springboot.validation.NotFoundException",
                        "com.test.util.StringUtils"),
                new java.util.ArrayList<String>(required));
    }

    @Test
    public void coversSaysWhetherAMappingApplies() {
        assertTrue(MAPPING.covers("io.elasticore.springboot3.validation"));
        assertTrue(MAPPING.covers("io.elasticore.runtime"));
        assertFalse(MAPPING.covers("io.elasticore.base"));
        assertFalse(MAPPING.covers("io.elasticore.springboot3x.a"));
    }

    @Test
    public void theMappingIsSaidSorted() {
        assertEquals(
                "io.elasticore.base.util -> com.test.util, io.elasticore.runtime -> com.test.runtime,"
                        + " io.elasticore.springboot3 -> com.test.springboot",
                MAPPING.describe());
    }

    /** A copy of the runtime under the project's own base package is still the runtime: listed. */
    @Test
    public void aCopyUnderTheProjectsOwnPackageIsListed() {
        RuntimePackages inside = RuntimePackages.parse("io.elasticore.springboot3=com.acme.shop.runtime");
        Set<String> own = RuntimePackages.ownRoots(Arrays.asList("com.acme.shop.entity", "com.acme.shop.dto"));
        assertTrue(own.contains("com.acme.shop"), own.toString());
        String text = inside.rewrite(String.join(
                "\n",
                "import io.elasticore.springboot3.validation.NotFoundException;",
                "import com.acme.shop.entity.Item;",
                ""));
        assertEquals(
                Collections.singleton("com.acme.shop.runtime.validation.NotFoundException"),
                inside.requiredClasses(text, own));
        // A mapping value above the project's packages does not list the project's own classes.
        RuntimePackages above = RuntimePackages.parse("io.elasticore.springboot3=com.acme");
        String both = above.rewrite("import io.elasticore.springboot3.validation.NotFoundException;\n"
                + "import com.acme.shop.entity.Item;\n");
        assertEquals(Collections.singleton("com.acme.validation.NotFoundException"), above.requiredClasses(both, own));
    }

    /** A nested class is provided with the class it is in: the list names that one. */
    @Test
    public void aNestedBinaryNameListsItsOuterClass() {
        String xml = MAPPING.rewrite("<convert converter=\"io.elasticore.springboot3.domain.Money$JpaConverter\"/>\n"
                + "<listener class=\"io.elasticore.springboot3.crypto.Bidx$Listener$Inner\"/>\n");
        assertEquals(
                Arrays.asList("com.test.springboot.crypto.Bidx", "com.test.springboot.domain.Money"),
                new java.util.ArrayList<String>(MAPPING.requiredClasses(xml, Collections.<String>emptySet())));
        assertEquals(
                Collections.singleton("io.elasticore.springboot3.domain"),
                RuntimePackages.leftovers(
                        "x = io.elasticore.springboot3.domain.Money$JpaConverter;", Collections.<String>emptySet()));
    }

    /** The code is Kotlin as well: a package named by a Kotlin keyword does not compile there. */
    @Test
    public void kotlinKeywordsAreNoPackageName() {
        for (String bad : new String[] {
            "com.in.x", "com.fun", "com.object.rt", "com.is", "com.val", "com.when", "com.typealias", "com._", "_"
        }) {
            assertThrows(
                    IllegalArgumentException.class, () -> RuntimePackages.parse("io.elasticore.runtime=" + bad), bad);
        }
        RuntimePackages.parse("io.elasticore.runtime=com.data.inside");
    }

    /** A value under a key would be rewritten again on the next pass: refused, so the rewrite is idempotent. */
    @Test
    public void aValueUnderAKeyIsRefused() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> RuntimePackages.parse("io.elasticore.springboot3=io.elasticore.springboot3.copy"));
        assertTrue(e.getMessage().contains("'io.elasticore.springboot3.copy'"), e.getMessage());
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimePackages.parse(
                        "io.elasticore.springboot3=com.a,io.elasticore.runtime=io.elasticore.springboot3.r"));
        String once = MAPPING.rewrite("import io.elasticore.springboot3.validation.NotFoundException;");
        assertEquals(once, MAPPING.rewrite(once));
    }
}
