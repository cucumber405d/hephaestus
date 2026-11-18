package de.ibikus.appa.hephaestus.eventskafka.notification.adapter.in.messaging;

import de.ibikus.appa.hephaestus.eventskafka.notification.domain.NotificationFailedException;
import java.util.Map;
import org.apache.kafka.clients.consumer.AcknowledgeType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ShareKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultShareConsumerFactory;
import org.springframework.kafka.core.ShareConsumerFactory;

/**
 * Share groups (KIP-932, production-ready since Kafka 4.2) use their own consumer type: only the essentials are
 * set, since classic-only settings (auto.offset.reset, enable.auto.commit, assignment strategy) do not apply.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "notification.mode", havingValue = "share")
class ShareGroupConfiguration {

    @Bean
    ShareConsumerFactory<String, String> shareConsumerFactory(ConsumerFactory<?, ?> classicConsumers) {
        // The broker address as Boot resolved it (property, environment or a test's service connection)
        var bootstrapServers =
                classicConsumers.getConfigurationProperties().get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG);
        return new DefaultShareConsumerFactory<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                // One record per fetch, strictly (Kafka 4.2 record_limit): otherwise one member may acquire a whole
                // batch and the others idle, which hides how a share group spreads the work
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                1,
                "share.acquire.mode",
                "record_limit"));
    }

    @Bean
    ShareKafkaListenerContainerFactory<String, String> shareKafkaListenerContainerFactory(
            ShareConsumerFactory<String, String> shareConsumerFactory) {
        var factory = new ShareKafkaListenerContainerFactory<>(shareConsumerFactory);
        // On a listener exception the default is REJECT (archived at once). A failed notification is worth another
        // try: RELEASE it for redelivery to any member, until the broker's delivery limit archives it
        factory.setShareConsumerRecordRecoverer(
                (record, exception) -> hasCause(exception, NotificationFailedException.class)
                        ? AcknowledgeType.RELEASE
                        : AcknowledgeType.REJECT);
        return factory;
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (var cause = t; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }
}
