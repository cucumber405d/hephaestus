-- Responses of POST requests keyed by the client's Idempotency-Key, kept for 24 hours.
create table idempotency_key
(
    key             varchar(100) primary key,
    request_hash    char(64)     not null,
    response_status int          not null,
    response_body   jsonb,
    location        text,
    etag            text,
    created_at      timestamptz  not null
);

create index idempotency_key_created_at on idempotency_key (created_at);
