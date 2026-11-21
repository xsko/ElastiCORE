package io.elasticore.springboot3.mask;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Jackson 2 module (Spring Boot 3) that writes a {@link Mask} field masked. elasticore-springboot4 has the same
 * class on Jackson 3. Spring Boot registers a Module bean with the application's ObjectMapper; a field without
 * {@link Mask}, or one that already names its own serializer, is written as before.
 */
@Configuration(proxyBeanMethods = false)
public class MaskJackson {

    @Bean
    public Module elastiCoreMaskModule() {
        return module();
    }

    /** The module, for an ObjectMapper the application builds itself. */
    public static Module module() {
        SimpleModule module = new SimpleModule("elasticore-mask");
        module.setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(
                    SerializationConfig config, BeanDescription beanDesc, List<BeanPropertyWriter> properties) {
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
        public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(type.apply(String.valueOf(value)));
        }
    }
}
