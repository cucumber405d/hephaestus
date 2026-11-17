-- Orders exist only with a stock reservation in Inventory (reservation_id points into the other service:
-- no foreign key across service boundaries).
create table purchase_order
(
    id             uuid        primary key,
    status         varchar(20) not null,
    reservation_id uuid        not null,
    created_at     timestamptz not null
);

create table order_line
(
    order_id uuid        not null references purchase_order (id),
    sku      varchar(40) not null,
    quantity int         not null check (quantity > 0),
    primary key (order_id, sku)
);
