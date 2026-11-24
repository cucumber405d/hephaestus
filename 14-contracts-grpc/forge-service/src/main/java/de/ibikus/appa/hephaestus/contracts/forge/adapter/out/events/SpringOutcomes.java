package de.ibikus.appa.hephaestus.contracts.forge.adapter.out.events;

import de.ibikus.appa.hephaestus.contracts.forge.adapter.out.events.WorkOrderOutcome.Kind;
import de.ibikus.appa.hephaestus.contracts.forge.application.Outcomes;
import de.ibikus.appa.hephaestus.contracts.forge.domain.WorkOrder;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publishes the outcome inside the work order's transaction (the outbox picks it up from there). */
@Component
class SpringOutcomes implements Outcomes {

    private final ApplicationEventPublisher events;

    SpringOutcomes(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Override
    public void completed(WorkOrder wo) {
        events.publishEvent(new WorkOrderOutcome(UUID.randomUUID(), Kind.COMPLETED, wo.orderId(), wo.id(), null));
    }

    @Override
    public void failed(WorkOrder wo, String reason) {
        events.publishEvent(new WorkOrderOutcome(UUID.randomUUID(), Kind.FAILED, wo.orderId(), wo.id(), reason));
    }
}
