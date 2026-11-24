# 13 — архитектура

## Три стадии

```mermaid
flowchart LR
    subgraph S0["Стадия 0: legacy"]
        O0[ordering] <-->|цикл| F0[forge]
        F0 -->|SQL в чужую таблицу| I0[(stock_item)]
        F0 -->|XML-коды внутри| SUP0[Supplier]
    end
    subgraph S1["Стадия 1: modular"]
        O1[ordering] -->|WorkOrderRequested| F1[forge]
        F1 -.->|WorkOrderCompleted / Failed| O1
        O1 --> I1[inventory]
        F1 --> A1[supplier ACL] --> SUP1[Supplier]
    end
    subgraph S2["Стадия 2: Forge вынесен"]
        O2[ordering] -->|RemoteWorkOrderRequested<br/>forge.requests| K[(Kafka)]
        K --> FS[forge-service]
        FS -->|forge.events| K
        K -.-> O2
        FS --> A2[supplier ACL] --> SUP2[Supplier]
        FS -->|ACL /internal/work-orders| M2[монолит: старые WO-]
    end
    S0 --> S1 --> S2
```

Сплошные стрелки — зависимости кода; пунктир — события. На стадии 1 все типы событий лежат в API `forge`, поэтому
зависимость одна: ordering → forge (ADR-0010).

## Переключение (вариант A, ADR-0009)

```mermaid
sequenceDiagram
    participant C as Клиент
    participant GW as Envoy Gateway
    participant M as Монолит (ordering, migration, forge)
    participant K as Kafka
    participant S as forge-service
    participant B as Кузнец
    C->>M: POST /api/orders (custom)
    Note over M: в транзакции заказа: remote? (флаг в базе)
    alt до cutover
        M->>M: WorkOrderRequested → локальный forge → WO-000123
    else после cutover
        M->>K: RemoteWorkOrderRequested (outbox, после коммита)
        K->>S: forge.requests → FW-…
    end
    B->>GW: GET /api/work-orders
    GW->>S: список (фасад)
    S->>M: GET /internal/work-orders (открытые WO-)
    B->>GW: POST /api/work-orders/WO-…/complete
    GW->>M: (регулярка пути WO-)
    B->>GW: POST /api/work-orders/FW-…/complete
    GW->>S: остальное
    S->>K: forge.events (WorkOrderCompleted, eventId)
    K->>M: ordering: READY (один раз на eventId)
```

## Где что живёт

| | Монолит (`modular`) | `forge-service` |
|---|---|---|
| База | `workshop`: схемы `ordering`, `inventory`, `forge`, `migration` | `forge`: `work_order`, `material` |
| Id заказов на ковку | `WO-000123` (последовательность) | `FW-<uuid>` |
| Склад материалов | своя доля — для старых заказов | своя доля, заполняется при развёртывании |
| Проверка структуры | Spring Modulith `verify()` | ArchUnit (гексагон, как 02) |
| ACL к поставщику | модуль `supplier` | `adapter.out.supplier` (копия) |
