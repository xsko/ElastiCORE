package io.elasticore.springboot3.validation;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring Boot 4 with the web layer switched back to Jackson 2
 * (MappingJackson2HttpMessageConverter): a value of the wrong type is still a
 * field error with its path, and a body that is not JSON is still unreadable.
 * elasticore-springboot4 only; springboot3 is Jackson 2 throughout.
 */
@SuppressWarnings({"deprecation", "removal"})
public class Jackson2WebLayerOnBoot4Test {

    public static class Amount {
        final int value;

        private Amount(int value) {
            this.value = value;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Amount of(int value) {
            if (value < 0) throw new IllegalArgumentException("an amount cannot be negative: " + value);
            return new Amount(value);
        }
    }

    public static class Line {
        public long itemId;
        public Amount amount;
    }

    public static class Order {
        public List<Line> lines;
        public int count;
    }

    @RestController
    public static class Hand {
        @PostMapping("/order")
        public String order(@RequestBody Order order) {
            return "ok";
        }
    }

    private static final MockMvc MVC = MockMvcBuilders.standaloneSetup(new Hand())
            .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter())
            .setControllerAdvice(new RequestShapeAdvice(new DefaultListableBeanFactory().getBeanProvider(MessageSource.class)))
            .build();

    private static MockHttpServletRequestBuilder json(String body) {
        return post("/order").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    public void aValueOfTheWrongTypeIsAFieldError() throws Exception {
        MVC.perform(json("{\"count\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("count"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("typeMismatch"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be a number"));
        MVC.perform(json("{\"lines\":[{\"itemId\":1,\"amount\":-1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines[0].amount"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("an amount cannot be negative: -1"));
    }

    @Test
    public void aBodyThatIsNotJsonIsUnreadable() throws Exception {
        MVC.perform(json("{\"lines\":[{\"itemId\":1,\"amount\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unreadable"));
    }
}
