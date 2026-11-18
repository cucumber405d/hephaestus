package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.r4j;

import de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.SupplierCall;
import de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.SupplyProperties;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.in.Mechanism;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.out.SupplierOffers;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierOffer;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.UnknownOfferException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Mechanism 1: Resilience4j used directly, configured in code (its Spring Boot starter targets Boot 3).
 * Per request, outermost first: rate limiter (20/s towards the supplier) → bulkhead (10 at a time, no waiting) →
 * retry (3 attempts, exponential backoff with jitter) → circuit breaker around every single attempt. An open
 * breaker answers {@code CallNotPermittedException} at once, which the retry does not retry.
 */
@Component
public class Resilience4jSupplierOffers implements SupplierOffers {

    static final String NAME = "supplier-r4j";

    private final SupplierCall call;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;
    private final RateLimiter rateLimiter;

    public Resilience4jSupplierOffers(SupplierCall call, SupplyProperties properties, MeterRegistry meters) {
        this.call = call;
        var cb = properties.circuitBreaker();
        var circuitBreakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(cb.window())
                .minimumNumberOfCalls(cb.window())
                .failureRateThreshold(cb.failureRate())
                .slowCallDurationThreshold(cb.slowCall())
                .slowCallRateThreshold(cb.failureRate())
                .waitDurationInOpenState(cb.open())
                .permittedNumberOfCallsInHalfOpenState(cb.halfOpenCalls())
                .recordException(SupplierCall::isFailure)
                // A 404 is an answer: neither success nor failure for the breaker
                .ignoreExceptions(UnknownOfferException.class)
                .build());
        var r = properties.retry();
        var retries = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(r.attempts())
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                        r.delay(), r.multiplier(), (double) r.jitter().toMillis() / r.delay().toMillis()))
                .retryOnException(SupplierCall::isFailure)
                .build());
        var bulkheads = BulkheadRegistry.of(BulkheadConfig.custom()
                .maxConcurrentCalls(properties.bulkhead())
                .maxWaitDuration(Duration.ZERO)
                .build());
        var rateLimiters = RateLimiterRegistry.of(RateLimiterConfig.custom()
                .limitForPeriod(properties.rateLimitPerSecond())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        this.circuitBreaker = circuitBreakers.circuitBreaker(NAME);
        this.retry = retries.retry(NAME);
        this.bulkhead = bulkheads.bulkhead(NAME);
        this.rateLimiter = rateLimiters.rateLimiter(NAME);
        // resilience4j_circuitbreaker_state, _calls, resilience4j_retry_calls, ... tagged name=supplier-r4j
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakers).bindTo(meters);
        TaggedRetryMetrics.ofRetryRegistry(retries).bindTo(meters);
        TaggedBulkheadMetrics.ofBulkheadRegistry(bulkheads).bindTo(meters);
        TaggedRateLimiterMetrics.ofRateLimiterRegistry(rateLimiters).bindTo(meters);
    }

    @Override
    public Mechanism mechanism() {
        return Mechanism.R4J;
    }

    @Override
    public SupplierOffer fetch(String sku) {
        Supplier<SupplierOffer> protectedCall = () -> call.fetch(sku);
        protectedCall = CircuitBreaker.decorateSupplier(circuitBreaker, protectedCall);
        protectedCall = Retry.decorateSupplier(retry, protectedCall);
        protectedCall = Bulkhead.decorateSupplier(bulkhead, protectedCall);
        protectedCall = RateLimiter.decorateSupplier(rateLimiter, protectedCall);
        try {
            return protectedCall.get();
        } catch (RuntimeException e) {
            throw SupplierCall.translate(Mechanism.R4J.id(), sku, e);
        }
    }

    /** For tests and the learning notes: CLOSED, OPEN, HALF_OPEN. */
    public CircuitBreaker.State state() {
        return circuitBreaker.getState();
    }
}
