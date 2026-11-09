package de.ibikus.appa.hephaestus.restservice.catalog;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Application-wide beans. A Clock bean keeps time-dependent code testable. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class CatalogConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
