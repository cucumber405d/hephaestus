-- Orders exist only with a stock reservation in Inventory (reservation_id points into the other service:
-- no foreign key across service boundaries).
create table purchase_order
(
    id             uuid        primary key,
    -- "sub" of the customer's token (Keycloak user id)
    customer_id    varchar(64) not null,
    status         varchar(20) not null,
    reservation_id uuid        not null,
    created_at     timestamptz not null
);

create index purchase_order_customer_idx on purchase_order (customer_id);

create table order_line
(
    order_id uuid        not null references purchase_order (id),
    sku      varchar(40) not null,
    quantity int         not null check (quantity > 0),
    primary key (order_id, sku)
);
