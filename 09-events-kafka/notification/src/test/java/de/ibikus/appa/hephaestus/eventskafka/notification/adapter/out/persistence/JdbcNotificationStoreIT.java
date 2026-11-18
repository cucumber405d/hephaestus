package de.ibikus.appa.hephaestus.eventskafka.notification.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.eventskafka.notification.PostgresTestcontainers;
import de.ibikus.appa.hephaestus.eventskafka.notification.domain.Notification;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Real PostgreSQL: the same event id from two consumers at once still gives exactly one notification. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainers.class, JdbcNotificationStore.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JdbcNotificationStoreIT {

    @Autowired
    JdbcNotificationStore store;

    @Test
    void secondSaveOfTheSameEventIsRefused() {
        var eventId = UUID.randomUUID();

        assertThat(store.saveOnce(eventId, notification("carol"))).isTrue();
        assertThat(store.saveOnce(eventId, notification("carol"))).isFalse();
        assertThat(store.findByCustomer("carol")).hasSize(1);
    }

    @Test
    void concurrentSavesOfOneEventStoreOneNotification() throws Exception {
        var eventId = UUID.randomUUID();
        var customer = "race-" + eventId;
        var start = new CountDownLatch(1);
        var results = new ArrayList<Future<Boolean>>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 4; i++) {
                Callable<Boolean> save = () -> {
                    start.await();
                    return store.saveOnce(eventId, notification(customer));
                };
                results.add(pool.submit(save));
            }
            start.countDown();
            var stored = 0;
            for (var result : results) {
                stored += result.get() ? 1 : 0;
            }
            assertThat(stored).isEqualTo(1);
        }
        assertThat(store.findByCustomer(customer)).hasSize(1);
    }

    private static Notification notification(String customer) {
        return new Notification(UUID.randomUUID(), customer, UUID.randomUUID(), "EMAIL", "hi", Instant.now());
    }
}
