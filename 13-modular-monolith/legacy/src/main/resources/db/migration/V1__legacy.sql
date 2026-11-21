-- Stage 0: one schema for everything; any package reads and writes any table.
create table customer_order
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

create table stock_item
(
    sku      varchar(40) primary key,
    on_hand  int not null,
    reserved int not null
);

insert into stock_item (sku, on_hand, reserved)
values ('HAMMER-01', 100000, 0),
       ('ANVIL-01', 100000, 0);

create table material
(
    code    varchar(40) primary key,
    on_hand int not null
);

insert into material (code, on_hand)
values ('IRON-01', 20),
       ('STEEL-01', 20),
       ('MITHRIL-01', 0);

create sequence work_order_seq;

create table work_order
(
    id           varchar(20) primary key,
    order_id     uuid        not null unique,
    material     varchar(40) not null,
    quantity     int         not null,
    status       varchar(20) not null,
    -- the supplier's numeric code, as the supplier sent it
    lieferstatus int,
    created_at   timestamptz not null
);
