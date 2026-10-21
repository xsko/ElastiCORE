package io.elasticore.springboot3.validation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.validation.FieldError;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An API error in the caller's language: the request's locale, the domain's
 * bundle and a translation beside it, the application's own messages, and
 * the default the code carries when no one translated it.
 */
@ExtendWith(OutputCaptureExtension.class)
public class ErrorMessagesTest {

    private final ErrorMessages messages = new ErrorMessages(null, "i18n/sample-messages");

    @AfterEach
    void reset() {
        LocaleContextHolder.resetLocaleContext();
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * No Accept-Language: Spring's resolver answers the server machine's
     * locale (here Korean); the error is said in the default wording instead.
     */
    @Test
    public void aRequestThatNamesNoLanguageGetsTheDefaultWording() {
        Locale machine = Locale.getDefault();
        try {
            Locale.setDefault(Locale.KOREAN);
            LocaleContextHolder.setLocale(Locale.KOREAN);
            // What a container answers for a request without the header: its own locale.
            MockHttpServletRequest headerless = new MockHttpServletRequest() {
                @Override
                public Locale getLocale() {
                    return Locale.KOREAN;
                }
            };
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(headerless));
            assertEquals("Not found", messages.text("error.not-found.title", "x"));

            // No header, but the application chose Korean (a session, a cookie): kept.
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            assertEquals("없음", messages.text("error.not-found.title", "x"));

            MockHttpServletRequest korean = new MockHttpServletRequest();
            korean.addHeader("Accept-Language", "ko");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(korean));
            assertEquals("없음", messages.text("error.not-found.title", "x"));
        } finally {
            Locale.setDefault(machine);
        }
    }

    /** No messages.properties in the application: looked for once, and no warning on every error. */
    @Test
    public void aBundleThatIsNotThereIsNotReportedOnEveryLookup(CapturedOutput output) {
        LocaleContextHolder.setLocale(Locale.GERMAN);
        for (int i = 0; i < 3; i++) messages.text("error.not-found.title", "x");
        assertEquals(-1, output.getAll().indexOf("not found for MessageSource"), output.getAll());
    }

    @Test
    public void theRequestLanguageChoosesTheTranslation() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertEquals("주문 상태가 SHIPPED이면 배송할 수 없습니다 ([PLACED])",
                messages.text("state.Order.status.ship", "default", "SHIPPED", "[PLACED]"));

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertEquals("Order cannot ship when status is SHIPPED; it must be [PLACED]",
                messages.text("state.Order.status.ship", "default", "SHIPPED", "[PLACED]"));
    }

    /** A language no one translated gets the bundle's default, never the server machine's language. */
    @Test
    public void anUntranslatedLanguageGetsTheDefaultBundle() {
        Locale machine = Locale.getDefault();
        try {
            Locale.setDefault(Locale.KOREAN);
            LocaleContextHolder.setLocale(Locale.FRENCH);
            assertEquals("Not found", messages.text("error.not-found.title", "x"));
        } finally {
            Locale.setDefault(machine);
        }
    }

    @Test
    public void aTranslatorKeepsApostrophesOfAMessageWithoutValues() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertEquals("The total can't be negative", messages.text("invariant.Order.total-ge-0", "x"));
    }

    @Test
    public void noTranslationAnywhereIsTheDefaultAsWritten() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertEquals("Order {0} as is", messages.text("no.such.code", "Order {0} as is", "ignored"));
        assertEquals("plain", messages.text(null, "plain"));
    }

    @Test
    public void theApplicationsMessageSourceIsConsultedLast() {
        StaticMessageSource app = new StaticMessageSource();
        app.addMessage("custom.code", Locale.KOREAN, "앱 메시지");
        app.addMessage("error.not-found.title", Locale.KOREAN, "shadowed");
        ErrorMessages withApp = new ErrorMessages(app, "i18n/sample-messages");

        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertEquals("앱 메시지", withApp.text("custom.code", "x"));
        // The domain bundle's translation is found before the application's MessageSource.
        assertEquals("없음", withApp.text("error.not-found.title", "x"));
    }

    @Test
    public void aFieldErrorFallsBackToItsDefaultMessage() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        FieldError error = new FieldError("customerDTO", "login", null, false,
                new String[]{"NotNull.customerDTO.login", "NotNull"}, null, "must not be null");
        assertEquals("must not be null", messages.text(error));
    }

    /**
     * A refusal hand code throws with a message code is said like a generated
     * one: the code in the request's language, else its own wording with its values.
     */
    @Test
    public void aRefusalWithACodeIsTranslatedLikeAGeneratedOne() {
        ConflictException conflict = new ConflictException("state.Order.status.ship",
                "Order cannot ship when status is {0}; it must be {1}", "SHIPPED", "[PLACED]");
        assertEquals("Order cannot ship when status is SHIPPED; it must be [PLACED]", conflict.getMessage());
        assertEquals("state.Order.status.ship", conflict.getCode());

        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertEquals("주문 상태가 SHIPPED이면 배송할 수 없습니다 ([PLACED])",
                messages.text(conflict.getCode(), conflict.getMessage(), conflict.getArgs()));
        LocaleContextHolder.setLocale(Locale.FRENCH);
        assertEquals("Order cannot ship when status is SHIPPED; it must be [PLACED]",
                messages.text(conflict.getCode(), conflict.getMessage(), conflict.getArgs()));

        // No code: the message as written, apostrophes and all.
        ConflictException plain = new ConflictException("The loan can't be returned twice");
        assertEquals("The loan can't be returned twice", messages.text(plain.getCode(), plain.getMessage(), plain.getArgs()));

        assertEquals("loan.missing", new NotFoundException("loan.missing", "Loan {0} does not exist", 7).getCode());
        assertEquals("Loan 7 does not exist", new NotFoundException("loan.missing", "Loan {0} does not exist", 7).getMessage());
        assertEquals("x", new BadRequestException("bad.x", "x").getMessage());
        assertEquals("review.writer", new io.elasticore.springboot3.security.ForbiddenException("review.writer", "Only the writer").getCode());
        assertEquals("Sign in", new io.elasticore.springboot3.security.NotSignedInException("Sign in").getMessage());
    }

    /**
     * Two domain bundles, only the second translated: a file for the
     * language wins over another bundle's base file, whatever the order the
     * bundles are looked in - with and without values, by code and by an
     * error's codes, for ko and ko_KR.
     */
    @Test
    public void aTranslationInTheSecondBundleWinsOverTheFirstBundlesEnglish() {
        ErrorMessages twoDomains = new ErrorMessages(null, "i18n/billing-messages", "i18n/sample-messages");
        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertEquals("잘못된 요청", twoDomains.text("error.invalid.title", "x"));
        assertEquals("요청 본문이 없거나 JSON 이 아닙니다.", twoDomains.text("error.unreadable", "x"));
        assertEquals("잘못된 요청", twoDomains.text(new org.springframework.context.support.DefaultMessageSourceResolvable(
                new String[]{"error.invalid.title"}, new Object[]{"with a value"}, "x")));
        LocaleContextHolder.setLocale(Locale.KOREA);
        assertEquals("잘못된 요청", twoDomains.text("error.invalid.title", "x"));
        // Nobody translated it: the first bundle's own wording, as before.
        assertEquals("Method PUT is not supported here.", twoDomains.text("error.method-not-allowed", "x", "PUT"));
        LocaleContextHolder.setLocale(Locale.FRENCH);
        assertEquals("Invalid request", twoDomains.text("error.invalid.title", "x"));
    }

    /** A default with values is formatted when no bundle has the code. */
    @Test
    public void aDefaultPatternIsFormattedWhenNoBundleHasIt() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertEquals("must be one of: A, B", messages.format("no.such.code", "must be one of: {0}", "A, B"));
        assertEquals("plain", messages.format("no.such.code", "plain"));
    }
}
