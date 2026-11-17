package io.elasticore.springboot3.mask;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.elasticore.springboot.autoconfigure.ElastiCoreMaskAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

/** Spring Boot 4's JsonMapper (Jackson 3) writes a {@code @Mask} field masked and reads a request as it was sent. */
class MaskJackson3Test {

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
    void theApplicationsJsonMapperMasksTheMarkedFieldsOnly() {
        runner.run(context -> {
            JsonMapper mapper = context.getBean(JsonMapper.class);
            CustomerDTO dto = new CustomerDTO();
            dto.setCustomerId(7L);
            dto.setPhone("010-1234-5678");
            dto.setName("홍길동");
            dto.setEmail("hong@example.com");
            String json = mapper.writeValueAsString(dto);
            assertEquals("010-****-5678", mapper.readTree(json).get("phone").asString());
            assertEquals("홍*동", mapper.readTree(json).get("name").asString());
            assertEquals("hong@example.com", mapper.readTree(json).get("email").asString());
            assertEquals(7L, mapper.readTree(json).get("customerId").asLong());
            CustomerDTO read = mapper.readValue("{\"phone\":\"010-1234-5678\"}", CustomerDTO.class);
            assertEquals("010-1234-5678", read.getPhone());
        });
    }
}
