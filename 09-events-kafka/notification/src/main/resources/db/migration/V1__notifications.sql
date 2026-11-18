-- One row per event id ever handled: the idempotency record of the consumer
create table processed_event
(
    event_id     uuid        primary key,
    processed_at timestamptz not null
);

create table notification
(
    id          uuid         primary key,
    customer_id varchar(64)  not null,
    order_id    uuid         not null,
    channel     varchar(20)  not null,
    text        varchar(500) not null,
    created_at  timestamptz  not null
);

create index notification_customer_idx on notification (customer_id, created_at);
