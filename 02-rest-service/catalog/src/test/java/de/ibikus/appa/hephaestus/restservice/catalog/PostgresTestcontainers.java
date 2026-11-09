package de.ibikus.appa.hephaestus.restservice.catalog;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** A real PostgreSQL 17 for integration tests; Spring Boot wires the DataSource from the container. */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainers {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
    }
}
