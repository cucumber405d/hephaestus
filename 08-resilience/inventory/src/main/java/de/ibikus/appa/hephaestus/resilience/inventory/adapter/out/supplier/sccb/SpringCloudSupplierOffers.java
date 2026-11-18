package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.sccb;

import de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.SupplierCall;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.in.Mechanism;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.out.SupplierOffers;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierOffer;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

/**
 * Mechanism 2: the Spring Cloud Circuit Breaker abstraction. The code only knows {@code CircuitBreakerFactory} and
 * {@code run(call, fallback)}; Resilience4j underneath is configuration ({@link SpringCloudCircuitBreakerConfiguration}).
 * The abstraction has a circuit breaker and a time limiter, but no retry and no rate limiter: one attempt per request.
 */
@Component
public class SpringCloudSupplierOffers implements SupplierOffers {

    static final String ID = "supplier-sccb";

    private final SupplierCall call;
    private final CircuitBreakerFactory<?, ?> circuitBreakers;

    public SpringCloudSupplierOffers(SupplierCall call, CircuitBreakerFactory<?, ?> circuitBreakers) {
        this.call = call;
        this.circuitBreakers = circuitBreakers;
    }

    @Override
    public Mechanism mechanism() {
        return Mechanism.SCCB;
    }

    @Override
    public SupplierOffer fetch(String sku) {
        return circuitBreakers.create(ID).run(() -> call.fetch(sku), failure -> {
            // The fallback gets every failure, the unknown offer included: translate, never swallow
            throw SupplierCall.translate(Mechanism.SCCB.id(), sku, failure);
        });
    }
}
