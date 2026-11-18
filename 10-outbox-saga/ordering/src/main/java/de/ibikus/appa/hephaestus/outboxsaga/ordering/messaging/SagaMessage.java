package de.ibikus.appa.hephaestus.outboxsaga.ordering.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The one message shape of the saga, for events, commands and replies alike (JSON, the {@code type} says what it
 * is). It carries the order data along ("event-carried state"), so a participant needs nothing but the message.
 * Every service keeps its own copy of this contract; there is no shared library between services.
 *
 * @param messageId unique per message; a re-sent message keeps it (participants deduplicate by it)
 * @param saga {@code choreography} or {@code orchestration}: which style this order runs in
 * @param reason why a step failed or was compensated, else null
 */
public record SagaMessage(
        UUID messageId,
        String type,
        UUID orderId,
        String saga,
        String customerId,
        BigDecimal amount,
        List<Line> lines,
        String reason,
        Instant at) {

    public record Line(String sku, int quantity) {}

    public static final String CHOREOGRAPHY = "choreography";
    public static final String ORCHESTRATION = "orchestration";

    // Events
    public static final String ORDER_PLACED = "OrderPlaced";
    public static final String PAYMENT_RESERVED = "PaymentReserved";
    public static final String PAYMENT_DECLINED = "PaymentDeclined";
    public static final String PAYMENT_REFUNDED = "PaymentRefunded";
    public static final String STOCK_RESERVED = "StockReserved";
    public static final String STOCK_UNAVAILABLE = "StockUnavailable";
    public static final String STOCK_RELEASED = "StockReleased";
    public static final String DELIVERY_SCHEDULED = "DeliveryScheduled";
    public static final String DELIVERY_FAILED = "DeliveryFailed";
    public static final String DELIVERY_CANCELLED = "DeliveryCancelled";
    // Commands (orchestration)
    public static final String RESERVE_PAYMENT = "ReservePayment";
    public static final String REFUND_PAYMENT = "RefundPayment";
    public static final String RESERVE_STOCK = "ReserveStock";
    public static final String RELEASE_STOCK = "ReleaseStock";
    public static final String SCHEDULE_DELIVERY = "ScheduleDelivery";
    public static final String CANCEL_DELIVERY = "CancelDelivery";

    public SagaMessage {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public boolean choreography() {
        return CHOREOGRAPHY.equals(saga);
    }

    public boolean orchestration() {
        return ORCHESTRATION.equals(saga);
    }

    /** The follow-up message about the same order: new id and time, same order data. */
    public SagaMessage next(String nextType, String nextReason) {
        return new SagaMessage(
                UUID.randomUUID(), nextType, orderId, saga, customerId, amount, lines, nextReason, Instant.now());
    }
}
