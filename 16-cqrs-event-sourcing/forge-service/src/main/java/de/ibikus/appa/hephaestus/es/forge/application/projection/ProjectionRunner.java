package de.ibikus.appa.hephaestus.es.forge.application.projection;

import de.ibikus.appa.hephaestus.es.forge.application.eventstore.Checkpoint;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.EventCodec;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.EventStore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Feeds every enabled projection one batch per call: lock its checkpoint row, read the journal after it, apply, save
 * the new checkpoint — one transaction, so the read model and the checkpoint move together or not at all. A failing
 * batch (a handler error, an event this build cannot read) leaves the checkpoint where it was and is retried on the
 * next call; the other projections go on.
 */
@Service
public class ProjectionRunner {

    private static final Logger log = LoggerFactory.getLogger(ProjectionRunner.class);

    private final List<Projection> projections;
    private final EventStore store;
    private final EventCodec codec;
    private final CheckpointStore checkpoints;
    private final TransactionTemplate tx;
    private final Predicate<String> enabled;
    private final int batchSize;
    private final Map<String, String> lastErrors = new ConcurrentHashMap<>();

    @Autowired
    public ProjectionRunner(
            List<Projection> projections,
            EventStore store,
            EventCodec codec,
            CheckpointStore checkpoints,
            TransactionTemplate tx,
            Environment environment,
            @Value("${projections.batch-size:500}") int batchSize) {
        this(
                projections,
                store,
                codec,
                checkpoints,
                tx,
                name -> environment.getProperty("projections." + name + ".enabled", Boolean.class, true),
                batchSize);
    }

    public ProjectionRunner(
            List<Projection> projections,
            EventStore store,
            EventCodec codec,
            CheckpointStore checkpoints,
            TransactionTemplate tx,
            Predicate<String> enabled,
            int batchSize) {
        this.projections = projections;
        this.store = store;
        this.codec = codec;
        this.checkpoints = checkpoints;
        this.tx = tx;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    /** One batch per enabled projection; returns the number of events applied (0 = all caught up). */
    public int runOnce() {
        int applied = 0;
        for (var projection : projections) {
            if (!enabled.test(projection.name())) {
                continue;
            }
            try {
                Integer n = tx.execute(status -> batch(projection));
                applied += n == null ? 0 : n;
                lastErrors.remove(projection.name());
            } catch (RuntimeException e) {
                lastErrors.put(projection.name(), String.valueOf(e.getMessage()));
                log.error("projection {} stopped before an event it cannot apply", projection.name(), e);
            }
        }
        return applied;
    }

    public boolean isEnabled(String name) {
        return enabled.test(name);
    }

    /** The last error of the projection, empty when its last batch succeeded. */
    public String lastError(String name) {
        return lastErrors.getOrDefault(name, "");
    }

    private int batch(Projection projection) {
        var checkpoint = checkpoints.lock(projection.name());
        var events = store.readAll(checkpoint, batchSize);
        for (var event : events) {
            projection.apply(codec.decode(event), event);
        }
        if (!events.isEmpty()) {
            checkpoints.save(projection.name(), Checkpoint.of(events.getLast()));
        }
        return events.size();
    }
}
