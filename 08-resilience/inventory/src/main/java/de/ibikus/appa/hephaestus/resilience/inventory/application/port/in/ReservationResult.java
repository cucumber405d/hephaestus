package de.ibikus.appa.hephaestus.resilience.inventory.application.port.in;

import de.ibikus.appa.hephaestus.resilience.inventory.domain.Reservation;

/** The reservation and whether this call created it (false: a repeated request for the same order). */
public record ReservationResult(Reservation reservation, boolean created) {}
