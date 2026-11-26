-- The event store (16): every change of a work order or a material is an event in this append-only journal.
create schema forge;

create table forge.event
(
    -- global order for projections; read in (tx_id, global_position) order below the snapshot's xmin (ADR-0018)
    global_position bigserial primary key,
    stream_id       varchar(80)  not null,
    stream_type     varchar(40)  not null,
    version         int          not null,
    type            varchar(80)  not null,
    schema_version  int          not null,
    payload         jsonb        not null,
    metadata        jsonb        not null,
    recorded_at     timestamptz  not null default clock_timestamp(),
    tx_id           xid8         not null default pg_current_xact_id(),
    -- optimistic concurrency: two writers of the same stream version cannot both succeed
    unique (stream_id, version)
);

create index event_tx_order on forge.event (tx_id, global_position);

create function forge.reject_change() returns trigger
    language plpgsql as
$$
begin
    raise exception 'forge.event is append-only (% refused)', tg_op;
end;
$$;

create trigger event_append_only
    before update or delete or truncate
    on forge.event
    for each statement
execute function forge.reject_change();

-- Read side: projections and where each of them stopped
create schema read;

create table read.projection_checkpoint
(
    name     varchar(60) primary key,
    tx_id    xid8        not null,
    position bigint      not null
);
