package io.elasticore.base.model.loader;

import io.elasticore.base.model.entity.BaseFieldType;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.util.ConsoleLog;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The html type (issue 183): read as a long text, marked html; the 2.x generator, which would store it as a request
 * sends it, is stopped by a DSL warning (it fails the build on any).
 */
public class HtmlFieldTypeTest {

    @Test
    public void anHtmlFieldIsALongTextMarkedHtml() {
        Field f = new Loader().parseFieldLine("content", "html! @safe(relaxed) @plain(contentText)");
        assertEquals(BaseFieldType.STRING, f.getTypeInfo().getBaseFieldType());
        assertTrue(f.hasAnnotation("html"));
        assertTrue(f.hasAnnotation("text"));
        assertTrue(f.hasAnnotation("notnull"));
        assertEquals("relaxed", f.getAnnotationValue("safe"));
        assertEquals("contentText", f.getAnnotationValue("plain"));

        Field text = new Loader().parseFieldLine("note", "text");
        assertTrue(text.hasAnnotation("text"));
        assertFalse(text.hasAnnotation("html"));
    }

    @Test
    public void theTwoXGeneratorIsStoppedAndTheRuleEngineIsNot() {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("content", "html! @plain(contentText)");
        fields.put("contentText", "text");
        ConsoleLog.clearKey("DSL_WARN");
        BaseFieldType.ruleEngineTypes(true);
        try {
            new Loader().fields(fields);
        } finally {
            BaseFieldType.ruleEngineTypes(false);
        }
        assertEquals(0, ConsoleLog.countStoredLogkey("DSL_WARN"));

        new Loader().fields(fields);
        assertEquals(1, ConsoleLog.countStoredLogkey("DSL_WARN"));
        ConsoleLog.clearKey("DSL_WARN");
    }

    private static class Loader extends AbstractModelLoader {
        void fields(Map<String, String> fields) {
            super.parseField(fields, "Post", null);
        }

        @Override
        protected boolean isValidFieldName(String fieldName) {
            return true;
        }

        @Override
        public Field parseFieldLine(String fieldNm, String fieldLine) {
            return super.parseFieldLine(fieldNm, fieldLine, null);
        }
    }
}
