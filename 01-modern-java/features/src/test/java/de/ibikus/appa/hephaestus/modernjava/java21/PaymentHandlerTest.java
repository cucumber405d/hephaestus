package de.ibikus.appa.hephaestus.modernjava.java21;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.modernjava.java16.Money;
import de.ibikus.appa.hephaestus.modernjava.java17.PaymentResult.Approved;
import de.ibikus.appa.hephaestus.modernjava.java17.PaymentResult.Declined;
import de.ibikus.appa.hephaestus.modernjava.java17.PaymentResult.Pending;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PaymentHandlerTest {

    @Test
    void largeApprovedPaymentGoesToReview() {
        assertThat(PaymentHandler.handle(new Approved("T1", Money.eur("1500")))).isEqualTo("REVIEW T1");
    }

    @Test
    void regularApprovedPaymentIsOk() {
        assertThat(PaymentHandler.handle(new Approved("T2", Money.eur("10")))).isEqualTo("OK T2");
    }

    @Test
    void boundaryAmountIsNotReviewed() {
        assertThat(PaymentHandler.handle(new Approved("T3", Money.eur("1000")))).isEqualTo("OK T3");
    }

    @Test
    void declinedShowsReason() {
        assertThat(PaymentHandler.handle(new Declined("card expired"))).isEqualTo("DECLINED card expired");
    }

    @Test
    void pendingShowsRetryDelay() {
        assertThat(PaymentHandler.handle(new Pending(Duration.ofSeconds(30)))).isEqualTo("RETRY IN 30s");
    }
}
