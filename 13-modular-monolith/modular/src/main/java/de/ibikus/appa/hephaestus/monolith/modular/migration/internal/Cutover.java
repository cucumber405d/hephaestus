package de.ibikus.appa.hephaestus.monolith.modular.migration.internal;

import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two state changes of the Strangler Fig, each in one transaction with the check that guards it. Decommission holds
 * the cutover row for share while it switches the forge off; a rollback updates that row first, so it waits for a
 * running decommission and then sees it — "decommissioned, but new orders go local" cannot happen (review C1).
 */
@Component
class Cutover {

    static class Refused extends RuntimeException {

        Refused(String message) {
            super(message);
        }
    }

    private final JdbcForgeRouting routing;
    private final WorkOrders workOrders;

    Cutover(JdbcForgeRouting routing, WorkOrders workOrders) {
        this.routing = routing;
        this.workOrders = workOrders;
    }

    @Transactional
    void route(boolean remote) {
        routing.remote(remote);
        if (!remote && workOrders.decommissioned()) {
            throw new Refused("the local forge is decommissioned: no way back");
        }
    }

    /** @throws de.ibikus.appa.hephaestus.monolith.modular.forge.ForgeStillBusy while anything keeps the forge busy */
    @Transactional
    void decommission() {
        if (!routing.remote()) {
            throw new Refused("new work orders still go to the local forge: cut over first");
        }
        workOrders.decommission();
    }
}
