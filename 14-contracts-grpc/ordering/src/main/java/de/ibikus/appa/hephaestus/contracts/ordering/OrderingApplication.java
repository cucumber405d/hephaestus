package de.ibikus.appa.hephaestus.contracts.ordering;

import de.ibikus.appa.hephaestus.contracts.api.forge.v1.QuoteServiceGrpc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Ordering (13's modular monolith after the cutover, without forge, supplier and migration): asks Forge for a
 * binding quote over gRPC, then for the work order over Kafka (Avro). Modules: ordering, inventory.
 */
@SpringBootApplication
// @ApplicationModuleListener is @Async: listeners run after the publisher's commit, each in its own transaction
@EnableAsync
// The stub on channel "forge" (spring.grpc.client.channel.forge.target)
@ImportGrpcClients(target = "forge", types = QuoteServiceGrpc.QuoteServiceBlockingStub.class)
public class OrderingApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderingApplication.class, args);
    }
}
