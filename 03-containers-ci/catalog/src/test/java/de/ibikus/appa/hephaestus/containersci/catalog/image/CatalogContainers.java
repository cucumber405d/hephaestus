package de.ibikus.appa.hephaestus.containersci.catalog.image;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Containers for image tests: PostgreSQL and the catalog image on one network, wired by env vars only. */
final class CatalogContainers {

    static final int PORT = 8080;
    static final String DATASOURCE_URL = "jdbc:postgresql://postgres:5432/catalog";

    private CatalogContainers() {}

    /** The image under test, from -Dcatalog.image. */
    static String image() {
        return System.getProperty("catalog.image", "hephaestus/catalog:jib");
    }

    static PostgreSQLContainer postgres(Network network) {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17"))
                .withNetwork(network)
                .withNetworkAliases("postgres")
                .withDatabaseName("catalog")
                .withUsername("catalog")
                .withPassword("catalog");
    }

    /** The catalog image, ready when its readiness probe answers 200. */
    static GenericContainer<?> catalog(Network network, String image) {
        return catalog(network, image, PORT);
    }

    static GenericContainer<?> catalog(Network network, String image, int port) {
        return new GenericContainer<>(DockerImageName.parse(image))
                .withNetwork(network)
                .withEnv("SPRING_DATASOURCE_URL", DATASOURCE_URL)
                .withEnv("SPRING_DATASOURCE_USERNAME", "catalog")
                .withEnv("SPRING_DATASOURCE_PASSWORD", "catalog")
                .withExposedPorts(port)
                .waitingFor(Wait.forHttp("/actuator/health/readiness")
                        .forPort(port)
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofSeconds(120)));
    }

    static String baseUrl(GenericContainer<?> container, int port) {
        return "http://" + container.getHost() + ":" + container.getMappedPort(port);
    }

    /**
     * docker stop: SIGTERM, then SIGKILL after the timeout. GenericContainer.stop() kills (SIGKILL) and would never
     * exercise graceful shutdown. Returns the exit code.
     */
    static long stopGracefully(GenericContainer<?> container) {
        var docker = container.getDockerClient();
        docker.stopContainerCmd(container.getContainerId()).withTimeout(30).exec();
        return docker.inspectContainerCmd(container.getContainerId())
                .exec()
                .getState()
                .getExitCodeLong();
    }
}
