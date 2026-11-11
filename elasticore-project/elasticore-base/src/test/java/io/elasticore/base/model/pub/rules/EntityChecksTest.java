package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.BaseFieldType;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.model.pub.rules.base.EntityChecks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What a JPA entity cannot be generated or created as, said before its code
 * is written: a relation in an exposed composite key, and a table or column
 * a database reserves.
 */
public class EntityChecksTest {

    private static class TestLoader extends AbstractModelLoader {
        Field parse(String name, String fieldLine) {
            return super.parseFieldLine(name, fieldLine, null);
        }
    }

    private final TestLoader loader = new TestLoader();

    private EntitySpec spec(String name) {
        return new EntitySpec(name)
                .domain("shop", "Shop")
                .pkg("entity", "com.acme.shop.entity")
                .pkg("dto", "com.acme.shop.dto")
                .knownEntity("Ledger");
    }

    @Test
    public void aRelationInACompositeKeyIsSaid() {
        EntitySpec line = spec("LedgerLine")
                .addField(loader.parse("order", "Ledger @id @ManyToOne"))
                .addField(loader.parse("itemSeq", "int @id"))
                .addField(loader.parse("amount", "int"));
        assertEquals(
                "[elcore3] Shop.LedgerLine: its composite key holds the relation order: Ledger, and an exposed entity's"
                        + " controller takes each key field in the path, which a relation cannot be - key it by the"
                        + " related key's value (ledgerId: long @id), or do not expose it",
                EntityChecks.relationInExposedKey(line));
    }

    @Test
    public void aCompositeKeyByValueOrASingleKeyIsNotSaid() {
        EntitySpec byValue = spec("OrderItem")
                .addField(loader.parse("orderId", "long @id"))
                .addField(loader.parse("itemSeq", "int @id"))
                .addField(loader.parse("ledger", "Ledger @ManyToOne"));
        assertNull(EntityChecks.relationInExposedKey(byValue));
        EntitySpec single = spec("Note").addField(loader.parse("ledger", "Ledger @id @ManyToOne"));
        assertNull(EntityChecks.relationInExposedKey(single));
    }

    @Test
    public void aReservedTableOrColumnIsSaid() {
        EntitySpec order = spec("Order")
                .addField(loader.parse("orderId", "long @id @sequence"))
                .addField(loader.parse("group", "string(10)"))
                .addField(loader.parse("userName", "string(10)"))
                .addField(loader.parse("note", "string(10) @column(comment)"));
        assertEquals(
                "[Shop] Order: the table order, the column group, the column comment are reserved by Oracle,"
                        + " PostgreSQL or SQL Server and cannot be created there - name it otherwise with @table /"
                        + " @column.",
                EntityChecks.reservedNames(order));
    }

    @Test
    public void oneReservedNameIsSaidInTheSingular() {
        EntitySpec user = spec("Account").meta("table", "app.user").addField(loader.parse("accountId", "long @id"));
        assertEquals(
                "[Shop] Account: the table user is reserved by Oracle, PostgreSQL or SQL Server and cannot be created"
                        + " there - name it otherwise with @table / @column.",
                EntityChecks.reservedNames(user));
    }

    @Test
    public void whatIsNotAColumnOfTheTableIsNotSaid() {
        EntitySpec ok = spec("Shipment")
                .knownEntity("Order")
                .addField(loader.parse("shipmentId", "long @id"))
                .addField(loader.parse("order", "Order @ManyToOne"))
                .addField(loader.parse("user", "List<Ledger> @OneToMany"))
                .addField(loader.parse("size", "int @formula(1)"))
                .addField(loader.parse("level", "Level"))
                .addField(loader.parse("table", "money(10.2)"));
        assertNull(EntityChecks.reservedNames(ok));
        EntitySpec value = spec("Address").meta("embeddable", null).addField(loader.parse("order", "string(10)"));
        assertNull(EntityChecks.reservedNames(value));
    }

    @Test
    public void aPlainAbstractHasNoTableButItsColumnsAreSaid() {
        EntitySpec frame = spec("Order").meta("abstract", null).addField(loader.parse("group", "string(10)"));
        assertEquals(
                "[Shop] Order: the column group is reserved by Oracle, PostgreSQL or SQL Server and cannot be created"
                        + " there - name it otherwise with @table / @column.",
                EntityChecks.reservedNames(frame));
    }

    @Test
    public void anEnumColumnIsSaid() {
        EntitySpec e = spec("Task")
                .knownEnum("Level", "", false)
                .addField(loader.parse("taskId", "long @id"))
                .addField(loader.parse("level", "Level"));
        assertEquals(
                "[Shop] Task: the column level is reserved by Oracle, PostgreSQL or SQL Server and cannot be created"
                        + " there - name it otherwise with @table / @column.",
                EntityChecks.reservedNames(e));
    }

    @Test
    public void aTypeTheDslDoesNotHaveNamesWhatToWrite() {
        assertEquals("string(36), or string @id @genid for a generated key", BaseFieldType.insteadOf("uuid"));
        assertEquals("string(36), or string @id @genid for a generated key", BaseFieldType.insteadOf("UUID"));
        assertEquals("int", BaseFieldType.insteadOf("short"));
        assertEquals("int", BaseFieldType.insteadOf("byte"));
        assertEquals("string(1)", BaseFieldType.insteadOf("char"));
        assertEquals("string(1)", BaseFieldType.insteadOf("Character"));
        assertEquals("timestamp", BaseFieldType.insteadOf("OffsetDateTime"));
        assertEquals("timestamp", BaseFieldType.insteadOf("ZonedDateTime"));
        assertNull(BaseFieldType.insteadOf("Customer"));
        assertNull(BaseFieldType.insteadOf(null));
        assertEquals(" - write int instead", BaseFieldType.insteadOfSuffix("short"));
        assertEquals("", BaseFieldType.insteadOfSuffix("Customer"));
    }
}
