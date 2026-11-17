package io.elasticore.springboot3.mask;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.elasticore.springboot.autoconfigure.ElastiCoreMaskAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot 3's ObjectMapper writes a {@code @Mask} field masked and reads a request as it was sent. */
class MaskJacksonTest {

    public static class CustomerDTO {
        private Long customerId;

        @Mask(MaskType.PHONE)
        private String phone;

        @Mask(MaskType.NAME)
        private String name;

        private String email;

        public Long getCustomerId() {
            return customerId;
        }

        public void setCustomerId(Long customerId) {
            this.customerId = customerId;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(JacksonAutoConfiguration.class, ElastiCoreMaskAutoConfiguration.class));

    @Test
    void theApplicationsObjectMapperMasksTheMarkedFieldsOnly() {
        runner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            CustomerDTO dto = new CustomerDTO();
            dto.setCustomerId(7L);
            dto.setPhone("010-1234-5678");
            dto.setName("홍길동");
            dto.setEmail("hong@example.com");
            assertEquals(
                    "{\"customerId\":7,\"phone\":\"010-****-5678\",\"name\":\"홍*동\",\"email\":\"hong@example.com\"}",
                    mapper.writeValueAsString(dto));
            dto.setPhone(null);
            assertEquals(
                    "{\"customerId\":7,\"phone\":null,\"name\":\"홍*동\",\"email\":\"hong@example.com\"}",
                    mapper.writeValueAsString(dto));
            CustomerDTO read = mapper.readValue("{\"phone\":\"010-1234-5678\"}", CustomerDTO.class);
            assertEquals("010-1234-5678", read.getPhone());
        });
    }
}
