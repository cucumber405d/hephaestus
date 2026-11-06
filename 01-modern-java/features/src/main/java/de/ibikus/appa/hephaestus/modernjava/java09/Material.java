package de.ibikus.appa.hephaestus.modernjava.java09;

import java.math.BigDecimal;

/** A workshop material. (A record is a Java 16 feature; used here only as a value type.) */
public record Material(String code, String name, BigDecimal pricePerKg) {}
