package io.elasticore.sample.dddmybatis.shop.application;

import io.elasticore.sample.dddmybatis.shop.domain.event.OrderPlacedEvent;
import io.elasticore.sample.dddmybatis.shop.domain.repository.CustomerRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * What an order being placed means for the customer: counts the orders a
 * customer has placed.
 *
 * Plain Spring code, written by hand. It runs after the order's transaction
 * has committed, in a transaction of its own, so a failure here does not undo
 * the order. A step that must run in the caller's transaction - to see what
 * the signed-in user may do - is a plain @EventListener instead.
 */
@Component
public class LoyaltyListener {

    private final CustomerRepositoryPort customers;

    public LoyaltyListener(CustomerRepositoryPort customers) {
        this.customers = customers;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(OrderPlacedEvent event) {
        // The next step goes here, with customers.
    }
}
