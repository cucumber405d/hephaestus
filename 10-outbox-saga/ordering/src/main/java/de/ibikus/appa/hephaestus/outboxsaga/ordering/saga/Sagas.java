package de.ibikus.appa.hephaestus.outboxsaga.ordering.saga;

import de.ibikus.appa.hephaestus.outboxsaga.ordering.saga.Orchestrator.Saga;
import de.ibikus.appa.hephaestus.outboxsaga.ordering.saga.Orchestrator.State;
import de.ibikus.appa.hephaestus.outboxsaga.ordering.saga.Orchestrator.Step;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The orchestrator's state per order. Rows are locked while a reply or a timeout is applied. */
@Repository
public class Sagas {

    private final JdbcClient jdbc;

    public Sagas(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID orderId, Saga saga, Instant deadline) {
        jdbc.sql("insert into saga (order_id, step, state, deadline, pending_compensations) values (?, ?, ?, ?, ?)")
                .params(orderId, saga.step().name(), saga.state().name(), Timestamp.from(deadline),
                        saga.pendingCompensations())
                .update();
    }

    public Optional<Saga> lock(UUID orderId) {
        return jdbc.sql("select step, state, pending_compensations from saga where order_id = ? for update")
                .param(orderId)
                .query((rs, row) -> new Saga(
                        Step.valueOf(rs.getString("step")),
                        State.valueOf(rs.getString("state")),
                        rs.getInt("pending_compensations")))
                .optional();
    }

    /** Deadline only for a running step; a settled or compensating saga has none. */
    public void update(UUID orderId, Saga saga, Instant deadline) {
        jdbc.sql("update saga set step = ?, state = ?, deadline = ?, pending_compensations = ? where order_id = ?")
                .params(saga.step().name(), saga.state().name(),
                        saga.state() == State.RUNNING ? Timestamp.from(deadline) : null,
                        saga.pendingCompensations(), orderId)
                .update();
    }

    /** Running sagas whose step is overdue; skipped if another instance is handling them right now. */
    public List<UUID> overdue(Instant now) {
        return jdbc.sql("select order_id from saga where state = 'RUNNING' and deadline < ? for update skip locked")
                .param(Timestamp.from(now))
                .query(UUID.class)
                .list();
    }
}
