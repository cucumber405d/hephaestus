package de.ibikus.appa.hephaestus.es.forge.adapter.in.schedule;

import de.ibikus.appa.hephaestus.es.forge.application.WorkOrders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Work orders the supplier could not serve yet: reorder every FORGE_SUPPLY_RETRY. */
@Component
class SupplyRetryScheduler {

    private final WorkOrders workOrders;

    SupplyRetryScheduler(WorkOrders workOrders) {
        this.workOrders = workOrders;
    }

    @Scheduled(fixedDelayString = "${forge.supply-retry}", scheduler = Schedulers.SUPPLY)
    void retry() {
        workOrders.retryWaitingForMaterial();
    }
}
