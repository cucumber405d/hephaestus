package de.ibikus.appa.hephaestus.contracts.ordering;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL, a single-node Kafka 4.2 and Apicurio Registry 3 for integration tests. The registry gets the schemas
 * the way the pipeline registers them: released 1.0.0 first, then the current ones, rule BACKWARD.
 */
@TestConfiguration(proxyBeanMethods = false)
public class Containers {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.2.2");
    }

    @Bean
    GenericContainer<?> apicurio() {
        var registry = new GenericContainer<>(System.getProperty("apicurio.image", "apicurio/apicurio-registry:3.3.5"))
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/apis/registry/v3/system/info").forStatusCode(200));
        registry.start();
        SchemaRegistry.registerContracts(url(registry));
        return registry;
    }

    @Bean
    DynamicPropertyRegistrar apicurioUrl(GenericContainer<?> apicurio) {
        return registry -> registry.add("apicurio.url", () -> url(apicurio));
    }

    public static String url(GenericContainer<?> registry) {
        return "http://" + registry.getHost() + ":" + registry.getMappedPort(8080);
    }
}
