package de.ibikus.appa.hephaestus.es.forge.adapter.in.schedule;

import de.ibikus.appa.hephaestus.es.forge.application.OutcomePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the journal for outcomes to publish; a failed send is logged and retried on the next tick. */
@Component
class PublisherScheduler {

    private static final Logger log = LoggerFactory.getLogger(PublisherScheduler.class);

    private final OutcomePublisher publisher;

    PublisherScheduler(OutcomePublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${publisher.poll-interval:200ms}")
    void poll() {
        try {
            while (publisher.publishOnce() > 0) {
                // drain the backlog batch after batch
            }
        } catch (RuntimeException e) {
            log.warn("forge events not published yet, retrying: {}", e.getMessage());
        }
    }
}
