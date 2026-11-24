package de.ibikus.appa.hephaestus.contracts.forge.application;

import de.ibikus.appa.hephaestus.contracts.forge.domain.PriceList.Rate;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code forge.price-list}, {@code forge.units-per-day}, {@code forge.quote-ttl} (application.yaml). */
@ConfigurationProperties("forge")
public record PricingProperties(Map<String, Rate> priceList, int unitsPerDay, Duration quoteTtl) {}
