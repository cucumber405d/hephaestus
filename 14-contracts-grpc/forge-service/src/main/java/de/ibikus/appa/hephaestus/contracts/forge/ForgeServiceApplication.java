package de.ibikus.appa.hephaestus.contracts.forge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Forge as a service (from 13): quotes over gRPC, work orders from Kafka (Avro), outcomes back (Avro). */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class ForgeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForgeServiceApplication.class, args);
    }
}
