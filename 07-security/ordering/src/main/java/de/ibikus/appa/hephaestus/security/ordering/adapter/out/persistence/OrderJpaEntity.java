package de.ibikus.appa.hephaestus.security.ordering.adapter.out.persistence;

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

/** "order" is an SQL keyword; the table is purchase_order. */
@Entity
@Table(name = "purchase_order")
class OrderJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_line", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderLineEmbeddable> lines = new ArrayList<>();

    protected OrderJpaEntity() {}

    OrderJpaEntity(UUID id, String status, UUID reservationId, Instant createdAt, List<OrderLineEmbeddable> lines) {
        this.id = id;
        this.status = status;
        this.reservationId = reservationId;
        this.createdAt = createdAt;
        this.lines = new ArrayList<>(lines);
    }

    UUID getId() {
        return id;
    }

    String getStatus() {
        return status;
    }

    UUID getReservationId() {
        return reservationId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    List<OrderLineEmbeddable> getLines() {
        return lines;
    }
}
