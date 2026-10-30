package dddmybatis;

import io.elasticore.sample.dddmybatis.shop.domain.model.Order;
import io.elasticore.sample.dddmybatis.shop.domain.model.OrderItem;
import io.elasticore.sample.dddmybatis.shop.domain.model.OrderSpecifications;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A domain rule with a name, asked of a candidate.
 *
 * The value of the pattern is not that the comparison exists - it is that it
 * exists once. So what is worth holding down is that the rule answers about
 * a candidate, that it survives a null field rather than throwing on one, and
 * that two rules combine into a third without either being restated.
 *
 * No Spring: a specification is domain code that selects from what is already
 * in hand, which is the boundary the rule set documents rather than pretending
 * to push the rule into SQL.
 */
class SpecificationTest {

    @Test
    @DisplayName("the named rule answers about a candidate")
    void theRuleAnswers() {
        assertThat(OrderSpecifications.highValue().test(order("150.00")))
                .as("totalAmount > 100")
                .isTrue();
        assertThat(OrderSpecifications.highValue().test(order("100.00")))
                .as("the bound is exclusive, as the condition wrote it")
                .isFalse();
    }

    @Test
    @DisplayName("an unset field is not a match, and is not an exception either")
    void nullIsNotAMatch() {
        Order blank = new Order();

        assertThat(OrderSpecifications.highValue().test(blank)).isFalse();
        assertThat(OrderSpecifications.hasLines().test(blank)).isFalse();
        assertThat(OrderSpecifications.highValue().test(null))
                .as("a rule asked about nothing answers no")
                .isFalse();
    }

    @Test
    @DisplayName("the collection form reads the members, not the reference")
    void theCollectionForm() {
        Order withoutLines = order("10.00");
        assertThat(OrderSpecifications.hasLines().test(withoutLines)).isFalse();

        Order withLines = order("10.00");
        withLines.addOrderItem(newItem());
        assertThat(OrderSpecifications.hasLines().test(withLines)).isTrue();
    }

    @Test
    @DisplayName("two rules combine into a third without either being restated")
    void rulesCombine() {
        Predicate<Order> worthShipping =
                OrderSpecifications.highValue().and(OrderSpecifications.hasLines());

        Order valuableButEmpty = order("150.00");
        Order valuableWithLines = order("150.00");
        valuableWithLines.addOrderItem(newItem());

        assertThat(worthShipping.test(valuableButEmpty)).isFalse();
        assertThat(worthShipping.test(valuableWithLines))
                .as("this is why a specification is a predicate and not a boolean method")
                .isTrue();
    }

    private Order order(String amount) {
        Order order = new Order();
        order.creation().setTotalAmount(new BigDecimal(amount));
        return order;
    }

    private OrderItem newItem() {
        OrderItem item = new OrderItem();
        item.setItemSeq(1);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("10.00"));
        return item;
    }
}
