package de.ibikus.appa.hephaestus.observability.payment.messaging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * A failing message is retried three times a second apart, then logged and skipped (09 showed retries and the
 * DLT in depth; here the saga handles business failures as messages, not exceptions). Unreadable messages are
 * skipped at once.
 */
@Configuration(proxyBeanMethods = false)
class KafkaErrorHandling {

    @Bean
    CommonErrorHandler errorHandler() {
        var handler = new DefaultErrorHandler(new FixedBackOff(1_000, 3));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
