package de.ibikus.appa.hephaestus.eventskafka.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.eventskafka.notification.application.port.in.HandleOrderPlaced.Outcome;
import de.ibikus.appa.hephaestus.eventskafka.notification.application.port.out.NotificationStore;
import de.ibikus.appa.hephaestus.eventskafka.notification.domain.InvalidEventException;
import de.ibikus.appa.hephaestus.eventskafka.notification.domain.Notification;
import de.ibikus.appa.hephaestus.eventskafka.notification.domain.NotificationFailedException;
import de.ibikus.appa.hephaestus.eventskafka.notification.domain.OrderPlaced;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    /** In memory, with the same once-per-event-id contract as the database. */
    static final class InMemoryStore implements NotificationStore {

        final Set<UUID> processed = ConcurrentHashMap.newKeySet();
        final List<Notification> notifications = new ArrayList<>();

        @Override
        public synchronized boolean saveOnce(UUID eventId, Notification notification) {
            if (!processed.add(eventId)) {
                return false;
            }
            notifications.add(notification);
            return true;
        }

        @Override
        public synchronized List<Notification> findByCustomer(String customerId) {
            return notifications.stream()
                    .filter(n -> n.customerId().equals(customerId))
                    .toList();
        }
    }

    private final InMemoryStore store = new InMemoryStore();
    private final NotificationService service = new NotificationService(store, Clock.systemUTC());

    @Test
    void eventBecomesANotification() {
        var event = event("alice");

        assertThat(service.handle(event)).isEqualTo(Outcome.NOTIFIED);

        assertThat(service.forCustomer("alice")).singleElement().satisfies(n -> {
            assertThat(n.orderId()).isEqualTo(event.orderId());
            assertThat(n.text()).contains("alice").contains("2 item(s)");
        });
    }

    @Test
    void sameEventTwiceNotifiesOnce() {
        var event = event("alice");
        service.handle(event);

        assertThat(service.handle(event)).isEqualTo(Outcome.DUPLICATE);

        assertThat(service.forCustomer("alice")).hasSize(1);
        assertThat(service.duplicates()).isEqualTo(1);
    }

    @Test
    void failingCustomerAlwaysFailsOthersDoNot() {
        service.faults(new Faults(Set.of("bob"), 0, 0));

        assertThatThrownBy(() -> service.handle(event("bob"))).isInstanceOf(NotificationFailedException.class);
        assertThatThrownBy(() -> service.handle(event("bob"))).isInstanceOf(NotificationFailedException.class);
        assertThat(service.handle(event("alice"))).isEqualTo(Outcome.NOTIFIED);
    }

    @Test
    void transientFailuresFailTheNextAttemptsThenRecover() {
        service.faults(new Faults(Set.of(), 2, 0));
        var event = event("alice");

        assertThatThrownBy(() -> service.handle(event)).isInstanceOf(NotificationFailedException.class);
        assertThatThrownBy(() -> service.handle(event)).isInstanceOf(NotificationFailedException.class);
        assertThat(service.handle(event)).isEqualTo(Outcome.NOTIFIED);
    }

    @Test
    void eventWithoutCustomerIsInvalid() {
        assertThatThrownBy(() -> new OrderPlaced(UUID.randomUUID(), UUID.randomUUID(), " ", lines(), Instant.now()))
                .isInstanceOf(InvalidEventException.class);
    }

    static OrderPlaced event(String customer) {
        return new OrderPlaced(UUID.randomUUID(), UUID.randomUUID(), customer, lines(), Instant.now());
    }

    private static List<OrderPlaced.Line> lines() {
        return List.of(new OrderPlaced.Line("ANVIL-01", 2));
    }
}
