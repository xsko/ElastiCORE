package io.elasticore.base.model.loader;

import io.elasticore.base.model.core.Annotation;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * '*' is a parameter key of its own: @access(read=public, *=owner) keeps its
 * default beside the other entries, where it was read as a lone value and
 * dropped.
 */
public class StarParameterTest {

    private static final class Loader extends AbstractModelLoader {
        Annotation parse(String parameters) {
            return parseParameters("access", parameters);
        }

        Annotation parse(String name, String parameters) {
            return parseParameters(name, parameters);
        }
    }

    /** Outside @access a '*' is no key: a "--" comment such as "x *= 2" is the @label 2.x made of it. */
    @Test
    public void aStarOutsideAccessIsReadAs2xDid() {
        Annotation label = new Loader().parse("label", "x *= 2");
        assertEquals("2", label.getValue());
        assertNull(label.getProperties() == null ? null : label.getProperties().getProperty("*"));
        Annotation other = new Loader().parse("desc", "*=owner");
        assertEquals("*=owner", other.getValue());
        assertNull(other.getProperties() == null ? null : other.getProperties().getProperty("*"));
    }

    @Test
    public void aStarEntryBesideOthersIsKept() {
        Annotation a = new Loader().parse("read=public, create=public, *=owner");
        assertEquals("owner", a.getProperties().getProperty("*"));
        assertEquals("public", a.getProperties().getProperty("read"));
        assertEquals("public", a.getProperties().getProperty("create"));
    }

    @Test
    public void aStarEntryAloneIsAKeyToo() {
        Annotation a = new Loader().parse("*=user");
        assertEquals("user", a.getProperties().getProperty("*"));
        assertNull(a.getValue());
    }

    @Test
    public void wordsAreReadAsBefore() {
        Annotation a = new Loader().parse("min=7,max=23");
        assertEquals("7", a.getProperties().getProperty("min"));
        assertEquals("23", a.getProperties().getProperty("max"));
        assertEquals("admin", new Loader().parse("admin").getValue());
    }
}
