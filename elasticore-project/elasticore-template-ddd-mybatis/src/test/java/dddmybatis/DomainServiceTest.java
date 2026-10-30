package dddmybatis;

import io.elasticore.sample.dddmybatis.shop.domain.repository.OrderRepositoryPort;
import io.elasticore.sample.dddmybatis.shop.domain.repository.ProductRepositoryPort;
import io.elasticore.sample.dddmybatis.shop.domain.service.PricingDomainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An operation that belongs to no aggregate, wired to the ones it spans.
 *
 * The pattern waited on a question rather than on a mechanism: every other
 * DDD annotation here is written on an entity, and a domain service is
 * defined by not being any entity's. It is declared at the domain, so what is
 * worth holding down is that the declaration reaches the generated file - the
 * aggregates named by `uses` are the ports of the regenerated base - and that the file lands in
 * the domain rather than beside the use cases.
 *
 * Started as a Spring bean rather than only inspected, because the wiring is
 * the half that was generated: an injected repository that no context can
 * satisfy is the failure this would otherwise ship.
 */
@SpringBootTest
class DomainServiceTest {

    @Autowired
    private PricingDomainService pricing;

    @Test
    @DisplayName("the declared service is a bean, so its wiring actually resolves")
    void itIsWired() {
        assertThat(pricing)
                .as("generated wiring that no context can satisfy is not wiring")
                .isNotNull();
    }

    /**
     * The ports are fields of the regenerated base, so a name added to `uses`
     * reaches the service without an edit to the developer's class - the DSL
     * change is the whole change.
     */
    @Test
    @DisplayName("what it spans is what it is given, and it is given ports")
    void usesBecomesTheBasesPorts() {
        assertThat(PricingDomainService.class.getSuperclass().getSimpleName())
                .isEqualTo("PricingDomainServiceBase");
        assertThat(given())
                .as("uses: [Order, Product] - in that order, as the domain's own ports")
                .containsExactly(OrderRepositoryPort.class, ProductRepositoryPort.class);
    }

    /** The types the base is given, in declaration order. */
    private static List<Class<?>> given() {
        List<Class<?>> out = new ArrayList<>();
        for (Field f : PricingDomainService.class.getSuperclass().getDeclaredFields()) {
            if (f.isAnnotationPresent(Autowired.class)) out.add(f.getType());
        }
        return out;
    }

    /**
     * A domain service is handed the domain's interface, not Spring Data.
     *
     * This is the whole of what the repository inversion buys here, and it is
     * worth an assertion of its own because the earlier version of this class
     * was given JpaRepository subtypes - domain code depending on the
     * persistence mechanism, which is the one direction the architecture this
     * bundle is named after asks not to happen.
     */
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
        String pkg = PricingDomainService.class.getPackage().getName();

        assertThat(pkg)
                .as("a domain service is the domain's; the application layer is above it")
                .contains(".domain.")
                .doesNotContain(".application");
    }
}
