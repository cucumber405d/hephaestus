-- Binding quotes (14): Forge checks a work order request against the quote Ordering accepted.
create table quote
(
    id             varchar(40) primary key,
    material       varchar(40) not null,
    quantity       int         not null,
    price_cents    bigint      not null,
    lead_time_days int         not null,
    expires_at     timestamptz not null
);
