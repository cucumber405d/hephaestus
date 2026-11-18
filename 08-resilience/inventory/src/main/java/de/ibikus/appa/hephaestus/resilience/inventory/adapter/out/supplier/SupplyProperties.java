package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The protection numbers all three mechanisms share (supply.* in application.yaml), so the comparison is fair.
 * Not every mechanism can use every number: Spring Cloud Circuit Breaker has no retry, Spring 7 no circuit breaker,
 * only Resilience4j a rate limiter.
 */
@ConfigurationProperties("supply")
public record SupplyProperties(Retry retry, int bulkhead, CircuitBreaker circuitBreaker, int rateLimitPerSecond) {

    /**
     * maxRetries retries after the first attempt; exponential backoff from delay. Jitter: Spring 7 adds ± jitter
     * to each pause; Resilience4j randomises each pause by ± (jitter / delay), i.e. ±50 % here (200 ± 100 ms,
     * then 400 ± 200 ms).
     */
    public record Retry(int maxRetries, Duration delay, double multiplier, Duration jitter) {

        public int attempts() {
            return maxRetries + 1;
        }
    }

    /**
     * Count-based window of the last {@code window} calls; open when at least {@code failureRate} percent failed or
     * were slower than {@code slowCall}; stay open for {@code open}, then let {@code halfOpenCalls} probes through.
     */
    public record CircuitBreaker(int window, float failureRate, Duration slowCall, Duration open, int halfOpenCalls) {}
}
