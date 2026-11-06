package de.ibikus.appa.hephaestus.modernjava.java24;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Gatherer;
import java.util.stream.Gatherers;

/**
 * Java 24 Stream Gatherers (JEP 485; preview in 22 and 23): custom intermediate operations, the missing
 * counterpart of {@code Collector}. Built-ins: windowFixed, windowSliding, fold, scan, mapConcurrent.
 */
public final class PriceGatherers {

    private PriceGatherers() {}

    /** Keeps the first element per key: state (seen keys) + integrator, sequential, greedy. */
    public static <T, K> Gatherer<T, ?, T> distinctBy(Function<? super T, ? extends K> key) {
        return Gatherer.ofSequential(
                () -> new HashSet<K>(),
                Gatherer.Integrator.ofGreedy(
                        (seen, element, downstream) -> !seen.add(key.apply(element)) || downstream.push(element)));
    }

    /** Average of each sliding window; an input shorter than the window yields one window with all elements. */
    public static List<BigDecimal> movingAverage(List<BigDecimal> prices, int window) {
        return prices.stream()
                .gather(Gatherers.windowSliding(window))
                .map(slice -> slice.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(slice.size()), 2, RoundingMode.HALF_UP))
                .toList();
    }
}
