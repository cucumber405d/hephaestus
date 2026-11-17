package de.ibikus.appa.hephaestus.servicecommunication.ordering.adapter.out.inventory;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Inventory's HTTP API as a Java interface (HTTP Service Client); Spring generates the implementation on top of
 * RestClient. The records are Ordering's own copy of Inventory's JSON: no shared DTO module, so the services
 * are only coupled through the wire format (verified by contract tests in 14).
 */
@HttpExchange("/api")
interface InventoryHttpApi {

    /** The internal reservation API (unversioned on the server, which serves it for every version). */
    @PostExchange(url = "/reservations", version = "1")
    ReservationResponse reserve(@RequestBody ReservationRequest request);

    /** Version 2 of the stock API: version 1 is deprecated. */
    @GetExchange(url = "/stock/{sku}", version = "2")
    StockResponse stock(@PathVariable String sku);

    record ReservationRequest(UUID orderId, List<Line> lines) {}

    record Line(String sku, int quantity) {}

    record ReservationResponse(UUID id, UUID orderId) {}

    record StockResponse(String sku, int onHand, int reserved, int available) {}
}
