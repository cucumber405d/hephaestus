package de.ibikus.appa.hephaestus.resilience.inventory.application.fakes;

import de.ibikus.appa.hephaestus.resilience.inventory.application.port.in.Mechanism;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.out.SupplierOffers;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierOffer;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierUnavailableException;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.UnknownOfferException;
import java.math.BigDecimal;

/** A supplier adapter whose next answers the test decides. */
public class FakeSupplierOffers implements SupplierOffers {

    public static final SupplierOffer ANVIL = new SupplierOffer("ANVIL-01", new BigDecimal("120.00"), "EUR", 5, 20);

    private final Mechanism mechanism;
    private volatile String mode = "ok";
    private volatile int calls;

    public FakeSupplierOffers(Mechanism mechanism) {
        this.mechanism = mechanism;
    }

    public void succeed() {
        mode = "ok";
    }

    public void beUnavailable() {
        mode = "down";
    }

    public void knowNothing() {
        mode = "unknown";
    }

    public int calls() {
        return calls;
    }

    @Override
    public Mechanism mechanism() {
        return mechanism;
    }

    @Override
    public SupplierOffer fetch(String sku) {
        calls++;
        return switch (mode) {
            case "down" -> throw new SupplierUnavailableException("down", new RuntimeException("connection refused"));
            case "unknown" -> throw new UnknownOfferException(sku);
            default ->
                new SupplierOffer(sku, ANVIL.price(), ANVIL.currency(), ANVIL.leadTimeDays(), ANVIL.maxQuantity());
        };
    }
}
