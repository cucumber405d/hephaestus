-- Ordering (from 13's modular monolith): every module owns its schema (ADR-0010).
-- The event publication registry (Spring Modulith) creates event_publication in public itself.
create schema ordering;
create schema inventory;

create table ordering.customer_order
(
    id             uuid primary key,
    customer_id    varchar(64) not null,
    sku            varchar(40),
    custom         boolean     not null,
    material       varchar(40),
    quantity       int         not null,
    status         varchar(20) not null,
    reason         varchar(200),
    -- Forge's binding quote (custom orders); sent with the work order request
    quote_id       varchar(40),
    price_cents    bigint,
    lead_time_days int,
    created_at     timestamptz not null
);

-- Outcomes from forge-service already applied (Kafka delivers at least once)
create table ordering.processed_event
(
    event_id uuid primary key
);

create table inventory.stock_item
(
    sku      varchar(40) primary key,
    on_hand  int not null,
    reserved int not null
);

insert into inventory.stock_item (sku, on_hand, reserved)
values ('HAMMER-01', 100000, 0),
       ('ANVIL-01', 100000, 0);
