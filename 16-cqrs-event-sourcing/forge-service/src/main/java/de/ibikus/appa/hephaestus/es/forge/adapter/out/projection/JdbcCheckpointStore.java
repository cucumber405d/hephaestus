package de.ibikus.appa.hephaestus.es.forge.adapter.out.projection;

import de.ibikus.appa.hephaestus.es.forge.application.eventstore.Checkpoint;
import de.ibikus.appa.hephaestus.es.forge.application.projection.CheckpointStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCheckpointStore implements CheckpointStore {

    private final JdbcClient jdbc;

    public JdbcCheckpointStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Checkpoint lock(String name) {
        return jdbc.sql("select tx_id::text, position from read.projection_checkpoint where name = ? for update")
                .param(name)
                .query((rs, n) -> new Checkpoint(rs.getString(1), rs.getLong(2)))
                .single();
    }

    @Override
    public Checkpoint get(String name) {
        return jdbc.sql("select tx_id::text, position from read.projection_checkpoint where name = ?")
                .param(name)
                .query((rs, n) -> new Checkpoint(rs.getString(1), rs.getLong(2)))
                .single();
    }

    @Override
    public void save(String name, Checkpoint checkpoint) {
        jdbc.sql("update read.projection_checkpoint set tx_id = ?::xid8, position = ? where name = ?")
                .params(checkpoint.txId(), checkpoint.position(), name)
                .update();
    }

    @Override
    public long lag(String name) {
        return jdbc.sql("""
                        select count(*) from forge.event e, read.projection_checkpoint c
                         where c.name = ? and (e.tx_id, e.global_position) > (c.tx_id, c.position)""")
                .param(name)
                .query(Long.class)
                .single();
    }
}
