package io.elasticore.springboot3.mask;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * The Jackson 3 module (Spring Boot 4) that writes a {@link Mask} field masked. elasticore-springboot3 has the same
 * class on Jackson 2. Spring Boot registers a JacksonModule bean with the application's JsonMapper; a field without
 * {@link Mask}, or one that already names its own serializer, is written as before.
 */
@Configuration(proxyBeanMethods = false)
public class MaskJackson {

    @Bean
    public JacksonModule elastiCoreMaskModule() {
        return module();
    }

    /** The module, for a JsonMapper the application builds itself. */
    public static JacksonModule module() {
        SimpleModule module = new SimpleModule("elasticore-mask");
        module.setSerializerModifier(new ValueSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(
                    SerializationConfig config,
                    BeanDescription.Supplier beanDesc,
                    List<BeanPropertyWriter> properties) {
                for (BeanPropertyWriter property : properties) {
                    Mask mask = property.getAnnotation(Mask.class);
                    if (mask != null && !property.hasSerializer()) {
                        property.assignSerializer(new MaskSerializer(mask.value()));
                    }
                }
                return properties;
            }
        });
        return module;
    }

    static final class MaskSerializer extends StdSerializer<Object> {

        private final MaskType type;

        MaskSerializer(MaskType type) {
            super(Object.class);
            this.type = type;
        }

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext context) {
            gen.writeString(type.apply(String.valueOf(value)));
        }
    }
}
