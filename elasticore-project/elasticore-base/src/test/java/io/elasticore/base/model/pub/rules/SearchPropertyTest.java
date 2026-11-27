package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.model.pub.rules.base.SearchDtoBaseRules;
import io.elasticore.base.model.pub.rules.base.SearchDtoBaseRules.SearchProperty;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The search DTO's properties as one list (issue 373): what each property is
 * called, which field it searches, by which condition, and which end of a
 * range it is - the decisions the search DTO is rendered from.
 */
public class SearchPropertyTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    private static EntitySpec srch(String name) {
        return new EntitySpec(name).meta("searchable", "entity=" + name + ", pageSize=20");
    }

    /** The clean (3.0) output's properties, read after the search DTO's own rules ran. */
    private static List<SearchProperty> clean(EntitySpec e) {
        new RuleEngine()
                .templateSet(SourceCode.TemplateSet.V3)
                .register(new SearchDtoBaseRules())
                .run(e, "srchdto");
        return SearchDtoBaseRules.properties(e);
    }

    /** The 2.x-compatible output's. */
    private static List<SearchProperty> compatible(EntitySpec e) {
        new RuleEngine().register(new SearchDtoBaseRules()).run(e, "srchdto");
        return SearchDtoBaseRules.properties(e);
    }

    private static void assertProperty(
            SearchProperty p,
            String name,
            String field,
            String op,
            SearchProperty.Kind kind,
            String end,
            String alias) {
        assertEquals(name, p.name(), "name");
        assertEquals(field, p.field().name(), "field of " + name);
        assertEquals(op, p.op(), "op of " + name);
        assertEquals(kind, p.kind(), "kind of " + name);
        assertEquals(end, p.end(), "end of " + name);
        assertEquals(alias, p.alias(), "alias of " + name);
    }

    @Test
    public void eachConditionIsOneProperty() {
        EntitySpec e = srch("Product")
                .addField(loader.parse("sku", "string(50) @search(eq)"))
                .addField(loader.parse("stock", "int @search(>=)"))
                .addField(loader.parse("weight", "int @search(gte)"));
        List<SearchProperty> props = clean(e);
        assertEquals(3, props.size(), props.toString());
        assertProperty(props.get(0), "sku", "sku", "eq", SearchProperty.Kind.VALUE, null, null);
        assertProperty(props.get(1), "stock", "stock", ">=", SearchProperty.Kind.VALUE, null, null);
        // The keyword as its symbol, as the clean output reads it.
        assertProperty(props.get(2), "weight", "weight", ">=", SearchProperty.Kind.VALUE, null, null);
        assertSame(e.fields().get(0), props.get(0).field());
        assertNull(props.get(0).words());
    }

    @Test
    public void aLikeOnTextIgnoresCaseInTheCleanOutputOnly() {
        assertEquals(
                "%__%",
                clean(srch("P").addField(loader.parse("name", "string @search(like)")))
                        .get(0)
                        .op());
        assertEquals(
                "like",
                compatible(srch("P").addField(loader.parse("name", "string @search(like)")))
                        .get(0)
                        .op());
        assertEquals(
                "eq",
                compatible(srch("P").addField(loader.parse("n", "int @search(eq)")))
                        .get(0)
                        .op());
    }

    @Test
    public void aRangeIsTwoProperties() {
        List<SearchProperty> props =
                clean(srch("P").addField(loader.parse("price", "bigdecimal(10.2) @search(between)")));
        assertEquals(2, props.size(), props.toString());
        assertProperty(props.get(0), "priceFrom", "price", "between", SearchProperty.Kind.RANGE, "from", null);
        assertProperty(props.get(1), "priceTo", "price", "between", SearchProperty.Kind.RANGE, "to", null);

        List<SearchProperty> tilde = clean(srch("P").addField(loader.parse("at", "date @search(~,day)")));
        assertProperty(tilde.get(0), "dayFrom", "at", "~", SearchProperty.Kind.RANGE, "from", "day");
        assertProperty(tilde.get(1), "dayTo", "at", "~", SearchProperty.Kind.RANGE, "to", "day");
    }

    @Test
    public void anAliasNamesTheProperty() {
        List<SearchProperty> props =
                clean(srch("Order").addField(loader.parse("customerFirstName", "string @search(eq,customerName)")));
        assertProperty(
                props.get(0),
                "customerName",
                "customerFirstName",
                "eq",
                SearchProperty.Kind.VALUE,
                null,
                "customerName");
    }

    @Test
    public void anEqualityOnAListSearchesOneItem() {
        List<SearchProperty> props = clean(srch("Order").addField(loader.parse("tags", "List<String> @search(eq)")));
        assertProperty(props.get(0), "tagsItem", "tags", "eq", SearchProperty.Kind.ITEM, null, null);
        List<SearchProperty> in = clean(srch("Order").addField(loader.parse("tags", "List<String> @search(in)")));
        assertProperty(in.get(0), "tags", "tags", "in", SearchProperty.Kind.VALUE, null, null);
    }

    @Test
    public void anEmptinessCheckIsAYesOrNoInTheCleanOutput() {
        List<SearchProperty> props = clean(srch("Loan").addField(loader.parse("returnedDate", "date @search(isnull)")));
        assertProperty(
                props.get(0), "returnedDateEmpty", "returnedDate", "isnull", SearchProperty.Kind.EMPTY, null, null);
        List<SearchProperty> alias =
                clean(srch("Loan").addField(loader.parse("returnedDate", "date @search(isnull,open)")));
        assertProperty(alias.get(0), "openEmpty", "returnedDate", "isnull", SearchProperty.Kind.EMPTY, null, "open");
        // 2.x reads it as an equality on the field's own type.
        List<SearchProperty> old =
                compatible(srch("Loan").addField(loader.parse("returnedDate", "date @search(isnull)")));
        assertProperty(old.get(0), "returnedDate", "returnedDate", "isnull", SearchProperty.Kind.VALUE, null, null);
    }

    @Test
    public void wordsAreOnePropertyOverTheirFields() {
        EntitySpec e =
                srch("Post").addField(loader.parse("q", "string(100) @search(words: title, body, author.nickname)"));
        List<SearchProperty> props = clean(e);
        assertEquals(1, props.size());
        assertProperty(props.get(0), "q", "q", "words", SearchProperty.Kind.WORDS, null, null);
        assertEquals(
                Arrays.asList("title", "body", "author.nickname"), props.get(0).words());
        // Not searched in the 2.x-compatible output, so not a property there.
        assertTrue(compatible(srch("Post").addField(loader.parse("q", "string(100) @search(words: title)")))
                .isEmpty());
    }

    @Test
    public void anExposedDtoDerivesItsConditions() {
        EntitySpec e = srch("Product")
                .meta("expose", "20")
                .addField(loader.parse("uuid", "string(36)"))
                .addField(loader.parse("restocked", "datetime"))
                .addField(loader.parse("weight", "float"))
                .addField(loader.parse("secret", "string(30) @password"));
        List<SearchProperty> props = clean(e);
        assertEquals(4, props.size(), props.toString());
        assertProperty(props.get(0), "uuid", "uuid", "auto", SearchProperty.Kind.VALUE, null, null);
        assertProperty(props.get(1), "restockedFrom", "restocked", "between", SearchProperty.Kind.RANGE, "from", null);
        assertProperty(props.get(2), "restockedTo", "restocked", "between", SearchProperty.Kind.RANGE, "to", null);
        assertProperty(props.get(3), "weight", "weight", "eq", SearchProperty.Kind.VALUE, null, null);
        // The derived condition is handed on through the spec, never written into the shared field
        // (issue 376), and reading the list again says the same.
        assertEquals("between", e.fields().get(1).derivedSearch());
        assertNull(e.fields().get(1).searchValue());
        // A password is searched by nothing derived.
        assertNull(e.fields().get(3).derivedSearch());
        assertEquals(props, SearchDtoBaseRules.properties(e));
    }

    /**
     * A search DTO that is not @expose - a @service one - searches the inherited fields an @expose
     * sibling has (SpecFactory marks them searchedAsInherited, issue 376) by the condition their type
     * gives them, and nothing else it was not told to.
     */
    @Test
    public void aServiceDtoSearchesTheInheritedFieldsItIsGiven() {
        EntitySpec e = srch("Order")
                .meta("service", null)
                .addField(loader.parse("createdAt", "datetime"))
                .addField(loader.parse("createdBy", "string(50)"))
                .addField(loader.parse("secret", "string(30) @password"))
                .addField(loader.parse("note", "string(30)"));
        e.fields().get(0).searchedAsInherited(true);
        e.fields().get(1).searchedAsInherited(true);
        e.fields().get(2).searchedAsInherited(true);
        List<SearchProperty> props = clean(e);
        // Read again, the list is the same: deciding writes nothing.
        List<SearchProperty> again = SearchDtoBaseRules.properties(e);
        assertEquals(3, props.size(), props.toString());
        assertProperty(props.get(0), "createdAtFrom", "createdAt", "between", SearchProperty.Kind.RANGE, "from", null);
        assertProperty(props.get(1), "createdAtTo", "createdAt", "between", SearchProperty.Kind.RANGE, "to", null);
        assertProperty(props.get(2), "createdBy", "createdBy", "auto", SearchProperty.Kind.VALUE, null, null);
        assertEquals(props, again);
        assertEquals("between", e.fields().get(0).derivedSearch());
        assertEquals("auto", e.fields().get(1).derivedSearch());
        assertNull(e.fields().get(0).searchValue());

        // Unmarked, the same fields are no search of a @service DTO.
        EntitySpec unmarked = srch("Order")
                .meta("service", null)
                .addField(loader.parse("createdAt", "datetime"))
                .addField(loader.parse("createdBy", "string(50)"));
        assertTrue(clean(unmarked).isEmpty());
        assertNull(unmarked.fields().get(0).derivedSearch());
    }

    @Test
    public void aDeclaredFieldWithoutAConditionIsAPlainProperty() {
        Field declared = loader.parse("note", "string(30)");
        EntitySpec e = srch("Product").addField(declared);
        e.fields().get(0).declaredInDto(true);
        e.addField(loader.parse("carried", "string(30)"));
        List<SearchProperty> props = clean(e);
        assertEquals(1, props.size(), props.toString());
        assertProperty(props.get(0), "note", "note", null, SearchProperty.Kind.PLAIN, null, null);
    }

    @Test
    public void whatIsNoSearchIsNoProperty() {
        EntitySpec e = srch("Order")
                .knownEntity("Customer")
                .addField(loader.parse("customer", "Customer @ManyToOne @deferred"))
                .addField(loader.parse("lines", "List<OrderLine> @OneToMany @search(eq)"))
                .addField(loader.parse("customerId", "long @idof(customer)"))
                .addField(loader.parse("customerCustomerId", "long @ref(customer.customerId) @search(eq)"));
        List<SearchProperty> props = clean(e);
        assertTrue(props.isEmpty(), props.toString());
    }

    @Test
    public void aPropertySaysWhatItIs() {
        SearchProperty p = clean(srch("P").addField(loader.parse("price", "int @search(between,cost)")))
                .get(0);
        String text = p.toString();
        assertTrue(text.contains("costFrom"), text);
        assertTrue(text.contains("price"), text);
        assertTrue(text.contains("between"), text);
        assertTrue(text.contains("from"), text);
        SearchProperty same = clean(srch("P").addField(loader.parse("price", "int @search(between,cost)")))
                .get(0);
        assertEquals(p.name(), same.name());
    }
}
