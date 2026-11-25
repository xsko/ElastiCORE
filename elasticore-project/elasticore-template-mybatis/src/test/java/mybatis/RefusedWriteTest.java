package mybatis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.elasticore.sample.mybatis.shop.application.CustomerService;
import io.elasticore.sample.mybatis.shop.domain.model.Customer;
import io.elasticore.sample.mybatis.shop.infrastructure.persistence.ShopRepositoryHelper;
import io.elasticore.springboot3.validation.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue 344: a refused update leaves no trace in what its transaction reads. The update is mapped onto the row
 * MyBatis handed out - the object its session cache keeps - and then refused by beforeSave; a read of the same
 * row later in the same transaction (one test transaction: a batch, a caller that catches the refusal) answers
 * the stored value, not the refused one.
 */
// A database of its own: this context (with the refusing service) runs the schema script too.
@SpringBootTest(classes = MyBatisSampleApp.class,
        properties = "spring.datasource.url=jdbc:h2:mem:refusedwrite;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(RefusedWriteTest.Refusing.class)
@Transactional
class RefusedWriteTest {

    private static final String API = "/api/shop/customer";

    /** A beforeSave that refuses one name, as an application's rule refuses a change. */
    public static class RefusingCustomerService extends CustomerService {
        public RefusingCustomerService(ShopRepositoryHelper helper) {
            super(helper);
        }

        @Override
        protected void beforeSave(Customer customer) {
            if ("REFUSED CHANGE".equals(customer.getName()))
                throw new ConflictException("refused", "this name is refused");
        }
    }

    @TestConfiguration
    static class Refusing {
        @Bean
        @Primary
        RefusingCustomerService refusingCustomerService(ShopRepositoryHelper helper) {
            return new RefusingCustomerService(helper);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void aRefusedUpdateIsNotReadBackInTheSameTransaction() throws Exception {
        String created = mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"original\",\"email\":\"" + UUID.randomUUID() + "@a.com\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("customerId").asLong();
        // Read once, so the row is in the session's cache.
        assertEquals("original", name(id));

        mvc.perform(put(API).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":" + id + ",\"name\":\"REFUSED CHANGE\"}"))
                .andExpect(status().isConflict());

        assertEquals("original", name(id), "the refused change is not what the transaction reads");
    }

    private String name(long id) throws Exception {
        String body = mvc.perform(get(API + "/" + id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode row = json.readTree(body);
        return row.get("name").asText();
    }
}
