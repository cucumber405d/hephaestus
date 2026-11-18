package de.ibikus.appa.hephaestus.eventskafka.notification.adapter.in.messaging;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * After the cause is fixed: read everything new in the dead letter topic and send it back to orders.events with
 * its original key and type header. Its own consumer group remembers how far it got, so a record is replayed once.
 */
@Component
public class DltReplayer {

    private static final Logger LOG = LoggerFactory.getLogger(DltReplayer.class);
    static final String GROUP = "notification-dlt-replay";

    private final ConsumerFactory<String, String> consumers;
    private final KafkaTemplate<String, String> template;

    DltReplayer(ConsumerFactory<String, String> consumers, KafkaTemplate<String, String> template) {
        this.consumers = consumers;
        this.template = template;
    }

    /** Replays and returns how many records went back; stops after one second without new records. */
    public synchronized int replay() {
        var overrides = new Properties();
        overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        overrides.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        var replayed = 0;
        try (var consumer = consumers.createConsumer(GROUP, "dlt-replay", null, overrides)) {
            consumer.subscribe(List.of(Topics.ORDERS_EVENTS_DLT));
            var emptyPolls = 0;
            while (emptyPolls < 2) {
                var records = consumer.poll(Duration.ofMillis(500));
                if (records.isEmpty()) {
                    emptyPolls++;
                    continue;
                }
                emptyPolls = 0;
                for (var record : records) {
                    var back = new ProducerRecord<>(Topics.ORDERS_EVENTS, record.key(), record.value());
                    var type = record.headers().lastHeader(Topics.EVENT_TYPE_HEADER);
                    if (type != null) {
                        back.headers().add(Topics.EVENT_TYPE_HEADER, type.value());
                    }
                    template.send(back).join();
                    replayed++;
                }
                consumer.commitSync();
            }
        }
        LOG.info("Replayed {} record(s) from {}", replayed, Topics.ORDERS_EVENTS_DLT);
        return replayed;
    }
}
