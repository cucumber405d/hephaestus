package de.ibikus.appa.hephaestus.es.forge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.ibikus.appa.hephaestus.es.forge.Containers;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.EventCodec;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.EventStore;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialDecider;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent.StockReceived;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent.StockReceived.Source;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent.StockTaken;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/** Stock at a moment: a fold of the material's stream up to that moment, straight from the journal. */
@SpringBootTest
@Import(Containers.class)
class TimeTravelIT {

    @Autowired
    WorkOrders workOrders;

    @Autowired
    EventStore store;

    @Autowired
    EventCodec codec;

    @Autowired
    StockLedger ledger;

    @Autowired
    TransactionTemplate tx;

    final String code = "TT-" + UUID.randomUUID().toString().substring(0, 8);

    @Test
    void stockBeforeFirstEventIsZero() {
        var before = Instant.now().minusSeconds(60);
        append(0, new StockReceived(10, Source.SUPPLIER));
        assertThat(workOrders.stockAt(code, before)).isZero();
    }

    @Test
    void stockAtMiddleExcludesLaterTakes() {
        append(0, new StockReceived(10, Source.SUPPLIER));
        var afterReceipt = store.readStream(MaterialDecider.streamId(code)).getFirst().recordedAt();
        append(1, new StockTaken(4, "FW-x"));

        assertThat(workOrders.stockAt(code, afterReceipt)).isEqualTo(10);
        assertThat(workOrders.stockAt(code, Instant.now().plusSeconds(60))).isEqualTo(6);
    }

    @Test
    void stockNowEqualsLedger() {
        append(0, new StockReceived(10, Source.SUPPLIER), new StockTaken(3, "FW-y"));
        await().atMost(Duration.ofSeconds(10)).until(() -> ledger.onHand(code).orElse(-1) == 7);
        assertThat(workOrders.stockAt(code, Instant.now())).isEqualTo(7);
    }

    private void append(int expectedVersion, Object... events) {
        tx.executeWithoutResult(status -> store.append(
                MaterialDecider.streamId(code),
                "Material",
                expectedVersion,
                List.of(events).stream().map(e -> codec.encode(e, Map.of())).toList()));
    }
}
