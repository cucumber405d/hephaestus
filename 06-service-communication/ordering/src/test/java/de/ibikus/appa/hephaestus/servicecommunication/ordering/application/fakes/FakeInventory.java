package de.ibikus.appa.hephaestus.servicecommunication.ordering.application.fakes;

import de.ibikus.appa.hephaestus.servicecommunication.ordering.application.port.out.InventoryPort;
import de.ibikus.appa.hephaestus.servicecommunication.ordering.domain.OrderLine;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Inventory port double: reserves everything unless told to fail with a given exception. */
public class FakeInventory implements InventoryPort {

    private RuntimeException failure;
    private final List<List<OrderLine>> reservations = new ArrayList<>();

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public void succeed() {
        this.failure = null;
    }

    public List<List<OrderLine>> reservations() {
        return reservations;
    }

    @Override
    public UUID reserve(UUID orderId, List<OrderLine> lines) {
        if (failure != null) {
            throw failure;
        }
        reservations.add(lines);
        return UUID.randomUUID();
    }

    @Override
    public int available(String sku) {
        if (failure != null) {
            throw failure;
        }
        return 7;
    }
}
