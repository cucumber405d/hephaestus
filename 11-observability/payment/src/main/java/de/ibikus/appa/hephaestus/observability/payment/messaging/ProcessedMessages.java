package de.ibikus.appa.hephaestus.observability.payment.messaging;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumer: Kafka delivers at least once and messages may be re-sent. Called in the handling
 * transaction; a message id seen before means "already done, skip".
 */
@Component
public class ProcessedMessages {

    private final JdbcClient jdbc;

    public ProcessedMessages(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(UUID messageId) {
        return jdbc.sql("insert into processed_message (message_id, processed_at) values (?, ?) on conflict do nothing")
                        .params(messageId, Timestamp.from(Instant.now()))
                        .update()
                == 1;
    }
}
