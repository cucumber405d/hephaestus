package de.ibikus.appa.hephaestus.outboxsaga.inventory.stock;

import de.ibikus.appa.hephaestus.outboxsaga.inventory.messaging.SagaMessage.Line;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock rules: an order reserves all its lines or none; one reservation per order; releasing gives exactly the
 * reserved quantities back, once. Rows are locked in SKU order (no deadlocks between concurrent orders).
 */
@Component
public class Stock {

    public enum Outcome {
        RESERVED,
        UNAVAILABLE
    }

    private final JdbcClient jdbc;

    public Stock(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome reserve(UUID orderId, List<Line> lines) {
        var previous = status(orderId);
        if (previous.isPresent()) {
            return "RESERVED".equals(previous.get()) ? Outcome.RESERVED : Outcome.UNAVAILABLE;
        }
        var sorted = lines.stream().sorted(Comparator.comparing(Line::sku)).toList();
        for (var line : sorted) {
            var available = jdbc.sql("select on_hand - reserved from stock_item where sku = ? for update")
                    .param(line.sku())
                    .query(Integer.class)
                    .optional()
                    .orElse(0);
            if (line.quantity() > available) {
                record(orderId, "UNAVAILABLE");
                return Outcome.UNAVAILABLE;
            }
        }
        record(orderId, "RESERVED");
        for (var line : sorted) {
            jdbc.sql("update stock_item set reserved = reserved + ? where sku = ?")
                    .params(line.quantity(), line.sku())
                    .update();
            jdbc.sql("insert into stock_reservation_line (order_id, sku, quantity) values (?, ?, ?)")
                    .params(orderId, line.sku(), line.quantity())
                    .update();
        }
        return Outcome.RESERVED;
    }

    /** Gives reserved stock back; anything else is a no-op. Returns whether stock was given back. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean release(UUID orderId) {
        var status = status(orderId);
        if (status.isEmpty()) {
            record(orderId, "RELEASED");
            return false;
        }
        if (!"RESERVED".equals(status.get())) {
            return false;
        }
        jdbc.sql("update stock_item s set reserved = s.reserved - l.quantity from stock_reservation_line l"
                        + " where l.order_id = ? and l.sku = s.sku")
                .param(orderId)
                .update();
        jdbc.sql("update stock_reservation set status = 'RELEASED', updated_at = ? where order_id = ?")
                .params(Timestamp.from(Instant.now()), orderId)
                .update();
        return true;
    }

    public Optional<String> status(UUID orderId) {
        return jdbc.sql("select status from stock_reservation where order_id = ?")
                .param(orderId)
                .query(String.class)
                .optional();
    }

    public int reserved(String sku) {
        return jdbc.sql("select reserved from stock_item where sku = ?")
                .param(sku)
                .query(Integer.class)
                .single();
    }

    private void record(UUID orderId, String status) {
        jdbc.sql("insert into stock_reservation (order_id, status, updated_at) values (?, ?, ?)")
                .params(orderId, status, Timestamp.from(Instant.now()))
                .update();
    }
}
