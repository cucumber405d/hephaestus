-- Product categories. Names are unique ignoring case ("Swords" and "swords" are the same category).
create table category
(
    id      uuid primary key,
    name    varchar(100) not null,
    version bigint       not null
);

create unique index category_name_ci on category (lower(name));
