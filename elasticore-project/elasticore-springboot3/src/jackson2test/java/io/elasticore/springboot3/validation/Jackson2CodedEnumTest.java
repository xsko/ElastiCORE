package io.elasticore.springboot3.validation;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Jackson 2 only (Spring Boot 3): a DSL enum with a json code field is read
 * by a generated CustomDeserializer that calls fromCode. What it refuses
 * reaches Spring as a plain JsonMappingException around the
 * IllegalArgumentException - not a creator's ValueInstantiationException -
 * and is still said as the enum's own message.
 */
public class Jackson2CodedEnumTest {

    /** As EnumBaseRules writes an enum with a json field for Jackson 2. */
    @JsonSerialize(using = PaymentMethod.CustomSerializer.class)
    @JsonDeserialize(using = PaymentMethod.CustomDeserializer.class)
    public enum PaymentMethod {
        CARD("CRD"), CASH("CSH");

        private final String code;

        PaymentMethod(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        public static PaymentMethod fromCode(String code) {
            if (code == null) return null;
            if (code.isEmpty()) return null;
            for (PaymentMethod type : PaymentMethod.values()) {
                if (type.code.equals(code)) return type;
            }
            throw new IllegalArgumentException("Unknown PaymentMethod code: " + code);
        }

        public static class CustomSerializer extends JsonSerializer<PaymentMethod> {
            @Override
            public void serialize(PaymentMethod value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.getCode());
            }
        }

        public static class CustomDeserializer extends JsonDeserializer<PaymentMethod> {
            @Override
            public PaymentMethod deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return PaymentMethod.fromCode(p.getText());
            }
        }
    }

    public static class Payment {
        public PaymentMethod pay;
    }

    public static class Order {
        public List<Payment> payments;
    }

    @RestController
    public static class Orders {
        @PostMapping("/order")
        public String order(@RequestBody Order order) {
            return "ok";
        }
    }

    @Test
    public void anUnknownCodeIsTheEnumsOwnMessage() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Orders())
                .setControllerAdvice(new RequestShapeAdvice(new DefaultListableBeanFactory().getBeanProvider(MessageSource.class)))
                .build();
        mvc.perform(post("/order").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"pay\":\"CRD\"},{\"pay\":\"DINERS\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("payments[1].pay"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("typeMismatch"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Unknown PaymentMethod code: DINERS"));
    }
}
