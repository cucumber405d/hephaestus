package de.ibikus.appa.hephaestus.cdc.ordering.ordering;

import java.util.UUID;

/** Ordering's API. */
public interface Orders {

    UUID place(OrderRequest request);

    CustomerOrder get(UUID id);
}
