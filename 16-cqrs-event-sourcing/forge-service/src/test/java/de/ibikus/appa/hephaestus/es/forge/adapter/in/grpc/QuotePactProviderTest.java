package de.ibikus.appa.hephaestus.es.forge.adapter.in.grpc;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junit5.PluginTestTarget;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import de.ibikus.appa.hephaestus.es.forge.Containers;
import de.ibikus.appa.hephaestus.es.forge.FreePort;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * forge-service honours what Ordering expects of QuoteService: every interaction in ../pacts is replayed against
 * the real gRPC server (protobuf plugin). Scenario 2 of scripts/breaking-change.sh — a field Ordering reads,
 * removed wire-compatibly — passes buf and fails here.
 */
@Provider("forge-service-quote")
@PactFolder("../pacts")
@SpringBootTest
@Import(Containers.class)
class QuotePactProviderTest {

    static final int PORT = FreePort.find();

    @DynamicPropertySource
    static void grpcPort(DynamicPropertyRegistry registry) {
        registry.add("spring.grpc.server.port", () -> PORT);
    }

    @BeforeEach
    void target(PactVerificationContext context) {
        context.setTarget(new PluginTestTarget(Map.of("host", "localhost", "port", PORT, "transport", "grpc")));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verify(PactVerificationContext context) {
        context.verifyInteraction();
    }

    /** The price list in application.yaml already says so; the state names the assumption in the pact. */
    @State("IRON-01 costs 1200 per unit and needs 2 base days")
    void ironOnThePriceList() {}

    @State("FOO is not on the price list")
    void fooUnknown() {}
}
