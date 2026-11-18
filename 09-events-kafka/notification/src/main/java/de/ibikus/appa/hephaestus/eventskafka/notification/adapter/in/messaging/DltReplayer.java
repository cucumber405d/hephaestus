package de.ibikus.appa.hephaestus.eventskafka.notification.adapter.in.messaging;

import de.ibikus.appa.hephaestus.eventskafka.notification.domain.InvalidEventException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * After the cause is fixed: send what is in the dead letter topic back to orders.events, with its original key and
 * type header. Reads up to the end offsets taken at the start, so records that land in the DLT during the replay
 * (a record that fails again) wait for the next replay instead of looping. Poison records (invalid events) are
 * skipped: sending them again cannot help. Its own consumer group remembers how far it got.
 */
@Component
public class DltReplayer {

    private static final Logger LOG = LoggerFactory.getLogger(DltReplayer.class);
    static final String GROUP = "notification-dlt-replay";
    static final Duration MAX_DURATION = Duration.ofSeconds(30);

    public record Result(int replayed, int skipped) {}

    private final ConsumerFactory<String, String> consumers;
    private final KafkaTemplate<String, String> template;
    private final OrderPlacedReader reader;

    DltReplayer(
            ConsumerFactory<String, String> consumers, KafkaTemplate<String, String> template, OrderPlacedReader reader) {
        this.consumers = consumers;
        this.template = template;
        this.reader = reader;
    }

    public synchronized Result replay() {
        var overrides = new Properties();
        overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        overrides.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        var replayed = 0;
        var skipped = 0;
        try (var consumer = consumers.createConsumer(GROUP, "dlt-replay", null, overrides)) {
            // assign() instead of subscribe(): no group join to wait for; the group is used for the offsets only
            var partitions = consumer.partitionsFor(Topics.ORDERS_EVENTS_DLT).stream()
                    .map(info -> new TopicPartition(info.topic(), info.partition()))
                    .toList();
            consumer.assign(partitions);
            var end = consumer.endOffsets(partitions);
            Map<TopicPartition, OffsetAndMetadata> done = new HashMap<>();
            var deadline = System.nanoTime() + MAX_DURATION.toNanos();
            while (!reachedEnd(consumer, end) && System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                    var partition = new TopicPartition(record.topic(), record.partition());
                    if (record.offset() >= end.get(partition)) {
                        continue; // arrived after the start of this replay
                    }
                    if (isPoison(record)) {
                        skipped++;
                    } else {
                        sendBack(record);
                        replayed++;
                    }
                    done.put(partition, new OffsetAndMetadata(record.offset() + 1));
                }
            }
            if (!done.isEmpty()) {
                consumer.commitSync(done);
            }
        }
        LOG.info("Replayed {} record(s) from {}, skipped {} invalid", replayed, Topics.ORDERS_EVENTS_DLT, skipped);
        return new Result(replayed, skipped);
    }

    private static boolean reachedEnd(
            org.apache.kafka.clients.consumer.Consumer<String, String> consumer, Map<TopicPartition, Long> end) {
        return end.entrySet().stream().allMatch(e -> consumer.position(e.getKey()) >= e.getValue());
    }

    /**
     * A record that still cannot be read as an event (broken JSON, wrong type, missing fields). Decided by reading
     * it, not by the DLT exception headers: those may name a wrapper exception.
     */
    private boolean isPoison(ConsumerRecord<String, String> record) {
        try {
            reader.read(record);
            return false;
        } catch (InvalidEventException e) {
            return true;
        }
    }

    private void sendBack(ConsumerRecord<String, String> record) {
        var back = new ProducerRecord<>(Topics.ORDERS_EVENTS, record.key(), record.value());
        var type = record.headers().lastHeader(Topics.EVENT_TYPE_HEADER);
        if (type != null) {
            back.headers().add(Topics.EVENT_TYPE_HEADER, type.value());
        }
        template.send(back).join();
    }
}
