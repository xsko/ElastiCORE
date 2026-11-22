package ddd;

import io.elasticore.sample.ddd.shop.domain.model.Category;
import io.elasticore.sample.ddd.shop.domain.model.Shipment;
import io.elasticore.springboot3.validation.ErrorMessages;
import io.elasticore.springboot3.validation.InvariantViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * This model has no English label (issue 274): every refusal is answered in
 * English exactly as before the labels - the default bundle's sentence with
 * the values the runtime says equals the exception's own message, which is
 * the sentence the answer had, a lifecycle's state list in its brackets.
 */
class EnglishRefusalAsBeforeTest {

    private final ErrorMessages messages = new ErrorMessages(null, "i18n/shop-messages");

    @AfterEach
    void reset() {
        LocaleContextHolder.resetLocaleContext();
    }

    private String english(InvariantViolationException refused) {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        return messages.text(refused.getCode(), "not in the bundle", refused.getArgs());
    }

    @Test
    void aLifecycleRefusalReadsAsBefore() {
        Shipment sent = new Shipment();
        sent.send();
        InvariantViolationException move = catchThrowableOfType(sent::send, InvariantViolationException.class);
        assertThat(english(move)).isEqualTo("Shipment cannot send when status is SENT; it must be [READY]")
                .isEqualTo(move.getMessage());

        // One command of two lifecycles: the fee refuses.
        InvariantViolationException together =
                catchThrowableOfType(new Shipment()::cancel, InvariantViolationException.class);
        assertThat(english(together)).isEqualTo("Shipment cannot cancel when fee is DUE; it must be [PAID]")
                .isEqualTo(together.getMessage());
    }

    @Test
    void aBoundOfAFieldReadsAsBefore() throws Exception {
        Category category = new Category();
        category.setSortOrder(-1);
        InvariantViolationException below = check(category);
        assertThat(english(below)).isEqualTo("sortOrder must be at least 0").isEqualTo(below.getMessage());
        category.setSortOrder(1000);
        InvariantViolationException above = check(category);
        assertThat(english(above)).isEqualTo("sortOrder must be at most 999").isEqualTo(above.getMessage());
    }

    /** The generated listener's check, package-private as JPA calls it. */
    private static InvariantViolationException check(Category category) throws Exception {
        Class<?> invariants = Class.forName("io.elasticore.sample.ddd.shop.domain.model.CategoryInvariants");
        java.lang.reflect.Constructor<?> make = invariants.getDeclaredConstructor();
        make.setAccessible(true);
        Method check = invariants.getDeclaredMethod("check", Category.class);
        check.setAccessible(true);
        try {
            check.invoke(make.newInstance(), category);
        } catch (InvocationTargetException ex) {
            return (InvariantViolationException) ex.getCause();
        }
        throw new AssertionError("the bound did not refuse");
    }
}
