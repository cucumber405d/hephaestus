package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierUnavailableException;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.UnknownOfferException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class SupplierCallTest {

    @Test
    void timeLimiterTimeoutIsAFailureForTheBreaker() {
        assertThat(SupplierCall.isFailure(new TimeoutException("time limiter"))).isTrue();
    }

    @Test
    void wrappedUnknownOfferStaysUnknown() {
        var wrapped = new ExecutionException(new UnknownOfferException("NOPE-01"));

        assertThat(SupplierCall.translate("sccb", "NOPE-01", wrapped)).isInstanceOf(UnknownOfferException.class);
    }

    @Test
    void anythingElseIsUnavailable() {
        assertThat(SupplierCall.translate("r4j", "ANVIL-01", new IllegalStateException("bulkhead full")))
                .isInstanceOf(SupplierUnavailableException.class);
    }
}
