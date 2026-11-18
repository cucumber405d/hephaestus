package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.StubSupplierApi;
import de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.SupplierCall;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierUnavailableException;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.UnknownOfferException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The annotations only work through Spring's proxy, so the test runs a small context. */
class SpringResilientSupplierOffersTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues(
                    "supply.retry.max-retries=2",
                    "supply.retry.delay=10ms",
                    "supply.retry.multiplier=2",
                    "supply.retry.jitter=5ms")
            .withUserConfiguration(ResilientMethodsConfiguration.class)
            .withBean(StubSupplierApi.class)
            .withBean(SupplierCall.class)
            .withBean(RetryingSupplierCall.class)
            .withBean(SpringResilientSupplierOffers.class);

    @Test
    void healthySupplierAnswersInOneCall() {
        runner.run(context -> {
            assertThat(context.getBean(SpringResilientSupplierOffers.class).fetch("ANVIL-01").sku())
                    .isEqualTo("ANVIL-01");
            assertThat(context.getBean(StubSupplierApi.class).calls()).isEqualTo(1);
        });
    }

    @Test
    void failingSupplierIsTriedThreeTimesEveryRequestNoBreaker() {
        runner.run(context -> {
            var api = context.getBean(StubSupplierApi.class);
            var adapter = context.getBean(SpringResilientSupplierOffers.class);
            api.failing();

            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> adapter.fetch("ANVIL-01")).isInstanceOf(SupplierUnavailableException.class);
            }
            assertThat(api.calls()).isEqualTo(15);
        });
    }

    @Test
    void unknownOfferIsNotRetried() {
        runner.run(context -> {
            var api = context.getBean(StubSupplierApi.class);
            api.unknown();

            assertThatThrownBy(() -> context.getBean(SpringResilientSupplierOffers.class).fetch("NOPE-01"))
                    .isInstanceOf(UnknownOfferException.class);
            assertThat(api.calls()).isEqualTo(1);
        });
    }
}
