package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.sccb;

import de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.CircuitBreakerSettings;
import de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.SupplyProperties;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
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
