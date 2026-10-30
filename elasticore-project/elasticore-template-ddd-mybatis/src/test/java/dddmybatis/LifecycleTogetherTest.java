package dddmybatis;

import io.elasticore.sample.dddmybatis.shop.domain.model.FeeStatus;
import io.elasticore.sample.dddmybatis.shop.domain.model.Shipment;
import io.elasticore.sample.dddmybatis.shop.domain.model.ShipmentStatus;
import io.elasticore.springboot3.validation.InvariantViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A command that two lifecycles name (shop/model/shipment.yml): cancel moves
 * the shipment to CANCELLED and its fee to REFUNDED, both or neither.
 */
class LifecycleTogetherTest {

    @Test
    void aCommandOfTwoLifecyclesMovesBoth() {
        Shipment shipment = new Shipment();
        shipment.pay();
        assertThat(shipment.canCancel()).isTrue();

        shipment.cancel();

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
        assertThat(shipment.getFee()).isEqualTo(FeeStatus.REFUNDED);
        assertThat(shipment.canCancel()).isFalse();
    }

    @Test
    void aRefusalInEitherLifecycleMovesNeither() {
        // The fee is not paid: the status could move, but must not.
        Shipment unpaid = new Shipment();
        assertThat(unpaid.canCancel()).isFalse();
        assertThatThrownBy(unpaid::cancel)
                .isInstanceOf(InvariantViolationException.class)
                .hasMessageContaining("cannot cancel when fee is DUE");
        assertThat(unpaid.getStatus()).isEqualTo(ShipmentStatus.READY);

        // Already sent: the fee could move, but must not.
        Shipment sent = new Shipment();
        sent.pay();
        sent.send();
        assertThatThrownBy(sent::cancel).hasMessageContaining("cannot cancel when status is SENT");
        assertThat(sent.getFee()).isEqualTo(FeeStatus.PAID);
    }

    @Test
    void eachLifecycleStillMovesOnItsOwn() {
        Shipment shipment = new Shipment();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.READY);
        assertThat(shipment.getFee()).isEqualTo(FeeStatus.DUE);
        shipment.send();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.SENT);
        assertThat(shipment.getFee()).isEqualTo(FeeStatus.DUE);
    }
}
