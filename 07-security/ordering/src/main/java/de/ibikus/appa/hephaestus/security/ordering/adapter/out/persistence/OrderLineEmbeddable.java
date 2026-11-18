package de.ibikus.appa.hephaestus.security.ordering.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class OrderLineEmbeddable {

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    protected OrderLineEmbeddable() {}

    OrderLineEmbeddable(String sku, int quantity) {
        this.sku = sku;
        this.quantity = quantity;
    }

    String getSku() {
        return sku;
    }

    int getQuantity() {
        return quantity;
    }
}
