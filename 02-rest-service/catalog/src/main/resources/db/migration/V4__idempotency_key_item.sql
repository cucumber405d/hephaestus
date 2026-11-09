-- Idempotency keys now live in the same transaction as the created item: the key points at the item instead of
-- storing a copy of the HTTP response. No foreign key: a replay after the item was deleted is answered with 409.
drop table idempotency_key;

create table idempotency_key
(
    key          varchar(100) primary key,
    request_hash char(64)     not null,
    item_id      uuid         not null,
    created_at   timestamptz  not null
);

create index idempotency_key_created_at on idempotency_key (created_at);
