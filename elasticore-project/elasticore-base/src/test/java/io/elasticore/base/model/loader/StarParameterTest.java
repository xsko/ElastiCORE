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
