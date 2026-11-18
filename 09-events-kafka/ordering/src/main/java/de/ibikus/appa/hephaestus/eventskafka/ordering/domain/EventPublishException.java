package de.ibikus.appa.hephaestus.eventskafka.ordering.domain;

/**
 * The OrderPlaced event could not be handed to Kafka. The order itself is already stored: this is the dual-write
 * gap (database and broker are two systems without a shared transaction) that the outbox of subproject 10 closes.
 */
public class EventPublishException extends OrderingException {

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
