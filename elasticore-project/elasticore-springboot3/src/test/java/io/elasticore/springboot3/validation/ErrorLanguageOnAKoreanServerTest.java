package io.elasticore.springboot3.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One answer in one language, on a server whose machine language is Korean.
 *
 * This class runs in a JVM of its own started with -Duser.language=ko
 * -Duser.country=KR (the koreanServerTest task): the JDK and Hibernate
 * Validator cache bundles by locale, so the machine language cannot be
 * switched in the middle of a run. Each request is made the way Spring MVC
 * makes it on such a server: a container that answers ko_KR for a request
 * without Accept-Language, the resolver Spring Boot configures, and the
 * locale it resolved in LocaleContextHolder while @Valid ran.
 */
public class ErrorLanguageOnAKoreanServerTest {

    /** A sign-up form as the generator writes one: built-in messages, and a DSL sentence. */
    public static class SignUp {
        @Size(max = 10) public String login = "far-too-long-login";
        @Size(max = 10) public String nickname = "far-too-long-nick";
        @NotBlank public String name = " ";
        @DecimalMin("0.5") public BigDecimal rate = new BigDecimal("0.1");
        @Email(message = "Email format invalid") public String email = "no-at-sign";
    }

    private static LocalValidatorFactoryBean validator;

    private final ErrorMessages messages = new ErrorMessages(null, "i18n/sample-messages");

    @BeforeAll
    static void onAKoreanMachine() {
        assertEquals("ko", Locale.getDefault().getLanguage(),
                "run by the koreanServerTest task, on a JVM started with -Duser.language=ko");
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        BeanValidation.use(validator);
    }

    @AfterAll
    static void close() {
        validator.close();
    }

    @AfterEach
    void reset() {
        LocaleContextHolder.resetLocaleContext();
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * A request on a Korean container: Spring's resolver, the header if any,
     * and the field errors @Valid produced at the locale the resolver chose.
     */
    private BeanPropertyBindingResult request(String acceptLanguage, LocaleResolver resolver) {
        MockHttpServletRequest request = korean();
        if (acceptLanguage != null) request.addHeader("Accept-Language", acceptLanguage);
        if (resolver != null) request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, resolver);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        LocaleContextHolder.setLocale(resolver != null ? resolver.resolveLocale(request) : request.getLocale());
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new SignUp(), "signUp");
        validator.validate(result.getTarget(), result);
        return result;
    }

    /**
     * What a container on this machine hands Spring for a request without
     * Accept-Language: getLocale() is the machine's ko_KR, and no header.
     * (MockHttpServletRequest writes the header for the locales it is given,
     * so it is taken out again.)
     */
    private static MockHttpServletRequest korean() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setPreferredLocales(Collections.singletonList(Locale.KOREA));
        request.removeHeader("Accept-Language");
        return request;
    }

    private String said(BeanPropertyBindingResult result, String field) {
        FieldError error = result.getFieldError(field);
        return messages.text(error);
    }

    private static boolean hangul(String s) {
        for (char c : s.toCharArray()) {
            if (c >= '가' && c <= '힣') return true;
        }
        return false;
    }

    @Test
    public void noHeaderIsTheBundlesOwnWordingNotTheMachines() {
        BeanPropertyBindingResult r = request(null, new AcceptHeaderLocaleResolver());
        assertTrue(hangul(r.getFieldError("nickname").getDefaultMessage()),
                "the premise: Bean Validation said it in the machine's language");
        assertEquals("size must be between 0 and 10", said(r, "nickname"));
        assertEquals("must be greater than or equal to 0.5", said(r, "rate"));
        // The application's own ValidationMessages.properties, not its _ko beside it.
        assertEquals("USER-EN not blank", said(r, "name"));
        // A DSL sentence is said as written.
        assertEquals("Email format invalid", said(r, "email"));
        assertEquals("Not found", messages.text("error.not-found.title", "x"));
        assertEquals(Locale.ROOT, messages.language());
    }

    /** spring.web.locale: ko - the one setting for the default, for DSL texts and built-ins alike. */
    @Test
    public void theDefaultLanguageIsSpringWebLocale() {
        AcceptHeaderLocaleResolver korean = new AcceptHeaderLocaleResolver();
        korean.setDefaultLocale(Locale.KOREAN);
        BeanPropertyBindingResult r = request(null, korean);
        String nickname = said(r, "nickname");
        assertTrue(hangul(nickname) && nickname.contains("10"), nickname);
        assertEquals("없음", messages.text("error.not-found.title", "x"));
        // Accept-Language still overrides it; English is the bundles' own wording.
        BeanPropertyBindingResult english = request("en", korean);
        assertEquals("size must be between 0 and 10", said(english, "nickname"));
        assertEquals("Not found", messages.text("error.not-found.title", "x"));
        // A language nobody translated gets the default, Korean here.
        request("sv", korean);
        assertEquals("없음", messages.text("error.not-found.title", "x"));
    }

    @Test
    public void aLanguageNobodyTranslatedIsTheDefaultNotTheMachines() {
        BeanPropertyBindingResult swedish = request("sv", new AcceptHeaderLocaleResolver());
        assertEquals("size must be between 0 and 10", said(swedish, "nickname"));
        assertEquals("Not found", messages.text("error.not-found.title", "x"));
        // Hibernate Validator has French, the application has not: one language per answer.
        BeanPropertyBindingResult french = request("fr", new AcceptHeaderLocaleResolver());
        assertEquals("size must be between 0 and 10", said(french, "nickname"));
    }

    /** The first language the header names that the application translated. */
    @Test
    public void theFirstTranslatedLanguageOfTheHeaderWins() {
        BeanPropertyBindingResult r = request("de;q=0.9, ko;q=0.8", new AcceptHeaderLocaleResolver());
        String nickname = said(r, "nickname");
        assertTrue(hangul(nickname) && nickname.contains("10"), nickname);
        assertEquals("없음", messages.text("error.not-found.title", "x"));
        assertEquals("ko", messages.language().getLanguage());

        request("ko-KR", new AcceptHeaderLocaleResolver());
        assertEquals("없음", messages.text("error.not-found.title", "x"));
    }

    /** A code translation (Size.login, with Spring's values: {1} is max) wins over the built-in message. */
    @Test
    public void aTranslationByCodeWins() {
        BeanPropertyBindingResult r = request("ko", new AcceptHeaderLocaleResolver());
        assertEquals("로그인은 10자까지", said(r, "login"));
    }

    /**
     * English is the bundles' own wording (Locale.ROOT): any other English
     * locale lets an application's ValidationMessages_ko, and a key only its
     * MessageSource's _ko file has, answer in Korean on this machine - both
     * fall back to the machine's language for a locale they have no file for.
     */
    @Test
    public void englishNeverFallsBackToTheMachinesLanguage() {
        ResourceBundleMessageSource application = new ResourceBundleMessageSource();
        application.setBasenames("app/application-messages");
        application.setDefaultEncoding("UTF-8");
        assertEquals("PARENT-KO", application.getMessage("error.parent.title", null, "none", Locale.ENGLISH),
                "the premise: Spring Boot's MessageSource falls back to the machine's language");
        ErrorMessages withApplication = new ErrorMessages(application, "i18n/sample-messages");

        BeanPropertyBindingResult r = request("en", new AcceptHeaderLocaleResolver());
        assertTrue(hangul(r.getFieldError("nickname").getDefaultMessage())
                        || r.getFieldError("name").getDefaultMessage().contains("USER-KO"),
                "the premise: @Valid said it in Korean for en");
        assertEquals("USER-EN not blank", withApplication.text(r.getFieldError("name")));
        assertEquals("size must be between 0 and 10", withApplication.text(r.getFieldError("nickname")));
        assertEquals("default", withApplication.text("error.parent.title", "default"));

        // spring.web.locale: en is the same English.
        AcceptHeaderLocaleResolver english = new AcceptHeaderLocaleResolver();
        english.setDefaultLocale(Locale.ENGLISH);
        BeanPropertyBindingResult byDefault = request(null, english);
        assertEquals("USER-EN not blank", withApplication.text(byDefault.getFieldError("name")));
        assertEquals("default", withApplication.text("error.parent.title", "default"));

        // Korean asked for: the application's Korean, from both.
        BeanPropertyBindingResult korean = request("ko", new AcceptHeaderLocaleResolver());
        assertEquals("USER-KO not blank", withApplication.text(korean.getFieldError("name")));
        assertEquals("PARENT-KO", withApplication.text("error.parent.title", "default"));
    }

    /** The service-side check: a ConstraintViolation, translated by the same key and values as @Valid. */
    @Test
    public void aServiceSideViolationIsSaidTheSameWay() {
        request(null, new AcceptHeaderLocaleResolver());
        Set<ConstraintViolation<SignUp>> found = validator.validate(new SignUp());
        ConstraintViolation<SignUp> nickname = violation(found, "nickname");
        assertEquals("size must be between 0 and 10", messages.violation("Size.nickname", nickname, nickname.getMessage()));

        request("ko", new AcceptHeaderLocaleResolver());
        found = validator.validate(new SignUp());
        ConstraintViolation<SignUp> login = violation(found, "login");
        assertEquals("로그인은 10자까지", messages.violation("Size.login", login, login.getMessage()));
        ConstraintViolation<SignUp> email = violation(found, "email");
        assertEquals("Email format invalid", messages.violation("Email.email", email, email.getMessage()));
    }

    private static <T> ConstraintViolation<T> violation(Set<ConstraintViolation<T>> found, String field) {
        for (ConstraintViolation<T> v : found) {
            if (field.equals(v.getPropertyPath().toString())) return v;
        }
        throw new AssertionError("no violation of " + field + " in " + found);
    }

    /** A session the application keeps the language in: today's rule, kept. */
    @Test
    public void aSessionLanguageIsTheApplicationsChoice() {
        SessionLocaleResolver session = new SessionLocaleResolver();
        // Nothing chosen: the resolver answered the container's locale - nobody chose it.
        request(null, session);
        assertEquals("Not found", messages.text("error.not-found.title", "x"));

        MockHttpServletRequest chose = new MockHttpServletRequest();
        chose.setPreferredLocales(Collections.singletonList(Locale.ENGLISH));
        chose.removeHeader("Accept-Language");
        chose.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, session);
        session.setLocale(chose, null, Locale.KOREAN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(chose));
        LocaleContextHolder.setLocale(session.resolveLocale(chose));
        assertEquals("없음", messages.text("error.not-found.title", "x"));
    }

    /** A bundle's base file is not a translation, and the machine's language is not one either. */
    @Test
    public void onlyAFileOfItsOwnIsATranslation() {
        assertTrue(ErrorMessages.hasOwnFile("i18n/sample-messages", Locale.KOREAN));
        assertFalse(ErrorMessages.hasOwnFile("i18n/sample-messages", new Locale("sv")));
        assertFalse(ErrorMessages.hasOwnFile("i18n/no-such-bundle", Locale.KOREAN));
    }
}
