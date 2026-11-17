package de.ibikus.appa.hephaestus.servicecommunication.ordering.adapter.out.inventory;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where Inventory is and how long to wait for it. Defaults are deliberately short: without an explicit read
 * timeout a hanging Inventory would hold one of our threads per request forever.
 */
@ConfigurationProperties("inventory")
record InventoryClientProperties(
        URI url,
        @DefaultValue("1s") Duration connectTimeout,
        @DefaultValue("2s") Duration readTimeout) {}
