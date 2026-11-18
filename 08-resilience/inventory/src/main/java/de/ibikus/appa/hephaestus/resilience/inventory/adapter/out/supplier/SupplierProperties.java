package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where the supplier is (in Compose: behind Toxiproxy) and the HTTP timeouts every mechanism shares. */
@ConfigurationProperties("supplier")
public record SupplierProperties(URI url, Duration connectTimeout, Duration readTimeout) {}
