package de.ibikus.appa.hephaestus.monolith.modular.forge;

/** The local forge cannot be decommissioned: work orders are open or requests to it are still pending. */
public class ForgeStillBusy extends RuntimeException {

    private final int openWorkOrders;

    public ForgeStillBusy(int openWorkOrders) {
        super(openWorkOrders + " work order(s) still open or pending");
        this.openWorkOrders = openWorkOrders;
    }

    public int openWorkOrders() {
        return openWorkOrders;
    }
}
