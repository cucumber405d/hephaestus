package de.ibikus.appa.hephaestus.observability.ordering.orders;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Looks for overdue orchestration steps every second (a separate bean, so the call goes through the transaction). */
@Component
class SagaTimeouts {

    private final OrderService orders;

    SagaTimeouts(OrderService orders) {
        this.orders = orders;
    }

    @Scheduled(initialDelay = 1_000, fixedDelay = 1_000)
    void check() {
        orders.timeouts();
    }
}
