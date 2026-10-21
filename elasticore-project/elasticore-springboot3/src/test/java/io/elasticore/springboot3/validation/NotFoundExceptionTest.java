package io.elasticore.springboot3.validation;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the generated findById and delete throw for a row that is not there:
 * the generated API errors answer it as any EntityNotFoundException; where no
 * handler takes it, Spring still answers 404 - never the 500 an unhandled
 * EntityNotFoundException would be.
 */
public class NotFoundExceptionTest {

    @RestController
    public static class Items {
        @GetMapping("/item/1")
        public String item() {
            throw new NotFoundException("Item not found");
        }
    }

    @Test
    public void itIsAnEntityNotFoundException() {
        assertTrue(new NotFoundException("x") instanceof jakarta.persistence.EntityNotFoundException);
    }

    @Test
    public void withoutAHandlerItIsStillA404() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Items()).build();
        mvc.perform(get("/item/1")).andExpect(status().isNotFound());
    }
}
