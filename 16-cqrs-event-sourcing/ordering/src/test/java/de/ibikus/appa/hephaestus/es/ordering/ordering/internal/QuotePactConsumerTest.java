package de.ibikus.appa.hephaestus.es.ordering.ordering.internal;

import static au.com.dius.pact.consumer.dsl.PactBuilder.filePath;
import static org.assertj.core.api.Assertions.assertThat;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit.MockServerConfig;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.consumer.model.MockServerImplementation;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Interaction;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import de.ibikus.appa.hephaestus.es.ordering.ordering.internal.QuoteClient.Quoted;
import de.ibikus.appa.hephaestus.es.ordering.ordering.internal.QuoteClient.Refused;
import de.ibikus.appa.hephaestus.contracts.api.forge.v1.QuoteServiceGrpc;
import io.grpc.ManagedChannelBuilder;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Ordering's expectations of Forge's QuoteService, consumer-driven: only what Ordering reads is in the contract.
 * The test drives the real {@link GrpcQuoteClient} against Pact's gRPC mock server (protobuf plugin) and writes
 * pacts/ordering-forge-service-quote.json, which forge-service's build verifies (QuotePactProviderTest).
 * Exact values for price and lead time: they follow from the provider state's price list, and a field Forge stops
 * sending would otherwise decode as proto3's default and still match by type.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(
        providerName = "forge-service-quote",
        providerType = ProviderType.SYNCH_MESSAGE,
        pactVersion = PactSpecVersion.V4)
class QuotePactConsumerTest {

    private static final String PROTO = "../contracts/src/main/proto/hephaestus/forge/v1/quote.proto";

    @Pact(consumer = "ordering")
    V4Pact quoteForIron(PactBuilder builder) {
        return builder.usingPlugin("protobuf")
                .given("IRON-01 costs 1200 per unit and needs 2 base days")
                .expectsToReceive("quote for IRON-01 x5", "core/interaction/synchronous-message")
                .with(Map.of(
                        "pact:proto", filePath(PROTO),
                        "pact:content-type", "application/protobuf",
                        "pact:proto-service", "QuoteService/Quote",
                        "request", Map.of("material", "IRON-01", "quantity", "5"),
                        "response",
                                Map.of(
                                        "quote_id",
                                                "matching(regex, '^[0-9a-f-]{36}$', '6f1c2a8e-0000-4000-8000-000000000001')",
                                        "price_cents", "6000",
                                        "lead_time_days", "3",
                                        "expires_at", Map.of("seconds", "matching(integer, 1800000000)"))))
                .toPact();
    }

    @Pact(consumer = "ordering")
    V4Pact quoteForUnknownMaterial(PactBuilder builder) {
        return builder.usingPlugin("protobuf")
                .given("FOO is not on the price list")
                .expectsToReceive("quote for unknown material", "core/interaction/synchronous-message")
                .with(Map.of(
                        "pact:proto", filePath(PROTO),
                        "pact:content-type", "application/protobuf",
                        "pact:proto-service", "QuoteService/Quote",
                        "request", Map.of("material", "FOO", "quantity", "1"),
                        "responseMetadata", Map.of("grpc-status", "NOT_FOUND")))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "quoteForIron")
    @MockServerConfig(implementation = MockServerImplementation.Plugin, registryEntry = "protobuf/transport/grpc")
    void quoteForIron(MockServer forge, V4Interaction.SynchronousMessages interaction) {
        assertThat(client(forge).quote("IRON-01", 5)).isInstanceOfSatisfying(Quoted.class, quoted -> {
            assertThat(quoted.priceCents()).isEqualTo(6000);
            assertThat(quoted.leadTimeDays()).isEqualTo(3);
            assertThat(quoted.quoteId()).isNotBlank();
        });
    }

    @Test
    @PactTestFor(pactMethod = "quoteForUnknownMaterial")
    @MockServerConfig(implementation = MockServerImplementation.Plugin, registryEntry = "protobuf/transport/grpc")
    void quoteForUnknownMaterial(MockServer forge, V4Interaction.SynchronousMessages interaction) {
        assertThat(client(forge).quote("FOO", 1)).isEqualTo(new Refused("unknown material: FOO"));
    }

    private static GrpcQuoteClient client(MockServer forge) {
        var channel = ManagedChannelBuilder.forTarget("127.0.0.1:" + forge.getPort())
                .usePlaintext()
                .build();
        return new GrpcQuoteClient(QuoteServiceGrpc.newBlockingStub(channel), Duration.ofSeconds(5));
    }
}
