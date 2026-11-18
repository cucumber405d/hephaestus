-- Stock per SKU. reserved never exceeds on_hand; the service reserves only what is available.
create table stock_item
(
    sku      varchar(40)  primary key,
    name     varchar(200) not null,
    on_hand  int          not null,
    reserved int          not null,
    constraint stock_item_counts check (on_hand >= 0 and reserved >= 0 and reserved <= on_hand)
);

-- At most one reservation per order (idempotency key for repeated requests)
create table reservation
(
    id         uuid        primary key,
    order_id   uuid        not null,
    created_at timestamptz not null,
    constraint reservation_order_unique unique (order_id)
);

create table reservation_line
(
    reservation_id uuid        not null references reservation (id),
    sku            varchar(40) not null references stock_item (sku),
    quantity       int         not null check (quantity > 0),
    primary key (reservation_id, sku)
);
