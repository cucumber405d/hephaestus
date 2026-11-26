package de.ibikus.appa.hephaestus.es.forge.application.projection;

import de.ibikus.appa.hephaestus.es.forge.application.eventstore.Checkpoint;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Replay: empty a read model and move its checkpoint to the journal's start; the runner rebuilds it from there. */
@Service
public class ProjectionAdmin {

    /** The ForgeEvent publisher reads the journal like a projection but its output (Kafka) cannot be reset. */
    public static final String PUBLISHER = "forge-events-publisher";

    private final List<Projection> projections;
    private final CheckpointStore checkpoints;
    private final TransactionTemplate tx;
    private final ProjectionRunner runner;

    public ProjectionAdmin(
            List<Projection> projections,
            CheckpointStore checkpoints,
            TransactionTemplate tx,
            ProjectionRunner runner) {
        this.projections = projections;
        this.checkpoints = checkpoints;
        this.tx = tx;
        this.runner = runner;
    }

    /** Waits for a running batch of the projection (the checkpoint row lock), then resets it in one transaction. */
    public void rebuild(String name) {
        if (PUBLISHER.equals(name)) {
            throw new NotRebuildable(name + ": its events already left for Kafka");
        }
        var projection = find(name);
        tx.executeWithoutResult(status -> {
            checkpoints.lock(name);
            projection.reset();
            checkpoints.save(name, Checkpoint.START);
        });
    }

    public List<ProjectionStatus> status() {
        return projections.stream()
                .map(p -> {
                    var checkpoint = checkpoints.get(p.name());
                    return new ProjectionStatus(
                            p.name(),
                            runner.isEnabled(p.name()),
                            checkpoint.txId(),
                            checkpoint.position(),
                            checkpoints.lag(p.name()),
                            runner.lastError(p.name()));
                })
                .toList();
    }

    private Projection find(String name) {
        return projections.stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new ProjectionNotFound(name));
    }
}
