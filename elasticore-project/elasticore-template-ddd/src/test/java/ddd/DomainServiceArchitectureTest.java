package ddd;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import io.elasticore.sample.ddd.shop.domain.service.PricingService;
import io.elasticore.sample.ddd.shop.domain.service.WebAwarePricing;
import io.elasticore.sample.ddd.shop.interfaces.rest.OrderController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * namespace.domainService stays after domainService: was removed (3.1, issue
 * 370): the generated architecture test reads it as a domain package, so a
 * class written by hand there is checked as domain code.
 *
 * The rules are the generated ShopArchitectureTest's own, read from it, and
 * run on one class at a time: the hand-written PricingService passes, and a
 * class in the same package that names a controller fails.
 */
class DomainServiceArchitectureTest {

    @Test
    @DisplayName("a hand-written class in domain.service that names a controller fails the architecture test")
    void aClassThatNamesAControllerFails() throws Exception {
        JavaClasses classes = new ClassFileImporter().importClasses(WebAwarePricing.class);
        ArchRule rule = generatedRule("domainDependsOnNothingOutside");

        EvaluationResult result = rule.evaluate(classes);
        assertThat(result.hasViolation()).isTrue();
        assertThat(String.join("\n", result.getFailureReport().getDetails()))
                .contains(WebAwarePricing.class.getName())
                .contains(OrderController.class.getName());
        assertThatThrownBy(() -> rule.check(classes)).isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("the hand-written domain service, which takes only the domain's ports, passes it")
    void theDomainServicePasses() throws Exception {
        JavaClasses classes = new ClassFileImporter().importClasses(PricingService.class);
        for (String name : new String[] {"domainDependsOnNothingOutside", "domainNamesNoWeb"}) {
            assertThat(generatedRule(name).evaluate(classes).hasViolation()).as(name).isFalse();
        }
    }

    /** A rule of the generated test, which is package-private where it was written. */
    private static ArchRule generatedRule(String name) throws Exception {
        Field field = Class.forName("io.elasticore.sample.ddd.shop.ShopArchitectureTest").getDeclaredField(name);
        field.setAccessible(true);
        return (ArchRule) field.get(null);
    }
}
