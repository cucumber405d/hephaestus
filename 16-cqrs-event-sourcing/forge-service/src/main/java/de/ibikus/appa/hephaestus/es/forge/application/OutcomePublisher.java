package de.ibikus.appa.hephaestus.es.forge.application;

import de.ibikus.appa.hephaestus.es.forge.application.eventstore.Checkpoint;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.EventCodec;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.EventStore;
import de.ibikus.appa.hephaestus.es.forge.application.projection.CheckpointStore;
import de.ibikus.appa.hephaestus.es.forge.application.projection.ProjectionAdmin;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderCompleted;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderFailed;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The journal as outbox. A reader like the projections — its own checkpoint row, locked for the batch — whose
 * "read model" is the topic: every {@code WorkOrderCompleted} and {@code WorkOrderFailed} after the checkpoint goes to
 * the sink, each send waits for the ack, and only then the checkpoint moves (same transaction). A failed send rolls
 * the batch back: the checkpoint stays, the next call sends again — at least once, with a stable event id. All other
 * event types are skipped, and the checkpoint moves past them.
 */
@Service
public class OutcomePublisher {

    private final EventStore store;
    private final EventCodec codec;
    private final CheckpointStore checkpoints;
    private final WorkOrderRepository workOrders;
    private final OutcomeSink sink;
    private final TransactionTemplate tx;
    private final String name;
    private final int batchSize;

    @Autowired
    public OutcomePublisher(
            EventStore store,
            EventCodec codec,
            CheckpointStore checkpoints,
            WorkOrderRepository workOrders,
            OutcomeSink sink,
            TransactionTemplate tx,
            @Value("${publisher.batch-size:100}") int batchSize) {
        this(store, codec, checkpoints, workOrders, sink, tx, ProjectionAdmin.PUBLISHER, batchSize);
    }

    public OutcomePublisher(
            EventStore store,
            EventCodec codec,
            CheckpointStore checkpoints,
            WorkOrderRepository workOrders,
            OutcomeSink sink,
            TransactionTemplate tx,
            String name,
            int batchSize) {
        this.store = store;
        this.codec = codec;
        this.checkpoints = checkpoints;
        this.workOrders = workOrders;
        this.sink = sink;
        this.tx = tx;
        this.name = name;
        this.batchSize = batchSize;
    }

    /** One batch; returns the number of journal events passed (0 = caught up). Throws when a send failed. */
    public int publishOnce() {
        Integer n = tx.execute(status -> batch());
        return n == null ? 0 : n;
    }

    private int batch() {
        var checkpoint = checkpoints.lock(name);
        var events = store.readAll(checkpoint, batchSize);
        for (var row : events) {
            var event = codec.decode(row);
            var kind = switch (event) {
                case WorkOrderCompleted _ -> Outcome.Kind.COMPLETED;
                case WorkOrderFailed _ -> Outcome.Kind.FAILED;
                default -> null;
            };
            if (kind == null) {
                continue;
            }
            var state = workOrders.load(row.streamId()).state();
            var reason = event instanceof WorkOrderFailed failed ? failed.reason() : null;
            var eventId =
                    UUID.nameUUIDFromBytes((row.streamId() + "/" + row.version()).getBytes(StandardCharsets.UTF_8));
            sink.send(new Outcome(eventId, kind, state.orderId(), row.streamId(), reason));
        }
        if (!events.isEmpty()) {
            checkpoints.save(name, Checkpoint.of(events.getLast()));
        }
        return events.size();
    }
}
