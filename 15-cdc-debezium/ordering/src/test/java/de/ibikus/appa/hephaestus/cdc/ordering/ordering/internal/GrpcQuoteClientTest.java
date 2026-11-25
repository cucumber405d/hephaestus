package de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.cdc.ordering.FreePort;
import de.ibikus.appa.hephaestus.cdc.ordering.StubForge;
import de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal.QuoteClient.Quoted;
import de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal.QuoteClient.Refused;
import de.ibikus.appa.hephaestus.contracts.api.forge.v1.QuoteServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Every gRPC outcome becomes a quote or a refusal with the customer's reason; nothing waits past the deadline. */
class GrpcQuoteClientTest {

    static final int PORT = FreePort.find();
    static StubForge forge;
    static ManagedChannel channel;

    @BeforeAll
    static void start() {
        forge = new StubForge(PORT);
        channel = ManagedChannelBuilder.forAddress("localhost", PORT)
                .usePlaintext()
                .build();
    }

    @AfterAll
    static void stop() {
        channel.shutdownNow();
        forge.close();
    }

    @AfterEach
    void reset() {
        forge.reset();
    }

    private final GrpcQuoteClient client =
            new GrpcQuoteClient(QuoteServiceGrpc.newBlockingStub(channel), Duration.ofSeconds(2));

    @Test
    void quoteIsReturned() {
        assertThat(client.quote("IRON-01", 5)).isInstanceOfSatisfying(Quoted.class, quoted -> {
            assertThat(quoted.priceCents()).isEqualTo(6000);
            assertThat(quoted.leadTimeDays()).isEqualTo(3);
        });
    }

    @Test
    void slowServerIsUnavailable() {
        forge.delay = Duration.ofSeconds(3);
        var started = System.nanoTime();

        var result = client.quote("IRON-01", 1);

        assertThat(result).isEqualTo(new Refused("quote unavailable"));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void notFoundIsUnknownMaterial() {
        forge.refuse = Status.NOT_FOUND.withDescription("unknown material: FOO");

        assertThat(client.quote("FOO", 1)).isEqualTo(new Refused("unknown material: FOO"));
    }

    @Test
    void invalidArgumentIsInvalidQuantity() {
        forge.refuse = Status.INVALID_ARGUMENT;

        assertThat(client.quote("IRON-01", 0)).isEqualTo(new Refused("invalid quantity"));
    }

    @Test
    void absentServerIsUnavailable() {
        var nowhere = ManagedChannelBuilder.forAddress("localhost", FreePort.find())
                .usePlaintext()
                .build();
        try {
            var absent = new GrpcQuoteClient(QuoteServiceGrpc.newBlockingStub(nowhere), Duration.ofSeconds(2));

            assertThat(absent.quote("IRON-01", 1)).isEqualTo(new Refused("quote unavailable"));
        } finally {
            nowhere.shutdownNow();
        }
    }
}
