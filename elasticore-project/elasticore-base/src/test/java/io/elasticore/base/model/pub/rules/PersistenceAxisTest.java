package io.elasticore.base.model.pub.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The persistence a domain's mode names, the third choice a bundle is made of.
 * JPA, MyBatis and MongoDB are generated; a new one takes its place on the
 * axis without changing what JPA gets.
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
    public void everyPersistenceIsGenerated() {
        assertTrue(RulesConfig.Persistence.JPA.generated());
        assertTrue(RulesConfig.Persistence.MYBATIS.generated());
        assertTrue(RulesConfig.Persistence.MONGO.generated());
        assertTrue(RulesConfig.mongo("mongo"));
        assertTrue(RulesConfig.mongo("dsl,mongodb"));
        assertTrue(!RulesConfig.mongo("jpa,mongo"));
        assertTrue(!RulesConfig.mongo(null));
    }

    @Test
    public void mongoIsLayeredJava() {
        assertEquals(
                RulesConfig.MONGO_BUNDLE,
                RulesConfig.bundleFor(
                        RulesConfig.Architecture.LAYERED, RulesConfig.Language.JAVA, RulesConfig.Persistence.MONGO));
        assertEquals(
                RulesConfig.MONGO_BUNDLE,
                RulesConfig.bundleFor(null, RulesConfig.Language.JAVA, RulesConfig.Persistence.MONGO));
        // The client is the same whatever the server stores it in.
        assertEquals(
                "ts-model",
                RulesConfig.bundleFor(
                        RulesConfig.Architecture.HEXAGONAL,
                        RulesConfig.Language.TYPESCRIPT,
                        RulesConfig.Persistence.MONGO));
        IllegalArgumentException kt = assertThrows(
                IllegalArgumentException.class,
                () -> RulesConfig.bundleFor(
                        RulesConfig.Architecture.LAYERED, RulesConfig.Language.KOTLIN, RulesConfig.Persistence.MONGO));
        assertTrue(kt.getMessage().contains("MongoDB code is written in Java, layered"), kt.getMessage());
        IllegalArgumentException ddd = assertThrows(
                IllegalArgumentException.class,
                () -> RulesConfig.bundleFor(
                        RulesConfig.Architecture.DOMAIN_MODEL,
                        RulesConfig.Language.JAVA,
                        RulesConfig.Persistence.MONGO));
        assertTrue(ddd.getMessage().contains("architecture = 'ddd'"), ddd.getMessage());
        IllegalArgumentException hex = assertThrows(
                IllegalArgumentException.class,
                () -> RulesConfig.bundleFor(
                        RulesConfig.Architecture.HEXAGONAL, RulesConfig.Language.JAVA, RulesConfig.Persistence.MONGO));
        assertTrue(hex.getMessage().contains("architecture = 'hexagonal'"), hex.getMessage());
        assertNull(RulesConfig.mongoRefusal(RulesConfig.Architecture.LAYERED, RulesConfig.Language.JAVA));
        assertNull(RulesConfig.mongoRefusal(RulesConfig.Architecture.DOMAIN_MODEL, RulesConfig.Language.TYPESCRIPT));
        // A domain naming the layered bundle gets the MongoDB one; nothing else has one.
        assertEquals(RulesConfig.MONGO_BUNDLE, RulesConfig.mongoBundleOf("jpa-springboot-v3"));
        assertEquals(RulesConfig.MONGO_BUNDLE, RulesConfig.mongoBundleOf(" mongo-springboot "));
        assertNull(RulesConfig.mongoBundleOf("ddd-springboot"));
        assertNull(RulesConfig.mongoBundleOf(null));
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
    public void myBatisIsJavaInEveryArchitecture() {
        assertEquals(RulesConfig.MYBATIS_BUNDLE, RulesConfig.bundleFor(
                RulesConfig.Architecture.LAYERED, RulesConfig.Language.JAVA, RulesConfig.Persistence.MYBATIS));
        assertEquals("ddd-mybatis-springboot", RulesConfig.bundleFor(
                RulesConfig.Architecture.DOMAIN_MODEL, RulesConfig.Language.JAVA, RulesConfig.Persistence.MYBATIS));
        assertEquals("hexagonal-mybatis-springboot", RulesConfig.bundleFor(
                RulesConfig.Architecture.HEXAGONAL, RulesConfig.Language.JAVA, RulesConfig.Persistence.MYBATIS));
        // A domain naming its architecture's bundle gets the MyBatis one of it.
        assertEquals("hexagonal-mybatis-springboot", RulesConfig.mybatisBundleOf("hexagonal-springboot"));
        assertEquals("ddd-mybatis-springboot", RulesConfig.mybatisBundleOf("ddd-springboot"));
        assertEquals(RulesConfig.MYBATIS_BUNDLE, RulesConfig.mybatisBundleOf("jpa-springboot-v3"));
        assertThrows(IllegalArgumentException.class, () -> RulesConfig.bundleFor(
                RulesConfig.Architecture.HEXAGONAL, RulesConfig.Language.KOTLIN, RulesConfig.Persistence.MYBATIS));
        assertThrows(IllegalArgumentException.class, () -> RulesConfig.bundleFor(
                RulesConfig.Architecture.LAYERED, RulesConfig.Language.KOTLIN, RulesConfig.Persistence.MYBATIS));
        // The browser's model is the same whatever the server stores it in.
        assertEquals("ts-model", RulesConfig.bundleFor(
                RulesConfig.Architecture.LAYERED, RulesConfig.Language.TYPESCRIPT, RulesConfig.Persistence.MYBATIS));
    }
}
