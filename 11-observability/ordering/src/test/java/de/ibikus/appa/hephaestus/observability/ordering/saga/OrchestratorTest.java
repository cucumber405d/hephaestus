package de.ibikus.appa.hephaestus.observability.ordering.saga;

import static de.ibikus.appa.hephaestus.observability.ordering.messaging.SagaMessage.*;
import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.observability.ordering.saga.Orchestrator.Outcome;
import de.ibikus.appa.hephaestus.observability.ordering.saga.Orchestrator.Saga;
import de.ibikus.appa.hephaestus.observability.ordering.saga.Orchestrator.State;
import de.ibikus.appa.hephaestus.observability.ordering.saga.Orchestrator.Step;
import org.junit.jupiter.api.Test;

class OrchestratorTest {

    @Test
    void happyPathWalksThroughTheStepsToConfirmed() {
        var saga = Orchestrator.start();
        assertThat(Orchestrator.firstCommand()).isEqualTo(RESERVE_PAYMENT);

        var afterPayment = Orchestrator.onReply(saga, PAYMENT_RESERVED, null);
        assertThat(afterPayment.commands()).containsExactly(RESERVE_STOCK);

        var afterStock = Orchestrator.onReply(afterPayment.next(), STOCK_RESERVED, null);
        assertThat(afterStock.commands()).containsExactly(SCHEDULE_DELIVERY);

        var done = Orchestrator.onReply(afterStock.next(), DELIVERY_SCHEDULED, null);
        assertThat(done.outcome()).isEqualTo(Outcome.CONFIRMED);
        assertThat(done.next().state()).isEqualTo(State.CONFIRMED);
    }

    @Test
    void declinedPaymentRejectsWithoutCompensation() {
        var decision = Orchestrator.onReply(Orchestrator.start(), PAYMENT_DECLINED, "credit limit exceeded");

        assertThat(decision.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(decision.reason()).isEqualTo("credit limit exceeded");
        assertThat(decision.commands()).isEmpty();
    }

    @Test
    void missingStockRefundsThePayment() {
        var decision = Orchestrator.onReply(new Saga(Step.STOCK, State.RUNNING, 0), STOCK_UNAVAILABLE, "none");

        assertThat(decision.commands()).containsExactly(REFUND_PAYMENT);
        assertThat(decision.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(decision.next()).isEqualTo(new Saga(Step.STOCK, State.COMPENSATING, 1));
    }

    @Test
    void failedDeliveryCompensatesNewestFirst() {
        var decision = Orchestrator.onReply(new Saga(Step.DELIVERY, State.RUNNING, 0), DELIVERY_FAILED, "no courier");

        assertThat(decision.commands()).containsExactly(RELEASE_STOCK, REFUND_PAYMENT);
        assertThat(decision.next().pendingCompensations()).isEqualTo(2);
    }

    @Test
    void compensationEndsWhenAllConfirmationsArrived() {
        var compensating = new Saga(Step.DELIVERY, State.COMPENSATING, 2);

        var one = Orchestrator.onReply(compensating, STOCK_RELEASED, null);
        var two = Orchestrator.onReply(one.next(), PAYMENT_REFUNDED, null);

        assertThat(one.next().state()).isEqualTo(State.COMPENSATING);
        assertThat(two.next().state()).isEqualTo(State.REJECTED);
        assertThat(two.outcome()).isNull();
    }

    @Test
    void timeoutCompensatesTheCurrentStepToo() {
        var atDelivery = Orchestrator.onTimeout(new Saga(Step.DELIVERY, State.RUNNING, 0));

        assertThat(atDelivery.commands()).containsExactly(CANCEL_DELIVERY, RELEASE_STOCK, REFUND_PAYMENT);
        assertThat(atDelivery.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(atDelivery.reason()).isEqualTo("timeout at step delivery");
        assertThat(Orchestrator.onTimeout(Orchestrator.start()).commands()).containsExactly(REFUND_PAYMENT);
    }

    @Test
    void lateOrDuplicateRepliesChangeNothing() {
        var compensating = new Saga(Step.DELIVERY, State.COMPENSATING, 3);
        var confirmed = new Saga(Step.DELIVERY, State.CONFIRMED, 0);
        var atStock = new Saga(Step.STOCK, State.RUNNING, 0);

        assertThat(Orchestrator.onReply(compensating, DELIVERY_SCHEDULED, null).ignored(compensating))
                .isTrue();
        assertThat(Orchestrator.onReply(confirmed, DELIVERY_SCHEDULED, null).ignored(confirmed))
                .isTrue();
        assertThat(Orchestrator.onReply(atStock, PAYMENT_RESERVED, null).ignored(atStock))
                .isTrue();
        assertThat(Orchestrator.onTimeout(confirmed).ignored(confirmed)).isTrue();
    }
}
