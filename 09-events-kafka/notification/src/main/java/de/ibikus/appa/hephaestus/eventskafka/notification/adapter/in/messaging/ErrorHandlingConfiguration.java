package de.ibikus.appa.hephaestus.eventskafka.notification.adapter.in.messaging;

import de.ibikus.appa.hephaestus.eventskafka.notification.domain.InvalidEventException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * What happens when handling fails (classic group): retry in place with exponential backoff (1 s, 2 s, 4 s), then
 * publish the record to the dead letter topic (with headers telling why) and move on, so one bad record does not
 * block its partition forever. Invalid events skip the retries: they would fail the same way every time.
 * Boot hands this bean to the listener container factory.
 */
@Configuration(proxyBeanMethods = false)
class ErrorHandlingConfiguration {

    static final int RETRIES = 3;

    @Bean
    CommonErrorHandler errorHandler(KafkaTemplate<String, String> template) {
        var recoverer = new DeadLetterPublishingRecoverer(
                template, (record, exception) -> new TopicPartition(Topics.ORDERS_EVENTS_DLT, 0));
        var backOff = new ExponentialBackOff(1_000, 2.0);
        backOff.setMaxAttempts(RETRIES);
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
