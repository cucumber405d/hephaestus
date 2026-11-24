-- Forge's own database. The material stock is the service's share, seeded at deployment: "the stock is split when
-- the workshop moves" — the monolith keeps its own for the work orders it still finishes (ADR-0009).
create table material
(
    code    varchar(40) primary key,
    on_hand int not null
);

insert into material (code, on_hand)
values ('IRON-01', 20),
       ('STEEL-01', 20),
       ('MITHRIL-01', 0);

create table work_order
(
    id             varchar(40) primary key,
    -- one work order per customer order: a redelivered request finds it (idempotent intake)
    order_id       uuid        not null unique,
    material       varchar(40) not null,
    quantity       int         not null,
    status         varchar(20) not null,
    material_ready boolean     not null,
    failure_reason varchar(200),
    created_at     timestamptz not null
);
