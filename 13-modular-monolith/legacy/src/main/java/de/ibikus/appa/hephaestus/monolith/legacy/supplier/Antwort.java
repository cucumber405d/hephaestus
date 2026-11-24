package de.ibikus.appa.hephaestus.monolith.legacy.supplier;

/** The supplier's answer: {@code status} 0 delivered, 17 unknown article, 42 not deliverable. Leaks into forge. */
public record Antwort(int status, int liefermenge) {

    public static final int GELIEFERT = 0;
    public static final int UNBEKANNT = 17;
    public static final int NICHT_LIEFERBAR = 42;
}
