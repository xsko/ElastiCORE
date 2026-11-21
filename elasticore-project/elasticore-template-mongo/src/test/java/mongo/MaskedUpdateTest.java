package mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.elasticore.sample.mongo.shop.application.CustomerService;
import io.elasticore.sample.mongo.shop.application.dto.CustomerDTO;
import io.elasticore.sample.mongo.shop.domain.model.Customer;
import io.elasticore.sample.mongo.shop.infrastructure.persistence.CustomerRepository;
import io.elasticore.sample.mongo.shop.infrastructure.persistence.ProductRepository;
import io.elasticore.sample.mongo.shop.infrastructure.persistence.PurchaseOrderRepository;
import io.elasticore.sample.mongo.shop.infrastructure.persistence.ShopRepositoryHelper;
import io.elasticore.springboot3.mask.MaskType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Issue 270 over the generated MongoDB service, without a database: Customer.email is @Mask(EMAIL), and the update
 * compares the text sent with the stored value masked. The same over HTTP and a real MongoDB is
 * ShopApi.aMaskedFieldSentBackAsShownKeepsTheStoredValue, where Docker or a server is at hand.
 */
class MaskedUpdateTest {

    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final CustomerService service = new CustomerService(new ShopRepositoryHelper(
            customers, mock(ProductRepository.class), mock(PurchaseOrderRepository.class)));

    private Customer stored(String email) {
        Customer c = new Customer();
        c.setCustomerId(7L);
        c.setName("Kim");
        c.setEmail(email);
        when(customers.findById(7L)).thenReturn(Optional.of(c));
        when(customers.save(any(Customer.class))).thenAnswer(call -> call.getArgument(0));
        return c;
    }

    private static CustomerDTO sent(String email) {
        CustomerDTO dto = new CustomerDTO();
        dto.setCustomerId(7L);
        dto.setName("Kim renamed");
        dto.setEmail(email);
        return dto;
    }

    @Test
    void theMaskedTextSentBackKeepsTheStoredValue() {
        Customer c = stored("keeper@example.com");
        assertEquals("ke****@example.com", MaskType.EMAIL.apply("keeper@example.com"));
        service.update(sent("ke****@example.com"));
        assertEquals("keeper@example.com", c.getEmail());
        assertEquals("Kim renamed", c.getName());

        service.batchUpdate(List.of(sent("ke****@example.com")));
        assertEquals("keeper@example.com", c.getEmail());
    }

    @Test
    void anyOtherTextIsWritten() {
        Customer c = stored("keeper@example.com");
        service.update(sent("moved@example.com"));
        assertEquals("moved@example.com", c.getEmail());

        // The masked text of the old address is not the new one masked: written as sent.
        service.update(sent("ke****@example.com"));
        assertEquals("ke****@example.com", c.getEmail());
    }

    @Test
    void withoutAStoredValueTheTextIsWritten() {
        Customer c = stored(null);
        service.update(sent("ke****@example.com"));
        assertEquals("ke****@example.com", c.getEmail());
    }
}
