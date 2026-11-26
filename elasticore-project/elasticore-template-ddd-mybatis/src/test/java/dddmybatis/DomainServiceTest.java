package dddmybatis;

import io.elasticore.sample.dddmybatis.shop.domain.repository.OrderRepositoryPort;
import io.elasticore.sample.dddmybatis.shop.domain.repository.ProductRepositoryPort;
import io.elasticore.sample.dddmybatis.shop.domain.service.PricingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An operation that belongs to no aggregate, wired to the ones it spans.
 *
 * Written by hand (domainService: is no longer read since 3.1, issue 370): a
 * @Service in the domain.service package with the repository ports of what it
 * spans in its constructor. What is worth holding down is that the generated
 * ports wire into it, that they are the domain's own and not Spring Data, and
 * that the class lands in the domain - where the architecture test checks it.
 */
@SpringBootTest
class DomainServiceTest {

    @Autowired
    private PricingService pricing;

    @Test
    @DisplayName("the service is a bean, so the ports it takes resolve")
    void itIsWired() {
        assertThat(pricing).isNotNull();
    }

    @Test
    @DisplayName("what it spans is what it is given, and it is given ports")
    void itIsGivenThePortsOfWhatItSpans() {
        assertThat(given()).containsExactly(OrderRepositoryPort.class, ProductRepositoryPort.class);
    }

    /** A domain service is handed the domain's interface, not Spring Data. */
    @Test
    @DisplayName("nothing it is given knows Spring Data")
    void itIsGivenNoPersistenceMechanism() {
        for (Class<?> parameter : given()) {
            for (Class<?> inherited : parameter.getInterfaces()) {
                assertThat(inherited.getName())
                        .as("%s reaches Spring Data through %s", parameter.getSimpleName(), inherited)
                        .doesNotContain("org.springframework");
            }
            assertThat(parameter.getPackage().getName())
                    .as("a port the domain owns lives in the domain")
                    .contains(".domain.");
        }
    }

    @Test
    @DisplayName("it is domain code, and does not sit with the use cases")
    void itLivesInTheDomain() {
        assertThat(PricingService.class.getPackage().getName())
                .contains(".domain.")
                .doesNotContain(".application");
    }

    private static List<Class<?>> given() {
        Constructor<?>[] constructors = PricingService.class.getConstructors();
        assertThat(constructors).hasSize(1);
        return Arrays.asList(constructors[0].getParameterTypes());
    }
}
