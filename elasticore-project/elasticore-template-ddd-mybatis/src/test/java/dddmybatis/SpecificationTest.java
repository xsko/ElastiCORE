package dddmybatis;

import io.elasticore.sample.dddmybatis.shop.domain.model.Order;
import io.elasticore.sample.dddmybatis.shop.domain.model.OrderItem;
import io.elasticore.sample.dddmybatis.shop.domain.model.OrderRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A domain rule with a name, asked of a candidate.
 *
 * Written by hand (@specification is no longer read since 3.1, issue 370): a
 * static Predicate per rule in OrderRules. What is worth holding down is that
 * the rule answers about a candidate, that it survives a null field rather
 * than throwing on one, and that two rules combine into a third.
 *
 * No Spring: a rule selects from what is already in hand.
 */
class SpecificationTest {

    @Test
    @DisplayName("the named rule answers about a candidate")
    void theRuleAnswers() {
        assertThat(OrderRules.highValue().test(order("150.00")))
                .as("totalAmount > 100")
                .isTrue();
        assertThat(OrderRules.highValue().test(order("100.00")))
                .as("the bound is exclusive, as the condition wrote it")
                .isFalse();
    }

    @Test
    @DisplayName("an unset field is not a match, and is not an exception either")
    void nullIsNotAMatch() {
        Order blank = new Order();

        assertThat(OrderRules.highValue().test(blank)).isFalse();
        assertThat(OrderRules.hasLines().test(blank)).isFalse();
        assertThat(OrderRules.highValue().test(null))
                .as("a rule asked about nothing answers no")
                .isFalse();
    }

    @Test
    @DisplayName("the collection form reads the members, not the reference")
    void theCollectionForm() {
        Order withoutLines = order("10.00");
        assertThat(OrderRules.hasLines().test(withoutLines)).isFalse();

        Order withLines = order("10.00");
        withLines.addOrderItem(newItem());
        assertThat(OrderRules.hasLines().test(withLines)).isTrue();
    }

    @Test
    @DisplayName("two rules combine into a third without either being restated")
    void rulesCombine() {
        Predicate<Order> worthShipping =
                OrderRules.highValue().and(OrderRules.hasLines());

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
