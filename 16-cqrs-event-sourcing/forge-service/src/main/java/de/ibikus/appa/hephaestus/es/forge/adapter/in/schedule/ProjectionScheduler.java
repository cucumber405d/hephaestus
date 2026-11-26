package de.ibikus.appa.hephaestus.es.forge.adapter.in.schedule;

import de.ibikus.appa.hephaestus.es.forge.application.projection.ProjectionRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the journal for the projections every PROJECTIONS_POLL_INTERVAL, until all are caught up. */
@Component
class ProjectionScheduler {

    private final ProjectionRunner runner;

    ProjectionScheduler(ProjectionRunner runner) {
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${projections.poll-interval:200ms}", scheduler = Schedulers.PROJECTIONS)
    void poll() {
        while (runner.runOnce() > 0) {
            // keep going while there is work: a backlog is applied batch after batch, not one batch per interval
        }
    }
}
