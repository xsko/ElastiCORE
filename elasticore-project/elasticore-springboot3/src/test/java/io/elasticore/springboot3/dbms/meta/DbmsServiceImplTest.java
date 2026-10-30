package io.elasticore.springboot3.dbms.meta;

import io.elasticore.runtime.port.DbmsService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DbmsServiceImplTest {

    @DbmsService(id = "orders", datasource = "reporting", sqlSource = "orders.sql")
    static class Annotated {}

    private final DbmsService declared = Annotated.class.getAnnotation(DbmsService.class);

    @Test
    void itEqualsTheAnnotationTheJvmMakesWithTheSameMembers() {
        DbmsServiceImpl made = new DbmsServiceImpl("orders", "reporting", "orders.sql");
        assertEquals(declared, made);
        assertEquals(made, declared);
        assertEquals(declared.hashCode(), made.hashCode());
        assertEquals(made, made);
    }

    @Test
    void aDifferentMemberMakesADifferentAnnotation() {
        assertNotEquals(declared, new DbmsServiceImpl("orders", "primary", "orders.sql"));
        assertNotEquals(new DbmsServiceImpl("orders", "reporting", "other.sql"), declared);
        assertNotEquals(new DbmsServiceImpl("invoices", "reporting", "orders.sql"), declared);
        assertNotEquals(new DbmsServiceImpl("orders", "reporting", "orders.sql"), "orders");
    }

    @Test
    void itNamesItsMembers() {
        assertEquals(
                "@io.elasticore.runtime.port.DbmsService(datasource=\"reporting\", id=\"orders\", sqlSource=\"orders.sql\")",
                new DbmsServiceImpl("orders", "reporting", "orders.sql").toString());
        assertEquals(DbmsService.class, new DbmsServiceImpl("a", "b", "c").annotationType());
    }
}
