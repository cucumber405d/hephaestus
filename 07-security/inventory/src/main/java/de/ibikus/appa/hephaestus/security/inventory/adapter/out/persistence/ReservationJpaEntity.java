package de.ibikus.appa.hephaestus.security.inventory.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservation")
class ReservationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reservation_line", joinColumns = @JoinColumn(name = "reservation_id"))
    private List<ReservationLineEmbeddable> lines = new ArrayList<>();

    protected ReservationJpaEntity() {}

    ReservationJpaEntity(UUID id, UUID orderId, Instant createdAt, List<ReservationLineEmbeddable> lines) {
        this.id = id;
        this.orderId = orderId;
        this.createdAt = createdAt;
        this.lines = new ArrayList<>(lines);
    }

    UUID getId() {
        return id;
    }

    UUID getOrderId() {
        return orderId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    List<ReservationLineEmbeddable> getLines() {
        return lines;
    }
}
