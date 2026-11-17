package de.ibikus.appa.hephaestus.security.ordering.application.port.in;

import de.ibikus.appa.hephaestus.security.ordering.domain.Order;
import de.ibikus.appa.hephaestus.security.ordering.domain.Requester;
import java.util.UUID;

public interface OrderQuery {

    /**
     * The order, if it exists and the requester may read it. A foreign order is reported exactly like a missing
     * one, so ids of other customers' orders cannot be probed.
     */
    Order get(UUID id, Requester requester);
}
