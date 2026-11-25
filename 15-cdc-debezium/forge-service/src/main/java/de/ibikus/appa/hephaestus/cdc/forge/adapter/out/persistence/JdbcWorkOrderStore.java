package de.ibikus.appa.hephaestus.cdc.forge.adapter.out.persistence;

import de.ibikus.appa.hephaestus.cdc.forge.application.WorkOrderStore;
import de.ibikus.appa.hephaestus.cdc.forge.domain.WorkOrder;
import de.ibikus.appa.hephaestus.cdc.forge.domain.WorkOrder.Status;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcWorkOrderStore implements WorkOrderStore {

    private static final String COLUMNS = "id, order_id, material, quantity, status, material_ready";

    private final JdbcClient jdbc;

    JdbcWorkOrderStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean createIfAbsent(WorkOrder wo) {
        return jdbc.sql("""
                                insert into work_order
                                    (id, order_id, material, quantity, status, material_ready, created_at)
                                values (?, ?, ?, ?, ?, ?, clock_timestamp())
                                on conflict (order_id) do nothing""")
                        .params(
                                wo.id(),
                                wo.orderId(),
                                wo.material(),
                                wo.quantity(),
                                wo.status().name(),
                                wo.materialReady())
                        .update()
                == 1;
    }

    @Override
    public Optional<WorkOrder> find(String id) {
        return jdbc.sql("select " + COLUMNS + " from work_order where id = ?")
                .param(id)
                .query(JdbcWorkOrderStore::map)
                .optional();
    }

    @Override
    public Optional<WorkOrder> lockWaitingForMaterial(String id) {
        return jdbc.sql("select " + COLUMNS
                        + " from work_order where id = ? and status = 'PLANNED' and not material_ready for update")
                .param(id)
                .query(JdbcWorkOrderStore::map)
                .optional();
    }

    @Override
    public List<WorkOrder> list(Status status) {
        var name = status == null ? null : status.name();
        return jdbc.sql("select " + COLUMNS
                        + " from work_order where (cast(? as varchar) is null or status = ?) order by created_at")
                .params(name, name)
                .query(JdbcWorkOrderStore::map)
                .list();
    }

    @Override
    public List<String> waitingForMaterial() {
        return jdbc.sql("select id from work_order where status = 'PLANNED' and not material_ready")
                .query(String.class)
                .list();
    }

    @Override
    public boolean transition(String id, Status from, Status to, boolean requireMaterial) {
        return jdbc.sql("update work_order set status = ? where id = ? and status = ?"
                                + (requireMaterial ? " and material_ready" : ""))
                        .params(to.name(), id, from.name())
                        .update()
                == 1;
    }

    @Override
    public void materialReady(String id) {
        jdbc.sql("update work_order set material_ready = true where id = ?")
                .param(id)
                .update();
    }

    @Override
    public void failed(String id, String reason) {
        jdbc.sql("update work_order set status = 'FAILED', failure_reason = ? where id = ?")
                .params(reason, id)
                .update();
    }

    private static WorkOrder map(ResultSet rs, int row) throws SQLException {
        return new WorkOrder(
                rs.getString("id"),
                rs.getObject("order_id", UUID.class),
                rs.getString("material"),
                rs.getInt("quantity"),
                Status.valueOf(rs.getString("status")),
                rs.getBoolean("material_ready"));
    }
}
