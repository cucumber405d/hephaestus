-- Categories are read-only in this service; three fixed categories with stable ids.
insert into category (id, name, version)
values ('01900000-0000-7000-8000-000000000001', 'Armor', 0),
       ('01900000-0000-7000-8000-000000000002', 'Tools', 0),
       ('01900000-0000-7000-8000-000000000003', 'Weapons', 0);
