package io.elasticore.springboot3.http;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A call to another system failed: 502, with or without a handler for it. */
public class PortCallExceptionTest {

    @RestController
    public static class Ship {
        @PostMapping("/ship")
        public String ship() {
            throw new PortCallException("The courier did not answer", new IOException("connection refused"));
        }
    }

    @Test
    public void itIsABadGateway() {
        IOException cause = new IOException("connection refused");
        PortCallException ex = new PortCallException("The courier did not answer", cause);
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        assertEquals("The courier did not answer", ex.getReason());
        assertSame(cause, ex.getCause());
        assertNull(ex.getCode());

        PortCallException said = new PortCallException("courier.down", "Courier {0} did not answer", "ACME");
        assertEquals("courier.down", said.getCode());
        assertEquals("Courier ACME did not answer", said.getReason());
        assertArrayEquals(new Object[]{"ACME"}, said.getArgs());

        PortCallException answered = new PortCallException("pet.CourierApiPort", 503, "{\"error\":\"busy\"}",
                "The courier is busy", null);
        assertEquals(Integer.valueOf(503), answered.getUpstreamStatus());
        assertEquals("pet.CourierApiPort", answered.getPort());
        assertEquals("{\"error\":\"busy\"}", answered.getUpstreamBody());
        assertEquals(HttpStatus.BAD_GATEWAY, answered.getStatusCode());
    }

    /** An application whose API errors were generated before it existed: still 502, by Spring's own resolver. */
    @Test
    public void withoutAHandlerItIsStillA502() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Ship()).build();
        mvc.perform(post("/ship")).andExpect(status().isBadGateway());
    }
}
