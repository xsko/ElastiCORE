package io.elasticore.base.model.loader;

import io.elasticore.base.model.core.Annotation;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.RuleCatalog;
import io.elasticore.base.model.pub.rules.SourceCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A quoted annotation value holds what a regular expression holds (issue 267): the '=' of a
 * lookahead {@code (?=...)} and groups in groups. Before, the '=' made the loader read the
 * parameters as key=value pairs, and {@code @pattern('^(?=.*[A-Za-z])(?=.*[0-9]).+$', '...')}
 * wrote {@code @Pattern(regexp = "digit'")} with the default message - every value refused.
 */
public class ParenthesesInQuotedValueTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String line) {
            return super.parseFieldLine(name, line, null);
        }

        Annotation params(String name, String parameters) {
            return super.parseParameters(name, parameters);
        }
    }

    private final TestLoader loader = new TestLoader();

    private EntitySpec member(String line) {
        return new EntitySpec("Member")
                .domain("crm", "Crm")
                .pkg("entity", "com.acme.crm.entity")
                .pkg("dto", "com.acme.crm.dto")
                .addField(loader.parse("password", line));
    }

    private FieldSpec field(String line) {
        return member(line).fields().get(0);
    }

    private static final String ISSUE =
            "string(255)! @pattern('^(?=.*[A-Za-z])(?=.*[0-9]).+$', 'password needs a letter and a digit')";

    @Test
    public void theIssuesLookaheadsStayInTheRegex() {
        FieldSpec f = field(ISSUE);
        assertEquals("^(?=.*[A-Za-z])(?=.*[0-9]).+$", f.patternRegexp());
        assertEquals("password needs a letter and a digit", f.patternMessage());
    }

    @Test
    public void theJavaDtoAndTheTypeScriptClientGetTheWholePattern() {
        String java = RuleCatalog.standard()
                .engineFor("jpa-dto", "jpa-dto-file")
                .templateSet(SourceCode.TemplateSet.V3)
                .run(member(ISSUE), "dto")
                .render();
        assertTrue(
                java.contains(
                        "@Pattern(regexp = \"^(?=.*[A-Za-z])(?=.*[0-9]).+$\", message = \"password needs a letter and a digit\")"),
                java);
        String ts = RuleCatalog.standard()
                .engineFor("ts-model")
                .templateSet(SourceCode.TemplateSet.TYPESCRIPT)
                .run(member(ISSUE), "dto")
                .render();
        assertTrue(ts.contains("(?=.*[A-Za-z])(?=.*[0-9])"), ts);
        assertTrue(ts.contains("password needs a letter and a digit"), ts);
    }

    @Test
    public void aGroupInAGroupStaysInTheRegex() {
        FieldSpec f = field("string(20) @pattern('^((AB|CD)-[0-9]{3})$', 'a code such as AB-123') -- 코드");
        assertEquals("^((AB|CD)-[0-9]{3})$", f.patternRegexp());
        assertEquals("a code such as AB-123", f.patternMessage());
        assertEquals("코드", f.dslLabel());
    }

    @Test
    public void anEqualsInsideDoubleQuotesToo() {
        FieldSpec f = field("string(20) @pattern(\"^(?!x=).+$\", \"must not start with x=\")");
        assertEquals("^(?!x=).+$", f.patternRegexp());
        assertEquals("must not start with x=", f.patternMessage());
    }

    @Test
    public void keyValuePairsAreReadAsBefore() {
        // An '=' outside quotes is a pair, as in 2.x.
        Annotation named = loader.params("pattern", "regexp=\"^(?=.*[0-9]).+$\", message=\"needs a digit\"");
        assertEquals("^(?=.*[0-9]).+$", named.getProperties().getProperty("regexp"));
        assertEquals("needs a digit", named.getProperties().getProperty("message"));
        // An apostrophe that closes nothing is a letter: the '=' after it still makes a pair.
        Annotation apostrophe = loader.params("ui", "title=Owner's name, order=3");
        assertEquals("3", apostrophe.getProperties().getProperty("order"));
        // A value without '=' is one value.
        assertEquals("'^[A-Z]+$', 'capitals'", loader.params("pattern", "'^[A-Z]+$', 'capitals'").value);
    }
}
