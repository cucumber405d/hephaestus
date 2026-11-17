package de.ibikus.appa.hephaestus.servicecommunication.inventory.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_item")
class StockItemJpaEntity {

    @Id
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Column(nullable = false)
    private int reserved;

    String getSku() {
        return sku;
    }

    String getName() {
        return name;
    }

    int getOnHand() {
        return onHand;
    }

    int getReserved() {
        return reserved;
    }

    void setReserved(int reserved) {
        this.reserved = reserved;
    }
}
