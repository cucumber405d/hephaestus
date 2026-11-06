package de.ibikus.appa.hephaestus.modernjava.java11;

import static java.util.function.Predicate.not;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Java 11 String API ({@code lines}, {@code strip}, {@code isBlank}), {@code Predicate.not} and {@code var} in
 * lambda parameters.
 */
public final class PriceListParser {

    private PriceListParser() {}

    /** Parses lines of {@code CODE;PRICE}; blank lines and {@code #} comments are skipped. */
    public static Map<String, BigDecimal> parse(String text) {
        return text.lines()
                .map(String::strip)
                .filter(not(String::isBlank))
                .filter((var line) -> !line.startsWith("#"))
                .map(line -> line.split(";", 2))
                .collect(Collectors.toMap(parts -> parts[0].strip(), parts -> new BigDecimal(parts[1].strip())));
    }
}
