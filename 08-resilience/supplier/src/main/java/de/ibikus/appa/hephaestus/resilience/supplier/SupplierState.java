package de.ibikus.appa.hephaestus.resilience.supplier;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** Current faults and the number of offer calls that actually reached the supplier. */
@Component
class SupplierState {

    private final AtomicLong offerCalls = new AtomicLong();
    private volatile Faults faults = Faults.NONE;

    void countOfferCall() {
        offerCalls.incrementAndGet();
    }

    long offerCalls() {
        return offerCalls.get();
    }

    void resetCalls() {
        offerCalls.set(0);
    }

    Faults faults() {
        return faults;
    }

    void faults(Faults faults) {
        this.faults = faults;
    }
}
