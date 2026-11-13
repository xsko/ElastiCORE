package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.model.pub.rules.base.ControllerBaseRules;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @expose(methods="...") as each rule reads it, one artifact at a time: the
 * hexagonal use case, and the TypeScript client and what it imports. The
 * generated projects (ExposeMethodsTest) show the same end to end.
 */
public class ExposeMethodsRulesTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String line) {
            return super.parseFieldLine(name, line, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    // ---- the use case -------------------------------------------------------------

    private EntitySpec ticket(String methods) {
        String base = "com.acme.desk";
        EntitySpec e = new EntitySpec("Ticket")
                .domain("desk", "Desk")
                .meta("root", "")
                .meta("expose", "")
                .pkg("entity", base + ".domain.model")
                .pkg("dto", base + ".application.dto")
                .pkg("service", base + ".application")
                .pkg("control", base + ".adapter.in.web")
                .pkg("inboundPort", base + ".application.port.in")
                .addField(loader.parse("ticketId", "long @id @sequence"))
                .addField(loader.parse("subject", "string(100)! @editable"));
        return methods == null ? e : e.metaProperty("expose", "methods", methods);
    }

    private String port(EntitySpec e) {
        return RuleCatalog.standard()
                .engineFor("hexagonal-springboot")
                .templateSet(SourceCode.TemplateSet.V3)
                .run(e, "usecase")
                .render();
    }

    @Test
    public void thePortWithoutTheListPromisesEveryMethod() {
        String port = port(ticket(null));
        for (String method : new String[] {
            " save(", " findById(", " findBySearch(", " update(", " batchUpdate(", " delete(", " deleteById("
        }) {
            assertTrue(port.contains(method), method + "\n" + port);
        }
    }

    @Test
    public void thePortOfReadsOnlyPromisesNoWrite() {
        String port = port(ticket("read,search"));
        assertTrue(port.contains(" findById(") && port.contains(" findBySearch("), port);
        for (String method : new String[] {" save(", " update(", " batchUpdate(", " delete(", " deleteById("}) {
            assertFalse(port.contains(method), method + "\n" + port);
        }
    }

    @Test
    public void eachWriteNameKeepsItsOwnMethodsOnThePort() {
        String create = port(ticket("create"));
        assertTrue(create.contains(" save("), create);
        assertFalse(
                create.contains(" update(") || create.contains(" delete(") || create.contains(" deleteById("), create);

        String update = port(ticket("update"));
        assertTrue(update.contains(" update(") && update.contains(" batchUpdate("), update);
        assertFalse(
                update.contains(" save(") || update.contains(" delete(") || update.contains(" deleteById("), update);

        String delete = port(ticket("delete"));
        assertTrue(delete.contains(" delete(") && delete.contains(" deleteById("), delete);
        assertFalse(
                delete.contains(" save(") || delete.contains(" update(") || delete.contains(" batchUpdate("), delete);
    }

    // ---- the TypeScript client ----------------------------------------------------

    private EntitySpec article(String methods) {
        EntitySpec e = new EntitySpec("Article")
                .domain("content", "Content")
                .meta("expose", "")
                .apiPath("/api/v1")
                .pkg("dto", "io.acme.content.dto")
                .addField(loader.parse("articleId", "string(36) @id"))
                .addField(loader.parse("title", "string(300)!"));
        return methods == null ? e : e.metaProperty("expose", "methods", methods);
    }

    private SourceCode client(EntitySpec e) {
        return RuleCatalog.standard()
                .engineFor("ts-model")
                .templateSet(SourceCode.TemplateSet.TYPESCRIPT)
                .run(e, "api");
    }

    @Test
    public void aClientWithEveryEndpointImportsEverything() {
        String out = client(article(null)).render();
        assertTrue(out.contains("import { request, requestOrNull, seg } from './apiSupport';"), out);
        assertTrue(out.contains("ArticleDTO") && out.contains("ArticleKeyDTO") && out.contains("ArticleSrchDTO"), out);
    }

    @Test
    public void aClientThatOnlyDeletesImportsTheKeyAndNotTheDto() {
        String out = client(article("delete")).render();
        assertTrue(out.contains("import { request, seg } from './apiSupport';"), out);
        assertTrue(out.contains("import { ArticleKeyDTO }") || out.contains("ArticleKeyDTO } from"), out);
        assertFalse(out.contains("import { ArticleDTO }") || out.contains("ArticleSrchDTO"), out);
    }

    @Test
    public void aClientThatOnlyReadsCallsNoRequest() {
        String out = client(article("read")).render();
        assertTrue(out.contains("import { requestOrNull, seg } from './apiSupport';"), out);
        assertFalse(out.contains("ArticleKeyDTO") || out.contains("ArticleSrchDTO"), out);
    }

    @Test
    public void aClientThatOnlySearchesImportsTheSearchAndNotTheKey() {
        String out = client(article("search")).render();
        assertTrue(out.contains("import { request } from './apiSupport';"), out);
        assertTrue(out.contains("ArticleSrchDTO") && out.contains("ArticleDTO"), out);
        assertFalse(out.contains("ArticleKeyDTO"), out);
    }

    @Test
    public void aClientOfCommandsAloneIsStillWritten() {
        EntitySpec e = article("").addCommand("publish", Collections.<Field>emptyList());
        SourceCode code = client(e);
        assertTrue(code.isMake());
        String out = code.render();
        assertTrue(out.contains("function publish("), out);
        assertTrue(out.contains("import { request, seg } from './apiSupport';"), out);
        assertFalse(out.contains("function create(") || out.contains("ArticleKeyDTO"), out);
    }

    @Test
    public void aClientOfNothingIsNotWritten() {
        assertFalse(client(article("")).isMake());
    }

    // ---- the deprecation's nearest list -------------------------------------------

    @Test
    public void takingTheBatchAloneLeavesPutWhichUpdateOneSays() {
        EntitySpec e = ticket(null).meta("restexclude", "batchUpdate");
        assertTrue(
                ControllerBaseRules.restExcludeDeprecation(e)
                        .equals("[Ticket] @restExclude is deprecated: list what the controller serves instead,"
                                + " @expose(methods=\"create,read,search,updateOne,delete\"). It still applies."),
                ControllerBaseRules.restExcludeDeprecation(e));
    }
}
