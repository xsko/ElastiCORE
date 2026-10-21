package io.elasticore.springboot3.validation;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.elasticore.springboot3.domain.Quantity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What Spring MVC refuses before a controller method runs, answered in the
 * API's problem body with a code - by the one runtime advice the generated
 * handlers import, for any controller. Run on Jackson 2 by
 * elasticore-springboot3 and on Jackson 3 by elasticore-springboot4.
 */
public class RequestShapeErrorsTest {

    public enum Size3 { SMALL, LARGE }

    /** An enum read by its code, the way a generated enum with a json field is on Jackson 3 (and here on 2 as well). */
    public enum CardType {
        VISA("V"), MASTER("M");

        private final String code;

        CardType(String code) {
            this.code = code;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static CardType fromCode(String code) {
            for (CardType t : values()) {
                if (t.code.equals(code)) return t;
            }
            throw new IllegalArgumentException("Unknown CardType code: " + code);
        }
    }

    public static class Line {
        public long itemId;
        public Quantity quantity;
    }

    public static class Order {
        public List<Line> lines;
        public int count;
        public Size3 size;
        public CardType card;
        @Size(max = 3) public String note;
    }

    /** A controller written by hand, outside every generated package. */
    @RestController
    public static class Hand {
        @PostMapping("/order")
        public String order(@Valid @RequestBody Order order) {
            return "ok";
        }

        @GetMapping("/item/{id}")
        public Line item(@PathVariable("id") long id) {
            Line line = new Line();
            line.itemId = id;
            return line;
        }

        @GetMapping("/page")
        public String page(@RequestParam("number") int number) {
            return "ok";
        }

        @GetMapping("/sized")
        public String sized(@RequestParam("number") @Min(1) int number) {
            return "ok";
        }
    }

    private static LocalValidatorFactoryBean validator;
    private static MockMvc mvc;

    @BeforeAll
    static void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        RequestShapeAdvice errors = new RequestShapeAdvice(
                new DefaultListableBeanFactory().getBeanProvider(MessageSource.class));
        mvc = MockMvcBuilders.standaloneSetup(new Hand())
                .setControllerAdvice(errors)
                .setValidator(validator)
                .build();
    }

    @AfterAll
    static void close() {
        validator.close();
    }

    /** As an API client sends it. (Standalone MockMvc puts Jackson's XML converter first; Spring Boot puts it last.) */
    private static MockHttpServletRequestBuilder json(String body) {
        return post("/order").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    public void aBodyThatIsNotJsonIsUnreadable() throws Exception {
        mvc.perform(json("{\"lines\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("unreadable"))
                .andExpect(jsonPath("$.detail").value("The request body is missing or is not valid JSON."));
        // Broken inside a nested object, where Jackson noted the property it was in: still not JSON.
        mvc.perform(json("{\"lines\":[{\"itemId\":1,\"quantity\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unreadable"));
        mvc.perform(post("/order").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unreadable"));
    }

    /** Quantity.of refuses -1 while the body is read, before any validation: the field and the type's reason. */
    @Test
    public void aValueItsTypeRefusesIsAFieldError() throws Exception {
        mvc.perform(json("{\"lines\":[{\"itemId\":1,\"quantity\":-1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("invalid"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines[0].quantity"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("typeMismatch"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("a quantity cannot be negative: -1"));
        mvc.perform(json("{\"count\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("count"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be a number"));
        mvc.perform(json("{\"count\":99999999999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("count"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be a number"));
        mvc.perform(json("{\"size\":\"HUGE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be one of: SMALL, LARGE"));
        mvc.perform(json("{\"card\":\"DINERS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("card"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Unknown CardType code: DINERS"));
    }

    /** Index-free key: one translation for the field in every element of the list. */
    @Test
    public void aRefusedValueIsSaidInTheRequestsLanguage() throws Exception {
        mvc.perform(json("{\"lines\":[{\"itemId\":1,\"quantity\":-1}]}").header("Accept-Language", "ko"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("잘못된 요청"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("수량은 0 이상의 정수입니다"));
    }

    /**
     * Two domains: billing-messages has the shared codes in English and no
     * translation, sample-messages_ko translates error.unreadable. A Korean
     * request is said from the translation, not from the English file of the
     * domain that sorts first.
     */
    @Test
    public void aTranslationInOneDomainIsNotHiddenByAnotherDomainsEnglish() throws Exception {
        mvc.perform(json("{\"lines\":").header("Accept-Language", "ko"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unreadable"))
                .andExpect(jsonPath("$.detail").value("요청 본문이 없거나 JSON 이 아닙니다."));
        mvc.perform(json("{\"lines\":[{\"itemId\":1,\"quantity\":-1}]}").header("Accept-Language", "ko"))
                .andExpect(jsonPath("$.title").value("잘못된 요청"));
        // English, and a language nobody translated: the English wording.
        mvc.perform(json("{\"lines\":").header("Accept-Language", "en"))
                .andExpect(jsonPath("$.detail").value("The request body is missing or is not valid JSON."));
        mvc.perform(json("{\"lines\":").header("Accept-Language", "sv"))
                .andExpect(jsonPath("$.detail").value("The request body is missing or is not valid JSON."));
    }

    @Test
    public void aPathOrParameterOfTheWrongTypeOrMissing() throws Exception {
        mvc.perform(get("/item/abc").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("id"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("typeMismatch"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be a number"));
        mvc.perform(get("/page").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("number"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("NotNull"));
    }

    /** A parameter constraint (Spring's method validation) and a @Valid body of a hand-written controller. */
    @Test
    public void aConstraintOnAParameterOrABody() throws Exception {
        mvc.perform(get("/sized").param("number", "0").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("invalid"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("number"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("Min"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be greater than or equal to 1"));
        mvc.perform(json("{\"note\":\"far too long\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("note"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("Size"));
    }

    @Test
    public void aMethodAMediaTypeOrAPathTheApplicationDoesNotServe() throws Exception {
        mvc.perform(put("/order").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("method-not-allowed"))
                .andExpect(jsonPath("$.detail").value("Method PUT is not supported here."));
        mvc.perform(post("/order").contentType(MediaType.TEXT_PLAIN).accept(MediaType.APPLICATION_JSON).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("unsupported-media-type"));
        mvc.perform(get("/item/1").accept(MediaType.IMAGE_PNG))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value("not-acceptable"));
        mvc.perform(get("/nothing/here").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("not-found"))
                .andExpect(jsonPath("$.detail").value(containsString("/nothing/here")));
    }

    /** A browser asking for a page keeps Spring Boot's error page: the advice declines, the status stays. */
    @Test
    public void aBrowserKeepsTheErrorPage() throws Exception {
        String html = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
        mvc.perform(get("/nothing/here").header("Accept", html))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("\"code\""))));
        mvc.perform(put("/order").header("Accept", html).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().string(not(containsString("\"code\""))));
    }
}
