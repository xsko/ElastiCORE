package io.elasticore.springboot3.validation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.text.MessageFormat;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The field, the entity and the state a generated refusal names, said by
 * their labels in the one language the response is in (issue 274): the
 * label bundle's file of that language (i18n/crm-labels_ko), _en for
 * English - never the default file, which holds the DSL's "--" text - else
 * the name. And the values reach only a sentence that holds a placeholder.
 */
public class RefusalLabelsTest {

    enum Stage {
        OPEN,
        WON,
        LOST
    }

    private final ErrorMessages messages = new ErrorMessages(null, "i18n/crm-messages");

    @AfterEach
    void reset() {
        LocaleContextHolder.resetLocaleContext();
        RequestContextHolder.resetRequestAttributes();
    }

    /** What the generated invariant listener throws for annualRevenue @min(0). */
    private String minimum() {
        InvariantViolationException ex = new InvariantViolationException(
                "invariant.Account.annualRevenue.min",
                "annualRevenue must be at least 0",
                Label.of("Account.annualRevenue"),
                Label.of("Account"));
        return messages.text(ex.getCode(), ex.getMessage(), ex.getArgs());
    }

    /** What the generated mover throws for win, refused in a state: the codes first, then the labels. */
    private String step(String command, Stage now, Stage... from) {
        return messages.text(
                "state.Opportunity.stage." + command,
                "default",
                now.name(),
                java.util.Arrays.toString(from),
                Label.of("Opportunity.stage"),
                Label.of("Opportunity"),
                Label.of("OpportunityStage", now),
                Label.listOf("OpportunityStage", from));
    }

    @Test
    public void englishSaysTheEnglishLabelElseTheNameNeverTheDashText() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertEquals("Annual revenue must be at least 0", minimum());
        // Opportunity and stage have only "--" labels (Korean): their names. LOST has no _en label.
        assertEquals(
                "Opportunity cannot win when stage is LOST; it must be [Open]", step("win", Stage.LOST, Stage.OPEN));
        assertEquals(
                "Opportunity cannot lose when stage is Won; it must be [Open, LOST]",
                step("lose", Stage.WON, Stage.OPEN, Stage.LOST));
    }

    @Test
    public void anotherLanguageSaysItsOwnFile() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertEquals("거래처의 연 매출은(는) 0 이상이어야 합니다", minimum());
        assertEquals("영업 기회의 단계이(가) 수주이면 실주할 수 없습니다 (가능한 상태: [진행 중])", step("lose", Stage.WON, Stage.OPEN));
        LocaleContextHolder.setLocale(Locale.KOREA);
        assertEquals("거래처의 연 매출은(는) 0 이상이어야 합니다", minimum());
    }

    /** A translation of a state.* sentence written before the labels: {0} and {1} keep their meaning. */
    @Test
    public void anExistingStateTranslationKeepsTheCodes() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertEquals("단계가 LOST이면 수주할 수 없습니다 (가능한 상태: [OPEN])", step("win", Stage.LOST, Stage.OPEN));
    }

    /** Japanese is translated (crm-messages_ja) but has no label file: the names, not the "--" text. */
    @Test
    public void aLanguageWithoutLabelsSaysTheNames() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        assertEquals("annualRevenueは0以上です", minimum());
    }

    /** One language per response: the one the request chose, labels included. */
    @Test
    public void theLabelsFollowTheLanguageTheRequestChose() {
        assertEquals("거래처의 연 매출은(는) 0 이상이어야 합니다", minimumFor("fr, ko;q=0.8"));
        assertEquals("Annual revenue must be at least 0", minimumFor("en"));
        // Nobody translated Swedish: the bundles' own wording, labels in English.
        assertEquals("Annual revenue must be at least 0", minimumFor("sv"));
        assertEquals("Annual revenue must be at least 0", minimumFor(null));
    }

    private String minimumFor(String acceptLanguage) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (acceptLanguage != null) request.addHeader("Accept-Language", acceptLanguage);
        request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, new AcceptHeaderLocaleResolver());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        LocaleContextHolder.setLocale(request.getLocale());
        return minimum();
    }

    /**
     * A refusal that carried values before labels - state.*, a hand-written
     * code with values - is a MessageFormat pattern as it always was, with or
     * without a placeholder: '' is an apostrophe.
     */
    @Test
    public void aRefusalThatHadValuesIsAPatternAsBefore() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertEquals("지금은 다시 열 수 없습니다 (can't)", step("reopen", Stage.WON, Stage.LOST));
        ConflictException handWritten = new ConflictException("deal.closed", "Deal {0} is closed", "D-1");
        assertEquals(
                "닫힌 거래는 바꿀 수 없습니다 (can't)",
                messages.text(handWritten.getCode(), handWritten.getMessage(), handWritten.getArgs()));
        // What the same call said before labels: Spring's MessageFormat with the codes alone.
        org.springframework.context.support.ResourceBundleMessageSource before =
                new org.springframework.context.support.ResourceBundleMessageSource();
        before.setBasenames("i18n/crm-messages");
        before.setDefaultEncoding("UTF-8");
        before.setFallbackToSystemLocale(false);
        assertEquals(
                before.getMessage("state.Opportunity.stage.reopen", new Object[] {"WON", "[LOST]"}, Locale.KOREAN),
                step("reopen", Stage.WON, Stage.LOST));
    }

    /**
     * A refusal whose values are all labels - an invariant's, which had none
     * before - passes them only to a sentence that holds a '{': a translation
     * without a placeholder is said as written, its apostrophe kept; one with
     * placeholders is a MessageFormat pattern, '' an apostrophe.
     */
    @Test
    public void onlyASentenceWithAPlaceholderIsAPattern() {
        Object[] values = {Label.of("Account.name"), Label.of("Account")};
        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertEquals("거래처 이름은 x일 수 없습니다 (can't)", messages.text("invariant.Account.name-ne-x", "x", values));
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertEquals("An account can't be named x", messages.text("invariant.Account.name-ne-x", "x", values));
        assertEquals(
                "Annual revenue can't be over 1000",
                messages.text(
                        "invariant.Account.annualRevenue.max",
                        "x",
                        Label.of("Account.annualRevenue"),
                        Label.of("Account")));
        // No bundle has the code: the default as given, values or not.
        assertEquals("annualRevenue can't be 0", messages.text("no.such.code", "annualRevenue can't be 0", values));
    }

    /** A default pattern formatted where no bundle has the code says its labels too. */
    @Test
    public void aDefaultPatternSaysTheLabels() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertEquals(
                "Annual revenue is wrong",
                messages.format("no.such.code", "{0} is wrong", Label.of("Account.annualRevenue")));
    }

    /** Anywhere else the value is its name: a log, a MessageFormat, another MessageSource. */
    @Test
    public void elsewhereALabelIsItsName() {
        assertEquals("annualRevenue", Label.of("Account.annualRevenue").toString());
        assertEquals("Account", Label.of("Account").toString());
        assertEquals(
                "[OPEN, WON]",
                Label.listOf("OpportunityStage", Stage.OPEN, Stage.WON).toString());
        // As Arrays.toString and Kotlin's joinToString(" or ") say the same states: byte for byte.
        assertEquals(
                java.util.Arrays.toString(new Stage[] {Stage.OPEN, Stage.WON}),
                Label.listOf("OpportunityStage", Stage.OPEN, Stage.WON).toString());
        assertEquals(
                "OPEN or WON",
                Label.anyOf("OpportunityStage", Stage.OPEN, Stage.WON).toString());
        assertEquals("[]", Label.listOf("OpportunityStage").toString());
        assertEquals("null", Label.of("OpportunityStage", null).toString());
        assertEquals("", Label.anyOf("OpportunityStage").toString());
        assertEquals(
                "annualRevenue must be at least 0",
                MessageFormat.format("{0} must be at least 0", Label.of("Account.annualRevenue")));

        assertArrayEquals(
                new String[] {"Account.annualRevenue"},
                Label.of("Account.annualRevenue").getCodes());
        assertNull(Label.anyOf("OpportunityStage", Stage.OPEN, Stage.WON).getCodes());
        assertNull(Label.listOf("OpportunityStage", Stage.OPEN).getCodes());
        assertArrayEquals(
                new String[] {"OpportunityStage.OPEN"},
                Label.of("OpportunityStage", Stage.OPEN).getCodes());
        assertNotEquals(Label.listOf("OpportunityStage", Stage.OPEN), Label.anyOf("OpportunityStage", Stage.OPEN));
        assertEquals(Label.of("Account"), Label.of("Account"));
        assertEquals(Label.of("Account").hashCode(), Label.of("Account").hashCode());

        StaticMessageSource application = new StaticMessageSource();
        application.addMessage("Account.annualRevenue", Locale.ENGLISH, "Revenue (app)");
        application.addMessage("limit", Locale.ENGLISH, "{0} too high");
        assertEquals(
                "Revenue (app) too high",
                application.getMessage("limit", new Object[] {Label.of("Account.annualRevenue")}, Locale.ENGLISH));
        assertEquals(
                "Account too high",
                application.getMessage("limit", new Object[] {Label.of("Account")}, Locale.ENGLISH));
    }

    /** A bundle that is not a domain's (no -messages ending) has no label bundle; the names are said. */
    @Test
    public void withoutALabelBundleTheNamesAreSaid() {
        ErrorMessages plain = new ErrorMessages(null, "app/application-messages");
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertEquals("annualRevenue", plain.label(Label.of("Account.annualRevenue"), Locale.ROOT));
        assertEquals(
                "annualRevenue",
                new ErrorMessages(null, "i18n/none-messages").label(Label.of("Account.annualRevenue"), Locale.KOREAN));
    }
}
