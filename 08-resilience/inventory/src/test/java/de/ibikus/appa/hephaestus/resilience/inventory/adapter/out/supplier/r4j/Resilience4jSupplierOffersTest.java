package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.r4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.StubSupplierApi;
import de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.SupplierCall;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierUnavailableException;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.UnknownOfferException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class Resilience4jSupplierOffersTest {

    private final StubSupplierApi api = new StubSupplierApi();
    private final Resilience4jSupplierOffers adapter = new Resilience4jSupplierOffers(
            new SupplierCall(api), StubSupplierApi.properties(Duration.ofMillis(300)), new SimpleMeterRegistry());

    @Test
    void healthySupplierAnswersInOneCall() {
        assertThat(adapter.fetch("ANVIL-01").sku()).isEqualTo("ANVIL-01");
        assertThat(api.calls()).isEqualTo(1);
    }

    @Test
    void failingSupplierIsTriedThreeTimesThenUnavailable() {
        api.failing();

        assertThatThrownBy(() -> adapter.fetch("ANVIL-01")).isInstanceOf(SupplierUnavailableException.class);
        assertThat(api.calls()).isEqualTo(3);
    }

    @Test
    void openBreakerStopsCallingTheSupplier() {
        api.timingOut();
        // 3 attempts per request: after 4 requests the window of 10 calls is full of failures
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> adapter.fetch("ANVIL-01")).isInstanceOf(SupplierUnavailableException.class);
        }
        assertThat(adapter.state()).isEqualTo(CircuitBreaker.State.OPEN);
        var callsWhenOpened = api.calls();

        assertThatThrownBy(() -> adapter.fetch("ANVIL-01")).isInstanceOf(SupplierUnavailableException.class);
        assertThat(api.calls()).isEqualTo(callsWhenOpened);
    }

    @Test
    void breakerClosesAgainWhenTheSupplierRecovers() throws Exception {
        api.failing();
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> adapter.fetch("ANVIL-01")).isInstanceOf(SupplierUnavailableException.class);
        }
        api.healthy();
        Thread.sleep(400);

        for (int i = 0; i < 3; i++) {
            assertThat(adapter.fetch("ANVIL-01").sku()).isEqualTo("ANVIL-01");
        }
        assertThat(adapter.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void unknownOfferIsNotRetriedAndDoesNotOpenTheBreaker() {
        api.unknown();

        for (int i = 0; i < 12; i++) {
            assertThatThrownBy(() -> adapter.fetch("NOPE-01")).isInstanceOf(UnknownOfferException.class);
        }
        assertThat(api.calls()).isEqualTo(12);
        assertThat(adapter.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
