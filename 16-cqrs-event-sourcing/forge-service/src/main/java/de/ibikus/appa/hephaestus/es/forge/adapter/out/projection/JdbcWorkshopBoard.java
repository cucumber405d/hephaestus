package de.ibikus.appa.hephaestus.es.forge.adapter.out.projection;

import de.ibikus.appa.hephaestus.es.forge.application.BoardEntry;
import de.ibikus.appa.hephaestus.es.forge.application.WorkshopBoard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcWorkshopBoard implements WorkshopBoard {

    private final JdbcClient jdbc;

    JdbcWorkshopBoard(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<BoardEntry> list(Optional<String> status) {
        return jdbc.sql("""
                        select work_order_id, order_id, material, quantity, status, smith, failure_reason
                          from read.workshop_board
                         where (?::text is null or status = ?)
                         order by last_event_recorded_at, work_order_id""")
                .params(status.orElse(null), status.orElse(null))
                .query((rs, n) -> new BoardEntry(
                        rs.getString(1),
                        rs.getObject(2, UUID.class),
                        rs.getString(3),
                        rs.getInt(4),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getString(7)))
                .list();
    }

    @Override
    public List<String> waitingForMaterial() {
        return jdbc.sql("select work_order_id from read.workshop_board where status = 'WAITING_FOR_MATERIAL'")
                .query(String.class)
                .list();
    }
}
