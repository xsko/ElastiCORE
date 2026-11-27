package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What GET /schema/info answers from with config: schemaInfo: model (issue 373): the controller's line, the
 * warnings, and the domain's file - the entity, and each field's facts as FieldFacts states them.
 */
public class SchemaCatalogTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    // ---- the setting, the line, the warnings ---------------------------------------

    @Test
    public void onlyModelAsksForTheModelsAnswer() {
        assertTrue(SchemaCatalog.asked("model"));
        assertTrue(SchemaCatalog.asked(" model "));
        assertFalse(SchemaCatalog.asked("Model"));
        assertFalse(SchemaCatalog.asked(""));
        assertFalse(SchemaCatalog.asked(null));
        assertNull(SchemaCatalog.settingProblem("shop", null));
        assertNull(SchemaCatalog.settingProblem("shop", "  "));
        assertNull(SchemaCatalog.settingProblem("shop", "model"));
        assertEquals(
                "[shop] env.yml config.schemaInfo 'all' is not a value it takes - model (GET /schema/info answers from"
                        + " the model); it is ignored, and the endpoint answers as before",
                SchemaCatalog.settingProblem("shop", " all "));
        assertEquals(
                "[shop] config: schemaInfo: model is written for the 3.0 code - this domain generates the 2.x code"
                        + " (output = 'v2'), whose GET /schema/info answers as before (DTOUtils.getMetaInfo)",
                SchemaCatalog.compatibleProblem("shop"));
        assertEquals("elasticore/shop-schema.json", SchemaCatalog.resource("shop"));
    }

    @Test
    public void theLineIsTheRuntimesAnswerOrTheOneItAlwaysWas() {
        assertEquals(
                "io.elasticore.springboot3.schema.SchemaInfo.answer(\"shop\", \"Order\")",
                SchemaCatalog.answer(true, false, "shop", "Order", "OrderDTO"));
        assertEquals(
                "io.elasticore.springboot3.schema.SchemaInfo.answer(\"shop\", \"Order\")",
                SchemaCatalog.answer(true, true, "shop", "Order", "OrderDTO"));
        assertEquals(
                "ResponseEntity.ok(DTOUtils.getMetaInfo(OrderDTO.class))",
                SchemaCatalog.answer(false, false, "shop", "Order", "OrderDTO"));
        assertEquals(
                "ResponseEntity.ok(DTOUtils.getMetaInfo(OrderDTO::class.java))",
                SchemaCatalog.answer(false, true, "shop", "Order", "OrderDTO"));
    }

    @Test
    public void aSkeletonWithoutTheSlotIsSaid() {
        assertNull(SchemaCatalog.skeletonProblem("shop", "Order", null));
        assertNull(SchemaCatalog.skeletonProblem("shop", "Order", "        return ${schemaInfoAnswer};"));
        assertEquals(
                "[shop] Order: config: schemaInfo: model, but the controller skeleton (control.tmpl) has no"
                        + " ${schemaInfoAnswer}, so GET /schema/info answers as before - write 'return"
                        + " ${schemaInfoAnswer}' in its getMetaInfo()",
                SchemaCatalog.skeletonProblem(
                        "shop", "Order", "        return ResponseEntity.ok(DTOUtils.getMetaInfo(OrderDTO.class));"));
    }

    @Test
    public void aKeptControllerWithoutTheCallIsSaid() {
        String would = "    @GetMapping(\"/schema/info\")\n"
                + "        return io.elasticore.springboot3.schema.SchemaInfo.answer(\"shop\", \"Order\");\n";
        String old = "    @GetMapping(\"/schema/info\")\n"
                + "        return ResponseEntity.ok(DTOUtils.getMetaInfo(OrderDTO.class));\n";
        assertEquals(
                "[elcore3] src/OrderController.java is yours, and its GET /schema/info does not call"
                        + " SchemaInfo.answer - it answers as before, not from the model (config: schemaInfo: model)."
                        + " Return io.elasticore.springboot3.schema.SchemaInfo.answer(\"shop\", \"Order\") there, or"
                        + " delete the file to have it generated anew.",
                SchemaCatalog.keptControllerProblem("src/OrderController.java", would, old));
        // The generator writes no call: the setting is off, or the endpoint is left out.
        assertNull(SchemaCatalog.keptControllerProblem("x", old, old));
        // The file calls it - through a renamed runtime package too.
        assertNull(SchemaCatalog.keptControllerProblem("x", would, would));
        assertNull(
                SchemaCatalog.keptControllerProblem("x", would, would.replace("io.elasticore.springboot3", "acme.rt")));
        // The developer took the endpoint out.
        assertNull(SchemaCatalog.keptControllerProblem("x", would, "class OrderController {}"));
        assertNull(SchemaCatalog.keptControllerProblem("x", would, null));
        assertNull(SchemaCatalog.keptControllerProblem("x", null, old));
        assertNull(
                SchemaCatalog.keptControllerProblem("x", "io.elasticore.springboot3.schema.SchemaInfo.answer(", old));
    }

    // ---- the file --------------------------------------------------------------------

    private EntitySpec spec(String name, String... fieldLines) {
        EntitySpec e = new EntitySpec(name).domain("shop", "Shop").apiPath("/api");
        e.knownEntity("Product").knownEntity("Customer").knownEntity("Line");
        e.knownEmbeddable("Address", Collections.singletonList("city"));
        for (int i = 0; i < fieldLines.length; i += 2) e.addField(loader.parse(fieldLines[i], fieldLines[i + 1]));
        return e;
    }

    private static FieldSpec named(EntitySpec e, String name) {
        for (FieldSpec f : e.fields()) {
            if (f.name().equals(name)) return f;
        }
        throw new AssertionError(name);
    }

    private EnumSpec status() {
        return new EnumSpec("Status")
                .addField(loader.parse("code", "string(2) @code"))
                .addField(loader.parse("label", "string(20)"))
                .jsonField("code")
                .constant("ON_SALE", "\"ON\"", "On sale")
                .constant("OFF", "OF", "Off");
    }

    /** A model of the test: the DTOs, the entities and the enumerations given, and who serves a search. */
    private static final class Run implements SchemaCatalog.Model {
        final Map<String, SchemaCatalog.Dto> dtos = new HashMap<String, SchemaCatalog.Dto>();
        final Map<String, EntitySpec> entities = new HashMap<String, EntitySpec>();
        final Map<String, EnumSpec> enums = new HashMap<String, EnumSpec>();
        final List<String> searched = new ArrayList<String>();

        public SchemaCatalog.Dto dto(String className) {
            return dtos.get(className);
        }

        public EntitySpec entity(String name) {
            return entities.get(name);
        }

        public EnumSpec enumeration(String name) {
            return enums.get(name);
        }

        public String enumerationDomain(String name) {
            return enums.containsKey(name) ? "shop" : null;
        }

        public boolean searches(EntitySpec entity) {
            return searched.contains(entity.getName());
        }

        final Map<String, SchemaSearch.Dto> searchDtos = new HashMap<String, SchemaSearch.Dto>();
        final Map<String, SchemaSearch.Query> queries = new HashMap<String, SchemaSearch.Query>();

        public SchemaSearch.Dto searchDto(String className) {
            return searchDtos.get(className);
        }

        public SchemaSearch.Query query(EntitySpec entity) {
            return queries.get(entity.getName());
        }
    }

    private Run run() {
        Run run = new Run();
        run.entities.put("Product", spec("Product", "productId", "long @id", "name", "string(30)"));
        run.entities.put("Customer", spec("Customer", "customerId", "long @id", "name", "string(30)"));
        run.entities.put("Line", spec("Line", "lineId", "long @id"));
        run.entities.put("Address", spec("Address", "city", "string(20)"));
        run.enums.put("Status", status());
        run.searched.add("Product");
        return run;
    }

    @Test
    public void theFileHasEachServedEntityAndItsDtosFields() {
        EntitySpec item = spec(
                "Sc373Item",
                "itemId",
                "long @id",
                "name",
                "string(30)! @length(min=2) -- Name",
                "unitCost",
                "decimal(10.2)! @min(0.01) @max(999) @label(en=Unit cost) -- Danga",
                "status",
                "Status",
                "product",
                "Product @ManyToOne",
                "body",
                "text @html");
        item.meta("label", "Item for sale");
        EntitySpec dto = spec(
                "Sc373Item",
                "itemId",
                "long @id",
                "name",
                "string(30)! @length(min=2) -- Name",
                "unitCost",
                "decimal(10.2)! @min(0.01) @max(999) @label(en=Unit cost) -- Danga",
                "status",
                "Status",
                "productId",
                "long @idof(product)",
                "productName",
                "string @ref(product.name)",
                "body",
                "text @html",
                "tags",
                "List<string>");
        // As SpecFactory gives a DTO spec its domain's enumerations.
        dto.knownEnum(status(), null, false);
        Run run = run();
        run.dtos.put("ItemDTO", new SchemaCatalog.Dto("com.acme.dto.ItemDTO", dto, dto.fields()));

        String json = SchemaCatalog.json(
                "shop",
                Arrays.asList(
                        new SchemaCatalog.Served(item, "/api/shop/item", "ItemDTO", "ItemSrchDTO"),
                        new SchemaCatalog.Served(
                                spec("Sc373Note", "noteId", "long @id"), "/api/shop/note", "NoteDTO", "NoteSrchDTO")),
                run);
        String expected = String.join(
                "\n",
                "{",
                "  \"metaVersion\": 1,",
                "  \"domain\": \"shop\",",
                "  \"entities\": {",
                "    \"Sc373Item\": {",
                "      \"class\": \"com.acme.dto.ItemDTO\",",
                "      \"entity\": {",
                "        \"name\": \"Sc373Item\",",
                "        \"domain\": \"shop\",",
                "        \"label\": \"Item for sale\",",
                "        \"labels\": {},",
                "        \"path\": \"/api/shop/item\",",
                "        \"key\": \"itemId\",",
                "        \"dto\": \"ItemDTO\",",
                "        \"searchDto\": \"ItemSrchDTO\"",
                "      },",
                "      \"fields\": {",
                "        \"itemId\": {",
                "          \"kind\": \"Integer\",",
                "          \"label\": \"itemId\",",
                "          \"labels\": {},",
                "          \"required\": false,",
                "          \"readOnly\": false,",
                "          \"html\": false",
                "        },",
                "        \"name\": {",
                "          \"kind\": \"String\",",
                "          \"label\": \"Name\",",
                "          \"labels\": {},",
                "          \"required\": true,",
                "          \"maxLength\": 30,",
                "          \"minLength\": 2,",
                "          \"readOnly\": false,",
                "          \"html\": false",
                "        },",
                "        \"unitCost\": {",
                "          \"kind\": \"Decimal\",",
                "          \"label\": \"Danga\",",
                "          \"labels\": {",
                "            \"en\": \"Unit cost\"",
                "          },",
                "          \"required\": true,",
                "          \"min\": 0.01,",
                "          \"max\": 999,",
                "          \"readOnly\": false,",
                "          \"html\": false",
                "        },",
                "        \"status\": {",
                "          \"kind\": \"Enum\",",
                "          \"label\": \"status\",",
                "          \"labels\": {},",
                "          \"required\": false,",
                "          \"readOnly\": false,",
                "          \"html\": false,",
                "          \"enum\": \"Status\",",
                "          \"enumDomain\": \"shop\",",
                "          \"options\": [",
                "            {",
                "              \"value\": \"ON\",",
                "              \"name\": \"ON_SALE\",",
                "              \"label\": \"On sale\",",
                "              \"labels\": {}",
                "            },",
                "            {",
                "              \"value\": \"OF\",",
                "              \"name\": \"OFF\",",
                "              \"label\": \"Off\",",
                "              \"labels\": {}",
                "            }",
                "          ]",
                "        },",
                "        \"productId\": {",
                "          \"kind\": \"Ref\",",
                "          \"label\": \"productId\",",
                "          \"labels\": {},",
                "          \"required\": false,",
                "          \"readOnly\": false,",
                "          \"html\": false,",
                "          \"ref\": {",
                "            \"entity\": \"Product\",",
                "            \"domain\": \"shop\",",
                "            \"key\": \"productId\",",
                "            \"search\": \"/api/shop/product/search\",",
                "            \"display\": {",
                "              \"field\": \"productName\",",
                "              \"of\": \"name\"",
                "            }",
                "          }",
                "        },",
                "        \"productName\": {",
                "          \"kind\": \"String\",",
                "          \"label\": \"productName\",",
                "          \"labels\": {},",
                "          \"required\": false,",
                "          \"readOnly\": true,",
                "          \"html\": false",
                "        },",
                "        \"body\": {",
                "          \"kind\": \"Html\",",
                "          \"label\": \"body\",",
                "          \"labels\": {},",
                "          \"required\": false,",
                "          \"readOnly\": false,",
                "          \"html\": true",
                "        },",
                "        \"tags\": {",
                "          \"kind\": \"List\",",
                "          \"label\": \"tags\",",
                "          \"labels\": {},",
                "          \"required\": false,",
                "          \"readOnly\": false,",
                "          \"html\": false",
                "        }",
                "      }",
                "    },",
                "    \"Sc373Note\": {",
                "      \"entity\": {",
                "        \"name\": \"Sc373Note\",",
                "        \"domain\": \"shop\",",
                "        \"label\": \"Sc373Note\",",
                "        \"labels\": {},",
                "        \"path\": \"/api/shop/note\",",
                "        \"key\": \"noteId\",",
                "        \"dto\": \"NoteDTO\",",
                "        \"searchDto\": \"NoteSrchDTO\"",
                "      },",
                "      \"fields\": {}",
                "    }",
                "  }",
                "}",
                "");
        assertEquals(expected, json);
    }

    @Test
    public void theTypesLabelAndAKeyOfTwoFields() {
        EntitySpec line = spec("OrderLine", "orderId", "long @id", "lineNo", "int @id");
        EntitySpec dto = spec("OrderLine", "orderId", "long @id", "lineNo", "int @id");
        dto.meta("label", "Order line").labelTranslations(Collections.singletonMap("ko", "Line ko"));
        Run run = run();
        run.dtos.put("OrderLineDTO", new SchemaCatalog.Dto("a.OrderLineDTO", dto, dto.fields()));
        String json = SchemaCatalog.json(
                "shop",
                Collections.singletonList(new SchemaCatalog.Served(line, "/api/shop/orderLine", "OrderLineDTO", "S")),
                run);
        assertTrue(
                json.contains("\"label\": \"Order line\",\n        \"labels\": {\n          \"ko\": \"Line ko\"\n"),
                json);
        assertTrue(json.contains("\"key\": [\n          \"orderId\",\n          \"lineNo\"\n        ],"), json);
    }

    @Test
    public void whatAReferencePointsAt() {
        Run run = run();
        EntitySpec order = spec(
                "Order",
                "orderId",
                "long @id",
                "customer",
                "Customer @ManyToOne",
                "product",
                "Product @ManyToOne",
                "lines",
                "List<Line> @OneToMany(mappedBy=\"order\")",
                "home",
                "Address @Embedded");
        EntitySpec dto = spec(
                "Order",
                "customerCustomerId",
                "long @ref(customer.customerId)",
                "customerName",
                "string @ref(customer.name)",
                "product",
                "Product @ManyToOne @dtype(ProductDTO)",
                "lineCount",
                "int @ref(lines.lineId)",
                "home",
                "Address @Embedded",
                "plain",
                "string(10)",
                "nowhere",
                "long @ref(nothing.id)",
                "bare",
                "long @ref(customer)",
                "deep",
                "string @ref(customer.address.city)");
        List<FieldSpec> fields = dto.fields();

        Map<String, Object> flat = SchemaCatalog.ref(named(dto, "customerCustomerId"), order, fields, run);
        assertEquals("Customer", flat.get("entity"));
        assertEquals("customerId", flat.get("key"));
        // Customer's controller serves no search here.
        assertFalse(flat.containsKey("search"));
        Map<String, Object> display = new HashMap<String, Object>();
        display.put("field", "customerName");
        display.put("of", "name");
        assertEquals(display, flat.get("display"));
        assertEquals(
                "Ref",
                SchemaCatalog.field(named(dto, "customerCustomerId"), order, fields, run)
                        .get("kind"));

        // A nested row is the relation itself.
        Map<String, Object> nested = SchemaCatalog.ref(named(dto, "product"), order, fields, run);
        assertEquals("Product", nested.get("entity"));
        assertEquals("/api/shop/product/search", nested.get("search"));
        assertFalse(nested.containsKey("display"));

        // A shown value, a list, a value object, a plain value, an unknown relation: no reference.
        assertNull(SchemaCatalog.ref(named(dto, "customerName"), order, fields, run));
        assertNull(SchemaCatalog.ref(named(dto, "lineCount"), order, fields, run));
        assertNull(SchemaCatalog.ref(named(dto, "home"), order, fields, run));
        assertEquals(
                "Object",
                SchemaCatalog.field(named(dto, "home"), order, fields, run).get("kind"));
        assertNull(SchemaCatalog.ref(named(dto, "plain"), order, fields, run));
        assertNull(SchemaCatalog.ref(named(dto, "nowhere"), order, fields, run));
        assertNull(SchemaCatalog.ref(named(dto, "bare"), order, fields, run));
        assertNull(SchemaCatalog.ref(named(dto, "deep"), order, fields, run));
    }

    @Test
    public void aReferenceToAnEntityWithoutAKnownKeyIsNoReference() {
        Run run = run();
        run.entities.put("Customer", spec("Customer", "name", "string(30)"));
        EntitySpec order = spec("Order", "orderId", "long @id", "customer", "Customer @ManyToOne");
        EntitySpec dto = spec("Order", "customerId", "long @idof(customer)");
        assertNull(SchemaCatalog.ref(named(dto, "customerId"), order, dto.fields(), run));
        run.entities.remove("Customer");
        assertNull(SchemaCatalog.ref(named(dto, "customerId"), order, dto.fields(), run));
        // Two keys: the reference by id names both.
        run.entities.put("Customer", spec("Customer", "a", "long @id", "b", "long @id"));
        assertEquals(
                Arrays.asList("a", "b"),
                SchemaCatalog.ref(named(dto, "customerId"), order, dto.fields(), run)
                        .get("key"));
    }

    @Test
    public void aTypeIsNamedWithoutItsDomainOrPackage() {
        assertEquals("Office", SchemaCatalog.simple("common:Office"));
        assertEquals("Office", SchemaCatalog.simple("a.b.Office"));
        assertEquals("Office", SchemaCatalog.simple("Office"));
        assertNull(SchemaCatalog.simple(null));
    }

    @Test
    public void aBoundIsWrittenAsAJsonNumber() {
        Run run = run();
        EntitySpec e = spec("Sc373Count", "n", "int @min(007) @max(-0.50)", "m", "int @min(1)");
        Map<String, Object> f = SchemaCatalog.field(named(e, "n"), e, e.fields(), run);
        // 007 is no JSON number; 7 is.
        assertEquals("7", ((SchemaCatalog.JsonNumber) f.get("min")).text);
        assertEquals("-0.50", ((SchemaCatalog.JsonNumber) f.get("max")).text);
        assertEquals(
                "1",
                ((SchemaCatalog.JsonNumber) SchemaCatalog.field(named(e, "m"), e, e.fields(), run)
                                .get("min"))
                        .text);
    }

    /** No label on the DTO, nor on an entity the model knows: the served entity's own. Neither: its name. */
    @Test
    public void theEntitysOwnLabelWhenTheDtoHasNone() {
        EntitySpec served = spec("Sc373Desk", "deskId", "long @id");
        served.meta("label", "Desk");
        EntitySpec dto = spec("Sc373Desk", "deskId", "long @id");
        Run run = run();
        run.dtos.put("Sc373DeskDTO", new SchemaCatalog.Dto("a.Sc373DeskDTO", dto, dto.fields()));
        String json = SchemaCatalog.json(
                "shop",
                Collections.singletonList(new SchemaCatalog.Served(served, "/api/shop/sc373Desk", "Sc373DeskDTO", "S")),
                run);
        assertTrue(json.contains("\"label\": \"Desk\","), json);
    }

    /** What POST /search takes, after the fields: where the controller serves it and the run wrote its search DTO. */
    @Test
    public void theSearchWhereTheControllerServesOne() {
        EntitySpec served = spec("Sc373Shelf", "shelfId", "long @id");
        Run run = run();
        run.searchDtos.put(
                "Sc373ShelfSrchDTO",
                new SchemaSearch.Dto(
                        Collections
                                .<io.elasticore.base.model.pub.rules.base.SearchDtoBaseRules.SearchProperty>emptyList(),
                        30,
                        Arrays.asList("shelfId", "name")));
        List<SchemaCatalog.Served> one = Collections.singletonList(
                new SchemaCatalog.Served(served, "/api/shop/sc373Shelf", "Sc373ShelfDTO", "Sc373ShelfSrchDTO"));
        // The controller serves no search: none.
        assertFalse(SchemaCatalog.json("shop", one, run).contains("\"search\""));

        run.searched.add("Sc373Shelf");
        assertTrue(
                SchemaCatalog.json("shop", one, run)
                        .contains("      \"fields\": {},\n      \"search\": {\n        \"pageSize\": 30,\n"
                                + "        \"sortable\": [\n          \"shelfId\",\n          \"name\"\n        ],\n"
                                + "        \"fields\": {}\n      }\n"),
                SchemaCatalog.json("shop", one, run));
        // What a query sorts by, where the mode writes one; a list, not a page: no page size.
        run.queries.put(
                "Sc373Shelf",
                new SchemaSearch.Query(Collections.<String[]>emptyList(), Collections.singletonList("name")));
        String queried = SchemaCatalog.json(
                "shop",
                Collections.singletonList(new SchemaCatalog.Served(
                        served, "/api/shop/sc373Shelf", "Sc373ShelfDTO", "Sc373ShelfSrchDTO", false)),
                run);
        assertTrue(
                queried.contains(
                        "\"search\": {\n        \"pageSize\": null,\n        \"sortable\": [\n          \"name\"\n"),
                queried);
        // A search DTO the run did not write: none.
        run.searchDtos.clear();
        assertFalse(SchemaCatalog.json("shop", one, run).contains("\"search\""));
    }

    @Test
    public void theFileIsAscii() {
        assertEquals(
                "\"a\\\"b\\\\c\\nd\\re\\tf\\u0001\\u00e9\\ud310~\"",
                SchemaCatalog.string("a\"b\\c\nd\re\tf\u0001\u00e9\ud310~"));
        assertEquals("\"\\u007f\"", SchemaCatalog.string("\u007f"));
    }
}
