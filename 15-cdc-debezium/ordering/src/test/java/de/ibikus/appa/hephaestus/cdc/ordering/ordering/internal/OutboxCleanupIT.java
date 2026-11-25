package de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.cdc.ordering.Containers;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Old outbox rows go; rows younger than the retention stay (they are only kept to be looked at). */
@SpringBootTest(properties = {"outbox.retention=10m", "outbox.cleanup-interval=1h"})
@Import(Containers.class)
class OutboxCleanupIT {

    @Autowired
    OutboxCleanup cleanup;

    @Autowired
    JdbcClient jdbc;

    @Test
    void oldRowsAreDeleted() {
        var old = insert("clock_timestamp() - interval '1 hour'");
        var fresh = insert("clock_timestamp()");

        cleanup.cleanup();

        assertThat(exists(old)).isFalse();
        assertThat(exists(fresh)).isTrue();
    }

    private UUID insert(String createdAt) {
        var id = UUID.randomUUID();
        jdbc.sql("insert into ordering.outbox (id, aggregate_type, aggregate_id, type, payload, created_at) "
                        + "values (?, 'forge.requests', ?, 'WorkOrderRequest', ?, " + createdAt + ")")
                .params(id, UUID.randomUUID().toString(), new byte[] {0})
                .update();
        return id;
    }

    private boolean exists(UUID id) {
        return jdbc.sql("select count(*) from ordering.outbox where id = ?")
                        .param(id)
                        .query(Integer.class)
                        .single()
                == 1;
    }
}
