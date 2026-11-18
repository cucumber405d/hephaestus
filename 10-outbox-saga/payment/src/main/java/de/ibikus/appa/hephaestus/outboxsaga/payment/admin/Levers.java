package de.ibikus.appa.hephaestus.outboxsaga.payment.admin;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** Current faults and counters of this instance. */
@Component
public class Levers {

    private volatile Faults faults = Faults.NONE;
    private final AtomicLong handled = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();

    public Faults faults() {
        return faults;
    }

    public void faults(Faults faults) {
        this.faults = faults;
    }

    public boolean failsFor(String customerId) {
        return faults.failCustomers().contains(customerId);
    }

    public void delay() {
        if (faults.delayMs() > 0) {
            try {
                Thread.sleep(faults.delayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void handled() {
        handled.incrementAndGet();
    }

    public void duplicate() {
        duplicates.incrementAndGet();
    }

    public long handledCount() {
        return handled.get();
    }

    public long duplicateCount() {
        return duplicates.get();
    }
}
