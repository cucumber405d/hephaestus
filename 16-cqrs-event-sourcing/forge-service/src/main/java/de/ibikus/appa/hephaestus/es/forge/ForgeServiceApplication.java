package de.ibikus.appa.hephaestus.es.forge;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Forge (from 13): quotes over gRPC, work orders from Kafka (Avro) into its event store, outcomes back (Avro). */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ForgeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForgeServiceApplication.class, args);
    }

    /** Quote expiry; tests move it. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
