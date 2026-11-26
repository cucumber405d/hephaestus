package de.ibikus.appa.hephaestus.es.ordering.ordering.internal;

import de.ibikus.appa.hephaestus.contracts.api.forge.v1.QuoteRequest;
import de.ibikus.appa.hephaestus.contracts.api.forge.v1.QuoteServiceGrpc.QuoteServiceBlockingStub;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@code QuoteService/Quote} with a deadline (QUOTE_DEADLINE, 2 s): a slow or absent Forge must not hold the
 * customer. NOT_FOUND and INVALID_ARGUMENT are Forge's answers; every other status means "no quote now".
 */
@Component
class GrpcQuoteClient implements QuoteClient {

    static final String UNAVAILABLE = "quote unavailable";

    private final QuoteServiceBlockingStub forge;
    private final Duration deadline;

    GrpcQuoteClient(QuoteServiceBlockingStub forge, @Value("${forge.quote-deadline}") Duration deadline) {
        this.forge = forge;
        this.deadline = deadline;
    }

    @Override
    public QuoteResult quote(String material, int quantity) {
        try {
            var reply = forge.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
                    .quote(QuoteRequest.newBuilder()
                            .setMaterial(material == null ? "" : material)
                            .setQuantity(quantity)
                            .build());
            return new Quoted(reply.getQuoteId(), reply.getPriceCents(), reply.getLeadTimeDays());
        } catch (StatusRuntimeException e) {
            return switch (e.getStatus().getCode()) {
                case NOT_FOUND -> new Refused("unknown material: " + material);
                case INVALID_ARGUMENT -> new Refused("invalid quantity");
                default -> new Refused(UNAVAILABLE);
            };
        }
    }
}
