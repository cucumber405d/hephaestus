package de.ibikus.appa.hephaestus.cdc.ordering;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Ordering starts a second time against the same database. The database user is named like the module schema, as in
 * Compose and on the platform: search_path "$user" then resolves to schema "ordering" once V1 has created it, so
 * Flyway must not look for its history in the connection's current schema.
 */
class FlywayRestartIT {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))
            .withUsername("ordering")
            .withPassword("ordering")
            .withDatabaseName("ordering")
            .withInitScript("cdc-role.sql");

    @BeforeAll
    static void start() {
        POSTGRES.start();
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    @Test
    void secondStartFindsItsSchemaHistory() throws IOException {
        migrate();

        assertThatCode(FlywayRestartIT::migrate).doesNotThrowAnyException();
    }

    private static void migrate() throws IOException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("cdcUser", "cdc"))
                .defaultSchema(applicationProperty("spring.flyway.default-schema"))
                .load()
                .migrate();
    }

    // The value Ordering itself runs with (src/main/resources/application.yaml), not one chosen by the test
    private static String applicationProperty(String name) throws IOException {
        for (PropertySource<?> source :
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"))) {
            Object value = source.getProperty(name);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }
}
