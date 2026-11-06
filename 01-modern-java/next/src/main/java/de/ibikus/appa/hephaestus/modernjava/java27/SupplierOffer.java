package de.ibikus.appa.hephaestus.modernjava.java27;

import java.math.BigDecimal;
import java.time.Duration;

/** Combined answer of three supplier services. */
public record SupplierOffer(BigDecimal price, int stock, Duration leadTime) {}
