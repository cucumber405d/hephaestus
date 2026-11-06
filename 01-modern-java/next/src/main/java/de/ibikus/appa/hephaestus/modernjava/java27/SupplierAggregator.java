package de.ibikus.appa.hephaestus.modernjava.java27;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

/**
 * Structured concurrency (JEP 533, seventh preview in Java 27): subtasks forked in a scope live and die with it.
 * With the default joiner the scope waits for all subtasks to succeed; the first failure cancels (interrupts) the
 * siblings and {@code join()} throws with that failure as the cause. No leaked threads, no orphaned work.
 *
 * <p>API drift between previews: in JDK 25 {@code join()} threw the unchecked {@code FailedException}; in JDK 27 it
 * throws the checked {@link ExecutionException}.
 */
public final class SupplierAggregator {

    private SupplierAggregator() {}

    public static SupplierOffer offer(Callable<BigDecimal> price, Callable<Integer> stock, Callable<Duration> leadTime)
            throws InterruptedException, ExecutionException {
        try (var scope = StructuredTaskScope.open()) {
            var priceTask = scope.fork(price);
            var stockTask = scope.fork(stock);
            var leadTimeTask = scope.fork(leadTime);

            scope.join();

            return new SupplierOffer(priceTask.get(), stockTask.get(), leadTimeTask.get());
        }
    }
}
