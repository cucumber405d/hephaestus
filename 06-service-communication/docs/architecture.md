# Architecture — 06-service-communication

```mermaid
flowchart LR
    client[клиент] -->|"/api/orders/**, /api/stock/**"| gw[Gateway<br/>Spring Cloud Gateway MVC]
    gw -->|/api/orders/**| ord[Ordering]
    gw -->|/api/stock/**| inv[Inventory]
    ord -->|"POST /api/reservations, GET /api/stock/{sku}<br/>(API-Version 2)"| inv
    ord --> odb[(PostgreSQL ordering)]
    inv --> idb[(PostgreSQL inventory)]
    client -. "/api/reservations → 404" .-x gw
```

## Размещение заказа

```mermaid
sequenceDiagram
    participant C as Client
    participant G as Gateway
    participant O as Ordering
    participant I as Inventory
    C->>G: POST /api/orders {lines}
    G->>O: POST /api/orders (+X-Forwarded-*)
    Note over O: orderId = new UUID, no DB transaction yet
    O->>I: POST /api/reservations {orderId, lines}<br/>API-Version: 2, connect 1 s / read 2 s
    Note over I: one transaction: SELECT ... FOR UPDATE (sorted by SKU),<br/>check previous reservation of orderId, reserve all or nothing
    alt reserved
        I-->>O: 201 {id}
        O->>O: INSERT purchase_order (short transaction)
        O-->>C: 201 Location /api/orders/{id}
    else not enough stock
        I-->>O: 409 insufficient-stock {sku}
        O-->>C: 409 insufficient-stock {sku}
    else unknown SKU
        I-->>O: 422 unknown-sku {sku}
        O-->>C: 422 unknown-item {sku}
    else timeout / refused / 5xx
        O-->>C: 503 inventory-unavailable, Retry-After: 5
    end
```

## Два входа на платформе

`http://shop.localtest.me` → **Envoy** (Gateway API платформы: хосты, TLS в 21, одна точка входа кластера) →
**Spring Cloud Gateway** (прикладной вход магазина: какие пути публичны, позже — токены и BFF в 07) →
сервисы по DNS Service (`http://ordering`, `http://inventory`).

Решения и альтернативы — [спецификация](superpowers/specs/2026-11-17-06-service-communication-design.md), раздел 2.
