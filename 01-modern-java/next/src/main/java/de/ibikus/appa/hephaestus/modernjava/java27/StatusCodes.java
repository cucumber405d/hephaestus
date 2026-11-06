package de.ibikus.appa.hephaestus.modernjava.java27;

/**
 * Primitive types in patterns, instanceof and switch (JEP 532, fifth preview in Java 27): type patterns and guards
 * now work on {@code int} selectors too, so a switch can mix constants and guarded patterns.
 */
public final class StatusCodes {

    private StatusCodes() {}

    public static String describe(int code) {
        return switch (code) {
            case 200 -> "ok";
            case 404 -> "not found";
            case int c when c >= 500 -> "server error";
            case int c -> "other";
        };
    }
}
