package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.spring;

import de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.SupplierCall;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.in.Mechanism;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.out.SupplierOffers;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierOffer;
import org.springframework.stereotype.Component;

/** Mechanism 3: Spring Framework 7 built-in resilience (see {@link RetryingSupplierCall}); no extra library. */
@Component
public class SpringResilientSupplierOffers implements SupplierOffers {

    private final RetryingSupplierCall call;

    public SpringResilientSupplierOffers(RetryingSupplierCall call) {
        this.call = call;
    }

    @Override
    public Mechanism mechanism() {
        return Mechanism.SPRING;
    }

    @Override
    public SupplierOffer fetch(String sku) {
        try {
            return call.fetch(sku);
        } catch (RuntimeException e) {
            throw SupplierCall.translate(Mechanism.SPRING.id(), sku, e);
        }
    }
}
