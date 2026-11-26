package de.ibikus.appa.hephaestus.es.forge.application;

import static org.awaitility.Awaitility.await;

import de.ibikus.appa.hephaestus.es.forge.Containers;
import de.ibikus.appa.hephaestus.es.forge.FakeSupplier;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A publisher stuck on the broker (Kafka down: the send blocks) must not stop the read side: the projections run on
 * their own thread and the publisher holds no transaction — and so no xid that would hold back the journal's xmin —
 * while it waits.
 */
@SpringBootTest
@Import({Containers.class, BlockedPublisherIT.Sink.class})
class BlockedPublisherIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Sink {
        @Bean
        @Primary
        BlockingSink blockingSink() {
            return new BlockingSink();
        }
    }

    /** Blocks every send until released, like a producer waiting for metadata of an unreachable broker. */
    static final class BlockingSink implements OutcomeSink {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public void send(Outcome outcome) {
            entered.countDown();
            try {
                release.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("broker unreachable");
        }
    }

    @Autowired
    WorkOrders workOrders;

    @Autowired
    WorkshopBoard board;

    @Autowired
    BlockingSink sink;

    @DynamicPropertySource
    static void supplier(DynamicPropertyRegistry registry) {
        registry.add("supplier.url", FakeSupplier::url);
    }

    @AfterEach
    void release() {
        sink.release.countDown();
    }

    @Test
    void projectionsKeepUpdatingWhileThePublisherWaitsForTheBroker() throws InterruptedException {
        // a failed work order is an outcome: the publisher picks it up and blocks in send
        workOrders.request(UUID.randomUUID(), "MITHRIL-01", 1, Optional.empty());
        await().atMost(Duration.ofSeconds(15)).until(() -> sink.entered.getCount() == 0);

        var orderId = UUID.randomUUID();
        workOrders.request(orderId, "IRON-01", 1, Optional.empty());

        await().atMost(Duration.ofSeconds(5))
                .until(() -> board.list(Optional.empty()).stream()
                        .anyMatch(entry -> entry.orderId().equals(orderId)));
    }
}
