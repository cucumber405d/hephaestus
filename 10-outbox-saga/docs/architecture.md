# Architecture — 10-outbox-saga

```mermaid
flowchart LR
    client[клиент] -->|"POST /api/orders (202)"| ord[Ordering<br/>orders, history, saga]
    ord <-->|"ordering.events / *.commands / ordering.replies"| k[[Kafka]]
    k <--> pay[Payment<br/>лимиты]
    k <--> inv[Inventory<br/>склад]
    k <--> del[Delivery]
    subgraph pg[PostgreSQL: база на сервис]
      odb[(ordering)]
      pdb[(payment)]
      idb[(inventory)]
      ddb[(delivery)]
    end
    ord --- odb
    pay --- pdb
    inv --- idb
    del --- ddb
```

## Outbox (в каждом сервисе)

```mermaid
sequenceDiagram
    participant S as Сервис
    participant DB as Своя БД
    participant M as Spring Modulith
    participant K as Kafka
    S->>DB: BEGIN; изменение состояния
    S->>M: publishEvent(OutgoingMessage)
    M->>DB: insert event_publication
    S->>DB: COMMIT (оба или ничего)
    M->>K: send(topic, key = orderId) после коммита
    alt Kafka ответила
        M->>DB: completion_date = now
    else Kafka лежит
        Note over M,DB: публикация остаётся незавершённой;<br/>переотправка раз в 10 s
    end
```

## Хореография

```mermaid
sequenceDiagram
    participant O as Ordering
    participant P as Payment
    participant I as Inventory
    participant D as Delivery
    O-->>P: OrderPlaced
    P-->>I: PaymentReserved
    I-->>D: StockReserved
    D-->>O: DeliveryScheduled → CONFIRMED
    Note over D: при отказе: DeliveryFailed
    D-->>I: DeliveryFailed → StockReleased
    D-->>P: DeliveryFailed → PaymentRefunded
```

## Оркестрация

```mermaid
sequenceDiagram
    participant O as Ordering (оркестратор)
    participant P as Payment
    participant I as Inventory
    participant D as Delivery
    O->>P: ReservePayment
    P-->>O: PaymentReserved
    O->>I: ReserveStock
    I-->>O: StockReserved
    O->>D: ScheduleDelivery
    alt ответ
        D-->>O: DeliveryScheduled → CONFIRMED
    else нет ответа 30 s
        O->>D: CancelDelivery
        O->>I: ReleaseStock
        O->>P: RefundPayment
        Note over O: REJECTED «timeout at step delivery»
    end
```

Решения и альтернативы — [спецификация](superpowers/specs/2026-11-18-10-outbox-saga-design.md).
