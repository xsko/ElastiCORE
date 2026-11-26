package io.elasticore.blueprint.domain.shop.service

import io.elasticore.blueprint.domain.shop.event.OrderPlacedEvent
import io.elasticore.blueprint.domain.shop.repository.CustomerRepositoryPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * What an order being placed means for the customer: counts the orders a
 * customer has placed.
 *
 * Plain Spring code, written by hand. It runs after the order's transaction
 * has committed, in a transaction of its own. Open, because the transaction
 * is a proxy and this module's all-open plugin opens only the JPA classes.
 */
@Component
open class LoyaltyListener(private val customers: CustomerRepositoryPort) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun on(event: OrderPlacedEvent) {
        // The next step goes here, with customers.
    }
}
