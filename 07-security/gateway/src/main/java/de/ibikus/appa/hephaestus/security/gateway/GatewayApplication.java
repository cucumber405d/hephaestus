package de.ibikus.appa.hephaestus.security.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The shop's public entry and BFF: routes in application.yaml, login and CSRF in {@link SecurityConfiguration}. */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
