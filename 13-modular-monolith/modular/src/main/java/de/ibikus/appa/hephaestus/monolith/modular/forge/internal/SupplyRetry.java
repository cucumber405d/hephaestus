package de.ibikus.appa.hephaestus.monolith.modular.forge.internal;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Work orders the supplier could not serve yet (transient failure): reorder, one transaction each. */
@Component
class SupplyRetry {

    private final Forge forge;

    SupplyRetry(Forge forge) {
        this.forge = forge;
    }

    @Scheduled(fixedDelayString = "${forge.supply-retry}")
    void retry() {
        forge.waitingForMaterial().forEach(forge::supply);
    }
}
