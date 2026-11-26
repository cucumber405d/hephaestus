package de.ibikus.appa.hephaestus.es.forge.adapter.out.projection;

import de.ibikus.appa.hephaestus.es.forge.application.eventstore.RecordedEvent;
import de.ibikus.appa.hephaestus.es.forge.application.projection.Projection;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent.StockReceived;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent.StockTaken;
import java.sql.Timestamp;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** stock-ledger: stock per material now, and every movement (read.stock_ledger, read.stock_movement). */
@Component
public class StockLedgerProjection implements Projection {

    private static final String STREAM_PREFIX = "material-";

    private final JdbcClient jdbc;

    public StockLedgerProjection(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "stock-ledger";
    }

    @Override
    public void apply(Object event, RecordedEvent meta) {
        switch (event) {
            case StockReceived r -> move(meta, r.quantity(), r.source().name(), null);
            case StockTaken t -> move(meta, -t.quantity(), "TAKE", t.workOrderId());
            default -> {}
        }
    }

    @Override
    public void reset() {
        jdbc.sql("delete from read.stock_ledger").update();
        jdbc.sql("delete from read.stock_movement").update();
    }

    private void move(RecordedEvent meta, int delta, String source, String workOrderId) {
        var material = meta.streamId().substring(STREAM_PREFIX.length());
        jdbc.sql("""
                        insert into read.stock_ledger (material, on_hand) values (?, ?)
                        on conflict (material) do update set on_hand = read.stock_ledger.on_hand + excluded.on_hand""").params(material, delta).update();
        jdbc.sql("""
                        insert into read.stock_movement (position, material, delta, source, work_order_id, recorded_at)
                        values (?, ?, ?, ?, ?, ?)""")
                .params(meta.position(), material, delta, source, workOrderId, Timestamp.from(meta.recordedAt()))
                .update();
    }
}
