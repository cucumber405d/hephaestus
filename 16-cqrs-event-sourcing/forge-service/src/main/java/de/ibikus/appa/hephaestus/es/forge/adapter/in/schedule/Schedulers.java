package de.ibikus.appa.hephaestus.es.forge.adapter.in.schedule;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * One thread per poller: a fixed-delay task runs on a single scheduler thread, so with a shared one a publisher
 * waiting for the broker or a slow supplier would stop the projections too.
 */
@Configuration(proxyBeanMethods = false)
class Schedulers {

    static final String PROJECTIONS = "projectionsScheduler";
    static final String PUBLISHER = "publisherScheduler";
    static final String SUPPLY = "supplyScheduler";

    @Bean(PROJECTIONS)
    ThreadPoolTaskScheduler projectionsScheduler() {
        return single("projections-");
    }

    @Bean(PUBLISHER)
    ThreadPoolTaskScheduler publisherScheduler() {
        return single("publisher-");
    }

    @Bean(SUPPLY)
    ThreadPoolTaskScheduler supplyScheduler() {
        return single("supply-retry-");
    }

    private static ThreadPoolTaskScheduler single(String prefix) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(prefix);
        return scheduler;
    }
}
