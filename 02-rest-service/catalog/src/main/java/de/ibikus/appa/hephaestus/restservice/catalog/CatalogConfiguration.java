package de.ibikus.appa.hephaestus.restservice.catalog;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Application-wide beans. A Clock bean keeps time-dependent code testable. */
@Configuration(proxyBeanMethods = false)
class CatalogConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
