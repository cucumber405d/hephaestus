create table processed_message
(
    message_id   uuid        primary key,
    processed_at timestamptz not null
);

-- One row per order: SCHEDULED, FAILED or CANCELLED (also a cancel that came before the schedule: a late
-- schedule for that order is then refused)
create table delivery
(
    order_id    uuid        primary key,
    customer_id varchar(64) not null,
    status      varchar(20) not null,
    updated_at  timestamptz not null
);
