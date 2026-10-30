package dddmybatis;

import io.elasticore.sample.dddmybatis.shop.application.dto.OrderShipCommand;
import io.elasticore.sample.dddmybatis.shop.application.port.in.OrderUseCase;
import io.elasticore.sample.dddmybatis.shop.domain.model.OrderBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A command declared in the DSL is one way in, from the edge to the rule:
 *
 *   commands:
 *     ship:
 *       trackingNumber: string(50)!
 *     cancel:
 *
 * The endpoint, the command DTO, the use case and the transaction are
 * generated; the rule is the aggregate's, declared abstract in the base so a
 * command nobody wrote does not compile. This sample regenerates the owned
 * Order class on every build, so its rule is the throwing stub - the ERP
 * trial projects are where the rules are written and exercised.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BusinessCommandTest {

    private static final String ORDERS = "/api/v1/shop/order";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("the aggregate promises each command, and the rule is its own to write")
    void theBaseDeclaresTheCommandsAbstract() throws Exception {
        Method ship = OrderBase.class.getDeclaredMethod("ship", String.class);
        Method cancel = OrderBase.class.getDeclaredMethod("cancel");
        assertThat(Modifier.isAbstract(ship.getModifiers())).isTrue();
        assertThat(Modifier.isAbstract(cancel.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("the inbound port offers the commands next to the CRUD surface")
    void theUseCaseOffersTheCommands() throws Exception {
        assertThat(OrderUseCase.class.getMethod("ship", String.class, OrderShipCommand.class)).isNotNull();
        assertThat(OrderUseCase.class.getMethod("cancel", String.class)).isNotNull();
    }

    @Test
    @DisplayName("a command's parameters are validated like any request body")
    void aCommandIsValidated() throws Exception {
        mvc.perform(post(ORDERS + "/any-order/ship")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a command for an order that does not exist is a 404")
    void aCommandForNothingIsNotFound() throws Exception {
        mvc.perform(post(ORDERS + "/no-such-order/cancel"))
                .andExpect(status().isNotFound());
        mvc.perform(post(ORDERS + "/no-such-order/ship")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingNumber\":\"TRK-1\"}"))
                .andExpect(status().isNotFound());
    }
}
