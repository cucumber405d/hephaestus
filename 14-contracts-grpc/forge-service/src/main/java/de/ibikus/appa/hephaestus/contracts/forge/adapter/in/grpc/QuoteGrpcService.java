package de.ibikus.appa.hephaestus.contracts.forge.adapter.in.grpc;

import com.google.protobuf.Timestamp;
import de.ibikus.appa.hephaestus.contracts.api.forge.v1.QuoteReply;
import de.ibikus.appa.hephaestus.contracts.api.forge.v1.QuoteRequest;
import de.ibikus.appa.hephaestus.contracts.api.forge.v1.QuoteServiceGrpc;
import de.ibikus.appa.hephaestus.contracts.forge.application.Quotes;
import de.ibikus.appa.hephaestus.contracts.forge.domain.InvalidQuantity;
import de.ibikus.appa.hephaestus.contracts.forge.domain.UnknownMaterial;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

/**
 * {@code hephaestus.forge.v1.QuoteService} (quote.proto): any {@code BindableService} bean is served by Spring
 * Boot's gRPC server. Domain refusals become gRPC statuses, the description carries the text Ordering shows.
 */
@Component
class QuoteGrpcService extends QuoteServiceGrpc.QuoteServiceImplBase {

    private final Quotes quotes;

    QuoteGrpcService(Quotes quotes) {
        this.quotes = quotes;
    }

    @Override
    public void quote(QuoteRequest request, StreamObserver<QuoteReply> response) {
        try {
            var quote = quotes.quote(request.getMaterial(), request.getQuantity());
            response.onNext(QuoteReply.newBuilder()
                    .setQuoteId(quote.id())
                    .setPriceCents(quote.priceCents())
                    .setLeadTimeDays(quote.leadTimeDays())
                    .setExpiresAt(Timestamp.newBuilder()
                            .setSeconds(quote.expiresAt().getEpochSecond())
                            .setNanos(quote.expiresAt().getNano()))
                    .build());
            response.onCompleted();
        } catch (UnknownMaterial e) {
            response.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        } catch (InvalidQuantity e) {
            response.onError(
                    Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
