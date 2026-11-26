package io.elasticore.sample.ddd.billing.application;

import io.elasticore.sample.ddd.shop.domain.event.OrderPlacedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Billing's reaction to shop's order being placed: a receipt per order.
 *
 * One bounded context reacting to another's event, in plain Spring code. The
 * event is shop's published language - shop's env.yml publishes OrderPlaced
 * and billing's says it uses shop. It runs after shop's transaction has
 * committed, in billing's own.
 */
@Component
public class ReceiptingListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(OrderPlacedEvent event) {
        // The receipt is written here, from event.getOrderId() and event.getTotalAmount().
    }
}
