package de.ibikus.appa.hephaestus.eventskafka.ordering.adapter.out.inventory;

import de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.out.InventoryPort;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.OrderLine;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Inventory is not the topic of 09: a local stand-in that always reserves. The synchronous call to the real
 * Inventory is subproject 06; making it reliable across services is subproject 10.
 */
@Component
class LocalInventory implements InventoryPort {

    @Override
    public UUID reserve(UUID orderId, List<OrderLine> lines) {
        return UUID.randomUUID();
    }

    @Override
    public int available(String sku) {
        return 100;
    }
}
