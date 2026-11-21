package io.elasticore.base.model.loader;

import io.elasticore.base.model.entity.BaseFieldType;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.util.ConsoleLog;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The @encrypt mark: read as a field mark beside the others; the 2.x generator, which would store the value as sent,
 * is stopped by a DSL warning (it fails the build on any), and the rule engine is not.
 */
public class EncryptFieldMarkTest {

    @Test
    public void anEncryptedFieldIsAStringMarkedEncrypt() {
        Field f = new Loader().parseFieldLine("phone", "string(20) @encrypt @s(eq)");
        assertEquals(BaseFieldType.STRING, f.getTypeInfo().getBaseFieldType());
        assertTrue(f.hasAnnotation("encrypt"));
        assertEquals("eq", f.getAnnotationValue("s"));
        assertEquals(20, f.getLength());
    }

    @Test
    public void theTwoXGeneratorIsStoppedAndTheRuleEngineIsNot() {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("phone", "string(20) @encrypt @s(eq)");
        fields.put("name", "string(20)");
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
            super.parseField(fields, "Customer", null);
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
