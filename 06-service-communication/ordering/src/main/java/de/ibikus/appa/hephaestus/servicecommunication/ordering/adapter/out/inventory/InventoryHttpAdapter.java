package de.ibikus.appa.hephaestus.servicecommunication.ordering.adapter.out.inventory;

import de.ibikus.appa.hephaestus.servicecommunication.ordering.adapter.out.inventory.InventoryHttpApi.Line;
import de.ibikus.appa.hephaestus.servicecommunication.ordering.adapter.out.inventory.InventoryHttpApi.ReservationRequest;
import de.ibikus.appa.hephaestus.servicecommunication.ordering.application.port.out.InventoryPort;
import de.ibikus.appa.hephaestus.servicecommunication.ordering.domain.InsufficientStockException;
import de.ibikus.appa.hephaestus.servicecommunication.ordering.domain.InventoryUnavailableException;
import de.ibikus.appa.hephaestus.servicecommunication.ordering.domain.OrderLine;
import de.ibikus.appa.hephaestus.servicecommunication.ordering.domain.UnknownItemException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Translates between Ordering's port and Inventory's HTTP API, and translates Inventory's failures into
 * Ordering's language. No retries here: retries need idempotency and a budget (subproject 08).
 */
@Component
class InventoryHttpAdapter implements InventoryPort {

    private final InventoryHttpApi api;

    InventoryHttpAdapter(InventoryHttpApi api) {
        this.api = api;
    }

    @Override
    public UUID reserve(UUID orderId, List<OrderLine> lines) {
        var request = new ReservationRequest(
                orderId,
                lines.stream().map(line -> new Line(line.sku(), line.quantity())).toList());
        return call("reserve stock", () -> api.reserve(request).id(), lines.getFirst().sku());
    }

    @Override
    public int available(String sku) {
        return call("read stock of " + sku, () -> api.stock(sku).available(), sku);
    }

    private static <T> T call(String what, Supplier<T> call, String fallbackSku) {
        try {
            return call.get();
        } catch (HttpClientErrorException e) {
            var sku = skuOf(e, fallbackSku);
            switch (e.getStatusCode().value()) {
                case 409 -> throw new InsufficientStockException(sku);
                case 404, 422 -> throw new UnknownItemException(sku);
                // Any other 4xx means we sent something Inventory does not accept: our bug, a 500 is right
                default -> throw new IllegalStateException("Inventory rejected " + what + ": " + e.getStatusCode(), e);
            }
        } catch (HttpServerErrorException | ResourceAccessException e) {
            // 5xx, connection refused, connect or read timeout: Inventory cannot answer now
            throw new InventoryUnavailableException("Inventory unavailable while trying to " + what, e);
        }
    }

    /** Inventory names the offending SKU in its Problem Details; fall back to what we asked about. */
    private static String skuOf(HttpClientErrorException e, String fallback) {
        try {
            var problem = e.getResponseBodyAs(ProblemDetail.class);
            if (problem != null
                    && problem.getProperties() != null
                    && problem.getProperties().get("sku") instanceof String sku) {
                return sku;
            }
        } catch (RuntimeException ignored) {
            // not a Problem Details body
        }
        return fallback;
    }
}
