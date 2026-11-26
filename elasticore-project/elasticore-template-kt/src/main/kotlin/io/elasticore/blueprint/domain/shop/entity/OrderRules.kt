package io.elasticore.blueprint.domain.shop.entity

import java.math.BigDecimal
import java.util.function.Predicate

/**
 * Named rules about an order, asked of one already in hand.
 *
 * Predicates rather than boolean functions, so that two combine into a third:
 * highValue().and(hasLines()).
 */
object OrderRules {

    private val HIGH_VALUE = BigDecimal("100")

    /** More than 100. */
    fun highValue(): Predicate<Order?> =
        Predicate { order -> order?.totalAmount?.let { it > HIGH_VALUE } ?: false }

    /** At least one line. */
    fun hasLines(): Predicate<Order?> =
        Predicate { order -> !order?.items.isNullOrEmpty() }
}
