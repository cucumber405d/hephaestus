package de.ibikus.appa.hephaestus.monolith.modular;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Stage 1: the same Workshop, cut into modules that Spring Modulith verifies. */
@SpringBootApplication
@EnableScheduling
// @ApplicationModuleListener is @Async: listeners run after the publisher's commit, each in its own transaction
@EnableAsync
public class ModularApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModularApplication.class, args);
    }
}
