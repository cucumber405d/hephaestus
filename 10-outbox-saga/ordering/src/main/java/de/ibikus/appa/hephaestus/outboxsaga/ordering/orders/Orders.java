package de.ibikus.appa.hephaestus.outboxsaga.ordering.orders;

import de.ibikus.appa.hephaestus.outboxsaga.ordering.messaging.SagaMessage;
import de.ibikus.appa.hephaestus.outboxsaga.ordering.messaging.SagaMessage.Line;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Orders, their lines and history, in plain SQL. */
@Repository
public class Orders {

    public record Order(
            UUID id, String customerId, String saga, String status, BigDecimal amount, String reason, List<Line> lines) {}

    public record HistoryEntry(Instant at, String entry) {}

    private final JdbcClient jdbc;

    public Orders(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Order order, String firstTopic, String firstMessage, Instant now) {
        jdbc.sql("insert into purchase_order (id, customer_id, saga, status, amount, first_topic, first_message,"
                        + " created_at) values (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(order.id(), order.customerId(), order.saga(), order.status(), order.amount(), firstTopic,
                        firstMessage, Timestamp.from(now))
                .update();
        for (var line : order.lines()) {
            jdbc.sql("insert into order_line (order_id, sku, quantity) values (?, ?, ?)")
                    .params(order.id(), line.sku(), line.quantity())
                    .update();
        }
    }

    public void history(UUID orderId, String entry) {
        jdbc.sql("insert into order_history (order_id, at, entry) values (?, ?, ?)")
                .params(orderId, Timestamp.from(Instant.now()), entry.length() > 200 ? entry.substring(0, 200) : entry)
                .update();
    }

    /** Settles a pending order; an order that is already settled stays as it is. */
    public boolean settle(UUID orderId, String status, String reason) {
        return jdbc.sql("update purchase_order set status = ?, reason = ? where id = ? and status = 'PENDING'")
                        .params(status, reason, orderId)
                        .update()
                == 1;
    }

    public Optional<Order> find(UUID orderId) {
        var lines = jdbc.sql("select sku, quantity from order_line where order_id = ? order by sku")
                .param(orderId)
                .query((rs, row) -> new Line(rs.getString("sku"), rs.getInt("quantity")))
                .list();
        return jdbc.sql("select id, customer_id, saga, status, amount, reason from purchase_order where id = ?")
                .param(orderId)
                .query((rs, row) -> new Order(
                        rs.getObject("id", UUID.class),
                        rs.getString("customer_id"),
                        rs.getString("saga"),
                        rs.getString("status"),
                        rs.getBigDecimal("amount"),
                        rs.getString("reason"),
                        lines))
                .optional();
    }

    public List<HistoryEntry> historyOf(UUID orderId) {
        return jdbc.sql("select at, entry from order_history where order_id = ? order by id")
                .param(orderId)
                .query((rs, row) -> new HistoryEntry(rs.getTimestamp("at").toInstant(), rs.getString("entry")))
                .list();
    }

    /** The message that started the saga: topic and JSON text. */
    public Optional<String[]> firstMessage(UUID orderId) {
        return jdbc.sql("select first_topic, first_message from purchase_order where id = ?")
                .param(orderId)
                .query((rs, row) -> new String[] {rs.getString("first_topic"), rs.getString("first_message")})
                .optional();
    }

    /** A new message about the order, carrying its data. */
    public static SagaMessage message(Order order, String type, String reason) {
        return new SagaMessage(
                UUID.randomUUID(),
                type,
                order.id(),
                order.saga(),
                order.customerId(),
                order.amount(),
                order.lines(),
                reason,
                Instant.now());
    }
}
