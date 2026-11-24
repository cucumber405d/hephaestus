package de.ibikus.appa.hephaestus.monolith.modular.forge.internal;

import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrderRequested;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Takes requests after the ordering transaction committed; the publication registry redelivers after a crash. */
@Component
class WorkOrderRequests {

    private final Forge forge;

    WorkOrderRequests(Forge forge) {
        this.forge = forge;
    }

    @ApplicationModuleListener
    void on(WorkOrderRequested request) {
        forge.request(request.orderId(), request.material(), request.quantity());
    }
}
