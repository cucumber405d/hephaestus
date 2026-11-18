create table processed_message
(
    message_id   uuid        primary key,
    processed_at timestamptz not null
);

create table stock_item
(
    sku      varchar(40) primary key,
    on_hand  int         not null check (on_hand >= 0),
    reserved int         not null default 0 check (reserved >= 0 and reserved <= on_hand)
);

-- One row per order: RESERVED, UNAVAILABLE or RELEASED (also a release before any reservation)
create table stock_reservation
(
    order_id   uuid        primary key,
    status     varchar(20) not null,
    updated_at timestamptz not null
);

create table stock_reservation_line
(
    order_id uuid        not null references stock_reservation (order_id),
    sku      varchar(40) not null,
    quantity int         not null,
    primary key (order_id, sku)
);

insert into stock_item (sku, on_hand) values ('ANVIL-01', 10), ('HAMMER-01', 25), ('TONGS-01', 0);
