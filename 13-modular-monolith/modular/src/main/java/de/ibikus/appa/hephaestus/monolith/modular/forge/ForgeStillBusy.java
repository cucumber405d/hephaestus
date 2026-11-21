package de.ibikus.appa.hephaestus.monolith.modular.forge;

/** The local forge cannot be decommissioned: work orders are still open. */
public class ForgeStillBusy extends RuntimeException {

    private final int openWorkOrders;

    public ForgeStillBusy(int openWorkOrders) {
        super(openWorkOrders + " work order(s) still open");
        this.openWorkOrders = openWorkOrders;
    }

    public int openWorkOrders() {
        return openWorkOrders;
    }
}
