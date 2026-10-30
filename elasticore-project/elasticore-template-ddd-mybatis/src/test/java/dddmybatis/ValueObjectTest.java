package dddmybatis;

import io.elasticore.sample.dddmybatis.shop.domain.model.Address;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A value object: no identity, no life of its own, and no way to change.
 *
 * The rules already withheld the setter and made equality by value. What was
 * missing was the other half of "replaced rather than changed" - a way to get
 * a different one. Without it the only honest route was the all-args
 * constructor, every field repeated positionally at the call site, which is
 * how the wrong two get swapped.
 */
class ValueObjectTest {

    @Test
    @DisplayName("equality is by value, so two addresses that read the same are the same")
    void equalityIsByValue() {
        assertThat(warehouse()).isEqualTo(warehouse());
        assertThat(warehouse().hashCode()).isEqualTo(warehouse().hashCode());
    }

    @Test
    @DisplayName("there is no setter to change one with")
    void nothingCanChangeIt() {
        for (Method m : Address.class.getMethods()) {
            assertThat(m.getName())
                    .as("the setter Lombok would have generated is withheld")
                    .doesNotStartWith("set");
        }
    }

    @Test
    @DisplayName("a different value is a new one, and the original is untouched")
    void replacementLeavesTheOriginalAlone() {
        Address original = warehouse();
        Address moved = original.withCity("Rotterdam");

        assertThat(moved.getCity()).isEqualTo("Rotterdam");
        assertThat(moved.getStreet())
                .as("only the named field differs; the rest travelled with it")
                .isEqualTo(original.getStreet());
        assertThat(original.getCity())
                .as("the value that was replaced was not modified")
                .isEqualTo("Antwerp");
        assertThat(moved).isNotEqualTo(original);
    }

    private Address warehouse() {
        return new Address("Scheldelaan 1", "Antwerp", "VAN", "2040", "BE");
    }
}
