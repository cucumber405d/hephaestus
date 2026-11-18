package de.ibikus.appa.hephaestus.eventskafka.notification.application.service;

import de.ibikus.appa.hephaestus.eventskafka.notification.application.port.in.HandleOrderPlaced;
import de.ibikus.appa.hephaestus.eventskafka.notification.application.port.in.NotificationQuery;
import de.ibikus.appa.hephaestus.eventskafka.notification.application.port.out.NotificationStore;
import de.ibikus.appa.hephaestus.eventskafka.notification.domain.Notification;
import de.ibikus.appa.hephaestus.eventskafka.notification.domain.NotificationFailedException;
import de.ibikus.appa.hephaestus.eventskafka.notification.domain.OrderPlaced;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Kafka delivers at least once: after a crash or a rebalance an event may come again, and Ordering may re-send it.
 * The event id makes handling idempotent: the second time it is recognised and skipped.
 */
@Service
public class NotificationService implements HandleOrderPlaced, NotificationQuery {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationStore store;
    private final Clock clock;
    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();
    private final AtomicInteger transientFailuresLeft = new AtomicInteger();
    private volatile Faults faults = Faults.NONE;

    public NotificationService(NotificationStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    public Outcome handle(OrderPlaced event) {
        injectFaults(event);
        var notification = Notification.orderConfirmed(event, clock.instant());
        if (!store.saveOnce(event.eventId(), notification)) {
            duplicates.incrementAndGet();
            LOG.info("Duplicate event {} for order {}: skipped", event.eventId(), event.orderId());
            return Outcome.DUPLICATE;
        }
        processed.incrementAndGet();
        LOG.info("Notified {}: {}", event.customerId(), notification.text());
        return Outcome.NOTIFIED;
    }

    @Override
    public List<Notification> forCustomer(String customerId) {
        return store.findByCustomer(customerId);
    }

    public void faults(Faults faults) {
        this.faults = faults;
        transientFailuresLeft.set(faults.transientFailures());
    }

    public Faults faults() {
        return faults;
    }

    public long processed() {
        return processed.get();
    }

    public long duplicates() {
        return duplicates.get();
    }

    private void injectFaults(OrderPlaced event) {
        var current = faults;
        if (current.delayMs() > 0) {
            try {
                Thread.sleep(current.delayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NotificationFailedException("Interrupted");
            }
        }
        if (current.failCustomers().contains(event.customerId())) {
            throw new NotificationFailedException("Injected: cannot notify " + event.customerId());
        }
        if (transientFailuresLeft.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new NotificationFailedException("Injected transient failure for event " + event.eventId());
        }
    }
}
