package de.ibikus.appa.hephaestus.contracts.supplier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The external material supplier of the Workshop, as the legacy integration knows it. */
@SpringBootApplication
public class SupplierStubApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupplierStubApplication.class, args);
    }
}
