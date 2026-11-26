package io.elasticore.sample.dddmybatis.shop.domain.model;

import java.math.BigDecimal;
import java.util.function.Predicate;

/**
 * Named rules about an order, asked of one already in hand.
 *
 * Predicates rather than boolean methods, so that two combine into a third:
 * highValue().and(hasLines()).
 */
public final class OrderRules {

    private static final BigDecimal HIGH_VALUE = new BigDecimal("100");

    private OrderRules() {
    }

    /** More than 100. */
    public static Predicate<Order> highValue() {
        return order -> order != null
                && order.getTotalAmount() != null
                && order.getTotalAmount().compareTo(HIGH_VALUE) > 0;
    }

    /** At least one line. */
    public static Predicate<Order> hasLines() {
        return order -> order != null && order.getItems() != null && !order.getItems().isEmpty();
    }
}
