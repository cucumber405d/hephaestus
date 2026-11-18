package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.spring;

import de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.SupplierCall;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierOffer;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * The supplier call with Spring Framework 7's own resilience annotations (enabled by @EnableResilientMethods; they
 * work through a proxy, so the caller is another bean). Numbers from supply.retry.* like the other mechanisms.
 * There is no circuit breaker: during an outage every request still makes all its attempts.
 * {@code @ConcurrencyLimit} throttles: the eleventh concurrent caller waits for a free slot instead of being refused.
 */
@Component
public class RetryingSupplierCall {

    private final SupplierCall call;

    public RetryingSupplierCall(SupplierCall call) {
        this.call = call;
    }

    @Retryable(
            includes = {ResourceAccessException.class, HttpServerErrorException.class},
            maxRetriesString = "${supply.retry.max-retries}",
            delayString = "${supply.retry.delay}",
            multiplierString = "${supply.retry.multiplier}",
            jitterString = "${supply.retry.jitter}")
    @ConcurrencyLimit(10)
    public SupplierOffer fetch(String sku) {
        return call.fetch(sku);
    }
}
