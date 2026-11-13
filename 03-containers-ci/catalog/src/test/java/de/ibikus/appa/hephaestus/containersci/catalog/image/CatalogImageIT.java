package de.ibikus.appa.hephaestus.containersci.catalog.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/** The same checks for every image, whichever way it was built (-Dcatalog.image, -Dcatalog.flavor). */
class CatalogImageIT {

    private static final String WEAPONS = "01900000-0000-7000-8000-000000000003";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Network NETWORK = Network.newNetwork();
    private static final PostgreSQLContainer POSTGRES = CatalogContainers.postgres(NETWORK);

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
        NETWORK.close();
    }

    @Test
    void startsAndServesApi() throws Exception {
        try (var catalog = CatalogContainers.catalog(NETWORK, CatalogContainers.image())) {
            catalog.start();
            var base = CatalogContainers.baseUrl(catalog, CatalogContainers.PORT);

            var created = HTTP.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/items"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("""
                                    {"name":"Image sword","price":{"amount":"10.00","currency":"EUR"},"categoryId":"%s"}"""
                                    .formatted(WEAPONS)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(created.statusCode()).isEqualTo(201);

            var location = URI.create(created.headers().firstValue("Location").orElseThrow());
            var read = HTTP.send(
                    HttpRequest.newBuilder(URI.create(base + location.getPath())).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(read.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(read.body()).get("name").asString()).isEqualTo("Image sword");
        }
    }

    @Test
    void logsAsJson() {
        try (var catalog = CatalogContainers.catalog(NETWORK, CatalogContainers.image())) {
            catalog.start();

            // Paketo helpers print plain-text lines before the JVM starts; only JSON-looking lines are parsed
            var started = catalog.getLogs().lines()
                    .filter(line -> line.startsWith("{"))
                    .map(JSON::readTree)
                    .map(node -> node.path("message").asString(""))
                    .filter(message -> message.contains("Started CatalogApplication"))
                    .toList();
            assertThat(started).hasSize(1);
        }
    }

    @Test
    void runsAsNonRoot() {
        var user = DockerClientFactory.instance()
                .client()
                .inspectImageCmd(CatalogContainers.image())
                .exec()
                .getConfig()
                .getUser();

        assertThat(user).isNotBlank().isNotIn("0", "root").doesNotStartWith("0:").doesNotStartWith("root:");
    }

    @Test
    void shutsDownGracefullyOnSigterm() {
        try (var catalog = CatalogContainers.catalog(NETWORK, CatalogContainers.image())) {
            catalog.start();

            var exitCode = CatalogContainers.stopGracefully(catalog);

            assertThat(catalog.getLogs()).contains("Graceful shutdown complete");
            // 143 = 128 + SIGTERM: the JVM ran its shutdown hooks and exited on the signal
            assertThat(exitCode).isIn(0L, 143L);
        }
    }

    @Test
    void honoursPortVariable() throws Exception {
        try (var catalog = CatalogContainers.catalog(NETWORK, CatalogContainers.image(), 9090)
                .withEnv("PORT", "9090")) {
            catalog.start();

            var readiness = HTTP.send(
                    HttpRequest.newBuilder(URI.create(
                                    CatalogContainers.baseUrl(catalog, 9090) + "/actuator/health/readiness"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(readiness.statusCode()).isEqualTo(200);
        }
    }

    @Test
    void exitsWhenDatabaseIsUnreachable() throws Exception {
        try (GenericContainer<?> catalog = CatalogContainers.catalog(NETWORK, CatalogContainers.image())
                .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://nowhere:5432/catalog")
                .withStartupCheckStrategy(new AlwaysStarted())
                .waitingFor(new NoWait())) {
            catalog.start();
            var docker = catalog.getDockerClient();
            var deadline = Instant.now().plus(Duration.ofSeconds(60));

            var state = docker.inspectContainerCmd(catalog.getContainerId()).exec().getState();
            while (Boolean.TRUE.equals(state.getRunning()) && Instant.now().isBefore(deadline)) {
                Thread.sleep(500);
                state = docker.inspectContainerCmd(catalog.getContainerId()).exec().getState();
            }

            assertThat(state.getRunning()).isFalse();
            assertThat(state.getExitCodeLong()).isNotZero();
        }
    }

    /** The container is expected to die; do not let Testcontainers treat that as a failed start. */
    private static final class AlwaysStarted extends StartupCheckStrategy {
        @Override
        public StartupStatus checkStartupState(com.github.dockerjava.api.DockerClient client, String containerId) {
            return StartupStatus.SUCCESSFUL;
        }
    }

    private static final class NoWait extends org.testcontainers.containers.wait.strategy.AbstractWaitStrategy {
        @Override
        protected void waitUntilReady() {}
    }
}
