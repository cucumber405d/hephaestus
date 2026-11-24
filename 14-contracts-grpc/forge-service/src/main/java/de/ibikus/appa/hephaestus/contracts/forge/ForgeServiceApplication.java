package de.ibikus.appa.hephaestus.contracts.forge;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Forge as a service (from 13): quotes over gRPC, work orders from Kafka (Avro), outcomes back (Avro). */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
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
