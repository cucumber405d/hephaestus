package de.ibikus.appa.hephaestus.observability.ordering.messaging;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The outbox's safety net: a publication {@link OutboxRelay} could not send (Kafka down) stays incomplete in the
 * event_publication table and is resubmitted here until it goes through.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class OutboxConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxConfiguration.class);
    static final Duration RESUBMIT_OLDER_THAN = Duration.ofSeconds(10);

    @Bean
    OutboxResubmitter outboxResubmitter(IncompleteEventPublications incomplete) {
        return new OutboxResubmitter(incomplete);
    }

    static class OutboxResubmitter {

        private final IncompleteEventPublications incomplete;

        OutboxResubmitter(IncompleteEventPublications incomplete) {
            this.incomplete = incomplete;
        }

        @Scheduled(initialDelay = 10_000, fixedDelay = 10_000)
        void resubmit() {
            try {
                incomplete.resubmitIncompletePublicationsOlderThan(RESUBMIT_OLDER_THAN);
            } catch (RuntimeException e) {
                LOG.warn("Outbox resubmission failed, next attempt in 10 s: {}", e.toString());
            }
        }
    }
}
