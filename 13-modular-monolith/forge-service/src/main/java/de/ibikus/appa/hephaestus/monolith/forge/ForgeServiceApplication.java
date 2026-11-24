package de.ibikus.appa.hephaestus.monolith.forge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Stage 2: Forge on its own — the first piece strangled out of the Workshop monolith. */
@SpringBootApplication
@EnableScheduling
public class ForgeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForgeServiceApplication.class, args);
    }
}
