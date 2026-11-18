package de.ibikus.appa.hephaestus.observability.inventory.messaging;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way a message leaves this service. Must run inside the transaction that changes the state the message
 * talks about: then both are committed together or not at all, and no message is lost or sent for a rolled-back
 * change (the dual write of 09 is gone).
 */
@Component
public class Outbox {

    private final ApplicationEventPublisher events;
    private final TraceCarrier carrier;

    public Outbox(ApplicationEventPublisher events, TraceCarrier carrier) {
        this.events = events;
        this.carrier = carrier;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void send(String topic, SagaMessage message) {
        events.publishEvent(new OutgoingMessage(topic, message, carrier.capture()));
    }
}
