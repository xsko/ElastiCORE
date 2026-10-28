package io.elasticore.base.model.pub.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The persistence a domain's mode names, the third choice a bundle is made of.
 * Only JPA is generated; the axis exists so the next one has a place to go
 * without changing what JPA gets.
 */
public class PersistenceAxisTest {

    @Test
    public void unsaidIsJpa() {
        assertEquals(RulesConfig.Persistence.JPA, RulesConfig.persistenceOfMode(null));
        assertEquals(RulesConfig.Persistence.JPA, RulesConfig.persistenceOfMode(" "));
    }

    @Test
    public void documentsBesideAPersistenceDoNotChangeIt() {
        assertEquals(RulesConfig.Persistence.JPA, RulesConfig.persistenceOfMode("jpa,uml"));
        assertEquals(RulesConfig.Persistence.MYBATIS, RulesConfig.persistenceOfMode("dsl, MyBatis"));
        assertNull(RulesConfig.persistenceOfMode("uml"));
    }

    @Test
    public void jpaIsWrittenWhenAModeNamesItWithAnother() {
        // 2.x writes both halves of "jpa,mongo"; the rule engine keeps writing the JPA one.
        assertEquals(RulesConfig.Persistence.JPA, RulesConfig.persistenceOfMode("mongo,jpa"));
        assertEquals(RulesConfig.Persistence.JPA, RulesConfig.persistenceOfMode("mybatis,jpa"));
        assertEquals(RulesConfig.Persistence.MONGO, RulesConfig.persistenceOfMode("mongodb"));
    }

    @Test
    public void onlyJpaIsGenerated() {
        assertTrue(RulesConfig.Persistence.JPA.generated());
        for (RulesConfig.Persistence p : RulesConfig.Persistence.values()) {
            if (p != RulesConfig.Persistence.JPA) assertTrue(!p.generated(), p.name());
        }
    }

    @Test
    public void jpaNamesTheBundlesItAlwaysDid() {
        for (RulesConfig.Architecture a : RulesConfig.Architecture.values()) {
            for (RulesConfig.Language l : RulesConfig.Language.values()) {
                assertEquals(RulesConfig.bundleFor(a, l),
                        RulesConfig.bundleFor(a, l, RulesConfig.Persistence.JPA), a + "/" + l);
            }
        }
    }

    @Test
    public void anotherPersistenceGetsNoJpaBundle() {
        assertThrows(IllegalArgumentException.class, () -> RulesConfig.bundleFor(
                RulesConfig.Architecture.LAYERED, RulesConfig.Language.JAVA, RulesConfig.Persistence.MYBATIS));
        // The browser's model is the same whatever the server stores it in.
        assertEquals("ts-model", RulesConfig.bundleFor(
                RulesConfig.Architecture.LAYERED, RulesConfig.Language.TYPESCRIPT, RulesConfig.Persistence.MYBATIS));
    }
}
