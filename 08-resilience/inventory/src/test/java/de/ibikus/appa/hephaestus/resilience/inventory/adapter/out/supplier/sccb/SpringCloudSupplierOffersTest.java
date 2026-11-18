package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.sccb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.StubSupplierApi;
import de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.SupplierCall;
import de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.SupplyProperties;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierUnavailableException;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.UnknownOfferException;
import java.time.Duration;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JAutoConfiguration;

/** The adapter with Spring Cloud's own auto-configuration (the factory is built the way the application builds it). */
class SpringCloudSupplierOffersTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Resilience4JAutoConfiguration.class))
            .withPropertyValues("spring.cloud.circuitbreaker.bulkhead.resilience4j.enabled=false")
            .withBean(StubSupplierApi.class)
            .withBean(SupplierCall.class)
            .withBean(SupplyProperties.class, () -> StubSupplierApi.properties(Duration.ofSeconds(10)))
            .withUserConfiguration(SpringCloudCircuitBreakerConfiguration.class)
            .withBean(SpringCloudSupplierOffers.class);

    @Test
    void healthySupplierAnswers() {
        run(context -> {
            assertThat(adapter(context).fetch("ANVIL-01").sku()).isEqualTo("ANVIL-01");
            assertThat(api(context).calls()).isEqualTo(1);
        });
    }

    @Test
    void noRetryInTheAbstractionOneAttemptPerRequest() {
        run(context -> {
            api(context).failing();

            assertThatThrownBy(() -> adapter(context).fetch("ANVIL-01"))
                    .isInstanceOf(SupplierUnavailableException.class);
            assertThat(api(context).calls()).isEqualTo(1);
        });
    }

    @Test
    void openBreakerStopsCallingTheSupplier() {
        run(context -> {
            api(context).timingOut();
            for (int i = 0; i < 10; i++) {
                assertThatThrownBy(() -> adapter(context).fetch("ANVIL-01"))
                        .isInstanceOf(SupplierUnavailableException.class);
            }

            assertThatThrownBy(() -> adapter(context).fetch("ANVIL-01"))
                    .isInstanceOf(SupplierUnavailableException.class);
            assertThat(api(context).calls()).isEqualTo(10);
        });
    }

    @Test
    void unknownOfferPassesThroughAndDoesNotOpenTheBreaker() {
        run(context -> {
            api(context).unknown();
            for (int i = 0; i < 12; i++) {
                assertThatThrownBy(() -> adapter(context).fetch("NOPE-01")).isInstanceOf(UnknownOfferException.class);
            }
            api(context).healthy();

            assertThat(adapter(context).fetch("ANVIL-01").sku()).isEqualTo("ANVIL-01");
            assertThat(api(context).calls()).isEqualTo(13);
        });
    }

    private void run(Consumer<AssertableApplicationContext> test) {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            test.accept(context);
        });
    }

    private static SpringCloudSupplierOffers adapter(AssertableApplicationContext context) {
        return context.getBean(SpringCloudSupplierOffers.class);
    }

    private static StubSupplierApi api(AssertableApplicationContext context) {
        return context.getBean(StubSupplierApi.class);
    }
}
