package de.ibikus.appa.hephaestus.observability.ordering;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** Orders and their saga: tracks the choreography, runs the orchestration. */
@SpringBootApplication
public class OrderingApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderingApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
