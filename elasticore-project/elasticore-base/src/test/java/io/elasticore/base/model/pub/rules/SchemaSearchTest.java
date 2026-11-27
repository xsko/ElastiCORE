package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.model.pub.rules.base.SearchDtoBaseRules;
import io.elasticore.base.model.pub.rules.base.SearchDtoBaseRules.SearchProperty;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The search part of GET /schema/info from the model (issue 373): each search DTO property with the entity field it
 * searches and its operator in the DSL's words - as JPA's mapper applies the search DTO's decision, and as a MyBatis
 * or MongoDB query applies its conditions - with the page size and what a sort may name.
 */
public class SchemaSearchTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    /** The search DTO's properties as the clean (3.0) output decides them. */
    private List<SearchProperty> properties(String... fieldLines) {
        EntitySpec e = new EntitySpec("Order").meta("searchable", "entity=Order, pageSize=20");
        for (int i = 0; i < fieldLines.length; i += 2) e.addField(loader.parse(fieldLines[i], fieldLines[i + 1]));
        // Declared by the search DTO itself: one without a condition stays, as a plain property.
        for (FieldSpec f : e.fields()) f.declaredInDto(true);
        new RuleEngine()
                .templateSet(SourceCode.TemplateSet.V3)
                .register(new SearchDtoBaseRules())
                .run(e, "srchdto");
        return SearchDtoBaseRules.properties(e);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fields(Map<String, Object> search) {
        return (Map<String, Object>) search.get("fields");
    }

    private static Map<String, Object> entry(Object field, String op, String... more) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("field", field);
        out.put("op", op);
        for (String m : more) {
            if (m.equals("in")) out.put("in", Boolean.TRUE);
            else out.put("end", m);
        }
        return out;
    }

    private Map<String, Object> jpa(String... fieldLines) {
        return fields(SchemaSearch.of(new SchemaSearch.Dto(properties(fieldLines), 20, null), true, null));
    }

    @Test
    public void jpaSaysEachConditionInTheDslsWords() {
        Map<String, Object> f = jpa(
                "sku", "string(20) @search(eq)",
                "code", "string(20) @search(=)",
                "other", "string(20) @search(!=)",
                "more", "int @search(gt)",
                "least", "int @search(>=)",
                "less", "int @search(<)",
                "most", "int @search(lte)",
                "name", "string(30) @search(like)",
                "exact", "string(30) @search(%_%)",
                "same", "string(30) @search(__=)",
                "head", "string(30) @search(_%)",
                "headIc", "string(30) @search(__%)",
                "tail", "string(30) @search(%_)",
                "tailIc", "string(30) @search(%__)",
                "lone", "string(30) @search(%)",
                "label", "string(30) @search",
                "odd", "string(30) @search(ilike)");
        assertEquals(entry("sku", "eq"), f.get("sku"));
        assertEquals(entry("code", "eq"), f.get("code"));
        assertEquals(entry("other", "ne"), f.get("other"));
        assertEquals(entry("more", "gt"), f.get("more"));
        assertEquals(entry("least", "gte"), f.get("least"));
        assertEquals(entry("less", "lt"), f.get("less"));
        assertEquals(entry("most", "lte"), f.get("most"));
        // The 3.0 like on text: contains, ignoring case.
        assertEquals(entry("name", "like"), f.get("name"));
        assertEquals(entry("exact", "%_%"), f.get("exact"));
        assertEquals(entry("same", "__="), f.get("same"));
        assertEquals(entry("head", "_%"), f.get("head"));
        assertEquals(entry("headIc", "__%"), f.get("headIc"));
        assertEquals(entry("tail", "%_"), f.get("tail"));
        assertEquals(entry("tailIc", "%__"), f.get("tailIc"));
        // A lone % is the end of the value (MapperSpecRules' warning says so).
        assertEquals(entry("lone", "%_"), f.get("lone"));
        assertEquals(entry("label", "auto"), f.get("label"));
        // Not a condition: an equality (the build says so).
        assertEquals(entry("odd", "eq"), f.get("odd"));
        assertEquals(17, f.size(), f.toString());
    }

    @Test
    public void eachPropertyAsTheSearchDtoNamesIt() {
        Map<String, Object> f = jpa(
                "orderDate", "date @search(between)",
                "customerFirstName", "string @search(like,customerName)",
                "shippedAt", "datetime @search(isnull)",
                "statuses", "List<string> @search(in)",
                "status", "string(10) @search(in)",
                "tags", "List<string> @search(eq)",
                "itemName", "string @search(like) @ref(item.name)",
                "keyword", "string(50) @search(words:name, item.name)",
                "note", "string(20)");
        assertEquals(entry("orderDate", "between", "from"), f.get("orderDateFrom"));
        assertEquals(entry("orderDate", "between", "to"), f.get("orderDateTo"));
        // The name the condition gives is the property and, for JPA's mapper, the entity field.
        assertEquals(entry("customerName", "like"), f.get("customerName"));
        assertEquals(entry("shippedAt", "isnull"), f.get("shippedAtEmpty"));
        assertEquals(entry("statuses", "in", "in"), f.get("statuses"));
        // in on one value is an equality.
        assertEquals(entry("status", "eq"), f.get("status"));
        // An equality on a list: one element of it.
        assertEquals(entry("tags", "eq"), f.get("tagsItem"));
        assertEquals(entry("item.name", "like"), f.get("itemName"));
        assertEquals(entry(Arrays.asList("name", "item.name"), "words"), f.get("keyword"));
        // Declared with no condition: searches by nothing.
        assertEquals(entry("note", null), f.get("note"));
        assertEquals(
                Arrays.asList(
                        "orderDateFrom",
                        "orderDateTo",
                        "customerName",
                        "shippedAtEmpty",
                        "statuses",
                        "status",
                        "tagsItem",
                        "itemName",
                        "keyword",
                        "note"),
                new java.util.ArrayList<String>(f.keySet()));
    }

    @Test
    public void aQuerySaysWhatItApplies() {
        List<SearchProperty> props = properties(
                "orderDate", "date @search(between)",
                "customerFirstName", "string @search(like,customerName)",
                "shippedAt", "datetime @search(isnull)",
                "statuses", "List<string> @search(in)",
                "tags", "List<string> @search(eq)",
                "keyword", "string(50) @search(words:name)",
                "phone", "string(20) @search(eq)",
                "count", "int @search(gte)",
                "title", "string(20) @search(like)",
                "mobile", "string(20) @search(eq,cell)",
                "nick", "string(20) @search(like,nickname)",
                "itemName", "string @search(like) @ref(item.name)");
        List<String[]> conditions = Arrays.asList(
                new String[] {"between", "orderDate", "orderDate", "LocalDate"},
                new String[] {"ilike", "customerFirstName", "customerName", "String"},
                new String[] {"isnull", "shippedAt", "shippedAt", "LocalDateTime"},
                new String[] {"in", "statuses", "statuses", "String"},
                new String[] {"bidx", "phoneBidx", "phone", "String"},
                new String[] {"ge", "count", "count", "Integer"},
                // MongoDB: a text match on a value stored converted is an equality.
                new String[] {"eq", "title", "title", "String"},
                new String[] {"bidx", "mobileBidx", "cell", "String"});
        Map<String, Object> f = fields(SchemaSearch.of(
                new SchemaSearch.Dto(props, 20, null),
                true,
                new SchemaSearch.Query(conditions, Collections.<String>emptyList())));
        assertEquals(entry("orderDate", "between", "from"), f.get("orderDateFrom"));
        assertEquals(entry("orderDate", "between", "to"), f.get("orderDateTo"));
        // MyBatis compares the field the property is declared on.
        assertEquals(entry("customerFirstName", "like"), f.get("customerName"));
        assertEquals(entry("shippedAt", "isnull"), f.get("shippedAtEmpty"));
        assertEquals(entry("statuses", "in", "in"), f.get("statuses"));
        // No condition: one element of a list and the words search by nothing there.
        assertEquals(entry("tags", null), f.get("tagsItem"));
        assertEquals(entry(Arrays.asList("name"), null), f.get("keyword"));
        // A blind index: an equality on the field, not on the index.
        assertEquals(entry("phone", "eq"), f.get("phone"));
        assertEquals(entry("count", "gte"), f.get("count"));
        assertEquals(entry("title", "eq"), f.get("title"));
        // The blind index of the field the property is declared on, not of the name its condition gives.
        assertEquals(entry("mobile", "eq"), f.get("cell"));
        // Without a condition there: the field the query would compare - its own, or its @ref's path.
        assertEquals(entry("nick", null), f.get("nickname"));
        assertEquals(entry("item.name", null), f.get("itemName"));
    }

    @Test
    public void everyOperatorOfAQueryInTheDslsWords() {
        String[][] words = {
            {"eq", "eq"},
            {"ne", "ne"},
            {"gt", "gt"},
            {"ge", "gte"},
            {"lt", "lt"},
            {"le", "lte"},
            {"like", "%_%"},
            {"ilike", "like"},
            {"ieq", "__="},
            {"prefix", "_%"},
            {"iprefix", "__%"},
            {"suffix", "%_"},
            {"isuffix", "%__"},
            {"bidx", "eq"},
            {"bidxdigits", "eq"},
            {"auto", "auto"},
            {"between", "between"},
            {"isnull", "isnull"}
        };
        SearchProperty one = properties("n", "string(9) @search(eq)").get(0);
        for (String[] w : words) assertEquals(w[1], SchemaSearch.word(w[0], one), w[0]);
        SearchProperty list = properties("ns", "List<string> @search(in)").get(0);
        assertEquals("in", SchemaSearch.word("in", list));
        assertEquals("eq", SchemaSearch.word("in", one));
    }

    @Test
    public void thePageAndTheSort() {
        List<SearchProperty> props = properties("sku", "string(20) @search(eq)");
        Map<String, Object> paged =
                SchemaSearch.of(new SchemaSearch.Dto(props, 50, Arrays.asList("sku", "name")), true, null);
        assertEquals(50, paged.get("pageSize"));
        assertEquals(Arrays.asList("sku", "name"), paged.get("sortable"));
        assertEquals(Arrays.asList("pageSize", "sortable", "fields"), new java.util.ArrayList<String>(paged.keySet()));
        // A search that answers a list has no page size.
        assertNull(SchemaSearch.of(new SchemaSearch.Dto(props, 50, null), false, null)
                .get("pageSize"));
        // Nothing checks the sort: null.
        assertNull(SchemaSearch.of(new SchemaSearch.Dto(props, 50, null), true, null)
                .get("sortable"));

        // A query sorts only by what it has a column for.
        assertEquals(
                Collections.singletonList("sku"),
                SchemaSearch.sortable(Arrays.asList("sku", "name"), Arrays.asList("sku", "x")));
        assertEquals(Arrays.asList("sku", "x"), SchemaSearch.sortable(null, Arrays.asList("sku", "x")));
        assertNull(SchemaSearch.sortable(null, null));
        assertEquals(Arrays.asList("a"), SchemaSearch.sortable(Arrays.asList("a"), null));
    }
}
