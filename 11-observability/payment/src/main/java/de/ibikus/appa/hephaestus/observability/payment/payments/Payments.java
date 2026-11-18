package de.ibikus.appa.hephaestus.observability.payment.payments;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The payment rules. One reservation per order; reserving and refunding the same order again changes nothing
 * (safe under redelivery). Customers without an account get the default limit.
 */
@Component
public class Payments {

    public static final BigDecimal DEFAULT_LIMIT = new BigDecimal("500.00");

    public enum Outcome {
        RESERVED,
        DECLINED
    }

    private final JdbcClient jdbc;

    public Payments(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome reserve(UUID orderId, String customerId, BigDecimal amount) {
        var previous = status(orderId);
        if (previous.isPresent()) {
            return "RESERVED".equals(previous.get()) ? Outcome.RESERVED : Outcome.DECLINED;
        }
        jdbc.sql("insert into account (customer_id, credit_limit) values (?, ?) on conflict do nothing")
                .params(customerId, DEFAULT_LIMIT)
                .update();
        var available = jdbc.sql("select credit_limit - reserved from account where customer_id = ? for update")
                .param(customerId)
                .query(BigDecimal.class)
                .single();
        if (amount.compareTo(available) > 0) {
            record(orderId, customerId, amount, "DECLINED");
            return Outcome.DECLINED;
        }
        jdbc.sql("update account set reserved = reserved + ? where customer_id = ?")
                .params(amount, customerId)
                .update();
        record(orderId, customerId, amount, "RESERVED");
        return Outcome.RESERVED;
    }

    /** Gives a reserved amount back; anything else is a no-op. Returns whether money was given back. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean refund(UUID orderId, String customerId) {
        var status = status(orderId);
        if (status.isEmpty()) {
            // Refund before any reservation (an orchestrator timeout): remember it, so a late reservation is refused
            record(orderId, customerId, BigDecimal.ZERO, "REFUNDED");
            return false;
        }
        if (!"RESERVED".equals(status.get())) {
            return false;
        }
        var amount = jdbc.sql("select amount from payment_reservation where order_id = ? for update")
                .param(orderId)
                .query(BigDecimal.class)
                .single();
        jdbc.sql("update account set reserved = reserved - ? where customer_id = ?")
                .params(amount, customerId)
                .update();
        jdbc.sql("update payment_reservation set status = 'REFUNDED', updated_at = ? where order_id = ?")
                .params(Timestamp.from(Instant.now()), orderId)
                .update();
        return true;
    }

    public Optional<String> status(UUID orderId) {
        return jdbc.sql("select status from payment_reservation where order_id = ?")
                .param(orderId)
                .query(String.class)
                .optional();
    }

    public BigDecimal reserved(String customerId) {
        return jdbc.sql("select reserved from account where customer_id = ?")
                .param(customerId)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }

    private void record(UUID orderId, String customerId, BigDecimal amount, String status) {
        jdbc.sql("insert into payment_reservation (order_id, customer_id, amount, status, updated_at)"
                        + " values (?, ?, ?, ?, ?)")
                .params(orderId, customerId, amount, status, Timestamp.from(Instant.now()))
                .update();
    }
}
