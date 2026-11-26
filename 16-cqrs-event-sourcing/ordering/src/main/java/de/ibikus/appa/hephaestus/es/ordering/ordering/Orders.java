package de.ibikus.appa.hephaestus.es.ordering.ordering;

import java.util.UUID;

/** Ordering's API. */
public interface Orders {

    UUID place(OrderRequest request);

    CustomerOrder get(UUID id);
}
