-- Catalog items. Each item belongs to exactly one category; a category with items cannot be deleted (FK).
create table item
(
    id             uuid primary key,
    name           varchar(200)   not null,
    description    varchar(2000),
    price_amount   numeric(12, 2) not null,
    price_currency char(3)        not null,
    category_id    uuid           not null,
    created_at     timestamptz    not null,
    version        bigint         not null,
    constraint item_price_not_negative check (price_amount >= 0),
    constraint item_category_fk foreign key (category_id) references category (id)
);

create index item_category_id on item (category_id);
create index item_price_amount on item (price_amount);
