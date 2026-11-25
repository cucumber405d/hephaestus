-- 15: the outbox Debezium reads from the WAL (Outbox Event Router columns). payload = the bytes Apicurio's Avro
-- serializer produces for cdc.forge.requests — exactly what the relay of 14 would send.
create table ordering.outbox
(
    id             uuid primary key,
    aggregate_type varchar(64) not null,
    aggregate_id   varchar(64) not null,
    type           varchar(64) not null,
    payload        bytea       not null,
    created_at     timestamptz not null
);

create index outbox_created_at on ordering.outbox (created_at);

-- Logical decoding of this table only; Debezium uses the publication, it does not create one
create publication cdc_outbox for table ordering.outbox;

-- The connector's user (REPLICATION, created by the platform: compose init script, CNPG managed role)
grant usage on schema ordering to ${cdcUser};
grant select on ordering.outbox to ${cdcUser};
