package ddd;

import io.elasticore.sample.ddd.shop.domain.model.Order;
import io.elasticore.sample.ddd.shop.domain.model.OrderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The place an aggregate is made, and nothing more than the place.
 *
 * A factory is the half of the invariant story the generator cannot write:
 * the invariant says what must be true of an order, and something has to be
 * responsible for the moment it becomes true. What can be generated is where
 * that lives, not what it does - so this asserts the shape and asserts that
 * the body refuses to pretend.
 *
 * No Spring here on purpose. The factory is domain code with no dependency on
 * anything that has to be started, which is itself worth holding down.
 */
class AggregateFactoryTest {

    @Test
    @DisplayName("the declared factory exists on the aggregate's own factory class")
    void theFactoryIsGenerated() throws Exception {
        Method place = OrderFactory.class.getMethod("place", LocalDateTime.class);

        assertThat(Modifier.isStatic(place.getModifiers()))
                .as("a factory makes the aggregate; it is not one")
                .isTrue();
        assertThat(place.getReturnType())
                .as("what @factory(place) on Order promised to return")
                .isEqualTo(Order.class);
    }

    @Test
    @DisplayName("the signature is the root's required fields, and not its generated id")
    void theSignatureIsWhatTheModelCouldSay() {
        // What the source declares; a coverage agent adds a synthetic method of its own.
        Method[] methods = java.util.Arrays.stream(OrderFactory.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic())
                .toArray(Method[]::new);

        assertThat(methods)
                .as("one declared name, one method")
                .hasSize(1);
        assertThat(methods[0].getParameterTypes())
                .as("orderDate is required; orderId is generated, so being handed one makes nothing")
                .containsExactly(LocalDateTime.class);
    }

    @Test
    @DisplayName("the generated body refuses rather than returning half an aggregate")
    void theBodyIsLeftToTheDeveloper() {
        assertThatThrownBy(() -> OrderFactory.place(LocalDateTime.now()))
                .as("a stub that quietly returns something is a stub that reaches production")
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("write it");
    }
}
