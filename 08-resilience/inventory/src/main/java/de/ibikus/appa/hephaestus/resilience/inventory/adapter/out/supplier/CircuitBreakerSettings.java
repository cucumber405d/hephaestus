package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier;

import de.ibikus.appa.hephaestus.resilience.inventory.domain.UnknownOfferException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;

/** One circuit breaker configuration for both mechanisms that have one (Resilience4j and Spring Cloud CB). */
public final class CircuitBreakerSettings {

    private CircuitBreakerSettings() {}

    public static CircuitBreakerConfig from(SupplyProperties.CircuitBreaker cb) {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(cb.window())
                // Judge only a full window (the default would wait for 100 calls)
                .minimumNumberOfCalls(cb.window())
                .failureRateThreshold(cb.failureRate())
                .slowCallDurationThreshold(cb.slowCall())
                .slowCallRateThreshold(cb.failureRate())
                .waitDurationInOpenState(cb.open())
                .permittedNumberOfCallsInHalfOpenState(cb.halfOpenCalls())
                .recordException(SupplierCall::isFailure)
                // A 404 is an answer: neither success nor failure for the breaker
                .ignoreExceptions(UnknownOfferException.class)
                .build();
    }
}
