-- Stage 1: every module owns its schema; nobody reads another module's tables (ADR-0010).
-- The event publication registry (Spring Modulith) creates event_publication in public itself.
create schema ordering;
create schema inventory;
create schema forge;

create table ordering.customer_order
(
    id          uuid primary key,
    customer_id varchar(64) not null,
    sku         varchar(40),
    custom      boolean     not null,
    material    varchar(40),
    quantity    int         not null,
    status      varchar(20) not null,
    reason      varchar(200),
    created_at  timestamptz not null
);

create table inventory.stock_item
(
    sku      varchar(40) primary key,
    on_hand  int not null,
    reserved int not null
);

insert into inventory.stock_item (sku, on_hand, reserved)
values ('HAMMER-01', 100000, 0),
       ('ANVIL-01', 100000, 0);

create table forge.material
(
    code    varchar(40) primary key,
    on_hand int not null
);

insert into forge.material (code, on_hand)
values ('IRON-01', 20),
       ('STEEL-01', 20),
       ('MITHRIL-01', 0);

create sequence forge.work_order_seq;

create table forge.work_order
(
    id             varchar(20) primary key,
    order_id       uuid        not null unique,
    material       varchar(40) not null,
    quantity       int         not null,
    status         varchar(20) not null,
    -- false while the supplier could not be reached; the retry reorders, the smith cannot start yet
    material_ready boolean     not null,
    failure_reason varchar(200),
    created_at     timestamptz not null
);
