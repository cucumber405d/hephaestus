package de.ibikus.appa.hephaestus.security.ordering.adapter.out.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * are only coupled through the wire format (verified by contract tests in 14). Responses are read as a tolerant
 * reader: fields Inventory adds later are ignored instead of breaking Ordering.
 */
@HttpExchange("/api")
interface InventoryHttpApi {

    /**
     * The internal reservation API is unversioned on the server and served for every supported version. The client
     * still sends the current version 2: deprecation is per version, so a call with 1 would collect Deprecation
     * headers and start failing with 400 once version 1 is removed.
     */
    @PostExchange(url = "/reservations", version = "2")
    ReservationResponse reserve(@RequestBody ReservationRequest request);

    /** Version 2 of the stock API: version 1 is deprecated. */
    @GetExchange(url = "/stock/{sku}", version = "2")
    StockResponse stock(@PathVariable String sku);

    record ReservationRequest(UUID orderId, List<Line> lines) {}

    record Line(String sku, int quantity) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReservationResponse(UUID id, UUID orderId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StockResponse(String sku, int onHand, int reserved, int available) {}
}
