-- Messages already handled (idempotent consumer)
create table processed_message
(
    message_id   uuid        primary key,
    processed_at timestamptz not null
);

-- Credit per customer: an order may reserve up to credit_limit - reserved
create table account
(
    customer_id  varchar(64)    primary key,
    credit_limit numeric(12, 2) not null,
    reserved     numeric(12, 2) not null default 0 check (reserved >= 0)
);

-- One row per order: RESERVED, DECLINED, or REFUNDED (also a refund that came before any reservation: a later
-- reservation for that order is then refused)
create table payment_reservation
(
    order_id    uuid           primary key,
    customer_id varchar(64)    not null,
    amount      numeric(12, 2) not null,
    status      varchar(20)    not null,
    updated_at  timestamptz    not null
);

insert into account (customer_id, credit_limit) values ('alice', 1000.00), ('bob', 50.00);
