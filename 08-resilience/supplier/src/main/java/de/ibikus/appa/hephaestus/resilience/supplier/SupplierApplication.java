package de.ibikus.appa.hephaestus.resilience.supplier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Stand-in for an external supplier system that Inventory does not control and that misbehaves on demand. */
@SpringBootApplication
public class SupplierApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupplierApplication.class, args);
    }
}
