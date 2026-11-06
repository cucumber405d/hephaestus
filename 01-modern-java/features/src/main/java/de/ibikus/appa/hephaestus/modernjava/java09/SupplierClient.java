package de.ibikus.appa.hephaestus.modernjava.java09;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Java 9 {@code CompletableFuture} additions: {@code copy()} and {@code completeOnTimeout(...)}. */
public final class SupplierClient {

    private SupplierClient() {}

    /**
     * Returns the supplier's price, or {@code fallback} if it does not answer within {@code timeout}.
     * The supplier's own future is left untouched thanks to {@code copy()}.
     */
    public static CompletableFuture<BigDecimal> priceWithFallback(
            Supplier<CompletableFuture<BigDecimal>> call, Duration timeout, BigDecimal fallback) {
        return call.get().copy().completeOnTimeout(fallback, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
}
