package de.ibikus.appa.hephaestus.eventskafka.notification.adapter.out.persistence;

import de.ibikus.appa.hephaestus.eventskafka.notification.application.port.out.NotificationStore;
import de.ibikus.appa.hephaestus.eventskafka.notification.domain.Notification;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plain SQL: the idempotency check is one statement. "on conflict do nothing" also covers two consumers handling
 * the same event at the same moment (after a rebalance): the second insert waits for the first transaction and then
 * inserts nothing.
 */
@Repository
class JdbcNotificationStore implements NotificationStore {

    private final JdbcClient jdbc;

    JdbcNotificationStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public boolean saveOnce(UUID eventId, Notification n) {
        var firstTime = jdbc.sql("insert into processed_event (event_id, processed_at) values (?, ?) on conflict do nothing")
                        .params(eventId, Timestamp.from(n.createdAt()))
                        .update()
                == 1;
        if (firstTime) {
            jdbc.sql("insert into notification (id, customer_id, order_id, channel, text, created_at) values (?, ?, ?, ?, ?, ?)")
                    .params(n.id(), n.customerId(), n.orderId(), n.channel(), n.text(), Timestamp.from(n.createdAt()))
                    .update();
        }
        return firstTime;
    }

    @Override
    public List<Notification> findByCustomer(String customerId) {
        return jdbc.sql("select id, customer_id, order_id, channel, text, created_at from notification"
                        + " where customer_id = ? order by created_at, id")
                .param(customerId)
                .query((rs, row) -> new Notification(
                        rs.getObject("id", UUID.class),
                        rs.getString("customer_id"),
                        rs.getObject("order_id", UUID.class),
                        rs.getString("channel"),
                        rs.getString("text"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }
}
