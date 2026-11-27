package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.core.LabelTranslations;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The facts a screen reads about a field (issue 373): one function each, the
 * same ones the TypeScript client's XxxFields, XxxMeta and XxxLabel are
 * written from.
 */
public class FieldFactsTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    /** The one field of an Item carrying the given line. */
    private FieldSpec field(String name, String line) {
        return item().addField(loader.parse(name, line)).fields().get(0);
    }

    private static EntitySpec item() {
        return new EntitySpec("Item").domain("shop", "Shop");
    }

    // ---- label, labels -----------------------------------------------------------

    @Test
    public void theLabelIsTheDashTextTrimmedOrElseTheName() {
        assertEquals("Unit price", FieldFacts.label(field("unitPrice", "money(10.2) --   Unit price  ")));
        assertEquals("unitPrice", FieldFacts.label(field("unitPrice", "money(10.2)")));
        assertEquals("Unit price", FieldFacts.label(field("unitPrice", "money(10.2) @label(Unit price)")));
    }

    @Test
    public void theLabelsAreTheOtherLanguages() {
        Map<String, String> labels =
                FieldFacts.labels(field("unitCost", "money(10.2) @label(en=Unit cost, ja=Tanka) -- Danga"));
        Map<String, String> expected = new TreeMap<String, String>();
        expected.put("en", "Unit cost");
        expected.put("ja", "Tanka");
        assertEquals(expected, labels);
        assertTrue(FieldFacts.labels(field("unitCost", "money(10.2) -- Danga")).isEmpty());
    }

    // ---- html, writable, readOnly ------------------------------------------------

    @Test
    public void htmlIsTheMarkupTheServerCleans() {
        assertTrue(FieldFacts.html(field("body", "text @html")));
        assertFalse(FieldFacts.html(field("body", "text")));
    }

    @Test
    public void aPlainFieldIsWritable() {
        FieldSpec f = field("name", "string(30)");
        assertTrue(FieldFacts.writable(f));
        assertFalse(FieldFacts.readOnly(f));
    }

    @Test
    public void whatTheServerWritesIsReadOnly() {
        String[] lines = {
            "string(30) @readonly",
            "string(30) @noinput",
            "string(30) @behavior",
            "string(30) @statefield",
            "string(30) @snapshot",
            "string(30) @compute",
            "string(30) @formula(upper(name))",
            "string(30) @ref(customer.name)",
            "long @jpa:Version",
            "string(30) @jpa:org.springframework.data.annotation.CreatedBy"
        };
        for (String line : lines) {
            FieldSpec f = field("x", line);
            assertFalse(FieldFacts.writable(f), line);
            assertTrue(FieldFacts.readOnly(f), line);
        }
    }

    /** A stamp is the lifecycle's to write only where a split root has one to write it. */
    @Test
    public void aStampIsReadOnlyOnlyOnASplitRoot() {
        assertTrue(FieldFacts.writable(field("shippedAt", "datetime @stamp(shipped)")));

        EntitySpec split = item().entitySplitForBehavior("Item");
        FieldSpec stamp = split.addField(loader.parse("shippedAt", "datetime @stamp(shipped)"))
                .fields()
                .get(0);
        assertFalse(FieldFacts.writable(stamp));
        FieldSpec plain =
                split.addField(loader.parse("note", "string(30)")).fields().get(1);
        assertTrue(FieldFacts.writable(plain));
    }

    // ---- required, lengths, bounds ------------------------------------------------

    @Test
    public void requiredIsWhatARequestMustCarry() {
        assertTrue(FieldFacts.required(field("name", "string(30)!")));
        assertFalse(FieldFacts.required(field("name", "string(30)")));
        // A field no request writes is not asked of one.
        assertFalse(FieldFacts.required(field("name", "string(30)! @readonly")));
    }

    @Test
    public void theLengthsOfAText() {
        FieldSpec f = field("code", "string(30) @length(min=2)");
        assertEquals(Integer.valueOf(30), FieldFacts.maxLength(f));
        assertEquals(Integer.valueOf(2), FieldFacts.minLength(f));

        FieldSpec plain = field("code", "string(30)");
        assertEquals(Integer.valueOf(30), FieldFacts.maxLength(plain));
        assertNull(FieldFacts.minLength(plain));

        // Only a text has a length; a read-only one is not checked.
        assertNull(FieldFacts.maxLength(field("count", "int @length(max=5)")));
        assertNull(FieldFacts.minLength(field("count", "int @length(min=2, max=5)")));
        assertNull(FieldFacts.maxLength(field("code", "string(30) @readonly")));
        assertNull(FieldFacts.minLength(field("code", "string(30) @length(min=2) @readonly")));
    }

    @Test
    public void aLengthOfZeroIsAMaximumButNoMinimum() {
        FieldSpec f = field("code", "string @length(min=0, max=0)");
        assertEquals(Integer.valueOf(0), FieldFacts.maxLength(f));
        assertNull(FieldFacts.minLength(f));
        assertEquals(Integer.valueOf(1), FieldFacts.minLength(field("code", "string(9) @length(min=1)")));
    }

    @Test
    public void theBoundsAreTheirNumbers() {
        FieldSpec f = field("price", "bigdecimal(10.2) @min(0.01, 'too cheap') @max(999)");
        assertEquals("0.01", FieldFacts.min(f));
        assertEquals("999", FieldFacts.max(f));
        assertEquals("-5", FieldFacts.min(field("t", "int @minval(-5) @maxvalue(7)")));
        assertEquals("7", FieldFacts.max(field("t", "int @minval(-5) @maxvalue(7)")));
        assertEquals("1", FieldFacts.min(field("t", "int @minvalue(1) @maxval(2)")));
        assertEquals("2", FieldFacts.max(field("t", "int @minvalue(1) @maxval(2)")));
        assertNull(FieldFacts.min(field("t", "int")));
        assertNull(FieldFacts.max(field("t", "int")));
        // A read-only field is not checked.
        assertNull(FieldFacts.min(field("t", "int @min(1) @readonly")));
        assertNull(FieldFacts.max(field("t", "int @max(1) @readonly")));
    }

    @Test
    public void aBoundIsANumberOrNothing() {
        assertEquals("1", FieldFacts.bound("1"));
        assertEquals("0.01", FieldFacts.bound(" 0.01 , 'message'"));
        assertEquals("-3.5", FieldFacts.bound("-3.5"));
        assertNull(FieldFacts.bound(null));
        assertNull(FieldFacts.bound("ten"));
        assertNull(FieldFacts.bound("1."));
        assertNull(FieldFacts.bound(".5"));
        assertNull(FieldFacts.bound("1e3"));
        // Nothing before the comma is no number either (it stopped the generation before).
        assertNull(FieldFacts.bound(","));
        assertNull(FieldFacts.bound(",,"));
        assertNull(FieldFacts.bound(" , 'message'"));
        assertNull(FieldFacts.bound(""));
        assertEquals("7", FieldFacts.bound("7,"));
    }

    // ---- the entity's label --------------------------------------------------------

    @Test
    public void theTypesOwnLabelComesFirst() {
        Map<String, String> own = Collections.singletonMap("en", "Pet");
        EntitySpec e = item().labelTranslations(own);
        e.meta("label", "  Haustier ");
        FieldFacts.Label label = FieldFacts.entityLabel(e);
        assertEquals("Haustier", label.text());
        assertEquals(own, label.translations());
    }

    @Test
    public void withoutItsOwnTheTypeIsShownByItsEntitysLabel() {
        io.elasticore.base.model.core.Annotation model = LabelTranslations.parse("label", "' Tier ', fr=Animal");
        FieldFacts.Label label = FieldFacts.entityLabel(model);
        assertEquals("Tier", label.text());
        assertEquals(Collections.singletonMap("fr", "Animal"), label.translations());
        // A label without translations has none.
        FieldFacts.Label plain =
                FieldFacts.entityLabel(io.elasticore.base.model.core.Annotation.create("label", "Tier"));
        assertEquals("Tier", plain.text());
        assertTrue(plain.translations().isEmpty());
    }

    @Test
    public void withoutAnyLabelTheTypeHasNone() {
        assertNull(FieldFacts.entityLabel((io.elasticore.base.model.core.Annotation) null));
        assertNull(FieldFacts.entityLabel(LabelTranslations.parse("label", " , fr=Animal")));
        assertNull(FieldFacts.entityLabel(LabelTranslations.parse("label", "'   ', fr=Animal")));
        // A blank label of its own is none; nor is a type in no model.
        EntitySpec stray =
                new EntitySpec("NoSuchTypeAnywhere373").labelTranslations(Collections.singletonMap("en", "x"));
        stray.meta("label", "   ");
        assertNull(FieldFacts.entityLabel(stray));
        assertNull(FieldFacts.entityLabel(new EntitySpec("NoSuchTypeAnywhere373")));
    }

    // ---- enumerations --------------------------------------------------------------

    private EnumSpec status() {
        return new EnumSpec("Status")
                .addField(loader.parse("code", "string(1) @code"))
                .addField(loader.parse("label", "string(20)"))
                .addField(loader.parse("label_en", "string(20)"))
                .constant("ACTIVE", "\"A\"", "\" In use \"", "\"Active\"")
                .constant("STOPPED", "\"S\"", "\"Stopped\"", "\"Stopped\"")
                .jsonField("code");
    }

    private static EnumSpec.Constant constant(EnumSpec e, String name) {
        for (EnumSpec.Constant c : e.constants()) {
            if (c.name().equals(name)) return c;
        }
        throw new AssertionError(name);
    }

    @Test
    public void theWireValueIsTheJsonFieldsValue() {
        EnumSpec e = status();
        assertEquals("A", FieldFacts.enumWireValue(e, constant(e, "ACTIVE")));
        assertEquals("S", FieldFacts.enumWireValue(e, constant(e, "STOPPED")));
    }

    @Test
    public void withoutAJsonFieldTheWireValueIsTheConstantsName() {
        EnumSpec plain = new EnumSpec("Size").constant("LARGE");
        assertEquals("LARGE", FieldFacts.enumWireValue(plain, constant(plain, "LARGE")));
        // A json field the constant has no value for.
        EnumSpec shortRow = new EnumSpec("Size")
                .addField(loader.parse("label", "string(20)"))
                .addField(loader.parse("code", "string(1)"))
                .constant("SMALL", "\"Small\"")
                .jsonField("code");
        assertEquals("SMALL", FieldFacts.enumWireValue(shortRow, constant(shortRow, "SMALL")));
        // An unquoted value is taken as written.
        EnumSpec bare = new EnumSpec("Size")
                .addField(loader.parse("code", "int"))
                .constant("ONE", "1")
                .jsonField("code");
        assertEquals("1", FieldFacts.enumWireValue(bare, constant(bare, "ONE")));
    }

    @Test
    public void theLabelIsTheLabelColumnTrimmed() {
        EnumSpec e = status();
        assertEquals("In use", FieldFacts.enumLabel(e, constant(e, "ACTIVE")));
        assertEquals("Stopped", FieldFacts.enumLabel(e, constant(e, "STOPPED")));
    }

    @Test
    public void withoutALabelColumnTheConstantIsSaidInWords() {
        EnumSpec plain =
                new EnumSpec("Card").constant("MASTER_CARD").constant("VISA").constant("");
        assertEquals("Master card", FieldFacts.enumLabel(plain, constant(plain, "MASTER_CARD")));
        assertEquals("Visa", FieldFacts.enumLabel(plain, constant(plain, "VISA")));
        assertEquals("", FieldFacts.enumLabel(plain, constant(plain, "")));
        // A label column the constant has no value for.
        EnumSpec shortRow = new EnumSpec("Card")
                .addField(loader.parse("code", "string(1)"))
                .addField(loader.parse("label", "string(20)"))
                .constant("GIFT_CARD", "\"G\"")
                .jsonField("code");
        assertEquals("Gift card", FieldFacts.enumLabel(shortRow, constant(shortRow, "GIFT_CARD")));
    }

    @Test
    public void theLabelsAreTheTranslationsOfTheConstant() {
        Map<String, Map<String, String>> all = new TreeMap<String, Map<String, String>>();
        all.put("ACTIVE", Collections.singletonMap("en", "Active"));
        EnumSpec e = status().labelTranslations(all);
        assertEquals(Collections.singletonMap("en", "Active"), FieldFacts.enumLabels(e, constant(e, "ACTIVE")));
        assertTrue(FieldFacts.enumLabels(e, constant(e, "STOPPED")).isEmpty());
        assertTrue(FieldFacts.enumLabels(status(), constant(status(), "ACTIVE")).isEmpty());
    }

    // ---- kind ------------------------------------------------------------------------

    @Test
    public void theKindOfAValueIsOssiesWordForIt() {
        assertEquals("String", FieldFacts.kind(field("x", "string(30)")));
        assertEquals("Integer", FieldFacts.kind(field("x", "int")));
        assertEquals("Integer", FieldFacts.kind(field("x", "long")));
        assertEquals("Decimal", FieldFacts.kind(field("x", "bigdecimal(10.2)")));
        assertEquals("Float", FieldFacts.kind(field("x", "double")));
        assertEquals("Boolean", FieldFacts.kind(field("x", "boolean")));
        assertEquals("Date", FieldFacts.kind(field("x", "date")));
        assertEquals("DateTime", FieldFacts.kind(field("x", "datetime")));
        assertEquals("Opaque", FieldFacts.kind(field("x", "string(4000) @columndef(json)")));
    }

    @Test
    public void theKindSaysWhatTheModelKnowsBeyondThat() {
        assertEquals("Money", FieldFacts.kind(field("x", "money(10.2)")));
        assertEquals("Html", FieldFacts.kind(field("x", "text @html")));
        assertEquals("List", FieldFacts.kind(field("x", "List<String>")));

        EntitySpec e = item().knownEntity("Product").knownEnum("Status", "", false);
        e.addField(loader.parse("product", "Product @ManyToOne"))
                .addField(loader.parse("status", "Status"))
                .addField(loader.parse("address", "ZzAddress373DTO"))
                .addField(loader.parse("products", "List<Product> @OneToMany"));
        assertEquals("Ref", FieldFacts.kind(e.fields().get(0)));
        assertEquals("Enum", FieldFacts.kind(e.fields().get(1)));
        assertEquals("Object", FieldFacts.kind(e.fields().get(2)));
        assertEquals("List", FieldFacts.kind(e.fields().get(3)));
    }
}
