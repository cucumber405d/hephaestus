package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.sccb;

import de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.CircuitBreakerSettings;
import de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.SupplyProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Resilience4j implementation behind the Spring Cloud abstraction: the same circuit breaker as
 * mechanism 1, and a time limiter above the HTTP read timeout (the read timeout ends a slow call first; the time
 * limiter is the abstraction's own safety net).
 */
@Configuration(proxyBeanMethods = false)
class SpringCloudCircuitBreakerConfiguration {

    static final Duration TIME_LIMIT = Duration.ofSeconds(3);

    /**
     * Spring Cloud CB 2025.1 expects the Resilience4j registries as beans (normally the Resilience4j Spring Boot
     * starter provides them; we use the core modules only). Separate from mechanism 1's own registries.
     */
    @Bean
    CircuitBreakerRegistry springCloudCircuitBreakerRegistry(ObjectProvider<MeterRegistry> meters) {
        var registry = CircuitBreakerRegistry.ofDefaults();
        meters.ifAvailable(m ->
                TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(m));
        return registry;
    }

    @Bean
    TimeLimiterRegistry springCloudTimeLimiterRegistry() {
        return TimeLimiterRegistry.ofDefaults();
    }

    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> supplierCircuitBreaker(SupplyProperties properties) {
        return factory -> factory.configure(
                builder -> builder.circuitBreakerConfig(CircuitBreakerSettings.from(properties.circuitBreaker()))
                        .timeLimiterConfig(TimeLimiterConfig.custom()
                                .timeoutDuration(TIME_LIMIT)
                                .build()),
                SpringCloudSupplierOffers.ID);
    }
}
