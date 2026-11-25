package de.ibikus.appa.hephaestus.cdc.forge.adapter.in.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import de.ibikus.appa.hephaestus.contracts.api.forge.v1.QuoteRequest;
import de.ibikus.appa.hephaestus.contracts.api.forge.v1.QuoteServiceGrpc;
import de.ibikus.appa.hephaestus.cdc.forge.Containers;
import de.ibikus.appa.hephaestus.cdc.forge.FreePort;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** QuoteService over a real channel, as Ordering calls it. */
@SpringBootTest
@Import(Containers.class)
class QuoteGrpcIT {

    static final int PORT = FreePort.find();

    @DynamicPropertySource
    static void grpcPort(DynamicPropertyRegistry registry) {
        registry.add("spring.grpc.server.port", () -> PORT);
    }

    ManagedChannel channel;
    QuoteServiceGrpc.QuoteServiceBlockingStub quotes;

    @BeforeEach
    void connect() {
        channel = ManagedChannelBuilder.forAddress("localhost", PORT)
                .usePlaintext()
                .build();
        quotes = QuoteServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void disconnect() {
        channel.shutdownNow();
    }

    @Test
    void quoteReturnsPriceLeadTimeAndExpiry() {
        var reply = quotes.quote(request("IRON-01", 5));

        assertThat(reply.getPriceCents()).isEqualTo(6000);
        assertThat(reply.getLeadTimeDays()).isEqualTo(3);
        assertThat(reply.getQuoteId()).matches("[0-9a-f-]{36}");
        assertThat(Instant.ofEpochSecond(reply.getExpiresAt().getSeconds()))
                .isCloseTo(Instant.now().plus(15, ChronoUnit.MINUTES), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void unknownMaterialIsNotFound() {
        assertThatThrownBy(() -> quotes.quote(request("FOO", 1)))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
                    assertThat(e.getStatus().getDescription()).isEqualTo("unknown material: FOO");
                });
    }

    @Test
    void nonPositiveQuantityIsInvalidArgument() {
        for (var quantity : new int[] {0, -1}) {
            assertThatThrownBy(() -> quotes.quote(request("IRON-01", quantity)))
                    .isInstanceOfSatisfying(
                            StatusRuntimeException.class,
                            e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
        }
    }

    private static QuoteRequest request(String material, int quantity) {
        return QuoteRequest.newBuilder()
                .setMaterial(material)
                .setQuantity(quantity)
                .build();
    }
}
