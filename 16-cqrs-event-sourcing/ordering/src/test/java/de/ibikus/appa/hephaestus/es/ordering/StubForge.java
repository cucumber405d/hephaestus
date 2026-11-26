package de.ibikus.appa.hephaestus.es.ordering;

import de.ibikus.appa.hephaestus.contracts.api.forge.v1.QuoteReply;
import de.ibikus.appa.hephaestus.contracts.api.forge.v1.QuoteRequest;
import de.ibikus.appa.hephaestus.contracts.api.forge.v1.QuoteServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.UUID;

/**
 * A QuoteService on a local port that answers like Forge's price list for IRON-01 (1200 per unit, 3 days) — or
 * slowly ({@link #delay}), or with a status ({@link #refuse}). The test plays Forge.
 */
public final class StubForge implements AutoCloseable {

    public volatile Duration delay = Duration.ZERO;
    public volatile Status refuse;

    private final Server server;

    public StubForge(int port) {
        try {
            server = ServerBuilder.forPort(port)
                    .addService(new QuoteServiceGrpc.QuoteServiceImplBase() {
                        @Override
                        public void quote(QuoteRequest request, StreamObserver<QuoteReply> response) {
                            sleep(delay);
                            if (refuse != null) {
                                response.onError(refuse.asRuntimeException());
                                return;
                            }
                            response.onNext(QuoteReply.newBuilder()
                                    .setQuoteId(UUID.randomUUID().toString())
                                    .setPriceCents(1200L * request.getQuantity())
                                    .setLeadTimeDays(3)
                                    .build());
                            response.onCompleted();
                        }
                    })
                    .build()
                    .start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void reset() {
        delay = Duration.ZERO;
        refuse = null;
    }

    @Override
    public void close() {
        server.shutdownNow();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
