package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/** An in-memory supplier API for adapter tests: counts calls, answers as told. */
public final class StubSupplierApi implements SupplierHttpApi {

    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<Function<String, OfferResponse>> behaviour = new AtomicReference<>();

    public StubSupplierApi() {
        healthy();
    }

    public void healthy() {
        behaviour.set(sku -> new OfferResponse(sku, new java.math.BigDecimal("120.00"), "EUR", 5, 20));
    }

    public void timingOut() {
        behaviour.set(sku -> {
            throw new ResourceAccessException("Read timed out");
        });
    }

    public void failing() {
        behaviour.set(sku -> {
            throw HttpServerErrorException.create(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Unavailable",
                    org.springframework.http.HttpHeaders.EMPTY,
                    new byte[0],
                    java.nio.charset.StandardCharsets.UTF_8);
        });
    }

    public void unknown() {
        behaviour.set(sku -> {
            throw HttpClientErrorException.create(
                    HttpStatus.NOT_FOUND,
                    "Not Found",
                    org.springframework.http.HttpHeaders.EMPTY,
                    new byte[0],
                    java.nio.charset.StandardCharsets.UTF_8);
        });
    }

    public int calls() {
        return calls.get();
    }

    @Override
    public OfferResponse offer(String sku) {
        calls.incrementAndGet();
        return behaviour.get().apply(sku);
    }

    public static SupplyProperties properties(java.time.Duration open) {
        return new SupplyProperties(
                new SupplyProperties.Retry(2, java.time.Duration.ofMillis(10), 2, java.time.Duration.ofMillis(5)),
                10,
                new SupplyProperties.CircuitBreaker(10, 50, java.time.Duration.ofMillis(800), open, 3),
                20);
    }
}
