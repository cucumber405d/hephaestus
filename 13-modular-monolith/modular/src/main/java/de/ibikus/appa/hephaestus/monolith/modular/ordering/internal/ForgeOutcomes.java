package de.ibikus.appa.hephaestus.monolith.modular.ordering.internal;

import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrderCompleted;
import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrderFailed;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Forge's outcomes, after Forge's transaction committed. */
@Component
class ForgeOutcomes {

    private final OrderService orders;

    ForgeOutcomes(OrderService orders) {
        this.orders = orders;
    }

    @ApplicationModuleListener
    void on(WorkOrderCompleted completed) {
        orders.markReady(completed.orderId());
    }

    @ApplicationModuleListener
    void on(WorkOrderFailed failed) {
        orders.reject(failed.orderId(), failed.reason());
    }
}
