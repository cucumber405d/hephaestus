package de.ibikus.appa.hephaestus.security.ordering.adapter.out.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.security.ordering.StubInventoryServer;
import de.ibikus.appa.hephaestus.security.ordering.StubInventoryServer.Answer;
import de.ibikus.appa.hephaestus.security.ordering.domain.InventoryUnavailableException;
import de.ibikus.appa.hephaestus.security.ordering.domain.OrderLine;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.client.RestClient;

/**
 * The real client-credentials plumbing (authorized client manager plus interceptor) against a stub that plays both
 * Keycloak's token endpoint and Inventory.
 */
class InventoryClientCredentialsTest {

    private static final List<OrderLine> ONE_ANVIL = List.of(new OrderLine("ANVIL-01", 1));

    private StubInventoryServer server;
    private InventoryHttpAdapter adapter;

    @BeforeEach
    void start() throws Exception {
        server = new StubInventoryServer();
        var registration = ClientRegistration.withRegistrationId(InventoryClientConfiguration.REGISTRATION)
                .clientId("ordering")
                .clientSecret("ordering-secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(server.url() + "/token")
                .build();
        var registrations = new InMemoryClientRegistrationRepository(registration);
        var manager = InventoryClientConfiguration.clientCredentialsManager(
                registrations, new InMemoryOAuth2AuthorizedClientService(registrations));
        var properties = new InventoryClientProperties(server.url(), Duration.ofMillis(500), Duration.ofSeconds(2));
        adapter = new InventoryHttpAdapter(InventoryClientConfiguration.create(
                RestClient.builder(), properties, InventoryClientConfiguration.serviceToken(manager)));
        server.answer("/api/reservations", Answer.json(201, """
                {"id":"%s","orderId":"%s"}""".formatted(UUID.randomUUID(), UUID.randomUUID())));
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void everyCallCarriesTheServiceToken() {
        server.answer("/token", Answer.token("service-token-1"));

        adapter.reserve(UUID.randomUUID(), ONE_ANVIL);

        assertThat(server.lastRequest().authorization()).isEqualTo("Bearer service-token-1");
    }

    @Test
    void theTokenIsFetchedOnceAndReused() {
        server.answer("/token", Answer.token("service-token-1"));

        adapter.reserve(UUID.randomUUID(), ONE_ANVIL);
        adapter.reserve(UUID.randomUUID(), ONE_ANVIL);

        assertThat(server.calls("/token")).isEqualTo(1);
        assertThat(server.calls("/api/reservations")).isEqualTo(2);
    }

    @Test
    void refusedTokenMeansInventoryIsUnavailable() {
        server.answer("/token", Answer.json(401, """
                {"error":"unauthorized_client","error_description":"Invalid client secret"}"""));

        assertThatThrownBy(() -> adapter.reserve(UUID.randomUUID(), ONE_ANVIL))
                .isInstanceOf(InventoryUnavailableException.class);
        assertThat(server.calls("/api/reservations")).isZero();
    }
}
