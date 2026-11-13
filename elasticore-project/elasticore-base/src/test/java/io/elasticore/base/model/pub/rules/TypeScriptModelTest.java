package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The model as a browser sees it, and what a third language proved.
 *
 * Java and Kotlin share a runtime, so the seam was never asked whether it was
 * language-neutral or merely JVM-shaped. TypeScript asks: it has no
 * annotations, no class literals, no functional interfaces, and its imports
 * are module paths rather than qualified names.
 */
public class TypeScriptModelTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String line) {
            return super.parseFieldLine(name, line, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    private EntitySpec article() {
        return new EntitySpec("Article")
                .domain("content", "Content")
                .pkg("dto", "io.acme.content.dto")
                .addField(loader.parse("articleId", "string(36) @id"))
                .addField(loader.parse("title", "string(300)!"))
                .addField(loader.parse("viewCount", "long"))
                .addField(loader.parse("featured", "boolean"));
    }

    private String render(EntitySpec e, String target) {
        return RuleCatalog.standard()
                .engineFor("ts-model")
                .templateSet(SourceCode.TemplateSet.TYPESCRIPT)
                .run(e, target)
                .render();
    }

    // ---- what the language says ----------------------------------------

    /** A DTO is an interface: nothing is constructed on that side. */
    @Test
    public void dtoIsAnInterface() {
        assertTrue(render(article(), "dto").contains("export interface ArticleDTO {"),
                render(article(), "dto"));
    }

    /**
     * Required becomes a property that is always there; everything else is
     * optional, which is how TypeScript says a value may be absent.
     */
    @Test
    public void requiredDecidesOptionality() {
        String out = render(article(), "dto");

        assertTrue(out.contains("title: string;"), out);
        assertTrue(out.contains("viewCount?: number;"), out);
        assertFalse(out.contains("title?:"), out);
    }

    /** The model's Java types become the primitives a browser has. */
    @Test
    public void typesBecomeTypeScriptPrimitives() {
        String out = render(article(), "dto");

        assertTrue(out.contains("articleId?: string;"), out);
        assertTrue(out.contains("viewCount?: number;"), out);
        assertTrue(out.contains("featured?: boolean;"), out);
    }

    /**
     * The shared rules annotate as they decide, and this language has no
     * annotations - so the decisions arrive and the annotations do not.
     */
    @Test
    public void theAnnotationsTheSharedRulesAddedAreNotEmitted() {
        String out = render(article(), "dto");

        assertFalse(out.contains("@Schema"), out);
        assertFalse(out.contains("@NotNull"), out);
        assertFalse(out.contains("jakarta"), out);
    }

    // ---- what the seam turned out to be --------------------------------

    /**
     * A member that only means something on the JVM fails by name rather than
     * answering. A rule reaching one has assumed a runtime this language does
     * not have, and a plausible answer would hide that until the output was
     * read by a compiler.
     */
    @Test
    public void jvmOnlyMembersRefuseRatherThanInvent() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> Syntax.TYPESCRIPT.classLiteral("Views.Read"));

        assertTrue(ex.getMessage().contains("no meaning outside the JVM"), ex.getMessage());
        assertThrows(IllegalStateException.class, () -> Syntax.TYPESCRIPT.sam("T", "a", "b"));
        assertThrows(IllegalStateException.class, () -> Syntax.TYPESCRIPT.injectedProperty("T", "x"));
        assertThrows(IllegalStateException.class, () -> Syntax.TYPESCRIPT.overriding());
    }

    /**
     * The seam's one real break. importLine was written where a qualified
     * name IS the import; here it is a module path and a named binding, and
     * the qualified name has nothing to say about the path.
     */
    @Test
    public void anImportIsAModulePathAndABinding() {
        assertEquals("import { CategoryDTO } from './CategoryDTO';",
                Syntax.TYPESCRIPT.importLine("io.acme.content.dto.CategoryDTO"));
    }

    /** A collection is an array, not a generic of a collection type. */
    @Test
    public void collectionsBecomeArrays() {
        assertEquals("string[]", Syntax.TYPESCRIPT.typeName("java.util.List<String>"));
        assertEquals("number[]", Syntax.TYPESCRIPT.typeName("Integer[]"));
    }

    // ---- the search DTO, which is where sharing paid off ---------------

    /**
     * The From/To pair a between condition becomes is decided by the shared
     * rule, and arrives here unchanged. That decision is a fact about the
     * model, not about the JVM - which is the part worth sharing.
     */
    @Test
    public void aRangeConditionStillBecomesAPair() {
        EntitySpec e = new EntitySpec("Article")
                .domain("content", "Content")
                .meta("searchable", "entity=Article")
                .pkg("dto", "io.acme.content.dto")
                .addField(loader.parse("publishDate", "datetime @search(between)"));

        String out = render(e, "srchdto");

        assertTrue(out.contains("publishDateFrom?: string;"), out);
        assertTrue(out.contains("publishDateTo?: string;"), out);
        assertFalse(out.contains("DateTimeFormat"), out);
    }

    /**
     * An emptiness check is a yes or no on every side: the shared rule makes
     * it a Boolean, and the browser sends true, false or nothing.
     */
    @Test
    public void anEmptinessCheckBecomesAnOptionalBoolean() {
        EntitySpec e = new EntitySpec("Article")
                .domain("content", "Content")
                .meta("searchable", "entity=Article")
                .pkg("dto", "io.acme.content.dto")
                .addField(loader.parse("publishDate", "datetime @search(isnull)"));

        String out = render(e, "srchdto");

        assertTrue(out.contains("publishDateEmpty?: boolean;"), out);
        assertFalse(out.contains("publishDate?:"), out);
        assertFalse(out.contains("publishDateFrom"), out);
    }

    // ---- the client for what the controller exposes ---------------------

    private EntitySpec exposed() {
        return new EntitySpec("Article")
                .domain("content", "Content")
                .meta("expose", "")
                .apiPath("/api/v1")
                .pkg("dto", "io.acme.content.dto")
                .addField(loader.parse("articleId", "string(36) @id"))
                .addField(loader.parse("title", "string(300)!"));
    }

    /**
     * The path the client calls is the path the controller answers on. Both
     * are built from apiPath, the domain and the entity, so they cannot drift.
     */
    @Test
    public void theClientCallsTheControllersOwnPath() {
        assertTrue(render(exposed(), "api").contains("const BASE = '/api/v1/content/article';"),
                render(exposed(), "api"));
    }

    /** One function per endpoint, typed by the DTOs the server declares. */
    @Test
    public void everyEndpointBecomesATypedFunction() {
        String out = render(exposed(), "api");

        assertTrue(
                out.contains("export async function create(input: ArticleDTO, init?: RequestInit)"
                        + ": Promise<ArticleDTO> {"),
                out);
        assertTrue(out.contains("return request<ArticleDTO>('POST', BASE, input, init);"), out);
        assertTrue(out.contains("export async function update(input: ArticleDTO, init?: RequestInit)"), out);
        assertTrue(out.contains("export async function batchUpdate(inputs: ArticleDTO[], init?: RequestInit)"), out);
        assertTrue(out.contains("export async function deleteMany(keys: ArticleKeyDTO[], init?: RequestInit)"), out);
    }

    /** Signing in, out and asking who: each takes one call's RequestInit last and passes it on. */
    @Test
    public void theSignInFunctionsTakeACallsInit() {
        EntitySpec member = new EntitySpec("Member")
                .domain("content", "Content")
                .meta("expose", "")
                .meta("signin", "")
                .meta("access", "update=admin")
                .knownSignInEntities(1)
                .apiPath("/api/v1")
                .pkg("dto", "io.acme.content.dto")
                .addField(loader.parse("memberId", "long @id @sequence"))
                .addField(loader.parse("email", "string(100) @login"))
                .addField(loader.parse("secret", "string(100) @password"));

        String out = render(member, "api");

        assertTrue(
                out.contains("export async function signIn(email: string, secret: string, init?: RequestInit)"), out);
        assertTrue(out.contains("return request<MemberDTO>('POST', BASE + '/signin', { email, secret }, init);"), out);
        assertTrue(
                out.contains("export async function signOut(init?: RequestInit): Promise<void> {\n"
                        + "    return request<void>('POST', BASE + '/signout', undefined, init);"),
                out);
        assertTrue(out.contains("export async function me(init?: RequestInit): Promise<MemberDTO | null> {"), out);
        assertTrue(out.contains("return await request<MemberDTO>('GET', BASE + '/me', undefined, init);"), out);
    }

    /**
     * A lookup that finds nothing is a null, not a thrown error: the endpoint
     * answers 404 by having nothing to return, which is a value the caller
     * can check.
     */
    @Test
    public void findByIdAnswersNullRatherThanThrowing() {
        String out = render(exposed(), "api");

        assertTrue(out.contains("findById(articleId: string, init?: RequestInit): Promise<ArticleDTO | null>"), out);
        assertTrue(out.contains("requestOrNull<ArticleDTO>('GET', BASE + '/' + seg(articleId), init)"), out);
        assertTrue(out.contains("request<boolean>('DELETE', BASE + '/' + seg(articleId), undefined, init)"), out);
    }

    /**
     * The identifier is put in the path through an encoder, since it is a
     * value the caller supplied and a path segment cannot hold anything.
     */
    @Test
    public void identifiersAreEncodedIntoThePath() {
        assertTrue(render(exposed(), "api").contains("seg(articleId)"), render(exposed(), "api"));
    }

    /** An entity the DSL did not expose has no endpoints, so no client. */
    @Test
    public void anUnexposedEntityGetsNoClient() {
        EntitySpec e = new EntitySpec("Internal")
                .domain("content", "Content")
                .pkg("dto", "io.acme.content.dto");

        SourceCode code = RuleCatalog.standard()
                .engineFor("ts-model")
                .templateSet(SourceCode.TemplateSet.TYPESCRIPT)
                .run(e, "api");

        assertFalse(code.isMake());
    }

    // ---- the domain rules a browser can hold ----------------------------

    /** A root whose promises and named rules the DSL states. */
    private EntitySpec order() {
        return new EntitySpec("Order")
                .domain("shop", "Shop")
                .pkg("dto", "io.acme.shop.dto")
                .meta("root", "")
                .meta("invariant", "items.size > 0, 'an order has at least one line'")
                .meta("specification", "highValue, totalAmount > 100")
                .addField(loader.parse("orderId", "string(36) @id"))
                .addField(loader.parse("totalAmount", "bigdecimal(14.2)"))
                .addField(loader.parse("items", "List<OrderItem> @OneToMany(mappedBy=\"order\")"));
    }

    /**
     * A decimal is compared as a number, because it arrived as a string.
     *
     * This is the assertion the whole TypeScript side of the grammar exists
     * for. JSON's only number is a double, so a server that sent 14.49 as one
     * would be sending a different value than it holds - it sends "14.49".
     * The obvious hand-written condition, order.totalAmount > 100, compares a
     * string with a number: TypeScript accepts it, JavaScript answers false
     * whatever the amount, and nothing anywhere reports a problem.
     */
    @Test
    public void aDecimalIsReadAsANumber() {
        String out = render(order(), "specifications");

        assertTrue(out.contains("Number(order.totalAmount) > 100"), out);
        assertFalse(out.contains("order.totalAmount > 100"),
                "comparing the string the server sent against a number is the bug this replaces");
    }

    /** A collection is counted the way this language counts one. */
    @Test
    public void aCollectionIsCountedWithLength() {
        String out = render(order(), "invariants");

        assertTrue(out.contains("order.items.length <= 0"), out);
        assertFalse(out.contains(".size()"), "that is the JVM's spelling: " + out);
    }

    /** Nothing is dereferenced without asking whether it is there. */
    @Test
    public void everyReadIsGuarded() {
        String specifications = render(order(), "specifications");

        assertTrue(specifications.contains("order != null"), specifications);
        assertTrue(specifications.contains("order.totalAmount != null"), specifications);
    }

    /** A specification is a plain function, since a function is already a value. */
    @Test
    public void specificationsAreFunctions() {
        String out = render(order(), "specifications");

        assertTrue(out.contains("export function highValue(order: OrderDTO | null | undefined): boolean"), out);
        assertTrue(out.contains("import { OrderDTO } from './OrderDTO';"), out);
        assertFalse(out.contains("Predicate"), "the JVM's workaround has nothing to translate to: " + out);
    }

    /**
     * An invariant reports rather than throws, and says whose job it is.
     *
     * The server refuses to write an aggregate that breaks its promise, and
     * that is what makes the promise true. A browser is not being asked to be
     * trustworthy - it is being told early, so a form can say what is wrong
     * before the request that would have failed.
     */
    @Test
    public void invariantsReportRatherThanEnforce() {
        String out = render(order(), "invariants");

        assertTrue(out.contains("export function violations(order: OrderDTO | null | undefined): string[]"), out);
        assertTrue(out.contains("broken.push('an order has at least one line');"), out);
        assertTrue(out.contains("The server enforces these when the row is written"),
                "a check the reader could mistake for enforcement has to say it is not: " + out);
        assertFalse(out.contains("throw "), out);
    }
}
