package de.ibikus.appa.hephaestus.modernjava.java09;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SupplierClientTest {

    @Test
    void returnsFallbackWhenSupplierIsTooSlow() throws Exception {
        var neverAnswers = new CompletableFuture<BigDecimal>();

        var price = SupplierClient.priceWithFallback(() -> neverAnswers, Duration.ofMillis(50), new BigDecimal("9.99"));

        assertThat(price.get(1, TimeUnit.SECONDS)).isEqualByComparingTo("9.99");
        assertThat(neverAnswers)
                .as("the supplier's own future is not completed")
                .isNotDone();
    }

    @Test
    void orTimeoutFailsInsteadOfFallingBack() {
        var neverAnswers = new CompletableFuture<BigDecimal>().orTimeout(50, TimeUnit.MILLISECONDS);

        assertThatThrownBy(() -> neverAnswers.get(1, TimeUnit.SECONDS))
                .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
    }

    @Test
    void returnsSupplierPriceWhenInTime() throws Exception {
        var price = SupplierClient.priceWithFallback(
                () -> CompletableFuture.completedFuture(new BigDecimal("4.80")),
                Duration.ofSeconds(1),
                new BigDecimal("9.99"));

        assertThat(price.get(1, TimeUnit.SECONDS)).isEqualByComparingTo("4.80");
    }
}
