-- Stage 2 transition (Strangler Fig, variant A — ADR-0009).
create schema migration;

-- Where new work orders go: false = the local forge module, true = forge-service via Kafka. Read in the ordering
-- transaction at publication, changed at runtime (POST /admin/forge/cutover), never an env variable.
create table migration.forge_cutover
(
    id     int primary key check (id = 1),
    remote boolean not null
);
insert into migration.forge_cutover (id, remote) values (1, false);

-- The local forge module switched off for good, once its last work order is finished
create table forge.lifecycle
(
    id             int primary key check (id = 1),
    decommissioned boolean not null
);
insert into forge.lifecycle (id, decommissioned) values (1, false);

-- Outcomes from forge-service already applied (Kafka delivers at least once)
create table ordering.processed_event
(
    event_id uuid primary key
);
