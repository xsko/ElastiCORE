package io.elasticore.springboot3.validation;

import io.elasticore.springboot3.bean.ApplicationContextProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The request-shape advice is there only where the generated ApiErrors
 * imports it. A 2.x application that followed the old guide and scans
 * io.elasticore.springboot3 gets what that scan always gave it
 * (ApplicationContextProvider, DbTransactionGateway) and not the advice.
 */
public class RequestShapeErrorsRegistrationTest {

    /** The old guide's advice, narrowed to the package the advice lives in and without this module's tests. */
    @Configuration
    @ComponentScan(basePackages = "io.elasticore.springboot3.validation",
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Test.*"))
    public static class TwoXApplication {
    }

    /** What two domains' generated ApiErrors do: each imports it. */
    @Configuration
    @Import(RequestShapeErrors.class)
    public static class ShopApiErrors {
    }

    @Configuration
    @Import(RequestShapeErrors.class)
    public static class BillingApiErrors {
    }

    @RestController
    public static class Hand {
        @PostMapping("/hand")
        public String take(@RequestBody Map<String, Object> body) {
            return "ok";
        }
    }

    @Configuration
    @EnableWebMvc
    @Import({ShopApiErrors.class, BillingApiErrors.class, Hand.class})
    public static class WebApplication {
    }

    @Test
    public void aComponentScanOfTheRuntimeFindsNoRequestShapeAdvice() {
        Set<String> found = new TreeSet<String>();
        for (BeanDefinition candidate : new ClassPathScanningCandidateComponentProvider(true)
                .findCandidateComponents("io.elasticore.springboot3")) {
            // The library's classes only, not this module's test fixtures.
            if (!candidate.getBeanClassName().contains("Test")) found.add(candidate.getBeanClassName());
        }
        // The scan works: the component a 2.x application always got from it.
        assertTrue(found.contains(ApplicationContextProvider.class.getName()), found.toString());
        for (String name : found) {
            assertFalse(name.contains("RequestShape"), "picked up by a component scan: " + name);
        }
    }

    @Test
    public void aScannedApplicationHasNoControllerAdviceOfTheRuntime() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TwoXApplication.class)) {
            assertTrue(context.getBeansWithAnnotation(ControllerAdvice.class).isEmpty(),
                    context.getBeansWithAnnotation(ControllerAdvice.class).keySet().toString());
        }
    }

    @Test
    public void importedByTwoDomainsItIsOneAdviceThatAnswers() throws Exception {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.setAllowBeanDefinitionOverriding(false);
        context.register(WebApplication.class);
        context.refresh();
        try {
            Map<String, Object> advice = context.getBeansWithAnnotation(ControllerAdvice.class);
            assertEquals(1, advice.size(), advice.keySet().toString());
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(post("/hand").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                            .content("{\"a\":"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("unreadable"));
        } finally {
            context.close();
        }
    }
}
